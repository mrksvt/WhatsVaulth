package com.mrksvt.waen.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import com.mrksvt.waen.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fallback pemutar TTS saat startForegroundService ditolak (Android 12+ melarang
 * FGS dari broadcast background, dan BridgeService hanya bound - tidak
 * foreground). Klip TTS pendek, jadi cukup: satu worker thread serial,
 * semaphore tunggu sampai selesai, partial wake lock agar CPU tidak tidur.
 */
object TtsInlinePlayer {

    private const val TAG = "TtsInlinePlayer"
    private const val MAX_CLIP_MS = 60_000L

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WaeTtsInline").apply { isDaemon = true }
    }
    private val done = Semaphore(0)

    @Volatile
    private var currentPath: String? = null

    fun play(context: Context, audioPath: String) {
        currentPath = audioPath
        while (done.tryAcquire()) {}
        executor.execute { playBlocking(context, audioPath) }
    }

    fun stop() {
        currentPath = null
        done.release()
    }

    private fun playBlocking(context: Context, audioPath: String) {
        val wl = try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "waen:tts-inline")
                .apply { acquire(MAX_CLIP_MS + 10_000L) }
        } catch (_: Throwable) {
            null
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        @Suppress("DEPRECATION")
        val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS) currentPath = null
        }
        @Suppress("DEPRECATION")
        val granted = am.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        var mp: MediaPlayer? = null
        try {
            if (currentPath != audioPath) return
            mp = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(audioPath)
                setOnCompletionListener { done.release() }
                setOnErrorListener { _, _, _ -> done.release(); true }
                prepare()
                if (currentPath == audioPath) start() else release()
            }
            done.tryAcquire(MAX_CLIP_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, "inline play failed: ${t.message}")
        } finally {
            mp?.let { p ->
                runCatching { if (p.isPlaying) p.stop() }
                runCatching { p.release() }
            }
            if (granted) {
                @Suppress("DEPRECATION")
                runCatching { am.abandonAudioFocus(focusListener) }
            }
            runCatching { wl?.release() }
            if (currentPath == audioPath) currentPath = null
        }
    }
}
