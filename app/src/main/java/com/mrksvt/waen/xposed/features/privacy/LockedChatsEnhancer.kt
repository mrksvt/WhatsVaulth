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
import java.lang.reflect.Field
import java.util.stream.Collectors

class LockedChatsEnhancer(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {
    private var chatCache: Any? = null

    override fun doHook() {
        if (!prefs.getBoolean("lockedchats_enhancer", false)) return

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

        val chatCacheClass = loadChatCacheClass(classLoader)
        val lockedChatsFields = ReflectionUtils.findAllFieldsUsingFilter(chatCacheClass) {
            f -> f.type == HashSet::class.java
        }

        XposedBridge.hookAllConstructors(chatCacheClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                chatCache = param.thisObject
            }
        })

        val loadedContacts = loadLoadedContactsMethod(classLoader)

        XposedBridge.hookMethod(loadedContacts, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val list = XposedHelpers.getObjectField(param.args[0], "A01") as MutableList<*>
                val lockedChats = lockedChatsFields[1]!!.get(chatCache) as HashSet<*>?
                val lockedNumbers = lockedChats!!.stream()
                    .map<String?> { userjid: Any? -> UserJid(userjid).phoneNumber }.collect(
                        Collectors.toList()
                    )
                list.removeIf { item: Any? ->
                    if (!WaContactWpp.TYPE.isInstance(item)) return@removeIf false
                    val waContact = WaContactWpp(item)
                    val phoneNumber = waContact.userJid.phoneNumber
                    lockedNumbers.contains(phoneNumber)
                }
            }
        })
    }

    public override fun getPluginName(): String {
        return "Locked Chats Enhancer"
    }
}
