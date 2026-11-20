package com.mrksvt.waen.xposed.core

object RuntimeLogPolicy {
    const val PREF_KEY = "runtime_file_logs"
    const val RELATIVE_DIRECTORY = "WhatsVault/logs"

    @JvmStatic
    fun shouldStart(isDebug: Boolean, enabled: Boolean): Boolean = isDebug && enabled

    @JvmStatic
    fun publicLogDirectory(externalStorageRoot: String): String =
        externalStorageRoot.trimEnd('/') + "/" + RELATIVE_DIRECTORY
}
