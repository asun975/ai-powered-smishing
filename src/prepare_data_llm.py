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

from preprocessing import sanitize_text, removeUrl, text_preprocess, remove_special_char, maskPII

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

        # Create column for sanitized sms text
        df["sanitized_text"] = df["text"].copy()
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: sanitize_text(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: removeUrl(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: text_preprocess(x))
        df["sanitized_text"] = df["sanitized_text"].apply(lambda x: remove_special_char(x))

        # Create column for masked PII in sms text
        df['masked_text'] = df['text'].copy()
        df['masked_text'] = df['masked_text'].apply(lambda x: maskPII(x))

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
        all_results = []
        all_preds = []

        for batch in batched_dataset:

            for processed_text, original_text, masked_text, ground_truth in zip(batch['sanitized_text'], batch['text'], batch['masked_text'], batch['label']):
                result = classifier(processed_text)[0]
                pred = {"LABEL_0": 0, "LABEL_1": 1}[result["label"]]
                score = result["score"]
                risk_score = score * 100 if pred == "SPAM" else (1 - score) * 100
                results = {
                    'original_text': original_text,
                    'processed_text': processed_text, 
                    'masked_text': masked_text,
                    'pred': pred,
                    'label': ground_truth,
                    'risk_score': risk_score
                }
                all_preds.append(pred)
                all_results.append(results)
       
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
