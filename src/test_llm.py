import requests
import pandas as pd
import json
import time

DATASET_PATH = 'data/llm_test_samples.csv'
API_URL = 'https://[Username-model].space/explain' # Paste endpoint url for LLM

if __name__ == "__main__":
    # Load test samples
    test_df = pd.read_csv(DATASET_PATH)
    test_df = test_df.rename(columns={'pred':'classification', 'masked_text':'text'})

    # Get one random sample 
    sample = test_df.sample(n=1, random_state=42).iloc[0]
    #print(sample)
   
    # Data must match format expected by API
    sample = sample[['text', 'classification', 'risk_score']]
    payload = sample.to_dict()

    # Convert classification to string labels
    payload['classification'] = "SPAM" if payload['classification'] == 1 else "SAFE"
    print("Text: " + payload["text"])
    print("Classification: " + payload["classification"])
    print("Risk_Score: ", payload["risk_score"])

    headers = {
        "Content-Type": "application/json",
    }
    start_time = time.time()
    response = requests.post(
        url=API_URL, 
        data=json.dumps(payload), 
        headers=headers
    )

    if response.status_code == 200:
        data = response.json()
        print("Model: ", data.get("version"))
        print("Explanation: ", data.get("explanation"))
    else:
        print(response.status_code)
        print(response.headers.get("Content-Type"))
        print(repr(response.text))
    end_time = time.time()
    print(f"Model response time: {end_time-start_time:.2f}")
