import re
from urllib.parse import urlparse

from distilbert_model import predict
from llm_explainer import generate_explanation
from preprocessing import clean_for_distilbert, clean_for_llm, should_skip

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
    skip, reason = should_skip(text)
    if skip:
        return {
            "text": text,
            "skipped": True,
            "skip_reason": reason,
            "prediction": "SAFE",
            "explanation": "Message skipped: no analyzable text content.",
        }

    # Strip PII before model inference; transformers prefer natural text otherwise.
    distilbert_input = clean_for_distilbert(text)
    result = predict(distilbert_input)
    ml_score = result["risk_score"]
    rb_score = rule_based_score(text)

    final_score = (0.6 * ml_score) + (0.4 * rb_score)
    final_prediction = "SPAM" if final_score >= 35 else "SAFE"

    # Pass PII-cleaned text to the explainer so no sensitive data is exposed.
    llm_input = clean_for_llm(text)
    explanation = generate_explanation(llm_input, final_score)

    return {
        "text": text,
        "skipped": False,
        "ml_prediction": result["prediction"],
        "ml_confidence": result["confidence"],
        "ml_score": ml_score,
        "rule_score": rb_score,
        "final_score": round(final_score, 2),
        "prediction": final_prediction,
        "explanation": explanation,
    }


def _print_result(result: dict):
    if result.get("skipped"):
        print(f"  [SKIPPED] reason: {result['skip_reason']}")
        return
    print(f"  Prediction  : {result['prediction']}")
    print(f"  Final Score : {result['final_score']} / 100")
    print(f"  ML ({result['ml_prediction']}, conf {result['ml_confidence']:.2%})  "
          f"Rule score: {result['rule_score']}")
    print(f"  Explanation : {result['explanation']}")


if __name__ == "__main__":
    examples = [
        # Smishing
        "URGENT! Your CIBC account has been locked. Login now at http://secure-cibc.xyz",
        "Your instagram account is hacked. Verify now: https://www.instagram.ca",
        "You have won a lottery! Claim your prize at http://prize-winner.xyz Click now!!",
        "Your package could not be delivered. Update your address: http://track-pkg.net",
        # Legitimate
        "Hey, are we still meeting at 7 tonight?",
        "Your appointment is confirmed for Tuesday at 3pm.",
    ]

    print("Loading model...")
    print("\n" + "=" * 65)
    print("BUILT-IN EXAMPLES")
    print("=" * 65)

    for msg in examples:
        print(f"\nMESSAGE: {msg}")
        print("-" * 65)
        _print_result(analyze_sms(msg))

    print("\n" + "=" * 65)
    print("Enter your own SMS messages to test (type 'quit' to exit):")

    while True:
        print()
        msg = input("SMS > ").strip()
        if msg.lower() in ("quit", "exit", "q"):
            break
        if not msg:
            continue
        _print_result(analyze_sms(msg))
