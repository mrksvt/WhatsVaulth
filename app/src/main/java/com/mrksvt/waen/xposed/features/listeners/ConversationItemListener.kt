package com.mrksvt.waen.xposed.features.listeners

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HeaderViewListAdapter
import android.widget.ListAdapter
import android.widget.ListView
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.features.others.TranslatorWrapperAdapter
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.xposed.core.HookOverrideStore
import com.mrksvt.waen.xposed.utils.Utils
import java.util.WeakHashMap

class ConversationItemListener(
    loader: ClassLoader,
    preferences:SharedPreferences
) : Feature(loader, preferences) {

    data class BoundConversationItem(
        val messageId: String,
        val rowId: Long,
        val message: FMessageWpp
    )

    companion object {
        private const val FIELD_BOUND_MESSAGE_ID = "conversation_item_bound_message_id"

        @JvmField
        val conversationListeners = HashSet<OnConversationItemListener>()

        var adapter: ListAdapter? = null

        @JvmField
        val listItems = WeakHashMap<View, BoundConversationItem>()

        private var hooked: XC_MethodHook.Unhook? = null

        @JvmStatic
        fun notifyDataSetChanged() {
            Handler(Looper.getMainLooper()).post {
                (adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }

        fun getBoundMessageId(view: View): String? {
            return XposedHelpers.getAdditionalInstanceField(view, FIELD_BOUND_MESSAGE_ID) as? String
        }

        fun isViewBoundToMessage(view: View, messageId: String): Boolean {
            return getBoundMessageId(view) == messageId
        }

        private fun bindViewToMessage(view: View, fMessage: FMessageWpp): BoundConversationItem {
            val boundItem = BoundConversationItem(
                messageId = fMessage.key.messageID,
                rowId = fMessage.rowId,
                message = fMessage
            )
            XposedHelpers.setAdditionalInstanceField(view, FIELD_BOUND_MESSAGE_ID, boundItem.messageId)
            listItems[view] = boundItem
            return boundItem
        }
    }

    @Throws(Throwable::class)
    override fun doHook() {
        WppCore.addListenerActivity { activity, type ->
            if (activity.javaClass.simpleName == "Conversation" && type == WppCore.ActivityChangeState.ChangeType.DESTROYED)
                hooked?.unhook()
        }

        val setAdapterHook = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val currentActivity = WppCore.getCurrentActivity()
                if (currentActivity == null || currentActivity.javaClass.simpleName != "Conversation") return

                val listView = param.thisObject as? ListView ?: return
                if (listView.id != android.R.id.list) return

                var currentAdapter = param.args[0] as? ListAdapter
                if (currentAdapter is HeaderViewListAdapter) {
                    currentAdapter = currentAdapter.wrappedAdapter
                }
                if (currentAdapter is TranslatorWrapperAdapter) {
                    currentAdapter = currentAdapter.realAdapter
                }
                if (currentAdapter == null) return

                adapter = currentAdapter

                for (listener in conversationListeners) {
                    listener.onAttachAdapter(adapter)
                }

                val wrapperAdapter = TranslatorWrapperAdapter.getOrCreateForRealAdapter(currentAdapter, prefs)
                param.args[0] = wrapperAdapter

                hooked?.unhook()

                Handler(Looper.getMainLooper()).post {
                    wrapperAdapter.attachListViewObserver(listView)
                }

                val method = try {
                    adapter!!.javaClass.getDeclaredMethod(
                        "getView",
                        Int::class.javaPrimitiveType,
                        View::class.java,
                        ViewGroup::class.java
                    )
                } catch (_: NoSuchMethodException) {
                    adapter!!.javaClass.getMethod(
                        "getView",
                        Int::class.javaPrimitiveType,
                        View::class.java,
                        ViewGroup::class.java
                    )
                }

                hooked = XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.thisObject !== adapter) return

                        val position = param.args[0] as Int
                        val convertView = param.args[1] as? View
                        val viewGroup = param.result as? ViewGroup ?: return

                        XposedBridge.log("[WAE_CIL] getView pos=$position thisObj=${param.thisObject?.javaClass?.simpleName}")
                        val fMessageObj = adapter!!.getItem(position) ?: return
                        XposedBridge.log("[WAE_CIL] fMessageObj class=${fMessageObj.javaClass.simpleName}")
                        val fMessage = FMessageWpp(fMessageObj)

                        bindViewToMessage(viewGroup, fMessage)

                        for (listener in conversationListeners) {
                            try {
                                listener.onItemBind(fMessage, viewGroup, position, convertView)
                            } catch (e: Throwable) {
                                logDebug(e)
                            }
                        }
                    }
                })
            }
        }

        if (com.mrksvt.waen.BuildConfig.DONATUR) {
            val ctx = Utils.application.applicationContext
            val override = HookOverrideStore.getMethodOverride(ctx, "conversation_item_bind")
            if (override != null) {
                try {
                    val clazz = classLoader.loadClass(override.first)
                    XposedBridge.hookAllMethods(clazz, override.second, setAdapterHook)
                    XposedHelpers.findAndHookMethod(ListView::class.java, "setAdapter", ListAdapter::class.java, setAdapterHook)
                    return
                } catch (e: Exception) {
                    XposedBridge.log("[WAE_CIL] dynamic hook failed: ${e.message}, fallback to static")
                }
            }
        }

        XposedHelpers.findAndHookMethod(ListView::class.java, "setAdapter", ListAdapter::class.java, setAdapterHook)
    }

    override fun getPluginName(): String {
        return "Conversation Item Listener"
    }

    abstract class OnConversationItemListener {
        /**
         * Called when a message item is rendered in the conversation
         *
         * @param fMessage The message
         * @param view     The view associated with the item
         * @param position The position
         * @param convertView The view from the adapter
         * @throws Throwable Errors caught in the hook
         */
        @Throws(Throwable::class)
        abstract fun onItemBind(
            fMessage: FMessageWpp,
            view: ViewGroup,
            position: Int,
            convertView: View?
        )

        open fun onAttachAdapter(adapter: ListAdapter?) {
            // TODO
        }
    }
}
