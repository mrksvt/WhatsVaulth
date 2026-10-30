package com.mrksvt.waen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChannelSelectionTest {

    /** Real shape: versionName is `1.6.2 (...)_mrksvt` plus a build suffix. */
    private val installed = "1.6.2-patch2-betaDebug (ABCDEF12)_mrksvt"

    private fun stable(tag: String, hash: String) = ReleaseInfo(
        tag, "WhatsVault $tag", hash, "notes", "https://github.com/x",
        "2026-10-29T10:00:00Z", false, "https://example/apk", 1024L)

    private fun beta(tag: String, hash: String) = ReleaseInfo(
        tag, "WhatsVault $tag", hash, "notes", "https://github.com/x",
        "2026-10-30T10:00:00Z", true, "https://example/apk", 1024L)

    private val newestBeta = beta("1.7.0-99999999", "99999999")
    private val newestStable = stable("1.6.3-55555555", "55555555")
    private val installedRelease = stable("1.6.2-ABCDEF12", "ABCDEF12")

    private val api = listOf(newestBeta, newestStable, installedRelease)

    @Test
    fun releaseChannelNeverSeesPrerelease() {
        val picked = selectChannelReleases(api, UpdateChannel.RELEASE)
        assertEquals(listOf(newestStable, installedRelease), picked)
        assertFalse(picked.contains(newestBeta))
    }

    @Test
    fun prereleaseChannelSeesBothTracks() {
        assertEquals(api, selectChannelReleases(api, UpdateChannel.PRERELEASE))
    }

    @Test
    fun prereleaseCandidateAloneReportsUpdate() {
        val onlyBeta = listOf(newestBeta, installedRelease)
        val candidate = selectChannelReleases(onlyBeta, UpdateChannel.PRERELEASE)
            .first { it.hash.isNotEmpty() }
        assertTrue(isUpdateForChannel(installed, candidate))
    }

    @Test
    fun prereleaseDoesNotTriggerReleaseChannelUpdate() {
        // Newest overall is a beta, but the stable head equals the installed build.
        val cache = listOf(newestBeta, installedRelease)
        val candidate = selectChannelReleases(cache, UpdateChannel.RELEASE)
            .firstOrNull { it.hash.isNotEmpty() }
        assertEquals(installedRelease, candidate)
        assertFalse(isUpdateForChannel(installed, candidate!!))
    }

    @Test
    fun installedBuildIsNotAnUpdate() {
        assertFalse(isUpdateForChannel(installed, installedRelease))
    }

    @Test
    fun hashMatchIsCaseInsensitive() {
        assertFalse(isUpdateForChannel(installed.lowercase(), installedRelease))
        assertFalse(isUpdateForChannel(installed, stable("x", "abcdef12")))
    }

    @Test
    fun hashlessCandidateIsNeverAnUpdate() {
        assertFalse(isUpdateForChannel(installed, stable("no-hash", "")))
    }

    @Test
    fun emptyChannelListYieldsNoCandidate() {
        val onlyBeta = listOf(newestBeta)
        val candidate = selectChannelReleases(onlyBeta, UpdateChannel.RELEASE)
            .firstOrNull { it.hash.isNotEmpty() }
        assertTrue(candidate == null)
    }

    @Test
    fun unknownInstalledVersionStillReportsNewerBuild() {
        // Empty/unknown versionName must not silently swallow a real update.
        assertTrue(isUpdateForChannel("", newestStable))
    }
}
