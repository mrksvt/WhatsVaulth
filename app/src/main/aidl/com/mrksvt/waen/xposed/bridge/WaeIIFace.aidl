// WaeIIFace.aidl - Extended for Contact Voice TTS
package com.mrksvt.waen.xposed.bridge;

import android.os.ParcelFileDescriptor;
import java.util.List;

interface WaeIIFace {
    // --- existing file ops ---
    ParcelFileDescriptor openFile(String path, boolean create);
    boolean createDir(String path);
    List listFiles(String path);
    boolean exists(String path);

    // --- Contact Voice TTS IPC (Tugas C) ---
    /**
     * Kirim request TTS dari sisi hook ke sisi app.
     * contactId  = JID kontak WhatsApp (62xxx@s.whatsapp.net)
     * messageId  = message key
     * text       = isi pesan yang mau di-synthesize
     *
     * Implementasi: tulis request JSON ke file, enqueue WorkManager.
     * Callback return value:
     *   "" (empty) = berhasil enqueue
     *   non-empty  = error message
     */
    String requestTTS(String contactId, String messageId, String text);

    /**
     * Cek auto_tts_enabled untuk satu kontak. 1 = true, 0 = false.
     */
    int isAutoTtsEnabled(String contactId);

    /**
     * Set auto_tts_enabled. enabled: 1 = true, 0 = false.
     * Return value: 0 = OK, -1 = error.
     */
    int setAutoTtsEnabled(String contactId, int enabled);

    /**
     * Status profil suara kontak.
     * 0 = NO_PROFILE, 1 = HAS_PROFILE, -1 = error.
     */
    int getContactVoiceProfileStatus(String contactId);

    /**
     * Simpan metadata voice note yang masuk (dari sisi hook).
     * contactId, messageHash, audioPath (path di storage sisi app),
     * durationMs, timestamp.
     * Return: "" = ok, non-empty = error.
     */
    String registerIncomingVoiceNote(
        String contactId,
        String messageHash,
        String audioPath,
        long durationMs,
        long timestamp
    );
}
