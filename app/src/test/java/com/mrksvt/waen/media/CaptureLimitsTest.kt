package com.mrksvt.waen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLimitsTest {

    private val gib = 1024L * 1024 * 1024
    private val mib = 1024L * 1024

    @Test
    fun `tepat di batas belum dianggap melebihi`() {
        assertFalse(CaptureLimits.exceedsSizeLimit(1000L, 1000L))
    }

    @Test
    fun `satu byte di atas batas dianggap melebihi`() {
        assertTrue(CaptureLimits.exceedsSizeLimit(1001L, 1000L))
    }

    @Test
    fun `batas default empat gibibyte`() {
        assertEquals(4L * gib, CaptureLimits.DEFAULT_MAX_SESSION_BYTES)
        assertFalse(CaptureLimits.exceedsSizeLimit(4L * gib))
        assertTrue(CaptureLimits.exceedsSizeLimit(4L * gib + 1))
    }

    @Test
    fun `batas nol atau negatif selalu melebihi`() {
        assertTrue(CaptureLimits.exceedsSizeLimit(0L, 0L))
        assertTrue(CaptureLimits.exceedsSizeLimit(1L, -5L))
    }

    @Test
    fun `ukuran nol tidak melebihi batas wajar`() {
        assertFalse(CaptureLimits.exceedsSizeLimit(0L, 1000L))
    }

    @Test
    fun `remainingBytes mengurangi safety margin`() {
        assertEquals(750L, CaptureLimits.remainingBytes(1000L, 250L))
    }

    @Test
    fun `remainingBytes di bawah margin dikembalikan nol bukan negatif`() {
        assertEquals(0L, CaptureLimits.remainingBytes(100L, 250L))
        assertEquals(0L, CaptureLimits.remainingBytes(250L, 250L))
    }

    @Test
    fun `remainingBytes ruang nol atau negatif jadi nol`() {
        assertEquals(0L, CaptureLimits.remainingBytes(0L, 250L))
        assertEquals(0L, CaptureLimits.remainingBytes(-500L, 250L))
    }

    @Test
    fun `remainingBytes margin negatif diperlakukan sebagai nol`() {
        assertEquals(1000L, CaptureLimits.remainingBytes(1000L, -50L))
    }

    @Test
    fun `remainingBytes margin default dua ratus lima puluh mebibyte`() {
        assertEquals(250L * mib, CaptureLimits.DEFAULT_SAFETY_MARGIN_BYTES)
        assertEquals(
            950L * mib,
            CaptureLimits.remainingBytes(1200L * mib)
        )
    }

    @Test
    fun `canStartCapture butuh ruang melebihi margin`() {
        assertTrue(CaptureLimits.canStartCapture(10L * gib, 500L * mib, 250L * mib))
        assertFalse(CaptureLimits.canStartCapture(600L * mib, 500L * mib, 250L * mib))
        assertFalse(CaptureLimits.canStartCapture(300L * mib, 500L * mib, 250L * mib))
        assertFalse(CaptureLimits.canStartCapture(0L, 500L * mib, 250L * mib))
    }

    @Test
    fun `canStartCapture tepat di ambang dianggap boleh`() {
        assertTrue(CaptureLimits.canStartCapture(750L * mib, 500L * mib, 250L * mib))
    }

    // ---- RecordingStorage.needsMigration (T-072) ----

    private val root = RecordingStorage.DEFAULT_RECORDINGS_ROOT

    @Test
    fun `file di Downloads WA Call Recordings perlu migrasi`() {
        assertTrue(
            RecordingStorage.needsMigration(
                "/sdcard/Download/WA Call Recordings/Call_Budi_20260918_120000.m4a",
                root,
                isVideoCall = false
            )
        )
    }

    @Test
    fun `file di dalam root baru tidak perlu migrasi`() {
        assertFalse(
            RecordingStorage.needsMigration(
                "$root/voice_call/Call_Budi_20260918_120000.m4a",
                root,
                isVideoCall = false
            )
        )
        assertFalse(
            RecordingStorage.needsMigration(
                "$root/video_call/Call_Budi_20260918_120000-video.mp4",
                root,
                isVideoCall = true
            )
        )
    }

    @Test
    fun `file bukan rekaman tidak perlu migrasi`() {
        assertFalse(
            RecordingStorage.needsMigration("/sdcard/Download/foto.jpeg", root, isVideoCall = true)
        )
        assertFalse(
            RecordingStorage.needsMigration("/sdcard/Download/notes.txt", root, isVideoCall = false)
        )
    }

    @Test
    fun `file audio di folder video tidak perlu migrasi`() {
        assertFalse(
            RecordingStorage.needsMigration(
                "$root/video_call/Call_Budi_20260918_120000.m4a",
                root,
                isVideoCall = true
            )
        )
    }

    @Test
    fun `file video di folder voice tidak perlu migrasi`() {
        assertFalse(
            RecordingStorage.needsMigration(
                "$root/voice_call/Call_Budi_20260918_120000.mp4",
                root,
                isVideoCall = false
            )
        )
    }

    @Test
    fun `file video di lokasi lama perlu migrasi`() {
        assertTrue(
            RecordingStorage.needsMigration(
                "/sdcard/Download/WA Call Recordings/Call_Budi_20260918_120000-video.mp4",
                root,
                isVideoCall = true
            )
        )
    }

    @Test
    fun `path null atau kosong tidak perlu migrasi`() {
        assertFalse(RecordingStorage.needsMigration(null, root, isVideoCall = false))
        assertFalse(RecordingStorage.needsMigration("", root, isVideoCall = false))
        assertFalse(RecordingStorage.needsMigration("   ", root, isVideoCall = false))
        assertFalse(
            RecordingStorage.needsMigration(
                "/sdcard/Download/WA Call Recordings/Call_Budi_20260918_120000.m4a",
                null,
                isVideoCall = false
            )
        )
        assertFalse(
            RecordingStorage.needsMigration(
                "/sdcard/Download/WA Call Recordings/Call_Budi_20260918_120000.m4a",
                "",
                isVideoCall = false
            )
        )
    }

    @Test
    fun `path tanpa parent tidak perlu migrasi`() {
        assertFalse(RecordingStorage.needsMigration("Call_Budi_20260918_120000.m4a", root, false))
    }

    @Test
    fun `ekstensi audio yang didukung`() {
        assertTrue(RecordingStorage.isAudioRecording("a.m4a"))
        assertTrue(RecordingStorage.isAudioRecording("a.WAV"))
        assertTrue(RecordingStorage.isAudioRecording("a.aac"))
        assertFalse(RecordingStorage.isAudioRecording("a.mp4"))
        assertFalse(RecordingStorage.isAudioRecording("a.mp3"))
    }
}
