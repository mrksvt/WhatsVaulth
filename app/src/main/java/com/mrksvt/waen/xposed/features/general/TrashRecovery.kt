package com.mrksvt.waen.xposed.features.general

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.db.DelMessageStore
import com.mrksvt.waen.xposed.utils.Utils

class TrashRecovery(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val filter = IntentFilter("com.mrksvt.waen.CLEAR_DELETED_LOG")
        Utils.application.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                DelMessageStore.getInstance(Utils.application).deleteAll()
            }
        }, filter, Context.RECEIVER_EXPORTED)
    }

    override fun getPluginName(): String = "Trash Recovery"
}
