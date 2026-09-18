package com.mrksvt.waen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingStorageTest {

    private fun legacy(configuredPath: String? = null): List<File> =
        RecordingStorage.legacyBaseDirs(
            configuredPath = configuredPath,
            downloadsDir = File("/sdcard/Download"),
            externalRoot = File("/sdcard"),
            whatsappDataDirs = listOf(
                File("/sdcard/Android/data/com.whatsapp/files/Recordings"),
                File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings")
            )
        )

    @Test
    fun `video call masuk ke folder video_call`() {
        val folder = RecordingStorage.callKindFolder(File("/sdcard/WhatsVault/recordings"), true)
        assertEquals("video_call", folder.name)
        assertEquals(
            "/sdcard/WhatsVault/recordings/video_call",
            folder.absolutePath
        )
    }

    @Test
    fun `voice call masuk ke folder voice_call`() {
        val folder = RecordingStorage.callKindFolder(File("/sdcard/WhatsVault/recordings"), false)
        assertEquals("voice_call", folder.name)
        assertEquals(
            "/sdcard/WhatsVault/recordings/voice_call",
            folder.absolutePath
        )
    }

    @Test
    fun `nama folder berbeda antar jenis panggilan`() {
        val root = File("/sdcard/WhatsVault/recordings")
        assertTrue(
            RecordingStorage.callKindFolder(root, true) !=
                RecordingStorage.callKindFolder(root, false)
        )
    }

    @Test
    fun `root default sama dengan path yang diminta user`() {
        assertEquals("/sdcard/WhatsVault/recordings", RecordingStorage.DEFAULT_RECORDINGS_ROOT)
        assertEquals(
            RecordingStorage.DEFAULT_RECORDINGS_ROOT,
            RecordingStorage.defaultRecordingsRoot()
        )
    }

    @Test
    fun `root default konsisten dengan nama folder pada App`() {
        val expected = File("/sdcard", RecordingStorage.ROOT_DIR_NAME)
            .resolve(RecordingStorage.RECORDINGS_DIR_NAME)
            .absolutePath
        assertEquals(expected, RecordingStorage.DEFAULT_RECORDINGS_ROOT)
    }

    @Test
    fun `folder default berada di bawah root`() {
        val root = File(RecordingStorage.DEFAULT_RECORDINGS_ROOT)
        assertEquals(
            "/sdcard/WhatsVault/recordings/video_call",
            RecordingStorage.callKindFolder(root, true).absolutePath
        )
        assertEquals(
            "/sdcard/WhatsVault/recordings/voice_call",
            RecordingStorage.callKindFolder(root, false).absolutePath
        )
    }

    @Test
    fun `legacyBaseDirs selalu memuat Downloads WA Call Recordings`() {
        val dirs = legacy().map { it.absolutePath }
        assertTrue(
            "harus ada Downloads/WA Call Recordings, dapat: $dirs",
            dirs.contains("/sdcard/Download/WA Call Recordings")
        )
    }

    @Test
    fun `legacyBaseDirs memuat folder WhatsApp dan Business`() {
        val dirs = legacy().map { it.absolutePath }
        assertTrue(dirs.contains("/sdcard/Android/data/com.whatsapp/files/Recordings"))
        assertTrue(dirs.contains("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings"))
        assertTrue(dirs.contains("/sdcard/WA Call Recordings"))
    }

    @Test
    fun `legacyBaseDirs memuat configuredPath saat diisi`() {
        val dirs = legacy("/storage/emulated/0/Music/WhatsVault/Recordings")
            .map { it.absolutePath }
        assertTrue(
            "configuredPath harus dipindai, dapat: $dirs",
            dirs.contains("/storage/emulated/0/Music/WhatsVault/Recordings/WA Call Recordings")
        )
    }

    @Test
    fun `legacyBaseDirs melewati configuredPath null atau kosong`() {
        val fromNull = legacy(null).map { it.absolutePath }
        val fromEmpty = legacy("").map { it.absolutePath }
        val fromBlank = legacy("   ").map { it.absolutePath }
        assertEquals(fromNull, fromEmpty)
        assertEquals(fromNull, fromBlank)
        assertTrue(fromNull.none { it.contains("WA Call Recordings/WA Call Recordings") })
    }

    @Test
    fun `legacyBaseDirs tidak menghasilkan duplikat`() {
        val dirs = legacy("/sdcard/Download").map { it.absolutePath }
        assertEquals("tidak boleh ada duplikat: $dirs", dirs.size, dirs.toSet().size)
    }

    @Test
    fun `legacyBaseDirs urutan deterministik`() {
        assertEquals(legacy().map { it.absolutePath }, legacy().map { it.absolutePath })
    }

    @Test
    fun `legacyBaseDirs tidak memuat folder baru`() {
        val dirs = legacy().map { it.absolutePath }
        assertTrue(
            "folder baru tidak boleh muncul sebagai legacy: $dirs",
            dirs.none { it.endsWith(RecordingStorage.VIDEO_DIR_NAME) }
        )
        assertTrue(
            "folder baru tidak boleh muncul sebagai legacy: $dirs",
            dirs.none { it.endsWith(RecordingStorage.VOICE_DIR_NAME) }
        )
    }
}
