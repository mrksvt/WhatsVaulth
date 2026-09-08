package com.mrksvt.waen.xposed.features.voice_tts.app.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object VoiceTtsStore {

    @Volatile
    private var db: VoiceTtsDatabase? = null

    fun getInstance(context: Context): VoiceTtsDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                VoiceTtsDatabase::class.java,
                VoiceTtsDatabase.DB_NAME
            )
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { db = it }
        }
    }

    fun close() {
        db?.close()
        db = null
    }
}
