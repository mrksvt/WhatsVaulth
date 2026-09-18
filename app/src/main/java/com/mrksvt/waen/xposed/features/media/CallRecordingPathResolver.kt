package com.mrksvt.waen.xposed.features.media

import com.mrksvt.waen.media.RecordingStorage
import java.io.File

/**
 * Menentukan folder dan nama file rekaman panggilan.
 *
 * Semua fungsi murni (`String`/`Boolean` masuk, `File`/`String` keluar) supaya
 * bisa diuji di JVM. Pemanggil menyediakan nilai preferensi apa adanya.
 */
object CallRecordingPathResolver {

    const val EXTENSION_AUDIO = "m4a"
    const val EXTENSION_VIDEO = "mp4"

    const val VIDEO_SUFFIX = "-video"

    enum class CallKind {
        VOICE,
        VIDEO
    }

    fun callKindOf(isVideoCall: Boolean): CallKind =
        if (isVideoCall) CallKind.VIDEO else CallKind.VOICE

    /**
     * Folder tujuan rekaman.
     *
     * `rootPath` kosong/blank memakai [RecordingStorage.DEFAULT_RECORDINGS_ROOT]
     * supaya preferensi usang tidak mengarahkan rekaman ke lokasi tak terduga.
     *
     * `isBusiness` sengaja TIDAK mengubah struktur folder: WhatsApp dan
     * WhatsApp Business berbagi root yang sama. Parameter dipertahankan supaya
     * pemanggil bisa menyertakan konteksnya tanpa memecah signature nanti.
     */
    fun resolveAppDir(rootPath: String?, isVideoCall: Boolean, isBusiness: Boolean): File {
        val root = rootPath
            ?.takeIf { it.isNotBlank() }
            ?: RecordingStorage.DEFAULT_RECORDINGS_ROOT

        return RecordingStorage.callKindFolder(File(root), isVideoCall)
    }

    fun buildFileNameFor(
        identifier: String?,
        timestamp: String,
        isVideoCall: Boolean
    ): String {
        val safeIdentifier = sanitize(identifier)
        val suffix = if (isVideoCall) VIDEO_SUFFIX else ""
        val extension = if (isVideoCall) EXTENSION_VIDEO else EXTENSION_AUDIO
        return "Call_${safeIdentifier}_$timestamp$suffix.$extension"
    }

    fun sanitize(value: String?): String {
        val cleaned = value
            ?.replace(Regex("[\\\\/:*?\"<>|\r\n]+"), "_")
            ?.trim()
            .orEmpty()

        return cleaned.ifEmpty { "Unknown" }
    }
}
