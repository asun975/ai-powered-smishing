package com.example.smishingdetection

import android.app.Application
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.local.model.BlockedPhoneNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class DatabaseHelper(application: Application) {
    private val db = (application as SmishingDetectorApplication).database
    private val messageDao = db.analyzedMessageDao()
    private val blockedPhoneDao = db.blockedPhoneNumberDao()

    suspend fun insertMessage(message: AnalyzedMessage): Long {
        return messageDao.insertMessage(message)
    }

    suspend fun getMessage(id: Long): AnalyzedMessage {
        return messageDao.getById(id)
    }

    fun getAllMessages(): Flow<List<AnalyzedMessage>> {
        return messageDao.getAll().distinctUntilChanged()
    }

    suspend fun quarantineMessage(id: Long) {
        messageDao.updateStatus(id, "quarantine")
    }

    suspend fun markAsSafe(id: Long) {
        messageDao.delete(messageDao.getById(id))
    }

    fun getByStatus(status:String): Flow<List<AnalyzedMessage>> {
        return messageDao.getByStatus(status).distinctUntilChanged()
    }

    fun countByStatus(status: String): Flow<Int> {
        return messageDao.countByStatus(status).distinctUntilChanged()
    }

    suspend fun addToBlockList(phoneNumber: String) {
        val phone = BlockedPhoneNumber(phoneNumber)
        blockedPhoneDao.insertPhoneNumber(phone)
    }

    suspend fun removeFromBlockList(phone: BlockedPhoneNumber) {
        blockedPhoneDao.delete(phone)
    }

    suspend fun checkBlockedPhone(phone: String): Boolean {
        return blockedPhoneDao.exists(phone)
    }
}