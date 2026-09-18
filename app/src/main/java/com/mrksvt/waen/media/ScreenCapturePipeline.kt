package com.mrksvt.waen.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import android.view.Surface
import com.mrksvt.waen.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merekam layar ke file `.mp4` video-only (tanpa audio).
 *
 * Alur: `MediaProjection.createVirtualDisplay()` dengan surface dari
 * `MediaCodec.createInputSurface()`, lalu drain loop encoder di thread sendiri
 * yang menulis sampel ke `MediaMuxer`.
 *
 * Audio TIDAK ditangani di sini. Audio tetap dari jalur hook `CallRecording`,
 * dan penggabungan dilakukan `AudioVideoMuxer` (WP-08).
 *
 * Satu instance untuk satu sesi capture. `stop()` idempotent.
 */
class ScreenCapturePipeline(private val outputFile: File) {

    companion object {
        private const val TAG = "ScreenCapturePipeline"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_TIMEOUT_US = 500_000L
        private const val I_FRAME_INTERVAL_SECONDS = 2
        private const val BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR

        /**
         * Batas putaran `INFO_TRY_AGAIN_LATER` setelah `stop()` dipanggil
         * sebelum drain loop menyerah. Tanpa ini, encoder yang tidak pernah
         * mengeluarkan EOS akan membuat thread berputar tanpa henti.
         */
        private const val MAX_IDLE_SPINS_AFTER_STOP = 50

        private fun logD(msg: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, msg)
        }

