from pathlib import Path
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

# Absolute path to project root
BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = BASE_DIR / "models" / "distilbert"

print("Loading model from:", MODEL_PATH)

tokenizer = AutoTokenizer.from_pretrained(str(MODEL_PATH), local_files_only=True)
model = AutoModelForSequenceClassification.from_pretrained(str(MODEL_PATH), local_files_only=True)

def predict(text):
    inputs = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=128
    )

    with torch.no_grad():
        outputs = model(**inputs)

    probs = torch.softmax(outputs.logits, dim=1)
    predicted_class = torch.argmax(probs, dim=1).item()
    confidence = probs[0][predicted_class].item()

    prediction = "SPAM" if predicted_class == 1 else "SAFE"
    risk_score = confidence * 100 if predicted_class == 1 else (1 - confidence) * 100

    return {
        "prediction": prediction,
        "confidence": round(confidence, 4),
        "risk_score": round(risk_score, 2)
    }