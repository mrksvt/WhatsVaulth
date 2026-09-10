package com.mrksvt.waen.xposed.bridge.service

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mrksvt.waen.App
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.bridge.WaeIIFace
import com.mrksvt.waen.xposed.features.voice_tts.app.VoiceTtsWorker
import com.mrksvt.waen.xposed.features.voice_tts.app.db.VoiceTtsStore
import java.io.File
import java.io.FileNotFoundException

object HookBinder : WaeIIFace.Stub() {

    private const val TAG = "HookBinder"

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    // ---- existing file ops ----

    override fun openFile(path: String, create: Boolean): ParcelFileDescriptor? {
        val file = File(path)
        if (!file.exists() && create) {
            try {
                file.parentFile?.mkdirs()
                file.createNewFile()
            } catch (_: Exception) {
                return null
            }
        }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun createDir(path: String): Boolean {
        return File(path).mkdirs()
    }

    override fun exists(path: String): Boolean {
        return File(path).exists()
    }

    override fun listFiles(path: String): List<File> {
        return File(path).listFiles()?.toList() ?: emptyList()
    }

    // ---- Contact Voice TTS IPC ----

    override fun requestTTS(contactId: String, messageId: String, text: String): String {
        return try {
            val ctx = getApplicationContext() ?: return "App context not available"
            enqueueTtsWork(ctx, contactId, messageId, text)
            ""
        } catch (e: Exception) {
            Log.w(TAG, "requestTTS failed: ${e.message}")
            e.message ?: "Unknown error"
        }
    }

    override fun isAutoTtsEnabled(contactId: String): Int {
        return try {
            val ctx = getApplicationContext() ?: return 0
            val db = VoiceTtsStore.getInstance(ctx)
            val row = db.voiceProfileDao().findByContact(contactId)
            if (row?.autoTtsEnabled == true) 1 else 0
        } catch (e: Exception) {
            Log.w(TAG, "isAutoTtsEnabled failed: ${e.message}")
            0
        }
    }

    override fun setAutoTtsEnabled(contactId: String, enabled: Int): Int {
        return try {
            val ctx = getApplicationContext() ?: return -1
            val db = VoiceTtsStore.getInstance(ctx)
            db.voiceProfileDao().ensureRow(contactId)
            val changed = db.voiceProfileDao().setAutoTtsEnabled(contactId, enabled == 1)
            if (changed == 0) {
                Log.w(TAG, "setAutoTtsEnabled: row missing/unupdated for $contactId")
                -1
            } else {
                0
            }
        } catch (e: Exception) {
            Log.w(TAG, "setAutoTtsEnabled failed: ${e.message}")
            -1
        }
    }

    override fun getContactVoiceProfileStatus(contactId: String): Int {
        return try {
            val ctx = getApplicationContext() ?: return -1
            val db = VoiceTtsStore.getInstance(ctx)
            val profile = db.voiceProfileDao().findByContact(contactId)
            if (profile != null && profile.embeddingPath.isNotBlank()) 1 else 0
        } catch (e: Exception) {
            Log.w(TAG, "getContactVoiceProfileStatus failed: ${e.message}")
            -1
        }
    }

    override fun registerIncomingVoiceNote(
        contactId: String,
        messageHash: String,
        audioPath: String,
        durationMs: Long,
        timestamp: Long
    ): String {
        return try {
            val ctx = getApplicationContext() ?: return "App context not available"
            val db = VoiceTtsStore.getInstance(ctx)
            val exists = db.messageHashDao().exists(messageHash)
            if (exists) {
                logD("Voice note already registered: $messageHash")
                return ""
            }
            db.messageHashDao().insert(
                com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.MessageHashEntity(
                    messageHash = messageHash,
                    messageId = "",
                    contactId = contactId,
                    audioPath = audioPath,
                    durationMs = durationMs,
                    createdAt = timestamp
                )
            )
            // enqueue embedding extraction
            val workRequest = OneTimeWorkRequestBuilder<VoiceTtsWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(VoiceTtsWorker.KEY_ACTION, VoiceTtsWorker.ACTION_EXTRACT_EMBEDDING)
                        .putString(VoiceTtsWorker.KEY_CONTACT_ID, contactId)
                        .putString(VoiceTtsWorker.KEY_AUDIO_PATH, audioPath)
                        .putString(VoiceTtsWorker.KEY_MESSAGE_HASH, messageHash)
                        .putLong(VoiceTtsWorker.KEY_DURATION_MS, durationMs)
                        .build()
                )
                .addTag("voice_tts_embedding")
                .build()
            WorkManager.getInstance(ctx).enqueue(workRequest)
            logD("Voice note registered & embedding work enqueued for $contactId")
            ""
        } catch (e: Exception) {
            Log.w(TAG, "registerIncomingVoiceNote failed: ${e.message}")
            e.message ?: "Unknown error"
        }
    }

    // ---- helpers ----

    private fun getApplicationContext(): Context? {
        return App.instance?.applicationContext
    }

    private fun enqueueTtsWork(
        context: Context,
        contactId: String,
        messageId: String,
        text: String
    ) {
        val workRequest = OneTimeWorkRequestBuilder<VoiceTtsWorker>()
            .setInputData(
                Data.Builder()
                    .putString(VoiceTtsWorker.KEY_ACTION, VoiceTtsWorker.ACTION_GENERATE_TTS)
                    .putString(VoiceTtsWorker.KEY_CONTACT_ID, contactId)
                    .putString(VoiceTtsWorker.KEY_MESSAGE_ID, messageId)
                    .putString(VoiceTtsWorker.KEY_TEXT, text)
                    .build()
            )
            .addTag("voice_tts_generate")
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
        logD("TTS work enqueued: contact=$contactId msg=$messageId")
    }
}
