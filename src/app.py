from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel
from transformers import pipeline, AutoTokenizer, AutoModelForSequenceClassification
from huggingface_hub import login
import os

app = FastAPI()

# Login to Hugging Face with token from environment
HF_TOKEN = os.environ.get("HF_TOKEN")
if HF_TOKEN:
    login(token=HF_TOKEN)
    print("Logged in to Hugging Face!")

# Load model
print("Loading model...")
MODEL_NAME = "totoro2211/sms-smishing-distilbert"

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)

classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer,
    return_token_type_ids=False
)

print("Model loaded!")

# --- Request schema ---
class ClassifyRequest(BaseModel):
    text: str = ""

# --- Error handlers (matches Flask's jsonify error format) ---
@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(status_code=400, content={"error": str(exc)})

@app.exception_handler(Exception)
async def global_error_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"error": str(exc)})

# --- Routes ---
@app.get("/")
def home():
    return {"status": "SMS Classifier API is running!"}

@app.post("/classify")
def classify(body: ClassifyRequest):
    text = body.text

    # Match Flask's exact 400 behaviour for empty text
    if not text:
        return JSONResponse(status_code=400, content={"error": "No text provided"})

    result = classifier(text)
    prediction = result[0]

    label = "SPAM" if prediction["label"] == "LABEL_1" else "SAFE"
    confidence = float(prediction["score"])

    # Response shape identical to Flask version
    return {
        "label": label,
        "confidence": confidence
    }
