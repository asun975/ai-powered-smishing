# Security Testing Report — Week 11

**Owner:** Yugveer Singh Sidhu
**Task:** Security testing
**Branch analyzed:** `main`
**Date:** 2026-07-25

> **Note:** `main` runs a different architecture than the `Yugveer` branch —
> classification, LLM explanation, and URL scanning are all delegated to
> **externally-hosted APIs** (via `BuildConfig.CLASSIFIER_API_URL` /
> `LLM_API_URL` / `SCAN_API_URL`), and blocking is done by phone number
> (`blocked_senders` table) rather than by message status. This is a fresh
> review of `main`'s actual code.

## Method

Manual static review of `smishingdetection/app/` (manifest, network config,
`DatabaseHelper.kt`, `MainActivity.kt`, `Preprocessing.kt`,
`SmishingClassifier.kt`, `UrlAnalyzer.kt`, `build.gradle.kts`) plus a
dependency vulnerability scan of `requirements.txt` with `pip-audit`. No
dynamic/runtime penetration testing was performed.

## Findings

### 1. [HIGH] Cleartext HTTP is explicitly permitted, twice over

`AndroidManifest.xml` sets `android:usesCleartextTraffic="true"` **and**
supplies a `network_security_config.xml` that separately declares
`<base-config cleartextTrafficPermitted="true">` — belt-and-suspenders
permission for unencrypted traffic. All three API clients
(`SmishingClassifier`, `UrlAnalyzer`, and presumably `LlmExplainer`) use
`HttpURLConnection` with no TLS enforcement of their own.

**Impact:** If any of `CLASSIFIER_API_URL` / `LLM_API_URL` / `SCAN_API_URL`
resolve to an `http://` endpoint in a real deployment, every SMS body (after
PII stripping) and every extracted URL is sent unencrypted, interceptable by
anyone on the network path.

**Recommendation:** Confirm all three configured endpoints are HTTPS in
practice; if so, remove `usesCleartextTraffic` and tighten
`network_security_config.xml` to `cleartextTrafficPermitted="false"` so a
future misconfiguration can't silently downgrade to plaintext.

### 2. [HIGH] SMS content and extracted URLs are sent to third-party external services with no visible authentication

`SmishingClassifier.classify()` and `UrlAnalyzer.analyzeUrl()` both POST
message text / URLs to externally-hosted APIs, with no API key, bearer
token, or other credential attached to the request in the code reviewed.
`UrlAnalyzer`'s response format (`uuid`, `malicious`, `score`) strongly
resembles a public scanning service (e.g. urlscan.io-style).

**Impact:** Two separate concerns: (a) if these are genuinely open,
unauthenticated endpoints, anyone could hit them directly outside the app
context, consuming quota or capacity meant for real users; (b) more
importantly, the app is, by design, forwarding potentially sensitive SMS
content (after PII stripping, per `Preprocessing.kt`) and full URLs to
third-party infrastructure the team doesn't control. This should be a
documented, deliberate data-handling decision (e.g. in a privacy notice),
not an incidental side effect.

**Recommendation:** Confirm with whoever owns these endpoints whether
auth is handled at a layer not visible in this repo (e.g. an API gateway),
and document what third-party services receive user SMS content, for the
team's own data-handling records.

### 3. [MEDIUM] `READ_SMS` + `ContentObserver` polling gives the app access to the entire SMS inbox, not just new messages

Beyond `RECEIVE_SMS` (new incoming messages only), the manifest also
requests `READ_SMS`, and `MainActivity` registers a `ContentObserver` on
`Telephony.Sms.CONTENT_URI` that queries the **most recent message in the
entire SMS database** on every change (`checkLatestSms()`), as a second,
redundant detection path alongside the broadcast receiver.

**Impact:** This is a materially larger permission/data-access footprint
than `RECEIVE_SMS` alone. `READ_SMS` grants access to a user's full SMS
history, not just messages arriving while the app runs — appropriate to
justify explicitly, since Play Store policy scrutinizes `READ_SMS` usage
closely for exactly this reason.

**Recommendation:** If the dual detection (broadcast + content observer) is
intentional redundancy (e.g. to catch messages missed by the broadcast on
some OEMs), that's a reasonable engineering tradeoff — just make sure it's
documented as such, since it's the kind of thing a Play Store review or a
security-conscious user would ask about.

