# Performance Testing Report — Week 9

**Owner:** Yugveer Singh Sidhu
**Task:** Performance testing (document results)
**Branch analyzed:** `main`
**Date:** 2026-07-25

> **Note:** `main` is a materially different, more advanced codebase than the
> `Yugveer` branch — it uses a native Kotlin pipeline
> (`Preprocessing.kt` → `SmishingClassifier.kt` → `UrlAnalyzer.kt` →
> `DatabaseHelper.kt`) calling **externally-hosted** classification/LLM/URL-scan
> APIs (via `BuildConfig.CLASSIFIER_API_URL` / `LLM_API_URL` / `SCAN_API_URL`),
> rather than the local FastAPI + DistilBERT setup used on `Yugveer`. This
> report is a fresh analysis of `main`'s actual code, not a relabeled copy of
> the `Yugveer`-branch report.

## Scope

What's benchmarked here: `Preprocessing.kt` (PII stripping / URL extraction,
runs on-device before any network call) and the SQLite operations in
`DatabaseHelper.kt` (`analyzed_messages` + `blocked_senders` tables).

**Not covered:** end-to-end classification/LLM/URL-scan latency. Those three
API base URLs are supplied via an untracked Gradle properties file
(`appProperties.getProperty("CLASSIFIER_API_URL")` etc. in `build.gradle.kts`)
— they are intentionally not committed to the repo, so this environment has
no way to know what they point to or reach them. See **Recommendation**.

**Environment:** JVM 21 (OpenJDK), Kotlin compiler 1.3, Python 3.11.15,
x86_64 Linux, 4 vCPUs.

## Method

`Preprocessing.kt` was compiled and run as-is (verbatim, unmodified) via
`kotlinc` + the JVM — a real compiled/executed benchmark, not a Python
re-implementation. (One compile-only shim was needed: the apt-packaged
`kotlinc` here is version 1.3.31, which predates `String.lowercase()`,
added in Kotlin 1.5 stdlib. A same-package extension function was added
*only in the benchmark harness* to satisfy that reference — `Preprocessing.kt`
itself was not modified.) 10 representative SMS samples were run through
each function 2,000× (20,000 calls total), timed with `System.nanoTime()`,
after a 200-iteration JIT warmup.

SQLite operations were benchmarked against an in-memory database built with
main's exact schema (including the `url_scan_result` column and the separate
`blocked_senders` table), executed directly with Python's `sqlite3` module.

## Results

### `Preprocessing.kt` (compiled Kotlin, real execution)

| Function | Mean | Median | p95 | Max |
|---|---|---|---|---|
| `preprocessClassifierText` | 0.0138 ms | 0.0131 ms | 0.0246 ms | 2.2672 ms |
| `preprocessLlmText` | 0.0121 ms | 0.0108 ms | 0.0213 ms | 0.3180 ms |
| `extractUrl` | 0.0051 ms | 0.0041 ms | 0.0093 ms | 0.2833 ms |

All three are sub-millisecond at every percentile except one outlier max
(likely a GC pause) — preprocessing is not a bottleneck.

### `DatabaseHelper.kt` SQLite operations

| Operation | n | Mean | Median | p95 | Max |
|---|---|---|---|---|---|
| `insertMessage` (with `url_scan_result`) | 5,000 | 0.0027 ms | 0.0023 ms | 0.0047 ms | 0.1750 ms |
| `updateStatus` (by id) | 1,000 | 0.0026 ms | 0.0016 ms | 0.0057 ms | 0.0573 ms |
| `findByMessage` (cache lookup, by exact text) | 1,000 | **0.2730 ms** | 0.2578 ms | 0.4080 ms | 0.6092 ms |
| `blockSender` | 1,000 | 0.0027 ms | 0.0021 ms | 0.0065 ms | 0.0681 ms |
| `isSenderBlocked` | 1,000 | 0.0025 ms | 0.0021 ms | 0.0039 ms | 0.0958 ms |

### Finding: `findByMessage` does a full table scan

`onCreate()` in `DatabaseHelper.kt` does not create an index on the `message`
column, and `findByMessage` looks rows up by exact message text
(`WHERE message = ?`). At 5,000 stored messages this already costs ~0.27ms
per lookup on average (vs. microseconds for the indexed-equivalent
`blocked_senders.phone`, which is `UNIQUE` and therefore auto-indexed). This
scales linearly with message history size — on a long-lived install with
tens of thousands of messages, cache-hit lookups (which exist specifically
to *avoid* a network round-trip) could become slow enough to erode their
own benefit.

**Recommendation:** Add `CREATE INDEX idx_message ON analyzed_messages(message)`
in `onCreate()` (and in the upgrade path, since `onUpgrade` recreates the
table). This is a one-line, low-risk fix.

## Recommendation for completing end-to-end performance testing

1. Obtain the actual `CLASSIFIER_API_URL` / `LLM_API_URL` / `SCAN_API_URL`
   values from whoever manages the deployment (they live in an untracked
   properties file, correctly kept out of version control).
2. Time `SmishingClassifier.classify()` and `UrlAnalyzer.analyzeUrl()`
   end-to-end against those live endpoints — both already have 30-second
   timeouts configured, so pay attention to p95/p99, not just mean, since a
   slow classification API directly delays the on-device "Analyzing..."
   state the user sees.
3. Re-run the SQLite benchmarks above on a real device/emulator (not a JVM
   approximation) to capture actual flash I/O overhead, especially after
   adding the `message` index above.
