package com.example.smishingdetection.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.smishingdetection.SuspiciousMessagesActivity
import kotlinx.coroutines.flow.Flow

/**
 * Local SQLite database that stores detected smishing messages.
 *
 * Table: analyzed_messages
 * Columns:
 *   id              - auto-increment primary key
 *   phone_number    - sender address
 *   date            - ISO-8601 timestamp of when the message was received
 *   message         - original SMS body
 *   risk_score      - 0–100 combined score from the API
 *   status          - "caution" | "quarantined"
 *   explanation     - plain-English LLM explanation
 *   url_scan_result - returns the results of the url scan from urlscan.io
 *
 * Status rules:
 *   35 ≤ risk_score < 70     → caution
 *   risk_score ≥ 70          → quarantined
 */
@Entity(tableName = "analyzed_messages")
data class AnalyzedMessage(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "risk_score") val riskScore: Double,
    @ColumnInfo(name = "status") val status: String = when {
        riskScore >= 70.0 -> "quarantine"
        else -> "caution"
    },
    @ColumnInfo(name = "explanation") val explanation: String,
    @ColumnInfo(name = "url_scan_result") val urlScanResult: String
)