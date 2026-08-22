package com.mrksvt.waen.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "theme_presets")
data class ThemePresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "primary_color")
    val primaryColor: Int,

    @ColumnInfo(name = "text_color")
    val textColor: Int,

    @ColumnInfo(name = "background_color")
    val backgroundColor: Int,

    @ColumnInfo(name = "use_monet", defaultValue = "0")
    val useMonet: Boolean = false,

    @ColumnInfo(name = "tick_preset_id")
    val tickPresetId: Long? = null,

    @ColumnInfo(name = "font_preset_id")
    val fontPresetId: Long? = null
)
