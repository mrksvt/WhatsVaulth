package com.mrksvt.waen.xposed.features.voice_tts.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.ContactNameDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.MessageHashDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.TtsCacheDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.VoiceProfileDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.ContactNameEntity
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.MessageHashEntity
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.TtsCacheEntity
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.VoiceProfileEntity

/**
 * Database khusus pipeline Contact Voice TTS (sisi app).
 * Terpisah dari MessageHistoryDatabase supaya fitur baru tidak memaksa
 * bump/migrasi DB existing yang dipakai banyak fitur.
 */
@Database(
    entities = [
        VoiceProfileEntity::class,
        TtsCacheEntity::class,
        MessageHashEntity::class,
        ContactNameEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VoiceTtsDatabase : RoomDatabase() {

    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun ttsCacheDao(): TtsCacheDao
    abstract fun messageHashDao(): MessageHashDao
    abstract fun contactNameDao(): ContactNameDao

    companion object {
        const val DB_NAME = "voice_tts.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_hash ADD COLUMN trained INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE message_hash ADD COLUMN expression TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_name` (" +
                        "`contact_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
                        "PRIMARY KEY(`contact_id`))"
                )
            }
        }

        fun build(context: Context): VoiceTtsDatabase =
            Room.databaseBuilder(context.applicationContext, VoiceTtsDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
