import pandas as pd
from datasets import Dataset
from os import getcwd, path
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)

BASE_DIR = getcwd()
BASE_MODEL_DIR = path.join(BASE_DIR, 'models', 'sms-spam-model')
NEW_MODEL_DIR = path.join(BASE_DIR, 'models', 'sms-spam-model-v2')

print('Current working directory: ', BASE_DIR)
print('Results will be saved to: ', NEW_MODEL_DIR)

# -------------------------------
# 1. LOAD NEW DATASET (WITH URLs)
# -------------------------------
df = pd.read_csv("data/train_dataset.csv")
df = df.sample(n=5000, random_state=42)

# Keep only message + label
df = df[["url", "label"]]
df = df.dropna()

print(df.head())

# -------------------------------
# 2. CONVERT TO HF DATASET
# -------------------------------
dataset = Dataset.from_pandas(df)

dataset = dataset.train_test_split(test_size=0.2)

# -------------------------------
# 3. LOAD EXISTING MODEL
# -------------------------------
model_path = BASE_MODEL_DIR

tokenizer = AutoTokenizer.from_pretrained(model_path)

model = AutoModelForSequenceClassification.from_pretrained(model_path)

# -------------------------------
# 4. TOKENIZATION
# -------------------------------
def tokenize(example):
    return tokenizer(
        example["url"],
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
# 5. TRAINING CONFIG
# -------------------------------
training_args = TrainingArguments(
    output_dir=path.join(NEW_MODEL_DIR, 'results'),
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    num_train_epochs=2,  # small = avoids overfitting
    logging_dir=path.join(NEW_MODEL_DIR, 'logs')
)

# -------------------------------
# 6. TRAINER
# -------------------------------
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=dataset["train"],
    eval_dataset=dataset["test"]
)

# -------------------------------
# 7. CONTINUE TRAINING
# -------------------------------
trainer.train()

# -------------------------------
# 8. SAVE UPDATED MODEL
# -------------------------------
updated_model_path = NEW_MODEL_DIR

trainer.save_model(updated_model_path)
tokenizer.save_pretrained(updated_model_path)

print(f"\nUpdated model saved to: {updated_model_path}")
