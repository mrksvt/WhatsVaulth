package com.mrksvt.waen.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun disabledNeverChecksOnLaunch() {
        assertFalse(UpdateCheckPolicy.shouldCheckOnLaunch(false, 0L, now))
    }

    @Test
    fun firstLaunchChecksImmediately() {
        assertTrue(UpdateCheckPolicy.shouldCheckOnLaunch(true, 0L, now))
    }

    @Test
    fun recentCheckDoesNotRunAgain() {
        val lastCheck = now - UpdateCheckPolicy.INTERVAL_MILLIS + 1L
        assertFalse(UpdateCheckPolicy.shouldCheckOnLaunch(true, lastCheck, now))
    }

    @Test
    fun elapsedIntervalChecksAgain() {
        val lastCheck = now - UpdateCheckPolicy.INTERVAL_MILLIS
        assertTrue(UpdateCheckPolicy.shouldCheckOnLaunch(true, lastCheck, now))
    }

    @Test
    fun periodicCheckRequiresEnabledPreferenceAndNetwork() {
        assertTrue(UpdateCheckPolicy.shouldRunPeriodic(true, true))
        assertFalse(UpdateCheckPolicy.shouldRunPeriodic(true, false))
        assertFalse(UpdateCheckPolicy.shouldRunPeriodic(false, true))
    }
}
