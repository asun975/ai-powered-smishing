# 📱 AI-Powered Smishing Detection System

## 🚀 Overview

This project is an **AI-based smishing (SMS phishing) detection system** that analyzes text messages and classifies them as:

* ✅ **SAFE**
* ⚠️ **SPAM (Smishing attack)**

The system combines:

* 🤖 Machine Learning (DistilBERT)
* 📏 Rule-based detection
* 🧠 Explainable AI (text-based reasoning)

---

## 🧩 How It Works

The system follows a **hybrid detection pipeline**:

1. **Preprocessing**

   * Cleans text (lowercase, remove noise, stemming)

2. **Machine Learning Model**

   * Fine-tuned DistilBERT model trained on SMS spam dataset

3. **Rule-Based System**

   * Detects suspicious keywords (e.g., "urgent", "verify")
   * Analyzes URLs and domain trust

4. **Score Fusion**

   * Final Score = `0.7 × ML Score + 0.3 × Rule Score`

5. **Explanation Generator**

   * Provides human-readable reasoning for the decision

---

## 📂 Project Structure

```
ai-powered-smishing/
│
├── data/
│   └── spam.csv                # Dataset used for training
│
├── src/
│   ├── train_model.py         # Train ML model
│   ├── distilbert_model.py    # Prediction using trained model
│   ├── preprocessing.py       # Text cleaning
│   ├── pipeline.py            # Main detection pipeline
│   └── llm_explainer.py       # Explanation generator
│
├── requirements.txt
├── README.md
└── .gitignore
```

---

## ⚙️ Installation

### 1. Clone the repository

```bash
git clone https://github.com/asun975/ai-powered-smishing.git
cd ai-powered-smishing
```

### 2. Install dependencies

```bash
pip install -r requirements.txt
```

Or manually:

```bash
pip install torch transformers datasets accelerate pandas scikit-learn nltk
```

---

## 🧠 Train the Model

Run:

```bash
python src/train_model.py
```

This will:

* Load dataset from `data/spam.csv`
* Train DistilBERT model
* Save model locally in `models/distilbert`

⚠️ Note: The trained model is not included in the repo (to keep it lightweight).

---

## 🔍 Run the Detection System

```bash
python src/pipeline.py
```

### Example Output

```json
{
  "text": "Your account is locked. Verify now!",
  "ml_prediction": "SPAM",
  "ml_score": 85.3,
  "rule_score": 40,
  "final_score": 71.2,
  "prediction": "SPAM",
  "explanation": "This message is likely a smishing attack..."
}
```

---

## 🧪 Features

### 🤖 Machine Learning

* Fine-tuned DistilBERT for SMS classification

### 📏 Rule-Based Detection

* Keyword detection
* URL/domain analysis
* Trusted domain filtering

### ⚖️ Hybrid Scoring

* Combines ML and rule-based results for better accuracy

### 🧠 Explainability

* Generates explanations to justify predictions

---

## 🛠️ Technologies Used

* Python
* PyTorch
* Hugging Face Transformers
* Datasets
* NLTK
* Scikit-learn

---

## ⚠️ Limitations

* Small dataset may limit accuracy
* Rule-based logic is basic
* Explanation system is currently rule-based (not a full LLM yet)

---

## 🔮 Future Improvements

* Deploy as web app (Streamlit / Flask)
* Integrate real LLM (TinyLlama / Gemma)
* Improve phishing URL detection
* Expand dataset for better performance
* Real-time SMS filtering API

---

## 👥 Team Contributions

| Member        | Contribution                            |
| ------------- | --------------------------------------- |
|               |                                         |
|               |                                         |
|               |                                         |



---

## 📌 Summary

This project demonstrates a **hybrid AI approach** combining:

* Deep learning
* Rule-based logic
* Explainability

to build a practical **smishing detection system**.

---

## 📜 License

This project is for educational purposes.
