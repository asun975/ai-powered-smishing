from urllib.parse import urlparse
import re


def extract_urls(text: str) -> list[str]:
    return re.findall(r"(https?://\S+)", text)


def generate_explanation(text: str, risk_score: float) -> str:
    parts = []
    lower = text.lower()

    suspicious_keywords = [
        "urgent", "verify", "click", "password", "login", "reset",
        "locked", "suspended", "hacked", "security", "confirm"
    ]
    found = [w for w in suspicious_keywords if w in lower]
    if found:
        parts.append(f"It contains suspicious wording such as: {', '.join(found[:4])}.")

    urls = extract_urls(text)
    if urls:
        domains = [urlparse(url).netloc for url in urls]
        parts.append(f"It includes link(s): {', '.join(domains)}.")

    if risk_score >= 70:
        summary = "This message is highly suspicious and likely a smishing attempt."
    elif risk_score >= 40:
        summary = "This message has multiple suspicious signals and should be treated carefully."
    else:
        summary = "This message looks relatively safe, but you should still verify unexpected links or requests."

    if not parts:
        return summary
    return summary + " " + " ".join(parts)
