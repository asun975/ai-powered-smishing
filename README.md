# AI-Powered Smishing Detection

This repository contains a hybrid SMS phishing detection system built for a course project. It combines a fine-tuned DistilBERT classifier with rule-based URL and keyword analysis, then generates a short explanation for the user.

## What this version improves

This merged version keeps the strongest parts from the team branches:
- **Yugveer branch:** clean repo structure, training pipeline, local model loading
- **Gustavo branch:** better training metrics and clearer label handling
- **Rachna branch:** risk-score and end-user explanation idea
- **Ashley contribution:** explanation module concept

## Project structure

```text
ai-powered-smishing/
├── data/
│   └── spam.csv
├── src/
│   ├── distilbert_model.py
│   ├── llm_explainer.py
│   ├── pipeline.py
│   ├── preprocessing.py
│   └── train_model.py
├── requirements.txt
├── .gitignore
└── README.md
```

## Setup

```bash
pip install -r requirements.txt
```

## Train the model

```bash
python src/train_model.py
```

This saves the trained model locally to `models/distilbert/`.

## Run the detector

```bash
python src/pipeline.py
```

Example output:

```python
{
    'text': 'Your instagram account is hacked. Verify now: https://www.instagram.ca',
    'ml_prediction': 'SAFE',
    'ml_confidence': 0.995,
    'ml_score': 0.5,
    'rule_score': 80,
    'final_score': 32.3,
    'prediction': 'SAFE',
    'explanation': 'This message has multiple suspicious signals...'
}
```

## Why the hybrid approach helps

A pure ML model can miss brand impersonation and suspicious link patterns. A pure rule-based system can overflag harmless messages. This repo combines both:
- **ML score** from fine-tuned DistilBERT
- **Rule score** from suspicious words, URLs, and domain mismatch checks
- **Final prediction** from a weighted score

## Current limitations

- The dataset is relatively small.
- Trusted-domain logic is intentionally simple for the demo.
- The explanation module is template-based, not a full LLM.

## Suggested future work

- Add more smishing-specific training data
- Replace the explanation module with a lightweight local LLM
- Add a Streamlit or Flask frontend
- Save evaluation reports and confusion matrix plots

## Team

- **Yugveer Sidhu**
- Gustavo De Vera
- Rachna
- Ashley Sun

## Notes for GitHub

The trained model is not committed to the repo because it is large. After cloning, train it locally with:

```bash
python src/train_model.py
```
