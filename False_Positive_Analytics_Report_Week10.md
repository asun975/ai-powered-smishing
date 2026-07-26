# False Positive Analytics Report — Week 10

**Owner:** Yugveer Singh Sidhu
**Task:** Track false positive results (implement analytics)
**Branch analyzed:** `main`
**Date:** 2026-07-25

## Definition

A **false positive** is a message the detector flagged as risky
(`status = "caution"` or `"quarantined"`) that a human later determined was
not actually spam and moved back to `"safe"`.

## Difference from the `Yugveer`-branch version of this feature

On `main`, `DatabaseHelper.kt` has **no `markAsSafe()` convenience method** —
only a generic `updateStatus(id: Long, newStatus: String)`, plus a completely
separate `blocked_senders` table (`blockSender()` / `isSenderBlocked()`) for
blocking by phone number. Since there's no single dedicated "mark safe" entry
point to hook into, the false-positive check has to live inside the generic
`updateStatus()` call itself.

## Implementation

Add a new column (bump `DATABASE_VERSION` 3 → 4) and check the row's current
status before applying the update:

```kotlin
const val COL_FALSE_POSITIVE = "is_false_positive"
// column added to analyzed_messages: is_false_positive INTEGER NOT NULL DEFAULT 0

fun updateStatus(id: Long, newStatus: String): Int {
    val currentStatus = readableDatabase.query(
        TABLE_NAME, arrayOf(COL_STATUS), "$COL_ID = ?", arrayOf(id.toString()),
        null, null, null
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    val wasFlaggedSpam = currentStatus in setOf("caution", "quarantined")

    val values = ContentValues().apply {
        put(COL_STATUS, newStatus)
        // Only a risky→safe transition is a false positive. Risky→blocked is a
        // correct detection the user acted on, not a model error.
        if (newStatus == "safe" && wasFlaggedSpam) put(COL_FALSE_POSITIVE, 1)
    }
    return writableDatabase.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id.toString()))
}
```

Plus the same three analytics reads as before:

| Method | Returns |
|---|---|
| `countFalsePositives()` | Total false positives recorded |
| `getFalsePositives()` | The actual false-positive rows, newest first |
| `falsePositiveRate()` | False positives as % of all analyzed messages |

Critically, this distinguishes a **correct** detection the user acted on
(quarantined → blocked, via `blockSender`/`updateStatus(id, "blocked")`) from
an **incorrect** one (quarantined → safe) — only the latter is a model error
worth tracking.

## Testing

Verified against an executable SQLite harness built from main's *actual*
schema (`analyzed_messages` with `url_scan_result`, plus the separate
`blocked_senders` table) — real `sqlite3` execution:

| # | Test | Result |
|---|---|---|
| 1 | `updateStatus(id, "safe")` on a quarantined message records 1 false positive | PASS |
| 2 | `updateStatus(id, "safe")` on an already-safe message does not double-count | PASS |
| 3 | `updateStatus(id, "blocked")` on a quarantined message does **not** count — it's a correct detection | PASS |
| 4 | `falsePositiveRate()` correctly computes percentage (1 of 3 messages → 33.33%) | PASS |
| 5 | `blocked_senders` table operations are unaffected by / independent of false-positive tracking | PASS |

5/5 passed.

## Known follow-ups (not yet implemented)

- No UI surface yet displays these numbers — `SuspiciousMessagesActivity`
  would be the natural place to add a small analytics summary (e.g. "X false
  positives out of Y flagged messages").
- This tracks only detector-flagged-then-safe reversals. It does not track
  the inverse error (a genuinely safe message the user manually blocked via
  `blockSender`, which would be a *false negative* on the user's part, not
  the model's) — flag if the team wants that tracked too.
- Local, per-device analytics only — no cross-device aggregation, since
  there's no accounts/backend system to attribute data to.
