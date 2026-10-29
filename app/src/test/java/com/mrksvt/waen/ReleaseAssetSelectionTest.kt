package com.mrksvt.waen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetSelectionTest {

    private val plainDebug = ApkAsset(
        "WhatsVault-1.6.2-debug.ABC12345_mrksvt.apk", "https://x/plain-debug.apk", 100L)
    private val businessDebug = ApkAsset(
        "WhatsVault-Business-1.6.2-debug.ABC12345_mrksvt.apk", "https://x/business-debug.apk", 200L)

    @Test
    fun whatsappFlavorNeverPicksBusinessApk() {
        val picked = selectApkAsset(listOf(businessDebug, plainDebug), isBusiness = false)
        assertEquals(plainDebug, picked)
    }

    @Test
    fun businessFlavorNeverPicksPlainApk() {
        val picked = selectApkAsset(listOf(plainDebug, businessDebug), isBusiness = true)
        assertEquals(businessDebug, picked)
    }

    @Test
    fun businessFlavorWithOnlyPlainApkReturnsNull() {
        assertNull(selectApkAsset(listOf(plainDebug), isBusiness = true))
    }

    @Test
    fun releaseWithoutApkReturnsNull() {
        val assets = listOf(
            ApkAsset("WhatsVault-1.6.2-debug.ABC12345_mrksvt.aab", "https://x/a.aab", 1L),
            ApkAsset("checksums.txt", "https://x/c.txt", 2L)
        )
        assertNull(selectApkAsset(assets, isBusiness = false))
        assertNull(selectApkAsset(emptyList(), isBusiness = false))
    }

    @Test
    fun apkWithEmptyUrlIsIgnored() {
        val broken = ApkAsset("WhatsVault-1.6.2-debug.ABC12345_mrksvt.apk", "", 100L)
        assertNull(selectApkAsset(listOf(broken), isBusiness = false))
    }

    @Test
    fun mixedFilenameStillMatchesFlavor() {
        val mixed = ApkAsset(
            "WhatsVault-1.6.2-debug.ABC12345_mrksvt.apk", "https://x/mixed.apk", 42L)
        assertEquals(mixed, selectApkAsset(listOf(mixed), isBusiness = false))
    }

    @Test
    fun businessApplicationIdDetection() {
        assertEquals(true, isBusinessApplicationId("com.mrksvt.waen.w4b"))
        assertEquals(false, isBusinessApplicationId("com.mrksvt.waen"))
    }
}
