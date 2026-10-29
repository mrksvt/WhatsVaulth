package com.mrksvt.waen.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkVerificationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val installedPackage = "com.mrksvt.waen"
    private val installedVersion = 162L

    @Test
    fun validApkPassesMetadataCheck() {
        val failure = verifyApkMetadata(
            installedPackage, 170L, installedPackage, installedVersion)
        assertNull(failure)
    }

    @Test
    fun wrongPackageIsRejected() {
        val failure = verifyApkMetadata(
            "com.other.app", 170L, installedPackage, installedVersion)
        assertEquals(InstallFailure.WRONG_PACKAGE, failure)
    }

    @Test
    fun sameVersionCodeIsRejected() {
        val failure = verifyApkMetadata(
            installedPackage, installedVersion, installedPackage, installedVersion)
        assertEquals(InstallFailure.NOT_NEWER, failure)
    }

    @Test
    fun olderVersionCodeIsRejected() {
        val failure = verifyApkMetadata(
            installedPackage, 100L, installedPackage, installedVersion)
        assertEquals(InstallFailure.NOT_NEWER, failure)
    }

    @Test
    fun missingPackageNameIsRejected() {
        val failure = verifyApkMetadata(null, 170L, installedPackage, installedVersion)
        assertEquals(InstallFailure.NOT_AN_APK, failure)
    }

    @Test
    fun corruptFileIsNotAnArchive() {
        val file = folder.newFile("corrupt.apk")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertFalse(isValidApkArchive(file))
    }

    @Test
    fun emptyFileIsNotAnArchive() {
        val file = folder.newFile("empty.apk")
        assertFalse(isValidApkArchive(file))
    }

    @Test
    fun missingFileIsNotAnArchive() {
        assertFalse(isValidApkArchive(File(folder.root, "missing.apk")))
    }

    @Test
    fun zipWithoutManifestIsNotAnArchive() {
        val file = folder.newFile("noManifest.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertFalse(isValidApkArchive(file))
    }

    @Test
    fun zipWithManifestIsAnArchive() {
        val file = folder.newFile("valid.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertTrue(isValidApkArchive(file))
    }

    @Test
    fun matchingSignaturePasses() {
        val failure = verifySignature(setOf("aaa", "bbb"), setOf("bbb"))
        assertNull(failure)
    }

    @Test
    fun differentSignatureIsRejected() {
        val failure = verifySignature(setOf("aaa"), setOf("zzz"))
        assertEquals(InstallFailure.SIGNATURE_MISMATCH, failure)
    }

    @Test
    fun unknownSignatureDoesNotBlock() {
        assertNull(verifySignature(emptySet(), setOf("zzz")))
        assertNull(verifySignature(setOf("aaa"), emptySet()))
    }
}
