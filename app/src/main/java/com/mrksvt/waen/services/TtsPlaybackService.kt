package com.mrksvt.waen.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.R
import com.mrksvt.waen.receivers.TtsPlayReceiver
import java.io.File

/**
 * Tugas F: foreground service yang memutar file hasil TTS dari cache.
 *
 * Dijalankan oleh TtsPlayReceiver (PendingIntent broadcast dari action
 * notifikasi, bukan Activity). Sequence:
 *   request AudioFocus -> play MediaPlayer -> selesai/error ->
 *   abandon focus -> stopSelf.
 *
 * AudioFocus dipakai TRANSIENT_MAY_DUCK supaya musik/user media lain hanya
 * mengecil, tidak berhenti (sopan terhadap pemutar lain).
 */
class TtsPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.mrksvt.waen.tts.ACTION_PLAY"
        const val ACTION_STOP = "com.mrksvt.waen.tts.ACTION_STOP"
        const val EXTRA_AUDIO_PATH = "audio_path"
        const val EXTRA_MESSAGE_ID = "message_id"

        private const val CHANNEL_ID = "wae_tts_playback"
        private const val NOTIFICATION_ID = 9821777

        fun startPlay(context: Context, audioPath: String, messageId: String): Boolean {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_AUDIO_PATH, audioPath)
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            return try {
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("TtsPlaybackService", "startPlay failed: ${t.message}")
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, TtsPlaybackService::class.java).apply {
                    action = ACTION_STOP
                })
            } catch (_: Exception) {}
        }
    }

    private var player: MediaPlayer? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var abandoning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8+ contract: if started via startForegroundService(), must call
        // startForeground() within ~5s regardless of whether we will play anything.
        startForeground(NOTIFICATION_ID, buildNotification(intent?.getStringExtra(EXTRA_MESSAGE_ID) ?: ""))
        when (intent?.action) {
            ACTION_PLAY -> handlePlay(intent)
            ACTION_STOP -> {
                releaseAndStop()
                stopSelf(startId)
            }
            else -> {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handlePlay(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_AUDIO_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.exists() || file.length() == 0L) {
            stopSelf()
            return
        }

        releasePlayer()

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    try { player?.pause() } catch (_: Exception) {}
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // duck: volume rendah, tetap jalan
                    try { player?.setVolume(0.2f, 0.2f) } catch (_: Exception) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    try {
                        player?.setVolume(1f, 1f)
                        player?.start()
                    } catch (_: Exception) {}
                }
            }
        }

        val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(listener)
            .build()

        val result = am.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED && BuildConfig.DEBUG) {
            android.util.Log.d("TtsPlaybackService", "focus not granted (result=$result), playing anyway")
        }
        focusRequest = request

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { releaseAndStop() }
            mp.setOnErrorListener { _, _, _ ->
                releaseAndStop()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("TtsPlaybackService", "play failed: ${t.message}")
            releaseAndStop()
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    private fun releaseAndStop() {
        if (abandoning) return
        abandoning = true
        releasePlayer()
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        focusRequest = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildNotification(messageId: String): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mgr.getNotificationChannel(CHANNEL_ID) == null
        ) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.voice_tts_notification_title), NotificationManager.IMPORTANCE_LOW)
                    .apply { setSound(null, null) }
            )
        }

        val stopIntent = Intent(this, TtsPlayReceiver::class.java).apply {
            action = TtsPlayReceiver.ACTION_STOP_TTS
            putExtra(TtsPlayReceiver.EXTRA_MESSAGE_ID, messageId)
        }
        val stopPi = PendingIntent.getBroadcast(
            this, messageId.hashCode(), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.voice_tts_notification_title))
            .setContentText(getString(R.string.voice_tts_notification_playing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.voice_tts_stop), stopPi)
            .build()
    }
}
