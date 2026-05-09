# AI-Powered Smishing Detection
The objective of this project is an AI-powered smishing detection for android-based mobile devices. Our solution will identify smishing messages in real-time and present a risk score and explanation for the user.
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
- Currently evaluating feasibilty of local models inference on mobile devices

## Features completed


## Features still in progress
- Risk score and confidence level 
- User feeback module using a LLM and chain of thought reasoning
- App features: quarantine, blocking, delete message
## Known issues or limitations
- Model bias due to limited or outdated dataset for mobile smishing and URL detection
- False positive/negatives and LLM hallucination
- On-device performance for model inference
- Mobile resource contraints like battery, storage, and CPU/GPU memory
## Relevant Links
### DistilBERT transformer model
- [DistilBERT docs](https://huggingface.co/docs/transformers/en/model_doc/distilbert?usage=Pipeline#transformers.DistilBertModel)
- [transformers installation](https://huggingface.co/docs/transformers/en/installation)

Install the CPU-only version of transformers:
```
pip install torch --index-url https://download.pytorch.org/whl/cpu
pip install transformers
```
### Datasets
Almeida, T. & Hidalgo, J. (2011). SMS Spam Collection [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C5CC84.

mishra, sandhya; Soni, Devpriya (2022), “SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION”, Mendeley Data, V1, doi: 10.17632/f45bkkt8pr.1
### Tiny Llama
[https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0](https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0)
