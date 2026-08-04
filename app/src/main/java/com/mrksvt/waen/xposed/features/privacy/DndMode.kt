package com.mrksvt.waen.xposed.features.privacy

import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore.getPrivBoolean
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadDndModeMethod
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge

class DndMode(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (!getPrivBoolean("dndmode", false)) return
        val dndMethod = loadDndModeMethod(classLoader)
        logDebug(getMethodDescriptor(dndMethod))
        XposedBridge.hookMethod(dndMethod, XC_MethodReplacement.DO_NOTHING)
    }

    override fun getPluginName(): String {
        return "Dnd Mode"
    }
}
