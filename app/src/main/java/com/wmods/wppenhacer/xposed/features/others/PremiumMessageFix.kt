package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.wmods.wppenhacer.xposed.core.Feature
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

class PremiumMessageFix(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun getPluginName(): String = "premium_message_fix"

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
                migratePremiumMessage(db)
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
                        val db = param.thisObject as SQLiteDatabase
                        if (!isPremiumMessageSql(sql, db)) return
                        migratePremiumMessage(db)
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
                        val db = param.thisObject as SQLiteDatabase
                        if (!isPremiumMessageSql(sql, db)) return
                        migratePremiumMessage(db)
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
                        val db = param.thisObject as SQLiteDatabase
                        if (!table.contains(TABLE, ignoreCase = true) || !tableExists(db, TABLE)) return
                        migratePremiumMessage(db)
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
                log("PremiumMessage query SQLiteException (returning empty cursor): ${cause.message}")
                param.result = emptyPremiumMessageCursor()
            } else {
                throw e
            }
        } catch (e: SQLiteException) {
            log("PremiumMessage query SQLiteException (returning empty cursor): ${e.message}")
            param.result = emptyPremiumMessageCursor()
        } catch (e: Throwable) {
            log("PremiumMessage query failed (returning empty cursor): ${e.message}")
            param.result = emptyPremiumMessageCursor()
        }
    }

    private fun isPremiumMessageSql(sql: String, db: SQLiteDatabase): Boolean {
        if (!sql.contains(TABLE, ignoreCase = true)) return false
        // Skip if table doesn't exist (avoid empty cursor logs)
        return tableExists(db, TABLE)
    }

    private fun emptyPremiumMessageCursor(): MatrixCursor =
        MatrixCursor(EXPECTED_COLUMNS)

    private fun migratePremiumMessage(db: SQLiteDatabase) {
        if (schemaMigrated.get()) return
        synchronized(migrateLock) {
            if (schemaMigrated.get()) return
            try {
                if (!db.isOpen) return
                val hasTable = tableExists(db, TABLE)
                if (!hasTable) return
                
                // Start transaction for atomic migration
                db.beginTransaction()
                try {
                    val existing = columnNames(db, TABLE)
                    for ((col, decl) in MISSING_COLUMNS) {
                        if (col !in existing) {
                            val sql = "ALTER TABLE $TABLE ADD COLUMN $col $decl"
                            try {
                                db.execSQL(sql)
                                log("Added column $col to $TABLE")
                            } catch (e: SQLiteException) {
                                // Skip duplicate column errors (expected)
                                if (!e.message?.contains("duplicate column name")!!) {
                                    log("ALTER TABLE $col failed: ${e.message}")
                                }
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                schemaMigrated.set(true)
            } catch (e: Throwable) {
                log("migratePremiumMessage: ${e.message}")
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
        private const val TABLE = "premium_message"

        private val migrateLock = Any()
        private val schemaMigrated = AtomicBoolean(false)

        private val MISSING_COLUMNS = linkedMapOf(
            "message_type" to "INTEGER DEFAULT 0",
            "device_id" to "INTEGER DEFAULT 0",
            "ad_id" to "TEXT",
            "created_at" to "INTEGER DEFAULT 0",
            "updated_at" to "INTEGER DEFAULT 0",
        )

        private val EXPECTED_COLUMNS = arrayOf(
            "premium_message_id",
            "name",
            "text",
            "media_uri",
            "media_type",
            "created_from_premium_message_id",
            "last_sent_timestamp",
            "promotion_template_name",
            "creation_source",
            "is_premium_broadcast",
            "broadcast_raw_jid",
            "message_type",
            "device_id",
            "ad_id",
            "created_at",
            "updated_at",
        )
    }
}
