# Block Message Feature — Testing & Performance Report
Owner: Yugveer
Scope: `Block Message` feature (button, `DatabaseHelper.blockMessage()`, confirmation toast) added on top of the existing Mark as Safe (SCRUM-50) flow.
## 1. Feature Testing
The Android app has no committed Gradle wrapper / Android SDK in this repo, so the
UI itself was not built in an emulator as part of this report. Instead, every code
path was verified two ways: a full manual trace of the Kotlin logic, and an
executable test harness that replicates `DatabaseHelper`'s exact SQL against a
real SQLite database (`sqlite3`, in-memory), since that's the part of the feature
with actual behavior to break.
### 1.1 Manual code trace
- Fresh SMS arrives -> `Block Message` button visible whenever the result card is
  shown, except when `status == "blocked"`.
- Tap `Block Message` -> `db.blockMessage(currentMessageBody)` sets `status =
  'blocked'` for that message; badge flips to grey **BLOCKED**; both the Mark as
  Safe and Block Message buttons hide; a Toast reads **"Message blocked
  successfully"**.
- `showError()` and the "new SMS arrived" receiver path both explicitly hide the
  Block Message button so it can't be shown against a stale/error state.
- Fixed while implementing: `renderUI()` previously recomputed `status` from the
  risk score on every cache hit, ignoring whatever was actually persisted. That
  meant a message marked Safe or Blocked would revert to its original
  Caution/Quarantined label the next time the identical SMS body arrived from
  cache. `showCachedResult()` now reads the persisted `status` column instead.
### 1.2 Automated SQL-logic tests (`sqlite3`, real execution)
5/5 passed:
| # | Test | Result |
|---|---|---|
| 1 | Blocking a quarantined (high-risk) message sets `status='blocked'` | PASS |
| 2 | Cache-hit path returns the persisted `blocked` status rather than recomputing `quarantined` from the stored risk score | PASS |
| 3 | A safe (low-risk) message can also be blocked — blocking isn't restricted to spam | PASS |
| 4 | Blocking a message with no matching row updates 0 rows and does not throw | PASS |
| 5 | `getByStatus('blocked')` returns exactly the blocked rows, no more/less | PASS |
### 1.3 Static checks
- `xmllint --noout` on both copies of `activity_main.xml` — valid.
- Brace/paren/bracket balance check on both copies of `MainActivity.kt` and
  `DatabaseHelper.kt` — balanced.
### Known gap
No real Android Studio / emulator run was performed (no Gradle wrapper is
committed to this repo, and there's no Android SDK in this sandbox). Recommend
one manual pass in Android Studio — send a test SMS, tap **Block Message**, and
confirm the toast + badge — before merging.
## 2. Performance Testing
Full end-to-end performance testing (including the DistilBERT model's inference
latency) requires a trained model in `models/distilbert/`, which isn't committed
to the repo and needs `python src/train_model.py` to be run locally first (it
downloads base model weights and trains on `data/spam.csv`). That step wasn't run
for this report. What follows benchmarks the parts of the pipeline that **are**
runnable without the trained model: the regex/NLP preprocessing stage every
message passes through, and the SQLite operations the Block/Mark-as-Safe features
rely on.
**Environment:** Python 3.11.15, x86_64 Linux, 4 vCPUs.
### 2.1 Preprocessing pipeline (`src/preprocessing.py`)
10 representative SMS samples (mix of spam/ham, PII, URLs, media tags, trivial
replies), run 2000x each (20,000 calls per function):
| Function | Mean | Median | p95 | Max |
|---|---|---|---|---|
| `should_skip` | 0.0048 ms | 0.0058 ms | 0.0080 ms | 0.0765 ms |
| `clean_for_llm` | 0.0066 ms | 0.0077 ms | 0.0114 ms | 0.0793 ms |
| `clean_for_distilbert` | 0.0045 ms | 0.0052 ms | 0.0078 ms | 0.0634 ms |
| `clean_text` (legacy rule-based prep) | 0.0307 ms | 0.0349 ms | 0.0574 ms | 0.2917 ms |
All four stay well under 1ms per message even at p95 — preprocessing is not a
bottleneck relative to network latency to the FastAPI server or DistilBERT
inference.
### 2.2 SQLite operations (Block feature)
Benchmarked against an in-memory SQLite DB using the same schema and queries as
`DatabaseHelper.kt`:
| Operation | n | Mean | Median | p95 | Max |
|---|---|---|---|---|---|
| `insertMessage` | 5,000 | 0.0026 ms | 0.0022 ms | 0.0046 ms | 0.0447 ms |
| `blockMessage` (update by body) | 1,000 | 0.0028 ms | 0.0023 ms | 0.0042 ms | 0.0445 ms |
| `findByMessage` (cache lookup) | 1,000 | 0.0053 ms | 0.0050 ms | 0.0058 ms | 0.0694 ms |
| `getByStatus('blocked')` (full scan, 1,000 matching rows) | 1 query | — | — | — | 1.9082 ms |
All single-row operations complete in microseconds; even a full-table scan
returning 1,000 rows takes under 2ms. On-device SQLite (a real phone, not this
sandbox) will be slower due to flash I/O, but these numbers show the query
patterns themselves — not the SQL logic — are not the limiting factor.
### 2.3 Recommendation
The bottleneck for real-world latency is the network round trip to
`POST /analyze` and the DistilBERT forward pass, neither of which this report
covers (no trained model available). If performance testing is required for
those specifically, train the model first (`python src/train_model.py`), then
benchmark `POST /analyze` end-to-end with a tool like `hyperfine` or a simple
timed loop against the running FastAPI server.
