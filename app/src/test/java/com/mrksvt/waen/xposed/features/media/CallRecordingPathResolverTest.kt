package com.mrksvt.waen.xposed.features.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRecordingPathResolverTest {

    private val defaultRoot = "/sdcard/WhatsVault/recordings"

    @Test
    fun `root null jatuh ke default dan subfolder video`() {
        val dir = CallRecordingPathResolver.resolveAppDir(null, isVideoCall = true, isBusiness = false)
        assertEquals("$defaultRoot/video_call", dir.absolutePath)
    }

    @Test
    fun `root kosong jatuh ke default`() {
        assertEquals(
            "$defaultRoot/video_call",
            CallRecordingPathResolver.resolveAppDir("", true, false).absolutePath
        )
        assertEquals(
            "$defaultRoot/voice_call",
            CallRecordingPathResolver.resolveAppDir("   ", false, false).absolutePath
        )
    }

    @Test
    fun `root dari preferensi dipakai apa adanya`() {
        val dir = CallRecordingPathResolver.resolveAppDir("/storage/emulated/0/Custom", true, false)
        assertEquals("/storage/emulated/0/Custom/video_call", dir.absolutePath)
    }

    @Test
    fun `voice call masuk ke voice_call`() {
        assertEquals(
            "$defaultRoot/voice_call",
            CallRecordingPathResolver.resolveAppDir(null, false, false).absolutePath
        )
    }

    @Test
    fun `business tidak mengubah struktur folder`() {
        val wa = CallRecordingPathResolver.resolveAppDir(null, true, isBusiness = false)
        val w4b = CallRecordingPathResolver.resolveAppDir(null, true, isBusiness = true)
        assertEquals(wa.absolutePath, w4b.absolutePath)

        val voiceWa = CallRecordingPathResolver.resolveAppDir(null, false, false)
        val voiceW4b = CallRecordingPathResolver.resolveAppDir(null, false, true)
        assertEquals(voiceWa.absolutePath, voiceW4b.absolutePath)
    }

    @Test
    fun `callKindOf memetakan boolean`() {
        assertEquals(CallRecordingPathResolver.CallKind.VIDEO, CallRecordingPathResolver.callKindOf(true))
        assertEquals(CallRecordingPathResolver.CallKind.VOICE, CallRecordingPathResolver.callKindOf(false))
    }

    @Test
    fun `nama file video berakhiran -video mp4`() {
        val name = CallRecordingPathResolver.buildFileNameFor("Budi", "20260918_120000", true)
        assertEquals("Call_Budi_20260918_120000-video.mp4", name)
    }

    @Test
    fun `nama file voice berakhiran m4a`() {
        val name = CallRecordingPathResolver.buildFileNameFor("Budi", "20260918_120000", false)
        assertEquals("Call_Budi_20260918_120000.m4a", name)
    }

    @Test
    fun `identifier null jadi Unknown`() {
        assertEquals(
            "Call_Unknown_20260918_120000.m4a",
            CallRecordingPathResolver.buildFileNameFor(null, "20260918_120000", false)
        )
        assertEquals(
            "Call_Unknown_20260918_120000-video.mp4",
            CallRecordingPathResolver.buildFileNameFor("   ", "20260918_120000", true)
        )
    }

    @Test
    fun `karakter ilegal pada identifier diganti underscore`() {
        val name = CallRecordingPathResolver.buildFileNameFor("a/b:c*d", "20260918_120000", false)
        assertEquals("Call_a_b_c_d_20260918_120000.m4a", name)
    }

    @Test
    fun `nama file video dan voice berbeda agar tidak bertabrakan`() {
        val ts = "20260918_120000"
        val video = CallRecordingPathResolver.buildFileNameFor("Budi", ts, true)
        val voice = CallRecordingPathResolver.buildFileNameFor("Budi", ts, false)
        assertTrue("nama video dan voice harus berbeda", video != voice)
        assertTrue(video.endsWith(".mp4"))
        assertTrue(voice.endsWith(".m4a"))
    }

    @Test
    fun `folder video dan voice berbeda`() {
        val video = CallRecordingPathResolver.resolveAppDir(null, true, false)
        val voice = CallRecordingPathResolver.resolveAppDir(null, false, false)
        assertTrue(video.absolutePath != voice.absolutePath)
    }
}
