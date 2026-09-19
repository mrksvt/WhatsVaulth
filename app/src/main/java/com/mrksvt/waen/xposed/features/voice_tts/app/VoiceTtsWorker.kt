package com.mrksvt.waen.xposed.features.voice_tts.app

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.features.voice_tts.app.db.VoiceTtsStore
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.TtsCacheEntity
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.VoiceProfileEntity
import com.mrksvt.waen.xposed.features.voice_tts.core.AudioDecoder
import com.mrksvt.waen.xposed.features.voice_tts.core.SpeakerEmbedding
import com.mrksvt.waen.xposed.features.voice_tts.core.TtsCachePaths
import com.mrksvt.waen.xposed.features.voice_tts.core.VoiceExpression
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.pow

/**
 * Two actions (Tugas B):
 *
 * ACTION_EXTRACT_EMBEDDING: user-triggered dari screen TTS (VoiceNotesFragment)
 *   setelah note ditandai train+ekspresi. Semua note trained utk ekspresi tsb
 *   di-decode (opus -> PCM), vektor akustiknya (SpeakerEmbedding) dirata-rata,
 *   ditulis ke <contact>.<expr>.emb + sidecar <contact>.json per ekspresi.
 *
 * ACTION_GENERATE_TTS: called from HookBinder.requestTTS().
 *   Ekspresi dipilih dari emoji dalam teks (VoiceExpression.detect); engine
 *   memakai pitch/rate sidecar ekspresi tsb.
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
        const val KEY_EXPRESSION = "expression"

        const val ACTION_EXTRACT_EMBEDDING = "extract_embedding"
        const val ACTION_GENERATE_TTS = "generate_tts"

        private const val TAG = "VoiceTtsWorker"
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

    // ---- training ----

    private fun doExtractEmbedding(): Result {
        val contactId = inputData.getString(KEY_CONTACT_ID) ?: return Result.failure()
        val expression = inputData.getString(KEY_EXPRESSION) ?: VoiceExpression.NORMAL

        val db = VoiceTtsStore.getInstance(applicationContext)
        val sources = (if (expression == VoiceExpression.AUTO)
            db.messageHashDao().allTrainedForContact(contactId)
        else
            db.messageHashDao().trainedForExpression(contactId, expression)
            ).filter { File(it.audioPath).exists() }
        if (sources.isEmpty()) {
            Log.w(TAG, "No trained notes on disk for $contactId/$expression")
            return Result.failure()
        }

        val vectors = ArrayList<FloatArray>()
        var f0Sum = 0f
        var f0N = 0
        var vpSum = 0f
        for (row in sources) {
            val pcm = AudioDecoder.decodeToPcm(File(row.audioPath)) ?: continue
            if (pcm.size < 8192) continue
            val res = SpeakerEmbedding.compute(pcm) ?: continue
            vectors.add(res.vector)
            if (res.f0Hz > 0f) {
                f0Sum += res.f0Hz
                vpSum += res.voicedPerSec
                f0N++
            }
        }
        if (vectors.isEmpty()) {
            Log.w(TAG, "All ${sources.size} notes failed decode/analysis for $contactId/$expression")
            return Result.failure()
        }

        val embDir = File(applicationContext.filesDir, "voice_embeddings")
        embDir.mkdirs()
        val vectorBytes = SpeakerEmbedding.toBytesLE(SpeakerEmbedding.average(vectors))

        val f0 = if (f0N > 0) f0Sum / f0N else 0f
        val voiced = if (f0N > 0) vpSum / f0N else 0f
        val pitch = if (f0 > 0f) {
            val oct = ln((f0 / 155f).toDouble()) / Math.log(2.0) / 2.0
            2.0.pow(oct).toFloat().coerceIn(0.6f, 1.6f)
        } else {
            1.0f
        }
        // voiced = frame gate per detik (0..100, hop 10ms): densitas energi
        // bicara, dipetakan ke rentang tempo Google TTS yang wajar.
        val rate = if (f0 > 0f) {
            (0.8f + voiced / 100f * 0.3f).coerceIn(0.75f, 1.15f)
        } else {
            0.85f
        }

        val sidecar = File(embDir, "$contactId.json")
        val root = runCatching { JSONObject(sidecar.readText()) }.getOrElse { JSONObject() }
        val expressions = root.optJSONObject("expressions") ?: JSONObject().also {
            root.put("expressions", it)
        }
        val now = System.currentTimeMillis()
        // AUTO: satu vektor sidik jari untuk semua emosi; emosi hanya beda
        // prosodi (pitch/rate) hasil turunan baseline.
        val targets = if (expression == VoiceExpression.AUTO) {
            VoiceExpression.derive(pitch, rate)
        } else {
            mapOf(expression to (pitch to rate))
        }
        for ((expr, pr) in targets) {
            File(embDir, "$contactId.$expr.emb").writeBytes(vectorBytes)
            expressions.put(
                expr,
                JSONObject()
                    .put("f0Hz", f0.toDouble())
                    .put("pitch", pr.first.toDouble())
                    .put("rate", pr.second.toDouble())
                    .put("sources", vectors.size)
                    .put("updatedAt", now)
            )
        }
        root.put("updatedAt", now)
        sidecar.writeText(root.toString())

        val totalTrained = db.messageHashDao().allForContact(contactId).count { it.trained }
        val existing = db.voiceProfileDao().findByContact(contactId)
        val primaryExpr = if (expression == VoiceExpression.AUTO) VoiceExpression.NORMAL else expression
        val primaryEmb = File(embDir, "$contactId.$primaryExpr.emb").absolutePath
        val embeddingPath = if (primaryExpr == VoiceExpression.NORMAL) {
            primaryEmb
        } else {
            existing?.embeddingPath?.takeIf { it.isNotBlank() } ?: primaryEmb
        }
        db.voiceProfileDao().upsert(
            (existing ?: VoiceProfileEntity(
                contactId = contactId,
                embeddingPath = embeddingPath
            )).copy(
                embeddingPath = embeddingPath,
                sourceCount = totalTrained,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Audio lama di-cache dengan parameter voice sebelumnya; buang supaya
        // generate ulang memakai pitch/rate hasil training barusan.
        db.ttsCacheDao().deleteByContact(contactId)

        logD("Trained $contactId/$expression: ${vectors.size}/${sources.size} notes, f0=$f0 pitch=$pitch rate=$rate")
        return Result.success()
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

        val expression = VoiceExpression.detect(text)

        val engine = FallbackTtsEngine(applicationContext)
        try {
            val success = engine.speakToFile(text, contactId, expression, audioFile)
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
        logD("TTS generated ($expression): ${audioFile.absolutePath} (${audioFile.length()} bytes)")
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
