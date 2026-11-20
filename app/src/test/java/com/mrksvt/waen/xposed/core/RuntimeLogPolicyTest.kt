package com.mrksvt.waen.xposed.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogPolicyTest {
    @Test
    fun `file logging only starts for debug build with toggle enabled`() {
        assertTrue(RuntimeLogPolicy.shouldStart(isDebug = true, enabled = true))
        assertFalse(RuntimeLogPolicy.shouldStart(isDebug = true, enabled = false))
        assertFalse(RuntimeLogPolicy.shouldStart(isDebug = false, enabled = true))
    }

    @Test
    fun `public log path never uses Android media fallback`() {
        assertEquals(
            "/storage/emulated/0/WhatsVault/logs",
            RuntimeLogPolicy.publicLogDirectory("/storage/emulated/0")
        )
        assertFalse(
            RuntimeLogPolicy.publicLogDirectory("/storage/emulated/0")
                .contains("Android/media")
        )
    }
}
