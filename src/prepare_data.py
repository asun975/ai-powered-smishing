import pandas as pd

def print_info(df):
    print(f"Total samples {df.shape}")
    print(f"Nbr of benign samples: {len(df[df['LABEL']=='ham'])}")

df1=pd.read_csv("data/smssmishcollection/SMSSmishCollection.txt", sep="\t", header=None, names=['LABEL', 'TEXT'])
df2 = pd.read_csv('data/SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION/Dataset_5971.csv')
df2 = df2[['LABEL', 'TEXT']]

df = pd.concat([df1, df2])
print(df.shape)
df.drop_duplicates()
print_info(df)

# Remove duplicates found in training dataset 
train_df = pd.read_csv('data/spam.csv', names=['LABEL', 'TEXT'])
df = pd.concat([df, train_df]).drop_duplicates(keep=False)
print_info(df)

# Save test dataset
df.to_csv('data/SMSSmish_test.csv', index=False)
