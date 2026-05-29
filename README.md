# AI-Powered Smishing Detection

Our project is an Android application capable of performing real‑time analysis of incoming SMS messages to detect potential smishing (SMS phishing) attempts. Incoming SMS messages are evaluated using a text classification API that assigns a dynamic risk score, and a Large Language Model (LLM) API will generate clear, human‑readable explanations describing why the message was flagged. Based on the assessed risk level, the app can trigger alerts, quarantine suspicious messages, or allow the user to block the sender.

## Usage
The initial prototype focuses on individual modules only and does not represent a complete end‑to‑end use case. 
- Implemented broadcast receiver to read incoming messages using an emulator
- Classifier (DistilBERT) model trained in message text and URL that classifies SMS text as smishing or benign and assigns a risk score to the message.
- LLM (TinyLlama) provides a human-readable explanation of a sample message and risk score

### Set-up Project
- Download spam.csv from origin: Gustavo/spam.csv and save to data/spam.csv

```bash
git clone https://github.com:asun975/ai-powered-smishing.git
cd ai-powered-smishing

# Install dependencies
pip install -r requirements.txt
```

```bash
# Train the DistilBERT model
python src/distilbert_model_prototype.py
python src/train_model.py

# Create samples for testing
python src/prepare_data.py

# Test sms-spam-model-v2
python src/test_model.py

# Generate explanation for assigned risk score with Tiny Llama
python src/tinyllama.py
```
This trains a base DistilBERT model on the SMS Spam Collection dataset and saves the model locally to models/sms-spam-model/

train_model.py provides additional training on URLs using a kaggle dataset for malicious URL detection. This model is saved to models/sms-spam-model-v2/

## Features (in progress)

- 🔍 **Real‑time SMS scanning**  
- 🧠 **Risk scoring via classification API**  
- 💬 **LLM‑generated explanations** for flagged messages  
- 🛡️ **Quarantine simulation** for high‑risk messages  
- 🚫 **Blocking simulation** (UI‑level only; no OS‑level blocking)  
- 🔔 **User alerts** for suspicious content  
- 🔒 **Data‑cleaning module** (Preprocessing SMS message for distilBERT. Remove sensitive data and PII)

## Project Structure

```
.
├── app/
│   ├── AndroidManifest.xml
│   ├── MainActivity.kt
│   └── activity_main.xml
├── src/
│   ├── distilbert_model_prototype.py
│   ├── test_model.py
│   ├── tinyllama.py
│   └── train_model.py
├── .gitignore
├── README.md
└── requirements.txt
```

## Known issues or limitations

- Model bias due to limited or outdated dataset for mobile smishing and URL detection
- False positive/negatives and LLM hallucination
- Mobile resource contraints like battery, storage, and CPU/GPU memory
- Android limitations on non-default apps ability to delete and block messages.

## Relevant Links
### Dataset
Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection

**Test dataset sourced from**

- https://www.kaggle.com/datasets/galactus007/sms-smishing-collection-data-set
- https://data.mendeley.com/datasets/f45bkkt8pr/1

### DistilBERT transformer model
- [DistilBERT docs](https://huggingface.co/docs/transformers/en/model_doc/distilbert?usage=Pipeline#transformers.DistilBertModel)
- [transformers installation](https://huggingface.co/docs/transformers/en/installation)

### Tiny Llama LLM
https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0

## Team
- Rachna Alleear
- Gustavo De Vera Teixeira
- Ashley Sun
- Yugveer Sidhu
