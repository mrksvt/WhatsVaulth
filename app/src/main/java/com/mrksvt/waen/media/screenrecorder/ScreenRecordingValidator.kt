package com.mrksvt.waen.media.screenrecorder

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Validasi hasil rekaman layar.
 *
 * `validate` murni (menerima [VideoMetadata]) supaya keputusan bisa diuji di
 * JVM tanpa Android runtime. [readMetadata] yang menyentuh `MediaExtractor`
 * hanya dipanggil service/engine setelah finalize.
 */
object ScreenRecordingValidator {

    const val AVC_MIME = "video/avc"

    /** Batas atas kewajaran durasi (12 jam); di luar itu dianggap metadata rusak. */
    const val MAX_DURATION_US = 12L * 60L * 60L * 1_000_000L

    data class VideoMetadata(
        val mimeType: String?,
        val width: Int,
        val height: Int,
        val durationUs: Long,
        val sampleCount: Long,
        val sizeBytes: Long
    )

    /**
     * `null` berarti valid. Selain itu berisi alasan kegagalan.
     * Menolak: file kosong, tanpa track video, bukan AVC, tanpa sampel,
     * dimensi/durasi tidak positif atau di luar batas wajar.
     */
    @JvmStatic
    fun validate(meta: VideoMetadata?): ScreenRecorderResult.Error? {
        if (meta == null) return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        if (meta.sizeBytes <= 0L) return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        if (meta.mimeType != AVC_MIME) return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        if (meta.width <= 0 || meta.height <= 0) return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        if (meta.sampleCount <= 0L) return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        if (meta.durationUs <= 0L || meta.durationUs > MAX_DURATION_US) {
            return ScreenRecorderResult.Error.OUTPUT_VALIDATION
        }
        return null
    }

    /**
     * Baca metadata track video pertama. `null` bila tidak ada track video
     * atau file tidak bisa dibuka. Menghitung sampel dengan iterasi extractor,
     * jadi biayanya proporsional jumlah frame.
     */
    @JvmStatic
    fun readMetadata(file: File): VideoMetadata? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var videoTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    format = candidate
                    break
                }
            }
            val trackFormat = format ?: return null
            if (videoTrack < 0) return null

            extractor.selectTrack(videoTrack)
            var samples = 0L
            while (extractor.sampleTime >= 0L) {
                samples++
                if (!extractor.advance()) break
            }

            VideoMetadata(
                mimeType = trackFormat.getString(MediaFormat.KEY_MIME),
                width = trackFormat.intOrZero(MediaFormat.KEY_WIDTH),
                height = trackFormat.intOrZero(MediaFormat.KEY_HEIGHT),
                durationUs = trackFormat.longOrZero(MediaFormat.KEY_DURATION),
                sampleCount = samples,
                sizeBytes = file.length()
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0

    private fun MediaFormat.longOrZero(key: String): Long =
        if (containsKey(key)) getLong(key) else 0L
}
