from transformers import pipeline

classifier = pipeline(
    "text-classification",
    model="./sms-spam-model"
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

while True:
    msg = input("\nEnter SMS (or 'exit'): ")
    if msg == "exit":
        break
    predict(msg)