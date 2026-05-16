import os
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline

# To use Transformers in an offline or firewalled environment requires the downloaded and cached files ahead of time. 
tokenizer = AutoTokenizer.from_pretrained("models/TinyLlama/TinyLlama-1.1B-Chat-v1.0", local_files_only=True)
model = AutoModelForCausalLM.from_pretrained("models/TinyLlama/TinyLlama-1.1B-Chat-v1.0", local_files_only=True, torch_dtype=torch.float16)
classifier = pipeline(task="text-classification", model="models/mrm8488/bert-tiny-finetuned-sms-spam-detection")

# Save tokenizer and model to local dir
#tokenizer = AutoTokenizer.from_pretrained("TinyLlama/TinyLlama-1.1B-Chat-v1.0")
#model = AutoModelForCausalLM.from_pretrained("TinyLlama/TinyLlama-1.1B-Chat-v1.0", torch_dtype=torch.float16)

#tokenizer.save_pretrained("models/TinyLlama/TinyLlama-1.1B-Chat-v1.0")
#model.save_pretrained("models/TinyLlama/TinyLlama-1.1B-Chat-v1.0")

#classifier = pipeline("text-classification", model="mrm8488/bert-tiny-finetuned-sms-spam-detection")
#classifier.save_pretrained("models/mrm8488/bert-tiny-finetuned-sms-spam-detection")

# Test smishing message
message = "URGENT: Your bank account has been locked. Verify now: http://secure-login.xyz"

# Model classifies smishing message
result = classifier(message)

# Show Risk score and model output
print("bert-tiny-finetuned is ready!")
print(result)

label = result[0]['label']
score = result[0]['score']

# Use LABEL_0 for risk score
"""
if label == "LABEL_1":
    risk = score
else:
    risk = 1 - score
"""
risk = score
risk_percent = round(risk * 100)

if risk_percent >= 70:
    category = "HIGH RISK 🔴"
elif risk_percent >= 40:
    category = "SUSPICIOUS 🟡"
else:
    category = "LIKELY SAFE 🟢"

print("-----------------------------")
print("Message:", message)
print("Risk Score:", risk_percent, "/ 100")
print("Category:", category)
print("-----------------------------")

print("Tiny llama ready!")

# Getting Reply

prompt = f"""<start_of_turn>user
You are a cybersecurity assistant helping everyday people understand if an SMS message is a phishing scam.

An AI model analyzed this SMS message:
"{message}"

It gave a risk score of {risk_percent} out of 100 and categorized it as {category}.

Please explain in simple, clear steps why this message received that score.
Talk about specific parts of the message that are suspicious or safe.
Write it for someone who is not a tech expert.
Use 4 steps maximum.
<end_of_turn>
<start_of_turn>model
"""

inputs = tokenizer(prompt, return_tensors="pt")

outputs = model.generate(**inputs, max_new_tokens=300)

# Show LLM chain of thought reasoning
#response = tokenizer.decode(outputs[0], skip_special_tokens=True)
#reply = response.split("<start_of_turn>model")[-1].strip()

# Extract just the LLM's reply
print(tokenizer.decode(outputs[0][inputs["input_ids"].shape[-1]:]))
