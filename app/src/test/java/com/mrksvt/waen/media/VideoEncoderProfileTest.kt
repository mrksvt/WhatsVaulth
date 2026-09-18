package com.mrksvt.waen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEncoderProfileTest {

    @Test
    fun `bitrate naik monoton terhadap tinggi layar`() {
        val p480 = VideoEncoderProfile.forResolution(1080, 480)
        val p720 = VideoEncoderProfile.forResolution(1080, 720)
        val p1080 = VideoEncoderProfile.forResolution(1080, 1080)
        val p1440 = VideoEncoderProfile.forResolution(1080, 1440)

        assertTrue("720p > 480p", p720.bitRate > p480.bitRate)
        assertTrue("1080p > 720p", p1080.bitRate > p720.bitRate)
        assertTrue("1440p > 1080p", p1440.bitRate > p1080.bitRate)
    }

    @Test
    fun `resolusi selalu kelipatan 2`() {
        val sizes = listOf(1080 to 2400, 1081 to 2401, 999 to 2161, 720 to 1601, 3 to 3, 1 to 1)
        for ((w, h) in sizes) {
            val p = VideoEncoderProfile.forResolution(w, h)
            assertEquals("width ${p.width} harus genap", 0, p.width % 2)
            assertEquals("height ${p.height} harus genap", 0, p.height % 2)
        }
    }

    @Test
    fun `resolusi tidak pernah nol atau negatif`() {
        for ((w, h) in listOf(0 to 0, -5 to -5, 1 to 1, 0 to 1080, 1080 to 0)) {
            val p = VideoEncoderProfile.forResolution(w, h)
            assertTrue("width harus > 0 untuk input $w x $h", p.width > 0)
            assertTrue("height harus > 0 untuk input $w x $h", p.height > 0)
        }
    }

    @Test
    fun `frame rate dalam rentang 24 sampai 60`() {
        for (h in listOf(480, 720, 1080, 1440, 2160)) {
            val p = VideoEncoderProfile.forResolution(1080, h)
            assertTrue(
                "frameRate ${p.frameRate} di luar rentang untuk tinggi $h",
                p.frameRate in VideoEncoderProfile.MIN_FRAME_RATE..VideoEncoderProfile.MAX_FRAME_RATE
            )
        }
    }

    @Test
    fun `semua profile memakai mime H264`() {
        for (h in listOf(480, 720, 1080, 1440)) {
            assertEquals(VideoEncoderProfile.MIME_H264, VideoEncoderProfile.forResolution(1080, h).mimeType)
        }
    }

    @Test
    fun `tinggi target tidak melebihi layar`() {
        val p = VideoEncoderProfile.forResolution(1080, 600)
        assertTrue("tinggi ${p.height} tidak boleh melebihi layar 600", p.height <= 600)
    }

    @Test
    fun `auto memakai ukuran layar nyata tanpa menurunkan resolusi`() {
        val p = VideoEncoderProfile.forPreference(VideoEncoderProfile.PREF_AUTO, 1080, 2400)
        assertEquals(2400, p.height)
        assertEquals(1080, p.width)
        assertEquals("bitrate bucket 1440p atau lebih", 12_000_000, p.bitRate)
    }

    @Test
    fun `auto pada layar kecil memakai ukuran layar apa adanya`() {
        val p = VideoEncoderProfile.forPreference(VideoEncoderProfile.PREF_AUTO, 720, 1280)
        assertEquals(1280, p.height)
        assertEquals(720, p.width)
        assertEquals(8_000_000, p.bitRate)
    }

    @Test
    fun `nilai preferensi tak dikenal jatuh ke auto`() {
        val auto = VideoEncoderProfile.forPreference("auto", 1080, 2400)
        assertEquals(auto, VideoEncoderProfile.forPreference(null, 1080, 2400))
        assertEquals(auto, VideoEncoderProfile.forPreference("", 1080, 2400))
        assertEquals(auto, VideoEncoderProfile.forPreference("   ", 1080, 2400))
        assertEquals(auto, VideoEncoderProfile.forPreference("garbage", 1080, 2400))
    }

    @Test
    fun `preferensi tinggi dibatasi ukuran layar`() {
        val p = VideoEncoderProfile.forPreference("1080", 720, 1280)
        assertTrue("tinggi ${p.height} tidak boleh melebihi layar 1280", p.height <= 1280)
        assertEquals(0, p.width % 2)
        assertEquals(0, p.height % 2)
    }

    @Test
    fun `preferensi 720 menghasilkan bitrate lebih rendah dari 1080`() {
        val p720 = VideoEncoderProfile.forPreference("720", 1080, 2400)
        val p1080 = VideoEncoderProfile.forPreference("1080", 1080, 2400)
        assertTrue(p1080.bitRate > p720.bitRate)
    }

    @Test
    fun `label resolusi memakai tinggi`() {
        assertEquals("1080p", VideoEncoderProfile.forResolution(1080, 1080).resolutionLabel)
        assertEquals("1440p", VideoEncoderProfile.forResolution(1080, 1440).resolutionLabel)
    }

    @Test
    fun `downgrade menurunkan tinggi, bitrate, dan frame rate`() {
        val top = VideoEncoderProfile.forResolution(1080, 1440)
        val lower = VideoEncoderProfile.downgrade(top)
        assertNotNull(lower)
        assertTrue(lower!!.height < top.height)
        assertTrue(lower.bitRate < top.bitRate)
    }

    @Test
    fun `downgrade berhenti di ketinggian minimum`() {
        var current: VideoEncoderProfile.Profile? = VideoEncoderProfile.forResolution(1080, 1440)
        var hops = 0
        var lowest = 0
        while (current != null) {
            lowest = current.height
            current = VideoEncoderProfile.downgrade(current)
            hops++
            assertTrue("downgrade tidak boleh tak terbatas", hops < 10)
        }
        assertEquals(VideoEncoderProfile.MIN_HEIGHT, lowest)
        assertTrue("harus melewati beberapa tingkat", hops >= 2)
    }

    @Test
    fun `attemptChain mulai dari profile pilihan user`() {
        val chain = VideoEncoderProfile.attemptChain("720", 1080, 2400)
        assertEquals(720, chain.first().height)
    }

    @Test
    fun `attemptChain turun monoton dan dibatasi MAX_ATTEMPTS`() {
        val chain = VideoEncoderProfile.attemptChain("1440", 1080, 2400)
        assertTrue(chain.size <= VideoEncoderProfile.MAX_ATTEMPTS)
        for (i in 1 until chain.size) {
            assertTrue(
                "chain harus turun: ${chain[i].height} vs ${chain[i - 1].height}",
                chain[i].height < chain[i - 1].height
            )
        }
    }

    @Test
    fun `attemptChain dari 1440p mencakup tingkat terendah`() {
        val chain = VideoEncoderProfile.attemptChain("1440", 1080, 2400)
        assertEquals(
            "rantai harus sampai MIN_HEIGHT supaya configure() punya kesempatan terakhir",
            VideoEncoderProfile.MIN_HEIGHT,
            chain.last().height
        )
    }

    @Test
    fun `attemptChain tidak pernah kosong`() {
        for (pref in listOf(null, "", "garbage", "480", "1440")) {
            assertTrue(VideoEncoderProfile.attemptChain(pref, 1080, 2400).isNotEmpty())
        }
    }

    @Test
    fun `semua elemen attemptChain tetap kelipatan 2`() {
        for (profile in VideoEncoderProfile.attemptChain("1440", 1081, 2401)) {
            assertEquals(0, profile.width % 2)
            assertEquals(0, profile.height % 2)
            assertTrue(profile.width > 0)
            assertTrue(profile.height > 0)
        }
    }
}
