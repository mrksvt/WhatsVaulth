package com.mrksvt.waen.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.mrksvt.waen.BuildConfig
import java.io.File
import java.nio.ByteBuffer

/**
 * Menggabungkan video-only `.mp4` (dari `ScreenCapturePipeline`) dengan audio
 * `.m4a` (dari `CallRecording`) menjadi satu `.mp4` ber-audio.
 *
 * Audio dan video direkam oleh dua komponen berbeda sehingga timestamp-nya
 * tidak saling tahu. Karena itu setiap track disalin dengan timestamp absolut
 * dari extractor-nya sendiri, dan `start()` tidak dipanggil dua kali (muxer
 * hanya boleh di-start sekali setelah semua track ditambahkan).
 *
 * Kalau mux gagal, pemanggil WAJIB mempertahankan kedua file mentah
 * (`FINDINGS.md` B-4 keputusan opsi 1). File mentah tidak pernah dihapus di
 * sini.
 */
object AudioVideoMuxer {

    const val OUTCOME_KEEP_BOTH = "keep_both"
    const val OUTCOME_KEEP_PRIMARY_ONLY = "keep_primary_only"

    private const val TAG = "AudioVideoMuxer"
    private const val MAX_SAMPLE_SIZE = 1_000_000

    private fun logD(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private fun logW(msg: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg)
    }

    /**
     * Durasi akhir = durasi terpanjang. Track video yang lebih pendek dari
     * audio akan berhenti lebih awal (pemutar menahan frame terakhir), dan
     * sebaliknya. Memotong ke durasi terpendek akan membuang data.
     */
    fun alignDuration(videoDurationUs: Long, audioDurationUs: Long): Long =
        maxOf(videoDurationUs, audioDurationUs, 0L)

    /**
     * Kebijakan hasil mux dari `FINDINGS.md` B-4 opsi 1: apa pun hasilnya,
     * file mentah tidak pernah dihapus karena itu bukti dan jaring pengaman.
     */
    fun onMuxResult(success: Boolean): String =
        if (success) OUTCOME_KEEP_PRIMARY_ONLY else OUTCOME_KEEP_BOTH

    /**
     * Mux [videoFile] + [audioFile] menjadi [outputFile].
     *
     * Mengembalikan true hanya kalau file hasil ada dan tidak kosong. Pada
     * kegagalan, file output parsial dihapus tetapi kedua sumber dibiarkan.
     */
    fun mux(videoFile: File, audioFile: File, outputFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            logW("video sumber tidak ada atau kosong: ${videoFile.absolutePath}")
            return false
        }
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            logW("audio sumber tidak ada atau kosong: ${audioFile.absolutePath}")
            return false
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrack = selectTrack(videoExtractor, "video/")
            val audioTrack = selectTrack(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) {
                logW("track tidak lengkap: video=$videoTrack audio=$audioTrack")
                return false
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)

            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)

            applyRotation(muxer, videoFormat)
            muxer.start()

            copyTrack(videoExtractor, muxer, outVideoTrack, "video")
            copyTrack(audioExtractor, muxer, outAudioTrack, "audio")

            muxer.stop()
            muxer.release()
            muxer = null

            val ok = outputFile.exists() && outputFile.length() > 0L
            if (ok) {
                val videoUs = durationOf(videoFormat)
                val audioUs = durationOf(audioFormat)
                logD("mux selesai, target durasi ${alignDuration(videoUs, audioUs)}us")
            } else {
                logW("file hasil mux kosong/tiada")
                outputFile.delete()
            }
            ok
        } catch (t: Throwable) {
            logW("mux gagal: ${t.message}")
            try {
                muxer?.release()
            } catch (_: Throwable) {
            }
            if (outputFile.exists() && !outputFile.delete()) {
                logW("tidak bisa menghapus file output parsial")
            }
            false
        } finally {
            try {
                videoExtractor?.release()
            } catch (_: Throwable) {
            }
            try {
                audioExtractor?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun durationOf(format: MediaFormat): Long =
        if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun applyRotation(muxer: MediaMuxer, videoFormat: MediaFormat) {
        val rotation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            videoFormat.getInteger(MediaFormat.KEY_ROTATION)
        } else {
            0
        }
        if (rotation == 0) return
        try {
            muxer.setOrientationHint(rotation)
        } catch (t: Throwable) {
            logW("setOrientationHint($rotation) gagal: ${t.message}")
        }
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outTrackIndex: Int,
        label: String
    ) {
        val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_SIZE)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags

            try {
                muxer.writeSampleData(outTrackIndex, buffer, info)
            } catch (t: Throwable) {
                logW("writeSampleData $label gagal: ${t.message}")
                break
            }

            if (!extractor.advance()) break
        }
        logD("track $label selesai disalin")
    }
}
