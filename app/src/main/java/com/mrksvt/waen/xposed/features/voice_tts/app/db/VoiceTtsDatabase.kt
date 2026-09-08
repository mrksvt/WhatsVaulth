package com.mrksvt.waen.xposed.features.voice_tts.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.MessageHashDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.TtsCacheDao
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.VoiceProfileDao
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
        MessageHashEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoiceTtsDatabase : RoomDatabase() {

    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun ttsCacheDao(): TtsCacheDao
    abstract fun messageHashDao(): MessageHashDao

    companion object {
        const val DB_NAME = "voice_tts.db"
    }
}
