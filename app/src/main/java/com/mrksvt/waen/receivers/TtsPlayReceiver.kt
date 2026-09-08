package com.mrksvt.waen.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.services.TtsPlaybackService

/**
 * Tugas F: handles play/stop PendingIntents fired from TTS notification actions.
 *
 * Delegates playback to [TtsPlaybackService] (foreground service) instead of
 * playing inline, because audio playback requires a service lifecycle
 * (MediaPlayer must survive after the BroadcastReceiver returns).
 */
class TtsPlayReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_TTS = "com.mrksvt.waen.ACTION_PLAY_TTS"
        const val ACTION_STOP_TTS = "com.mrksvt.waen.ACTION_STOP_TTS"
        const val EXTRA_MESSAGE_ID = "tts_message_id"
        const val EXTRA_AUDIO_PATH = "tts_audio_path"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_TTS -> {
                val audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH) ?: return
                val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
                // Guard: hanya file hasil TTS milik app sendiri yang boleh diputar.
                // PendingIntent dibuat di proses WhatsApp (UID berbeda), jadi
                // input eksternal wajib divalidasi sebelum start service.
                if (!isAllowedTtsPath(context, audioPath)) return
                TtsPlaybackService.startPlay(context, audioPath, messageId)
            }
            ACTION_STOP_TTS -> {
                TtsPlaybackService.stop(context)
            }
        }
    }

    /**
     * Path harus berada tepat di direktori cache TTS app, nama file .wav,
     * tanpa traversal.
     */
    private fun isAllowedTtsPath(context: Context, audioPath: String): Boolean {
        return try {
            val cacheDir = java.io.File(context.filesDir, "tts_cache").canonicalFile
            val f = java.io.File(audioPath)
            val canon = f.canonicalFile
            canon.absolutePath.startsWith(cacheDir.absolutePath + java.io.File.separator) &&
                canon.name.endsWith(".wav")
        } catch (_: Exception) {
            false
        }
    }
}
