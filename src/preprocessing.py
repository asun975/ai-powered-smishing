#preprocessing
import re
from nltk.stem import SnowballStemmer
from sklearn.feature_extraction import _stop_words

stemmer = SnowballStemmer('english')

def clean_text(text):
    text = text.lower()
    text = re.sub(r"http\S+", "URL", text)
    text = re.sub(r"[^a-z\s]", "", text)

    words = text.split()
    words = [w for w in words if w not in _stop_words.ENGLISH_STOP_WORDS]
    words = [stemmer.stem(w) for w in words]

    return " ".join(words)