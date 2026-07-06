package com.example.smishingdetection.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blockedPhoneNumbers")
data class BlockedPhoneNumber(
    @PrimaryKey val phone: String
)