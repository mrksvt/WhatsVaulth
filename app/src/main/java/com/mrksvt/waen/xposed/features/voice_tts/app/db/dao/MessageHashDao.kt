package com.mrksvt.waen.xposed.features.voice_tts.app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.MessageHashEntity

@Dao
interface MessageHashDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: MessageHashEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM message_hash WHERE message_hash = :hash)")
    fun exists(hash: String): Boolean

    @Query("SELECT * FROM message_hash WHERE contact_id = :contactId ORDER BY created_at DESC LIMIT :limit")
    fun recentForContact(contactId: String, limit: Int): List<MessageHashEntity>

    @Query("SELECT * FROM message_hash WHERE contact_id = :contactId ORDER BY created_at DESC")
    fun allForContact(contactId: String): List<MessageHashEntity>

    @Query(
        "SELECT contact_id AS contactId, COUNT(*) AS noteCount, " +
            "SUM(CASE WHEN trained = 1 THEN 1 ELSE 0 END) AS trainedCount, " +
            "MAX(created_at) AS lastAt " +
            "FROM message_hash GROUP BY contact_id ORDER BY lastAt DESC"
    )
    fun contacts(): List<ContactNoteCounts>

    @Query("SELECT * FROM message_hash WHERE contact_id = :contactId AND trained = 1 AND expression = :expression")
    fun trainedForExpression(contactId: String, expression: String): List<MessageHashEntity>

    @Query("SELECT * FROM message_hash WHERE contact_id = :contactId AND trained = 1 ORDER BY created_at DESC")
    fun allTrainedForContact(contactId: String): List<MessageHashEntity>

    @Query("UPDATE message_hash SET trained = 1, expression = :expression WHERE message_hash = :hash")
    fun markTrained(hash: String, expression: String): Int

    @Query("UPDATE message_hash SET trained = 0, expression = '' WHERE message_hash = :hash")
    fun clearTrained(hash: String): Int
}

data class ContactNoteCounts(
    val contactId: String,
    val noteCount: Int,
    val trainedCount: Int,
    val lastAt: Long
)
