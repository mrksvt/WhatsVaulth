package com.mrksvt.waen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji bagian `AudioVideoMuxer` yang murni. `mux()` sendiri butuh
 * `MediaExtractor`/`MediaMuxer` sehingga tidak bisa dijalankan di JVM; kebijakan
 * hasil dan perhitungan durasi diuji di sini karena itulah yang menentukan
 * apakah file user dipertahankan atau tidak (FINDINGS B-4).
 */
class MuxDecisionTest {

    @Test
    fun `alignDuration memakai durasi terpanjang`() {
        assertEquals(5_000_000L, AudioVideoMuxer.alignDuration(5_000_000L, 3_000_000L))
        assertEquals(5_000_000L, AudioVideoMuxer.alignDuration(3_000_000L, 5_000_000L))
    }

    @Test
    fun `alignDuration saat durasi sama`() {
        assertEquals(7_700_000L, AudioVideoMuxer.alignDuration(7_700_000L, 7_700_000L))
    }

    @Test
    fun `alignDuration menangani durasi nol atau negatif`() {
        assertEquals(0L, AudioVideoMuxer.alignDuration(0L, 0L))
        assertEquals(1L, AudioVideoMuxer.alignDuration(1L, 0L))
        assertEquals(4_000_000L, AudioVideoMuxer.alignDuration(4_000_000L, -1L))
        assertEquals(0L, AudioVideoMuxer.alignDuration(-5L, -9L))
    }

    @Test
    fun `alignDuration tidak pernah negatif`() {
        val samples = listOf(-1L to -1L, 0L to -100L, -100L to 0L, Long.MIN_VALUE to 0L)
        for ((video, audio) in samples) {
            assertTrue(
                "hasil untuk ($video, $audio) tidak boleh negatif",
                AudioVideoMuxer.alignDuration(video, audio) >= 0L
            )
        }
    }

    @Test
    fun `onMuxResult sukses mempertahankan hanya hasil akhir`() {
        assertEquals(AudioVideoMuxer.OUTCOME_KEEP_PRIMARY_ONLY, AudioVideoMuxer.onMuxResult(true))
    }

    @Test
    fun `onMuxResult gagal mempertahankan kedua file mentah`() {
        assertEquals(AudioVideoMuxer.OUTCOME_KEEP_BOTH, AudioVideoMuxer.onMuxResult(false))
    }

    @Test
    fun `onMuxResult tidak pernah mengembalikan null atau kosong`() {
        for (success in listOf(true, false)) {
            val outcome = AudioVideoMuxer.onMuxResult(success)
            assertNotNull(outcome)
            assertTrue("outcome harus terisi", outcome.isNotBlank())
        }
    }

    @Test
    fun `kedua outcome berbeda`() {
        assertTrue(AudioVideoMuxer.onMuxResult(true) != AudioVideoMuxer.onMuxResult(false))
    }

}
