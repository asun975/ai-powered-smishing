import re
from urllib.parse import urlparse

from distilbert_model import predict
from tinyLlama_explainer import generate_reasoning

# Known trusted domains for demo purposes.
TRUSTED_DOMAINS = [
    "cibc.com", "scotiabank.com", "td.com", "rbc.com",
    "instagram.com", "paypal.com", "amazon.com", "apple.com", "netflix.com"
]

BRAND_DOMAINS = {
    "instagram": "instagram.com",
    "paypal": "paypal.com",
    "amazon": "amazon.com",
    "apple": "apple.com",
    "netflix": "netflix.com",
    "cibc": "cibc.com",
    "scotiabank": "scotiabank.com",
    "td": "td.com",
    "rbc": "rbc.com",
}



def extract_urls(text: str) -> list[str]:
    return re.findall(r"(https?://\S+)", text)



def is_trusted_domain(url: str) -> bool:
    domain = urlparse(url).netloc.lower()
    return any(trusted in domain for trusted in TRUSTED_DOMAINS)



def brand_domain_mismatch_score(text: str, urls: list[str]) -> int:
    text_lower = text.lower()
    score = 0

    for brand, official_domain in BRAND_DOMAINS.items():
        if brand in text_lower:
            for url in urls:
                domain = urlparse(url).netloc.lower()
                if official_domain not in domain:
                    score += 30
    return score



def rule_based_score(text: str) -> int:
    score = 0
    text_lower = text.lower()

    keywords = [
        "urgent", "verify", "click", "account", "password", "login",
        "hacked", "suspended", "locked", "security", "confirm",
        "reset", "limited", "warning", "unusual", "immediately",
        "bank", "prize", "winner", "claim", "free"
    ]

    for word in keywords:
        if word in text_lower:
            score += 10

    urls = extract_urls(text)
    for url in urls:
        score += 30 if not is_trusted_domain(url) else 0

    score += brand_domain_mismatch_score(text, urls)

    # More signals
    if re.search(r"\d{4,}", text):
        score += 5
    if text.count("!") >= 2:
        score += 5

    return max(0, min(score, 100))



def analyze_sms(text: str) -> dict:
    # Use raw text for DistilBERT; transformers generally perform better this way.
    result = predict(text)
    ml_score = result["risk_score"]
    rb_score = rule_based_score(text)

    # Slightly stronger rule weighting for obvious phishing clues.
    final_score = (0.6 * ml_score) + (0.4 * rb_score)
    final_prediction = "SPAM" if final_score >= 35 else "SAFE"

    explanation = generate_reasoning(message=text, risk_score=final_score, label=final_prediction)

    return {
        "text": text,
        "ml_prediction": result["prediction"],
        "ml_confidence": result["confidence"],
        "ml_score": ml_score,
        "rule_score": rb_score,
        "final_score": round(final_score, 2),
        "prediction": final_prediction,
        "explanation": explanation,
    }


if __name__ == "__main__":
    examples = [
        "Your instagram account is hacked. Verify now: https://www.instagram.ca",
        "Hey, are we still meeting at 7 tonight?",
        "URGENT! Your bank account has been locked. Login now at http://secure-login.xyz",
    ]
    print(analyze_sms(examples[0]))
"""
f   or msg in examples:
        print(analyze_sms(msg))
        print("-" * 80)
"""
