package com.mrksvt.waen.xposed.features.voice_tts.app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.TtsCacheEntity

@Dao
interface TtsCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: TtsCacheEntity): Long

    @Query("SELECT * FROM tts_cache WHERE contact_id = :contactId AND text_hash = :textHash LIMIT 1")
    fun findCached(contactId: String, textHash: String): TtsCacheEntity?

    @Query("SELECT * FROM tts_cache WHERE message_id = :messageId")
    fun findByMessage(messageId: String): List<TtsCacheEntity>

    @Query("DELETE FROM tts_cache WHERE contact_id = :contactId")
    fun deleteByContact(contactId: String)

    @Query("SELECT audio_file_path FROM tts_cache WHERE contact_id = :contactId AND text_hash = :textHash LIMIT 1")
    fun findCachedPath(contactId: String, textHash: String): String?
}
