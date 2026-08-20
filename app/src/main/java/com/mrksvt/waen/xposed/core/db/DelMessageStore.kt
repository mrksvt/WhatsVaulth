package com.mrksvt.waen.xposed.core.db

import android.content.Context
import com.mrksvt.waen.xposed.core.db.entity.DelMessage

class DelMessageStore private constructor(context: Context) {

    private val database = DelMessageDatabase.getInstance(context)
    private val dao = database.delMessageDao()

    companion object {
        @Volatile
        private var instance: DelMessageStore? = null

        @JvmStatic
        fun getInstance(context: Context): DelMessageStore {
            return instance ?: synchronized(this) {
                instance ?: DelMessageStore(context.applicationContext).also { instance = it }
            }
        }
    }

    fun insertMessage(jid: String, msgid: String, timestamp: Long) {
        val message = DelMessage(jid = jid, msgid = msgid, timestamp = timestamp)
        dao.insertMessage(message)
    }

    fun getMessagesByJid(jid: String?): java.util.HashSet<String> {
        if (jid == null) return java.util.HashSet()
        return HashSet(dao.getMessagesByJid(jid))
    }

    fun getTimestampByMessageId(msgid: String): Long {
        return dao.getTimestampByMessageId(msgid) ?: 0L
    }

    fun insertFullMessage(
        jid: String,
        msgid: String,
        timestamp: Long,
        text: String?,
        mediaPath: String?,
        mediaType: Int,
        senderName: String?,
        wa: String? = null,
        contact: String? = null,
        intime: Long? = null,
        deltime: Long? = null,
        voiceFileName: String? = null,
        fileId: String? = null
    ) {
        val message = DelMessage(
            jid = jid,
            msgid = msgid,
            timestamp = timestamp,
            text = text,
            mediaPath = mediaPath,
            mediaType = mediaType,
            senderName = senderName,
            wa = wa,
            contact = contact,
            intime = intime,
            deltime = deltime,
            voiceFileName = voiceFileName,
            fileId = fileId
        )
        dao.insertMessage(message)
    }

    fun getFullMessagesByJid(jid: String): List<DelMessage> {
        return dao.getFullMessagesByJid(jid)
    }

    fun getAllMessages(): List<DelMessage> {
        return dao.getAllMessages()
    }

    fun deleteByJid(jid: String) {
        dao.deleteByJid(jid)
    }

    fun deleteAll() {
        dao.deleteAll()
    }

}
