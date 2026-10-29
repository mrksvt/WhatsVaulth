package com.mrksvt.waen.update

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Progress of an APK download. */
sealed class DownloadState {
    object Idle : DownloadState()
    /**
     * @param percent 0..100, or -1 when the server did not send a content length.
     */
    data class Progress(val percent: Int, val bytesRead: Long, val totalBytes: Long) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
    object Cancelled : DownloadState()
}

/** Percentage of [totalBytes] covered by [bytesRead], clamped to 0..100. */
fun progressPercent(bytesRead: Long, totalBytes: Long): Int {
    if (totalBytes <= 0L) return -1
    val raw = (bytesRead * 100L / totalBytes).toInt()
    return raw.coerceIn(0, 100)
}

/**
 * Downloads a release APK into the app specific external files dir. All network and disk
 * work happens on a background executor; the caller is notified through [listener].
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) {

    interface Listener {
        fun onState(state: DownloadState)
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private var runningCall: okhttp3.Call? = null

    /** Directory holding downloaded APKs, created on demand. */
    fun updateDir(): File {
        val base = context.getExternalFilesDir(UPDATE_DIR) ?: context.filesDir
        val dir = File(base, UPDATE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Starts a download of [url] into a file named after [fileKey]. A second call while a
     * download is running is ignored.
     */
    fun start(url: String, fileKey: String, listener: Listener) {
        if (url.isEmpty()) {
            listener.onState(DownloadState.Failed("empty url"))
            return
        }
        cancelled.set(false)
        executor.execute {
            val target = File(updateDir(), apkFileName(fileKey))
            try {
                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                runningCall = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        listener.onState(DownloadState.Failed("http ${response.code}"))
                        return@use
                    }
                    val body = response.body
                    val total = body.contentLength()
                    val source = body.source()
                    var read = 0L
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var lastPercent = -2
                        while (true) {
                            if (cancelled.get()) break
                            val count = source.read(buffer)
                            if (count == -1) break
                            out.write(buffer, 0, count)
                            read += count
                            val percent = progressPercent(read, total)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                listener.onState(DownloadState.Progress(percent, read, total))
                            }
                        }
                    }
                    if (cancelled.get()) {
                        target.delete()
                        listener.onState(DownloadState.Cancelled)
                        return@use
                    }
                    listener.onState(DownloadState.Done(target))
                }
            } catch (e: Exception) {
                target.delete()
                if (cancelled.get()) {
                    listener.onState(DownloadState.Cancelled)
                } else {
                    listener.onState(DownloadState.Failed(e.message ?: e.javaClass.simpleName))
                }
            } finally {
                runningCall = null
            }
        }
    }

    /**
     * Stops the running download. The partial file is removed by the worker thread that
     * owns the transfer, so the terminal [DownloadState.Cancelled] is reported exactly once.
     */
    fun cancel() {
        cancelled.set(true)
        runningCall?.cancel()
    }

    /** Releases the executor. Call when the owner is destroyed. */
    fun shutdown() {
        cancelled.set(true)
        runningCall?.cancel()
        executor.shutdownNow()
    }

    companion object {
        private const val UPDATE_DIR = "updates"

        /** Visible for tests: the file name used for an APK keyed by [fileKey]. */
        fun apkFileName(fileKey: String): String = "$fileKey.apk"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
