package com.mrksvt.waen.update

object UpdateCheckPolicy {
    const val INTERVAL_HOURS = 12L
    const val INTERVAL_MILLIS = INTERVAL_HOURS * 60L * 60L * 1000L

    @JvmStatic
    fun shouldCheckOnLaunch(enabled: Boolean, lastCheckAtMillis: Long, nowMillis: Long): Boolean {
        if (!enabled) return false
        if (lastCheckAtMillis <= 0L) return true
        return nowMillis - lastCheckAtMillis >= INTERVAL_MILLIS
    }

    @JvmStatic
    fun shouldRunPeriodic(enabled: Boolean, networkConnected: Boolean): Boolean =
        enabled && networkConnected
}
