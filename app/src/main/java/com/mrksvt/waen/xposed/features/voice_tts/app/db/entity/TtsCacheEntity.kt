package com.mrksvt.waen.xposed.features.voice_tts.app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Hasil generate TTS yang sudah di-cache.
 *
 * Relasi konseptual (dijaga di repository, bukan FK constraint):
 *  - message_id  -> pesan WA asli (tidak ada tabel pesan di DB sisi app)
 *  - contact_id  -> voice_profiles.contact_id (boleh tidak ada profilnya;
 *    berarti digenerate dengan default voice)
 *
 * Lookup cache: (contact_id, text_hash). text_hash = SHA-256(text) supaya
 * indeks tetap kecil walau teks panjang.
 */
@Entity(
    tableName = "tts_cache",
    indices = [
        Index(value = ["contact_id", "text_hash"], unique = true),
        Index(value = ["message_id"])
    ]
)
data class TtsCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "cache_id")
    val cacheId: Long = 0,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "contact_id")
    val contactId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "text_hash")
    val textHash: String,

    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String,

    @ColumnInfo(name = "generated_at")
    val generatedAt: Long = System.currentTimeMillis()
)
