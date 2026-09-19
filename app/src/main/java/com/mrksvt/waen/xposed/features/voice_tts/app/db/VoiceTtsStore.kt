package com.mrksvt.waen.xposed.features.voice_tts.app.db

import android.content.Context

object VoiceTtsStore {

    @Volatile
    private var db: VoiceTtsDatabase? = null

    fun getInstance(context: Context): VoiceTtsDatabase {
        return db ?: synchronized(this) {
            db ?: VoiceTtsDatabase.build(context).also { db = it }
        }
    }

    fun close() {
        db?.close()
        db = null
    }
}
