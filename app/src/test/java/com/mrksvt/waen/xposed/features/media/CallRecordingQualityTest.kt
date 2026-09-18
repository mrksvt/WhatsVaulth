package com.mrksvt.waen.xposed.features.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tes profile kualitas audio Call Recording.
 *
 * Fokus: resolusi prefs tidak boleh pernah gagal (nilai usang tetap aman),
 * urutan kualitas naik monoton, dan rantai fallback bitrate berhenti di batas
 * bawah yang sehat.
 */
class CallRecordingQualityTest {

    // ---- T-002: resolveAudioProfile tidak pernah melempar / null ----

    @Test
    fun `nilai prefs tak dikenal jatuh ke AAC_96`() {
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile("garbage")
        )
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile("AAC_9999")
        )
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile("")
        )
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile("   ")
        )
    }

    @Test
    fun `nilai prefs null jatuh ke AAC_96`() {
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile(null)
        )
    }

    @Test
    fun `nilai prefs dikenal dipetakan tepat`() {
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.resolveAudioProfile("aac_96")
        )
        assertEquals(
            CallRecordingQuality.AAC_192,
            CallRecordingQuality.resolveAudioProfile("aac_192")
        )
        assertEquals(
            CallRecordingQuality.AAC_256,
            CallRecordingQuality.resolveAudioProfile("aac_256")
        )
    }

    @Test
    fun `nilai prefs tidak peka huruf besar dan spasi`() {
        assertEquals(
            CallRecordingQuality.AAC_256,
            CallRecordingQuality.resolveAudioProfile("  AAC_256  ")
        )
        assertEquals(
            CallRecordingQuality.AAC_192,
            CallRecordingQuality.resolveAudioProfile("AAC_192")
        )
    }

    // ---- T-001/T-004: kualitas profile naik monoton ----

    @Test
    fun `setiap profile HD punya bitrate di atas 96000`() {
        assertTrue(
            "harus ada profile HD (FR-06)",
            CallRecordingQuality.HD_PROFILES.isNotEmpty()
        )
        for (profile in CallRecordingQuality.HD_PROFILES) {
            assertTrue(
                "profile ${profile.id} bitrate ${profile.bitRate} harus > 96000",
                profile.bitRate > 96_000
            )
        }
    }

    @Test
    fun `AAC_256 bitrate lebih tinggi dari AAC_192`() {
        assertTrue(
            CallRecordingQuality.AAC_256.bitRate > CallRecordingQuality.AAC_192.bitRate
        )
    }

    @Test
    fun `urutan ALL naik monoton`() {
        val rates = CallRecordingQuality.ALL.map { it.bitRate }
        for (i in 1 until rates.size) {
            assertTrue(
                "bitrate harus naik: index $i (${rates[i]}) vs ${rates[i - 1]}",
                rates[i] > rates[i - 1]
            )
        }
    }

    @Test
    fun `sample rate profile HD minimal 44100`() {
        for (profile in CallRecordingQuality.ALL) {
            assertTrue(
                "profile ${profile.id} sampleRate ${profile.sampleRate} terlalu rendah",
                profile.sampleRate >= 44_100
            )
        }
    }

    @Test
    fun `semua profile memakai container M4A dan id unik`() {
        val ids = CallRecordingQuality.ALL.map { it.id }
        assertEquals("id profile harus unik", ids.size, ids.toSet().size)
        for (profile in CallRecordingQuality.ALL) {
            assertEquals(
                CallRecordingQuality.Container.M4A,
                profile.container
            )
        }
    }

    @Test
    fun `id profile sama dengan nilai prefs yang dipetakan`() {
        for (profile in CallRecordingQuality.ALL) {
            assertEquals(
                "resolveAudioProfile(${profile.id}) harus balik ke profile itu sendiri",
                profile,
                CallRecordingQuality.resolveAudioProfile(profile.id)
            )
        }
    }

    // ---- T-003: rantai penurunan bitrate ----

    @Test
    fun `downgraded menurunkan bitrate di atas batas bawah`() {
        val lower = CallRecordingQuality.AAC_256.downgraded()
        assertNotNull(lower)
        assertTrue(lower!!.bitRate < CallRecordingQuality.AAC_256.bitRate)
        assertTrue(lower.bitRate >= CallRecordingQuality.FLOOR_BITRATE)
    }

    @Test
    fun `downgraded berhenti di batas bawah dan tidak pernah null-loop`() {
        var current: CallRecordingQuality.AudioProfile? = CallRecordingQuality.AAC_256
        var hops = 0
        while (current != null) {
            assertTrue(
                "bitrate ${current.bitRate} tidak boleh di bawah MIN_BITRATE",
                current.bitRate >= CallRecordingQuality.FLOOR_BITRATE
            )
            current = current.downgraded()
            hops++
            assertTrue("rantai downgrade tidak boleh tak terbatas", hops < 20)
        }
    }

    @Test
    fun `attemptChain selalu minimal satu elemen`() {
        for (value in listOf(null, "", "garbage", "aac_96", "aac_256")) {
            val chain = CallRecordingQuality.attemptChain(value)
            assertTrue("chain untuk '$value' harus tidak kosong", chain.isNotEmpty())
        }
    }

    @Test
    fun `attemptChain mulai dari profile yang dipilih`() {
        assertEquals(
            CallRecordingQuality.AAC_256,
            CallRecordingQuality.attemptChain("aac_256").first()
        )
        assertEquals(
            CallRecordingQuality.AAC_96,
            CallRecordingQuality.attemptChain("nope").first()
        )
    }

    @Test
    fun `attemptChain turun monoton dan dibatasi MAX_ATTEMPTS`() {
        val chain = CallRecordingQuality.attemptChain("aac_256")
        assertTrue(chain.size <= CallRecordingQuality.MAX_ATTEMPTS)
        for (i in 1 until chain.size) {
            assertTrue(
                "chain harus turun: ${chain[i].bitRate} vs ${chain[i - 1].bitRate}",
                chain[i].bitRate < chain[i - 1].bitRate
            )
        }
        for (profile in chain) {
            assertNotNull(profile)
        }
    }

    // REWORK-1: batas upaya harus cukup untuk mencapai lantai dari profile
    // tertinggi. Sebelum perbaikan, rantai dari AAC_256 berhenti di 160000
    // sehingga 128000 dan 96000 tidak pernah dicoba.

    @Test
    fun `rantai dari AAC_256 mencapai lantai bitrate`() {
        val chain = CallRecordingQuality.attemptChain("aac_256")
        assertEquals(
            "rantai harus berakhir tepat di lantai",
            CallRecordingQuality.FLOOR_BITRATE,
            chain.last().bitRate
        )
    }

    @Test
    fun `rantai dari AAC_192 mencapai lantai bitrate`() {
        val chain = CallRecordingQuality.attemptChain("aac_192")
        assertEquals(
            CallRecordingQuality.FLOOR_BITRATE,
            chain.last().bitRate
        )
    }

    @Test
    fun `setiap bitrate menuju lantai ada di rantai AAC_256`() {
        val rates = CallRecordingQuality.attemptChain("aac_256").map { it.bitRate }
        assertEquals(
            "semua tingkat harus tercoba tanpa ada yang dilompati",
            listOf(256_000, 224_000, 160_000, 128_000, 96_000),
            rates
        )
    }

    @Test
    fun `MAX_ATTEMPTS cukup untuk menurunkan profile tertinggi ke lantai`() {
        var profile: CallRecordingQuality.AudioProfile? =
            CallRecordingQuality.resolveAudioProfile("aac_256")
        var steps = 0
        while (profile != null) {
            profile = profile.downgraded()
            steps++
        }
        assertEquals(
            "jumlah tingkat dari profile tertinggi harus sama dengan langkah penurunan",
            CallRecordingQuality.MAX_ATTEMPTS,
            steps
        )
    }

    @Test
    fun `attemptChain untuk AAC_96 tetap tidak turun di bawah batas bawah`() {
        val chain = CallRecordingQuality.attemptChain("aac_96")
        for (profile in chain) {
            assertTrue(profile.bitRate >= CallRecordingQuality.FLOOR_BITRATE)
        }
    }

    @Test
    fun `semua profile memakai encoder AAC lokal`() {
        for (profile in CallRecordingQuality.ALL) {
            assertEquals(
                "profile ${profile.id} harus memakai AAC_ENCODER",
                CallRecordingQuality.AAC_ENCODER,
                profile.encoder
            )
        }
    }

    @Test
    fun `downgraded mempertahankan identitas profile asal`() {
        val lower = CallRecordingQuality.AAC_256.downgraded()
        assertNotNull(lower)
        assertEquals(
            "id profile asal harus dipertahankan saat bitrate diturunkan",
            CallRecordingQuality.AAC_256.id,
            lower!!.id
        )
        assertEquals(
            "sample rate tidak ikut berubah",
            CallRecordingQuality.AAC_256.sampleRate,
            lower.sampleRate
        )
    }

    @Test
    fun `profile default AAC_96 setara perilaku lama`() {
        assertEquals(96_000, CallRecordingQuality.AAC_96.bitRate)
        assertEquals(44_100, CallRecordingQuality.AAC_96.sampleRate)
        assertTrue(
            "AAC_96 sudah di lantai bitrate, tidak boleh bisa diturunkan",
            CallRecordingQuality.AAC_96.downgraded() == null
        )
    }
}
