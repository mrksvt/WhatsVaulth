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
        multiSelectionLimitInfoClass.declaredConstructors.forEach { constructor ->
            val intIndexes = constructor.parameterTypes.indices.filter {
                constructor.parameterTypes[it] == Int::class.javaPrimitiveType
            }
            val booleanIndexes = constructor.parameterTypes.indices.filter {
                constructor.parameterTypes[it] == Boolean::class.javaPrimitiveType
            }
            if (intIndexes.isEmpty()) return@forEach
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val limitIndex = intIndexes.firstOrNull { (param.args[it] as Int) ushr 24 == 0 }
                    if (limitIndex != null) param.args[limitIndex] = Int.MAX_VALUE
                    booleanIndexes.forEach { param.args[it] = false }
                }
            })
        }
    }

    override fun getPluginName(): String = "Share Limit"
}