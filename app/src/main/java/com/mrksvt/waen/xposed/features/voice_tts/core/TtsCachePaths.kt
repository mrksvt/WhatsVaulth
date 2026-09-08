package com.mrksvt.waen.xposed.features.voice_tts.core

import com.mrksvt.waen.BuildConfig
import java.security.MessageDigest

/**
 * Kontrak penamaan file cache TTS, dipakai sisi app (writer) dan sisi hook
 * (poller) supaya keduanya menghitung path yang sama tanpa IPC tambahan.
 *
 * Sisi hook TIDAK boleh membaca Room DB sisi app (proses berbeda, file lock
 * berbeda), jadi keberadaan file audio dicek via bridge.exists(path).
 */
object TtsCachePaths {

    /** Direktori cache di storage privat aplikasi WhatsVault (sisi app). */
    @JvmStatic
    val CACHE_DIR: String = "/data/data/${BuildConfig.APPLICATION_ID}/files/tts_cache"

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Nama file deterministik: sanitasi contactId (JID mengandung '@' dan '.')
     * lalu digabung dengan hash teks. Teks sama + kontak sama => file sama,
     * sehingga cache hit langsung terlihat oleh poller.
     */
    fun fileName(contactId: String, textHash: String): String {
        val safeContact = contactId.map {
            if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_'
        }.joinToString("")
        return "${safeContact}_$textHash.wav"
    }

    fun cacheFile(contactId: String, textHash: String): String =
        "$CACHE_DIR/${fileName(contactId, textHash)}"
}
