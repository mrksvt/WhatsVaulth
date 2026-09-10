package com.mrksvt.waen.xposed.features.voice_tts.app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.VoiceProfileEntity

@Dao
interface VoiceProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: VoiceProfileEntity)

    @Query("SELECT * FROM voice_profiles WHERE contact_id = :contactId")
    fun findByContact(contactId: String): VoiceProfileEntity?

    @Query("SELECT auto_tts_enabled FROM voice_profiles WHERE contact_id = :contactId")
    fun isAutoTtsEnabled(contactId: String): Int?

    @Query("UPDATE voice_profiles SET auto_tts_enabled = :enabled, updated_at = :now WHERE contact_id = :contactId")
    fun setAutoTtsEnabled(contactId: String, enabled: Boolean, now: Long = System.currentTimeMillis()): Int

    // embedding_path, created_at, updated_at NOT NULL tanpa DEFAULT di schema:
    // INSERT OR IGNORE skip baris diam-diam bila kolom itu absen, jadi wajib
    // diisi eksplisit.
    @Query(
        "INSERT OR IGNORE INTO voice_profiles " +
            "(contact_id, embedding_path, auto_tts_enabled, source_count, created_at, updated_at) " +
            "VALUES (:contactId, '', 0, 0, :now, :now)"
    )
    fun ensureRow(contactId: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM voice_profiles WHERE auto_tts_enabled = 1")
    fun getAutoTtsContacts(): List<VoiceProfileEntity>
}
