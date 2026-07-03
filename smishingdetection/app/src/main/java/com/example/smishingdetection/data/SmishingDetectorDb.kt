package com.example.smishingdetection.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AnalyzedMessage::class, BlockedPhoneNumber::class], version = 1, exportSchema = false)
abstract class SmishingDetectorDb : RoomDatabase() {
    abstract fun analyzedMessageDao(): AnalyzedMessageDao
    abstract fun blockedPhoneNumberDao(): BlockedPhoneNumberDao
    companion object {
        @Volatile
        private var INSTANCE: SmishingDetectorDb? = null
        fun getDatabase(context: Context): SmishingDetectorDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmishingDetectorDb::class.java,
                    "smishing_detector.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}