package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.view.View
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DevEngineering(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    companion object {
        const val LOG_PATH = "/data/data/com.mrksvt.waen/files/wae_dev_log.txt"
    }

    override fun doHook() {
        if (!prefs.getBoolean("dev_engineering", false)) return

        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        XposedBridge.hookAllMethods(View::class.java, "performClick", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                try {
                    if (view.context?.packageName != "com.whatsapp") return
                } catch (_: Exception) { return }
                val id = view.id
                val resName = if (id == View.NO_ID) "no-id" else {
                    try { view.resources.getResourceEntryName(id) }
                    catch (e: Exception) { "0x${id.toString(16)}" }
                }
                val className = view.javaClass.simpleName
                val tag = view.tag?.toString() ?: "null"
                val timestamp = fmt.format(Date(System.currentTimeMillis()))
                val parent = (view.parent as? android.view.ViewGroup)
                val parentClass = parent?.javaClass?.simpleName ?: "null"
                val parentId = parent?.id ?: -1
                val parentResName = if (parentId <= 0) "no-id" else {
                    try { view.resources.getResourceEntryName(parentId) }
                    catch (e: Exception) { "0x${parentId.toString(16)}" }
                }
                val entry = "$timestamp | id=$resName | class=$className | tag=$tag | parent=$parentClass(id=$parentResName)"

                try {
                    val bridge = WppCore.getClientBridge() ?: return
                    val pfd = bridge.openFile(LOG_PATH, true) ?: return
                    val existing = try {
                        java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().readLines()
                    } catch (_: Exception) { emptyList() }
                    val lines = (listOf(entry) + existing).take(100)
                    val out = FileOutputStream(pfd.fileDescriptor)
                    out.write(lines.joinToString("\n").toByteArray())
                    out.flush()
                    out.close()
                    pfd.close()
                } catch (_: Exception) {}
            }
        })
    }

    override fun getPluginName(): String = "DevEngineering"
}
