package com.mrksvt.waen.xposed.core

import android.view.View
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Satu hook View dipakai bersama oleh banyak feature.
 * Tiap feature mendaftarkan listener, dispatcher yang hooking View lifecycle
 * hanya sekali lalu memanggil semua listener.
 */
object ViewHookDispatcher {

    fun interface ViewListener {
        fun onView(view: View)
    }

    fun interface SetFlagsFilter {
        fun filter(view: View, flags: Int, mask: Int): Int
    }

    fun interface MeasureListener {
        fun onMeasured(view: FrameLayout)
    }

    class DeferredSetFlags internal constructor(
        private val filter: SetFlagsFilter
    ) {
        @Volatile
        private var active = false

        fun activate() {
            if (active) return
            synchronized(this) {
                if (active) return
                onSetFlags(filter)
                active = true
            }
        }
    }

    private val attachListeners = CopyOnWriteArrayList<ViewListener>()
    private val detachListeners = CopyOnWriteArrayList<ViewListener>()
    private val setFlagsFilters = CopyOnWriteArrayList<SetFlagsFilter>()
    private val frameMeasureListeners = CopyOnWriteArrayList<MeasureListener>()

    @Volatile
    private var attachHooked = false

    @Volatile
    private var detachHooked = false

    @Volatile
    private var setFlagsHooked = false

    @Volatile
    private var frameMeasureHooked = false

    fun onAttachedToWindow(listener: ViewListener) {
        attachListeners.add(listener)
        if (attachHooked) return
        synchronized(this) {
            if (attachHooked) return
            hookViewLifecycle("onAttachedToWindow", attachListeners)
            attachHooked = true
        }
    }

    fun onDetachedFromWindow(listener: ViewListener) {
        detachListeners.add(listener)
        if (detachHooked) return
        synchronized(this) {
            if (detachHooked) return
            hookViewLifecycle("onDetachedFromWindow", detachListeners)
            detachHooked = true
        }
    }

    fun onSetFlags(filter: SetFlagsFilter) {
        setFlagsFilters.add(filter)
        if (setFlagsHooked) return
        synchronized(this) {
            if (setFlagsHooked) return
            val intType: Class<*> = Int::class.javaPrimitiveType!!
            val method = runCatching {
                View::class.java.getDeclaredMethod("setFlags", intType, intType)
            }.getOrNull() ?: return
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val original = param.args[0] as Int
                    val mask = param.args[1] as Int
                    var flags = original
                    for (f in setFlagsFilters) {
                        try {
                            flags = f.filter(view, flags, mask)
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                    if (flags != original) param.args[0] = flags
                }
            })
            setFlagsHooked = true
        }
    }

    fun deferSetFlags(filter: SetFlagsFilter) = DeferredSetFlags(filter)

    fun onFrameLayoutMeasured(listener: MeasureListener) {
        frameMeasureListeners.add(listener)
        if (frameMeasureHooked) return
        synchronized(this) {
            if (frameMeasureHooked) return
            val intType: Class<*> = Int::class.javaPrimitiveType!!
            val method = runCatching {
                FrameLayout::class.java.getDeclaredMethod("onMeasure", intType, intType)
            }.getOrNull() ?: return
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? FrameLayout ?: return
                    for (l in frameMeasureListeners) {
                        try {
                            l.onMeasured(view)
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            })
            frameMeasureHooked = true
        }
    }

    private fun hookViewLifecycle(methodName: String, listeners: List<ViewListener>) {
        val method = runCatching { View::class.java.getDeclaredMethod(methodName) }.getOrNull() ?: runCatching { View::class.java.getMethod(methodName) }.getOrNull() ?: return
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                for (l in listeners) {
                    try {
                        l.onView(view)
                    } catch (t: Throwable) {
                        XposedBridge.log(t)
                    }
                }
            }
        })
    }
}
