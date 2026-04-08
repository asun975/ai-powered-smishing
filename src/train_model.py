import pandas as pd
import numpy as np
from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)
from sklearn.metrics import accuracy_score, precision_recall_fscore_support


# Load dataset
df = pd.read_csv("data/spam.csv", encoding="latin-1")

# Keep only first 2 columns in case dataset has extra unnamed columns
df = df.iloc[:, :2]
df.columns = ["label", "message"]

# Convert labels
df["label"] = df["label"].map({"ham": 0, "spam": 1})
df = df.dropna()

# Convert to Hugging Face dataset
dataset = Dataset.from_pandas(df)
dataset = dataset.train_test_split(test_size=0.2, seed=42)

# Load tokenizer
tokenizer = AutoTokenizer.from_pretrained("distilbert-base-uncased")


def tokenize(example):
    return tokenizer(
        example["message"],
        truncation=True,
        padding="max_length",
        max_length=128
    )


# Tokenize dataset
dataset = dataset.map(tokenize, batched=True)
dataset.set_format(type="torch", columns=["input_ids", "attention_mask", "label"])

# Load model
model = AutoModelForSequenceClassification.from_pretrained(
    "distilbert-base-uncased",
    num_labels=2
)


# Evaluation metrics
def compute_metrics(eval_pred):
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=1)

    precision, recall, f1, _ = precision_recall_fscore_support(
        labels, predictions, average="binary"
    )
    accuracy = accuracy_score(labels, predictions)

    return {
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1
    }


# Training configuration
training_args = TrainingArguments(
    output_dir="models/distilbert",
    num_train_epochs=3,
    per_device_train_batch_size=8,
    per_device_eval_batch_size=8,
    eval_strategy="epoch",
    save_strategy="epoch",
    load_best_model_at_end=True,
    metric_for_best_model="f1",
    greater_is_better=True,
    logging_steps=50,
    report_to="none"
)


# Trainer
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=dataset["train"],
    eval_dataset=dataset["test"],
    compute_metrics=compute_metrics
)


# Train model
trainer.train()

# Evaluate final model
results = trainer.evaluate()
print("Evaluation results:", results)

# Save final model and tokenizer
trainer.save_model("models/distilbert")
tokenizer.save_pretrained("models/distilbert")

print("Model trained and saved successfully!")