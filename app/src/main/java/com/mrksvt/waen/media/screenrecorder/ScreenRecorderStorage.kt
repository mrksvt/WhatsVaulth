package com.mrksvt.waen.media.screenrecorder

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout penyimpanan rekaman layar SR.
 *
 * Semua fungsi menerima `File` dasar, bukan `Context`, supaya murni dan bisa
 * diuji di JVM. Pemanggil (service) menyediakan folder app-specific.
 */
object ScreenRecorderStorage {

    const val DIR_NAME = "screen_recordings"
    const val STAGING_SUFFIX = ".partial"
    const val EXTENSION = ".mp4"
    const val LAST_RESULT_POINTER = ".last_result"

    /** Folder hasil di bawah base app-specific. */
    @JvmStatic
    fun outputDir(baseDir: File): File = File(baseDir, DIR_NAME)

    /** Nama sesi unik; `uniqueSuffix` mencegah dua sesi pada detik sama bentrok. */
    @JvmStatic
    fun buildFileName(timestamp: String, uniqueSuffix: String): String =
        "sr_${timestamp}_$uniqueSuffix$EXTENSION"

    /** File staging; publish memindahkannya ke nama final. */
    @JvmStatic
    fun stagingFile(dir: File, fileName: String): File =
        File(dir, "$fileName$STAGING_SUFFIX")

    @JvmStatic
    fun finalFile(dir: File, fileName: String): File = File(dir, fileName)

    @JvmStatic
    fun timestamp(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))

    /**
     * Pindahkan staging ke nama final. Mengembalikan `false` tanpa menghapus
     * apa pun bila staging tidak ada. Final lama yang bernama sama dihapus
     * lebih dulu supaya rename tidak gagal di sebagian filesystem.
     */
    @JvmStatic
    fun publish(staging: File, finalFile: File): Boolean {
        if (!staging.exists() || staging.length() <= 0L) return false
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists() && !finalFile.delete()) return false
        return staging.renameTo(finalFile)
    }

    /**
     * Catat hasil terakhir yang valid. Hanya dipanggil setelah publish sukses,
     * sehingga kegagalan publish tidak menimpa pointer hasil lama.
     */
    @JvmStatic
    fun writeLastResult(dir: File, finalFile: File): Boolean = runCatching {
        dir.mkdirs()
        File(dir, LAST_RESULT_POINTER).writeText(finalFile.name)
        true
    }.getOrDefault(false)

    /** Hasil terakhir yang pointer-nya masih menunjuk file yang ada. */
    @JvmStatic
    fun readLastResult(dir: File): File? {
        val pointer = File(dir, LAST_RESULT_POINTER)
        if (!pointer.exists()) return null
        val name = runCatching { pointer.readText().trim() }.getOrNull()
        if (name.isNullOrEmpty()) return null
        val file = File(dir, name)
        return if (file.exists()) file else null
    }

    /** Semua staging `.partial` yang tersisa, untuk ditampilkan sebagai terputus. */
    @JvmStatic
    fun listPartials(dir: File): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(STAGING_SUFFIX) }.sortedBy { it.name }
    }
}
