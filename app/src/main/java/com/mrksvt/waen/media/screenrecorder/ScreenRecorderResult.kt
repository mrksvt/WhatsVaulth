package com.mrksvt.waen.media.screenrecorder

sealed class ScreenRecorderResult {
    abstract val sessionId: ScreenRecorderSession.Id

    data class Saved(
        override val sessionId: ScreenRecorderSession.Id,
        val outputPath: String,
        val durationUs: Long,
        val videoSampleCount: Long,
        val width: Int,
        val height: Int
    ) : ScreenRecorderResult() {
        init {
            require(outputPath.isNotBlank())
            require(durationUs > 0)
            require(videoSampleCount > 0)
            require(width > 0 && height > 0)
        }
    }

    data class Failed(
        override val sessionId: ScreenRecorderSession.Id,
        val error: Error,
        val partialPath: String? = null
    ) : ScreenRecorderResult() {
        init {
            require(partialPath == null || partialPath.isNotBlank())
        }
    }

    enum class Error {
        PROJECTION_START,
        CODEC_CONFIGURE,
        CODEC_START,
        CODEC_WRITE,
        CODEC_DRAIN,
        CODEC_DRAIN_TIMEOUT,
        MUXER_FINALIZE,
        OUTPUT_VALIDATION,
        STORAGE_UNAVAILABLE,
        STORAGE_FULL,
        OUTPUT_PUBLISH,
        SESSION_INTERRUPTED,
        PLAYBACK_UNAVAILABLE
    }
}
