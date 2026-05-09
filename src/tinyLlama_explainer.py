import os
import re
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline

os.environ["HF_DATASETS_OFFLINE"] = '1'
os.environ["TRANSFORMERS_OFFLINE"] = '1'

def generate_reasoning(message, risk_score, label):
    # To use Transformers in an offline or firewalled environment requires the downloaded and cached files ahead of time. 
    tokenizer = AutoTokenizer.from_pretrained("../models/TinyLlama/TinyLlama-1.1B-Chat-v1.0", local_files_only=True)
    model = AutoModelForCausalLM.from_pretrained("../models/TinyLlama/TinyLlama-1.1B-Chat-v1.0", local_files_only=True, torch_dtype=torch.float16)

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

