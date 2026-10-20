package com.mrksvt.waen.xposed.features.general

import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.db.DelMessageStore
import com.mrksvt.waen.xposed.core.db.RestoreState
import com.mrksvt.waen.xposed.core.db.entity.DeletedMessageBackupEntity
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONObject

/**
 * WP-04. Captures rows of `message`, `message_media`, `message_text` right
 * before WhatsApp issues a delete. Persists each captured rowset into the
 * `deleted_message_backup` table so [DeletedBackupViewer] can list, restore,
 * and purge them. Gated by preference `deleted_message_capture` and limited
 * to the WA msgstore.db path to avoid capturing unrelated database deletes.
 */
class DeletedMessageCapture(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    companion object {
        private const val CAPTURE_PREF = "deleted_message_capture"
        private val CAPTURED_TABLES = setOf("message", "message_media", "message_text")
        private const val MSGSTORE_PATH_TAIL = "/databases/msgstore.db"
    }

    override fun doHook() {
        if (!prefs.getBoolean(CAPTURE_PREF, false)) {
            logDebug("DeletedMessageCapture disabled by preference")
            return
        }
        try {
            XposedHelpers.findAndHookMethod(
                SQLiteDatabase::class.java,
                "delete",
                String::class.java,
                String::class.java,
                arrayOf(String::class.java),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            captureDeleteMethodCall(
                                db = param.thisObject as SQLiteDatabase,
                                table = param.args[0] as? String ?: return,
                                whereClause = param.args[1] as? String,
                                whereArgs = (param.args[2] as? Array<*>)?.mapNotNull { it?.toString() }
                                    ?.toTypedArray()
                            )
                        } catch (e: Throwable) {
                            log(e)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log(e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                SQLiteDatabase::class.java,
                "execSQL",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val sql = param.args[0] as? String ?: return
                            captureExecSqlMethodCall(
                                db = param.thisObject as SQLiteDatabase,
                                sql = sql
                            )
                        } catch (e: Throwable) {
                            log(e)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun isMsgStore(db: SQLiteDatabase): Boolean {
        return try {
            val path = db.path ?: return false
            path.endsWith(MSGSTORE_PATH_TAIL)
        } catch (e: Throwable) {
            log(e)
            false
        }
    }

    private fun captureDeleteMethodCall(
        db: SQLiteDatabase,
        table: String,
        whereClause: String?,
        whereArgs: Array<String>?
    ) {
        if (!isMsgStore(db)) return
        if (table !in CAPTURED_TABLES) return
        if (!prefs.getBoolean(CAPTURE_PREF, false)) return

        val rowsJson = captureRowsAsJson(db, table, whereClause, whereArgs)
        if (rowsJson.isEmpty()) return

        val keyId = extractKeyId(whereArgs, rowsJson)
        val hash = DelMessageStore.hashMessage(
            keyId = keyId,
            timestamp = extractRowTimestamp(rowsJson)
        )

        val now = System.currentTimeMillis()
        val messageMediaJson = if (table == "message_media") rowsJson else null
        val messageTextJson = if (table == "message_text") rowsJson else null
        val messageJson = if (table == "message") rowsJson else null

        val backup = DeletedMessageBackupEntity(
            deletedAt = now,
            restoreStatus = RestoreState.Pending.value,
            messageUniqueHash = hash,
            keyId = keyId,
            chatRawString = null,
            chatPhoneRawString = null,
            senderRawString = null,
            senderPhoneRawString = null,
            fromMe = null,
            messageType = null,
            messageTimestamp = now,
            messageJson = messageJson ?: "{}",
            messageMediaJson = messageMediaJson,
            messageTextJson = messageTextJson
        )

        try {
            DelMessageStore.getInstance(Utils.application).saveBackup(backup)
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun captureExecSqlMethodCall(db: SQLiteDatabase, sql: String) {
        if (!isMsgStore(db)) return
        val normalized = sql.lowercase().trim()
        val table = when {
            normalized.startsWith("delete from message_media") -> "message_media"
            normalized.startsWith("delete from message_text") -> "message_text"
            normalized.startsWith("delete from message ") || normalized.startsWith("delete from message\t") -> "message"
            else -> return
        }
        if (table !in CAPTURED_TABLES) return
        // Only capture when the statement is scoped by a WHERE clause. A
        // free-form DELETE without WHERE would copy the entire table into the
        // backup store, which is both wrong and unbounded.
        if (!normalized.contains(" where ")) return
    }

    private fun captureRowsAsJson(
        db: SQLiteDatabase,
        table: String,
        whereClause: String?,
        whereArgs: Array<String>?
    ): String {
        val arr = JSONArray()
        var cursor: Cursor? = null
        return try {
            cursor = if (whereClause != null) {
                db.query(table, null, whereClause, whereArgs, null, null, null, "512")
            } else {
                db.query(table, null, null, null, null, null, null, "512")
            }
            val cols = cursor.columnNames
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                for (i in cols.indices) {
                    val v: Any? = when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
                        else -> cursor.getString(i)
                    }
                    obj.put(cols[i], v ?: JSONObject.NULL)
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Throwable) {
            log(e)
            ""
        } finally {
            cursor?.close()
        }
    }

    private fun extractKeyId(whereArgs: Array<String>?, rowsJson: String): String {
        if (!whereArgs.isNullOrEmpty()) {
            return whereArgs.first()
        }
        return try {
            val arr = JSONArray(rowsJson)
            if (arr.length() == 0) return ""
            val obj = arr.getJSONObject(0)
            when {
                obj.has("key_id") -> obj.optString("key_id", "")
                obj.has("_id") -> obj.optLong("_id", 0L).toString()
                else -> ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    private fun extractRowTimestamp(rowsJson: String): Long {
        return try {
            val arr = JSONArray(rowsJson)
            if (arr.length() == 0) return 0L
            val obj = arr.getJSONObject(0)
            when {
                obj.has("timestamp") && !obj.isNull("timestamp") -> obj.optLong("timestamp", 0L)
                obj.has("_id") && !obj.isNull("_id") -> obj.optLong("_id", 0L)
                else -> 0L
            }
        } catch (e: Throwable) {
            0L
        }
    }

    override fun getPluginName(): String = "Deleted Message Capture"
}
