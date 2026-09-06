package com.mrksvt.waen.xposed.features.general

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

class CaptureDevice(
    loader: ClassLoader,
    preferences: android.content.SharedPreferences
) : Feature(loader, preferences) {

    override fun getPluginName(): String = "CaptureDevice"

    override fun doHook() {
        if (!prefs.getBoolean("capture_device_enable", false)) {
            logDebug("CaptureDevice disabled")
            return
        }

        try {
            hookDeviceIndicator()
        } catch (t: Throwable) {
            logDebug("Failed to hook CaptureDevice: ${t.message}", t)
        }
    }

    private fun hookDeviceIndicator() {
        try {
            val targetClass = classLoader.loadClass("com.whatsapp.conversation.ConversationMessage")
            val methods = targetClass.declaredMethods
            for (method in methods) {
                if (method.parameterTypes.any { param -> param == TextView::class.java } &&
                    method.parameterTypes.any { param -> param == ImageView::class.java }) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? View ?: return
                            showDeviceInfo(view, param)
                        }
                    })
                    logDebug("CaptureDevice hook installed on method: ${method.name}")
                    break
                }
            }
        } catch (e: Exception) {
            log("CaptureDevice hook failed: ${e.message}")
        }
    }

    private fun showDeviceInfo(view: View, param: XC_MethodHook.MethodHookParam) {
        try {
            val message = XposedHelpers.getObjectField(param.thisObject, "message") ?: return
            val isLinkedDevice = checkLinkedDevice(message)
            if (isLinkedDevice) {
                val iconView = view.findViewById<ImageView>(android.R.id.icon)
                iconView?.setImageResource(getLinkedDeviceIcon())
                iconView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            // Fallback: silent
        }
    }

    private fun checkLinkedDevice(message: Any): Boolean {
        return try {
            val fromLinkedDevice = XposedHelpers.getBooleanField(message, "fromLinkedDevice")
            fromLinkedDevice
        } catch (e: Exception) {
            false
        }
    }

    private fun getLinkedDeviceIcon(): Int {
        return android.R.drawable.ic_menu_share
    }
}
