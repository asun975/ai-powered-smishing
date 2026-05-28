import re
from sklearn.feature_extraction import _stop_words
from nltk.stem import SnowballStemmer

stemmer = SnowballStemmer("english")

def removeMedia(sms: str) -> str:
    """Remove media attachments from SMS message"""
    sms = re.sub(r"\[(?:image|photo|picture|mms|video|audio|file|attachment|gif|sticker)\]", "",sms)
    sms = re.sub(r"|<(?:image|photo|mms)>","", sms)
    sms = re.sub(r"|(?:image|photo|picture|video)\s+(?:attached|sent|received)", "", sms)
    return sms

def removePII(sms: str) -> str:
    """
    Replace personal identifiable information from SMS message.
    - phone number, email
    - sin, ssn
    - credit/debit card
    """
    # # Phone: handles (416) 555-1234, 416-555-1234, +1 416.555.1234, +44 20 7946 0958
    country_code = r"(?:\+\d{1,3}[\s.\-]?)?"
    area_code = r"(?:\(?\d{2,4}\)?[\s.\-]?)"
    local_number = r"\d{2,4}[\s.\-]\d{2,4}"
    sms = re.sub(f"{country_code}{area_code}{local_number}", "", sms)

    # Email
    sms = re.sub(r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b", "", sms)

    # Canadian SIN: 123 456 789 or 123-456-789 (requires separator to avoid false positives)
    sms = re.sub(r"\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b", "", sms)

    # US SSN: 123-45-6789
    sms = re.sub(r"\b\d{3}-\d{2}-\d{4}\b", "", sms) # SSN

    # card pattern
    sms = re.sub(r"\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b", "", sms)
    
    return sms

def removePIIWithContext(sms: str) -> str:
    """
    Replace personal identifiable information from SMS message with placeholder text 
    to keep context for the large language model.
    - phone number, email
    - sin, ssn
    - credit/debit card
    """
    # # Phone: handles (416) 555-1234, 416-555-1234, +1 416.555.1234, +44 20 7946 0958
    country_code = r"(?:\+\d{1,3}[\s.\-]?)?"
    area_code = r"(?:\(?\d{2,4}\)?[\s.\-]?)"
    local_number = r"\d{2,4}[\s.\-]\d{2,4}"
    sms = re.sub(f"{country_code}{area_code}{local_number}", "[PHONE]", sms)

    # Email
    sms = re.sub(r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b", "[EMAIL]", sms)

    # Canadian SIN: 123 456 789 or 123-456-789 (requires separator to avoid false positives)
    sms = re.sub(r"\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b", "[ID]", sms)

    # US SSN: 123-45-6789
    sms = re.sub(r"\b\d{3}-\d{2}-\d{4}\b", "[ID]", sms) # SSN

    # card pattern
    sms = re.sub(r"\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b", "[CARD]", sms)
    
    return sms

# TODO remove names, mfa codes, addresses

# Text Preprocessing for NLP model
def keep_english(sms: str) -> str:
    """Remove any sequence of 2+ special characters and replace extra whitespace with 
    a single whitespace.
    TODO non-unicode emoji, combined emoji sequences
    """
    sms = re.sub(r"[\s\W]{2,}", "", sms)
    sms = re.sub(r"\s+", " ", sms).strip()

    return sms

# --- PII patterns ---

# Phone: handles (416) 555-1234, 416-555-1234, +1 416.555.1234, +44 20 7946 0958
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

# Canadian SIN: 123 456 789 or 123-456-789 (requires separator to avoid false positives)
_SIN_RE = re.compile(r"\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b")

# US SSN: 123-45-6789
_SSN_RE = re.compile(r"\b\d{3}-\d{2}-\d{4}\b")

# --- Media/MMS patterns ---

_MEDIA_TAG_RE = re.compile(
    r"\[(?:image|photo|picture|mms|video|audio|file|attachment|gif|sticker)\]"
    r"|<(?:image|photo|mms)>"
    r"|(?:image|photo|picture|video)\s+(?:attached|sent|received)",
    re.IGNORECASE,
)

# Emoji-only check: strip all emoji and see what's left
_EMOJI_RE = re.compile(
    "[\U00010000-\U0010ffff]|[\U0001F300-\U0001F9FF]",
    flags=re.UNICODE,
)

# Messages that are too trivial to be smishing (single-word acknowledgements)
_TRIVIAL_WORDS = frozenset({
    "ok", "okay", "yes", "no", "sure", "thanks", "ty", "k", "yep",
    "nope", "cool", "nice", "lol", "haha", "bye", "hi", "hey", "hello",
})


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def strip_pii(text: str) -> str:
    """Replace PII with labeled placeholders so context is preserved for AI."""
    text = _PHONE_RE.sub("[PHONE]", text)
    text = _EMAIL_RE.sub("[EMAIL]", text)
    text = _CARD_RE.sub("[CARD]", text)
    text = _SIN_RE.sub("[ID]", text)
    text = _SSN_RE.sub("[ID]", text)
    return text


def is_media_only(text: str) -> bool:
    """Return True if the message carries no readable text (photo/video MMS)."""
    stripped = text.strip()
    if not stripped:
        return True
    # Only flag as media-only when the message actually contains a media tag.
    if not _MEDIA_TAG_RE.search(stripped):
        return False
    without_tags = _MEDIA_TAG_RE.sub("", stripped).strip()
    without_emoji = _EMOJI_RE.sub("", without_tags).strip()
    return len(without_emoji) < 5


def is_trivial(text: str) -> bool:
    """Return True if the message is too short or generic to be smishing."""
    cleaned = text.strip().lower()
    if len(cleaned) < 5:
        return True
    words = re.sub(r"[^a-z\s]", "", cleaned).split()
    return len(words) <= 2 and all(w in _TRIVIAL_WORDS for w in words)


def should_skip(text: str) -> tuple[bool, str]:
    """Return (True, reason) if the message should not be analyzed."""
    if is_media_only(text):
        return True, "media_only"
    if is_trivial(text):
        return True, "trivial"
    return False, ""


def clean_for_llm(text: str) -> str:
    """Prepare SMS text for the LLM explainer.

    Strips PII and normalizes whitespace while keeping URLs and structure
    intact so the LLM has full semantic context.
    """
    text = strip_pii(text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def clean_for_distilbert(text: str) -> str:
    """Minimal preprocessing for DistilBERT.

    Only strips PII — transformers perform better on natural-language text
    without aggressive cleaning like stemming or stop-word removal.
    """
    return strip_pii(text).strip()


def clean_text(text: str) -> str:
    """Aggressive cleaning for rule-based scoring (legacy).

    Strips PII first, then lowercases, removes punctuation, drops stop words,
    and applies stemming. Do NOT use this for transformer model input.
    """
    text = strip_pii(text)
    text = text.lower()
    text = re.sub(r"http\S+", "URL", text)
    text = re.sub(r"[^a-z\s]", " ", text)
    words = text.split()
    words = [w for w in words if w not in _stop_words.ENGLISH_STOP_WORDS]
    words = [stemmer.stem(w) for w in words]
    return " ".join(words)