        private fun logW(msg: String) {
            if (BuildConfig.DEBUG) Log.w(TAG, msg)
        }
    }

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var drainThread: Thread? = null

    private var videoTrackIndex = -1
    private var muxerStarted = false

    private val stopped = AtomicBoolean(false)
    private val draining = AtomicBoolean(false)

    @Volatile
    private var drainingEndOfStream = false

    var profile: VideoEncoderProfile.Profile? = null
        private set

    val isRunning: Boolean
        get() = codec != null && !stopped.get()

    /**
     * Siapkan encoder + muxer + virtual display untuk [frame].
     *
     * Mencoba setiap profile di [VideoEncoderProfile.attemptChain] sampai ada
     * yang berhasil di-`configure`; encoder H.264 menolak sebagian resolusi di
     * device tertentu. Mengembalikan false kalau semuanya gagal, dan pemanggil
     * harus tetap menyimpan audio (SC-03).
     */
    fun startCapture(
        projection: MediaProjection,
        frame: CaptureFrame,
        densityDpi: Int,
        preference: String?
    ): Boolean {
        if (isRunning) {
            logW("startCapture() dipanggil saat pipeline sudah jalan")
            return true
        }

        val snapped = CaptureFrame.snapEven(frame, frame.width, frame.height)
        val chain = VideoEncoderProfile.attemptChain(preference, snapped.width, snapped.height)
        for (candidate in chain) {
            if (tryStartWith(projection, candidate, densityDpi)) {
                profile = candidate
                return true
            }
        }

        logW("semua profile encoder gagal, capture video tidak dijalankan")
        releaseAll()
        return false
    }

    private fun tryStartWith(
        projection: MediaProjection,
        candidate: VideoEncoderProfile.Profile,
        densityDpi: Int
    ): Boolean {
        var localCodec: MediaCodec? = null
        var localMuxer: MediaMuxer? = null
        var localSurface: Surface? = null

        return try {
            val format = MediaFormat.createVideoFormat(candidate.mimeType, candidate.width, candidate.height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, candidate.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, candidate.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE)
                }
            }

            localCodec = MediaCodec.createEncoderByType(candidate.mimeType)
            localCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            localSurface = localCodec.createInputSurface()
            localMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            codec = localCodec
            inputSurface = localSurface
            muxer = localMuxer
            videoTrackIndex = -1
            muxerStarted = false
            stopped.set(false)

            localCodec.start()

            virtualDisplay = projection.createVirtualDisplay(
                "WaEnhancerScreenCapture",
                candidate.width,
                candidate.height,
                densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                localSurface,
                null,
                null
            )

            startDrainLoop()
            logD("capture jalan di ${candidate.width}x${candidate.height} @ ${candidate.bitRate}bps")
            true
        } catch (t: Throwable) {
            logW("configure gagal untuk ${candidate.resolutionLabel}: ${t.message}")
            safeStopCodec(localCodec)
            safeReleaseSurface(localSurface)
            safeReleaseMuxer(localMuxer)
            codec = null
            inputSurface = null
            muxer = null
            false
        }
    }

    private fun startDrainLoop() {
        if (!draining.compareAndSet(false, true)) return
        val thread = Thread({ drainLoop() }, "WaEnhancer-ScreenDrain").apply { isDaemon = true }
        drainThread = thread
        thread.start()
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        var idleSpinsAfterStop = 0
        while (true) {
            val localCodec = codec ?: break
            try {
                val index = localCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopped.get()) {
                            idleSpinsAfterStop++
                            if (idleSpinsAfterStop >= MAX_IDLE_SPINS_AFTER_STOP) {
                                logW("tidak ada EOS dari encoder, drain loop berhenti paksa")
                                break
                            }
                        }
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        idleSpinsAfterStop = 0
                        if (!muxerStarted) {
                            val localMuxer = muxer ?: break
                            videoTrackIndex = localMuxer.addTrack(localCodec.outputFormat)
                            localMuxer.start()
                            muxerStarted = true
                            logD("muxer dimulai, track=$videoTrackIndex")
                        }
                    }

                    index >= 0 -> {
                        idleSpinsAfterStop = 0
                        val buffer = localCodec.getOutputBuffer(index)
                        val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                        if (buffer != null && bufferInfo.size > 0 && !isConfig) {
                            val localMuxer = muxer
                            if (localMuxer != null && muxerStarted && videoTrackIndex >= 0) {
                                buffer.position(bufferInfo.offset)
                                buffer.limit(bufferInfo.offset + bufferInfo.size)
                                try {
                                    localMuxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                                } catch (t: Throwable) {
                                    logW("writeSampleData gagal: ${t.message}")
                                }
                            }
                        }

                        localCodec.releaseOutputBuffer(index, false)

                        if (isEos) {
                            drainingEndOfStream = true
                            break
                        }
                    }
                }

                if (stopped.get() && drainingEndOfStream) break
            } catch (t: Throwable) {
                logW("drain loop berhenti: ${t.message}")
                break
            }
        }
        draining.set(false)
    }

    /**
     * Hentikan capture dan tutup file. Aman dipanggil berkali-kali.
     *
     * Urutan penting: `signalEndOfInputStream()` dulu supaya encoder
     * mengeluarkan sisa frame, tunggu drain loop selesai, baru tutup muxer.
     * Tanpa itu file `.mp4` bisa tidak punya moov atom dan rusak.
     */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) {
            logD("stop() diabaikan, sudah dihentikan")
            return
        }

        val localCodec = codec
        try {
            localCodec?.signalEndOfInputStream()
        } catch (t: Throwable) {
            logW("signalEndOfInputStream gagal: ${t.message}")
        }

        waitForDrain()

        safeStopCodec(localCodec)
        safeReleaseSurface(inputSurface)
        safeReleaseVirtualDisplay()
        safeStopAndReleaseMuxer()

        codec = null
        inputSurface = null
        muxer = null
        virtualDisplay = null
        drainThread = null
        videoTrackIndex = -1
        muxerStarted = false
        drainingEndOfStream = false
        logD("capture dihentikan, file=${outputFile.absolutePath}")
    }

    private fun waitForDrain() {
        val thread = drainThread ?: return
        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_US / 1000
        while (thread.isAlive && System.currentTimeMillis() < deadline) {
            try {
                thread.join(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (thread.isAlive) logW("drain loop tidak selesai dalam batas waktu")
    }

    private fun safeStopCodec(target: MediaCodec?) {
        try {
            target?.stop()
        } catch (t: Throwable) {
            logW("codec.stop() gagal: ${t.message}")
        }
        try {
            target?.release()
        } catch (t: Throwable) {
            logW("codec.release() gagal: ${t.message}")
        }
    }

    private fun safeReleaseSurface(target: Surface?) {
        try {
            target?.release()
        } catch (t: Throwable) {
            logW("surface.release() gagal: ${t.message}")
        }
    }

    private fun safeReleaseVirtualDisplay() {
        try {
            virtualDisplay?.release()
        } catch (t: Throwable) {
            logW("virtualDisplay.release() gagal: ${t.message}")
        }
    }

    private fun safeStopAndReleaseMuxer() {
        val localMuxer = muxer
        if (localMuxer == null) return
        if (muxerStarted) {
            try {
                localMuxer.stop()
            } catch (t: Throwable) {
                logW("muxer.stop() gagal: ${t.message}")
            }
        }
        safeReleaseMuxer(localMuxer)
    }

    private fun safeReleaseMuxer(target: MediaMuxer?) {
        try {
            target?.release()
        } catch (t: Throwable) {
            logW("muxer.release() gagal: ${t.message}")
        }
    }

    private fun releaseAll() {
        safeStopCodec(codec)
        safeReleaseSurface(inputSurface)
        safeReleaseVirtualDisplay()
        safeStopAndReleaseMuxer()
        codec = null
        inputSurface = null
        muxer = null
        virtualDisplay = null
    }
}
