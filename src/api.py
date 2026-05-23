from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pipeline import analyze_sms

app = FastAPI(
    title="AI-Powered Smishing Detection API",
    description="API for detecting smishing (SMS phishing) using DistilBERT + rule-based scoring.",
    version="1.0.0"
)

# Allow requests from the Android app
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
    prediction: str          # "SPAM" or "SAFE"
    risk_score: float        # 0–100
    ml_prediction: str
    ml_confidence: float
    ml_score: float
    rule_score: float
    explanation: str


class HealthResponse(BaseModel):
    status: str
    version: str


# ── Endpoints ──────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse, tags=["Health"])
def health_check():
    """
    Check whether the API server is running and ready.
    """
    return {"status": "ok", "version": "1.0.0"}


@app.post("/analyze", response_model=SMSResponse, tags=["Smishing Detection"])
def analyze(request: SMSRequest):
    """
    Analyze an SMS message for smishing.

    - Runs the message through the locally trained DistilBERT model
    - Combines ML score with rule-based scoring (suspicious keywords, URL checks, brand mismatch)
    - Returns a final prediction (SPAM / SAFE), risk score (0–100), and a plain-English explanation
    """
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty.")

    try:
        result = analyze_sms(request.message)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")

    return SMSResponse(
        message=result["text"],
        prediction=result["prediction"],
        risk_score=result["final_score"],
        ml_prediction=result["ml_prediction"],
        ml_confidence=result["ml_confidence"],
        ml_score=result["ml_score"],
        rule_score=result["rule_score"],
        explanation=result["explanation"],
    )


# ── Run locally ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
