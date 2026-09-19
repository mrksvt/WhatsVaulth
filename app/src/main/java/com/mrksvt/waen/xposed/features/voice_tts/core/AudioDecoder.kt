package com.mrksvt.waen.xposed.features.voice_tts.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    fun decodeToPcm(file: File): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ArrayList<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(chunk)
                        val shorts = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                        while (shorts.hasRemaining()) pcm.add(shorts.short)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // format may change during decode; nothing extra to do for 16-bit PCM
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            // give up if no output arrives after EOS queued
                            for (retry in 0 until 20) {
                                val retryIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                                if (retryIdx >= 0) {
                                    val outBuf = codec.getOutputBuffer(retryIdx)!!
                                    val chunk = ByteArray(bufferInfo.size)
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.get(chunk)
                                    val shorts = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                                    while (shorts.hasRemaining()) pcm.add(shorts.short)
                                    codec.releaseOutputBuffer(retryIdx, false)
                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                        outputDone = true
                                        break
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            }

            return pcm.toShortArray()
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
