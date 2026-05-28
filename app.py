from flask import Flask, request, jsonify
from transformers import pipeline, AutoTokenizer, AutoModelForSequenceClassification
from huggingface_hub import login
import os

app = Flask(__name__)

# Login to Hugging Face with token from environment
HF_TOKEN = os.environ.get("HF_TOKEN")
if HF_TOKEN:
    login(token=HF_TOKEN)
    print("Logged in to Hugging Face!")

# Load your model with explicit tokenizer configuration
print("Loading model...")
MODEL_NAME = "totoro2211/sms-smishing-distilbert"

# Load tokenizer and model separately for better control
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)

# Create pipeline with return_token_type_ids=False
classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer,
    return_token_type_ids=False
)

print("Model loaded!")

@app.route('/', methods=['GET'])
def home():
    return jsonify({"status": "SMS Classifier API is running!"})

@app.route('/classify', methods=['POST'])
def classify():
    try:
        data = request.get_json()
        text = data.get('text', '')
        
        if not text:
            return jsonify({"error": "No text provided"}), 400
        
        # Classify
        result = classifier(text)
        prediction = result[0]
        
        # Map LABEL_0/LABEL_1 to SAFE/SPAM
        label = "SPAM" if prediction['label'] == "LABEL_1" else "SAFE"
        confidence = prediction['score']
        
        return jsonify({
            "label": label,
            "confidence": float(confidence)
        })
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7860)  # HF Spaces uses port 7860