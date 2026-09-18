package com.mrksvt.waen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFrameTest {

    @Test
    fun `fullScreen mengembalikan seluruh layar`() {
        val frame = CaptureFrame.fullScreen(1080, 2400, 0)
        assertEquals(0, frame.x)
        assertEquals(0, frame.y)
        assertEquals(1080, frame.width)
        assertEquals(2400, frame.height)
        assertEquals(0, frame.rotationDegrees)
    }

    @Test
    fun `fullScreen meneruskan rotasi`() {
        assertEquals(90, CaptureFrame.fullScreen(1080, 2400, 90).rotationDegrees)
        assertEquals(270, CaptureFrame.fullScreen(1080, 2400, 270).rotationDegrees)
    }

    @Test
    fun `snapEven membulatkan lebar ganjil ke bawah`() {
        val frame = CaptureFrame(x = 0, y = 0, width = 1081, height = 2400)
        val snapped = CaptureFrame.snapEven(frame, 1081, 2400)
        assertEquals(1080, snapped.width)
        assertEquals(0, snapped.width % 2)
    }

    @Test
    fun `snapEven membulatkan tinggi ganjil ke bawah`() {
        val frame = CaptureFrame(x = 0, y = 0, width = 1080, height = 2401)
        val snapped = CaptureFrame.snapEven(frame, 1080, 2401)
        assertEquals(2400, snapped.height)
        assertEquals(0, snapped.height % 2)
    }

    @Test
    fun `snapEven membulatkan x dan y ganjil`() {
        val frame = CaptureFrame(x = 101, y = 55, width = 400, height = 400)
        val snapped = CaptureFrame.snapEven(frame, 1080, 2400)
        assertEquals(0, snapped.x % 2)
        assertEquals(0, snapped.y % 2)
        assertTrue("x tidak boleh bertambah", snapped.x <= frame.x)
        assertTrue("y tidak boleh bertambah", snapped.y <= frame.y)
    }

    @Test
    fun `snapEven tidak mengubah frame yang sudah pas`() {
        val frame = CaptureFrame(x = 0, y = 0, width = 1080, height = 2400)
        assertEquals(frame, CaptureFrame.snapEven(frame, 1080, 2400))
    }

    @Test
    fun `snapEven tidak pernah menghasilkan dimensi nol`() {
        val frames = listOf(
            CaptureFrame(0, 0, 0, 0),
            CaptureFrame(0, 0, 1, 1),
            CaptureFrame(0, 0, -100, -100),
            CaptureFrame(-50, -50, 10, 10)
        )
        for (frame in frames) {
            val snapped = CaptureFrame.snapEven(frame, 1080, 2400)
            assertTrue("width harus > 0 untuk $frame", snapped.width > 0)
            assertTrue("height harus > 0 untuk $frame", snapped.height > 0)
        }
    }

    @Test
    fun `snapEven tidak pernah keluar batas layar`() {
        val frames = listOf(
            CaptureFrame(1000, 2300, 500, 500),
            CaptureFrame(1079, 2399, 1080, 2400),
            CaptureFrame(-10, -10, 5000, 5000),
            CaptureFrame(0, 0, 99999, 99999)
        )
        for (frame in frames) {
            val snapped = CaptureFrame.snapEven(frame, 1080, 2400)
            assertTrue("x negatif untuk $frame", snapped.x >= 0)
            assertTrue("y negatif untuk $frame", snapped.y >= 0)
            assertTrue(
                "sisi kanan ${snapped.x + snapped.width} melebihi layar untuk $frame",
                snapped.x + snapped.width <= 1080
            )
            assertTrue(
                "sisi bawah ${snapped.y + snapped.height} melebihi layar untuk $frame",
                snapped.y + snapped.height <= 2400
            )
        }
    }

    @Test
    fun `snapEven selalu genap untuk berbagai input`() {
        for (w in 1..12) {
            for (h in 1..12) {
                val snapped = CaptureFrame.snapEven(CaptureFrame(0, 0, w, h), 100, 100)
                assertEquals("width $w jadi ${snapped.width}", 0, snapped.width % 2)
                assertEquals("height $h jadi ${snapped.height}", 0, snapped.height % 2)
            }
        }
    }

    @Test
    fun `snapEven menangani layar sangat kecil`() {
        val snapped = CaptureFrame.snapEven(CaptureFrame(0, 0, 10, 10), 2, 2)
        assertEquals(2, snapped.width)
        assertEquals(2, snapped.height)
        assertEquals(0, snapped.x)
        assertEquals(0, snapped.y)
    }

    @Test
    fun `snapEven menangani layar nol`() {
        val snapped = CaptureFrame.snapEven(CaptureFrame(0, 0, 10, 10), 0, 0)
        assertTrue(snapped.width > 0)
        assertTrue(snapped.height > 0)
        assertTrue(snapped.width <= 2)
        assertTrue(snapped.height <= 2)
    }

    @Test
    fun `snapEven tidak menggeser frame yang sudah di dalam layar`() {
        val frame = CaptureFrame(x = 100, y = 200, width = 800, height = 1600)
        val snapped = CaptureFrame.snapEven(frame, 1080, 2400)
        assertEquals(frame, snapped)
    }
}
