package com.mrksvt.waen.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mrksvt.waen.R
import com.mrksvt.waen.media.screenrecorder.MediaMuxerBackend
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderEncoder
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderEngine
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderResult
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderSession
import com.mrksvt.waen.media.screenrecorder.ScreenRecorderStorage
import com.mrksvt.waen.media.screenrecorder.ScreenRecordingValidator
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service pemegang sesi rekam layar mandiri (SR).
 *
 * Seluruh transisi sesi dan operasi codec/muxer berjalan pada satu worker
 * karena `ScreenRecorderSession` menuntut single-thread. Sesi SENGAJA dibuat
 * di worker (bukan saat konstruksi service), sebab kontrak mengikat pemilik
 * ke thread pembuat. UI berkomunikasi lewat [LocalBinder].
 *
 * Consent tidak pernah di-cache: `resultData` dipakai sekali untuk
 * `getMediaProjection`.
 */
class ScreenRecorderService : Service() {

    inner class LocalBinder : Binder() {
        fun stop() {
            worker.execute { stopInternal(ScreenRecorderSession.StopReason.USER_INTERFACE) }
        }

        fun state(): ScreenRecorderSession.Snapshot = sessionRef.get()?.snapshot
            ?: ScreenRecorderSessionHolder.lastSnapshot
    }

