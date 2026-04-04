# Author: GustavoDeVera
# modified by Ashley Sun

import os
os.environ["KMP_DUPLICATE_LIB_OK"]="TRUE"
os.environ["HF_DATASETS_OFFLINE"] = '1'
os.environ["TRANSFORMERS_OFFLINE"] = '1'

import torch
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer,
    pipeline
)
from sklearn.metrics import accuracy_score, precision_recall_fscore_support

import matplotlib.pyplot as plt
import pandas as pd
import re
from datasets import Dataset
from nltk.stem import SnowballStemmer
from sklearn.feature_extraction import _stop_words
from sklearn.metrics import confusion_matrix
import numpy as np
import seaborn as sns

#model_path = "./models/distilbert/distilbert-base-uncased"
model_path = "./models/sms-spam-model-0"
tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForSequenceClassification.from_pretrained(model_path)

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

# 1.1 Data Preprocessing
# could make a function for this or use distilBERT data cleaning methods if any

# Show info about df
print(df.info())

# Convert Message column to lowercase
df["message"] = df["message"].str.lower()

# Remove https URLs
#df["message"] = df["message"].apply(lambda x: re.sub(r'http\S+', '', x))

# Replace a tab, new line or any sequence of +2 whitespaces with 
# a single whitespace
df["message"] = df["message"].apply(lambda x: re.sub(r"\\t\s+", ' ', x).strip())

# Remove non-lower case characters and whitespace
df["message"] = df["message"].apply(lambda x: re.sub(r"[^a-z\s]", '', x))

# Remove english stop words
df["message"] = df["message"].apply(lambda x: [word for word in x.split() if word not in _stop_words.ENGLISH_STOP_WORDS])

# Keep root word (Stemming/Lemmanization)
stemmer = SnowballStemmer('english')
df["message"] = df["message"].apply(lambda x: [stemmer.stem(word) for word in x])

# Convert message column back to string
df["message"] = df["message"].apply(lambda x: ' '.join(x))

# Show first 5 rows of dataframe
print(df.head())

# -------------------------------
# 2. CONVERT TO HF DATASET
# -------------------------------
dataset = Dataset.from_pandas(df)

# Train-test split
dataset = dataset.train_test_split(test_size=0.2)

try:
    # -------------------------------
    # 3. TOKENIZATION
    # -------------------------------

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
        columns=["input_ids", "attention_mask", "label"],

    )

    # -------------------------------
    # 4. LOAD MODEL
    # -------------------------------
    model = AutoModelForSequenceClassification.from_pretrained(
        model_path,
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
        num_train_epochs=1,
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
    #trainer.train()

    # -------------------------------
    # 9. SAVE MODEL
    # -------------------------------
    """
    def check_save_path(model_path):
        path_to_save = model_path
        while os.path.exists(path_to_save):
            # Assuming model_path follows ./models/sms-spam-model-#
            version = int(path_to_save.split('-')[-1])
            path_to_save = os.path.join("models","sms-spam-model-"+str(version+1))
            print(f"Checking path: {path_to_save}")

        return path_to_save
    
    saved_model = check_save_path(model_path)
    trainer.save_model(saved_model)
    tokenizer.save_pretrained(saved_model)
    print(f"Model saved to {saved_model}")
    """
    # -------------------------------
    # 10. LOAD FOR INFERENCE
    # -------------------------------
    classifier = pipeline(
        "text-classification",
        model=model,
        tokenizer=tokenizer
    )

    # Plot confusion matrix on test set
    import matplotlib
    matplotlib.use("Agg")
    predictions=trainer.predict(dataset['train'])    # Get predictions from trainer

    # Convert logits to predicted labels
    y_pred=np.argmax(predictions.predictions, axis=1)
    y_true=predictions.label_ids
    
    cm = confusion_matrix(y_true, y_pred)

    sns.heatmap(cm, annot=True, fmt="d", cmap="crest", xticklabels=["SAFE", "SPAM"], yticklabels=["SAFE", "SPAM"])
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.title("Confusion Matrix")
    plt.savefig("plots/cm-train.png")
except Exception as e:
    print(e)
"""
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
"""
