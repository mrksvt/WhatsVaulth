package com.mrksvt.waen.xposed.features.privacy

import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method

class TypingPrivacy(
    loader: ClassLoader,
    preferences:SharedPreferences
) : Feature(loader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        val ghostmode = WppCore.getPrivBoolean("ghostmode", false)
        val ghostmodeT = prefs.getBoolean("ghostmode_t", false)
        val ghostmodeR = prefs.getBoolean("ghostmode_r", false)

        val method: Method = Unobfuscator.loadGhostModeMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(method))

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val type = ReflectionUtils.getArg(param.args, Int::class.javaObjectType, 0)
                val jidObj = ReflectionUtils.getArg(param.args, FMessageWpp.UserJid.TYPE_JID, 0)

                if (jidObj == null) {
                    logDebug("UserJid not found in Typing Privacy")
                }

                val userJid = FMessageWpp.UserJid(jidObj)
                val privacy = CustomPrivacy.getJSON(userJid.phoneNumber)

                val customHideTyping = privacy.optBoolean("HideTyping", ghostmodeT) || ghostmode
                val customHideRecording = privacy.optBoolean("HideRecording", ghostmodeR) || ghostmode

                if ((type == 1 && customHideRecording) ||
                    (type == 0 && customHideTyping)
                ) {
                    param.result = null
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Typing Privacy"
    }
}