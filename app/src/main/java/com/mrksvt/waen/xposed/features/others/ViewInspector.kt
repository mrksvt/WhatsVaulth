package com.mrksvt.waen.xposed.features.others

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class ViewInspector(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private val context: Context = Utils.application

    // windowManager obtained from Activity onResume — null until then
    private var windowManager: WindowManager? = null

    // toggleButton = the draggable FAB-like View added via WindowManager
    private var toggleButton: View? = null

    // infoRootView = the FrameLayout added via WindowManager containing the info TextView
    private var infoRootView: View? = null

    private var isInspectMode = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun doHook() {
        if (!prefs.getBoolean("dev_view_inspector", false) || !BuildConfig.DONATUR) return

        isInspectMode = true
        createToggleButton()
        hookActivityLifecycle()
        hookViewTouch()
    }

    // Build the toggle button View (not added to WindowManager yet — onResume does that)
    @SuppressLint("ClickableViewAccessibility")
    private fun createToggleButton() {
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f

        toggleButton = View(context).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastRawX
                        val dy = event.rawY - lastRawY
                        val wm = windowManager ?: return@setOnTouchListener false
                        val params = (toggleButton?.layoutParams as? WindowManager.LayoutParams)
                            ?: return@setOnTouchListener false
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        wm.updateViewLayout(toggleButton, params)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = Math.abs(event.rawX - downRawX) > 10 ||
                                Math.abs(event.rawY - downRawY) > 10
                        if (!moved) toggleInspectMode()
                        true
                    }
                    else -> false
                }
            }
            // Semi-transparent circle background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isInspectMode) Color.parseColor("#CC2196F3") else Color.parseColor("#CC9E9E9E"))
            }
            isClickable = true
            isFocusable = false
        }
    }

    private fun hookActivityLifecycle() {
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? android.app.Activity ?: return
                    val pkg = try { activity.packageName } catch (_: Exception) { return }
                    if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

                    val wm = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        ?: return
                    windowManager = wm

                    mainHandler.post {
                        val btn = toggleButton ?: return@post
                        if (btn.parent == null) {
                            try {
                                wm.addView(btn, createToggleLayoutParams())
                            } catch (e: Exception) {
                                log(e)
                            }
                        }
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onPause",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? android.app.Activity ?: return
                    val pkg = try { activity.packageName } catch (_: Exception) { return }
                    if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

                    mainHandler.post {
                        cleanupAllViews()
                    }
                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hookViewTouch() {
        XposedHelpers.findAndHookMethod(
            View::class.java,
            "dispatchTouchEvent",
            MotionEvent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isInspectMode) return
                    val event = param.args[0] as? MotionEvent ?: return
                    if (event.action != MotionEvent.ACTION_DOWN) return

                    val view = param.thisObject as? View ?: return
                    val pkg = try { view.context?.packageName } catch (_: Exception) { null }
                    if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

                    // Don't intercept taps on our own overlay views
                    if (view === toggleButton || view === infoRootView ||
                        (infoRootView as? FrameLayout)?.let { view.parent === it } == true
                    ) return

                    mainHandler.post {
                        showViewInfo(view)
                    }
                }
            }
        )
    }

    private fun toggleInspectMode() {
        isInspectMode = !isInspectMode
        // Update button color to reflect state
        (toggleButton?.background as? GradientDrawable)?.setColor(
            if (isInspectMode) Color.parseColor("#CC2196F3") else Color.parseColor("#CC9E9E9E")
        )
        Utils.showToast(
            "Inspect: ${if (isInspectMode) "ON" else "OFF"}",
            Toast.LENGTH_SHORT
        )
        if (!isInspectMode) cleanupInfoView()
    }

    @SuppressLint("SetTextI18n")
    private fun showViewInfo(view: View) {
        cleanupInfoView()
        if (windowManager == null) return

        val infoText = buildViewInfo(view)

        // Build info TextView
        val textView = TextView(context).apply {
            text = infoText
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(24, 16, 24, 16)
        }

        // Rounded semi-opaque background
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(Color.argb(220, 30, 30, 30))
        }

        // Root FrameLayout — THIS is what we add/remove via WindowManager
        val root = FrameLayout(context).apply {
            background = bgDrawable
            addView(textView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER })
            isClickable = true
            isFocusable = false
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    cleanupInfoView()
                    true
                } else false
            }
        }

        infoRootView = root

        try {
            windowManager?.addView(root, createInfoLayoutParams())
        } catch (e: Exception) {
            log(e)
            infoRootView = null
            return
        }

        // Auto-dismiss after 3s
        mainHandler.postDelayed({ cleanupInfoView() }, 3000)
    }

    private fun buildViewInfo(view: View): String {
        val id = view.id
        val resName = if (id == View.NO_ID) "no-id" else {
            try { view.resources.getResourceEntryName(id) }
            catch (_: Exception) { "0x${id.toString(16)}" }
        }
        val resHex = if (id == View.NO_ID) "n/a" else "0x${id.toString(16)}"
        val className = view.javaClass.simpleName
        val tag = view.tag?.toString() ?: "null"

        val parent = view.parent as? android.view.ViewGroup
        val parentClass = parent?.javaClass?.simpleName ?: "null"
        val parentId = parent?.id ?: -1
        val parentResName = if (parentId <= 0) "no-id" else {
            try { view.resources.getResourceEntryName(parentId) }
            catch (_: Exception) { "0x${parentId.toString(16)}" }
        }

        return "ID: $resName ($resHex)\n" +
               "Class: $className\n" +
               "Tag: $tag\n" +
               "Parent: $parentClass ($parentResName)"
    }

    private fun createToggleLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            Utils.dipToPixels(48),
            Utils.dipToPixels(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = Utils.dipToPixels(16)
            y = Utils.dipToPixels(80)
        }
    }

    private fun createInfoLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
    }

    private fun cleanupInfoView() {
        mainHandler.removeCallbacksAndMessages(null)
        infoRootView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        infoRootView = null
    }

    private fun cleanupToggleButton() {
        toggleButton?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        // Don't null toggleButton — keep the View object for re-attach on next onResume
    }

    private fun cleanupAllViews() {
        cleanupInfoView()
        cleanupToggleButton()
    }

    override fun getPluginName(): String = "ViewInspector"
}
