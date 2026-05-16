# AI-Powered Smishing Detection

Our project is an Android application capable of performing real‑time analysis of incoming SMS messages to detect potential smishing (SMS phishing) attempts. Incoming SMS messages are evaluated using a text classification API that assigns a dynamic risk score, and a Large Language Model (LLM) API will generate clear, human‑readable explanations describing why the message was flagged. Based on the assessed risk level, the app can trigger alerts, quarantine suspicious messages, or allow the user to block the sender.

## Usage
The initial prototype focuses on individual modules only and does not represent a complete end‑to‑end use case. 
- Implemented broadcast receiver to read incoming messages using an emulator
- Trained a pretrained distilbert model from hugging face
- LLM (TinyLlama) provides a human-readable explanation of a sample message and risk score

### Set-up Project

```bash
git clone https://github.com:asun975/ai-powered-smishing.git
cd ai-powered-smishing

# Install dependencies
pip install -r requirements.txt

# Train the model
python src/distilbert_model_prototype.py
python src/train_model.py
```
## Features (in progress)

- 🔍 **Real‑time SMS scanning**  
- 🧠 **Risk scoring via classification API**  
- 💬 **LLM‑generated explanations** for flagged messages  
- 🛡️ **Quarantine simulation** for high‑risk messages  
- 🚫 **Blocking simulation** (UI‑level only; no OS‑level blocking)  
- 🔔 **User alerts** for suspicious content  
- 🔒 **Data‑cleaning module** (remove sensitive data and PII)

## Project Structure

```
.
├── app/
│   ├── AndroidManifest.xml
│   ├── MainActivity.kt
│   └── activity_main.xml
├── src/
│   ├── distilbert_model_prototype.py
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

## Acknowledgements
### Dataset
Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection

## Team
- Rachna Alleear
- Gustavo De Vera Teixeira
- Ashley Sun
- Yugveer Sidhu
