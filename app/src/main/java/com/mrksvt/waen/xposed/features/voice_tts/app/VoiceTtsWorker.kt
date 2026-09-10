package com.mrksvt.waen.xposed.features.voice_tts.app

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.features.voice_tts.app.db.VoiceTtsStore
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.TtsCacheEntity
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.VoiceProfileEntity
import com.mrksvt.waen.xposed.features.voice_tts.core.TtsCachePaths
import java.io.File
import java.security.MessageDigest

/**
 * Two actions (Tugas B):
 *
 * ACTION_EXTRACT_EMBEDDING: called from HookBinder.registerIncomingVoiceNote().
 *   Reads the voice-note audio file, extracts a speaker embedding (placeholder:
 *   zero-filled 256-byte file until the real ML model is plugged in), and
 *   upserts/averages the embedding into voice_profiles.
 *
 * ACTION_GENERATE_TTS: called from HookBinder.requestTTS().
 *   Checks tts_cache first; on miss generates audio via the TtsEngine and
 *   stores the result for later IPC-driven playback.
 *
 * Both run on a background thread managed by WorkManager (survives Doze, is
 * killed-and-retried on crash, etc.).
 */
class VoiceTtsWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_CONTACT_ID = "contact_id"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_TEXT = "text"
        const val KEY_AUDIO_PATH = "audio_path"
        const val KEY_MESSAGE_HASH = "message_hash"
        const val KEY_DURATION_MS = "duration_ms"

        const val ACTION_EXTRACT_EMBEDDING = "extract_embedding"
        const val ACTION_GENERATE_TTS = "generate_tts"

        private const val TAG = "VoiceTtsWorker"
        private const val EMBEDDING_DIM = 256
    }

    override fun doWork(): Result {
        return try {
            when (inputData.getString(KEY_ACTION)) {
                ACTION_EXTRACT_EMBEDDING -> doExtractEmbedding()
                ACTION_GENERATE_TTS -> doGenerateTts()
                else -> {
                    logD("Unknown action, skipping")
                    Result.failure()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "doWork failed: ${t.message}", t)
            Result.retry()
        }
    }

    // ---- embedding extraction ----

    private fun doExtractEmbedding(): Result {
        val contactId = inputData.getString(KEY_CONTACT_ID) ?: return Result.failure()
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()
        val messageHash = inputData.getString(KEY_MESSAGE_HASH) ?: return Result.failure()
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)

        val db = VoiceTtsStore.getInstance(applicationContext)
        if (db.voiceProfileDao().findByContact(contactId)?.lastSourceMessageHash == messageHash) {
            logD("Already trained for this note: $messageHash")
            return Result.success()
        }

        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Log.w(TAG, "Audio file not found: $audioPath")
            return Result.failure()
        }

        val embeddingDir = File(applicationContext.filesDir, "voice_embeddings")
        embeddingDir.mkdirs()
        val embeddingFile = File(embeddingDir, "${contactId}.emb")

        // extractSpeakerEmbedding(audioFile, embeddingFile)
        //   placeholder until ML model is integrated
        extractPlaceholderEmbedding(audioFile, embeddingFile)

        val existing = db.voiceProfileDao().findByContact(contactId)
        if (existing != null) {
            db.voiceProfileDao().upsert(
                existing.copy(
                    embeddingPath = embeddingFile.absolutePath,
                    sourceCount = existing.sourceCount + 1,
                    lastSourceMessageHash = messageHash,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            db.voiceProfileDao().upsert(
                VoiceProfileEntity(
                    contactId = contactId,
                    embeddingPath = embeddingFile.absolutePath,
                    sourceCount = 1,
                    lastSourceMessageHash = messageHash
                )
            )
        }

        db.messageHashDao().insert(
            com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.MessageHashEntity(
                messageHash = messageHash,
                messageId = inputData.getString("message_id") ?: "",
                contactId = contactId,
                audioPath = audioPath,
                durationMs = durationMs
            )
        )

        logD("Embedding saved for $contactId (${embeddingFile.length()} bytes)")
        return Result.success()
    }

    /**
     * Produces a deterministic stub embedding from the audio file hash.
     * Replace with actual zero-shot voice-cloning inference (OpenVoice V2 /
     * YourTTS ONNX) when the ML module is ready. The file format and path
     * contract remain unchanged; only this function body changes.
     */
    private fun extractPlaceholderEmbedding(audioFile: File, outputFile: File) {
        val sha256 = MessageDigest.getInstance("SHA-256")
        audioFile.inputStream().use { ins ->
            val buf = ByteArray(8192)
            var n: Int
            while (true) {
                n = ins.read(buf)
                if (n <= 0) break
                sha256.update(buf, 0, n)
            }
        }
        val hash = sha256.digest()   // 32 bytes
        outputFile.outputStream().use { out ->
            repeat(EMBEDDING_DIM / hash.size + 1) {
                out.write(hash)
            }
            // exact EMBEDDING_DIM bytes
            outputFile.deleteOnExit()
            val pad = EMBEDDING_DIM - (EMBEDDING_DIM / hash.size) * hash.size
            if (pad > 0) out.write(ByteArray(pad))
        }
        // truncate to exact size
        if (outputFile.length() != EMBEDDING_DIM.toLong()) {
            val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
            outputFile.inputStream().use { ins ->
                tmp.outputStream().use { outs ->
                    val buf = ByteArray(1024)
                    var remaining = EMBEDDING_DIM
                    while (remaining > 0) {
                        val read = ins.read(buf, 0, remaining.coerceAtMost(buf.size))
                        if (read <= 0) break
                        outs.write(buf, 0, read)
                        remaining -= read
                    }
                }
            }
            tmp.renameTo(outputFile)
        }
    }

    // ---- TTS generation ----

    private fun doGenerateTts(): Result {
        val contactId = inputData.getString(KEY_CONTACT_ID) ?: return Result.failure()
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()

        val db = VoiceTtsStore.getInstance(applicationContext)
        val textHash = sha256Hex(text)

        // cache hit
        val cachedPath = db.ttsCacheDao().findCachedPath(contactId, textHash)
        if (cachedPath != null && File(cachedPath).exists()) {
            logD("TTS cache hit: $cachedPath")
            return Result.success()
        }

        val cacheDir = File(TtsCachePaths.CACHE_DIR)
        cacheDir.mkdirs()
        val audioFile = File(cacheDir, TtsCachePaths.fileName(contactId, textHash))

        val profile = db.voiceProfileDao().findByContact(contactId)
        val voiceId = profile?.embeddingPath

        val engine = FallbackTtsEngine(applicationContext)
        try {
            val success = engine.speakToFile(text, voiceId, audioFile)
            if (!success) {
                Log.w(TAG, "TTS synthesis failed for msg=$messageId")
                return Result.failure()
            }
        } finally {
            engine.shutdown()
        }

        db.ttsCacheDao().insert(
            TtsCacheEntity(
                messageId = messageId,
                contactId = contactId,
                text = text,
                textHash = textHash,
                audioFilePath = audioFile.absolutePath
            )
        )
        logD("TTS generated: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
        return Result.success()
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }
}
