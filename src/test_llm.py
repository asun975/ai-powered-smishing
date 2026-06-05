import requests
import pandas as pd

DATASET_PATH = 'data/llm_test_samples.csv'

# Load test samples
test_df = pd.read_csv(DATASET_PATH)
test_df = test_df.reset_index()
payload_df = test_df.loc[:, ['index', 'original_text', 'pred', 'risk_score']]
payload_df = payload_df.rename(columns={'index':'id'})
print(payload_df.info())
"""
# Base endpoint for API
apiUrl = "/explain"

headers = {
    "Content-Type": "application/json",
}

# Data must match json format expected by API
payload = {
    "sms_text":sms_text,
    "classification": pred,
    "risk_score": risk_score
}

response = requests.post(apiUrl)

if response.status_code == 200:
    data = response.json()
    explanation = data['explanation']
else:
    error_detail = response.json()
"""