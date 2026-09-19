package com.mrksvt.waen.xposed.features.voice_tts.app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Nama tampilan kontak yang di-resolve dari sisi WA (bisa jid @lid yang
 * tidak ada di phonebook). Diisi saat capture voice note via bridge.
 */
@Entity(tableName = "contact_name")
data class ContactNameEntity(
    @PrimaryKey
    @ColumnInfo(name = "contact_id")
    val contactId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,
)
