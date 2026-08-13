package com.mrksvt.waen.xposed.core

import android.util.Log
import com.mrksvt.waen.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object TelegramReporter {

    private const val TAG = "TelegramReporter"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun sendHookFixReport(
        hookKey: String,
        resourceName: String,
        waVersion: String,
        errorLog: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!BuildConfig.DONATUR || BuildConfig.TELEGRAM_BOT_TOKEN.isEmpty()) {
            onError("Telegram reporting hanya tersedia di donatur build")
            return
        }

        Thread {
            try {
                val message = buildMessage(hookKey, resourceName, waVersion, errorLog)
                val json = JSONObject().apply {
                    put("chat_id", BuildConfig.TELEGRAM_CHAT_ID)
                    put("text", message)
                    put("parse_mode", "HTML")
                }.toString()

                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot${BuildConfig.TELEGRAM_BOT_TOKEN}/sendMessage")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        val msg = "HTTP ${response.code}: ${response.body?.string()}"
                        Log.w(TAG, "Send failed: $msg")
                        onError(msg)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Network error saat kirim report", e)
                onError("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error saat kirim report", e)
                onError("Error: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun buildMessage(
        hookKey: String,
        resourceName: String,
        waVersion: String,
        errorLog: String
    ): String {
        val trimmedError = if (errorLog.length > 3000) {
            errorLog.take(3000) + "\n... (truncated)"
        } else {
            errorLog
        }
        return """
🔧 <b>Hook Fix Report</b>
📱 <b>WA Version:</b> <code>${escapeHtml(waVersion)}</code>
🔑 <b>Hook Key:</b> <code>${escapeHtml(hookKey)}</code>
📝 <b>Resource/Override:</b> <code>${escapeHtml(resourceName)}</code>
❌ <b>Error:</b>
<pre>${escapeHtml(trimmedError)}</pre>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
