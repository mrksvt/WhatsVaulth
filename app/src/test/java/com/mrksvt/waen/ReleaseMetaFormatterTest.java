package com.mrksvt.waen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Meta line of a release row: hash, date and the update track.
 */
public class ReleaseMetaFormatterTest {

    private static final String HASH = "26CB54F0";
    private static final String ISO = "2026-11-08T03:47:07Z";
    private static final String LABEL = "Prerelease";

    @Test
    public void stableRowShowsHashAndDate() {
        assertEquals(HASH + "  \u00B7  Nov 08, 2026",
                ReleaseMetaFormatter.format(HASH, ISO, false, LABEL));
    }

    @Test
    public void prereleaseRowAlsoShowsTheTrack() {
        assertEquals(HASH + "  \u00B7  Nov 08, 2026  \u00B7  Prerelease",
                ReleaseMetaFormatter.format(HASH, ISO, true, LABEL));
    }

    @Test
    public void missingDateKeepsHashAndTrack() {
        assertEquals(HASH + "  \u00B7  Prerelease",
                ReleaseMetaFormatter.format(HASH, "", true, LABEL));
        assertEquals(HASH + "  \u00B7  Prerelease",
                ReleaseMetaFormatter.format(HASH, null, true, LABEL));
    }

    @Test
    public void malformedDateIsDropped() {
        assertEquals(HASH + "  \u00B7  Prerelease",
                ReleaseMetaFormatter.format(HASH, "not-a-date", true, LABEL));
    }

    @Test
    public void blankLabelIsNotAppended() {
        assertEquals(HASH + "  \u00B7  Nov 08, 2026",
                ReleaseMetaFormatter.format(HASH, ISO, true, "  "));
        assertEquals(HASH + "  \u00B7  Nov 08, 2026",
                ReleaseMetaFormatter.format(HASH, ISO, true, null));
    }

    @Test
    public void blankHashStillShowsDate() {
        assertEquals("Nov 08, 2026", ReleaseMetaFormatter.format("", ISO, false, LABEL));
        assertEquals("", ReleaseMetaFormatter.format("", "", false, LABEL));
    }

    @Test
    public void formatDateReturnsEmptyForBadInput() {
        assertEquals("", ReleaseMetaFormatter.formatDate(null));
        assertEquals("", ReleaseMetaFormatter.formatDate(""));
        assertEquals("", ReleaseMetaFormatter.formatDate("2026-11-08"));
        assertFalse(ReleaseMetaFormatter.formatDate(ISO).isEmpty());
        assertTrue(ReleaseMetaFormatter.formatDate(ISO).contains("2026"));
    }
}
