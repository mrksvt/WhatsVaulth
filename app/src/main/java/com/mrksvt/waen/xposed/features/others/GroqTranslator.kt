package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.widget.AbsListView
import android.widget.HeaderViewListAdapter
import android.widget.ListAdapter
import android.widget.ListView
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class GroqTranslator(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    private var isWrapping = false

    private fun resolveWrapper(adapter: ListAdapter?): TranslatorWrapperAdapter? {
        if (adapter is TranslatorWrapperAdapter) return adapter
        if (adapter is HeaderViewListAdapter) {
            val wrapped = adapter.wrappedAdapter
            if (wrapped is TranslatorWrapperAdapter) return wrapped
        }
        return null
    }

    override fun doHook() {
        if (!prefs.getBoolean("google_translate", false)) return

        XposedHelpers.findAndHookMethod(
            ListView::class.java,
            "setAdapter",
            ListAdapter::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isWrapping) return

                    val currentActivity = WppCore.getCurrentActivity()
                    if (currentActivity == null ||
                        currentActivity.javaClass.simpleName != "Conversation"
                    ) return

                    val listView = param.thisObject as ListView
                    if (listView.id != android.R.id.list) return

                    val incoming = param.args[0] as? ListAdapter ?: return
                    if (incoming is TranslatorWrapperAdapter) return

                    val wrapper = TranslatorWrapperAdapter(incoming, prefs)
                    isWrapping = true
                    param.args[0] = wrapper
                    isWrapping = false
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            AbsListView::class.java,
            "setSelectionFromTop",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val lv = param.thisObject as? AbsListView ?: return
                    val wrapper = resolveWrapper(lv.adapter) ?: return
                    val pos = param.args[0] as Int
                    if (pos == wrapper.count - 1) {
                        val (isTranslation, _) = wrapper.resolve(pos)
                        if (isTranslation) {
                            param.args[0] = pos - 1
                        }
                    }
                }
            }
        )
    }

    override fun getPluginName(): String = "Groq Translator"
}
