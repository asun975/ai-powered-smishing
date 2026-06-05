import requests

# paste your urlscan.io API Key with read/write permissions
API_KEY = 'YOUR-API-KEY-HERE'

suspicious_url = "https://urlscan.io"
scan_endpoint = "https://urlscan.io/api/v1/scan"
result_endpoint = "https://urlscan.io/api/v1/result/"

headers = {
    'Content-Type': 'application/json',
    'api-key': API_KEY
}

payload = {
    'url':suspicious_url,
    'visibility':'private'
}

response = requests.post(scan_endpoint, json=payload, headers=headers)

if response.status_code == 200:
    data = response.json()
    scan_id = data.get("uuid")
    url=result_endpoint + scan_id + '/'
    scan_result = requests.get(url,{'api-key': API_KEY})

    if scan_result.status_code == 200:
        data = response.json()
        print(data)
    else:
        print("Failed to retrieve scan results.")
        print(response.status_code)
        print(response.headers)
        print(repr(response.text))
else:
    print("Scan failed.")
    print(response.status_code)
    print(response.headers)
    print(repr(response.text))
