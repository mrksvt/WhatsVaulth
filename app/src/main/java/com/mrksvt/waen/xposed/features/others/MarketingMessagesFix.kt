package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mrksvt.waen.xposed.core.Feature
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

class MarketingMessagesFix(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun getPluginName(): String = "marketing_fix"

    override fun doHook() {
        hookOpenDatabase()
        hookRawQuery()
        hookRawQueryWithFactory()
        hookQueryTable()
    }

    private fun hookOpenDatabase() {
        val afterOpen = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val path = param.args.getOrNull(0) as? String ?: return
                if (!path.contains("smb.db", ignoreCase = true)) return
                val db = param.result as? SQLiteDatabase ?: return
                migrateMarketingBackgroundSend(db)
            }
        }
        try {
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "openDatabase", afterOpen)
            XposedBridge.hookAllMethods(SQLiteDatabase::class.java, "openOrCreateDatabase", afterOpen)
        } catch (e: Throwable) {
            log("Error hooking openDatabase: ${e.message}")
        }
    }

    private fun hookRawQuery() {
        try {
            XposedHelpers.findAndHookMethod(
                SQLiteDatabase::class.java,
                "rawQuery",
                String::class.java,
                Array<String?>::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sql = param.args[0] as? String ?: return
                        if (!isMarketingBackgroundSendSql(sql)) return
                        val db = param.thisObject as SQLiteDatabase
                        migrateMarketingBackgroundSend(db)
                        runSafeQuery(param) {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("Error hooking rawQuery: ${e.message}")
        }
    }

    private fun hookRawQueryWithFactory() {
        try {
            XposedHelpers.findAndHookMethod(
                SQLiteDatabase::class.java,
                "rawQueryWithFactory",
                SQLiteDatabase.CursorFactory::class.java,
                String::class.java,
                Array<String?>::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sql = param.args[1] as? String ?: return
                        if (!isMarketingBackgroundSendSql(sql)) return
                        val db = param.thisObject as SQLiteDatabase
                        migrateMarketingBackgroundSend(db)
                        runSafeQuery(param) {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            logDebug("rawQueryWithFactory hook skipped: ${e.message}")
        }
    }

    private fun hookQueryTable() {
        try {
            XposedHelpers.findAndHookMethod(
                SQLiteDatabase::class.java,
                "query",
                String::class.java,
                Array<String?>::class.java,
                String::class.java,
                Array<String?>::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val table = param.args[0] as? String ?: return
                        if (!table.contains(TABLE, ignoreCase = true)) return
                        val db = param.thisObject as SQLiteDatabase
                        migrateMarketingBackgroundSend(db)
                        runSafeQuery(param) {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("Error hooking query(table): ${e.message}")
        }
    }

    private fun runSafeQuery(param: XC_MethodHook.MethodHookParam, block: () -> Any?) {
        try {
            param.result = block()
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is SQLiteException) {
                log("Marketing query SQLiteException (returning empty cursor): ${cause.message}")
                param.result = emptyMarketingCursor()
            } else {
                throw e
            }
        } catch (e: SQLiteException) {
            log("Marketing query SQLiteException (returning empty cursor): ${e.message}")
            param.result = emptyMarketingCursor()
        } catch (e: Throwable) {
            log("Marketing query failed (returning empty cursor): ${e.message}")
            param.result = emptyMarketingCursor()
        }
    }

    private fun isMarketingBackgroundSendSql(sql: String): Boolean =
        sql.contains(TABLE, ignoreCase = true)

    private fun emptyMarketingCursor(): MatrixCursor =
        MatrixCursor(EXPECTED_COLUMNS)

    private fun migrateMarketingBackgroundSend(db: SQLiteDatabase) {
        if (schemaMigrated.get()) return
        synchronized(migrateLock) {
            if (schemaMigrated.get()) return
            try {
                if (!db.isOpen) return
                val path = try {
                    db.path.orEmpty()
                } catch (_: Throwable) {
                    ""
                }
                val hasTable = tableExists(db, TABLE)
                if (!hasTable) {
                    if (path.contains("smb.db", ignoreCase = true)) {
                        logDebug("smb.db open but $TABLE missing — skip migrate")
                    }
                    return
                }
                val existing = columnNames(db, TABLE)
                for ((col, decl) in MISSING_COLUMNS) {
                    if (col !in existing) {
                        val sql = "ALTER TABLE $TABLE ADD COLUMN $col $decl"
                        try {
                            db.execSQL(sql)
                            log("Added column $col to $TABLE")
                        } catch (e: SQLiteException) {
                            logDebug("ALTER TABLE $col skipped (already exists): ${e.message}")
                        }
                    }
                }
                schemaMigrated.set(true)
            } catch (e: Throwable) {
                log("migrateMarketingBackgroundSend: ${e.message}")
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        return try {
            db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf(name)
            ).use { it.moveToFirst() }
        } catch (_: Throwable) {
            false
        }
    }

    private fun columnNames(db: SQLiteDatabase, table: String): Set<String> {
        return try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val nameIdx = c.getColumnIndex("name")
                buildSet {
                    while (c.moveToNext()) {
                        if (nameIdx >= 0) add(c.getString(nameIdx))
                    }
                }
            }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    companion object {
        private const val TABLE = "marketing_messages_background_send"

        private val migrateLock = Any()
        private val schemaMigrated = AtomicBoolean(false)

        private val MISSING_COLUMNS = linkedMapOf(
            "scheduled_batch_id" to "TEXT",
            "free_reserved_messages_count" to "INTEGER DEFAULT 0",
            "failed_message_id" to "TEXT",
            "analytics_session_json" to "TEXT",
        )

        private val EXPECTED_COLUMNS = arrayOf(
            "premium_message_id",
            "creation_timestamp",
            "scheduled_timestamp",
            "scheduled_batch_id",
            "retry_count",
            "error_code",
            "processing_state",
            "last_handled_timestamp",
            "campaign_id",
            "smart_list_option",
            "smart_list_selection",
            "entry_point",
            "free_reserved_messages_count",
            "broadcast_raw_jid",
            "failed_message_id",
            "analytics_session_json",
        )
    }
}
