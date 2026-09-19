package com.mrksvt.waen.xposed.features.voice_tts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class SpeakerEmbeddingTest {

    private fun syntheticVoiced(seconds: Float = 1.5f, f0: Float = 150f): ShortArray {
        val sr = SpeakerEmbedding.SAMPLE_RATE
        val n = (seconds * sr).toInt()
        val rnd = Random(42)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            val syllable = if ((t * 3.5f) % 1f < 0.7f) 1f else 0.05f
            var s = sin(2.0 * Math.PI * f0 * t).toFloat()
            s += 0.5f * sin(2.0 * Math.PI * 2 * f0 * t).toFloat()
            s += 0.25f * sin(2.0 * Math.PI * 3 * f0 * t).toFloat()
            s *= syllable
            s += (rnd.nextFloat() - 0.5f) * 0.02f
            out[i] = (s.coerceIn(-1f, 1f) * 20000f).toInt().toShort()
        }
        return out
    }

    @Test
    fun computeProducesFiniteVectorAndF0CloseToInput() {
        val res = SpeakerEmbedding.compute(syntheticVoiced())
        assertNotNull(res)
        res!!
        assertEquals(SpeakerEmbedding.VECTOR_SIZE, res.vector.size)
        for (x in res.vector) assertTrue("non-finite in vector", x.isFinite())
        assertTrue("MFCC means should be non-zero", abs(res.vector[1]) > 1e-4f)
        assertTrue("f0 ${res.f0Hz} should be ~150Hz", abs(res.f0Hz - 150f) < 22.5f)
        assertTrue("voiced frames expected", res.voicedPerSec > 1f)
    }

    @Test
    fun toBytesLEMatches256ByteEmbContract() {
        val res = SpeakerEmbedding.compute(syntheticVoiced())!!
        val bytes = SpeakerEmbedding.toBytesLE(res.vector)
        assertEquals(256, bytes.size)
    }

    @Test
    fun averageStaysWithinVectorSpace() {
        val a = SpeakerEmbedding.compute(syntheticVoiced(f0 = 120f))!!.vector
        val b = SpeakerEmbedding.compute(syntheticVoiced(f0 = 190f))!!.vector
        val avg = SpeakerEmbedding.average(listOf(a, b))
        assertEquals(SpeakerEmbedding.VECTOR_SIZE, avg.size)
        for (x in avg) assertTrue(x.isFinite())
    }
}
