# AI-Powered Smishing Detection

This repository contains a hybrid SMS phishing detection system built for a course project. It combines a fine-tuned DistilBERT classifier with rule-based URL and keyword analysis, then generates a short plain-English explanation for the user.

## What this version improves

This merged version keeps the strongest parts from the team branches:
- **Yugveer branch:** clean repo structure, training pipeline, local model loading, preprocessing, API edge case handling
- **Gustavo branch:** better training metrics and clearer label handling
- **Rachna branch:** risk-score and end-user explanation idea
- **Ashley contribution:** explanation module concept

## Project structure

```text
ai-powered-smishing/
├── app/
│   ├── MainActivity.kt
│   ├── AndroidManifest.xml
│   └── activity_main.xml
├── data/
│   └── spam.csv
├── src/
│   ├── api.py
│   ├── distilbert_model.py
│   ├── llm_explainer.py
│   ├── pipeline.py
│   ├── preprocessing.py
│   ├── tinyllama.py
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

This saves the trained model locally to `models/distilbert/`. The model is not committed to the repo because of its file size — you must train it locally after cloning.

## Run the detector (CLI)

```bash
python src/pipeline.py
```

Runs six built-in example messages then opens an interactive prompt where you can type any SMS and see the result:

```
SMS > Your CIBC account is locked. Verify now: http://secure-cibc.xyz

  Cleaned     : Your [PHONE] account is locked. Verify now: http://secure-cibc.xyz
  Prediction  : SPAM
  Final Score : 82.0 / 100
  ML (SPAM, conf 97.50%)  Rule score: 70
  Explanation : This message has multiple suspicious signals...
```

## Run the API server

```bash
python src/api.py
```

Starts a FastAPI server on `http://0.0.0.0:8000`. The Android app connects to this server to analyze incoming SMS messages.

### Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Check if the server is running |
| POST | `/analyze` | Analyze an SMS message |

### Example request

```bash
curl -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{"message": "URGENT: Your bank account has been locked. Verify now: http://secure-login.xyz"}'
```

### Example response

```json
{
  "message": "URGENT: Your bank account has been locked. Verify now: http://secure-login.xyz",
  "prediction": "SPAM",
  "risk_score": 82.0,
  "ml_prediction": "SPAM",
  "ml_confidence": 0.975,
  "ml_score": 100.0,
  "rule_score": 50.0,
  "explanation": "This message has multiple suspicious signals...",
  "skipped": false,
  "skip_reason": ""
}
```

### API edge cases handled (SCRUM-35)

| Input | Response |
|---|---|
| Empty or whitespace message | 400 — Message cannot be empty |
| Message over 1000 characters | 400 — Message too long |
| Media-only or trivial message (e.g. "ok", "[Image]") | `skipped: true`, `prediction: SAFE`, `risk_score: 0` |
| Model files not found on server | 503 — Model not available |
| Unexpected server error | 500 — Internal server error (details logged server-side only) |

## Data Cleaning & Preprocessing (Week 3 — Yugveer)

Before any message reaches the ML model or LLM explainer, it goes through a preprocessing pipeline in `src/preprocessing.py`.

### What gets filtered out

| Type | Example | Result |
|---|---|---|
| Empty message | `""` | Skipped — `media_only` |
| Photo / MMS | `[Image]`, `[Video]` | Skipped — `media_only` |
| Trivial message | `"ok"`, `"yes thanks"`, `"hey"` | Skipped — `trivial` |

### What gets masked (PII stripping)

Sensitive information is replaced with labeled placeholders before being sent to any model, so no personal data leaks through.

| PII type | Example input | Output |
|---|---|---|
| Phone number | `Call 416-555-1234` | `Call [PHONE]` |
| Email address | `email@phish.com` | `[EMAIL]` |
| Credit/debit card | `4111 1111 1111 1111` | `[CARD]` |
| SIN / SSN | `123 456 789` | `[ID]` |

### How to verify it yourself

Run the full pipeline:

```bash
python src/pipeline.py
```

Type any SMS at the interactive prompt and see how it is cleaned before scoring. Smishing messages still get detected because keywords and URLs are preserved — only personal data is masked.

## Why the hybrid approach helps

A pure ML model can miss brand impersonation and suspicious link patterns. A pure rule-based system can overflag harmless messages. This repo combines both:
- **ML score** from fine-tuned DistilBERT (60% weight)
- **Rule score** from suspicious words, URLs, and domain mismatch checks (40% weight)
- **Final prediction** from the weighted combined score (threshold: 35/100)

## Android app

The `app/` folder contains the Android component:
- **MainActivity.kt** — registers a broadcast receiver that intercepts incoming SMS messages and sends them to the Python API for analysis
- **AndroidManifest.xml** — declares the `RECEIVE_SMS` permission
- **activity_main.xml** — UI layout that displays the SMS text and analysis result

## Current limitations

- The dataset is relatively small.
- Trusted-domain logic is intentionally simple for the demo.
- TinyLlama (`tinyllama.py`) is included but the default explainer (`llm_explainer.py`) is still template-based — full LLM integration is planned.

## Suggested future work

- Add more smishing-specific training data
- Wire TinyLlama into the pipeline as the active explainer
- Add a Streamlit or Flask frontend
- Save evaluation reports and confusion matrix plots

## Team

- **Yugveer Sidhu**
- Gustavo De Vera
- Rachna
- Ashley Sun
