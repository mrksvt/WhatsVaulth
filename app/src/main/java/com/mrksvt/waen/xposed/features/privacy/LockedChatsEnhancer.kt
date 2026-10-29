package com.mrksvt.waen.xposed.features.privacy

import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.components.FMessageWpp.UserJid
import com.mrksvt.waen.xposed.core.components.WaContactWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadChatCacheClass
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadLoadedContactsMethod
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadLockedChatsMethod
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadNotificationMethod
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class LockedChatsEnhancer(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {
    private var chatCache: Any? = null

    override fun doHook() {
        if (!prefs.getBoolean("lockedchats_enhancer", false)) return

        // Best-effort: WhatsApp moved the locked-chats source out of the notification
        // path on some versions (2.26.41.xx), so discovery can legitimately fail here.
        try {
            val jidNotifications = loadNotificationMethod(classLoader)
            val lockedChatsMethod = loadLockedChatsMethod(classLoader)

            XposedBridge.hookMethod(jidNotifications, object : XC_MethodHook() {

                override fun beforeHookedMethod(param: MethodHookParam) {
                    val unhook = XposedBridge.hookMethod(lockedChatsMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.setResult(ArrayList<Any?>())
                        }
                    })
                    param.setObjectExtra("hook", unhook)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val unhook = param.getObjectExtra("hook") as Unhook?
                    unhook?.unhook()
                }
            })
        } catch (e: Throwable) {
            log("LockedChatsEnhancer: notification suppression unavailable on this version: ${e.message}")
        }

        val chatCacheClass = loadChatCacheClass(classLoader)
        val lockedChatsFields = ReflectionUtils.findAllFieldsUsingFilter(chatCacheClass) {
            f -> f.type == HashSet::class.java
        }
        val lockedChatsField = lockedChatsFields.getOrNull(1) ?: lockedChatsFields.firstOrNull() ?: return

        XposedBridge.hookAllConstructors(chatCacheClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                chatCache = param.thisObject
            }
        })

        val loadedContacts = loadLoadedContactsMethod(classLoader)

        XposedBridge.hookMethod(loadedContacts, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val list = XposedHelpers.getObjectField(param.args[0], "A01") as MutableList<*>
                val cache = chatCache ?: return
                val lockedChats = lockedChatsField.get(cache) as? HashSet<*> ?: return
                if (lockedChats.isEmpty()) return
                val lockedIds = HashSet<String>()
                for (jid in lockedChats) {
                    if (jid == null) continue
                    val userJid = UserJid(jid)
                    userJid.phoneRawString?.let { lockedIds.add(it) }
                    userJid.userRawString?.let { lockedIds.add(it) }
                    userJid.phoneNumber?.let { lockedIds.add(it) }
                }
                if (lockedIds.isEmpty()) return
                list.removeIf { item: Any? ->
                    if (!WaContactWpp.TYPE.isInstance(item)) return@removeIf false
                    val userJid = WaContactWpp(item).userJid
                    listOf(userJid.phoneRawString, userJid.userRawString, userJid.phoneNumber)
                        .any { it != null && it in lockedIds }
                }
            }
        })
    }

    public override fun getPluginName(): String {
        return "Locked Chats Enhancer"
    }
}
