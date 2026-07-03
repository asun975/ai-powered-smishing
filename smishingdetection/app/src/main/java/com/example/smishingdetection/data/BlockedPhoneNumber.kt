package com.example.smishingdetection.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blockedPhoneNumbers")
data class BlockedPhoneNumber(
    @PrimaryKey val phone: String
)