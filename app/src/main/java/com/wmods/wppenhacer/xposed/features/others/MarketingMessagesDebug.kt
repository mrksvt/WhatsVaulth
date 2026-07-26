package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import com.wmods.wppenhacer.xposed.core.Feature
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class MarketingMessagesDebug(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun getPluginName(): String = "marketing_debug"

    override fun doHook() {
        if (!prefs.getBoolean(getPluginName(), false)) return

        hookRawQuery()
        hookQueryTable()
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
                        if (!sql.contains("marketing_messages_background_send", ignoreCase = true)) return
                        logDebug("[rawQuery] SQL: $sql")
                        logTableSchema(param.thisObject as SQLiteDatabase)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val sql = param.args[0] as? String ?: return
                        if (!sql.contains("marketing_messages_background_send", ignoreCase = true)) return
                        if (param.result == null) {
                            logDebug("[rawQuery] Failed - null result. SQL: $sql")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("Error hooking rawQuery: ${e.message}")
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
                        if (!table.contains("marketing_messages_background_send", ignoreCase = true)) return
                        logDebug("[query] Table: $table")
                        logTableSchema(param.thisObject as SQLiteDatabase)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val table = param.args[0] as? String ?: return
                        if (!table.contains("marketing_messages_background_send", ignoreCase = true)) return
                        if (param.result == null) {
                            logDebug("[query] Failed - null result. Table: $table")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("Error hooking query(table): ${e.message}")
        }
    }

    private fun logTableSchema(db: SQLiteDatabase) {
        try {
            val cursor = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE name = 'marketing_messages_background_send'",
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val schema = it.getString(0)
                    logDebug("[Schema] $schema")
                }
            }
        } catch (e: Throwable) {
            logDebug("[Schema] Error: ${e.message}")
        }
    }
}