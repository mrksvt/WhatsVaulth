package com.mrksvt.waen.media.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecorderEncoderTest {

    @Test
    fun `keeps resolution below max long side`() {
        assertEquals(720 to 1280, ScreenRecorderEncoder.scaleToMaxLongSide(720, 1280))
    }

    @Test
    fun `scales tall screen to max long side preserving ratio`() {
        assertEquals(576 to 1280, ScreenRecorderEncoder.scaleToMaxLongSide(1080, 2400))
    }

    @Test
    fun `scales wide screen to max long side preserving ratio`() {
        assertEquals(1280 to 576, ScreenRecorderEncoder.scaleToMaxLongSide(2400, 1080))
    }

    @Test
    fun `rounds odd dimensions down to even`() {
        val (w, h) = ScreenRecorderEncoder.scaleToMaxLongSide(1081, 719)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
        assertEquals(1080 to 718, w to h)
    }

    @Test
    fun `never produces dimension below two`() {
        val (w, h) = ScreenRecorderEncoder.scaleToMaxLongSide(1, 1)
        assertTrue(w >= 2)
        assertTrue(h >= 2)
    }

    @Test
    fun `profile always uses avc and thirty fps`() {
        val profile = ScreenRecorderEncoder.profileFor(1080, 2400)
        assertEquals(ScreenRecorderEncoder.MIME, profile.mimeType)
        assertEquals(ScreenRecorderEncoder.FRAME_RATE, profile.frameRate)
        assertEquals(0, profile.width % 2)
        assertEquals(0, profile.height % 2)
        assertTrue(profile.width <= ScreenRecorderEncoder.MAX_LONG_SIDE)
        assertTrue(profile.height <= ScreenRecorderEncoder.MAX_LONG_SIDE)
    }

    @Test
    fun `attempt chain descends and is bounded`() {
        val chain = ScreenRecorderEncoder.attemptChain(1080, 2400)
        assertTrue(chain.isNotEmpty())
        assertTrue(chain.size <= ScreenRecorderEncoder.MAX_ATTEMPTS)
        for (i in 1 until chain.size) {
            assertTrue("height must strictly decrease", chain[i].height < chain[i - 1].height)
        }
        assertEquals(ScreenRecorderEncoder.FRAME_RATE, chain.first().frameRate)
    }

    @Test
    fun `attempt chain from small screen still terminates`() {
        val chain = ScreenRecorderEncoder.attemptChain(480, 480)
        assertTrue(chain.isNotEmpty())
        assertTrue(chain.size <= ScreenRecorderEncoder.MAX_ATTEMPTS)
        assertTrue(chain.all { it.frameRate == ScreenRecorderEncoder.FRAME_RATE })
    }

    @Test
    fun `bitrate falls as height falls`() {
        val high = ScreenRecorderEncoder.profileFor(1080, 1920)
        val low = ScreenRecorderEncoder.profileFor(480, 480)
        assertTrue(high.bitRate >= low.bitRate)
    }
}
