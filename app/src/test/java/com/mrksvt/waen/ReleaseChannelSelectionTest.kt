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

    private val both = setOf(UpdateChannel.RELEASE, UpdateChannel.PRERELEASE)

    @Test
    fun releaseChannelNeverSeesPrerelease() {
        val picked = selectChannelReleases(api, UpdateChannel.RELEASE)
        assertEquals(listOf(newestStable, installedRelease), picked)
        assertFalse(picked.contains(newestBeta))
    }

    @Test
    fun prereleaseChannelSeesOnlyPrereleases() {
        assertEquals(listOf(newestBeta), selectChannelReleases(api, UpdateChannel.PRERELEASE))
    }

    @Test
    fun followingBothTracksSeesEveryRelease() {
        assertEquals(api, selectFollowedReleases(api, both))
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
        val updates = channelUpdates(cache, both, installed)
        assertEquals(setOf(UpdateChannel.PRERELEASE), updates.keys)
        assertEquals(newestBeta, updates[UpdateChannel.PRERELEASE])
    }

    @Test
    fun followingBothReportsOneUpdatePerOutdatedTrack() {
        val cache = listOf(newestBeta, newestStable, installedRelease)
        val updates = channelUpdates(cache, both, installed)
        assertEquals(newestStable, updates[UpdateChannel.RELEASE])
        assertEquals(newestBeta, updates[UpdateChannel.PRERELEASE])
    }

    @Test
    fun followingBothWithNoUpdateReportsNothing() {
        val cache = listOf(installedRelease)
        assertTrue(channelUpdates(cache, both, installed).isEmpty())
    }

    @Test
    fun followingOneTrackIgnoresTheOther() {
        val cache = listOf(newestBeta, newestStable, installedRelease)
        assertEquals(setOf(UpdateChannel.RELEASE),
            channelUpdates(cache, setOf(UpdateChannel.RELEASE), installed).keys)
        assertEquals(setOf(UpdateChannel.PRERELEASE),
            channelUpdates(cache, setOf(UpdateChannel.PRERELEASE), installed).keys)
    }

    @Test
    fun installedBuildIsNotAnUpdate() {
        assertFalse(isUpdateForChannel(installed, installedRelease))
        assertTrue(channelUpdates(listOf(installedRelease), both, installed).isEmpty())
    }

    @Test
    fun olderReleaseIsNotOfferedToNewerBuild() {
        // Regression: installed 1.6.4 must never be told to update to the older 1.6.3.
        val installed164 = "1.6.4 (26CB54F0)_mrksvt"
        assertFalse(isUpdateForChannel(installed164, newestStable))
        assertTrue(channelUpdates(api, setOf(UpdateChannel.RELEASE), installed164).isEmpty())
    }

    @Test
    fun newerReleaseIsStillOffered() {
        val installed163 = "1.6.3 (B906F98A)_mrksvt"
        assertTrue(isUpdateForChannel(installed163, stable("v1.6.4-77777777", "77777777")))
    }

    @Test
    fun sameVersionButDifferentBuildIsAnUpdate() {
        // A rebuild of the same version with another commit is still a new build.
        val installed164 = "1.6.4 (26CB54F0)_mrksvt"
        assertTrue(isUpdateForChannel(installed164, stable("v1.6.4-17A15D3E", "17A15D3E")))
    }

    @Test
    fun newerPrereleaseIsStillAnnouncedToANewerStableBuild() {
        // Installed 1.6.4 must not be offered the older 1.6.3, but a 1.7.0 beta
        // is newer and stays a valid update.
        val installed164 = "1.6.4 (26CB54F0)_mrksvt"
        val updates = channelUpdates(api, both, installed164)
        assertEquals(setOf(UpdateChannel.PRERELEASE), updates.keys)
        assertEquals(newestBeta, updates[UpdateChannel.PRERELEASE])
    }

    @Test
    fun hashMatchIsCaseInsensitive() {
        assertFalse(isUpdateForChannel(installed.lowercase(), installedRelease))
        assertFalse(isUpdateForChannel(installed, stable("x", "abcdef12")))
    }

    @Test
    fun hashlessCandidateIsNeverAnUpdate() {
        assertFalse(isUpdateForChannel(installed, stable("no-hash", "")))
        assertTrue(channelUpdates(listOf(stable("no-hash", "")), both, installed).isEmpty())
    }

    @Test
    fun emptyChannelListYieldsNoCandidate() {
        val onlyBeta = listOf(newestBeta)
        val candidate = selectChannelReleases(onlyBeta, UpdateChannel.RELEASE)
            .firstOrNull { it.hash.isNotEmpty() }
        assertTrue(candidate == null)
    }

    @Test
    fun emptyFollowSetYieldsNoCandidate() {
        assertTrue(channelUpdates(api, emptySet(), installed).isEmpty())
    }

    @Test
    fun unknownInstalledVersionStillReportsNewerBuild() {
        // Empty/unknown versionName must not silently swallow a real update.
        assertTrue(isUpdateForChannel("", newestStable))
    }

    @Test
    fun allChannelsCoversEveryTrack() {
        assertEquals(setOf(UpdateChannel.RELEASE, UpdateChannel.PRERELEASE),
            UpdateChannel.allChannels())
    }

    @Test
    fun candidateIsKnownEvenWhenTheTrackIsNotFollowedYet() {
        // Regression: the check computes the candidate of every track, so following a
        // track later on does not have to wait for another network round.
        val cache = listOf(newestBeta, newestStable, installedRelease)
        val updates = channelUpdates(cache, UpdateChannel.allChannels(), installed)
        assertEquals(setOf(UpdateChannel.RELEASE, UpdateChannel.PRERELEASE), updates.keys)
        assertEquals(newestStable, updates[UpdateChannel.RELEASE])
        assertEquals(newestBeta, updates[UpdateChannel.PRERELEASE])
    }
}
