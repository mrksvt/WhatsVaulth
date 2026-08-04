package com.mrksvt.waen.xposed.features.general

import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge

class ShareLimit(classLoader: ClassLoader, prefs:SharedPreferences) : Feature(classLoader, prefs) {

    override fun doHook() {
        if(!prefs.getBoolean("removeforwardlimit", false)) return
        val multiSelectionLimitInfoClass = Unobfuscator.loadMultiSelectionLimitInfoClass(classLoader)
        XposedBridge.hookAllConstructors(multiSelectionLimitInfoClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = Int.MAX_VALUE
            }
        })
    }

    override fun getPluginName(): String = "Share Limit"
}