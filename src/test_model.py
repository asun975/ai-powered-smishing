import numpy as np
import os
import pandas as pd
import time
import torch
import sys

from datasets import Dataset
from tqdm import tqdm
from sklearn.metrics import accuracy_score, f1_score
from traceback import print_exception
from transformers import (
    pipeline,
    AutoTokenizer,
    AutoModelForSequenceClassification
)

BATCH_SIZE=32
MAX_LENGTH=128
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

def load_model():
    model_path = os.path.join('.', 'models', 'sms-spam-model-v2')

    if not os.path.exists(model_path):
            raise FileNotFoundError(
                f"Trained model not found at {model_path}. Run: python src/distilbert_model_prototype.py and python src/train_model.py"
            )
    tokenizer = AutoTokenizer.from_pretrained(model=model_path, local_files_only=True)
    model = AutoModelForSequenceClassification.from_pretrained(model=model_path, local_files_only=True)

    model.to(DEVICE)
    model.eval()

    return model, tokenizer, model_path

def load_dataset():
    dataset_name = os.path.join('data', 'kaggle_malicious_url_dataset', 'test_dataset.csv')
    test_size=2000

    print("Loading dataset...")

    # Create dataframe of 2000 random samples
    df = pd.read_csv(dataset_name)
    df = df.sample(n=test_size, random_state=42)
    df = df[['url', 'label']]
    df = df.dropna() # Drop rows with null

    # Convert to Hugging Face dataset
    dataset = Dataset.from_pandas(df)
    
    return dataset

def predict(text):
    classifier = load_model()

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

def main():
    try:
        # Test examples
        #predict("URGENT! Your account has been compromised. Click here now!")
        #predict("Hey, are we still meeting later?")
        #predict("You won $1000! Claim your prize now!")

        model, tokenizer, model_path = load_model()

        dataset = load_dataset()
        labels = dataset['label']

        classifier = pipeline(
            "text-classification",
            model=model,
            tokenizer=tokenizer
        )

        # Tokenize data
        urls = dataset['url']
        encodings = tokenizer(
            urls,
            truncation=True,
            padding="max_length",
            max_length=MAX_LENGTH,
            return_tensors="pt"
        )
    
        input_ids = encodings["input_ids"]
        attention_mask = encodings["attention_mask"]

        # Batch evaluation
        all_preds = []
        num_batches = int(np.ceil(len(urls) / BATCH_SIZE))
        print("Running inference benchmark...")

        # Measure inference time
        start_total = time.time()

        # what is this?
        if DEVICE=="cuda":
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

            all_preds.extend(predictions.cpu().numpy())

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

        accuracy = accuracy_score(labels, all_preds)
        f1 = f1_score(labels, all_preds)

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

        print(f"Model:                {model_path}")
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
    except Exception as e:
            print(f"An unexpected exception occured of type {type(e)}")
            print("*** print_exception:")
            print_exception(e, limit=2, file=sys.stdout)

if __name__ == "__main__":
    main()
