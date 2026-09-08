package com.mrksvt.waen.xposed.features.voice_tts.app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Speaker embedding per kontak.
 *
 * Catatan relasi (dijaga di level repository, bukan FK constraint, supaya
 * migration Room tidak pecah saat baris WA-side hilang lebih dulu):
 *  - contact_id  : JID kontak WhatsApp (mis. 628123@s.whatsapp.net)
 *  - last_source_message_hash -> message_hash.message_hash (konseptual)
 */
@Entity(
    tableName = "voice_profiles",
    indices = [Index(value = ["contact_id"], unique = true)]
)
data class VoiceProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "contact_id")
    val contactId: String,

    @ColumnInfo(name = "embedding_path")
    val embeddingPath: String,

    @ColumnInfo(name = "auto_tts_enabled")
    val autoTtsEnabled: Boolean = false,

    @ColumnInfo(name = "source_count")
    val sourceCount: Int = 0,

    @ColumnInfo(name = "last_source_message_hash")
    val lastSourceMessageHash: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt
)
