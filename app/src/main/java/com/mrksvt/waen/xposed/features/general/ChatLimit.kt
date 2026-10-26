package com.mrksvt.waen.xposed.features.general

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteTransactionListener
import android.os.Bundle
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore.homeActivityClass
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadChatLimitDelete2Method
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadChatLimitDeleteMethod
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadEphemeralInsertdb
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadFmessageTimestampField
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadSeeMoreConstructor
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference

class ChatLimit(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    // WhatsApp's own msgstore connection; writing through it queues behind WhatsApp's
    // transactions instead of racing them for the file lock like a second connection does
    @Volatile
    private var msgStoreDb: WeakReference<SQLiteDatabase>? = null

    @Volatile
    private var msgStoreHook: XC_MethodHook.Unhook? = null

    override fun doHook() {
        val antiDisappearing = prefs.getBoolean("antidisappearing", false)
        val revokeallmessages = prefs.getBoolean("revokeallmessages", false)

        val chatLimitDeleteMethod = loadChatLimitDeleteMethod(classLoader)
        val chatLimitDelete2Method = loadChatLimitDelete2Method(classLoader)
        val fmessageTimestampMethod = loadFmessageTimestampField(classLoader)

        val epUpdateMethod = loadEphemeralInsertdb(classLoader)

        if (antiDisappearing) {
            captureMsgStoreDatabase()
        } else {
            msgStoreDb = null
        }

        XposedHelpers.findAndHookMethod(
            homeActivityClass,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (antiDisappearing) {
                        keepEphemeralMessages()
                    }
                }
            })

        XposedBridge.hookMethod(epUpdateMethod, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                if (antiDisappearing) {
                    val contentValues = param.result as ContentValues
                    contentValues.put("expire_timestamp", KEEP_EXPIRE_TIMESTAMP)
                }
            }
        })

        if (revokeallmessages) {
            XposedBridge.hookMethod(chatLimitDelete2Method, object : XC_MethodHook() {
                private var unhooked: Unhook? = null

                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val list = ReflectionUtils.findInstancesOfType(
                        param.args,
                        MutableSet::class.java
                    )
                    if (list.isEmpty()) return
                    val listMessages = list[0]!!.second
                    var isExpired = false
                    for (fmessageObj in listMessages) {
                        val timestamp = fmessageTimestampMethod.getLong(fmessageObj)
                        // verify message is expired (max: 3 days)
                        if (System.currentTimeMillis() - timestamp > 3 * 24 * 60 * 60 * 1000) {
                            isExpired = true
                            break
                        }
                    }
                    if (!isExpired) {
                        unhooked = XposedBridge.hookMethod(
                            chatLimitDeleteMethod,
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    if (ReflectionUtils.isCalledFromMethod(chatLimitDelete2Method)) {
                                        param.setResult(0L)
                                    }
                                }
                            })
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (unhooked != null) {
                        unhooked!!.unhook()
                    }
                }
            })
        }

        val seeMoreMethod = loadSeeMoreConstructor(classLoader)

        XposedBridge.hookMethod(seeMoreMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!prefs.getBoolean("removeseemore", false)) return
                param.args[1] = Int.MAX_VALUE
            }
        })
    }

    private fun captureMsgStoreDatabase() {
        if (msgStoreDb != null) return
        val hook = XposedHelpers.findAndHookMethod(
            SQLiteDatabase::class.java,
            "beginTransactionWithListener",
            SQLiteTransactionListener::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (msgStoreDb != null) return
                    val database = param.thisObject as? SQLiteDatabase ?: return
                    if (database.path?.endsWith("/msgstore.db") != true) return
                    msgStoreDb = WeakReference(database)
                    unhook()
                    keepEphemeralMessages()
                }
            })
        this.msgStoreHook = hook
    }

    private fun keepEphemeralMessages() {
        val database = msgStoreDb?.get() ?: return
        Utils.databaseExecutor.execute {
            try {
                if (!database.isOpen) return@execute
                database.execSQL(
                    "UPDATE message_ephemeral SET expire_timestamp = $KEEP_EXPIRE_TIMESTAMP " +
                            "WHERE expire_timestamp IS NULL OR expire_timestamp != $KEEP_EXPIRE_TIMESTAMP"
                )
            } catch (e: Exception) {
                logDebug(e)
            }
        }
    }

    private fun unhook() {
        msgStoreHook?.unhook()
        msgStoreHook = null
    }

    override fun getPluginName(): String {
        return "Chat Limit"
    }

    companion object {
        private const val KEEP_EXPIRE_TIMESTAMP = 2553512370000L
    }
}
