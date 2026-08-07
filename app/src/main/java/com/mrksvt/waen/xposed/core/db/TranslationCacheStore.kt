package com.mrksvt.waen.xposed.core.db

import com.mrksvt.waen.xposed.core.db.entity.TranslationCacheEntity

object TranslationCacheStore {

    private fun dao() = MessageHistoryStore.getInstance().translationCacheDao

    fun getByJid(jid: String): Map<String, String> {
        return try {
            dao().getByJid(jid).associate { it.messageId to it.translation }
        } catch (_: Exception) { emptyMap() }
    }

    fun upsert(jid: String, messageId: String, translation: String) {
        try {
            dao().upsert(TranslationCacheEntity(jid = jid, messageId = messageId, translation = translation))
        } catch (_: Exception) {}
    }

    fun delete(jid: String, messageId: String) {
        try {
            dao().delete(jid, messageId)
        } catch (_: Exception) {}
    }
}
