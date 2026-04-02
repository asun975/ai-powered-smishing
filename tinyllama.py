from transformers import AutoTokenizer, AutoModelForCausalLM

# To use Transformers in an offline or firewalled environment requires the downloaded and cached files ahead of time. 
model_name = "C:\\Users\\ashle\\.cache\\huggingface\\hub\\models--TinyLlama--TinyLlama-1.1B-Chat-v1.0\\snapshots\\fe8a4ea1ffedaf415f4da2f062534de366a451e6"

tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)
model = AutoModelForCausalLM.from_pretrained(model_name, local_files_only=True)

messages = [
    {"role": "user", "content": "Who are you?"},
]
inputs = tokenizer.apply_chat_template(
	messages,
	add_generation_prompt=True,
	tokenize=True,
	return_dict=True,
	return_tensors="pt",
).to(model.device)

outputs = model.generate(**inputs, max_new_tokens=40)
print(tokenizer.decode(outputs[0][inputs["input_ids"].shape[-1]:]))