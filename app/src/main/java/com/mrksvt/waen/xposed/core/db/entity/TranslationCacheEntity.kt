package com.mrksvt.waen.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "TranslationCache", primaryKeys = ["jid", "message_id"])
data class TranslationCacheEntity(
    @ColumnInfo(name = "jid")
    val jid: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "translation")
    val translation: String
)
