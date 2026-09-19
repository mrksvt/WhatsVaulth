package com.mrksvt.waen.xposed.features.voice_tts.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Speaker feature vector (not a neural embedding - a real acoustic descriptor):
 * MFCC mean+std, F0 stats, energy, voiced density. Deterministic, zero-dep,
 * safe to average across several voice notes of the same speaker.
 */
object SpeakerEmbedding {

    const val VECTOR_SIZE = 64          // 64 float32 = 256 bytes (.emb contract)
    const val SAMPLE_RATE = 48_000      // MediaCodec opus output rate

    data class Result(
        val vector: FloatArray,         // VECTOR_SIZE
        val f0Hz: Float,
        val voicedPerSec: Float,
        val durationSec: Float
    )

    private const val FRAME = 1024
    private const val HOP = 512
    private const val NUM_MEL = 26
    private const val NUM_CEP = 13

    fun compute(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): Result? {
        if (pcm.size < FRAME * 4) return null

        val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
        preEmphasis(samples)

        val frameCount = (samples.size - FRAME) / HOP + 1
        if (frameCount < 4) return null

        val melLog = Array(frameCount) { FloatArray(NUM_MEL) }
        val energy = FloatArray(frameCount)
        val window = FloatArray(FRAME) {
            0.54f - 0.46f * cos((2.0 * Math.PI * it / (FRAME - 1)).toFloat())
        }
        var sumLogEnergy = 0.0
        var sumAbs = 0.0

        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        for (f in 0 until frameCount) {
            val off = f * HOP
            for (i in 0 until FRAME) {
                re[i] = samples[off + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var e = 0f
            for (i in 0 until FRAME / 2) {
                val p = re[i] * re[i] + im[i] * im[i]
                e += p
            }
            energy[f] = max(e / FRAME, 1e-10f)
            sumLogEnergy += ln(energy[f].toDouble())
            for (i in 0 until FRAME) sumAbs += abs(samples[off + i])
            powerToMelLog(re, im, e, melLog[f], sampleRate)
        }

        val mean = FloatArray(NUM_CEP)
        val m2 = FloatArray(NUM_CEP)
        for (f in 0 until frameCount) {
            val c = melToCepstrum(melLog[f])
            for (k in 0 until NUM_CEP) {
                mean[k] += c[k]
                m2[k] += c[k] * c[k]
            }
        }
        val std = FloatArray(NUM_CEP)
        for (k in 0 until NUM_CEP) {
            mean[k] /= frameCount
            val varv = max(m2[k] / frameCount - mean[k] * mean[k], 0f)
            std[k] = sqrt(varv)
        }

        val rms = (sumAbs / (FRAME.toLong() * frameCount)).toFloat()
        val f0Lag = f0Stats(samples, sampleRate, rms)

        val durationSec = samples.size.toFloat() / sampleRate
        val voicedPerSec = f0Lag.second / durationSec

        val vector = FloatArray(VECTOR_SIZE)
        for (k in 0 until NUM_CEP) {
            vector[k] = mean[k]
            vector[NUM_CEP + k] = std[k]
        }
        vector[2 * NUM_CEP] = ln(max(energy.average().toDouble(), 1e-10)).toFloat()
        vector[2 * NUM_CEP + 1] = f0Lag.first
        vector[2 * NUM_CEP + 2] = f0Lag.third
        vector[2 * NUM_CEP + 3] = voicedPerSec
        vector[2 * NUM_CEP + 4] = durationSec
        // sisa slot tetap 0: kontrak 256-byte .emb tidak berubah

        return Result(vector, f0Lag.first, voicedPerSec, durationSec)
    }

    fun toBytesLE(v: FloatArray): ByteArray {
        val out = ByteArray(v.size * 4)
        val buf = ByteBufferWrap(out)
        for (x in v) buf.putFloat(x)
        return out
    }

    fun average(vectors: List<FloatArray>): FloatArray {
        val avg = FloatArray(VECTOR_SIZE)
        for (v in vectors) for (i in 0 until VECTOR_SIZE) avg[i] += v[i]
        for (i in 0 until VECTOR_SIZE) avg[i] /= vectors.size
        return avg
    }

    // ---- internal DSP ----

    private class ByteBufferWrap(val array: ByteArray) {
        private var pos = 0
        fun putFloat(x: Float) {
            val bits = x.toRawBits()
            array[pos++] = (bits and 0xff).toByte()
            array[pos++] = ((bits shr 8) and 0xff).toByte()
            array[pos++] = ((bits shr 16) and 0xff).toByte()
            array[pos++] = ((bits shr 24) and 0xff).toByte()
        }
    }

    private fun preEmphasis(s: FloatArray) {
        for (i in s.size - 1 downTo 1) s[i] -= 0.97f * s[i - 1]
    }

    private fun melToFreq(m: Float) = 700f * (10f.pow(m / 2595f) - 1f)
    private fun freqToMel(f: Float) = 2595f * log10(1f + f / 700f)

    private fun powerToMelLog(re: FloatArray, im: FloatArray, totalE: Float, out: FloatArray, sampleRate: Int) {
        val nyq = FRAME / 2
        val melLo = freqToMel(0f)
        val melHi = freqToMel(sampleRate / 2f)
        val bin = IntArray(NUM_MEL + 2) {
            val m = melLo + (melHi - melLo) * it / (NUM_MEL + 1)
            min((melToFreq(m) / sampleRate * nyq).toInt().coerceIn(0, nyq - 1), nyq - 1)
        }
        for (m in 0 until NUM_MEL) {
            val left = bin[m]
            val center = bin[m + 1]
            val right = bin[m + 2]
            if (center == left && center == right) { out[m] = -20f; continue }
            var acc = 0f
            var count = 0
            for (k in left until center) {
                acc += powerSpectrum(re, im, k) * (k - left) / max(center - left, 1)
                count++
            }
            for (k in center until right) {
                acc += powerSpectrum(re, im, k) * (right - k) / max(right - center, 1)
                count++
            }
            out[m] = ln(max(acc / max(count, 1), 1e-10f))
        }
    }

    private fun powerSpectrum(re: FloatArray, im: FloatArray, k: Int): Float {
        val r = re[k]; val i = im[k]
        return r * r + i * i
    }

    private fun melToCepstrum(mel: FloatArray): FloatArray {
        val c = FloatArray(NUM_CEP)
        for (n in 0 until NUM_CEP) {
            var acc = 0.0
            for (k in mel.indices) {
                acc += mel[k] * cos(Math.PI * n * (k + 0.5) / mel.size)
            }
            c[n] = acc.toFloat()
        }
        return c
    }

    /** @return (meanVoicedF0, voicedFrames, f0Std) */
    private fun f0Stats(s: FloatArray, sampleRate: Int, rms: Float): Triple<Float, Float, Float> {
        val minLag = sampleRate / 400
        val maxLag = sampleRate / 60
        val win = maxLag * 2
        if (win > s.size) return Triple(0f, 0f, 0f)
        val step = sampleRate / 100

        var sumF = 0f
        var sumF2 = 0f
        var n = 0
        var pos = 0
        while (pos + win < s.size) {
            var frameRms = 0f
            for (i in 0 until win) frameRms += s[pos + i] * s[pos + i]
            frameRms = sqrt(frameRms / win)
            if (frameRms > rms * 1.5f) {
                var bestLag = -1
                var bestVal = 0f
                var energy = 0f
                for (i in 0 until win) energy += s[pos + i] * s[pos + i]
                if (energy > 1e-6f) {
                    var lag = minLag
                    while (lag <= maxLag) {
                        var c = 0f
                        for (i in 0 until win - lag) c += s[pos + i] * s[pos + i + lag]
                        if (c > bestVal) { bestVal = c; bestLag = lag }
                        lag++
                    }
                    if (bestLag > 0 && bestVal / energy > 0.35f) {
                        val f = sampleRate / bestLag.toFloat()
                        sumF += f; sumF2 += f * f; n++
                    }
                }
            }
            pos += step
        }
        if (n == 0) return Triple(0f, 0f, 0f)
        val mean = sumF / n
        val varv = max(sumF2 / n - mean * mean, 0f)
        return Triple(mean, n.toFloat(), sqrt(varv))
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = cos(ang.toFloat())
            val wi = kotlin.math.sin(ang.toFloat())
            var i = 0
            while (i < n) {
                var ar = 1f
                var ai = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * ar - im[i + k + len / 2] * ai
                    val vi = re[i + k + len / 2] * ai + im[i + k + len / 2] * ar
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = ar * wr - ai * wi
                    ai = ar * wi + ai * wr
                    ar = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
