@file:Suppress("UNCHECKED_CAST")

package com.mrksvt.waen.xposed.features.general

import android.annotation.SuppressLint
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences
import de.robv.android.xposed.XposedBridge

class PinnedLimit(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    @SuppressLint("DiscouragedApi")
    override fun doHook() {
        if (!prefs.getBoolean(PINNED_LIMIT_PREF_KEY, false)) return

        XposedBridge.hookMethod(
            Unobfuscator.loadPinnedInChatMethod(classLoader),
            XC_MethodReplacement.returnConstant(PINNED_LIMIT_ENABLED)
        )

        XposedBridge.hookMethod(Unobfuscator.loadSetPinnedLimitMethod(classLoader), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (ReflectionUtils.isCalledFromStrings(SYNC_RESPONSE_HANDLER_CLASS_NAME)) {
                    param.result = null
                }
            }
        })

        hookPinnedChatsScope()
    }

    private fun hookPinnedChatsScope() {
        val pinnedHashSetMethod = Unobfuscator.loadPinnedHashSetMethod(classLoader)
        val pinSelectedMethod = Unobfuscator.loadPinSelectedChatsMethod(classLoader)
        val trimExcessMethod = Unobfuscator.loadTrimExcessPinnedChatsMethod(classLoader)
        val pinSelection = ThreadLocal<Set<Any?>>()
        val trimming = ThreadLocal<Boolean>()

        runCatching {
            XposedBridge.hookMethod(
                Unobfuscator.loadPinnedChatsPremiumUpsellMethod(classLoader),
                XC_MethodReplacement.returnConstant(false)
            )
        }.onFailure { logDebug(it) }

        XposedBridge.hookMethod(pinnedHashSetMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val map = param.result as? Map<Any?, Any?> ?: return
                if (trimming.get() == true) {
                    param.result = LinkedHashMap<Any?, Any?>()
                    return
                }
                val selection = pinSelection.get() ?: return
                param.result = LinkedHashMap(map.filterKeys { it in selection })
            }
        })

        XposedBridge.hookMethod(pinSelectedMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val instance = param.thisObject ?: return
                val selectionField = instance.javaClass.declaredFields.firstOrNull { field ->
                    field.isAccessible = true
                    val value = (field.get(instance) as? Set<*>).orEmpty()
                    value.isNotEmpty() && value.all { FMessageWpp.UserJid.TYPE_JID.isInstance(it) }
                } ?: return
                val selection = selectionField.get(instance) as? Set<Any?> ?: return
                if (selection.size <= PIN_BATCH_SIZE) {
                    pinSelection.set(HashSet(selection))
                    return
                }
                try {
                    for (batch in selection.chunked(PIN_BATCH_SIZE)) {
                        val batchSet = LinkedHashSet(batch)
                        selectionField.set(instance, batchSet)
                        pinSelection.set(batchSet)
                        XposedBridge.invokeOriginalMethod(param.method, instance, param.args)
                    }
                } finally {
                    selectionField.set(instance, selection)
                    pinSelection.remove()
                }
                param.result = null
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                pinSelection.remove()
            }
        })

        XposedBridge.hookMethod(trimExcessMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                trimming.set(true)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                trimming.remove()
            }
        })

        runCatching {
            val pinChatHandlerMethod = Unobfuscator.loadPinChatHandlerMethod(classLoader)
            val applyingSync = ThreadLocal<Boolean>()
            XposedBridge.hookMethod(pinChatHandlerMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    applyingSync.set(true)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    applyingSync.remove()
                }
            })
            XposedBridge.hookMethod(Unobfuscator.loadSetPinnedLimitMethod(classLoader), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (applyingSync.get() == true && param.args.getOrNull(2) == false) {
                        param.result = null
                    }
                }
            })
        }.onFailure { logDebug(it) }
    }

    override fun getPluginName(): String = "Pinned Limit"

    private companion object {
        private const val PINNED_LIMIT_PREF_KEY = "pinnedlimit"
        private const val PINNED_LIMIT_ENABLED = 60
        private const val PIN_BATCH_SIZE = 3
        private const val SYNC_RESPONSE_HANDLER_CLASS_NAME = "SyncResponseHandler"
    }
}