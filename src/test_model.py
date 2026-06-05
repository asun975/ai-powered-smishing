import numpy as np
import os
import pandas as pd
import time
import torch

from datasets import Dataset
from sklearn.metrics import (
    accuracy_score, 
    f1_score, 
    recall_score, 
    precision_score,
    confusion_matrix
)
from transformers import (
    pipeline,
    AutoTokenizer,
    AutoModelForSequenceClassification
)

from preprocessing import sanitize_text, removeUrl, text_preprocess, remove_special_char
#TODO logging results
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
                f"Test samples not found at {DATASET_PATH}.\nRun: python src/prepare_data.py\nOr download test_samples.csv from https://github.com/asun975/ai-powered-smishing and save to data/"
            )

    # Create dataframe of n random samples
    df = pd.read_csv(data)
    df = df.sample(n=sample_size, random_state=42)
    # Convert labels to numeric
    df["label"] = df["label"].map({"ham": 0, "smishing": 1})
    
    return df

def main():
    try:
        # Load model and tokenizer
        model, tokenizer = load_model()
        
        # HF text-classification
        classifier = pipeline("text-classification", device=DEVICE, model=model, tokenizer=tokenizer)

        # Load dataset
        df = load_dataset(DATASET_PATH, N_SIZE)

        # Text preprocessing and sanitization
        df["sanitized_text"] = df["text"].copy()
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: sanitize_text(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: removeUrl(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: text_preprocess(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: remove_special_char(x))

        # Convert to Hugging Face dataset
        dataset = Dataset.from_pandas(df)
        
        # Tokenize features
        dataset = dataset.map(
            lambda examples: tokenizer(
                examples["sanitized_text"], 
                truncation=True,
                padding="max_length",
                max_length=MAX_LENGTH,
                return_tensors="pt")
            )
        
        batched_dataset = dataset.batch(batch_size=BATCH_SIZE)

        texts = dataset['sanitized_text']
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

            for processed_text, original_text in zip(batch['sanitized_text'], batch['text']):
                result = classifier(processed_text)[0]
                label = {"LABEL_0": 0, "LABEL_1": 1}[result["label"]]
                score = result["score"]
                risk_score = score * 100 if label == "SPAM" else (1 - score) * 100
                results_dict = {
                    'original_text': original_text,
                    'processed_text': processed_text,
                    'pred': label,
                    'confidence':score,
                    'risk_score': risk_score
                }
                all_preds.append(label)
                all_results.append(results_dict)

            if DEVICE == "cuda":
                    torch.cuda.synchronize()

            end_time = time.time() 

            batch_time = end_time - start_time

            print(
                f"| Batch Size: {len(batch['sanitized_text'])} "
                f"| Time: {batch_time:.4f}s"
            )

        end_total = time.time()

        # Metrics
        accuracy = accuracy_score(labels, all_preds)
        f1 = f1_score(labels, all_preds)
        recall = recall_score(labels, all_preds)
        precision = precision_score(labels, all_preds)
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
        print(f"Precision:             {precision:.4f}")
        print(f"Recall:             {recall:.4f}")

        print("\nPerformance Metrics")
        print("-" * 30)

        print(f"Total Inference Time: {total_time:.4f} sec")
        print(f"Throughput:           {samples_per_second:.2f} samples/sec")

        if peak_memory is not None:
            print(f"Peak GPU Memory:      {peak_memory:.2f} MB")

        print("=" * 60)

        # Evaluate performance with confusion matrix
        cm = confusion_matrix(labels, all_preds)
        cm_labels = np.unique(labels)
        df_cm = pd.DataFrame(cm, index=cm_labels, columns=cm_labels)
        print(df_cm)

        # Show true negatives, false positives, false negatives and true positives
        tn, fp, fn, tp = cm.ravel().tolist()
        print(f"True Negative: {tn}\nFalse Positive: {fp}\nFalse Negative: {fn}\nTrue Positive: {tp}")
       
        # Save test results with original text
        df_results = pd.DataFrame.from_dict(all_results)
        # TODO: save original text and masked text for llm input
        df_results.to_csv("data/test_results.csv")
        print("Saved all results to data/test_results.csv")
    
    except Exception as e:
            print(f"An unexpected exception occured of type {type(e)}")
            print("*** print_exception:")
            print(e)

if __name__ == "__main__":
    main()
