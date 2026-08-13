package com.mrksvt.waen.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.core.db.entity.TranslationCacheEntity

@Dao
interface TranslationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: TranslationCacheEntity)

    @Query("SELECT * FROM TranslationCache WHERE jid = :jid")
    fun getByJid(jid: String): List<TranslationCacheEntity>

    @Query("DELETE FROM TranslationCache WHERE jid = :jid AND message_id = :messageId")
    fun delete(jid: String, messageId: String)

    @Query("DELETE FROM TranslationCache WHERE jid = :jid")
    fun deleteByJid(jid: String)
}
