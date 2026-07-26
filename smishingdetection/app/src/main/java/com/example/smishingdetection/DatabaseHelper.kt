package com.example.smishingdetection

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "smishing_detector.db"
        const val DATABASE_VERSION = 3   // was 2 — bumped for blocked_senders table

        const val TABLE_NAME = "analyzed_messages"
        const val TABLE_BLOCKED_SENDERS = "blocked_senders"

        const val COL_ID = "id"
        const val COL_PHONE = "phone_number"
        const val COL_DATE = "date"
        const val COL_MESSAGE = "message"
        const val COL_RISK_SCORE = "risk_score"
        const val COL_PREDICTION = "prediction"
        const val COL_STATUS = "status"
        const val COL_EXPLANATION = "explanation"
        const val COL_URL_SCAN = "url_scan_result"

        const val STATUS_PENDING = "pending"
        const val STATUS_QUARANTINED = "quarantined"
        const val STATUS_CAUTION = "caution"
        const val STATUS_SAFE = "safe"

        fun statusFromScore(riskScore: Double): String = when {
            riskScore >= 70.0 -> STATUS_QUARANTINED
            riskScore >= 35.0 -> STATUS_CAUTION
            else -> STATUS_SAFE
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
                $COL_EXPLANATION TEXT   NOT NULL DEFAULT '',
                $COL_URL_SCAN TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_BLOCKED_SENDERS (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                phone       TEXT    NOT NULL UNIQUE,
                blocked_at  TEXT    NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Rebuild analyzed_messages on any upgrade (existing behavior — this wipes message history)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
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
            $COL_EXPLANATION TEXT   NOT NULL DEFAULT '',
            $COL_URL_SCAN TEXT NOT NULL DEFAULT ''
        )
        """.trimIndent()
        )
        // Blocked senders table survives upgrades — only created if missing
        db.execSQL(
            """
        CREATE TABLE IF NOT EXISTS $TABLE_BLOCKED_SENDERS (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            phone       TEXT    NOT NULL UNIQUE,
            blocked_at  TEXT    NOT NULL
        )
        """.trimIndent()
        )
    }

    // ── Write ────────────────────────────────────────────────────────────────

    fun insertMessage(
        phoneNumber: String,
        date: String,
        message: String,
        riskScore: Double,
        prediction: String,
        explanation: String,
        urlScanResult: String = ""
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
            put(COL_URL_SCAN, urlScanResult)
        }
        return writableDatabase.insert(TABLE_NAME, null, values)
    }

    fun insertPendingMessage(
        phoneNumber: String,
        date: String,
        message: String
    ): Long {
        val values = ContentValues().apply {
            put(COL_PHONE, phoneNumber)
            put(COL_DATE, date)
            put(COL_MESSAGE, message)
            put(COL_RISK_SCORE, 0.0)
            put(COL_PREDICTION, "PENDING")
            put(COL_STATUS, STATUS_PENDING)
            put(COL_EXPLANATION, "Analysis queued...")
            put(COL_URL_SCAN, "")
        }
        return writableDatabase.insert(TABLE_NAME, null, values)
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    fun findByMessage(message: String): Map<String, String>? {
        val cursor = readableDatabase.query(
            TABLE_NAME, null, "$COL_MESSAGE = ?", arrayOf(message),
            null, null, "$COL_DATE DESC", "1"
        )
        return cursor.use {
            if (!it.moveToFirst()) return null
            mapOf(
                COL_PHONE to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                COL_DATE to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                COL_MESSAGE to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                COL_RISK_SCORE to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                COL_PREDICTION to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                COL_STATUS to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
                COL_URL_SCAN to it.getString(it.getColumnIndexOrThrow(COL_URL_SCAN)),
            )
        }
    }

    fun getByStatus(status: String): List<Map<String, String>> {
        val cursor = readableDatabase.query(
            TABLE_NAME, null, "$COL_STATUS = ?", arrayOf(status),
            null, null, "$COL_DATE DESC"
        )
        return cursor.use {
            val results = mutableListOf<Map<String, String>>()
            while (it.moveToNext()) {
                results.add(
                    mapOf(
                        COL_ID to it.getLong(it.getColumnIndexOrThrow(COL_ID)).toString(),
                        COL_PHONE to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                        COL_DATE to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        COL_MESSAGE to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                        COL_RISK_SCORE to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                        COL_PREDICTION to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                        COL_STATUS to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                        COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
                        COL_URL_SCAN to it.getString(it.getColumnIndexOrThrow(COL_URL_SCAN)),
                    )
                )
            }
            results
        }
    }

    fun getAllMessages(): List<Map<String, String>> {
        val cursor = readableDatabase.query(TABLE_NAME, null, null, null, null, null, "$COL_DATE DESC")
        return cursor.use {
            val results = mutableListOf<Map<String, String>>()
            while (it.moveToNext()) {
                results.add(
                    mapOf(
                        COL_ID to it.getLong(it.getColumnIndexOrThrow(COL_ID)).toString(),
                        COL_PHONE to it.getString(it.getColumnIndexOrThrow(COL_PHONE)),
                        COL_DATE to it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        COL_MESSAGE to it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)),
                        COL_RISK_SCORE to it.getDouble(it.getColumnIndexOrThrow(COL_RISK_SCORE)).toString(),
                        COL_PREDICTION to it.getString(it.getColumnIndexOrThrow(COL_PREDICTION)),
                        COL_STATUS to it.getString(it.getColumnIndexOrThrow(COL_STATUS)),
                        COL_EXPLANATION to it.getString(it.getColumnIndexOrThrow(COL_EXPLANATION)),
                        COL_URL_SCAN to it.getString(it.getColumnIndexOrThrow(COL_URL_SCAN)),
                    )
                )
            }
            results
        }
    }

    fun countByStatus(status: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COL_STATUS = ?", arrayOf(status)
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ── Update / Delete ──────────────────────────────────────────────────────

    fun updateStatus(id: Long, newStatus: String): Int {
        val values = ContentValues().apply { put(COL_STATUS, newStatus) }
        return writableDatabase.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updateAnalyzedMessage(
        id: Long,
        riskScore: Double,
        prediction: String,
        explanation: String,
        urlScanResult: String
    ): Int {
        val status = statusFromScore(riskScore)
        val values = ContentValues().apply {
            put(COL_RISK_SCORE, riskScore)
            put(COL_PREDICTION, prediction)
            put(COL_STATUS, status)
            put(COL_EXPLANATION, explanation)
            put(COL_URL_SCAN, urlScanResult)
        }
        return writableDatabase.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteMessage(id: Long): Int {
        return writableDatabase.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // ── Blocked senders  ─────────────────────────────────────────────────────

    /** Adds a phone number to the local blocked list. Safe to call more than once. */
    fun blockSender(phoneNumber: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val values = ContentValues().apply {
            put("phone", phoneNumber)
            put("blocked_at", timestamp)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_BLOCKED_SENDERS, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    /** Returns true if this number is on the app's local blocked list. */
    fun isSenderBlocked(phoneNumber: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_BLOCKED_SENDERS, arrayOf("id"), "phone = ?", arrayOf(phoneNumber),
            null, null, null, "1"
        )
        return cursor.use { it.moveToFirst() }
    }
}