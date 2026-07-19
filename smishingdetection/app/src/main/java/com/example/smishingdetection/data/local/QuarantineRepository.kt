package com.example.smishingdetection.data.local

import android.os.Build
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.local.database.AnalyzedMessageDao
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Interface to the data layer
 */
interface AnalyzedMessageRepository {
    suspend fun insertMessage(
        phoneNumber: String?,
        date: String,
        message: String,
        riskScore: Double,
        explanation: String?,
        urlVerdict: UrlAnalyzerResponse?
    ): Long
    suspend fun getMessageById(messageId: Long): AnalyzedMessage
    suspend fun quarantineMessage(messageId: Long)
    suspend fun markAsSafe(messageId: Long)
    suspend fun countByStatus(status: String): Int
    suspend fun getAllMessages(): List<AnalyzedMessage>
    suspend fun getMessagesByStatus(status: String) : List<AnalyzedMessage>
}

class QuarantineRepository(
    private val localDataSource: AnalyzedMessageDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): AnalyzedMessageRepository {

    override suspend fun insertMessage(
        phoneNumber: String?,
        date: String,
        message: String,
        riskScore: Double,
        explanation: String?,
        urlVerdict: UrlAnalyzerResponse?
    ): Long =
        withContext(ioDispatcher) {
        // Separate datetime formatting for API 25 and 24
        val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        }

        // Format phone number
        val sender = phoneNumber ?: "Unknown"

        // Format riskScore (double)
        val riskScorePercent = (riskScore*100)

        // Format url API response TODO: change db to store each value from API response
        val scanResult = if(urlVerdict == null) {
            ""
        } else {
            "Scan result returned Malicious:${urlVerdict.malicious} for ${urlVerdict.url} submitted with an overall score of ${urlVerdict.score}"
        }

        val message = AnalyzedMessage(
            id = 0,
            phoneNumber = sender,
            date = timestamp,
            message = message,
            riskScore = riskScorePercent,
            explanation = explanation ?: "",
            urlScanResult = scanResult
        )
        val messageId = localDataSource.insertMessage(message)
        return@withContext messageId
    }

    override suspend fun getMessageById(messageId: Long): AnalyzedMessage {
        // TODO check for null id
        return localDataSource.getById(messageId)
    }

    override suspend fun quarantineMessage(messageId: Long) {
        localDataSource.updateStatus(messageId, status = "quarantine")
    }

    override suspend fun markAsSafe(messageId: Long) {
        val message = localDataSource.getById(messageId)
        localDataSource.delete(message)
    }

    override suspend fun countByStatus(status: String): Int {
        return localDataSource.countByStatus(status)
    }

    override suspend fun getAllMessages(): List<AnalyzedMessage> {
        return localDataSource.getAll()

    }

    override suspend fun getMessagesByStatus(status: String): List<AnalyzedMessage> {
        return localDataSource.getByStatus(status)
    }
}