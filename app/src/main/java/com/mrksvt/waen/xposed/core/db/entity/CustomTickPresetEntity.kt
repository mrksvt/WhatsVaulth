package com.mrksvt.waen.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_tick_presets")
data class CustomTickPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "svg_pending_path", defaultValue = "")
    val svgPendingPath: String? = null,

    @ColumnInfo(name = "svg_sent_path", defaultValue = "")
    val svgSentPath: String? = null,

    @ColumnInfo(name = "svg_delivered_path", defaultValue = "")
    val svgDeliveredPath: String? = null,

    @ColumnInfo(name = "svg_read_path", defaultValue = "")
    val svgReadPath: String? = null,

    @ColumnInfo(name = "svg_failed_path", defaultValue = "")
    val svgFailedPath: String? = null,

    @ColumnInfo(name = "color_pending")
    val colorPending: Int = 0xFFAAAAAA.toInt(),

    @ColumnInfo(name = "color_sent")
    val colorSent: Int = 0xFFAAAAAA.toInt(),

    @ColumnInfo(name = "color_delivered")
    val colorDelivered: Int = 0xFFAAAAAA.toInt(),

    @ColumnInfo(name = "color_read")
    val colorRead: Int = 0xFF4FC3F7.toInt(),

    @ColumnInfo(name = "color_failed")
    val colorFailed: Int = 0xFFE53935.toInt()
)
