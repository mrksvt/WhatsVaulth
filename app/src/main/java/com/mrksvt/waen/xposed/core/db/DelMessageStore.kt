package com.mrksvt.waen.xposed.core.db

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mrksvt.waen.xposed.core.db.entity.DelMessage
import com.mrksvt.waen.xposed.core.db.entity.DeletedMessageBackupEntity
import java.security.MessageDigest

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

        /**
         * Stable dedup hash for a deleted message: SHA-256 over key id + timestamp.
         */
        @JvmStatic
        fun hashMessage(keyId: String, timestamp: Long): String {
            return try {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest("$keyId|$timestamp".toByteArray(Charsets.UTF_8))
                val sb = StringBuilder(digest.size * 2)
                for (b in digest) {
                    val v = b.toInt() and 0xff
                    if (v < 0x10) sb.append('0')
                    sb.append(Integer.toHexString(v))
                }
                sb.toString()
            } catch (t: Throwable) {
                "$keyId|$timestamp"
            }
        }
    }

    fun insertMessage(jid: String, msgid: String, timestamp: Long) {
        val message = DelMessage(jid = jid, msgid = msgid, timestamp = timestamp)
        dao.insertMessage(message)
    }

    fun getTimestampByJidAndMsgId(jid: String, msgid: String): Long {
        return dao.getTimestampByJidAndMsgId(jid, msgid) ?: 0L
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

    fun getMessagesAfter(lastId: Long, limit: Int): List<DelMessage> {
        return dao.getMessagesAfter(lastId, limit)
    }

    fun deleteByJid(jid: String) {
        dao.deleteByJid(jid)
    }

    fun deleteAll() {
        dao.deleteAll()
    }

    // --- backup API (WP-03, FR-13) ---

    /**
     * Persists a deleted message backup. Returns the stored backup id.
     */
    fun saveBackup(backup: DeletedMessageBackupEntity): Long {
        return try {
            dao.upsertDeletedMessageBackup(backup)
            backup.id
        } catch (t: Throwable) {
            -1L
        }
    }

    fun getBackupById(id: Long): DeletedMessageBackupEntity? {
        return try {
            dao.getBackupById(id)
        } catch (t: Throwable) {
            null
        }
    }

    fun listAllBackups(): List<DeletedMessageBackupEntity> {
        return try {
            dao.listDeletedMessageBackups(
                SimpleSQLiteQuery("SELECT * FROM deleted_message_backup ORDER BY deleted_at DESC")
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * Lists backups for one chat. [chatKey] is matched against both the raw jid column and the
     * phone-number raw column.
     */
    fun listBackupsByChatKey(chatKey: String): List<DeletedMessageBackupEntity> {
        return try {
            dao.listDeletedMessageBackups(
                SimpleSQLiteQuery(
                    "SELECT * FROM deleted_message_backup " +
                        "WHERE chat_raw_string = ? OR chat_phone_raw_string = ? " +
                        "ORDER BY deleted_at DESC",
                    arrayOf(chatKey, chatKey)
                )
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun listPendingBackups(): List<DeletedMessageBackupEntity> {
        return try {
            dao.listDeletedMessageBackups(
                SimpleSQLiteQuery(
                    "SELECT * FROM deleted_message_backup " +
                        "WHERE restore_status = ? " +
                        "ORDER BY deleted_at DESC",
                    arrayOf(RestoreState.Pending.value)
                )
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun listBackupsByStatus(state: RestoreState): List<DeletedMessageBackupEntity> {
        return try {
            dao.listDeletedMessageBackups(
                SimpleSQLiteQuery(
                    "SELECT * FROM deleted_message_backup " +
                        "WHERE restore_status = ? " +
                        "ORDER BY deleted_at DESC",
                    arrayOf(state.value)
                )
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun listRestoredBackups(): List<DeletedMessageBackupEntity> {
        return try {
            dao.listDeletedMessageBackups(
                SimpleSQLiteQuery(
                    "SELECT * FROM deleted_message_backup " +
                        "WHERE restore_status = ? " +
                        "ORDER BY deleted_at DESC",
                    arrayOf(RestoreState.Restored.value)
                )
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * Returns the total number of backups currently stored.
     */
    fun countBackups(): Int {
        return try {
            dao.countDeletedMessageBackups()
        } catch (t: Throwable) {
            listAllBackups().size
        }
    }

    fun markRestoreState(
        id: Long,
        state: RestoreState,
        error: String? = null,
        restoredAt: Long = 0L
    ) {
        try {
            dao.updateRestoreState(id, state.value, error, restoredAt)
        } catch (t: Throwable) {
        }
    }

    /**
     * Restores a single backup into the writable msgstore connection supplied by the caller
     * (see MessageStore.getWritableDatabase()). Returns true when the row was inserted.
     */
    fun restoreBackupById(db: android.database.sqlite.SQLiteDatabase?, id: Long): Boolean {
        return try {
            if (db == null || !db.isOpen) return false
            val backup = dao.getBackupById(id) ?: return false
            val chatRowId = resolveChatRowId(db, backup)
            if (chatRowId == null) {
                dao.updateRestoreState(
                    id,
                    RestoreState.Failed.value,
                    "chat_row_id not resolvable for key_id=${backup.keyId}",
                    0L
                )
                return false
            }
            val values = buildMessageValues(backup, chatRowId)
            val rowId = db.insertWithOnConflict(
                "message",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
            )
            if (rowId == -1L) {
                dao.updateRestoreState(
                    id,
                    RestoreState.SkippedExists.value,
                    null,
                    System.currentTimeMillis()
                )
                false
            } else {
                dao.updateRestoreState(
                    id,
                    RestoreState.Restored.value,
                    null,
                    System.currentTimeMillis()
                )
                true
            }
        } catch (t: Throwable) {
            try {
                dao.updateRestoreState(id, RestoreState.Failed.value, t.message, 0L)
            } catch (ignored: Throwable) {
            }
            false
        }
    }

    /**
     * Restores every pending backup into the writable msgstore connection. Returns the number of
     * rows inserted.
     */
    fun restoreAllBackups(db: android.database.sqlite.SQLiteDatabase?): Int {
        var restored = 0
        val candidates = try {
            listPendingBackups() + listBackupsByStatus(RestoreState.Failed)
        } catch (t: Throwable) {
            listPendingBackups()
        }
        for (backup in candidates) {
            if (restoreBackupById(db, backup.id)) {
                restored++
            }
        }
        return restored
    }

    private fun resolveChatRowId(
        db: android.database.sqlite.SQLiteDatabase,
        backup: DeletedMessageBackupEntity
    ): Long? {
        val candidates = listOfNotNull(backup.chatRawString, backup.chatPhoneRawString)
        for (raw in candidates) {
            try {
                db.rawQuery(
                    "SELECT c._id FROM chat c INNER JOIN jid j ON j._id = c.jid_row_id " +
                        "WHERE j.raw_string = ? LIMIT 1",
                    arrayOf(raw)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(0)
                    }
                }
            } catch (t: Throwable) {
            }
        }
        // Fallback: the captured `message` row itself carries chat_row_id. Reading
        // it keeps restore working even when the jid columns were not populated.
        val fromRow = firstRowLongField(backup.messageJson, "chat_row_id")
        if (fromRow != null && fromRow > 0) {
            try {
                db.rawQuery("SELECT _id FROM chat WHERE _id = ? LIMIT 1", arrayOf(fromRow.toString()))
                    .use { cursor ->
                        if (cursor.moveToFirst()) {
                            return cursor.getLong(0)
                        }
                    }
            } catch (t: Throwable) {
            }
        }
        return null
    }

    private fun firstRowLongField(json: String?, field: String): Long? {
        if (json.isNullOrBlank() || json == "{}") return null
        return try {
            val trimmed = json.trim()
            val obj = when {
                trimmed.startsWith("[") -> {
                    val arr = org.json.JSONArray(trimmed)
                    if (arr.length() == 0) return null
                    arr.getJSONObject(0)
                }
                trimmed.startsWith("{") -> org.json.JSONObject(trimmed)
                else -> return null
            }
            if (obj.has(field) && !obj.isNull(field)) obj.optLong(field, -1L) else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun buildMessageValues(
        backup: DeletedMessageBackupEntity,
        chatRowId: Long
    ): android.content.ContentValues {
        val values = android.content.ContentValues()
        values.put("chat_row_id", chatRowId)
        values.put(
            "from_me",
            backup.fromMe ?: firstRowIntField(backup.messageJson, "from_me") ?: 0
        )
        values.put("key_id", backup.keyId)
        values.put(
            "timestamp",
            backup.messageTimestamp ?: firstRowLongField(backup.messageJson, "timestamp") ?: 0L
        )
        values.put(
            "message_type",
            backup.messageType ?: firstRowIntField(backup.messageJson, "message_type") ?: 0
        )
        values.put("text_data", extractTextData(backup))
        return values
    }

    private fun firstRowIntField(json: String?, field: String): Int? {
        if (json.isNullOrBlank() || json == "{}") return null
        return try {
            val trimmed = json.trim()
            val obj = when {
                trimmed.startsWith("[") -> {
                    val arr = org.json.JSONArray(trimmed)
                    if (arr.length() == 0) return null
                    arr.getJSONObject(0)
                }
                trimmed.startsWith("{") -> org.json.JSONObject(trimmed)
                else -> return null
            }
            if (obj.has(field) && !obj.isNull(field)) obj.optInt(field, -1) else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Pulls the plain `text_data` value out of the captured JSON payloads so a
     * restored row contains the message text, not a serialized JSON blob.
     */
    private fun extractTextData(backup: DeletedMessageBackupEntity): String? {
        fun fromJson(json: String?): String? {
            if (json.isNullOrBlank() || json == "{}") return null
            return try {
                val trimmed = json.trim()
                val obj = when {
                    trimmed.startsWith("[") -> {
                        val arr = org.json.JSONArray(trimmed)
                        if (arr.length() == 0) return null
                        arr.getJSONObject(0)
                    }
                    trimmed.startsWith("{") -> org.json.JSONObject(trimmed)
                    else -> return null
                }
                when {
                    obj.has("text_data") && !obj.isNull("text_data") ->
                        obj.optString("text_data", null)
                    obj.has("text") && !obj.isNull("text") ->
                        obj.optString("text", null)
                    else -> null
                }
            } catch (t: Throwable) {
                null
            }
        }
        return fromJson(backup.messageJson) ?: fromJson(backup.messageTextJson)
    }

    fun deleteBackupById(id: Long): Int {
        return try {
            dao.deleteBackupById(id)
        } catch (t: Throwable) {
            0
        }
    }

    fun deleteBackupsByIds(ids: LongArray): Int {
        if (ids.isEmpty()) return 0
        return try {
            dao.deleteBackupsByIds(ids)
        } catch (t: Throwable) {
            0
        }
    }

    fun deleteBackupsByChatKeys(chatKeys: List<String>): Int {
        if (chatKeys.isEmpty()) return 0
        return try {
            dao.deleteBackupsByChatKeys(chatKeys)
        } catch (t: Throwable) {
            0
        }
    }

    fun deleteAllBackups(): Int {
        return try {
            val ids = listAllBackups().map { it.id }.toLongArray()
            dao.deleteBackupsByIds(ids)
        } catch (t: Throwable) {
            0
        }
    }
}
