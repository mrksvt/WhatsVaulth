package com.mrksvt.waen.media.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

private fun sample(ptsUs: Long, flags: Int = 0) = EncodedSample(
    buffer = ByteBuffer.allocate(8), offset = 0, size = 8, presentationTimeUs = ptsUs, flags = flags
)

private class EosDrain(private val frames: Int, private val eosLate: Boolean) : DrainBackend {
    private var emitted = 0
    private var formatSent = false
    override fun dequeueOutput(timeoutUs: Long): Dequeue {
        if (!formatSent) {
            formatSent = true
            return Dequeue.FormatChanged
        }
        if (emitted < frames) {
            emitted++
            return Dequeue.Buffer(emitted, sample(emitted * 33_000L))
        }
        if (eosLate) return Dequeue.TryAgain
        return Dequeue.Buffer(999, sample(0L, EncodedSample.FLAG_END_OF_STREAM))
    }
    override fun releaseOutput(index: Int) {}
    override fun signalEndOfInputStream() {}
    override fun outputFormat(): TrackFormat = TrackFormat(null)
}

private class TrackingMuxer(private val failStop: Boolean = false) : MuxerBackend {
    var written = 0
    var stopped = false
    override fun addTrack(format: TrackFormat): Int = 0
    override fun start() {}
    override fun writeSample(track: Int, sample: EncodedSample) {
        written++
    }
    override fun stop() {
        if (failStop) throw IllegalStateException("stop")
        stopped = true
    }
    override fun release() {}
}

class ScreenRecorderFinalizeTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun `save requires eos before deadline`() {
        val muxer = TrackingMuxer()
        val engine = ScreenRecorderEngine(EosDrain(frames = 3, eosLate = false), muxer, clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        val saved = outcome as ScreenRecorderEngine.Outcome.Saved
        assertEquals(3L, saved.sampleCount)
        assertEquals(3, muxer.written)
        assertTrue(muxer.stopped)
    }

    @Test
    fun `late eos times out and stays unsaved`() {
        val engine = ScreenRecorderEngine(
            EosDrain(frames = 3, eosLate = true),
            TrackingMuxer(),
            clock = { 0L }
        )

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(
            ScreenRecorderResult.Error.CODEC_DRAIN_TIMEOUT,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `failed muxer stop never reports saved`() {
        val engine = ScreenRecorderEngine(
            EosDrain(frames = 2, eosLate = false),
            TrackingMuxer(failStop = true),
            clock = { 0L }
        )

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertTrue(outcome is ScreenRecorderEngine.Outcome.Failed)
    }

    @Test
    fun `failed publish keeps previous valid result and reports failure`() {
        val dir = ScreenRecorderStorage.outputDir(temp.root)
        dir.mkdirs()
        val previous = ScreenRecorderStorage.finalFile(dir, "old.mp4")
        previous.writeBytes(ByteArray(16) { 1 })
        ScreenRecorderStorage.writeLastResult(dir, previous)

        val emptyStaging = ScreenRecorderStorage.stagingFile(dir, "new.mp4")
        emptyStaging.createNewFile()
        val target = ScreenRecorderStorage.finalFile(dir, "new.mp4")

        assertFalse(ScreenRecorderStorage.publish(emptyStaging, target))
        assertEquals(previous, ScreenRecorderStorage.readLastResult(dir))
        assertTrue(previous.exists())
        assertFalse(target.exists())
    }

    @Test
    fun `validated file publishes and becomes last result`() {
        val dir = ScreenRecorderStorage.outputDir(temp.root)
        dir.mkdirs()
        val staging = ScreenRecorderStorage.stagingFile(dir, "a.mp4")
        staging.writeBytes(ByteArray(64) { 2 })
        val target = ScreenRecorderStorage.finalFile(dir, "a.mp4")

        assertTrue(ScreenRecorderStorage.publish(staging, target))
        assertTrue(ScreenRecorderStorage.writeLastResult(dir, target))
        assertEquals(target, ScreenRecorderStorage.readLastResult(dir))
        assertNull(ScreenRecorderStorage.readLastResult(File(temp.root, "missing")))
    }
}
