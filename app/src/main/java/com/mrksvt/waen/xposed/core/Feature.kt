package com.mrksvt.waen.xposed.core

import android.content.SharedPreferences
import android.util.Log
import com.mrksvt.waen.BuildConfig
import de.robv.android.xposed.XposedBridge

abstract class Feature(
    @JvmField val classLoader: ClassLoader,
    @JvmField val prefs: SharedPreferences
) {

    companion object {
        @JvmField
        var DEBUG = false
    }

    @Throws(Throwable::class)
    abstract fun doHook()

    abstract fun getPluginName(): String

    private fun formatObject(obj: Any?): String {
        if (obj == null) return "null"

        if (obj.javaClass.isArray) {
            return when (obj) {
                is Array<*> -> obj.contentToString()
                is IntArray -> obj.contentToString()
                is ByteArray -> obj.contentToString()
                is ShortArray -> obj.contentToString()
                is LongArray -> obj.contentToString()
                is FloatArray -> obj.contentToString()
                is DoubleArray -> obj.contentToString()
                is BooleanArray -> obj.contentToString()
                is CharArray -> obj.contentToString()
                else -> obj.toString()
            }
        }
        return if (obj is Throwable) obj.stackTraceToString() else obj.toString()
    }

    private fun sanitizeLog(message: String): String {
        return message
            .replace(Regex("\\+?\\d{10,15}@[a-zA-Z0-9.-]+"), "[REDACTED_JID]")
            .replace(Regex("\\+?\\d{10,15}"), "[REDACTED_PHONE]")
            .replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[REDACTED_ID]")
            .replace(Regex("message[_\\s]?(id|ID)['\"]?\\s*[:=]\\s*['\"]?[^'\"\\s]+"), "message_id=[REDACTED]")
            .replace(Regex("jid['\"]?\\s*[:=]\\s*['\"]?[^'\"\\s]+"), "jid=[REDACTED]")
    }

    fun logDebug(obj: Any?) {
        if (!DEBUG || !BuildConfig.DEBUG) return

        val formattedStr = formatObject(obj)
        log(sanitizeLog(formattedStr))

        if (obj is Throwable) {
            Log.i("Vector-lsposed", "${getPluginName()}-> ${obj.message}", obj)
        } else {
            Log.i("Vector-lsposed", "${getPluginName()}-> $formattedStr")
        }
    }

    fun logDebug(title: String, obj: Any?) {
        if (!DEBUG || !BuildConfig.DEBUG) return

        val formattedStr = formatObject(obj)
        log(sanitizeLog("$title: $formattedStr"))

        if (obj is Throwable) {
            Log.i("WAE", "${getPluginName()}-> $title: ${obj.message}", obj)
        } else {
            Log.i("WAE", "${getPluginName()}-> $title: $formattedStr")
        }
    }

    fun log(obj: Any?) {
        if (obj is Throwable) {
            XposedBridge.log(String.format("[%s] Error:", getPluginName()))
            XposedBridge.log(obj)
        } else {
            XposedBridge.log(String.format("[%s] %s", getPluginName(), sanitizeLog(formatObject(obj))))
        }
    }
}