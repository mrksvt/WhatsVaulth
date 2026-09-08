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
}
