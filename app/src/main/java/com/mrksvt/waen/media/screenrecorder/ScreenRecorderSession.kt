package com.mrksvt.waen.media.screenrecorder

import java.util.UUID

/** Create on the service worker; readers may observe immutable snapshots from any thread. */
class ScreenRecorderSession {
    data class Id(val value: UUID = UUID.randomUUID())

    enum class State {
        IDLE, REQUESTING, STARTING, RECORDING, STOPPING, SAVED, ERROR
    }

    enum class StopReason {
        USER_INTERFACE,
        NOTIFICATION,
        PROJECTION_REVOKED,
        SCREEN_LOCKED,
        SERVICE_DESTROYED,
        STORAGE_LIMIT,
        FAILURE
    }

    data class Snapshot(
        val state: State = State.IDLE,
        val sessionId: Id? = null,
        val stopReason: StopReason? = null,
        val result: ScreenRecorderResult? = null
    ) {
        val isActive: Boolean
            get() = state == State.REQUESTING || state == State.STARTING ||
                state == State.RECORDING || state == State.STOPPING
    }

    private val ownerThread = Thread.currentThread()

    @Volatile
    var snapshot: Snapshot = Snapshot()
        private set

    /** Null means an existing session still owns capture, including consent and finalization. */
    fun requestStart(): Id? {
        checkOwner()
        if (snapshot.isActive) return null
        val id = Id()
        snapshot = Snapshot(state = State.REQUESTING, sessionId = id)
        return id
    }

    fun cancelConsent(sessionId: Id): Boolean {
        checkOwner()
        if (!matches(sessionId, State.REQUESTING)) return false
        snapshot = Snapshot()
        return true
    }

    fun consentGranted(sessionId: Id): Boolean {
        checkOwner()
        if (!matches(sessionId, State.REQUESTING)) return false
        snapshot = snapshot.copy(state = State.STARTING)
        return true
    }

    fun recordingStarted(sessionId: Id): Boolean {
        checkOwner()
        if (!matches(sessionId, State.STARTING)) return false
        snapshot = snapshot.copy(state = State.RECORDING)
        return true
    }

    /** Only the first accepted stop may schedule finalization; its reason is retained. */
    fun requestStop(sessionId: Id, reason: StopReason): Boolean {
        checkOwner()
        if (!matches(sessionId, State.STARTING) && !matches(sessionId, State.RECORDING)) {
            return false
        }
        snapshot = snapshot.copy(state = State.STOPPING, stopReason = reason)
        return true
    }

    /** The engine supplies Saved only after finalization, validation and publication succeed. */
    fun completeSaved(result: ScreenRecorderResult.Saved): Boolean {
        checkOwner()
        if (!matches(result.sessionId, State.STOPPING)) return false
        snapshot = snapshot.copy(state = State.SAVED, result = result)
        return true
    }

    /** Call after failed startup or after the worker finishes cleanup of a failed capture. */
    fun completeFailed(result: ScreenRecorderResult.Failed): Boolean {
        checkOwner()
        if (snapshot.sessionId != result.sessionId || !snapshot.isActive) return false
        snapshot = snapshot.copy(
            state = State.ERROR,
            stopReason = snapshot.stopReason ?: StopReason.FAILURE,
            result = result
        )
        return true
    }

    private fun matches(sessionId: Id, state: State): Boolean =
        snapshot.sessionId == sessionId && snapshot.state == state

    private fun checkOwner() {
        check(Thread.currentThread() === ownerThread) {
            "Screen recorder transitions must run on the owning worker"
        }
    }
}
