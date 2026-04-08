import re
from sklearn.feature_extraction import _stop_words
from nltk.stem import SnowballStemmer

stemmer = SnowballStemmer("english")


def clean_text(text: str) -> str:
    """Light preprocessing for rule-based checks or optional experiments.

    Note: We do NOT use this cleaned text for DistilBERT inference because
    transformer models usually work better on the original message.
    """
    text = text.lower()
    text = re.sub(r"http\S+", "URL", text)
    text = re.sub(r"[^a-z\s]", " ", text)
    words = text.split()
    words = [w for w in words if w not in _stop_words.ENGLISH_STOP_WORDS]
    words = [stemmer.stem(w) for w in words]
    return " ".join(words)
