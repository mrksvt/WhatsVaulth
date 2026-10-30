package com.mrksvt.waen;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UpdateChannelTest {

    private static ReleaseInfo release(String tag, boolean prerelease) {
        return new ReleaseInfo(tag, "WhatsVault " + tag, "ABCDEF12", "notes",
                "https://github.com/x", "2026-10-30T10:00:00Z", prerelease,
                "https://example/apk", 1024L);
    }

    private static ReleaseInfo stable(String tag) {
        return release(tag, false);
    }

    private static ReleaseInfo beta(String tag) {
        return release(tag, true);
    }

    @Test
    public void defaultChannelIsRelease() {
        assertSame(UpdateChannel.RELEASE, UpdateChannel.defaultChannel());
    }

    @Test
    public void keysAreStableTokens() {
        assertEquals("release", UpdateChannel.RELEASE.key());
        assertEquals("prerelease", UpdateChannel.PRERELEASE.key());
    }

    @Test
    public void fromKeyParsesEveryKnownKey() {
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("release"));
        assertSame(UpdateChannel.PRERELEASE, UpdateChannel.fromKey("prerelease"));
    }

    @Test
    public void fromKeyIsNullSafeAndTrimmed() {
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey(null));
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey(""));
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("   "));
        assertSame(UpdateChannel.PRERELEASE, UpdateChannel.fromKey("  prerelease \n"));
    }

    @Test
    public void fromKeyIsCaseInsensitiveAndExact() {
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("Release"));
        assertSame(UpdateChannel.PRERELEASE, UpdateChannel.fromKey("Prerelease"));
        // Must be an exact token match, never a prefix match.
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("releases"));
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("pre-release"));
    }

    @Test
    public void corruptValueFallsBackToGivenDefault() {
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("beta"));
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey("{\"x\":1}"));
        assertSame(UpdateChannel.PRERELEASE,
                UpdateChannel.fromKey("nonsense", UpdateChannel.PRERELEASE));
        assertSame(UpdateChannel.RELEASE, UpdateChannel.fromKey(null, null));
    }

    @Test
    public void releaseChannelAcceptsOnlyStableReleases() {
        assertTrue(UpdateChannel.RELEASE.accepts(stable("1.6.2-AAAABBBB")));
        assertFalse(UpdateChannel.RELEASE.accepts(beta("1.7.0-CCCCDDDD")));
        assertFalse(UpdateChannel.RELEASE.accepts(null));
    }

    @Test
    public void prereleaseChannelAcceptsBothTracks() {
        assertTrue(UpdateChannel.PRERELEASE.accepts(stable("1.6.2-AAAABBBB")));
        assertTrue(UpdateChannel.PRERELEASE.accepts(beta("1.7.0-CCCCDDDD")));
        assertFalse(UpdateChannel.PRERELEASE.accepts(null));
    }

    @Test
    public void filterKeepsStoredOrder() {
        ReleaseInfo newestStable = stable("1.6.2-AAAABBBB");
        ReleaseInfo newestBeta = beta("1.7.0-CCCCDDDD");
        ReleaseInfo olderStable = stable("1.6.1-EEEEFFFF");
        List<ReleaseInfo> cached = Arrays.asList(newestBeta, newestStable, olderStable);

        List<ReleaseInfo> stableOnly = UpdateChannel.RELEASE.filter(cached);
        assertEquals(2, stableOnly.size());
        assertSame(newestStable, stableOnly.get(0));
        assertSame(olderStable, stableOnly.get(1));

        List<ReleaseInfo> everything = UpdateChannel.PRERELEASE.filter(cached);
        assertEquals(3, everything.size());
        assertSame(newestBeta, everything.get(0));
    }

    @Test
    public void filterDropsPrereleaseEntriesFromReleaseChannel() {
        ReleaseInfo entry = beta("1.7.0-CCCCDDDD");
        assertTrue(UpdateChannel.RELEASE.filter(Collections.singletonList(entry)).isEmpty());
        assertEquals(1, UpdateChannel.PRERELEASE.filter(Collections.singletonList(entry)).size());
    }

    @Test
    public void filterToleratesNullOrEmptyInput() {
        assertNotNull(UpdateChannel.RELEASE.filter(null));
        assertTrue(UpdateChannel.RELEASE.filter(null).isEmpty());
        assertTrue(UpdateChannel.PRERELEASE.filter(Collections.<ReleaseInfo>emptyList()).isEmpty());
        assertTrue(UpdateChannel.RELEASE.filter(Arrays.asList((ReleaseInfo) null)).isEmpty());
    }

    @Test
    public void legacyEntryWithoutPrereleaseFlagCountsAsStable() {
        // Releases parsed from a cache written before the prerelease flag was
        // introduced default to false, so they must stay visible on RELEASE.
        ReleaseInfo legacy = new ReleaseInfo("1.5.5-A94A2CE3", "", "A94A2CE3", "",
                "", "", false, "", 0L);
        assertTrue(UpdateChannel.RELEASE.accepts(legacy));
    }
}
