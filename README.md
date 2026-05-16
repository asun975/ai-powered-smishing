# AI-Powered Smishing Detection
This project delivers an Android application capable of performing real‑time analysis of incoming SMS messages to detect potential smishing (SMS phishing) attempts. Incoming text is evaluated using a text classification API that assigns a dynamic risk score, and a Large Language Model (LLM) API generates clear, human‑readable explanations describing why the message was flagged. Based on the assessed risk level, the app can trigger alerts, quarantine suspicious messages, or allow the user to block the sender.
## Setup
-  Requires Android Studio
```
# Setup environment to train models
pip -r requirements.txt
```
## Train model
```
python src/distilbert_model_prototype.py
python src/train_model.py
```
This trains a base DistilBERT model on the SMS Spam Collection dataset and saves the model locally to models/sms-spam-model/

In addition, train_model.py provides training on URLs using a kaggle dataset for malicious URL detection. This model is saved to models/sms-spam-model-v2/
## Dataset
Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection
## Current project status
- Trained intial model using pretrained distilbert model from hugging face
- Completed initial testing for LLM reasoning using TinyLLama


## Features completed


## Features still in progress
- Risk score and confidence level 
- User feeback module using a LLM and chain of thought reasoning
- App features: notify, quarantine, blocking message
## Known issues or limitations
- Model bias due to limited or outdated dataset for mobile smishing and URL detection
- False positive/negatives and LLM hallucination
- Mobile resource contraints like battery, storage, and CPU/GPU memory
- Android limitations for non-default apps to delete or block messages.
## Relevant Links
