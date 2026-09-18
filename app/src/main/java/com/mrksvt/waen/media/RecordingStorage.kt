package com.mrksvt.waen.media

import java.io.File

/**
 * Sumber tunggal layout penyimpanan rekaman panggilan.
 *
 * Rekaman dipisah per jenis panggilan supaya video dan suara tidak bercampur:
 * `/sdcard/WhatsVault/recordings/video_call` dan `.../voice_call`.
 *
 * Semua fungsi di sini murni (menerima `File`/`String`, mengembalikan `File`)
 * supaya bisa diuji di JVM tanpa Android runtime. Pemanggil yang menyediakan
 * path platform.
 */
object RecordingStorage {

    const val VIDEO_DIR_NAME = "video_call"
    const val VOICE_DIR_NAME = "voice_call"

    const val ROOT_DIR_NAME = "WhatsVault"
    const val RECORDINGS_DIR_NAME = "recordings"

    /** Nama folder lama yang masih dipakai versi sebelum pemisahan per jenis. */
    const val LEGACY_DIR_NAME = "WA Call Recordings"

    const val DEFAULT_RECORDINGS_ROOT = "/sdcard/WhatsVault/recordings"

    /** Folder penyimpanan untuk satu rekaman, berdasarkan jenis panggilan. */
    @JvmStatic
    fun callKindFolder(root: File, isVideoCall: Boolean): File =
        File(root, if (isVideoCall) VIDEO_DIR_NAME else VOICE_DIR_NAME)

    /** Root default rekaman. Dipakai saat preferensi user belum diisi. */
    @JvmStatic
    fun defaultRecordingsRoot(): String = DEFAULT_RECORDINGS_ROOT

    /**
     * Folder lama yang tetap dipindai supaya rekaman dari versi sebelumnya
     * tidak hilang dari daftar.
     *
     * Urutan deterministik dan hasilnya bebas duplikat. Folder yang tidak
     * relevan (`configuredPath` null) cukup dilewati.
     */
    @JvmStatic
    fun legacyBaseDirs(
        configuredPath: String?,
        downloadsDir: File,
        externalRoot: File,
        whatsappDataDirs: List<File>
    ): List<File> {
        val candidates = ArrayList<File>(6)
        candidates.add(File(downloadsDir, LEGACY_DIR_NAME))

        if (!configuredPath.isNullOrBlank()) {
            candidates.add(File(configuredPath, LEGACY_DIR_NAME))
        }

        candidates.add(File(externalRoot, LEGACY_DIR_NAME))
        candidates.addAll(whatsappDataDirs)

        val seen = LinkedHashSet<String>()
        val result = ArrayList<File>(candidates.size)
        for (candidate in candidates) {
            if (seen.add(candidate.absolutePath)) {
                result.add(candidate)
            }
        }
        return result
    }
}
