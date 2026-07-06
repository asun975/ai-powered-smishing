package com.example.smishingdetection.data.local

import com.example.smishingdetection.data.local.model.BlockedPhoneNumber
import com.example.smishingdetection.data.local.database.BlockedPhoneNumberDao

interface BlockRepository {
    suspend fun addToBlockList(phoneNumber: String)
    suspend fun removeFromBlockList(phoneNumber: String)
    suspend fun checkBlockList(phoneNumber: String): Boolean
}

class DefaultBlockRepository(
    private val localDataSource: BlockedPhoneNumberDao
): BlockRepository {
    override suspend fun addToBlockList(phoneNumber: String) {
        // TODO edge case: unexpected phone number format
        val phone = BlockedPhoneNumber(phoneNumber)
        localDataSource.insertPhoneNumber(phone)
    }

    override suspend fun removeFromBlockList(phoneNumber: String) {
        // TODO edge case: unexpected phone number format
        val phone = BlockedPhoneNumber(phoneNumber)
        localDataSource.delete(phone)
    }

    override suspend fun checkBlockList(phoneNumber: String): Boolean {
        // TODO edge case: unexpected phone number format
        return localDataSource.exists(phoneNumber)
    }
}