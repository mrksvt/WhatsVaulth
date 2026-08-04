package com.mrksvt.waen.provider

import com.crossbowffs.remotepreferences.RemotePreferenceProvider
import com.mrksvt.waen.BuildConfig

class RemotePreferenceProvider : RemotePreferenceProvider(
    BuildConfig.APPLICATION_ID + ".preferences",
    arrayOf(BuildConfig.APPLICATION_ID + "_preferences")
) {
    override fun checkAccess(prefFileName: String, prefKey: String, write: Boolean): Boolean {
        return !write
    }
}