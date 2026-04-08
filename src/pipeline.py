import re
from urllib.parse import urlparse

from distilbert_model import predict
from llm_explainer import generate_explanation


TRUSTED_DOMAINS = [
    "cibc.com",
    "scotiabank.com",
    "td.com",
    "rbc.com",
    "instagram.com",
    "paypal.com",
    "amazon.com",
    "apple.com",
    "netflix.com"
]


def extract_urls(text):
    return re.findall(r'(https?://\S+)', text)


def is_suspicious_domain(url):
    domain = urlparse(url).netloc.lower()
    for trusted in TRUSTED_DOMAINS:
        if trusted in domain:
            return False
    return True


def brand_domain_mismatch_score(text, urls):
    text_lower = text.lower()
    score = 0

    brand_domains = {
        "instagram": "instagram.com",
        "paypal": "paypal.com",
        "amazon": "amazon.com",
        "apple": "apple.com",
        "netflix": "netflix.com"
    }

    for brand, official_domain in brand_domains.items():
        if brand in text_lower:
            for url in urls:
                domain = urlparse(url).netloc.lower()
                if official_domain not in domain:
                    score += 30

    return score


def rule_based_score(text):
    score = 0
    text_lower = text.lower()

    keywords = [
        "urgent", "verify", "click", "account", "password", "login",
        "hacked", "suspended", "locked", "security", "confirm",
        "reset", "limited", "warning", "unusual", "immediately"
    ]

    for word in keywords:
        if word in text_lower:
            score += 10

    urls = extract_urls(text)

    for url in urls:
        if is_suspicious_domain(url):
            score += 30
        else:
            score -= 10

    score += brand_domain_mismatch_score(text, urls)

    return max(0, min(score, 100))


def analyze_sms(text):
    # Use raw text for DistilBERT
    result = predict(text)
    ml_score = result["risk_score"]

    rb_score = rule_based_score(text)

    final_score = (0.6 * ml_score) + (0.4 * rb_score)
    final_prediction = "SPAM" if final_score >= 35 else "SAFE"

    explanation = generate_explanation(text, final_score)

    return {
        "text": text,
        "ml_prediction": result["prediction"],
        "ml_score": ml_score,
        "rule_score": rb_score,
        "final_score": round(final_score, 2),
        "prediction": final_prediction,
        "explanation": explanation
    }


if __name__ == "__main__":
    msg = "Your instagram account is hacked. Verify now: https://www.instagram.ca"
    print(analyze_sms(msg))