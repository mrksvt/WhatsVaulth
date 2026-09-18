package com.mrksvt.waen.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.R
import com.mrksvt.waen.media.AudioVideoMuxer
import com.mrksvt.waen.media.CaptureLimits
import com.mrksvt.waen.media.CaptureFrame
import com.mrksvt.waen.media.ScreenCapturePipeline
import com.mrksvt.waen.media.VideoEncoderProfile
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service pemegang `MediaProjection` untuk rekaman layar video call.
 *
 * Service ini sengaja TIDAK menyentuh pipeline encoder; itu tugas
 * `ScreenCapturePipeline` di WP-04 lewat `ScreenCaptureController`. Di sini
 * hanya: memegang projection, menampilkan notifikasi, dan memberi tahu
 * pemanggil saat projection mati.
 *
 * Android 14+ (API 34) mengizinkan satu konsumsi token per projection. Setiap
 * panggilan butuh consent baru lewat `ScreenCapturePermissionActivity`.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.mrksvt.waen.screen.ACTION_START"
        const val ACTION_STOP = "com.mrksvt.waen.screen.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_AUDIO_PATH = "audio_path"

        private const val CHANNEL_ID = "wae_screen_capture"
        private const val NOTIFICATION_ID = 9821778
        private const val SIZE_CHECK_INTERVAL_MS = 15_000L
        private const val TAG = "ScreenCaptureService"

        const val PREF_VIDEO_QUALITY = "call_recording_video_quality"

        /** Dipanggil pipeline saat projection hilang di luar kendali kita. */
        @Volatile
        var onProjectionStopped: (() -> Unit)? = null

        /** Dipanggil saat encoder gagal start. Audio tetap disimpan (SC-03). */
        @Volatile
        var onCaptureFailed: (() -> Unit)? = null

        /** Projection aktif, atau null. Dibaca `ScreenCaptureController`. */
        @Volatile
        var activeProjection: MediaProjection? = null
            private set

        @Volatile
        var isCapturing: Boolean = false
            private set

        /**
         * Mulai capture. `resultData` wajib berasal dari
         * `createScreenCaptureIntent()`; kalau null atau resultCode bukan
         * RESULT_OK, service tidak akan mulai dan mengembalikan false.
         */
        fun start(
            context: Context,
            resultCode: Int,
            resultData: Intent?,
            outputPath: String,
            audioPath: String? = null
        ): Boolean {
            if (resultCode != Activity.RESULT_OK || resultData == null) {
                logW("start() dipanggil tanpa consent valid (code=$resultCode, data=${resultData != null})")
                return false
            }
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
                putExtra(EXTRA_AUDIO_PATH, audioPath)
            }
            return try {
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                logW("startForegroundService gagal: ${t.message}")
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
                )
            } catch (t: Throwable) {
                logW("stop() gagal: ${t.message}")
            }
        }

        private fun logW(msg: String) {
            if (BuildConfig.DEBUG) Log.w(TAG, msg)
        }
    }

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var pipelineRef: ScreenCapturePipeline? = null
    private var audioPath: String? = null
    private var watchdog: Timer? = null
    private var outputPath: String? = null
    private val stopping = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private fun logW(msg: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
                }
                outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH)

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    logW("onStartCommand: consent tidak valid, stop. code=$resultCode")
                    notifyConsentDenied()
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundCompat()
                if (!acquireProjection(resultCode, resultData)) {
                    notifyConsentDenied()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun acquireProjection(resultCode: Int, resultData: Intent): Boolean {
        if (projection != null) {
            logW("acquireProjection: projection sudah aktif")
            return true
        }
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = manager.getMediaProjection(resultCode, resultData)
            if (newProjection == null) {
                logW("getMediaProjection mengembalikan null")
                return false
            }

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    logW("projection dihentikan oleh sistem")
                    cancelWatchdog()
                    stopPipeline()
                    releaseProjection()
                    isCapturing = false
                    muxIfPossible()
                    onProjectionStopped?.invoke()
                    finishAndStopSelf()
                }
            }
            newProjection.registerCallback(callback, mainHandler)

            projection = newProjection
            projectionCallback = callback
            activeProjection = newProjection
            isCapturing = true

            startPipelineIfPossible(newProjection)
            true
        } catch (t: Throwable) {
            logW("getMediaProjection gagal: ${t.message}")
            false
        }
    }

    /**
     * Menyalakan `ScreenCapturePipeline` untuk projection yang baru didapat.
     *
     * Kegagalan di sini TIDAK menggagalkan service: rekaman audio tetap jalan
     * (SC-03). Service hanya berhenti kalau projection-nya sendiri tidak ada.
     */
    private fun startPipelineIfPossible(active: MediaProjection) {
        val path = outputPath
        if (path.isNullOrBlank()) {
            logW("outputPath kosong, capture video dilewati")
            return
        }

        val available = try {
            File(path).let { target ->
                target.mkdirs()
                target.usableSpace
            }
        } catch (t: Throwable) {
            logW("tidak bisa membaca ruang tersisa: ${t.message}")
            -1L
        }

        if (available in 0..Long.MAX_VALUE &&
            !CaptureLimits.canStartCapture(available)
        ) {
            logW("ruang tidak cukup untuk capture video, dilewati")
            onCaptureFailed?.invoke()
            return
        }

        val metrics = resources.displayMetrics
        val pipeline = ScreenCapturePipeline(File(path))
        val preference = prefsOrNull()?.getString(PREF_VIDEO_QUALITY, VideoEncoderProfile.PREF_AUTO)

        val frame = CaptureFrame.fullScreen(metrics.widthPixels, metrics.heightPixels)
        val started = pipeline.startCapture(
            projection = active,
            frame = frame,
            densityDpi = metrics.densityDpi,
            preference = preference
        )

        if (started) {
            pipelineRef = pipeline
            logD("pipeline capture jalan -> ${pipeline.profile?.resolutionLabel}")
            startSizeWatchdog(File(path))
        } else {
            logW("pipeline gagal start; rekaman audio tidak terpengaruh")
            onCaptureFailed?.invoke()
        }
    }

    private fun prefsOrNull() = try {
        PreferenceManager.getDefaultSharedPreferences(this)
    } catch (t: Throwable) {
        logW("prefs tidak tersedia: ${t.message}")
        null
    }

    /**
     * Hentikan capture kalau file sudah melewati batas ukuran. Audio tetap
     * disimpan (SC-03); hanya capture layar yang dihentikan.
     */
    private fun startSizeWatchdog(videoDir: File) {
        watchdog?.cancel()
        val task = object : TimerTask() {
            override fun run() {
                try {
                    val bytes = currentCaptureBytes(videoDir)
                    if (CaptureLimits.exceedsSizeLimit(bytes)) {
                        logW("batas ukuran tercapai ($bytes byte), capture dihentikan")
                        stopCapture()
                    }
                } catch (t: Throwable) {
                    logW("size watchdog error: ${t.message}")
                }
            }
        }
        val timer = Timer("WaEnhancer-SizeWatchdog", true)
        timer.schedule(task, SIZE_CHECK_INTERVAL_MS, SIZE_CHECK_INTERVAL_MS)
        watchdog = timer
    }

    private fun currentCaptureBytes(videoDir: File): Long {
        val file = outputPath?.let { File(it) }
        val videoBytes = if (file != null && file.exists()) file.length() else 0L
        val audioBytes = audioPath?.let { File(it) }?.takeIf { it.exists() }?.length() ?: 0L
        return videoBytes + audioBytes
    }

    private fun stopPipeline() {
        val pipeline = pipelineRef
        pipelineRef = null
        try {
            pipeline?.stop()
        } catch (t: Throwable) {
            logW("pipeline.stop() gagal: ${t.message}")
        }
    }

    private fun stopCapture() {
        if (!stopping.compareAndSet(false, true)) return
        cancelWatchdog()
        stopPipeline()
        releaseProjection()
        isCapturing = false
        muxIfPossible()
        finishAndStopSelf()
    }

    /**
     * Gabungkan video-only dengan audio `.m4a` dari sisi hook.
     *
     * Kebijakan `FINDINGS.md` B-4 opsi 1: kalau mux gagal, file video mentah
     * TIDAK dihapus, dan audio juga dibiarkan. Kalau berhasil, hanya file video
     * mentah yang dibuang (isinya sudah pindah ke file hasil).
     */
    private fun muxIfPossible() {
        val videoFile = outputPath?.let { File(it) } ?: return
        val audioFile = audioPath?.let { File(it) }

        if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
            logW("audio tidak tersedia, video mentah dipertahankan")
            return
        }
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            logW("video kosong, tidak ada yang di-mux")
            return
        }

        val kindFolder = videoFile.parentFile ?: return
        val identifier = videoFile.nameWithoutExtension.substringBeforeLast("-video")
        val merged = File(kindFolder, "$identifier.merged.mp4")

        val success = AudioVideoMuxer.mux(videoFile, audioFile, merged)
        val outcome = AudioVideoMuxer.onMuxResult(success)
        logD("mux outcome=$outcome -> ${merged.absolutePath}")

        if (success && outcome == AudioVideoMuxer.OUTCOME_KEEP_PRIMARY_ONLY) {
            val renamed = File(kindFolder, merged.name.removeSuffix(".merged.mp4") + ".mp4")
            if (!merged.renameTo(renamed)) {
                logW("rename hasil mux gagal, file tetap di ${merged.name}")
            }
            if (!videoFile.delete()) {
                logW("tidak bisa menghapus video mentah, dibiarkan: ${videoFile.name}")
            }
        }
    }

    private fun releaseProjection() {
        val callback = projectionCallback
        projectionCallback = null
        val current = projection
        projection = null
        activeProjection = null

        try {
            if (callback != null) current?.unregisterCallback(callback)
        } catch (t: Throwable) {
            logW("unregisterCallback gagal: ${t.message}")
        }
        try {
            current?.stop()
        } catch (t: Throwable) {
            logW("projection.stop() gagal: ${t.message}")
        }
    }

    private fun finishAndStopSelf() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
            logW("stopForeground gagal: ${t.message}")
        }
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyConsentDenied() {
        if (BuildConfig.DEBUG) Log.d(TAG, "consent ditolak, rekaman audio tidak terpengaruh")
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.screen_capture_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setSound(null, null) }
            )
        }

        val stopPi = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.screen_capture_stop),
                stopPi
            )
            .build()
    }

    private fun cancelWatchdog() {
        try {
            watchdog?.cancel()
        } catch (_: Throwable) {
        }
        watchdog = null
    }

    override fun onDestroy() {
        cancelWatchdog()
        stopPipeline()
        releaseProjection()
        isCapturing = false
        activeProjection = null
        super.onDestroy()
    }
}
