package com.example.smishingdetection

import android.app.Application
import com.example.smishingdetection.data.AnalyzedMessage
import com.example.smishingdetection.data.BlockedPhoneNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class DatabaseHelper(application: Application) {
    private val db = (application as SmishingDetectorApplication).database
    private val messageDao = db.analyzedMessageDao()
    private val phoneDao = db.blockedPhoneNumberDao()

    suspend fun insertMessage(id: Long): Long {
        return messageDao.insertMessage(messageDao.getById(id))
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

    fun getCautionMessages(): Flow<List<AnalyzedMessage>> {
        return messageDao.getbyStatus("caution").distinctUntilChanged()
    }

    fun getQuarantineMessages(): Flow<List<AnalyzedMessage>> {
        return messageDao.getbyStatus("quarantine").distinctUntilChanged()
    }

    fun countByStatus(status: String): Flow<Int> {
        return messageDao.countByStatus(status).distinctUntilChanged()
    }

    suspend fun blockPhoneNumber(phone: BlockedPhoneNumber) {
        phoneDao.insertPhoneNumber(phone)
    }

    suspend fun removeFromBlockList(phone: BlockedPhoneNumber) {
        phoneDao.delete(phone)
    }

    suspend fun checkBlockedPhone(phone: BlockedPhoneNumber): Boolean {
        return phoneDao.exists(phone)
    }
}