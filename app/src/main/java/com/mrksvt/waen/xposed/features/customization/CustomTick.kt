package com.mrksvt.waen.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject

class CustomTick(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private val drawableCache = HashMap<String, Drawable>()
    private val seenDebugIds = HashSet<Int>()

    override fun getPluginName(): String = "Custom Tick"

    override fun doHook() {
        XposedBridge.log("[CT] doHook called DONATUR=${BuildConfig.DONATUR} enable=${prefs.getBoolean("custom_tick_enable", false)} presetId=${prefs.getLong("custom_tick_active_preset_id", -1L)}")
        if (!prefs.getBoolean("custom_tick_enable", false)) return

        val presetId = prefs.getLong("custom_tick_active_preset_id", -1L)
        if (presetId == -1L) return

        val json = prefs.getString("custom_tick_active_preset_json", null)
        if (json == null) {
            XposedBridge.log("[CT] no preset json in prefs")
            return
        }

        val idToDrawable = HashMap<Int, Drawable>()
        val originalById = HashMap<Int, Drawable>()
        val waRes = Utils.application.resources

        try {
            val obj = JSONObject(json)
            val waPkg = Utils.application.packageName

            val states = listOf(
                Triple("svgPending", "colorPending", listOf("wds_ic_message_waiting", "ic_clock_black_24dp")),
                Triple("svgSent", "colorSent", listOf("wa_ic_send", "wa_ic_check", "message_got_receipt_from_server", "msg_status_server_receive")),
                Triple("svgDelivered", "colorDelivered", listOf("wa_ic_receipt", "wa_ic_check_circle", "vec_ic_receipt_filled", "message_got_receipt_from_target", "msg_status_client")),
                Triple("svgRead", "colorRead", listOf("ic_notif_mark_read", "ic_read", "vec_ic_read", "vec_wds_ic_read", "ic_tick_green_solid", "wa_ic_check_circle_filled")),
                Triple("svgFailed", "colorFailed", listOf("ic_msg_fail", "wa_ic_error", "wa_ic_error_filled", "vec_my_status_error", "message_unsent"))
            )

            for ((svgKey, colorKey, names) in states) {
                val raw = obj.optString(svgKey, "")
                if (raw.isEmpty()) continue
                val color = obj.optInt(colorKey, 0xFFAAAAAA.toInt())
                for (name in names) {
                    val id = waRes.getIdentifier(name, "drawable", waPkg)
                    if (id == 0) continue
                    val original = waRes.getDrawable(id) ?: continue
                    val custom = buildCustom(raw, color, original.intrinsicWidth, original.intrinsicHeight) ?: continue
                    idToDrawable[id] = custom
                    originalById[id] = original
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("[CT] load error: ${e.message}")
        }

        if (idToDrawable.isEmpty()) {
            XposedBridge.log("[CT] nothing to hook")
            return
        }
        XposedBridge.log("[CT] idToDrawable.size=${idToDrawable.size}")

        XposedBridge.hookAllMethods(Resources::class.java, "getDrawable", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val id = param.args[0] as? Int ?: return
                val d = param.result as? Drawable
                if (seenDebugIds.add(id)) {
                    val name = try { waRes.getResourceEntryName(id) } catch (_: Exception) { "?" }
                    XposedBridge.log("[CT-dbg] getDrawable id=$id name=$name cls=${d?.javaClass?.simpleName} size=${d?.intrinsicWidth}x${d?.intrinsicHeight}")
                }
                val custom = idToDrawable[id] ?: return
                param.result = custom.constantState?.newDrawable()?.mutate() ?: custom
                val name = try { waRes.getResourceEntryName(id) } catch (_: Exception) { "?" }
                XposedBridge.log("[CT] getDrawable replaced id=$id name=$name size=${custom.intrinsicWidth}x${custom.intrinsicHeight}")
            }
        })

        XposedBridge.hookAllMethods(ImageView::class.java, "setImageResource", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val id = param.args[0] as? Int ?: return
                val custom = idToDrawable[id] ?: return
                val view = param.thisObject as? ImageView ?: return
                param.result = null
                view.setImageDrawable(custom.constantState?.newDrawable()?.mutate() ?: custom)
                XposedBridge.log("[CT] setImageResource replaced id=$id")
            }
        })

        XposedBridge.hookAllMethods(ImageView::class.java, "setImageDrawable", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val d = param.args[0] as? Drawable ?: return
                val state = unwrapDrawable(d).constantState ?: return
                for ((id, original) in originalById) {
                    val origState = original.constantState ?: continue
                    if (state === origState) {
                        param.args[0] = idToDrawable[id]?.constantState?.newDrawable()?.mutate()
                        XposedBridge.log("[CT] setImageDrawable replaced id=$id")
                        return
                    }
                }
            }
        })
        XposedBridge.log("[CT] hooks installed")
    }

    private fun unwrapDrawable(d: Drawable): Drawable {
        var cur = d
        var guard = 0
        while (cur is DrawableWrapper && guard++ < 8) {
            val child = cur.drawable ?: break
            cur = child
        }
        return cur
    }

    private fun buildCustom(raw: String, color: Int, width: Int, height: Int): Drawable? {
        val sizeW = if (width > 0) width else dp(24)
        val sizeH = if (height > 0) height else dp(24)
        val cacheKey = "${raw.hashCode()}:$color:${sizeW}x$sizeH"
        drawableCache[cacheKey]?.let { return it }
        return try {
            val bitmap = when {
                raw.startsWith("svg:") -> renderSvgFromString(raw.removePrefix("svg:"), sizeW, sizeH)
                raw.startsWith("b64:") -> {
                    val bytes = android.util.Base64.decode(raw.removePrefix("b64:"), android.util.Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                else -> renderSvgFromString(raw, sizeW, sizeH)
            } ?: return null
            val drawable = BitmapDrawable(Utils.application.resources, tintBitmap(bitmap, color))
            drawableCache[cacheKey] = drawable
            drawable
        } catch (e: Exception) {
            XposedBridge.log("[CT] buildCustom failed: ${e.message}")
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * Utils.application.resources.displayMetrics.density).toInt()

    private fun renderSvgFromString(svgContent: String, width: Int, height: Int): Bitmap? {
        return try {
            val svg = SVG.getFromString(svgContent)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            svg.documentWidth = width.toFloat()
            svg.documentHeight = height.toFloat()
            svg.renderToCanvas(Canvas(bmp))
            bmp
        } catch (e: Exception) {
            XposedBridge.log("[CT] renderSvgFromString failed: ${e.message}")
            null
        }
    }

    private fun tintBitmap(source: Bitmap, color: Int): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, 0f, 0f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        paint.color = color
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), paint)
        return result
    }
}
