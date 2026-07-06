package com.example.smishingdetection.data.local

import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.local.database.AnalyzedMessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * Interface to the data layer
 */
interface AnalyzedMessageRepository {
    suspend fun insertMessage(
        phoneNumber: String,
        date: String,
        message: String,
        riskScore: Double
    ): Long
    suspend fun getMessageById(messageId: Long): AnalyzedMessage
    suspend fun quarantineMessage(messageId: Long)
    suspend fun markAsSafe(messageId: Long)
    suspend fun countByStatus(status: String): Int
    fun getAllMessages(): Flow<List<AnalyzedMessage>>
    fun getMessagesByStatus(status: String) : Flow<List<AnalyzedMessage>>
}

class QuarantineRepository(
    private val localDataSource: AnalyzedMessageDao
): AnalyzedMessageRepository {

    override suspend fun insertMessage(
        phoneNumber: String,
        date: String,
        message: String,
        riskScore: Double
    ): Long {
        val message = AnalyzedMessage(
            id = 0,
            phoneNumber = phoneNumber,
            date = date,
            message = message,
            riskScore = riskScore // check activity for risk score implementation
        )
        val messageId = localDataSource.insertMessage(message)
        return messageId
    }

    override suspend fun getMessageById(messageId: Long): AnalyzedMessage {
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

    override fun getAllMessages(): Flow<List<AnalyzedMessage>> {
        return localDataSource.getAll().distinctUntilChanged()
    }

    override fun getMessagesByStatus(status: String): Flow<List<AnalyzedMessage>> {
        return localDataSource.getByStatus(status)
    }
}