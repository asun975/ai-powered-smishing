## Set-up
Requires python >= 3.10

1. Create python virtual environment and install requirements.txt
```
cd ai-powered-smishing/fastapi-url-analyzer
python -m venv venv
venv/Scripts/activate
python -m pip install -r requirements.txt

```
2. Create .env and add your API key for urlscan.io.
```
API_KEY = ""
```
3. Add .env to fastapi/.gitignore so it is not tracked by version control.
### Test url-analyzer from Android Studio emulator
1. Run api.py
2. Launch emulator from Android Studio
3. In the emulator, open a browser and enter 10.0.2.2/8000/health

## Endpoints
Read fastapi docs at http://localhost:8000/docs for more information on endpoints.

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Check if API KEY is configured |
| POST | `/analyze` | Send an URL scan to urlscan.io |
## /analyze Request
```json
{
    "url": "http://google.ca"
}
```
## /analyze Response
```json
{
    "url": "http://google.ca",
    "uuid": "scan_id",
    "malicious": false,
    "score": 0
}
```
