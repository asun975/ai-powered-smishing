# AI-Powered Smishing Detection

Our project is an Android application capable of performing real‑time analysis of incoming SMS messages to detect potential smishing (SMS phishing) attempts. Incoming SMS messages are evaluated using a text classification API that assigns a dynamic risk score, and a Large Language Model (LLM) API will generate clear, human‑readable explanations describing why the message was flagged. Based on the assessed risk level, the app can trigger alerts, quarantine suspicious messages, or allow the user to block the sender.
## Usage

### Features
- 🔍 **Real‑time SMS scanning**
- 🔒 **Data‑cleaning module** (text preprocessing and data sanitization)
- 🧠 **Risk scoring via classification API**
- **URL sandbox analysis**
- 💬 **LLM‑generated explanations** for flagged messages
- 🔔 **User alerts** for suspicious content
- 🛡️ **Quarantine simulation** for high‑risk messages
- 🚫 **Blocking suspicious senders** (user prompted blocking)

### Model Evaluation:
The detection model, sms-spam-model-v2, was tested using benign and smishing messages from the Kaggle dataset SMS Smishing Collection Data Set, and SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION dataset from Mendeley. The test dataset was preprocessed and sanitized to closely replicate the real conditions of SMS messages passed into our model

We evaluated the model using scikit learn metrics for accuracy, F1 score, precision, recall and a confusion matrix.
- **Accuracy**: 94.6%
- **F1 Score**: 96.0%
- **Precision**: 94.5%
- **Recall**: 97.6%

### Risk Score System
Risk score is derived from the confidence level and label returned by classifier. 
```
private fun getRiskScore(label: String, confidence: Float): Pair<Float, String> { 
    val riskScore = if (label == "SPAM") confidence else (1 - confidence) 
    val riskLevel = when { 
        riskScore > 0.75 -> "HIGH" 
        riskScore >= 0.30 -> "MEDIUM" 
        else -> "LOW" 
    } 
    return Pair(riskScore, riskLevel) 
} 
```
#### How the confidence score is calculated by the classifier model
When an SMS message is passed through the model, it is first tokenized into smaller units (tokens), which are then embedded into numerical vectors. These embeddings are processed through multiple transformer layers that use learned weights and attention mechanisms to capture contextual relationships between words, such as urgency, intent, and suspicious patterns. The final layer of the model outputs raw prediction values called logits, which are then passed through a SoftMax function to convert them into probabilities. The highest probability corresponds to the predicted class, and this value is reported as the confidence score. Therefore, the confidence score is influenced by the model’s learned parameters (weights and biases), the contextual meaning of the input text, and the relative strength of features such as keywords, structure, and semantic patterns identified during training. 

## Project Structure
```
.
├── hugging-face/
│   ├── distilbert/
│   │   ├── app.py
│   │   ├── Dockerfile
│   │   └── requirements.txt
│   └── groq-llama/
│       ├── app.py
│       ├── Dockerfile
│       └── requirements.txt
├── smishingdetection
├── src/
│   ├── distilbert_model_prototype.py
│   ├── prepare_data_nlp.py
│   ├── preprocessing.py
│   ├── test_model.py
│   ├── test_preprocessing.py
│   └── train_model.py
├── .gitignore
├── README.md
└── requirements.txt
```

## Set-up Project
```bash
git clone https://github.com:asun975/ai-powered-smishing.git
cd ai-powered-smishing

# Install dependencies
pip install -r requirements.txt
```

Save training datasets to ai-powered-smishing/data/
- Download spam.csv from origin: Gustavo/spam.csv
- Download train_dataset.csv from https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection

```bash
# Train the DistilBERT model
python src/distilbert_model_prototype.py
python src/train_model.py

# Generate explanation for assigned risk score with Tiny Llama
python src/tinyllama.py
```
This trains a base DistilBERT model on the SMS Spam Collection dataset and saves the model locally to models/sms-spam-model/

train_model.py provides additional training on URLs using a kaggle dataset for malicious URL detection. This model is saved to models/sms-spam-model-v2/

### Set-up Hugging Face Spaces API
#### Set-up Classifier API
1. Create Huggingface Space Account: https://huggingface.co/spaces
2. Create Huggingface Spaces for the classifier model and LLM
3. Update app.py from hugging-face/groq-llama and hugging-face/distilbert
- Add secret: HF_TOKEN = your HF token
4. Deploy (5-10 min build time)

**Classifier API Endpoint**
POST /classify
- Expects: JSON {"text": "message"}
- Returns: {"label": "SPAM"/"SAFE", "confidence": 0.95}

**LLM API Endpoint** 
POST /explain
- Expects: JSON {"text": "cleaned SMS text", "classification": "SPAM" or "SAFE", "risk_score": 0.87 }
- Returns: JSON { "explanation": explanation, "classification": classification, "risk_score": risk_score, "version": "model_version"}

## Android App
The `smishingdetection/` folder contains the Android Studio project source.

### Setup in Android Studio

1. Import the project `ai-power-smishing/smishingdetection`
2. Create app.properties in project root and add API endpoints for the classifier, LLM and URL APIs

```
## This file loads your custom API urls for the classifier model and LLM
#
# This file should *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.

CLASSIFIER_API_URL = "your api URL for classifier"
LLM_API_URL = "your api URL for LLM"
SCAN_API_URL = "http://10.0.2.2:8000"
```

3. Sync Gradle
4. Make sure the Python API is running on your PC (`ai-powered-smishing/fastapi-url-analyzer/api.py`)
5. Click the green **Run** button → select your emulator

## Known issues or limitations
- Model bias due to limited or outdated dataset for mobile smishing and URL detection
- False positive/negatives and LLM hallucination
- Mobile resource constraints like battery, storage, and CPU/GPU memory
- Android limitations on non-default apps ability to delete and block messages.

## Acknowledgements
### Datasets
- Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

- mishra, sandhya; Soni, Devpriya (2022), “SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION”, Mendeley Data, V1, doi: 10.17632/f45bkkt8pr.1

- https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection

- https://www.kaggle.com/datasets/galactus007/sms-smishing-collection-data-set


### DistilBERT transformer model
- [DistilBERT docs](https://huggingface.co/docs/transformers/en/model_doc/distilbert?usage=Pipeline#transformers.DistilBertModel)
- [transformers installation](https://huggingface.co/docs/transformers/en/installation)

### Tiny Llama LLM
https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0

### Llama 4 Scout - Groq
https://console.groq.com/docs/model/meta-llama/llama-4-scout-17b-16e-instruct

## Team
- Rachna Alleear
- Gustavo De Vera Teixeira
- Ashley Sun
- Yugveer Sidhu
