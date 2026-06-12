from flask import Flask, request, jsonify
from transformers import pipeline, AutoTokenizer, AutoModelForSequenceClassification
import os

app = Flask(__name__)

# Read environment secret for model's access token
# Use a fine-grained access token to read your model's repository
access_token = os.environ["HF_TOKEN"]

print("Loading model...")
MODEL_NAME = "username/huggingface-model"

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, token=access_token)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME, token=access_token)

classifier = pipeline(
    "text-classification", 
    model=model, 
    tokenizer=tokenizer, 
    return_token_type_ids=False
)

print("Model loaded!")
    
# --- Error handlers ---
@app.errorhandler(400)
def bad_request(e):
    return jsonify({"error": str(e)}), 400

@app.errorhandler(500)
def internal_error(e):
    return jsonify({"error": str(e)}), 500

# --- Routes ---
@app.route('/', methods=['GET'])
def home():
    return jsonify({"status": "SMS Classifier API is running!"})

@app.route('/classify', methods=['POST'])
def classify():
    # Validate request has JSON body
    data = request.get_json(force=True, silent=True)
    if data is None:
        return jsonify({"error": "Invalid JSON body"}), 400

    text = data.get('text', '')

    # Return status code 400 for empty text
    if not text:
        return jsonify({"error": "No text provided"}), 400

    # Validate text is a string
    if not isinstance(text, str):
        return jsonify({"error": "Text must be a string"}), 400

    try:
        result = classifier(text)
        prediction = result[0]

        label = "SPAM" if prediction["label"] == "LABEL_1" else "SAFE"
        confidence = float(prediction["score"])

        # Response shape identical to original
        return jsonify({
            "label": label,
            "confidence": confidence
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7860)
