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

N_SIZE=1000
BATCH_SIZE=32
MAX_LENGTH=128
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_NAME = 'models/sms-spam-model-v2'
DATASET_PATH = 'data/test_samples.csv'

def load_model():

    if not os.path.exists(MODEL_NAME):
            raise FileNotFoundError(
                f"Trained model not found at {MODEL_NAME}. Run: python src/distilbert_model_prototype.py and python src/train_model.py"
            )
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, local_files_only=True)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME, local_files_only=True)

    model.to(DEVICE)
    model.eval()

    return model, tokenizer

def load_dataset(data, sample_size):
    
    print("Loading dataset...")
    if not os.path.exists(DATASET_PATH):
            raise FileNotFoundError(
                f"Test samples not found at {DATASET_PATH}. Run: python src/prepare_data.py"
            )

    # Create dataframe of n random samples
    df = pd.read_csv(data)
    df = df.sample(n=sample_size, random_state=42)
    # Convert labels to numeric
    df["label"] = df["label"].map({"ham": 0, "smishing": 1})

    # Convert to Hugging Face dataset
    dataset = Dataset.from_pandas(df)
    
    return dataset

def main():
    try:
        # Load model and tokenizer
        model, tokenizer = load_model()
        
        # HF text-classification
        classifier = pipeline("text-classification", device=DEVICE, model=model, tokenizer=tokenizer)

        # Load dataset
        dataset = load_dataset(DATASET_PATH, N_SIZE)
        
        # Tokenize features
        dataset = dataset.map(
            lambda examples: tokenizer(
                examples["text"], 
                truncation=True,
                padding="max_length",
                max_length=MAX_LENGTH,
                return_tensors="pt")
            )
        
        batched_dataset = dataset.batch(batch_size=BATCH_SIZE)

        texts = dataset['text']
        labels = dataset['label']
        all_results = []
        all_preds = []

        # Measure inference time
        print("Running inference benchmark...")
        start_total = time.time()

        for batch in batched_dataset:

            start_time = time.time()
            if DEVICE=="cuda":
                torch.cuda.reset_peak_memory_stats()

            for text in batch['text']:
                result = classifier(text)[0]
                label = {"LABEL_0": 0, "LABEL_1": 1}[result["label"]]
                score = result["score"]
                risk_score = score * 100 if label == "SPAM" else (1 - score) * 100
                results_dict = {
                    'message': text,
                    'pred': label,
                    'confidence':score,
                    'risk_score': risk_score
                }
                all_preds.append(label)

            if DEVICE == "cuda":
                    torch.cuda.synchronize()

            end_time = time.time() 

            all_results.append(results_dict)
            batch_time = end_time - start_time

            print(
                f"| Batch Size: {len(batch['text'])} "
                f"| Time: {batch_time:.4f}s"
            )

        end_total = time.time()

        # Metrics
        accuracy = accuracy_score(labels, all_preds)
        f1 = f1_score(labels, all_preds)
        total_time = end_total - start_total
        samples_per_second = len(texts) / total_time

        # Device memory
        if DEVICE == "cuda":
            peak_memory = torch.cuda.max_memory_allocated() / 1024**2
        else:
            peak_memory = None
        
        # Results
        print("\n" + "=" * 60)
        print("BENCHMARK RESULTS")
        print("=" * 60)

        print(f"Model:                {MODEL_NAME}")
        print(f"Device:               {DEVICE}")
        print(f"Dataset Size:         {len(texts)}")
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
