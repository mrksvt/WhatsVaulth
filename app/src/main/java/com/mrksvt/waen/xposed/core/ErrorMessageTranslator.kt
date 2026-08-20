package com.mrksvt.waen.xposed.core

import android.content.Context
import com.mrksvt.waen.R

/**
 * Translates technical exception info into user-friendly messages.
 * Used only in donatur builds via ErrorItem.toHumanString().
 */
internal object ErrorMessageTranslator {

    fun translate(context: Context, pluginName: String?, message: String?, errorDetail: String?): String {
        val msg = message.orEmpty()
        val detail = errorDetail.orEmpty()
        val plugin = pluginName ?: context.getString(R.string.err_human_unknown_plugin)

        val body = when {
            // IllegalArgumentException: key must be application-specific resource id
            isIllegalArgResourceId(msg) ->
                context.getString(R.string.err_human_illegal_arg_resource_id)

            // ClassNotFoundException
            isClassNotFound(msg, detail) ->
                context.getString(R.string.err_human_class_not_found)

            // NoSuchMethodException / NoSuchMethodError
            isNoSuchMethod(msg, detail) ->
                context.getString(R.string.err_human_no_such_method)

            // NullPointerException / NullPointerError
            isNullPointer(msg, detail) ->
                context.getString(R.string.err_human_null_pointer)

            // RuntimeException: X not found (common Unobfuscator pattern)
            isNotFound(msg) -> {
                val subject = extractNotFoundSubject(msg)
                context.getString(R.string.err_human_not_found, subject)
            }

            // XposedHelpers.findClass / findMethod failures
            isXposedClassNotFound(msg, detail) ->
                context.getString(R.string.err_human_class_not_found)

            // Version incompatibility signals
            isVersionMismatch(msg, detail) ->
                context.getString(R.string.err_human_version_mismatch)

            // Generic fallback
            else -> context.getString(R.string.err_human_generic, plugin)
        }

        return context.getString(R.string.err_human_template, plugin, body)
    }

    // --- pattern matchers ---

    private fun isIllegalArgResourceId(msg: String): Boolean =
        msg.contains("key must be an application-specific resource id", ignoreCase = true) ||
        msg.contains("The key must be an application-specific resource", ignoreCase = true)

    private fun isClassNotFound(msg: String, detail: String): Boolean =
        msg.contains("ClassNotFoundException", ignoreCase = true) ||
        detail.contains("ClassNotFoundException", ignoreCase = true) ||
        msg.contains("java.lang.ClassNotFoundException", ignoreCase = true)

    private fun isNoSuchMethod(msg: String, detail: String): Boolean =
        msg.contains("NoSuchMethod", ignoreCase = true) ||
        detail.contains("NoSuchMethod", ignoreCase = true)

    private fun isNullPointer(msg: String, detail: String): Boolean =
        msg.contains("NullPointerException", ignoreCase = true) ||
        detail.contains("NullPointerException", ignoreCase = true) ||
        // Kotlin NPE message pattern
        msg.contains("null cannot be cast", ignoreCase = true) ||
        msg.contains("must not be null", ignoreCase = true)

    private fun isNotFound(msg: String): Boolean =
        msg.contains("not found", ignoreCase = true) &&
        (msg.contains("RuntimeException", ignoreCase = true) || msg.length < 120)

    private fun isXposedClassNotFound(msg: String, detail: String): Boolean =
        detail.contains("de.robv.android.xposed.XposedHelpers.findClass", ignoreCase = true) ||
        msg.contains("XposedHelpers", ignoreCase = true)

    private fun isVersionMismatch(msg: String, detail: String): Boolean =
        msg.contains("UnsupportedOperationException", ignoreCase = true) ||
        msg.contains("IncompatibleClassChangeError", ignoreCase = true) ||
        detail.contains("AbstractMethodError", ignoreCase = true)

    private fun extractNotFoundSubject(msg: String): String {
        // e.g. "RuntimeException: HookSendButton not found"
        val colonIdx = msg.lastIndexOf(':')
        return if (colonIdx >= 0) msg.substring(colonIdx + 1).trim().removeSuffix(" not found").trim()
        else msg.take(40)
    }
}
