package com.mrksvt.waen.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Tes ekstraksi nama kontak dari nama file recording.
 * File tidak perlu ada - parseDuration early-return saat length 0.
 */
class RecordingContactNameTest {

    private fun nameOf(fileName: String): String =
        Recording(File("/nonexistent/$fileName")).contactName

    @Test
    fun `nama dengan koma dan titik tetap terbaca`() {
        assertEquals(
            "Budi, S.Kom",
            nameOf("Call_Budi,_S.Kom_20260908_123456.m4a")
        )
    }

    @Test
    fun `nama dengan dash dan apostrof terbaca`() {
        assertEquals("John-Doe", nameOf("Call_John-Doe_20260908_123456.m4a"))
        assertEquals("Andi's Chat", nameOf("Call_Andi's_Chat_20260908_123456.m4a"))
    }

    @Test
    fun `nama unicode dan emoji terbaca`() {
        assertEquals("Rina \uD83D\uDC69‍\uD83D\uDCBB", nameOf("Call_Rina_\uD83D\uDC69‍\uD83D\uDCBB_20260908_123456.m4a"))
    }

    @Test
    fun `identifier Unknown tetap Unknown`() {
        assertEquals("Unknown", nameOf("Call_Unknown_20260908_123456.m4a"))
    }

    @Test
    fun `nomor telepon polos terbaca sebagai identifier`() {
        assertEquals("6281234567890", nameOf("Call_6281234567890_20260908_123456.m4a"))
    }

    @Test
    fun `lid jid terbaca sebagai identifier`() {
        assertEquals("12345678912345@lid", nameOf("Call_12345678912345@lid_20260908_123456.m4a"))
    }

    @Test
    fun `file tanpa identifier jadi Unknown`() {
        assertEquals("Unknown", nameOf("Call_20260908_123456.m4a"))
    }

    @Test
    fun `ekstensi wav didukung`() {
        assertEquals("Siti", nameOf("Call_Siti_20260908_123456.wav"))
    }

    @Test
    fun `case insensitive prefix`() {
        assertEquals("Siti", nameOf("call_Siti_20260908_123456.m4a"))
    }

    @Test
    fun `bukan pola recording pakai nama file apa adanya`() {
        assertEquals("memo penting", nameOf("memo penting.mp3"))
    }

    @Test
    fun `timestamp lebih dulu di identifier tidak salah potong`() {
        // identifier non-greedy match + _ di-restore ke spasi untuk display
        assertEquals("20260101 1200000 person", nameOf("Call_20260101_1200000_person_20260908_123456.m4a"))
    }
}
