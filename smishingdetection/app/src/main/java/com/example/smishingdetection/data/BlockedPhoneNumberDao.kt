package com.example.smishingdetection.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BlockedPhoneNumberDao{
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoneNumber(phone: BlockedPhoneNumber)

    @Delete
    suspend fun delete(phone: BlockedPhoneNumber)

    @Query("SELECT * FROM blockedPhoneNumbers")
    fun getAll(): List<BlockedPhoneNumber>

    @Query("SELECT EXISTS(SELECT 1 FROM blockedPhoneNumbers WHERE phone = :phone)")
    suspend fun exists(phone: String): Boolean
}