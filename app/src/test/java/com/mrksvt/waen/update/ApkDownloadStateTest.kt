package com.mrksvt.waen.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkDownloadStateTest {

    @Test
    fun percentIsZeroAtStart() {
        assertEquals(0, progressPercent(0L, 1000L))
    }

    @Test
    fun percentIsHalfwayAtHalfBytes() {
        assertEquals(50, progressPercent(500L, 1000L))
    }

    @Test
    fun percentReachesHundredAtEnd() {
        assertEquals(100, progressPercent(1000L, 1000L))
    }

    @Test
    fun percentNeverExceedsHundred() {
        assertEquals(100, progressPercent(2000L, 1000L))
    }

    @Test
    fun unknownTotalReportsIndeterminate() {
        assertEquals(-1, progressPercent(4096L, -1L))
        assertEquals(-1, progressPercent(4096L, 0L))
    }

    @Test
    fun apkFileNameIsDeterministic() {
        assertEquals("ABC12345.apk", ApkDownloader.apkFileName("ABC12345"))
        assertEquals(
            ApkDownloader.apkFileName("ABC12345"),
            ApkDownloader.apkFileName("ABC12345"))
    }
}
