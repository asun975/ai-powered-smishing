import os
import pandas as pd
import torch

from datasets import Dataset
from transformers import (
    pipeline,
    AutoTokenizer,
    AutoModelForSequenceClassification
)

from preprocessing import sanitize_text, removeUrl, text_preprocess, remove_special_char, maskPII

MAX_LENGTH=128
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_NAME = 'models/sms-spam-model-v2'
DATASET_PATH = 'sms test samples.csv'
OUTPUT_PATH = 'data/llm_test_samples.csv'

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

def main():
    try:
        # Load model and tokenizer
        model, tokenizer = load_model()
        
        # HF text-classification
        classifier = pipeline("text-classification", device=DEVICE, model=model, tokenizer=tokenizer)

        # Create dataframe of n random samples
        df = pd.read_csv(DATASET_PATH)
        df.drop(columns=['url'], inplace=True) # drop url column
        df.rename(columns={'message':'text'}, inplace=True) # rename message col
        # Convert labels to numeric
        df["label"] = df["label"].map({"legitimate": 0, "phishing": 1})

        # Create column for sanitized sms text
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
        
        print(F"Running {MODEL_NAME}...")
        texts = dataset['sanitized_text']
        all_results = []

        for t in texts:
            result = classifier(t)[0]
            pred = {"LABEL_0": 0, "LABEL_1": 1}[result["label"]]
            score = result["score"]
            risk_score = score if pred == 1 else (1 - score)
            results = {
                'processed_text': t, 
                'pred': pred,
                'risk_score': risk_score
            }
            all_results.append(results)
    
        # Convert results to dataframe
        df_samples = pd.DataFrame.from_dict(all_results) 

        df_samples['original_text'] = df['text'].copy() # Add back original text
        print("Masking PII in sms input...")
        # Create masked PII column that will pass to LLM
        df_samples['masked_text'] = df['text'].copy()
        df_samples['masked_text'] = df_samples['masked_text'].apply(lambda x: maskPII(x))

        df_samples['label'] = df['label'].copy() # Add ground truth labels

        print(df_samples.info()) # check dataframe contents

        # Save to csv
        df_samples.to_csv(OUTPUT_PATH, index=False)
        print(f"\nSaved all results to {OUTPUT_PATH}")
    
    except Exception as e:
            print(f"An unexpected exception occured of type {type(e)}")
            print("*** print_exception:")
            print(e)

if __name__ == "__main__":
    main()
