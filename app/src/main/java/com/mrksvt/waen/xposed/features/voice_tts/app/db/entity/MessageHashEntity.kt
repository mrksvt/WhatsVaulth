package com.mrksvt.waen.xposed.features.voice_tts.app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registry voice note yang sudah pernah di-hook dari sisi hook.
 * Key = hash stabil (message_id + media fingerprint). Dipakai guard anti-duplikat
 * Tugas A: voice note yang sama tidak boleh di-copy / di-embed dua kali.
 */
@Entity(
    tableName = "message_hash",
    indices = [
        Index(value = ["message_hash"], unique = true),
        Index(value = ["contact_id"])
    ]
)
data class MessageHashEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_hash")
    val messageHash: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "contact_id")
    val contactId: String,

    @ColumnInfo(name = "audio_path")
    val audioPath: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
