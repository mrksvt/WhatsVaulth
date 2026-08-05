package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.ListView
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.features.listeners.ConversationItemListener
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class GroqTranslator(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    private var isWrapping = false

    override fun doHook() {
        if (!prefs.getBoolean("google_translate", false)) return

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {

            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                if (position % 2 != 0) return
            }
        })

        XposedHelpers.findAndHookMethod(
            ListView::class.java,
            "setAdapter",
            ListAdapter::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
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
                    listView.adapter = wrapper
                    isWrapping = false
                }
            }
        )
    }

    override fun getPluginName(): String {
        return "Groq Translator"
    }
}
