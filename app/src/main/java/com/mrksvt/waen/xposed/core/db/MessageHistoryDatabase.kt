package com.mrksvt.waen.xposed.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mrksvt.waen.xposed.core.db.dao.CustomTickPresetDao
import com.mrksvt.waen.xposed.core.db.dao.HideSeenDao
import com.mrksvt.waen.xposed.core.db.dao.MessageDao
import com.mrksvt.waen.xposed.core.db.dao.TranslationCacheDao
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity
import com.mrksvt.waen.xposed.core.db.entity.HideSeenEntity
import com.mrksvt.waen.xposed.core.db.entity.MessageEntity
import com.mrksvt.waen.xposed.core.db.entity.TranslationCacheEntity

@Database(
    entities = [MessageEntity::class, HideSeenEntity::class, TranslationCacheEntity::class, CustomTickPresetEntity::class],
    version = 8,
    exportSchema = false
)
abstract class MessageHistoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun hideSeenDao(): HideSeenDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun customTickPresetDao(): CustomTickPresetDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_tick_presets (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        svg_pending_path TEXT DEFAULT '',
                        svg_sent_path TEXT DEFAULT '',
                        svg_delivered_path TEXT DEFAULT '',
                        svg_read_path TEXT DEFAULT '',
                        svg_failed_path TEXT DEFAULT '',
                        color_pending INTEGER NOT NULL DEFAULT -5592406,
                        color_sent INTEGER NOT NULL DEFAULT -5592406,
                        color_delivered INTEGER NOT NULL DEFAULT -5592406,
                        color_read INTEGER NOT NULL DEFAULT -786376,
                        color_failed INTEGER NOT NULL DEFAULT -1777125
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS custom_tick_presets")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_tick_presets (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        svg_pending_path TEXT DEFAULT '',
                        svg_sent_path TEXT DEFAULT '',
                        svg_delivered_path TEXT DEFAULT '',
                        svg_read_path TEXT DEFAULT '',
                        svg_failed_path TEXT DEFAULT '',
                        color_pending INTEGER NOT NULL DEFAULT -5592406,
                        color_sent INTEGER NOT NULL DEFAULT -5592406,
                        color_delivered INTEGER NOT NULL DEFAULT -5592406,
                        color_read INTEGER NOT NULL DEFAULT -786376,
                        color_failed INTEGER NOT NULL DEFAULT -1777125
                    )
                """.trimIndent())
            }
        }
    }
}
