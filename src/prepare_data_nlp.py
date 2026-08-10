import pandas as pd
import os

"""
To create test_samples.csv:

Download and save datasets to data/
Mendeley dataset: https://data.mendeley.com/datasets/f45bkkt8pr/1
Kaggle dataset: https://www.kaggle.com/datasets/galactus007/sms-smishing-collection-data-set

cd ai-powered-smishing
python src/prepare_data.py
"""
KAGGLE_DATA = "data/SMSSmishCollection.txt"
MENDELEY_DATA = "data/Dataset_5971.csv"
SAVE_DIR = "src/data/"

def print_info(df):
    print(f"Total samples {df.shape[0]}")
    print(f"Nbr of benign samples: {len(df[df['label']=='ham'])}")
    print(f"Types of smishing texts: {df['label'].unique()}\n")

if __name__ == "__main__":

    if not os.path.exists(KAGGLE_DATA):
        raise FileNotFoundError(f"""Missing dataset at {KAGGLE_DATA}
            Download from:
            https://www.kaggle.com/datasets/galactus007/sms-smishing-collection-data-set
            """)
    if not os.path.exists(MENDELEY_DATA):
        raise FileNotFoundError(f"""Missing dataset at {MENDELEY_DATA}
            Download from:
            https://data.mendeley.com/datasets/f45bkkt8pr/1
            """)
    if not os.path.exists(SAVE_DIR):
        os.mkdir(SAVE_DIR)
        print(f"Directory {SAVE_DIR} created!")
    
    kaggle_df = pd.read_csv(KAGGLE_DATA, sep="\t", header=None, names=['label', 'text'])

    mendeley_df = pd.read_csv(MENDELEY_DATA)
    mendeley_df= mendeley_df.iloc[:, :2]
    mendeley_df.columns = mendeley_df.columns.str.lower()

    df = pd.concat([kaggle_df, mendeley_df])

    # Drop duplicate entries in text column and nan
    df.drop_duplicates(subset=["text"], inplace=True)
    df.dropna(inplace=True)

    # Normalize label types
    smish_labels = df[df['label']!='ham'].loc[:]['label'].unique()
    df['label'] = df['label'].replace(smish_labels, 'smishing')

    # Remove duplicated rows in training data
    train_df = pd.read_csv('data/spam.csv')
    train_df.columns = ['label', 'text']

    # Keep only rows in df that are NOT in train_df
    df_result = df.merge(train_df, how='left', indicator=True)
    df_result = df_result[df_result['_merge'] == 'left_only'].drop('_merge', axis=1)

    # Show dataframe info
    print("Showing info for kaggle dataset")
    print_info(kaggle_df)
    print("Showing info for mendeley dataset")
    print_info(mendeley_df)
    print("-" * 20)
    print("Compiled data")
    print_info(df)
    print("remove duplicates from training set")
    print_info(df_result)

    # Save test dataset
    dataset = os.path.join(SAVE_DIR, "test_samples.csv")
    df_result.to_csv(dataset, index=False)

    # Load test data
    test_df = pd.read_csv(dataset)
    print(test_df.head())
