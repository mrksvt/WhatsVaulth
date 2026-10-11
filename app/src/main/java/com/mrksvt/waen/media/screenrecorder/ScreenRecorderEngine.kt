package com.mrksvt.waen.media.screenrecorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sampel terenkode. Memakai `java.nio.ByteBuffer` (bukan `MediaCodec.BufferInfo`)
 * supaya jalur drain bisa diuji di JVM dengan fake backend.
 */
data class EncodedSample(
    val buffer: ByteBuffer,
    val offset: Int,
    val size: Int,
    val presentationTimeUs: Long,
    val flags: Int
) {
    val isCodecConfig: Boolean get() = flags and FLAG_CODEC_CONFIG != 0
    val isEndOfStream: Boolean get() = flags and FLAG_END_OF_STREAM != 0

    companion object {
        const val FLAG_CODEC_CONFIG = 2
        const val FLAG_END_OF_STREAM = 4
    }
}

/** Handle opaque format track; implementasi Android menyimpan `MediaFormat`. */
class TrackFormat(val platform: Any?)

sealed class Dequeue {
    object TryAgain : Dequeue()
    object FormatChanged : Dequeue()
    data class Buffer(val index: Int, val sample: EncodedSample) : Dequeue()
}

interface DrainBackend {
    fun dequeueOutput(timeoutUs: Long): Dequeue
    fun releaseOutput(index: Int)
    fun signalEndOfInputStream()
    fun outputFormat(): TrackFormat
}

interface MuxerBackend {
    fun addTrack(format: TrackFormat): Int
    fun start()
    fun writeSample(track: Int, sample: EncodedSample)
    fun stop()
    fun release()
}

/**
 * Drain + finalize satu sesi. Seluruh logika keputusan (batas deadline, hitung
 * sampel, propagasi error, idempotensi) ada di kelas ini sehingga bisa diuji
 * dengan fake [DrainBackend]/[MuxerBackend] tanpa MediaCodec.
 *
 * Cleanup codec TIDAK dilakukan di sini; itu milik [ScreenRecorderEncoder]
 * setelah drain berhenti, supaya release tidak balapan dengan operasi codec.
 */
