package com.mrksvt.waen.media.screenrecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.mrksvt.waen.media.VideoEncoderProfile

/**
 * Persiapan encoder AVC untuk SR.
 *
 * Pemilihan profile dan rantai fallback adalah fungsi murni (bisa diuji JVM).
 * Pembuatan `MediaCodec` memakai API Android dan hanya dipanggil worker
 * sebelum projection display dibuat.
 *
 * Canvas tetap per sesi: seluruh sesi memakai satu dimensi encoder, rasio
 * dipertahankan dengan letterbox saat projection diskalakan.
 */
class ScreenRecorderEncoder private constructor(
    val profile: VideoEncoderProfile.Profile,
    private val codec: MediaCodec,
    val inputSurface: Surface
) {

    private var released = false

    fun release() {
        if (released) return
        released = true
        runCatching { inputSurface.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    sealed class Outcome {
        data class Ready(val encoder: ScreenRecorderEncoder) : Outcome()
        data class Failed(val error: ScreenRecorderResult.Error) : Outcome()
    }

    companion object {
        const val MIME = VideoEncoderProfile.MIME_H264
        const val MAX_LONG_SIDE = 1280
        const val FRAME_RATE = 30
        const val MAX_ATTEMPTS = 3
        const val I_FRAME_INTERVAL_SECONDS = 1

        private fun even(value: Int): Int {
            val safe = if (value < 2) 2 else value
            return safe - (safe % 2)
        }

        /** Skala ke sisi panjang maksimal, rasio dipertahankan, ukuran genap. */
        fun scaleToMaxLongSide(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
            val width = if (screenWidth < 2) 2 else screenWidth
            val height = if (screenHeight < 2) 2 else screenHeight
            val longSide = maxOf(width, height)
            if (longSide <= MAX_LONG_SIDE) return even(width) to even(height)

            val scale = MAX_LONG_SIDE.toDouble() / longSide
            return even((width * scale).toInt()) to even((height * scale).toInt())
        }

        fun profileFor(screenWidth: Int, screenHeight: Int): VideoEncoderProfile.Profile {
            val (width, height) = scaleToMaxLongSide(screenWidth, screenHeight)
            return VideoEncoderProfile.Profile(
                id = "sr-${width}x$height",
                width = width,
                height = height,
                bitRate = bitRateFor(height),
                frameRate = FRAME_RATE,
                mimeType = MIME
            )
        }

        /**
         * Rantai profile yang dicoba saat `configure()` gagal. Height selalu
         * turun lewat [VideoEncoderProfile.downgrade] supaya rantai tidak
         * mandek di tingkat yang sama.
         */
        fun attemptChain(screenWidth: Int, screenHeight: Int): List<VideoEncoderProfile.Profile> {
            val chain = ArrayList<VideoEncoderProfile.Profile>(MAX_ATTEMPTS)
            var current: VideoEncoderProfile.Profile? = profileFor(screenWidth, screenHeight)
            while (current != null && chain.size < MAX_ATTEMPTS) {
                chain.add(current)
                current = VideoEncoderProfile.downgrade(current)
            }
            return chain
        }

        private fun bitRateFor(height: Int): Int = when {
            height >= 1080 -> 6_000_000
            height >= 720 -> 4_000_000
            else -> 2_500_000
        }

        /**
         * Coba configure+start pada seluruh kandidat. Setiap kegagalan
         * melepas codec yang sudah dibuat sebelum mencoba kandidat berikut,
         * sehingga tidak ada resource tertinggal saat start gagal.
         */
        fun create(screenWidth: Int, screenHeight: Int): Outcome {
            var lastError = ScreenRecorderResult.Error.CODEC_CONFIGURE
            for (profile in attemptChain(screenWidth, screenHeight)) {
                var codec: MediaCodec? = null
                var surface: Surface? = null
                try {
                    codec = MediaCodec.createEncoderByType(MIME)
                    codec.configure(formatFor(profile), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    surface = codec.createInputSurface()
                    codec.start()
                    return Outcome.Ready(ScreenRecorderEncoder(profile, codec, surface))
                } catch (e: Exception) {
                    surface?.let { runCatching { it.release() } }
                    codec?.let {
                        runCatching { it.stop() }
                        runCatching { it.release() }
                    }
                    lastError = classify(e)
                }
            }
            return Outcome.Failed(lastError)
        }

        private fun formatFor(profile: VideoEncoderProfile.Profile): MediaFormat =
            MediaFormat.createVideoFormat(MIME, profile.width, profile.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, profile.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, profile.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            }

        private fun classify(error: Exception): ScreenRecorderResult.Error =
            if (error is IllegalStateException) {
                ScreenRecorderResult.Error.CODEC_START
            } else {
                ScreenRecorderResult.Error.CODEC_CONFIGURE
            }
    }
}