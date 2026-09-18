package com.mrksvt.waen.media

/**
 * Area layar yang direkam, dalam piksel.
 *
 * V1 selalu memakai [fullScreen] karena mode crop per-arah (hanya lawan bicara
 * / hanya diri sendiri) dibatalkan pada revisi rencana saat ini. Tipe ini tetap
 * menerima rect sembarang supaya mode crop bisa dihidupkan kembali tanpa
 * membongkar `ScreenCapturePipeline`.
 */
data class CaptureFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0
) {
    companion object {
        fun fullScreen(screenWidth: Int, screenHeight: Int, rotationDegrees: Int = 0): CaptureFrame =
            CaptureFrame(
                x = 0,
                y = 0,
                width = screenWidth,
                height = screenHeight,
                rotationDegrees = rotationDegrees
            )

        /**
         * Bulatkan batas frame ke koordinat genap dan jaga agar tetap di dalam
         * layar. Encoder H.264 menolak dimensi ganjil, dan `MediaCodec` menolak
         * rect di luar buffer.
         */
        fun snapEven(frame: CaptureFrame, screenWidth: Int, screenHeight: Int): CaptureFrame {
            val safeScreenWidth = if (screenWidth < 2) 2 else screenWidth
            val safeScreenHeight = if (screenHeight < 2) 2 else screenHeight

            val left = frame.x.coerceIn(0, safeScreenWidth - 2)
            val top = frame.y.coerceIn(0, safeScreenHeight - 2)

            val maxWidth = safeScreenWidth - left
            val maxHeight = safeScreenHeight - top

            val width = evenAtLeastTwo(frame.width.coerceAtMost(maxWidth))
            val height = evenAtLeastTwo(frame.height.coerceAtMost(maxHeight))

            return frame.copy(x = left - (left % 2), y = top - (top % 2), width = width, height = height)
        }

        private fun evenAtLeastTwo(value: Int): Int {
            val safe = if (value < 2) 2 else value
            return safe - (safe % 2)
        }
    }
}
