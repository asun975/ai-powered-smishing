package com.example.smishingdetector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite database that stores every analyzed SMS message.
 *
 * Table: analyzed_messages
 * Columns:
 *   id            - auto-increment primary key
 *   phone_number  - sender address
 *   date          - ISO-8601 timestamp of when the message was received
 *   message       - original SMS body
 *   risk_score    - 0–100 combined score from the API
 *   prediction    - "SAFE", "SPAM"
 *   status        - "safe" | "caution" | "quarantined"
 *   explanation   - plain-English LLM explanation
 *
 * Status rules:
 *   risk_score < 35          → safe
 *   35 ≤ risk_score < 70     → caution
 *   risk_score ≥ 70          → quarantined
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "smishing_detector.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "analyzed_messages"

        const val COL_ID = "id"
        const val COL_PHONE = "phone_number"
        const val COL_DATE = "date"
        const val COL_MESSAGE = "message"
        const val COL_RISK_SCORE = "risk_score"
        const val COL_PREDICTION = "prediction"
        const val COL_STATUS = "status"
        const val COL_EXPLANATION = "explanation"

        fun statusFromScore(riskScore: Double): String = when {
            riskScore >= 70.0 -> "quarantined"
            riskScore >= 35.0 -> "caution"
            else -> "safe"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE      TEXT    NOT NULL,
                $COL_DATE       TEXT    NOT NULL,
                $COL_MESSAGE    TEXT    NOT NULL,
                $COL_RISK_SCORE REAL    NOT NULL DEFAULT 0,
                $COL_PREDICTION TEXT    NOT NULL DEFAULT 'SAFE',
                $COL_STATUS     TEXT    NOT NULL DEFAULT 'safe',
                $COL_EXPLANATION TEXT   NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Insert a new analyzed message. Returns the new row ID, or -1 on failure.
     */
    fun insertMessage(
        phoneNumber: String,
        date: String,
        message: String,
        riskScore: Double,
        prediction: String,
        explanation: String,
    ): Long {
        val status = statusFromScore(riskScore)
        val values = ContentValues().apply {
            put(COL_PHONE, phoneNumber)
            put(COL_DATE, date)
            put(COL_MESSAGE, message)
            put(COL_RISK_SCORE, riskScore)
            put(COL_PREDICTION, prediction)
            put(COL_STATUS, status)
            put(COL_EXPLANATION, explanation)
        }
        return writableDatabase.insert(TABLE_NAME, null, values)
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Look up a previously analyzed message by its exact body text.
     * Returns a map of column→value, or null if not found (cache miss).
     */
    fun findByMessage(message: String): Map<String, String>? {
        val cursor = readableDatabase.query(
            TABLE_NAME,
            null,
            "$COL_MESSAGE = ?",
            arrayOf(message),
            null, null,
            "$COL_DATE DESC",
            "1"           // only the most recent matching row
        )
        return cursor.use {
            if (!it.moveToFirst()) return null
            mapOf(
                COL_PHONE       to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                COL_DATE        to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                COL_MESSAGE     to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                COL_RISK_SCORE  to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                COL_PREDICTION  to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                COL_STATUS      to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
            )
        }
    }

    /**
     * Get all messages with a given status ("safe" | "caution" | "quarantined"),
     * newest first.
     */
    fun getByStatus(status: String): List<Map<String, String>> {
        val cursor = readableDatabase.query(
            TABLE_NAME,
            null,
            "$COL_STATUS = ?",
            arrayOf(status),
            null, null,
            "$COL_DATE DESC"
        )
        return cursor.use {
            val results = mutableListOf<Map<String, String>>()
            while (it.moveToNext()) {
                results.add(
                    mapOf(
                        COL_ID          to it.getLong(it.getColumnIndexOrThrow(COL_ID)).toString(),
                        COL_PHONE       to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                        COL_DATE        to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        COL_MESSAGE     to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                        COL_RISK_SCORE  to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                        COL_PREDICTION  to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                        COL_STATUS      to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                        COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
                    )
                )
            }
            results
        }
    }

    /**
     * Get all messages regardless of status, newest first.
     */
    fun getAllMessages(): List<Map<String, String>> {
        val cursor = readableDatabase.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_DATE DESC"
        )
        return cursor.use {
            val results = mutableListOf<Map<String, String>>()
            while (it.moveToNext()) {
                results.add(
                    mapOf(
                        COL_ID          to it.getLong(it.getColumnIndexOrThrow(COL_ID)).toString(),
                        COL_PHONE       to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                        COL_DATE        to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        COL_MESSAGE     to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                        COL_RISK_SCORE  to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                        COL_PREDICTION  to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                        COL_STATUS      to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                        COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
                    )
                )
            }
            results
        }
    }

    /** Total count of messages by status — useful for your teammate's UI. */
    fun countByStatus(status: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COL_STATUS = ?",
            arrayOf(status)
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
