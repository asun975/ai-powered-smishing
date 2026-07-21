# AI-Powered Smishing Detection
A hybrid SMS phishing detection system combining a fine-tuned DistilBERT classifier, rule-based URL/keyword scoring, and a plain-English LLM explanation — served via a FastAPI backend and consumed by an Android app.
## Team
- **Yugveer Singh Sidhu**
- Gustavo De Vera Teixeira
- Rachna Alleear
- Ashley Sun
---
## Project Structure
```text
ai-powered-smishing/
├── app/
│   ├── MainActivity.kt          ← SMS receiver + API call + DB cache
│   ├── DatabaseHelper.kt        ← SQLite DB (stores all analyzed messages)
│   ├── AndroidManifest.xml      ← Permissions (RECEIVE_SMS, INTERNET)
│   ├── activity_main.xml        ← UI layout
│   └── src/                     ← Full Android Studio source tree
├── data/
│   └── spam.csv                 ← Training dataset
├── models/
│   └── distilbert/              ← Trained model (not committed — train locally)
├── src/
│   ├── api.py                   ← FastAPI server
│   ├── distilbert_model.py      ← DistilBERT inference
│   ├── llm_explainer.py         ← Plain-English explanation generator
│   ├── pipeline.py              ← Main analyze_sms() orchestrator
│   ├── preprocessing.py         ← PII stripping, media/trivial filtering
│   ├── tinyllama.py             ← TinyLlama prototype (not wired in yet)
│   └── train_model.py           ← DistilBERT fine-tuning script
├── requirements.txt
├── .gitignore
└── README.md
```
---
## Setup
### 1. Install Python dependencies
```bash
pip install -r requirements.txt
```
### 2. Train the DistilBERT model
```bash
python src/train_model.py
```
Saves the trained model to `models/distilbert/`. The model is not committed to the repo due to file size — you must train it locally after cloning.
---
## Running the API Server
```bash
python src/api.py
```
Starts a FastAPI server on `http://0.0.0.0:8000`.
> **Note:** On Windows you may need to allow port 8000 through the firewall:
> ```powershell
> New-NetFirewallRule -DisplayName "Allow FastAPI 8000" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
> ```
### Endpoints
| Method | Path | Description |
|---|---|---|
| GET | `/health` | Check if the server is running |
| POST | `/analyze` | Analyze an SMS message for smishing |
### Interactive API Docs
Open `http://localhost:8000/docs` in your browser to test all endpoints via Swagger UI.
### Example Request
```bash
curl -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{"message": "URGENT: Your CIBC account has been locked. Verify now: http://secure-cibc.xyz"}'
```
### Example Response
```json
{
  "message": "URGENT: Your CIBC account has been locked. Verify now: http://secure-cibc.xyz",
  "prediction": "SPAM",
  "risk_score": 99.9,
  "ml_prediction": "SPAM",
  "ml_confidence": 0.9983,
  "ml_score": 99.83,
  "rule_score": 100.0,
  "explanation": "This message is highly suspicious and likely a smishing attempt. It contains suspicious wording such as: urgent, verify, locked. It includes link(s): secure-cibc.xyz.",
  "skipped": false,
  "skip_reason": ""
}
```
### API Edge Cases (SCRUM-35)
| Input | Response |
|---|---|
| Empty or whitespace message | `400` — Message cannot be empty |
| Message over 1000 characters | `400` — Message too long |
| Media-only message (`[Image]`) | `skipped: true`, `prediction: SAFE`, `risk_score: 0` |
| Trivial message (`"ok"`, `"hey"`) | `skipped: true`, `prediction: SAFE`, `risk_score: 0` |
| Model files not found | `503` — Model not available (run `train_model.py` first) |
| Unexpected server error | `500` — Internal error (full traceback logged server-side only) |
---
## Running the Detector (CLI)
```bash
python src/pipeline.py
```
Runs built-in example messages then opens an interactive prompt:
```
SMS > Your CIBC account is locked. Verify now: http://secure-cibc.xyz
  Prediction  : SPAM
  Final Score : 99.9 / 100
  ML (SPAM, conf 99.83%)  Rule score: 100
  Explanation : This message is highly suspicious...
```
---
## Android App (SCRUM-43)
The `app/` folder contains the full Android Studio project source.
### Setup in Android Studio
1. Open Android Studio → **Open** → select `C:\Users\yugve\AndroidStudioProjects\SmishingDetector`
2. Wait for Gradle to sync
3. Make sure the Python API is running on your PC (`python src/api.py`)
4. Add the Windows firewall rule (see above)
5. Click the green **Run** button → select your emulator
### Testing with the Emulator
1. Start the emulator (Pixel 6, API 37)
2. In Android Studio → emulator `...` menu → **Phone** → **SMS tab**
3. Enter a phone number and message → click **Send**
4. The app will display the SPAM/SAFE verdict, risk score, and explanation
### How it Works
```
Incoming SMS → BroadcastReceiver (MainActivity.kt)
     ↓
Check local SQLite DB (cache hit? → show instantly, no API call)
     ↓ (cache miss)
POST /analyze to Python API at 10.0.2.2:8000
     ↓
Save result to SQLite DB
     ↓
Show: SPAM/SAFE badge + Risk Score + Status + Explanation
```
### Local Database (DatabaseHelper.kt)
Every analyzed message is stored on the phone in SQLite (`smishing_detector.db`).
| Column | Type | Description |
|---|---|---|
| `phone_number` | TEXT | Sender address |
| `date` | TEXT | Timestamp (yyyy-MM-dd HH:mm:ss) |
| `message` | TEXT | Original SMS body |
| `risk_score` | REAL | 0–100 combined score |
| `prediction` | TEXT | `SPAM` or `SAFE` |
| `status` | TEXT | `safe` / `caution` / `quarantined` / `blocked` |
| `explanation` | TEXT | Plain-English reason |
**Status rules:**
- `risk_score < 35` → **safe**
- `35 ≤ risk_score < 70` → **caution**
- `risk_score ≥ 70` → **quarantined**
**Useful DB queries for teammates:**
```kotlin
db.getByStatus("caution")       // all caution messages
db.getByStatus("quarantined")   // all quarantined messages
db.getAllMessages()              // everything
db.countByStatus("quarantined") // count only
```
### Mark as Safe (SCRUM-50)
Messages flagged as **caution** or **quarantined** show a green **Mark as Safe** button below the result card. Tapping it:
1. Updates the message's `status` to `safe` in the local SQLite database
2. Flips the prediction badge to green (`SAFE`)
3. Hides the button
This is also available to teammates via `db.markAsSafe(messageBody)` or `db.updateStatusById(id, "safe")`.
### Block Message
Every result card shows a dark grey **Block Message** button. Tapping it:
1. Updates the message's `status` to `blocked` in the local SQLite database
2. Flips the prediction badge to grey (`BLOCKED`)
3. Hides both the Mark as Safe and Block Message buttons
4. Shows a confirmation toast: **"Message blocked successfully"**
A blocked message stays `blocked` even if the same SMS body arrives again later (cache hits read the persisted `status` column rather than only recomputing it from the risk score). This is also available to teammates via `db.blockMessage(messageBody)` or `db.updateStatusById(id, "blocked")`, and blocked messages can be listed with `db.getByStatus("blocked")`.
### Permissions Required
| Permission | Reason |
|---|---|
| `RECEIVE_SMS` | Intercept incoming SMS messages |
| `INTERNET` | Call the Python analysis API |
---
## Preprocessing Pipeline (SCRUM — Yugveer Week 3)
All messages pass through `src/preprocessing.py` before reaching any model.
### Messages That Get Skipped
| Type | Example | Skip Reason |
|---|---|---|
| Empty message | `""` | `media_only` |
| Photo/MMS | `[Image]`, `[Video]` | `media_only` |
| Trivial reply | `"ok"`, `"hey"`, `"lol"` | `trivial` |
### PII Stripping
| PII Type | Input | Output |
|---|---|---|
| Phone number | `Call 416-555-1234` | `Call [PHONE]` |
| Email address | `email@phish.com` | `[EMAIL]` |
| Credit/debit card | `4111 1111 1111 1111` | `[CARD]` |
| SIN / SSN | `123 456 789` | `[ID]` |
---
## How the Hybrid Scoring Works
```
Final Score = (0.6 × ML Score) + (0.4 × Rule Score)
Prediction  = SPAM if Final Score ≥ 35, else SAFE
```
| Component | Weight | What it checks |
|---|---|---|
| DistilBERT ML | 60% | Fine-tuned on spam/ham dataset |
| Rule-based scorer | 40% | Keywords, untrusted URLs, brand domain mismatch |
---
## Known Limitations / TODO
- `llm_explainer.py` is template-based — real LLM API call (Claude/OpenAI) planned
- `tinyllama.py` is a prototype — not wired into the main pipeline yet
- URL analyzer (separate repo) needs integration into `pipeline.py`
- Android app shows only the most recent SMS — history screen planned (SCRUM-42)
- No push notification yet when SPAM is detected
