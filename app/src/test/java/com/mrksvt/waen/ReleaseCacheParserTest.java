package com.mrksvt.waen;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseCacheParserTest {

    private static final String OLD_ENTRY =
            "{\"tag\":\"1.6.1-A969C54C\",\"title\":\"WhatsVault 1.6.1\",\"hash\":\"A969C54C\","
                    + "\"changelog\":\"Fixes\",\"url\":\"https://github.com/x\","
                    + "\"published\":\"2026-10-01T10:00:00Z\",\"prerelease\":false}";

    private static final String NEW_ENTRY =
            "{\"tag\":\"1.6.2-B2331EBC\",\"title\":\"WhatsVault 1.6.2\",\"hash\":\"B2331EBC\","
                    + "\"changelog\":\"Notes\",\"url\":\"https://github.com/y\","
                    + "\"published\":\"2026-10-29T10:00:00Z\",\"prerelease\":false,"
                    + "\"apkUrl\":\"https://example/apk\",\"apkSize\":12345}";

    @Test
    public void oldJsonWithoutApkFieldsStillParses() {
        List<ReleaseInfo> releases = ReleaseCacheParser.parseReleases("[" + OLD_ENTRY + "]");
        assertEquals(1, releases.size());
        ReleaseInfo release = releases.get(0);
        assertEquals("1.6.1-A969C54C", release.tagName);
        assertEquals("A969C54C", release.hash);
        assertEquals("", release.apkUrl);
        assertEquals(0L, release.apkSize);
    }

    @Test
    public void newJsonKeepsApkMetadata() {
        List<ReleaseInfo> releases = ReleaseCacheParser.parseReleases("[" + NEW_ENTRY + "]");
        assertEquals(1, releases.size());
        assertEquals("https://example/apk", releases.get(0).apkUrl);
        assertEquals(12345L, releases.get(0).apkSize);
    }

    @Test
    public void mixedCacheKeepsStoredOrder() {
        List<ReleaseInfo> releases =
                ReleaseCacheParser.parseReleases("[" + NEW_ENTRY + "," + OLD_ENTRY + "]");
        assertEquals(2, releases.size());
        assertEquals("B2331EBC", releases.get(0).hash);
        assertEquals("A969C54C", releases.get(1).hash);
        assertEquals("", releases.get(1).apkUrl);
    }

    @Test
    public void negativeApkSizeIsClamped() {
        String entry = "{\"hash\":\"ABCDEF12\",\"apkSize\":-5}";
        assertEquals(0L, ReleaseCacheParser.parseReleases("[" + entry + "]").get(0).apkSize);
    }

    @Test
    public void missingSizeFallsBackToZero() {
        String entry = "{\"hash\":\"ABCDEF12\",\"apkSize\":\"not-a-number\"}";
        assertEquals(0L, ReleaseCacheParser.parseReleases("[" + entry + "]").get(0).apkSize);
    }

    @Test
    public void blankAndNullInputReturnEmptyList() {
        assertTrue(ReleaseCacheParser.parseReleases("").isEmpty());
        assertTrue(ReleaseCacheParser.parseReleases(null).isEmpty());
        assertTrue(ReleaseCacheParser.parseReleases("   ").isEmpty());
    }

    @Test
    public void corruptCacheReturnsEmptyList() {
        assertTrue(ReleaseCacheParser.parseReleases("[{broken").isEmpty());
        assertTrue(ReleaseCacheParser.parseReleases("not json").isEmpty());
        assertTrue(ReleaseCacheParser.parseReleases("[1,2,3]").isEmpty());
    }

    @Test
    public void escapedCharactersSurvive() {
        String entry = "{\"hash\":\"ABCDEF12\",\"changelog\":\"line1\\nline2 \\\"quoted\\\" \\u00e9\"}";
        ReleaseInfo release = ReleaseCacheParser.parseReleases("[" + entry + "]").get(0);
        assertEquals("line1\nline2 \"quoted\" \u00e9", release.changelog);
    }

    @Test
    public void emptyArrayIsEmpty() {
        assertTrue(ReleaseCacheParser.parseReleases("[]").isEmpty());
    }
}
