package com.mrksvt.waen.xposed.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import com.mrksvt.waen.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Log runtime ke `/storage/emulated/0/WhatsVault/logs`, khusus build debug dan hanya
 * ketika preference [RuntimeLogPolicy.PREF_KEY] aktif.
 *
 * Hook berjalan di proses WhatsApp yang tidak punya akses storage luas. Karena itu
 * penulisan normal dilakukan lewat HookProvider milik aplikasi modul. Tidak ada fallback
 * ke Android/media: kalau provider belum siap, data tetap di buffer dan provider dicoba
 * kembali pada flush berikutnya.
 */
object RuntimeLog {

    private const val MAX_BUFFER_LINES = 5000
    private const val FLUSH_INTERVAL_MS = 1500L
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_LOG_FILES = 10

    private val started = AtomicBoolean(false)
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val clockFormat = SimpleDateFormat("HHmmss", Locale.US)

    @Volatile
    private var sink: Sink? = null

    @Volatile
    private var fileName: String = "whatsvault.log"

    @Volatile
    private var resolvedPath: String? = null

    @Volatile
    private var appContext: Context? = null

    private sealed class Sink {
        data class FileSink(val dir: File) : Sink()
        object ProviderSink : Sink()
    }

    val path: String?
        get() = resolvedPath

    fun start(context: Context?, enabled: Boolean) {
        if (!RuntimeLogPolicy.shouldStart(BuildConfig.DEBUG, enabled)) return
        if (context == null) return
        appContext = context.applicationContext ?: context
        if (!started.compareAndSet(false, true)) return

        val now = Date()
        fileName = "whatsvault-${dateFormat.format(now)}-${clockFormat.format(now)}.log"

        val thread = Thread({
            resolveSink()
            writeHeader()
            while (true) {
                try {
                    Thread.sleep(FLUSH_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                flush()
            }
        }, "WhatsVault-RuntimeLog")
        thread.isDaemon = true
        thread.start()
    }

    fun log(tag: String, message: String) {
        if (!BuildConfig.DEBUG || !started.get()) return
        synchronized(lock) {
            val line = "${timeFormat.format(Date())} [$tag] $message"
            if (pending.size >= MAX_BUFFER_LINES) pending.pollFirst()
            pending.addLast(line)
        }
    }

    fun log(tag: String, throwable: Throwable) {
        log(tag, throwable.stackTraceToString())
    }

    private fun requestedDirectory(): File = File(
        RuntimeLogPolicy.publicLogDirectory(Environment.getExternalStorageDirectory().absolutePath)
    )

    private fun resolveSink() {
        val requested = requestedDirectory()

        // Proses WhatsApp mungkin punya akses langsung pada perangkat tertentu.
        if (canWrite(requested)) {
            useFileSink(requested)
            return
        }

        // Jalur normal: aplikasi modul menulis ke path publik memakai
        // MANAGE_EXTERNAL_STORAGE. Tetap pilih provider walau ping awal gagal, karena
        // proses provider bisa baru hidup beberapa detik kemudian.
        sink = Sink.ProviderSink
        resolvedPath = File(requested, fileName).absolutePath
    }

    private fun useFileSink(dir: File) {
        sink = Sink.FileSink(dir)
        resolvedPath = File(dir, fileName).absolutePath
        cleanupOldFiles(dir)
    }

    private fun cleanupOldFiles(dir: File) {
        runCatching {
            val files = dir.listFiles { file ->
                file.isFile && file.name.startsWith("whatsvault-") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    private fun canWrite(dir: File): Boolean {
        return runCatching {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, ".write-test")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    private fun providerUri(): Uri =
        Uri.parse("content://${BuildConfig.APPLICATION_ID}.hookprovider")

    private fun writeHeader() {
        val context = appContext ?: return
        val packageName = runCatching { context.packageName }.getOrDefault("?")
        log("RuntimeLog", "=== WhatsVault runtime log ===")
        log(
            "RuntimeLog",
            "module=${BuildConfig.VERSION_NAME} package=$packageName " +
                    "android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) " +
                    "device=${Build.MANUFACTURER} ${Build.MODEL}"
        )
        log("RuntimeLog", "sink=${resolvedPath ?: "tidak tersedia"}")
    }

    private fun flush() {
        val current = sink ?: return
        val batch: String
        synchronized(lock) {
            if (pending.isEmpty()) return
            batch = pending.joinToString(separator = "\n", postfix = "\n")
        }

        val written = when (current) {
            is Sink.FileSink -> appendToFile(current.dir, batch)
            is Sink.ProviderSink -> appendViaProvider(batch)
        }
        if (!written) return

        // Hapus hanya setelah seluruh batch berhasil ditulis. Kalau provider belum siap
        // atau izin storage modul belum aktif, buffer dipertahankan untuk retry.
        synchronized(lock) {
            repeat(batch.count { it == '\n' }.coerceAtMost(pending.size)) {
                pending.pollFirst()
            }
        }
    }

    private fun appendToFile(dir: File, text: String): Boolean {
        return runCatching {
            val file = File(dir, fileName)
            if (file.length() > MAX_FILE_BYTES) file.delete()
            FileOutputStream(file, true).use { it.write(text.toByteArray()) }
            true
        }.getOrDefault(false)
    }

    private fun appendViaProvider(text: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            val extras = Bundle().apply {
                putString("text", text)
                putString("file", fileName)
            }
            val result = context.contentResolver.call(providerUri(), "appendLog", null, extras)
            if (result?.getBoolean("ok") == true) {
                result.getString("path")?.let { resolvedPath = it }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
