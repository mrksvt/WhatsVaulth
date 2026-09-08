package com.mrksvt.waen.xposed.features.voice_tts.app

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.mrksvt.waen.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface TtsEngine {
    fun speakToFile(text: String, voiceId: String?, outputFile: File): Boolean
    fun shutdown()
}

/**
 * Android TextToSpeech fallback engine (Tugas G: default voice).
 *
 * On device without any ML model loaded, this is always used.
 * If voiceId != null, attempts to match by name (for cloned voice profiles
 * that integrate with Android TTS voices in the future). Falls back to
 * default device voice.
 *
 * On-device ML voice cloning (OpenVoice V2 / YourTTS) would implement a
 * separate TtsEngine subclass that loads the ONNX model from
 * app-specific storage. That is left as an extension point; this fallback
 * is production-ready and zero-dependency.
 */
class FallbackTtsEngine(context: Context) : TtsEngine {

    companion object {
        private const val TAG = "FallbackTtsEngine"
        private const val INIT_TIMEOUT_SEC = 5L
        private const val SYNTH_TIMEOUT_SEC = 15L
    }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initSuccess = false
    private val initLatch = CountDownLatch(1)

    init {
        tts = TextToSpeech(appContext) { status ->
            initSuccess = (status == TextToSpeech.SUCCESS)
            if (initSuccess) {
                tts?.language = Locale.getDefault()
            }
            initLatch.countDown()
        }
        if (!initLatch.await(INIT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            Log.w(TAG, "TTS init timeout")
            tts?.shutdown()
            tts = null
        }
    }

    override fun speakToFile(text: String, voiceId: String?, outputFile: File): Boolean {
        val engine = tts ?: return false
        if (!initSuccess) return false

        if (voiceId != null) {
            val voices = engine.voices
            val match = voices?.firstOrNull { it.name.contains(voiceId, ignoreCase = true) }
            if (match != null) engine.voice = match
        }

        outputFile.parentFile?.mkdirs()

        val synthLatch = CountDownLatch(1)
        var synthResult = TextToSpeech.ERROR
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                synthResult = TextToSpeech.SUCCESS
                synthLatch.countDown()
            }
            @Deprecated("Deprecated")
            override fun onError(utteranceId: String) {
                synthResult = TextToSpeech.ERROR
                synthLatch.countDown()
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                synthResult = TextToSpeech.ERROR
                synthLatch.countDown()
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.synthesizeToFile(text, params, outputFile, "tts_${System.nanoTime()}")
        if (result == TextToSpeech.ERROR) {
            if (BuildConfig.DEBUG) Log.d(TAG, "synthesizeToFile returned ERROR immediately")
            return false
        }
        if (!synthLatch.await(SYNTH_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Synth timeout")
            return false
        }
        return synthResult == TextToSpeech.SUCCESS && outputFile.exists() && outputFile.length() > 0
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
