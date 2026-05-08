# AI-Powered Smishing Detection
The objective of this project is an AI-powered smishing detection for android-based mobile devices. Our project will perform real-time analysis of SMS messages and provide user feedback in the form of a risk score for smishing content. 
## Setup
-  Install Android Studio
```
pip -r requirements.txt
```
## Train model
This saves the trained model locally to models/sms-spam-model/
```
python src/distilbert_model_prototype.py
```
Additional training on smishing URLs 
## Current project status
- trained intial model using pretrained distilbert model from hugging face
- initial testing for LLM performance
### Dataset
Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

Hannousse, Abdelhakim; Yahiouche, Salima (2021), “Web page phishing detection”, Mendeley Data, V3, doi: 10.17632/c2gw7fy2j4.3

https://www.kaggle.com/datasets/pilarpieiro/tabular-dataset-ready-for-malicious-url-detection

## Features completed


## Features still in progress
- risk score and confidence level 
- user feeback module using a LLM and chain of thought reasoning
- app features: quarantine, blocking, delete message
## Known issues or limitations
- the current NLP model is limited by a small dataset for smishing content
## Relevant Links
