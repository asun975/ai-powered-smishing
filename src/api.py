import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from pipeline import analyze_sms

# ── Logging ────────────────────────────────────────────────────────────────────

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("smishing_api")

# Maximum SMS length we will accept (real SMS is 160 chars, but MMS can be longer)
MAX_MESSAGE_LENGTH = 1000

# ── App setup ──────────────────────────────────────────────────────────────────

app = FastAPI(
    title="AI-Powered Smishing Detection API",
    description=(
        "Detects smishing (SMS phishing) using a fine-tuned DistilBERT model "
        "combined with rule-based scoring for suspicious keywords, URLs, and brand mismatches."
    ),
    version="1.0.0",
)

# Allow requests from the Android app (adjust origins in production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Request / Response Models ──────────────────────────────────────────────────

class SMSRequest(BaseModel):
    message: str

    class Config:
        json_schema_extra = {
            "example": {
                "message": "URGENT: Your bank account has been locked. Verify now: http://secure-login.xyz"
            }
        }


class SMSResponse(BaseModel):
    message: str
    prediction: str       # "SPAM" or "SAFE"
    risk_score: float     # 0–100 combined score
    ml_prediction: str    # DistilBERT-only prediction
    ml_confidence: float  # 0.0–1.0
    ml_score: float       # 0–100 ML contribution
    rule_score: float     # 0–100 rule-based contribution
    explanation: str      # plain-English reason
    skipped: bool = False     # True when message was filtered before ML inference
    skip_reason: str = ""     # why it was skipped (empty string if not skipped)


class HealthResponse(BaseModel):
    status: str
    version: str


# ── Startup: warm-up model load ────────────────────────────────────────────────

@app.on_event("startup")
async def startup_event():
    """
    Force-load the DistilBERT model when the server starts so the first real
    request doesn't pay the cold-start penalty.  Logs a clear error if the
    model files are missing (user needs to run train_model.py first).
    """
    try:
        from distilbert_model import predict  # noqa: F401  — import triggers model load
        logger.info("DistilBERT model loaded and ready.")
    except FileNotFoundError:
        logger.error(
            "DistilBERT model files not found at models/distilbert/. "
            "Run src/train_model.py to train and save the model first."
        )
    except Exception as e:
        logger.warning(f"Model pre-load warning (may still work at request time): {e}")


# ── Endpoints ──────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse, tags=["Health"])
def health_check():
    """Return 200 OK when the server is running and accepting requests."""
    return {"status": "ok", "version": "1.0.0"}


@app.post("/analyze", response_model=SMSResponse, tags=["Smishing Detection"])
def analyze(request: SMSRequest):
    """
    Analyze an SMS message for smishing.

    **Pipeline:**
    1. Input validation (empty / too long)
    2. Preprocessing — PII strip, media-only / trivial filter
    3. DistilBERT ML inference (60 % weight)
    4. Rule-based scoring — keywords, URL trust, brand mismatch (40 % weight)
    5. LLM explanation generation

    **Edge cases:**
    - Empty or whitespace-only → **400**
    - Message over 1 000 characters → **400**
    - Media-only or trivial messages → `skipped=true`, `prediction="SAFE"`, `risk_score=0`
    - Model files missing → **503** (run `train_model.py` first)
    - Any other internal error → **500**
    """
    # ── 1. Input validation ───────────────────────────────────────────────────

    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty.")

    if len(request.message) > MAX_MESSAGE_LENGTH:
        raise HTTPException(
            status_code=400,
            detail=(
                f"Message is too long ({len(request.message)} characters). "
                f"Maximum allowed length is {MAX_MESSAGE_LENGTH} characters."
            ),
        )

    # ── 2. Run the full pipeline ──────────────────────────────────────────────

    try:
        result = analyze_sms(request.message)

    except FileNotFoundError as e:
        # Model weights not on disk — server-side configuration problem
        logger.error(f"Model file not found: {e}")
        raise HTTPException(
            status_code=503,
            detail=(
                "The detection model is not available on this server. "
                "Please contact the server administrator."
            ),
        )
    except OSError as e:
        # Disk / permissions error loading the model
        logger.error(f"OS error loading model: {e}")
        raise HTTPException(
            status_code=503,
            detail="Could not load the detection model. Please try again later.",
        )
    except ValueError as e:
        # Bad input that slipped past the length/empty checks
        logger.warning(f"ValueError during analysis: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid input: {str(e)}")
    except Exception as e:
        # Catch-all — log full traceback server-side, return generic message to client
        logger.error(f"Unexpected error during analysis: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail="Analysis failed due to an internal server error.",
        )

    # ── 3. Handle skipped messages (media-only, trivial, too short) ───────────
    #
    # When should_skip() returns True, analyze_sms() returns a dict that does NOT
    # contain ml_prediction / ml_confidence / ml_score / rule_score / final_score.
    # We must fill in safe defaults so SMSResponse can be constructed without crashing.

    if result.get("skipped"):
        logger.info(
            f"Message skipped — reason: {result.get('skip_reason', 'unknown')} "
            f"| text: {result['text'][:60]!r}"
        )
        return SMSResponse(
            message=result["text"],
            prediction="SAFE",
            risk_score=0.0,
            ml_prediction="SAFE",
            ml_confidence=0.0,
            ml_score=0.0,
            rule_score=0.0,
            explanation=result.get(
                "explanation", "Message was skipped: no analyzable text content."
            ),
            skipped=True,
            skip_reason=result.get("skip_reason", ""),
        )

    # ── 4. Normal result ──────────────────────────────────────────────────────

    return SMSResponse(
        message=result["text"],
        prediction=result["prediction"],
        risk_score=result["final_score"],
        ml_prediction=result["ml_prediction"],
        ml_confidence=result["ml_confidence"],
        ml_score=result["ml_score"],
        rule_score=result["rule_score"],
        explanation=result["explanation"],
        skipped=False,
        skip_reason="",
    )


# ── Run locally ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
