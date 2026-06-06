import time
import requests

API_KEY = "YOUR-API-KEY-HERE"
URL_TO_SCAN = "https://torontosun.com/sports/basketball/nba/why-viral-female-spurs-fans-skip-game-2-finals?utm_source=firefox-newtab-en-ca"

SCAN_URL = "https://urlscan.io/api/v1/scan/"
RESULT_URL_TEMPLATE = "https://urlscan.io/api/v1/result/{scan_id}/"

INITIAL_WAIT = 10      # seconds before polling
POLL_INTERVAL = 2      # seconds between polls
MAX_WAIT = 120         # total timeout in seconds


def submit_scan(url):
    headers = {
        "Content-Type": "application/json",
        "API-Key": API_KEY
    }

    payload = {
        "url": url,
        "visibility": "private"
    }

    response = requests.post(
        SCAN_URL,
        json=payload,
        headers=headers,
        timeout=30
    )

    response.raise_for_status()

    data = response.json()
    scan_id = data.get("uuid")

    if not scan_id:
        raise RuntimeError(f"No UUID returned: {data}")

    return scan_id


def get_scan_result(scan_id, header):
    result_url = RESULT_URL_TEMPLATE.format(scan_id=scan_id)

    response = requests.get(
        result_url,
        headers=header,
        timeout=30
    )

    return response


def poll_for_result(scan_id, header):
    print(f"Waiting {INITIAL_WAIT} seconds before polling...")
    time.sleep(INITIAL_WAIT)

    start_time = time.time()

    while True:
        elapsed = time.time() - start_time

        if elapsed > MAX_WAIT:
            raise TimeoutError(
                f"Scan did not complete within {MAX_WAIT} seconds"
            )

        response = get_scan_result(scan_id, header)

        if response.status_code == 200:
            return response.json()

        # URLScan commonly returns 404 while the scan is still processing
        if response.status_code == 404:
            print("Scan still processing...")
            time.sleep(POLL_INTERVAL)
            continue

        response.raise_for_status()


def determine_verdict(result):
    overall = result.get("verdicts", {}).get("overall", {})

    malicious = overall.get("malicious")

    if malicious is True:
        return "MALICIOUS"

    if malicious is False:
        return "SAFE"

    return "UNKNOWN"


def main():
    try:
        header = {"api-key":API_KEY}
        scan_id = submit_scan(URL_TO_SCAN)
        print(f"Scan submitted. UUID: {scan_id}")

        result = poll_for_result(scan_id, header)

        verdict = determine_verdict(result)

        print(f"Verdict: {verdict}")

        overall = result.get("verdicts", {}).get("overall", {})
        print("Overall verdict details:")
        print(overall)

    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    main()