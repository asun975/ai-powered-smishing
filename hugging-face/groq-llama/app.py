from flask import Flask, request, jsonify
from groq import Groq
import os

app = Flask(__name__)

# Groq client — reads API key from environment secret
GROQ_API_KEY = os.environ.get("HF_TOKEN")
client = Groq(api_key=GROQ_API_KEY)

# Groq's hosted Gemma 2b model name
# Llama 4 Scout - best for explanations
GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct" 

print("Groq LLM API ready!")

def build_prompt(sms_text: str, risk_score: float, classification: str) -> str:
    risk_percent = int(risk_score * 100)
    return (
        f"You are a cybersecurity analyst."
        f"A phishing detection system has already analyzed an SMS message and produced the following results:\n\n"
        f"Classification: {classification}\n"
        f"Confidence: {risk_percent}%\n\n"
        f"Your task is to explain WHY the message received this classification.\n\n"
        f"Focus on:\n"
        f"- Suspicious URLs or domains\n"
        f"- Urgent or threatening language\n"
        f"- Requests for credentials or personal information\n"
        f"- Financial scams, rewards, or refunds\n"
        f"- Impersonation attempts\n"
        f"- Social engineering tactics\n\n"
        f"Return ONLY:\n\n"
        f"Reason: <one concise sentence between 10 and 30 words>\n\n"
        f"SMS Message:\n{sms_text}"
    )

@app.route('/', methods=['GET'])
def home():
    return jsonify({
        "status": "Groq Gemma LLM Explanation API is running!",
        "version": "Groq-Hosted",
        "model": GROQ_MODEL
    })

@app.route('/explain', methods=['POST'])
def explain():
    """
    Expects JSON body:
    {
        "text": "cleaned SMS text",
        "classification": "SPAM" or "SAFE",
        "risk_score": 0.87
    }
    """
    try:
        data = request.get_json()

        sms_text = data.get('text', '').strip()
        classification = data.get('classification', 'UNKNOWN').strip().upper()
        risk_score = max(0.0, min(1.0, float(data.get('risk_score', 0.5))))

        if not sms_text:
            return jsonify({"error": "No SMS text provided"}), 400

        if not GROQ_API_KEY:
            return jsonify({"error": "GROQ_API_KEY not set in environment"}), 500

        # Call Groq API — fast, no model loading needed
        chat_completion = client.chat.completions.create(
            model=GROQ_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "You are a cybersecurity assistant that explains SMS phishing (smishing) threats to everyday users in simple, clear language."
                },
                {
                    "role": "user",
                    "content": build_prompt(sms_text, risk_score, classification)
                }
            ],
            max_tokens=150,
            temperature=0.7
        )

        explanation = chat_completion.choices[0].message.content.strip()

        return jsonify({
            "explanation": explanation,
            "classification": classification,
            "risk_score": risk_score,
            "version": "groq"
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7860)