### 4. [MEDIUM] Raw SMS content and phone numbers stored unencrypted on-device, not excluded from backup

Same pattern as the `Yugveer` branch: `analyzed_messages` stores the message
body and sender number in plaintext via plain `SQLiteOpenHelper` (no
SQLCipher/at-rest encryption), and `android:allowBackup="true"` is paired
with default/empty `data_extraction_rules.xml` and `backup_rules.xml` — no
`<exclude>` rules configured for either file. `blocked_senders` (a list of
phone numbers a user has explicitly blocked) has the same exposure.

**Recommendation:** Exclude the database from cloud/local backup via
`<exclude>` rules, and/or encrypt it at rest.

### 5. [LOW] `torch==2.11.0` has a known critical vulnerability (dependency scan)

`pip-audit -r requirements.txt` against `main`'s pinned versions found:

| Package | Version | Advisory | Fixed in |
|---|---|---|---|
| `torch` | 2.11.0 | `PYSEC-2025-194` | 2.13.0 |
| `setuptools` | 81.0.0 (transitive) | `PYSEC-2026-3447` | 83.0.0 |

`PYSEC-2025-194` describes a critical, publicly-disclosed memory corruption
vulnerability in `torch.jit.script`, exploitable locally. `PYSEC-2026-3447`
is a build-time (`sdist`) packaging issue affecting package maintainers on
macOS, not this app's runtime — informational only, low priority here.

**Impact:** Only relevant if `torch.jit.script` is actually invoked (e.g. in
`train_model.py` / `distilbert_model_prototype.py`) with untrusted input —
worth confirming, since "local host" exploitation typically requires running
attacker-controlled model artifacts or scripted modules.

**Recommendation:** Bump `torch` to `>=2.13.0` in `requirements.txt`;
low-effort, no known reason not to.

### 6. [INFO — positive finding] API endpoint URLs are kept out of version control

`build.gradle.kts` reads `CLASSIFIER_API_URL` / `LLM_API_URL` /
`SCAN_API_URL` from an `appProperties` file that is **not** committed to the
repo (only standard Gradle config like `gradle.properties` is tracked). Good
practice — endpoint URLs (and any credentials they might imply) aren't
sitting in git history.

### 7. [INFO — positive finding] Parameterized SQL throughout; no injection risk

Every query across `DatabaseHelper.kt` (`analyzed_messages` and
`blocked_senders`) uses parameterized `selectionArgs` / `?` placeholders, not
string concatenation. No SQL injection risk found.

### 8. [INFO — positive finding] Child activities are correctly non-exported

`SuspiciousMessagesActivity` and `MessageDetailActivity` are both
`android:exported="false"` with `parentActivityName` set — only reachable
from within the app, not launchable by other apps via intent. Only
`MainActivity` (the launcher) is exported, as required.

### 9. [INFO — positive finding] No hardcoded secrets found

No API keys, tokens, or passwords hardcoded in the reviewed `.kt`/`.xml`/
`.gradle.kts` files.

## Summary

| # | Finding | Severity |
|---|---|---|
| 1 | Cleartext HTTP explicitly permitted (manifest + network security config) | HIGH |
| 2 | SMS content/URLs sent to unauthenticated third-party APIs | HIGH |
| 3 | `READ_SMS` + full-inbox `ContentObserver` polling — broad data access | MEDIUM |
| 4 | Raw PII stored unencrypted on-device, not excluded from backup | MEDIUM |
| 5 | `torch==2.11.0` has a known critical vulnerability | LOW |
| 6 | API URLs kept out of version control | — (verified safe) |
| 7 | Parameterized SQL throughout | — (verified safe) |
| 8 | Non-launcher activities correctly non-exported | — (verified safe) |
| 9 | No hardcoded secrets | — (verified safe) |

## Out of scope / follow-up recommended

- No dynamic testing (MITM capture against the real `CLASSIFIER_API_URL`/
  `SCAN_API_URL` endpoints, fuzzing, or MobSF static APK analysis) — would
  need a built APK and the actual (untracked) endpoint values, neither of
  which are available in this environment.
- Recommend confirming with the endpoint owners whether auth exists at an
  infrastructure layer not visible from the client code (e.g. mTLS, a
  gateway API key injected server-side).
