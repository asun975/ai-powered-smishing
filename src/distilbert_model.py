import torch
from os import path
from transformers import AutoTokenizer, AutoModelForSequenceClassification

# Local sms-spam model
MODEL_PATH = path.join("..", "models", "sms-spam-model-0")


def load_model():
    if path.exists(MODEL_PATH):
        tokenizer = AutoTokenizer.from_pretrained(str(MODEL_PATH), local_files_only=True)
        model = AutoModelForSequenceClassification.from_pretrained(
            str(MODEL_PATH),
            local_files_only=True,
        )
        model.eval()
        return tokenizer, model
    else:
        print(f"Trained model not found at {MODEL_PATH}. Run: python src/train_model.py")



tokenizer, model = load_model()


def predict(text: str) -> dict:
    """Run inference using the locally trained DistilBERT model."""
    inputs = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=128,
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
        "risk_score": round(risk_score, 2),
    }
