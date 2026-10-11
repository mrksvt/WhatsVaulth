package com.mrksvt.waen.media.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScreenRecordingValidatorTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private fun meta(
        mime: String? = ScreenRecordingValidator.AVC_MIME,
        width: Int = 1280,
        height: Int = 720,
        durationUs: Long = 5_000_000L,
        sampleCount: Long = 150L,
        sizeBytes: Long = 1024L
    ) = ScreenRecordingValidator.VideoMetadata(mime, width, height, durationUs, sampleCount, sizeBytes)

    @Test
    fun `accepts valid avc video`() {
        assertNull(ScreenRecordingValidator.validate(meta()))
    }

    @Test
    fun `rejects missing metadata`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(null)
        )
    }

    @Test
    fun `rejects zero byte file`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(sizeBytes = 0L))
        )
    }

    @Test
    fun `rejects audio only`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(mime = "audio/mp4a-latm"))
        )
    }

    @Test
    fun `rejects zero sample recording`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(sampleCount = 0L))
        )
    }

    @Test
    fun `rejects non positive dimensions`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(width = 0, height = 720))
        )
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(width = 1280, height = 0))
        )
    }

    @Test
    fun `rejects non positive duration`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(meta(durationUs = 0L))
        )
    }

    @Test
    fun `rejects absurd duration`() {
        assertEquals(
            ScreenRecorderResult.Error.OUTPUT_VALIDATION,
            ScreenRecordingValidator.validate(
                meta(durationUs = ScreenRecordingValidator.MAX_DURATION_US + 1)
            )
        )
    }
}

class ScreenRecorderStorageTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private val dir get() = ScreenRecorderStorage.outputDir(temp.root)

    @Test
    fun `builds file name with extension and suffix`() {
        val name = ScreenRecorderStorage.buildFileName("20260101_120000", "abcd")
        assertEquals("sr_20260101_120000_abcd.mp4", name)
    }

    @Test
    fun `staging and final file live in same dir`() {
        val staging = ScreenRecorderStorage.stagingFile(dir, "a.mp4")
        val finalFile = ScreenRecorderStorage.finalFile(dir, "a.mp4")
        assertEquals(dir, staging.parentFile)
        assertEquals(dir, finalFile.parentFile)
        assertTrue(staging.name.endsWith(ScreenRecorderStorage.STAGING_SUFFIX))
    }

    @Test
    fun `publish moves staging to final`() {
        dir.mkdirs()
        val staging = ScreenRecorderStorage.stagingFile(dir, "a.mp4")
        staging.writeBytes(ByteArray(32) { 1 })
        val finalFile = ScreenRecorderStorage.finalFile(dir, "a.mp4")

        assertTrue(ScreenRecorderStorage.publish(staging, finalFile))
        assertFalse(staging.exists())
        assertTrue(finalFile.exists())
        assertEquals(32L, finalFile.length())
    }

    @Test
    fun `publish refuses empty staging and keeps no final`() {
        dir.mkdirs()
        val staging = ScreenRecorderStorage.stagingFile(dir, "a.mp4")
        staging.createNewFile()
        val finalFile = ScreenRecorderStorage.finalFile(dir, "a.mp4")

        assertFalse(ScreenRecorderStorage.publish(staging, finalFile))
        assertFalse(finalFile.exists())
    }

    @Test
    fun `publish refuses missing staging`() {
        dir.mkdirs()
        val finalFile = ScreenRecorderStorage.finalFile(dir, "a.mp4")
        assertFalse(
            ScreenRecorderStorage.publish(
                ScreenRecorderStorage.stagingFile(dir, "a.mp4"),
                finalFile
            )
        )
    }

    @Test
    fun `failed publish does not destroy previous valid result`() {
        dir.mkdirs()
        val previous = ScreenRecorderStorage.finalFile(dir, "old.mp4")
        previous.writeBytes(ByteArray(16) { 2 })
        ScreenRecorderStorage.writeLastResult(dir, previous)

        val emptyStaging = ScreenRecorderStorage.stagingFile(dir, "new.mp4")
        emptyStaging.createNewFile()
        val newFinal = ScreenRecorderStorage.finalFile(dir, "new.mp4")
        assertFalse(ScreenRecorderStorage.publish(emptyStaging, newFinal))

        assertTrue(previous.exists())
        assertEquals(previous, ScreenRecorderStorage.readLastResult(dir))
    }

    @Test
    fun `last result round trip`() {
        dir.mkdirs()
        val result = ScreenRecorderStorage.finalFile(dir, "x.mp4")
        result.writeBytes(ByteArray(8) { 3 })

        assertTrue(ScreenRecorderStorage.writeLastResult(dir, result))
        assertEquals(result, ScreenRecorderStorage.readLastResult(dir))
    }

    @Test
    fun `last result is null when pointer target deleted`() {
        dir.mkdirs()
        val result = ScreenRecorderStorage.finalFile(dir, "gone.mp4")
        result.writeBytes(ByteArray(8) { 4 })
        ScreenRecorderStorage.writeLastResult(dir, result)

        assertTrue(result.delete())
        assertNull(ScreenRecorderStorage.readLastResult(dir))
    }

    @Test
    fun `lists partials sorted`() {
        dir.mkdirs()
        ScreenRecorderStorage.stagingFile(dir, "b.mp4").writeBytes(ByteArray(4))
        ScreenRecorderStorage.stagingFile(dir, "a.mp4").writeBytes(ByteArray(4))
        ScreenRecorderStorage.finalFile(dir, "ok.mp4").writeBytes(ByteArray(4))

        val partials = ScreenRecorderStorage.listPartials(dir)
        assertEquals(2, partials.size)
        assertEquals(listOf("a.mp4.partial", "b.mp4.partial"), partials.map { it.name })
    }

    @Test
    fun `listPartials on missing dir is empty`() {
        assertTrue(ScreenRecorderStorage.listPartials(File(temp.root, "nope")).isEmpty())
    }
}
