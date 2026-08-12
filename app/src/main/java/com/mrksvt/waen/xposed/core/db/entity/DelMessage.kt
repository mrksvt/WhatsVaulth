package com.mrksvt.waen.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "delmessages",
    indices = [Index(value = ["jid", "msgid"], unique = true)]
)
data class DelMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "jid")
    var jid: String? = null,

    @ColumnInfo(name = "msgid")
    var msgid: String? = null,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    var timestamp: Long? = 0L,

    @ColumnInfo(name = "text")
    var text: String? = null,

    @ColumnInfo(name = "mediaPath")
    var mediaPath: String? = null,

    @ColumnInfo(name = "mediaType", defaultValue = "-1")
    var mediaType: Int? = -1,

    @ColumnInfo(name = "senderName")
    var senderName: String? = null,

    @ColumnInfo(name = "wa")
    var wa: String? = null,

    @ColumnInfo(name = "contact")
    var contact: String? = null,

    @ColumnInfo(name = "intime", defaultValue = "0")
    var intime: Long? = 0L,

    @ColumnInfo(name = "deltime", defaultValue = "0")
    var deltime: Long? = 0L,

    @ColumnInfo(name = "voiceFileName")
    var voiceFileName: String? = null,

    @ColumnInfo(name = "fileId")
    var fileId: String? = null
)
