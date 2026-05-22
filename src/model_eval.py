# benchmark_transformer.py
#
# Benchmarking + inference timing + batch evaluation
# for Hugging Face Transformer models
#
# Features:
# - GPU / CPU benchmarking
# - Batch inference timing
# - Throughput measurement
# - Accuracy / F1 evaluation
# - Memory usage
# - Works with any text classification model
#
# Install:
# pip install transformers datasets torch scikit-learn tqdm

import os
import pandas as pd
import time
import torch
import numpy as np

from tqdm import tqdm
from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
)
from sklearn.metrics import accuracy_score, f1_score


# =========================================================
# CONFIG
# =========================================================

MODEL_NAME = os.path.join('models', 'sms-spam-model-v2')

# Use testing data set from Kaggle (URL)
DATASET_NAME = os.path.join('data', 'kaggle_malicious_url_dataset', 'test_dataset.csv')

BATCH_SIZE = 32
MAX_LENGTH = 128

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"


# =========================================================
# LOAD MODEL
# =========================================================

print(f"\nLoading model: {MODEL_NAME}")
print(f"Using device: {DEVICE}")

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)

model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)
model.to(DEVICE)
model.eval()


# =========================================================
# LOAD DATASET
# =========================================================

print("\nLoading dataset...")

df = pd.read_csv(DATASET_NAME)
df = df.sample(n=2000, random_state=42) # smaller subset for quick benchmarking

df = df[["url", "label"]] # Use url and label column only
df = df.dropna() # drop rows with null values

# Convert to HF Dataset
dataset = Dataset.from_pandas(df)
urls = dataset["url"]
labels = dataset["label"]

print(f"Loaded {len(urls)} samples")


# =========================================================
# TOKENIZATION
# =========================================================

print("\nTokenizing dataset...")

encodings = tokenizer(
    urls,
    truncation=True,
    padding=True,
    max_length=MAX_LENGTH,
    return_tensors="pt"
)

input_ids = encodings["input_ids"]
attention_mask = encodings["attention_mask"]


# =========================================================
# BATCH EVALUATION
# =========================================================

all_predictions = []

num_batches = int(np.ceil(len(urls) / BATCH_SIZE))

print("\nRunning inference benchmark...")

start_total = time.time()

if DEVICE == "cuda":
    torch.cuda.reset_peak_memory_stats()

for i in tqdm(range(num_batches)):

    start_idx = i * BATCH_SIZE
    end_idx = min((i + 1) * BATCH_SIZE, len(urls))

    batch_input_ids = input_ids[start_idx:end_idx].to(DEVICE)
    batch_attention = attention_mask[start_idx:end_idx].to(DEVICE)

    # Measure inference time
    start_time = time.time()

    with torch.no_grad():
        outputs = model(
            input_ids=batch_input_ids,
            attention_mask=batch_attention
        )

    if DEVICE == "cuda":
        torch.cuda.synchronize()

    end_time = time.time()

    logits = outputs.logits
    predictions = torch.argmax(logits, dim=-1)

    all_predictions.extend(predictions.cpu().numpy())

    batch_time = end_time - start_time

    print(
        f"Batch {i+1}/{num_batches} "
        f"| Batch Size: {end_idx - start_idx} "
        f"| Time: {batch_time:.4f}s"
    )

end_total = time.time()

# =========================================================
# METRICS
# =========================================================

accuracy = accuracy_score(labels, all_predictions)
f1 = f1_score(labels, all_predictions)

total_time = end_total - start_total
samples_per_second = len(urls) / total_time

# =========================================================
# MEMORY USAGE
# =========================================================

if DEVICE == "cuda":
    peak_memory = torch.cuda.max_memory_allocated() / 1024**2
else:
    peak_memory = None


# =========================================================
# RESULTS
# =========================================================

print("\n" + "=" * 60)
print("BENCHMARK RESULTS")
print("=" * 60)

print(f"Model:                {MODEL_NAME}")
print(f"Device:               {DEVICE}")
print(f"Dataset Size:         {len(urls)}")
print(f"Batch Size:           {BATCH_SIZE}")
print(f"Max Sequence Length:  {MAX_LENGTH}")

print("\nEvaluation Metrics")
print("-" * 30)

print(f"Accuracy:             {accuracy:.4f}")
print(f"F1 Score:             {f1:.4f}")

print("\nPerformance Metrics")
print("-" * 30)

print(f"Total Inference Time: {total_time:.4f} sec")
print(f"Throughput:           {samples_per_second:.2f} samples/sec")

if peak_memory is not None:
    print(f"Peak GPU Memory:      {peak_memory:.2f} MB")

print("=" * 60)
#########################
import os
from transformers import pipeline

model_path = os.path.join('.', 'models', 'sms-spam-model-v2')

if not os.path.exists(model_path):
        raise FileNotFoundError(
            f"Trained model not found at {model_path}. Run: python src/distilbert_model_prototype.py and python src/train_model.py"
        )

classifier = pipeline(
    "text-classification",
    model=model_path
)

def predict(text):
    result = classifier(text)[0]

    label_map = {
        "LABEL_0": "SAFE",
        "LABEL_1": "SPAM"
    }

    label = label_map[result["label"]]
    score = result["score"]

    risk_score = score * 100 if label == "SPAM" else (1 - score) * 100

    print(f"\nMessage: {text}")
    print(f"Prediction: {label}")
    print(f"Confidence: {score:.4f}")
    print(f"Risk Score: {risk_score:.2f}")

# Test examples
predict("URGENT! Your account has been compromised. Click here now!")
predict("Hey, are we still meeting later?")
predict("You won $1000! Claim your prize now!")
