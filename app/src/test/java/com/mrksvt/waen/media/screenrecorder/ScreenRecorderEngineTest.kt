package com.mrksvt.waen.media.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

private fun buffer(size: Int = 8): ByteBuffer = ByteBuffer.allocate(size)

private fun frame(ptsUs: Long, flags: Int = 0) = EncodedSample(
    buffer = buffer(),
    offset = 0,
    size = 8,
    presentationTimeUs = ptsUs,
    flags = flags
)

private class FakeDrain(
    private val script: MutableList<Dequeue> = mutableListOf()
) : DrainBackend {
    var eosSignalled = false
    val releasedIndices = mutableListOf<Int>()
    var formatProvided = false

    fun enqueue(vararg items: Dequeue) {
        script.addAll(items)
    }

    override fun dequeueOutput(timeoutUs: Long): Dequeue {
        if (script.isEmpty()) return Dequeue.TryAgain
        return script.removeAt(0)
    }

    override fun releaseOutput(index: Int) {
        releasedIndices.add(index)
    }

    override fun signalEndOfInputStream() {
        eosSignalled = true
    }

    override fun outputFormat(): TrackFormat {
        formatProvided = true
        return TrackFormat(null)
    }
}

private class FakeMuxer(
    private val failWrite: Boolean = false,
    private val failStop: Boolean = false
) : MuxerBackend {
    var started = false
    var stopped = false
    var released = false
    var trackAdded = false
    val written = mutableListOf<EncodedSample>()

    override fun addTrack(format: TrackFormat): Int {
        trackAdded = true
        return 0
    }

    override fun start() {
        started = true
    }

    override fun writeSample(track: Int, sample: EncodedSample) {
        if (failWrite) throw IllegalStateException("write failed")
        written.add(sample)
    }

    override fun stop() {
        if (failStop) throw IllegalStateException("stop failed")
        stopped = true
    }

    override fun release() {
        released = true
    }
}

class ScreenRecorderEngineTest {

    private val eos = frame(0L, EncodedSample.FLAG_END_OF_STREAM)

    @Test
    fun `saves when frames then eos`() {
        val drain = FakeDrain()
        drain.enqueue(
            Dequeue.FormatChanged,
            Dequeue.Buffer(0, frame(0)),
            Dequeue.Buffer(1, frame(33_000)),
            Dequeue.Buffer(2, eos)
        )
        val muxer = FakeMuxer()
        val engine = ScreenRecorderEngine(drain, muxer, clock = { 0L })

        val outcome = engine.stopAndFinalize(deadlineUs = 1_000_000L)

        assertTrue(outcome is ScreenRecorderEngine.Outcome.Saved)
        val saved = outcome as ScreenRecorderEngine.Outcome.Saved
        assertEquals(2L, saved.sampleCount)
        assertEquals(33_000L, saved.durationUs)
        assertTrue(muxer.stopped)
    }

    @Test
    fun `skips codec config samples`() {
        val drain = FakeDrain()
        drain.enqueue(
            Dequeue.FormatChanged,
            Dequeue.Buffer(0, frame(0, EncodedSample.FLAG_CODEC_CONFIG)),
            Dequeue.Buffer(1, frame(33_000)),
            Dequeue.Buffer(2, eos)
        )
        val muxer = FakeMuxer()
        val engine = ScreenRecorderEngine(drain, muxer, clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(1L, (outcome as ScreenRecorderEngine.Outcome.Saved).sampleCount)
        assertEquals(1, muxer.written.size)
    }

    @Test
    fun `releases every output buffer even on success`() {
        val drain = FakeDrain()
        drain.enqueue(
            Dequeue.FormatChanged,
            Dequeue.Buffer(7, frame(0)),
            Dequeue.Buffer(8, eos)
        )
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { 0L })

        engine.stopAndFinalize(1_000_000L)

        assertTrue(drain.releasedIndices.containsAll(listOf(7, 8)))
    }

    @Test
    fun `times out when no output before deadline`() {
        val drain = FakeDrain()
        val now = longArrayOf(0L)
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { now[0] })

        val outcome = engine.stopAndFinalize(deadlineUs = 5_000L)

        assertEquals(
            ScreenRecorderResult.Error.CODEC_DRAIN_TIMEOUT,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `times out after idle spins without deadline`() {
        val drain = FakeDrain()
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { 0L })

        val outcome = engine.stopAndFinalize(deadlineUs = Long.MAX_VALUE)

        assertEquals(
            ScreenRecorderResult.Error.CODEC_DRAIN_TIMEOUT,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `write failure yields failed and not saved`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.FormatChanged, Dequeue.Buffer(0, frame(0)), Dequeue.Buffer(1, eos))
        val engine = ScreenRecorderEngine(drain, FakeMuxer(failWrite = true), clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(
            ScreenRecorderResult.Error.CODEC_WRITE,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `muxer stop failure yields failed`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.FormatChanged, Dequeue.Buffer(0, frame(0)), Dequeue.Buffer(1, eos))
        val engine = ScreenRecorderEngine(drain, FakeMuxer(failStop = true), clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(
            ScreenRecorderResult.Error.MUXER_FINALIZE,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `zero frame recording is not saved`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.FormatChanged, Dequeue.Buffer(0, eos))
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `frame before format is a failure`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.Buffer(0, frame(0)), Dequeue.Buffer(1, eos))
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { 0L })

        val outcome = engine.stopAndFinalize(1_000_000L)

        assertEquals(
            ScreenRecorderResult.Error.MUXER_FINALIZE,
            (outcome as ScreenRecorderEngine.Outcome.Failed).error
        )
    }

    @Test
    fun `stop is idempotent and second call returns first outcome`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.FormatChanged, Dequeue.Buffer(0, frame(0)), Dequeue.Buffer(1, eos))
        val muxer = FakeMuxer()
        val engine = ScreenRecorderEngine(drain, muxer, clock = { 0L })

        val first = engine.stopAndFinalize(1_000_000L)
        val second = engine.stopAndFinalize(1_000_000L)

        assertEquals(first, second)
        assertTrue(engine.isStopped)
        assertEquals(1, muxer.written.size)
    }

    @Test
    fun `signals eos exactly once`() {
        val drain = FakeDrain()
        drain.enqueue(Dequeue.FormatChanged, Dequeue.Buffer(0, frame(0)), Dequeue.Buffer(1, eos))
        val engine = ScreenRecorderEngine(drain, FakeMuxer(), clock = { 0L })

        engine.stopAndFinalize(1_000_000L)
        engine.stopAndFinalize(1_000_000L)

        assertTrue(drain.eosSignalled)
    }

    @Test
    fun `releaseMuxer releases and is safe to repeat`() {
        val drain = FakeDrain()
        val muxer = FakeMuxer()
        val engine = ScreenRecorderEngine(drain, muxer, clock = { 0L })

        engine.releaseMuxer()
        engine.releaseMuxer()

        assertTrue(muxer.released)
        assertFalse(drain.eosSignalled)
    }
}