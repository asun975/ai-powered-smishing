import re

_PHONE_RE = re.compile(
    r"""
    (?:
        \+\d{1,3}[\s.\-]?(?:\(?\d{1,4}\)?[\s.\-]?)?\d{1,4}[\s.\-]\d{2,4}[\s.\-]\d{2,4}
        |
        \(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}
    )
    """,
    re.VERBOSE,
)

_EMAIL_RE = re.compile(
    r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b"
)

# Credit/debit card: 16 digits optionally grouped by spaces or dashes
_CARD_RE = re.compile(
    r"\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"
)

# Canadian SIN: 123 456 789 or 123-456-789 
# (requires separator to avoid false positives)
_SIN_RE = re.compile(r"\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b")

# US SSN: 123-45-6789
_SSN_RE = re.compile(r"\b\d{3}-\d{2}-\d{4}\b")

_EMOJI_RE = re.compile(
    "[\U00010000-\U0010ffff]|[\U0001F300-\U0001F9FF]",
    flags=re.UNICODE,
)

_BANK_RE = re.compile(r"\s[0-9]{9,18}\s")

_MFA_RE = re.compile(r"[0-9]{6}")

_IP_ADDRESS_RE = re.compile(r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}")

_URL_RE = re.compile(r"http\S+")

_WHITESPACE_RE = re.compile(r"\s+")

_SPECIAL_CHAR_RE = re.compile(r'[^\w\s\d]')

# Remove PII
def sanitize_text(sms_text: str) -> str:
    sms_text = _EMAIL_RE.sub("", sms_text)
    sms_text = _PHONE_RE.sub("", sms_text)
    sms_text = _CARD_RE.sub("", sms_text)
    sms_text = _SIN_RE.sub("", sms_text)
    sms_text = _SSN_RE.sub("", sms_text)
    sms_text = _BANK_RE.sub("", sms_text)
    sms_text = _MFA_RE.sub("", sms_text)
    sms_text = _IP_ADDRESS_RE.sub("", sms_text)
    return sms_text

# Separate url removal to compare model performance
def removeUrl(sms_text: str) -> str:
    sms_text = _URL_RE.sub("", sms_text)
    return sms_text

# Mask Personal Identifiable Information (PII) for LLM
def maskPII(sms_text: str) -> str:
    sms_text = _EMAIL_RE.sub("[EMAIL]", sms_text)
    sms_text = _PHONE_RE.sub("[PHONE]", sms_text)
    sms_text = _CARD_RE.sub("[CARD]", sms_text)
    sms_text = _SIN_RE.sub("[ID]", sms_text)
    sms_text = _SSN_RE.sub("[ID]", sms_text)
    sms_text = _BANK_RE.sub("[CARD]", sms_text)
    sms_text = _MFA_RE.sub("[CODE]", sms_text)
    sms_text = _IP_ADDRESS_RE.sub("[IP ADDRESS]", sms_text)
    sms_text = _URL_RE.sub("[URL]", sms_text)
    return sms_text

# Data cleaning
def text_preprocess(sms_text: str) -> str:
    sms_text = sms_text.lower()
    sms_text = _EMOJI_RE.sub("", sms_text)
    sms_text = _WHITESPACE_RE.sub(" ", sms_text)
    sms_text = sms_text.strip()
    return sms_text

# Separate removing special characters to test model performance
def remove_special_char(sms_text: str) -> str:
    sms_text = _SPECIAL_CHAR_RE.sub(" ", sms_text) # prevent words from joining
    # remove extra whitespaces from special character regex
    sms_text = _WHITESPACE_RE.sub(" ", sms_text)  
    return sms_text
