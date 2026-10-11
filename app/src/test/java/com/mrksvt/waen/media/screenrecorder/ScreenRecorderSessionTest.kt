package com.mrksvt.waen.media.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecorderSessionTest {

    private val saved = ScreenRecorderResult.Saved(
        sessionId = ScreenRecorderSession.Id(),
        outputPath = "/tmp/x.mp4",
        durationUs = 1_000_000L,
        videoSampleCount = 30L,
        width = 720,
        height = 1280
    )

    private fun failedFor(id: ScreenRecorderSession.Id) = ScreenRecorderResult.Failed(
        sessionId = id,
        error = ScreenRecorderResult.Error.CODEC_WRITE
    )

    @Test
    fun `happy path reaches saved`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()
        assertNotNull(id)
        assertEquals(ScreenRecorderSession.State.REQUESTING, session.snapshot.state)

        assertTrue(session.consentGranted(id!!))
        assertEquals(ScreenRecorderSession.State.STARTING, session.snapshot.state)

        assertTrue(session.recordingStarted(id))
        assertEquals(ScreenRecorderSession.State.RECORDING, session.snapshot.state)

        assertTrue(session.requestStop(id, ScreenRecorderSession.StopReason.USER_INTERFACE))
        assertEquals(ScreenRecorderSession.State.STOPPING, session.snapshot.state)

        assertTrue(
            session.completeSaved(
                saved.copy(sessionId = id)
            )
        )
        assertEquals(ScreenRecorderSession.State.SAVED, session.snapshot.state)
    }

    @Test
    fun `duplicate start rejected while active`() {
        val session = ScreenRecorderSession()
        assertNotNull(session.requestStart())
        assertNull(session.requestStart())
    }

    @Test
    fun `restart allowed after saved`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        session.consentGranted(id)
        session.recordingStarted(id)
        session.requestStop(id, ScreenRecorderSession.StopReason.NOTIFICATION)
        session.completeSaved(saved.copy(sessionId = id))

        assertNotNull(session.requestStart())
    }

    @Test
    fun `cancel consent returns to idle`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        assertTrue(session.cancelConsent(id))
        assertEquals(ScreenRecorderSession.State.IDLE, session.snapshot.state)
        assertNull(session.snapshot.sessionId)
    }

    @Test
    fun `commands from previous session are rejected`() {
        val session = ScreenRecorderSession()
        val first = session.requestStart()!!
        session.cancelConsent(first)

        val second = session.requestStart()!!
        assertFalse(session.consentGranted(first))
        assertFalse(session.recordingStarted(first))
        assertTrue(session.consentGranted(second))
    }

    @Test
    fun `double stop is rejected after first accepted`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        session.consentGranted(id)
        session.recordingStarted(id)

        assertTrue(session.requestStop(id, ScreenRecorderSession.StopReason.USER_INTERFACE))
        assertFalse(session.requestStop(id, ScreenRecorderSession.StopReason.NOTIFICATION))
    }

    @Test
    fun `stop before recording is rejected`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        assertFalse(session.requestStop(id, ScreenRecorderSession.StopReason.USER_INTERFACE))
    }

    @Test
    fun `saved only accepted from stopping`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        session.consentGranted(id)
        session.recordingStarted(id)
        assertFalse(session.completeSaved(saved.copy(sessionId = id)))
        assertEquals(ScreenRecorderSession.State.RECORDING, session.snapshot.state)
    }

    @Test
    fun `failure at each active phase is terminal`() {
        val phases = listOf<(ScreenRecorderSession, ScreenRecorderSession.Id) -> Unit>(
            { s, id -> Unit },
            { s, id -> s.consentGranted(id) },
            { s, id -> s.consentGranted(id); s.recordingStarted(id) }
        )
        for (advance in phases) {
            val session = ScreenRecorderSession()
            val id = session.requestStart()!!
            advance(session, id)
            assertTrue(session.completeFailed(failedFor(id)))
            assertEquals(ScreenRecorderSession.State.ERROR, session.snapshot.state)
        }
    }

    @Test
    fun `completeFailed rejects unknown session`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        assertFalse(session.completeFailed(failedFor(ScreenRecorderSession.Id())))
        assertEquals(ScreenRecorderSession.State.REQUESTING, session.snapshot.state)
    }

    @Test
    fun `off worker writes are rejected`() {
        val session = ScreenRecorderSession()
        val id = session.requestStart()!!
        var rejected = false
        val thread = Thread {
            rejected = runCatching { session.consentGranted(id) }.isFailure
        }
        thread.start()
        thread.join()
        assertTrue("transitions must be owner-thread only", rejected)
        assertEquals(ScreenRecorderSession.State.REQUESTING, session.snapshot.state)
    }

    @Test
    fun `invalid saved metadata rejected at construction`() {
        assertFalse(
            runCatching {
                ScreenRecorderResult.Saved(
                    sessionId = ScreenRecorderSession.Id(),
                    outputPath = "",
                    durationUs = 1L,
                    videoSampleCount = 1L,
                    width = 2,
                    height = 2
                )
            }.isSuccess
        )
        assertFalse(
            runCatching {
                ScreenRecorderResult.Saved(
                    sessionId = ScreenRecorderSession.Id(),
                    outputPath = "/x.mp4",
                    durationUs = 0L,
                    videoSampleCount = 1L,
                    width = 2,
                    height = 2
                )
            }.isSuccess
        )
        assertFalse(
            runCatching {
                ScreenRecorderResult.Saved(
                    sessionId = ScreenRecorderSession.Id(),
                    outputPath = "/x.mp4",
                    durationUs = 1L,
                    videoSampleCount = 0L,
                    width = 2,
                    height = 2
                )
            }.isSuccess
        )
    }
}