    private val binder = LocalBinder()
    private val sessionRef = AtomicReference<ScreenRecorderSession?>(null)
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sr-worker").apply { isDaemon = true }
    }

    private val pendingResultData = AtomicReference<Intent?>(null)
    private var projection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var encoder: ScreenRecorderEncoder? = null
    private var muxer: MediaMuxer? = null
    private var stagingRef: File? = null
    private var finalRef: File? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, RESULT_CODE_INVALID)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode == RESULT_CODE_INVALID || resultData == null) {
                    Log.w(TAG, "start ditolak: consent tidak valid")
                    stopSelf()
                    return START_NOT_STICKY
                }
                pendingResultData.set(resultData)
                startAsForeground()
                worker.execute { beginSession(resultCode) }
            }
            ACTION_STOP -> worker.execute { stopInternal(ScreenRecorderSession.StopReason.NOTIFICATION) }
            else -> {
                Log.w(TAG, "aksi tidak dikenal: ${intent?.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        worker.execute { stopInternal(ScreenRecorderSession.StopReason.SERVICE_DESTROYED) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun session(): ScreenRecorderSession =
        sessionRef.get() ?: ScreenRecorderSession().also { sessionRef.set(it) }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.sr_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
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

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenRecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sr_notification_title))
            .setContentText(getString(R.string.sr_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.sr_notification_stop), stopIntent)
            .build()
    }

    private fun beginSession(resultCode: Int) {
        val session = session()
        val id = session.requestStart() ?: run {
            Log.w(TAG, "sesi sudah aktif, start diabaikan")
            return
        }
        val resultData = pendingResultData.getAndSet(null) ?: run {
            fail(session, id, ScreenRecorderResult.Error.PROJECTION_START)
            return
        }
        val metrics = resources.displayMetrics
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val created = try {
            manager.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            null
        }
        if (created == null) {
            fail(session, id, ScreenRecorderResult.Error.PROJECTION_START)
            return
        }
        projection = created
        session.consentGranted(id)
        created.registerCallback(projectionCallback, null)

        val outcome = ScreenRecorderEncoder.create(metrics.widthPixels, metrics.heightPixels)
        if (outcome !is ScreenRecorderEncoder.Outcome.Ready) {
            fail(session, id, (outcome as ScreenRecorderEncoder.Outcome.Failed).error)
            return
        }
        encoder = outcome.encoder
        session.recordingStarted(id)

        val baseDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val outputDir = ScreenRecorderStorage.outputDir(baseDir)
        outputDir.mkdirs()
        val fileName = ScreenRecorderStorage.buildFileName(
            ScreenRecorderStorage.timestamp(),
            id.value.toString().take(8)
        )
        val staging = ScreenRecorderStorage.stagingFile(outputDir, fileName)
        val finalFile = ScreenRecorderStorage.finalFile(outputDir, fileName)

        val activeMuxer = try {
            MediaMuxer(staging.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            fail(session, id, ScreenRecorderResult.Error.MUXER_FINALIZE)
            return
        }
        muxer = activeMuxer

        virtualDisplay = outcome.encoder.createVirtualDisplay(
            created, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi
        )
        if (virtualDisplay == null) {
            fail(session, id, ScreenRecorderResult.Error.PROJECTION_START)
            return
        }
        stagingRef = staging
        finalRef = finalFile
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            worker.execute { stopInternal(ScreenRecorderSession.StopReason.PROJECTION_REVOKED) }
        }
    }

    private fun stopInternal(reason: ScreenRecorderSession.StopReason) {
        val session = sessionRef.get()
        val snapshot = session?.snapshot
        val id = snapshot?.sessionId

        if (session != null && id != null) {
            when (snapshot.state) {
                ScreenRecorderSession.State.REQUESTING -> session.cancelConsent(id)
                ScreenRecorderSession.State.STARTING,
                ScreenRecorderSession.State.RECORDING -> session.requestStop(id, reason)
                else -> Unit
            }
        }

        virtualDisplay?.release()
        virtualDisplay = null

        val activeMuxer = muxer
        val activeEncoder = encoder
        val staging = stagingRef
        val finalFile = finalRef
        val dir = staging?.parentFile

        val outcome = if (activeEncoder != null && activeMuxer != null) {
            val engine = ScreenRecorderEngine(
                activeEncoder.drainBackend(),
                MediaMuxerBackend(activeMuxer),
                clock = { System.nanoTime() / 1_000L }
            )
            val result = engine.stopAndFinalize(System.nanoTime() / 1_000L + DRAIN_BUDGET_US)
            engine.releaseMuxer()
            result
        } else {
            runCatching { activeMuxer?.stop() }
            runCatching { activeMuxer?.release() }
            null
        }
        muxer = null
        activeEncoder?.release()
        encoder = null
        releaseProjection()

        if (session != null && id != null) {
            when (outcome) {
                is ScreenRecorderEngine.Outcome.Saved -> publishSaved(session, id, staging, finalFile, dir)
                null -> fail(session, id, ScreenRecorderResult.Error.SESSION_INTERRUPTED)
                is ScreenRecorderEngine.Outcome.Failed -> fail(session, id, outcome.error)
            }
        }
        teardown()
    }

    private fun publishSaved(
        session: ScreenRecorderSession,
        id: ScreenRecorderSession.Id,
        staging: File?,
        finalFile: File?,
        dir: File?
    ) {
        if (staging == null || finalFile == null) {
            fail(session, id, ScreenRecorderResult.Error.OUTPUT_VALIDATION)
            return
        }
        val metadata = ScreenRecordingValidator.readMetadata(staging)
        val error = ScreenRecordingValidator.validate(metadata)
        if (error != null || metadata == null) {
            fail(session, id, error ?: ScreenRecorderResult.Error.OUTPUT_VALIDATION)
            return
        }
        if (!ScreenRecorderStorage.publish(staging, finalFile)) {
            fail(session, id, ScreenRecorderResult.Error.OUTPUT_PUBLISH)
            return
        }
        dir?.let { ScreenRecorderStorage.writeLastResult(it, finalFile) }
        session.completeSaved(
            ScreenRecorderResult.Saved(
                sessionId = id,
                outputPath = finalFile.absolutePath,
                durationUs = metadata.durationUs,
                videoSampleCount = metadata.sampleCount,
                width = metadata.width,
                height = metadata.height
            )
        )
    }

    private fun fail(
        session: ScreenRecorderSession,
        id: ScreenRecorderSession.Id,
        error: ScreenRecorderResult.Error
    ) {
        val partial = stagingRef?.takeIf { it.exists() }?.absolutePath
        session.completeFailed(
            ScreenRecorderResult.Failed(sessionId = id, error = error, partialPath = partial)
        )
    }

    private fun releaseProjection() {
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        pendingResultData.set(null)
    }

    private fun teardown() {
        sessionRef.get()?.let { ScreenRecorderSessionHolder.publish(it.snapshot) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.mrksvt.waen.sr.ACTION_START"
        const val ACTION_STOP = "com.mrksvt.waen.sr.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "sr_result_code"
        const val EXTRA_RESULT_DATA = "sr_result_data"

        private const val CHANNEL_ID = "wae_screen_recorder"
        private const val NOTIFICATION_ID = 9821779
        private const val RESULT_CODE_INVALID = Int.MIN_VALUE
        private const val DRAIN_BUDGET_US = 8_000_000L
        private const val TAG = "ScreenRecorderService"

        fun start(context: Context, resultCode: Int, resultData: Intent?): Boolean {
            if (resultCode == RESULT_CODE_INVALID || resultData == null) return false
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService ditolak: ${e.message}")
                false
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, ScreenRecorderService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}

/** Snapshot terakhir agar UI bisa membacanya setelah service berhenti. */
object ScreenRecorderSessionHolder {
    @Volatile
    var lastSnapshot: ScreenRecorderSession.Snapshot = ScreenRecorderSession.Snapshot()
        private set

    fun publish(snapshot: ScreenRecorderSession.Snapshot) {
        lastSnapshot = snapshot
    }
}
