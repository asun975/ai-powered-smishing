import os
from transformers import pipeline

model_path = os.path.join('.', 'models', 'sms-spam-model-v2')

if not os.path.exists(model_path):
        raise FileNotFoundError(
            f"Trained model not found at {model_path}. Run: python src/distilbert_model_prototype.py and python src/train_model.py"
        )

classifier = pipeline(
    "text-classification",
    model=model_path
)

def predict(text):
    result = classifier(text)[0]

    label_map = {
        "LABEL_0": "SAFE",
        "LABEL_1": "SPAM"
    }

    label = label_map[result["label"]]
    score = result["score"]

    risk_score = score * 100 if label == "SPAM" else (1 - score) * 100

    print(f"\nMessage: {text}")
    print(f"Prediction: {label}")
    print(f"Confidence: {score:.4f}")
    print(f"Risk Score: {risk_score:.2f}")

# Test examples
predict("URGENT! Your account has been compromised. Click here now!")
predict("Hey, are we still meeting later?")
predict("You won $1000! Claim your prize now!")
