import os
import re
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline

def load_model():
    model_path = os.path.join(os.getcwd(), 'models', 'TinyLlama')
    
    if not model_path.exists():
        tokenizer = AutoTokenizer.from_pretrained("TinyLlama/TinyLlama-1.1B-Chat-v1.0")
        model = AutoModelForCausalLM.from_pretrained("TinyLlama/TinyLlama-1.1B-Chat-v1.0")
        tokenizer.save_pretrained(model_path)
        model.save_pretrained(model_path)

    tokenizer = AutoTokenizer.from_pretrained(model_path, local_files_only=True)
    model = AutoModelForCausalLM.from_pretrained(model_path, local_files_only=True)

    return tokenizer, model

def generate_explanation(message, risk_score, label):

    tokenizer, model = load_model()

    prompt = f"""<start_of_turn>user
    You are a cybersecurity assistant helping everyday people understand if an SMS message is a phishing scam.

    An AI model analyzed this SMS message:
    "{message}"

    It gave a risk score of {risk_score} out of 100 and categorized it as {label}.

    Please explain in simple, clear steps why this message received that score.
    Talk about specific parts of the message that are suspicious or safe.
    Write it for someone who is not a tech expert.
    Use 4 steps maximum.
    <end_of_turn>
    <start_of_turn>model
    """

    inputs = tokenizer(prompt, return_tensors="pt")

    outputs = model.generate(**inputs, max_new_tokens=300)

    # Extract just the LLM's reply
    reply=tokenizer.decode(outputs[0][inputs["input_ids"].shape[-1]:])
    reply=re.sub(r"\\t\s+", ' ', reply).strip()
    return reply
