import os
os.environ["KMP_DUPLICATE_LIB_OK"]="TRUE"

import pandas as pd
import torch
from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer,
    pipeline
)
from sklearn.metrics import accuracy_score, precision_recall_fscore_support

# -------------------------------
# 1. LOAD DATA
# -------------------------------
df = pd.read_csv("spam.csv")

# Rename columns if needed
df.columns = ["label", "message"]

# Convert labels to numeric
df["label"] = df["label"].map({"ham": 0, "spam": 1})

# Remove nulls
df = df.dropna()

# -------------------------------
# 2. CONVERT TO HF DATASET
# -------------------------------
dataset = Dataset.from_pandas(df)

# Train-test split
dataset = dataset.train_test_split(test_size=0.2)

# -------------------------------
# 3. TOKENIZATION
# -------------------------------
tokenizer = AutoTokenizer.from_pretrained("distilbert-base-uncased")

def tokenize(example):
    return tokenizer(
        example["message"],
        truncation=True,
        padding="max_length",
        max_length=128
    )

dataset = dataset.map(tokenize, batched=True)

dataset.set_format(
    type="torch",
    columns=["input_ids", "attention_mask", "label"]
)

# -------------------------------
# 4. LOAD MODEL
# -------------------------------
model = AutoModelForSequenceClassification.from_pretrained(
    "distilbert-base-uncased",
    num_labels=2
)

# -------------------------------
# 5. METRICS
# -------------------------------
def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = logits.argmax(axis=1)

    precision, recall, f1, _ = precision_recall_fscore_support(
        labels, preds, average="binary"
    )
    acc = accuracy_score(labels, preds)

    return {
        "accuracy": acc,
        "f1": f1,
        "precision": precision,
        "recall": recall
    }

# -------------------------------
# 6. TRAINING CONFIG
# -------------------------------
training_args = TrainingArguments(
    output_dir="./results",
    learning_rate=2e-5,
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    num_train_epochs=3,
    eval_strategy="epoch",
    save_strategy="epoch",
    logging_dir="./logs",
    load_best_model_at_end=True
)

# -------------------------------
# 7. TRAINER
# -------------------------------
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=dataset["train"],
    eval_dataset=dataset["test"],
    processing_class=tokenizer,
    compute_metrics=compute_metrics
)

# -------------------------------
# 8. TRAIN MODEL
# -------------------------------
trainer.train()

# -------------------------------
# 9. SAVE MODEL
# -------------------------------
model_path = "./sms-spam-model"
trainer.save_model(model_path)
tokenizer.save_pretrained(model_path)

# -------------------------------
# 10. LOAD FOR INFERENCE
# -------------------------------
classifier = pipeline(
    "text-classification",
    model=model_path,
    tokenizer=tokenizer
)

# -------------------------------
# 11. TEST EXAMPLES
# -------------------------------
def predict(text):
    result = classifier(text)[0]

    label = result["label"]
    score = result["score"]

    # Convert LABEL_0 / LABEL_1 → readable
    label_map = {
        "LABEL_0": "SAFE",
        "LABEL_1": "SPAM"
    }

    readable_label = label_map[label]

    # Risk score
    if readable_label == "SPAM":
        risk_score = score * 100
    else:
        risk_score = (1 - score) * 100

    return {
        "text": text,
        "prediction": readable_label,
        "confidence": round(score, 4),
        "risk_score": round(risk_score, 2)
    }

# Example tests
tests = [
    "URGENT! Your account has been compromised. Click here now!",
    "Hey, are we still meeting later?",
    "You won $1000! Claim your prize now!"
]

for t in tests:
    print(predict(t))

