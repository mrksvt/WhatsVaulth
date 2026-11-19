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
 * Log runtime ke file, khusus build debug.
 *
 * Semua hook berjalan di proses WhatsApp, bukan di proses modul. Proses WhatsApp tidak
 * punya izin menulis ke /storage/emulated/0 secara umum, jadi urutan pemilihan tujuan:
 *
 * 1. `/storage/emulated/0/WhatsVault/logs` langsung (berhasil kalau WhatsApp punya akses
 *    penyimpanan luas).
 * 2. Lewat ContentProvider modul (`HookProvider`), yang berjalan di proses aplikasi modul
 *    dan memegang MANAGE_EXTERNAL_STORAGE. Ini jalur yang biasanya dipakai dan tetap
 *    menghasilkan path yang diminta.
 * 3. Folder media WhatsApp (`Android/media/<pkg>/WhatsVault/logs`), yang selalu bisa
 *    ditulis tanpa izin dan masih bisa dibuka lewat pengelola berkas.
 *
 * Penulisan dilakukan di thread terpisah dengan buffer, supaya hook di main thread tidak
 * ikut menunggu I/O.
 */
object RuntimeLog {

    private const val REQUESTED_DIR = "WhatsVault/logs"
    private const val MAX_BUFFER_LINES = 5000
    private const val FLUSH_INTERVAL_MS = 1500L
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_PROVIDER_FAILURES = 3
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

    private var providerFailures = 0

    private sealed class Sink {
        data class FileSink(val dir: File) : Sink()
        object ProviderSink : Sink()
    }

    /** Path file log yang sedang dipakai, null kalau belum/tidak tersedia. */
    val path: String?
        get() = resolvedPath

    fun start(context: Context?) {
        if (!BuildConfig.DEBUG) return
        if (context == null) return
        appContext = context.applicationContext ?: context
        if (!started.compareAndSet(false, true)) return
        // Nama per sesi: log startup tidak tercampur log spam dari sesi panjang, dan
        // rotasi ukuran hampir tidak pernah terpicu.
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
        if (!BuildConfig.DEBUG) return
        if (!started.get()) return
        // Formatting di dalam lock: SimpleDateFormat tidak thread safe dan log bisa
        // datang dari banyak thread hook sekaligus.
        synchronized(lock) {
            val line = "${timeFormat.format(Date())} [$tag] $message"
            if (pending.size >= MAX_BUFFER_LINES) pending.pollFirst()
            pending.addLast(line)
        }
    }

    fun log(tag: String, throwable: Throwable) {
        log(tag, throwable.stackTraceToString())
    }

    private fun resolveSink() {
        val context = appContext ?: return

        // 1. Path yang diminta, langsung dari proses WhatsApp.
        val requested = File(Environment.getExternalStorageDirectory(), REQUESTED_DIR)
        if (canWrite(requested)) {
            useFileSink(requested)
            return
        }

        // 2. Lewat provider modul.
        if (providerAvailable()) {
            sink = Sink.ProviderSink
            resolvedPath = File(requested, fileName).absolutePath
            return
        }

        // 3. Folder media WhatsApp, selalu bisa ditulis.
        val fallback = mediaFallback()
        if (fallback != null) {
            useFileSink(fallback)
            return
        }

        // 4. Penyimpanan internal aplikasi, hanya bisa dibaca lewat root.
        val internal = File(context.filesDir, "logs")
        if (canWrite(internal)) {
            useFileSink(internal)
        }
    }

    private fun useFileSink(dir: File) {
        sink = Sink.FileSink(dir)
        resolvedPath = File(dir, fileName).absolutePath
        cleanupOldFiles(dir)
    }

    /** Sisakan beberapa file terbaru supaya storage tidak penuh. */
    private fun cleanupOldFiles(dir: File) {
        runCatching {
            val files = dir.listFiles { file ->
                file.isFile && file.name.startsWith("whatsvault-") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    private fun mediaFallback(): File? {
        val context = appContext ?: return null
        val root = context.externalMediaDirs?.firstOrNull() ?: return null
        val dir = File(root, REQUESTED_DIR)
        return if (canWrite(dir)) dir else null
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

    private fun providerAvailable(): Boolean {
        val context = appContext ?: return false
        return runCatching {
            val result = context.contentResolver.call(providerUri(), "pingLog", null, null)
            result?.getBoolean("ok") == true
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
            val builder = StringBuilder()
            while (pending.isNotEmpty()) {
                builder.append(pending.pollFirst()).append('\n')
            }
            batch = builder.toString()
        }
        val written = when (current) {
            is Sink.FileSink -> appendToFile(current.dir, batch)
            is Sink.ProviderSink -> appendViaProvider(batch)
        }
        if (written) {
            providerFailures = 0
            return
        }
        providerFailures++
        if (providerFailures >= MAX_PROVIDER_FAILURES) {
            providerFailures = 0
            // Jalur yang dipakai sudah tidak bisa ditulis (misalnya proses modul mati).
            // Pindah ke folder media WhatsApp supaya log tidak hilang begitu saja.
            val fallback = mediaFallback()
            if (fallback != null) {
                useFileSink(fallback)
                appendToFile(fallback, batch)
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
            result?.getBoolean("ok") == true
        }.getOrDefault(false)
    }
}
