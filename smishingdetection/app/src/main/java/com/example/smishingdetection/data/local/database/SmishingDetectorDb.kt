package com.example.smishingdetection.data.local.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.local.model.BlockedPhoneNumber

@Database(
    entities = [AnalyzedMessage::class, BlockedPhoneNumber::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(3, 4)
    ]
)
abstract class SmishingDetectorDb : RoomDatabase() {
    abstract fun analyzedMessageDao(): AnalyzedMessageDao
    abstract fun blockedPhoneNumberDao(): BlockedPhoneNumberDao
    companion object {
        @Volatile
        private var INSTANCE: SmishingDetectorDb? = null
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create block list table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blockedPhoneNumbers(" +
                            "phone TEXT NOT NULL PRIMARY KEY)"
                )
                // Create temp table for new analyzed_messages
                db.execSQL(
                    "CREATE TABLE temp_messages("+
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"+
                    "phone_number TEXT NOT NULL,"+
                    "date TEXT NOT NULL,"+
                    "message TEXT NOT NULL,"+
                    "risk_score REAL NOT NULL,"+
                    "status TEXT NOT NULL,"+
                    "explanation TEXT NOT NULL DEFAULT '',"+
                    "url_scan_result TEXT NOT NULL DEFAULT '')"
                )
                // Copy old rows from version 2 database
                db.execSQL("""
                    INSERT INTO temp_messages(id, phone_number, date, message, risk_score, status, explanation, url_scan_result)
                    SELECT id, phone_number, date, message, risk_score, status, explanation, url_scan_result
                    FROM analyzed_messages
                    WHERE prediction != 'SAFE'
                """.trimIndent()
                )
                // Replace with version 3 table
                db.execSQL("DROP TABLE analyzed_messages")
                db.execSQL("ALTER TABLE temp_messages RENAME to analyzed_messages")
            }
        }
        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create temp table for new analyzed_messages
                db.execSQL(
                    "CREATE TABLE version5_analyzed_messages("+
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"+
                            "phone_number TEXT NOT NULL DEFAULT 'Unknown',"+
                            "date TEXT NOT NULL,"+
                            "message TEXT NOT NULL,"+
                            "risk_score REAL NOT NULL,"+
                            "status TEXT NOT NULL,"+
                            "explanation TEXT NOT NULL DEFAULT 'No explanation available.',"+
                            "url_scan_result TEXT NOT NULL DEFAULT 'No scan result available.')"
                )
                // Copy old rows from version 4 database
                db.execSQL("""
                    INSERT INTO version5_analyzed_messages(id, phone_number, date, message, risk_score, status, explanation, url_scan_result)
                    SELECT id, phone_number, date, message, risk_score, status, explanation, url_scan_result
                    FROM analyzed_messages
                """.trimIndent()
                )
                // Replace with version 3 table
                db.execSQL("DROP TABLE analyzed_messages")
                db.execSQL("ALTER TABLE version5_analyzed_messages RENAME to analyzed_messages")
            }
        }
        fun getDatabase(context: Context): SmishingDetectorDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmishingDetectorDb::class.java,
                    "smishing_detector.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }
    }
}