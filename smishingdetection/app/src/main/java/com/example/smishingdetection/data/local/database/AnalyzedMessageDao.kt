package com.example.smishingdetection.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyzedMessageDao {

    /**
     * Insert a new analyzed message. Returns the new row ID, or -1 on failure.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: AnalyzedMessage): Long

    /** Delete a message by its row ID. */
    @Delete
    suspend fun delete(message: AnalyzedMessage)

    /**
     * Get all messages, newest first.
     */
    @Query("SELECT * FROM analyzed_messages ORDER BY date")
    suspend fun getAll(): List<AnalyzedMessage>

    /**
     * Get all messages with a given status ("safe" | "caution" | "quarantined"),
     * newest first.
     */
    @Query("SELECT * FROM analyzed_messages WHERE status = :status ORDER BY date")
    suspend fun getByStatus(status: String): List<AnalyzedMessage>

    @Query("SELECT COUNT(*) FROM analyzed_messages WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT * FROM analyzed_messages WHERE id = :id")
    suspend fun getById(id: Long): AnalyzedMessage

    /** Update the status of a message (e.g. promote caution → quarantined). */
    @Query(
        """
        UPDATE analyzed_messages
        SET status = :status
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: String
    )
}