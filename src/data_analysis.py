import pandas as pd

df = pd.read_csv("data/kaggle_malicious_url_dataset/test_dataset.csv")
df = df[['url', 'label']]
print(df['label'].unique())
print(df.columns)
