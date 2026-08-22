package com.mrksvt.waen.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Typeface
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.io.File

class CustomFont(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        @Volatile
        var cachedTypeface: Typeface? = null

        @Volatile
        var cachedPresetJson: String? = null
    }

    override fun getPluginName(): String = "Custom Font"

    override fun doHook() {
        if (!BuildConfig.DONATUR) return
        if (!prefs.getBoolean("custom_font_enable", false)) return

        val presetJson = prefs.getString("custom_font_active_preset_json", null)
        if (presetJson == null) {
            XposedBridge.log("[CustomFont] no active preset json in prefs")
            return
        }

        val typeface: Typeface? = try {
            loadTypefaceFromJson(presetJson)
        } catch (e: Throwable) {
            XposedBridge.log("[CustomFont] Failed to load typeface: ${e.message}")
            null
        }

        if (typeface == null) {
            XposedBridge.log("[CustomFont] Typeface is null, skipping hook")
            return
        }

        cachedTypeface = typeface
        cachedPresetJson = presetJson

        // Hook setTypeface(Typeface)
        try {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView",
                classLoader,
                "setTypeface",
                Typeface::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cached = getActiveTypeface() ?: return
                        val pkg = param.thisObject?.javaClass?.name ?: return
                        if (!pkg.startsWith("com.whatsapp")) return
                        param.args[0] = cached
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[CustomFont] Failed to hook setTypeface(Typeface): ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView",
                classLoader,
                "setTypeface",
                Typeface::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cached = getActiveTypeface() ?: return
                        val pkg = param.thisObject?.javaClass?.name ?: return
                        if (!pkg.startsWith("com.whatsapp")) return
                        param.args[0] = cached
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[CustomFont] Failed to hook setTypeface(Typeface,int): ${e.message}")
        }
    }

    private fun loadTypefaceFromJson(json: String): Typeface? {
        val obj = JSONObject(json)
        val source = obj.optString("source", "bundled")
        val bundledName = obj.optString("bundledName", "")
        val customPath = obj.optString("customPath", "")

        return when (source) {
            "custom" -> {
                if (customPath.isNotEmpty()) {
                    val file = File(customPath)
                    if (file.exists()) Typeface.createFromFile(file) else null
                } else null
            }
            else -> {
                if (bundledName.isNotEmpty()) {
                    Typeface.createFromAsset(Utils.application.assets, "fonts/$bundledName")
                } else null
            }
        }
    }

    private fun getActiveTypeface(): Typeface? {
        val current = prefs.getString("custom_font_active_preset_json", null)
        if (current != null && current != cachedPresetJson) {
            cachedPresetJson = current
            cachedTypeface = try {
                loadTypefaceFromJson(current)
            } catch (e: Throwable) {
                XposedBridge.log("[CustomFont] reload failed: ${e.message}")
                null
            }
        }
        return cachedTypeface
    }
}