class ScreenRecorderEngine(
    private val drain: DrainBackend,
    private val muxer: MuxerBackend,
    private val clock: () -> Long
) {

    sealed class Outcome {
        data class Saved(val sampleCount: Long, val durationUs: Long) : Outcome()
        data class Failed(val error: ScreenRecorderResult.Error) : Outcome()
    }

    private val stopped = AtomicBoolean(false)
    private var lastOutcome: Outcome? = null
    private var trackIndex = -1
    private var muxerStarted = false

    val isStopped: Boolean get() = stopped.get()

    /**
     * Signal EOS lalu drain sampai EOS atau [deadlineUs]. Idempotent: panggilan
     * kedua mengembalikan hasil panggilan pertama tanpa menyentuh backend lagi.
     */
    fun stopAndFinalize(deadlineUs: Long): Outcome {
        if (!stopped.compareAndSet(false, true)) {
            return lastOutcome ?: Outcome.Failed(ScreenRecorderResult.Error.SESSION_INTERRUPTED)
        }
        val outcome = try {
            drain.signalEndOfInputStream()
            drainLoop(deadlineUs)
        } catch (e: Exception) {
            Outcome.Failed(ScreenRecorderResult.Error.CODEC_DRAIN)
        }
        lastOutcome = outcome
        return outcome
    }

    private fun drainLoop(deadlineUs: Long): Outcome {
        var sampleCount = 0L
        var durationUs = 0L
        var sawEos = false
        var idleSpins = 0

        while (!sawEos) {
            if (clock() >= deadlineUs) {
                return Outcome.Failed(ScreenRecorderResult.Error.CODEC_DRAIN_TIMEOUT)
            }
            when (val next = drain.dequeueOutput(DEQUEUE_TIMEOUT_US)) {
                is Dequeue.TryAgain -> {
                    idleSpins++
                    if (idleSpins >= MAX_IDLE_SPINS) {
                        return Outcome.Failed(ScreenRecorderResult.Error.CODEC_DRAIN_TIMEOUT)
                    }
                }
                is Dequeue.FormatChanged -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(drain.outputFormat())
                        muxer.start()
                        muxerStarted = true
                    }
                }
                is Dequeue.Buffer -> {
                    idleSpins = 0
                    try {
                        val sample = next.sample
                        if (sample.isEndOfStream) {
                            sawEos = true
                        } else if (!sample.isCodecConfig) {
                            if (!muxerStarted) {
                                return Outcome.Failed(ScreenRecorderResult.Error.MUXER_FINALIZE)
                            }
                            try {
                                muxer.writeSample(trackIndex, sample)
                            } catch (e: Exception) {
                                return Outcome.Failed(ScreenRecorderResult.Error.CODEC_WRITE)
                            }
                            sampleCount++
                            if (sample.presentationTimeUs > durationUs) {
                                durationUs = sample.presentationTimeUs
                            }
                        }
                    } finally {
                        drain.releaseOutput(next.index)
                    }
                }
            }
        }

        if (!muxerStarted) return Outcome.Failed(ScreenRecorderResult.Error.MUXER_FINALIZE)
        if (sampleCount <= 0L) return Outcome.Failed(ScreenRecorderResult.Error.OUTPUT_VALIDATION)
        return try {
            muxer.stop()
            Outcome.Saved(sampleCount, durationUs)
        } catch (e: Exception) {
            Outcome.Failed(ScreenRecorderResult.Error.MUXER_FINALIZE)
        }
    }

    /** Lepas muxer. Aman dipanggil sekali; tidak menyentuh codec. */
    fun releaseMuxer() {
        runCatching { muxer.release() }
    }

    companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L

        /** 50 x 10 ms = 500 ms tanpa output sebelum dianggap macet. */
        const val MAX_IDLE_SPINS = 50
    }
}

/** Adapter drain untuk `MediaCodec` nyata. */
class MediaCodecDrainBackend(private val codec: MediaCodec) : DrainBackend {

    private val bufferInfo = MediaCodec.BufferInfo()

    override fun dequeueOutput(timeoutUs: Long): Dequeue {
        val index = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        return when {
            index == MediaCodec.INFO_TRY_AGAIN_LATER -> Dequeue.TryAgain
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Dequeue.FormatChanged
            index >= 0 -> {
                val buffer = codec.getOutputBuffer(index)
                if (buffer == null) {
                    Dequeue.TryAgain
                } else {
                    Dequeue.Buffer(
                        index,
                        EncodedSample(
                            buffer = buffer,
                            offset = bufferInfo.offset,
                            size = bufferInfo.size,
                            presentationTimeUs = bufferInfo.presentationTimeUs,
                            flags = bufferInfo.flags
                        )
                    )
                }
            }
            else -> Dequeue.TryAgain
        }
    }

    override fun releaseOutput(index: Int) {
        codec.releaseOutputBuffer(index, false)
    }

    override fun signalEndOfInputStream() {
        codec.signalEndOfInputStream()
    }

    override fun outputFormat(): TrackFormat = TrackFormat(codec.outputFormat)
}

/** Adapter muxer untuk `MediaMuxer` nyata. */
class MediaMuxerBackend(private val muxer: MediaMuxer) : MuxerBackend {

    private val bufferInfo = MediaCodec.BufferInfo()

    override fun addTrack(format: TrackFormat): Int =
        muxer.addTrack(format.platform as MediaFormat)

    override fun start() {
        muxer.start()
    }

    override fun writeSample(track: Int, sample: EncodedSample) {
        sample.buffer.position(sample.offset)
        sample.buffer.limit(sample.offset + sample.size)
        bufferInfo.set(sample.offset, sample.size, sample.presentationTimeUs, sample.flags)
        muxer.writeSampleData(track, sample.buffer, bufferInfo)
    }

    override fun stop() {
        muxer.stop()
    }

    override fun release() {
        muxer.release()
    }
}
