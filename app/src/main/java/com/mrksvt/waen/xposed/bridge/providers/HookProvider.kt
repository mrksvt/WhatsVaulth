package com.mrksvt.waen.xposed.bridge.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import com.mrksvt.waen.xposed.bridge.service.HookBinder
import java.io.File
import java.io.FileOutputStream

class HookProvider : ContentProvider() {

    companion object {
        private const val LOG_DIR = "WhatsVault/logs"
        private const val MAX_LOG_FILES = 10
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        when (method) {
            "getHookBinder" -> {
                val result = Bundle()
                result.putBinder("binder", HookBinder)
                return result
            }
            // Dipakai RuntimeLog dari proses WhatsApp. Proses itu tidak punya izin
            // menulis ke /storage/emulated/0, sedangkan aplikasi modul punya
            // MANAGE_EXTERNAL_STORAGE.
            "pingLog" -> {
                val dir = logDir()
                if (dir != null) cleanupOldFiles(dir)
                return Bundle().apply { putBoolean("ok", dir != null) }
            }
            "appendLog" -> {
                val bundle = extras ?: return failed()
                val text = bundle.getString("text") ?: return failed()
                val name = safeName(bundle.getString("file"))
                val dir = logDir() ?: return failed()
                return runCatching {
                    dir.mkdirs()
                    val file = File(dir, name)
                    FileOutputStream(file, true).use { it.write(text.toByteArray()) }
                    Bundle().apply {
                        putBoolean("ok", true)
                        putString("path", file.absolutePath)
                    }
                }.getOrElse { failed() }
            }
        }
        return null
    }

    private fun failed() = Bundle().apply { putBoolean("ok", false) }

    /**
     * Nama file berasal dari bundle yang bisa dikirim aplikasi lain karena provider ini
     * exported. Batasi ke nama file biasa supaya tidak bisa menulis ke luar folder log.
     */
    private fun safeName(raw: String?): String {
        val name = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
        if (name.isNullOrEmpty() || name == "." || name == "..") return "whatsvault.log"
        val clean = name.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        return if (clean.isEmpty() || clean == "." || clean == "..") "whatsvault.log" else clean
    }

    private fun logDir(): File? {
        // Hanya path yang diminta. Kalau MANAGE_EXTERNAL_STORAGE belum diberikan,
        // kembalikan null supaya proses pemanggil memakai folder media WhatsApp yang
        // masih bisa dibuka pengguna, bukan folder data aplikasi yang tidak terjangkau.
        val dir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
        val usable = runCatching {
            if (!dir.exists() && !dir.mkdirs()) return@runCatching false
            val probe = File(dir, ".write-test")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
        return if (usable) dir else null
    }

    /** Sisakan beberapa file log terbaru supaya storage tidak penuh. */
    private fun cleanupOldFiles(dir: File) {
        runCatching {
            val files = dir.listFiles { file ->
                file.isFile && file.name.startsWith("whatsvault-") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String {
        return ""
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return 0
    }
}
