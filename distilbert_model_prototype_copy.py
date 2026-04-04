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

import pandas as pd
import re
from datasets import Dataset
from nltk.stem import SnowballStemmer
from sklearn.feature_extraction import _stop_words

model_path = "./models/distilbert/distilbert-base-uncased"
#model_path = "./models/sms-spam-model1-0"
tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForSequenceClassification.from_pretrained(model_path)

# -------------------------------
# 1. LOAD DATA
# -------------------------------
df = pd.read_csv("spam.csv")

df=pd.read_csv(r"C:\Users\ashle\GitHub\ai-powered-smishing\dataset\SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION\Dataset_5971.csv")
# Show info about df
print(df.info())
print(df.columns)

# normalize labels
df_cleaned = df.copy()
df_cleaned['LABEL'] = df_cleaned['LABEL'].str.lower()
df_cleaned['LABEL'] = df_cleaned['LABEL'].apply(lambda x: re.sub('smishing', 'spam', x))

# Remove columns
df_cleaned.drop(columns=['URL', 'EMAIL', 'PHONE'], inplace=True)

# Remove rows with NaN
df_cleaned.dropna()

# Text preprocessing
# Rename columns if needed
df_cleaned.columns = ["label", "message"]

# Convert message column to lowercase
df_cleaned["message"] = df_cleaned["message"].str.lower()

# Convert labels to numeric - fix later
df_cleaned["label"] = df_cleaned["label"].map({"ham": 0, "spam": 1}).astype('int')

# Remove URLs
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: re.sub(r'http\S+', '', x))

# Remove email strings
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: re.sub(r'\w+@\w+\.\w+', '', x))

# Replace a tab, new line or any sequence of +2 whitespaces with 
# a single whitespace
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: re.sub(r"\\t\s+", ' ', x).strip())

# Remove non-lower case characters and non-whitespace
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: re.sub(r"[^a-z\s]", '', x))

# Remove english stop words
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: [word for word in x.split() if word not in _stop_words.ENGLISH_STOP_WORDS])

# Keep root word (Stemming/Lemmanization)
stemmer = SnowballStemmer('english')
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: [stemmer.stem(word) for word in x])

# Convert message column back to string
df_cleaned["message"] = df_cleaned["message"].apply(lambda x: ' '.join(x))

# Show first 5 rows of dataframe
print(df_cleaned.head())

# -------------------------------
# 2. CONVERT TO HF DATASET
# -------------------------------
dataset = Dataset.from_pandas(df_cleaned)

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
        num_train_epochs=3,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_dir="./logs",
        load_best_model_at_end=True,
        use_cpu=True,
        dataloader_pin_memory=False
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
    
    def check_save_path(model_path):
        path_to_save = model_path
        while os.path.exists(path_to_save):
            # Assuming model are saved to ./models/sms-spam-model-#
            version = int(path_to_save.split('-')[-1])
            path_to_save = os.path.join("models","sms-spam-model1-"+str(version+1))
            print(f"Checking path: {path_to_save}")

        return path_to_save
    
    saved_model = check_save_path('models/sms-spam-model1-0')
    trainer.save_model(saved_model)
    tokenizer.save_pretrained(saved_model)
    print(f"Model saved to {saved_model}")
    # -------------------------------
    # 10. LOAD FOR INFERENCE
    # -------------------------------
    classifier = pipeline(
        "text-classification",
        model=saved_model,
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
except Exception as e:
    print(e)
