package com.mrksvt.waen.xposed.features.customization

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.widget.BaseAdapter
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.components.WaContactWpp
import com.mrksvt.waen.xposed.core.db.MessageStore
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.core.devkit.UnobfuscatorCache
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import com.mrksvt.waen.xposed.utils.WaeCoroutineExceptionHandler
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.function.Predicate
import java.util.regex.Pattern

class SeparateGroup(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        const val CHATS = 200
        const val STATUS = 300
        const val GROUPS = 500

        @JvmField
        var tabs = ArrayList<Int>()
        var tabInstances = HashMap<Int, Any>()

        fun resolveUserJid(chat: Any): FMessageWpp.UserJid? {
            try {
                val clazz = chat.javaClass
                val waContactField = ReflectionUtils.getFieldByExtendType(clazz, WaContactWpp.TYPE)
                if (waContactField != null) {
                    val waContactObj = waContactField.get(chat)
                    if (waContactObj != null) {
                        val waContact = WaContactWpp(waContactObj)
                        val userJid = waContact.userJid
                        if (!userJid.isNull) {
                            return userJid
                        }
                    }
                }
                val userJidField =
                    ReflectionUtils.getFieldByExtendType(clazz, FMessageWpp.UserJid.TYPE_JID)
                if (userJidField != null) {
                    val jidObject = userJidField.get(chat)
                    val userJid = FMessageWpp.UserJid(jidObject)
                    if (!userJid.isNull) {
                        return userJid
                    }
                }

                var jid = getObjectField(chat, "A00")
                if (jid == null) {
                    jid = getObjectField(chat, "A01")
                }
                if (jid != null) {
                    val userJid = FMessageWpp.UserJid(jid)
                    if (!userJid.isNull) {
                        return userJid
                    }
                }

                return null
            } catch (_: Throwable) {
                return null
            }
        }

    }

    override fun doHook() {
        hookMaxItemCount()

        if (!prefs.getBoolean("separategroups", false)) return

        // Anchor wajib harus resolve SEBELUM tab GROUPS disuntikkan ke daftar tab.
        // Di 2.26.46 method daftar chat pindah dari ConversationsFragment ke
        // ConversationsFragmentKt sehingga loadTabFragmentMethod gagal. Dulu kegagalan itu
        // terjadi setelah hookTabList() menyuntikkan id 500 dan sebelum hookTabName()
        // terpasang: id 500 jadi tidak punya judul/fragment dan WhatsApp melempar
        // IllegalArgumentException di HomeActivity.onCreate.
        val tabNameMethod = runCatching { Unobfuscator.loadTabNameMethod(classLoader) }
            .onFailure { logDebug("SeparateGroup: tab name anchor missing, feature disabled: ${it.message}") }
            .getOrNull() ?: return
        val getTabMethod = runCatching { Unobfuscator.loadGetTabMethod(classLoader) }
            .onFailure { logDebug("SeparateGroup: get tab anchor missing, feature disabled: ${it.message}") }
            .getOrNull() ?: return
        val conversationsFragmentClass = runCatching {
            Unobfuscator.loadConversationsFragmentClass(classLoader)
        }
            .onFailure { logDebug("SeparateGroup: ConversationsFragment class missing, feature disabled: ${it.message}") }
            .getOrNull() ?: return

        // Setting group tab name
        hookTabName(tabNameMethod)

        // Setting up fragments (judul + fragment harus ada sebelum tab ditambah)
        hookTabInstance(getTabMethod, conversationsFragmentClass)

        // Jaring pengaman terakhir sebelum id tab GROUPS masuk ke daftar tab.
        guardTabValidators(conversationsFragmentClass)

        // Modifying tab list order
        runCatching { hookTabList() }
            .onFailure { logDebug("SeparateGroup: tab list hook failed: ${it.message}") }

        // Setting group icon
        runCatching { hookTabIcon() }
            .onFailure { logDebug("SeparateGroup: tab icon hook failed: ${it.message}") }

        // Setting tab count
        runCatching { hookTabCount() }
            .onFailure { logDebug("SeparateGroup: tab count hook failed: ${it.message}") }
    }

    override fun getPluginName(): String {
        return "Separate Group"
    }

    private fun hookMaxItemCount() {
        val bottomNavigationViewCls = sequenceOf(
            ".BottomNavigationView",
            "HomeLegacyBottomNavigationView"
        ).mapNotNull { suffix ->
            runCatching {
                Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, suffix)
            }.getOrNull()
        }.firstOrNull()
        if (bottomNavigationViewCls == null) {
            logDebug("SeparateGroup: BottomNavigationView class not found")
            return
        }
        // WhatsApp 2.26.43+: HomeLegacyBottomNavigationView does not declare getMaxItemCount,
        // it is inherited from the base bottom bar class. Hook the declaring class instead.
        val declaring = generateSequence(bottomNavigationViewCls) { it.superclass }
            .firstOrNull { cls ->
                runCatching { cls.getDeclaredMethod("getMaxItemCount") }.isSuccess
            }
        if (declaring == null) {
            logDebug("SeparateGroup: getMaxItemCount not declared in ${bottomNavigationViewCls.name} hierarchy")
            return
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                declaring,
                "getMaxItemCount",
                XC_MethodReplacement.returnConstant(99)
            )
        }.onFailure { logDebug("SeparateGroup: getMaxItemCount hook failed: ${it.message}") }
    }

    /**
     * Jaring pengaman untuk validator tab WhatsApp. Method validator melempar
     * `IllegalArgumentException("The item position should be less or equal to: " + jumlah tab)`
     * untuk id tab yang tidak dikenal. Kalau ada anchor yang bergeser dan id tab tambahan
     * (500) tidak ter-map, lemparan itu terjadi di HomeActivity.onCreate dan aplikasi tidak
     * bisa dibuka. Guard ini hanya mengganti hasil pada kasus yang memang akan melempar.
     */
    private fun guardTabValidators(chatFragmentClass: Class<*>?) {
        val validatorClass = runCatching { Unobfuscator.loadTabValidatorClass(classLoader) }
            .onFailure { logDebug("SeparateGroup: tab validator anchor missing: ${it.message}") }
            .getOrNull() ?: return

        val fragmentType = runCatching {
            classLoader.loadClass("androidx.fragment.app.Fragment")
        }.getOrNull()

        var hooked = 0
        for (method in validatorClass.declaredMethods) {
            if (method.parameterCount != 1) continue
            if (method.parameterTypes[0] != Int::class.javaPrimitiveType) continue
            val returnsTitle = method.returnType == String::class.java
            val returnsFragment =
                fragmentType != null && fragmentType.isAssignableFrom(method.returnType)
            if (!returnsTitle && !returnsFragment) continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Hook lain (mis. hookTabName/hookTabInstance untuk id 500) sudah
                        // mengisi hasil. Jangan sentuh id bawaan WhatsApp supaya tidak ada
                        // instance fragment ganda pada tab normal.
                        if (param.result != null) return
                        val arg = param.args[0] as? Int ?: return
                        val tabId = if (arg == GROUPS) GROUPS else resolveTabId(arg)
                        if (tabId != GROUPS) return
                        try {
                            param.result = XposedBridge.invokeOriginalMethod(
                                param.method,
                                param.thisObject,
                                param.args
                            )
                        } catch (t: Throwable) {
                            logDebug(
                                "SeparateGroup: id tab $tabId ditolak validator WhatsApp " +
                                        "(${t.message}), nilai cadangan dipakai"
                            )
                            param.result = if (returnsTitle) "" else newChatFragment(chatFragmentClass)
                        }
                    }
                })
                hooked++
            }.onFailure { logDebug("SeparateGroup: validator hook failed: ${it.message}") }
        }
        logDebug("SeparateGroup: tab validator guard ${validatorClass.name} ($hooked method)")
    }

    private fun newChatFragment(chatFragmentClass: Class<*>?): Any? {
        val cls = chatFragmentClass ?: return null
        return runCatching {
            cls.declaredConstructors.firstOrNull { it.parameterCount == 0 }?.newInstance()
        }.getOrNull()
    }

    /**
     * Sebagian versi memanggil accessor tab dengan posisi, versi lain dengan id tab.
     * Jangan pernah mengindeks daftar tab dengan id tab (IndexOutOfBounds di callback hook).
     */
    private fun resolveTabId(arg: Int): Int {
        if (tabs.contains(arg)) return arg
        return tabs.getOrNull(arg) ?: arg
    }

    private fun hookTabCount() {
        val runMethod = Unobfuscator.loadTabCountMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(runMethod))

        val enableCountMethod = Unobfuscator.loadEnableCountTabMethod(classLoader)
        val badgeWrapperConstructor = Unobfuscator.loadEnableCountTabBadgeWrapper(classLoader)
        val badgeItemConstructor = Unobfuscator.loadEnableCountTabBadgeItem(classLoader)
        val emptyBadgeClass = Unobfuscator.loadEnableCountTabEmptyBadgeClass(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(enableCountMethod))

        XposedBridge.hookMethod(enableCountMethod, object : XC_MethodHook() {
            @SuppressLint("Range", "Recycle")
            override fun beforeHookedMethod(param: MethodHookParam) {
                val indexTab = param.args[2] as Int
                if (indexTab != tabs.indexOf(CHATS)) return

                param.result = null

                CoroutineScope(Dispatchers.IO + WaeCoroutineExceptionHandler).launch {
                    val unseenChatCounts = getUnseenChatCounts()
                    withContext(Dispatchers.Main) {
                        if (tabs.contains(CHATS) && tabInstances.containsKey(CHATS)) {
                            val chatsBadge = if (unseenChatCounts.chatCount <= 0) {
                                XposedHelpers.getStaticObjectField(emptyBadgeClass, "A00")
                            } else {
                                val params = ReflectionUtils.initArray(badgeWrapperConstructor.parameterTypes)
                                params[0] = badgeItemConstructor.newInstance(unseenChatCounts.chatCount)
                                badgeWrapperConstructor.newInstance(*params)
                            }
                            XposedBridge.invokeOriginalMethod(
                                param.method,
                                param.thisObject,
                                arrayOf(param.args[0], chatsBadge, tabs.indexOf(CHATS))
                            )
                        }
                        if (tabs.contains(GROUPS) && tabInstances.containsKey(GROUPS)) {
                            val chatsBadge = if (unseenChatCounts.groupCount <= 0) {
                                XposedHelpers.getStaticObjectField(emptyBadgeClass, "A00")
                            } else {
                                val params = ReflectionUtils.initArray(badgeWrapperConstructor.parameterTypes)
                                params[0] = badgeItemConstructor.newInstance(
                                    unseenChatCounts.groupCount
                                )
                                badgeWrapperConstructor.newInstance(*params)
                            }
                            XposedBridge.invokeOriginalMethod(
                                param.method,
                                param.thisObject,
                                arrayOf(param.args[0], chatsBadge, tabs.indexOf(GROUPS))
                            )
                        }
                    }
                }
            }
        })
    }

    fun getUnseenChatCounts(): UnseenChatCounts {
        val db = MessageStore.getInstance().getDatabase()
            ?: return UnseenChatCounts(chatCount = 0, groupCount = 0)

        val sql = """
        SELECT
            COALESCE(SUM(CASE WHEN jid.server = ? THEN 0 ELSE 1 END), 0) AS chat_count,
            COALESCE(SUM(CASE WHEN jid.server = ? THEN 1 ELSE 0 END), 0) AS group_count
        FROM chat
        INNER JOIN jid ON jid._id = chat.jid_row_id
        WHERE chat.unseen_message_count <> 0
          AND chat.archived = 0
          AND chat.chat_lock = 0
          AND (chat.group_type = 0 OR chat.group_type = 6)
        """.trimIndent()

        db.rawQuery(sql, arrayOf("g.us", "g.us")).use { cursor ->
            if (!cursor.moveToFirst()) {
                return UnseenChatCounts(chatCount = 0, groupCount = 0)
            }

            return UnseenChatCounts(
                chatCount = cursor.getInt(cursor.getColumnIndexOrThrow("chat_count")),
                groupCount = cursor.getInt(cursor.getColumnIndexOrThrow("group_count"))
            )
        }
    }

    private fun hookTabIcon() {
        val iconTabMethod = Unobfuscator.loadIconTabMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod))
        val menuAddAndroidX = Unobfuscator.loadAddMenuAndroidX(classLoader)
        logDebug(menuAddAndroidX.toString())

        XposedBridge.hookMethod(iconTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val hooked = XposedBridge.hookMethod(menuAddAndroidX, object : XC_MethodHook() {
                    override fun afterHookedMethod(innerParam: MethodHookParam) {
                        if (innerParam.args.size > 2 && (innerParam.args[1] as Int) == GROUPS) {
                            val menuItem = innerParam.result as MenuItem
                            menuItem.setIcon(
                                Utils.getID(
                                    "home_tab_communities_selector",
                                    "drawable"
                                )
                            )
                        }
                    }
                })
                param.setObjectExtra("hooked", hooked)
            }

            @SuppressLint("ResourceType")
            override fun afterHookedMethod(param: MethodHookParam) {
                val hooked = param.getObjectExtra("hooked") as? Unhook
                hooked?.unhook()
            }
        })
    }

    @SuppressLint("ResourceType")
    private fun hookTabName(tabNameMethod: java.lang.reflect.Method) {
        XposedBridge.hookMethod(tabNameMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val arg = param.args[0] as? Int ?: return
                if (resolveTabId(arg) == GROUPS) {
                    param.result = UnobfuscatorCache.getInstance().getString("groups")
                }
            }
        })
    }

    private fun hookTabInstance(getTabMethod: java.lang.reflect.Method, cFragClass: Class<*>) {
        logDebug(Unobfuscator.getMethodDescriptor(getTabMethod))

        // Bagian berikut opsional: kalau anchor-nya hilang (mis. 2.26.46 memindahkan method
        // daftar chat ke ConversationsFragmentKt), hook dilewati saja. Jangan sampai
        // kegagalan di sini membatalkan sisa doHook() setelah tab GROUPS disuntikkan.
        val methodTabInstance = runCatching { Unobfuscator.loadTabFragmentMethod(classLoader) }
            .onFailure { logDebug("SeparateGroup: chat list anchor missing, skip list filter: ${it.message}") }
            .getOrNull()
        logDebug(Unobfuscator.getMethodDescriptor(methodTabInstance))

        val recreateFragmentMethod = runCatching { Unobfuscator.loadRecreateFragmentConstructor(classLoader) }
            .onFailure { logDebug("SeparateGroup: recreate fragment anchor missing: ${it.message}") }
            .getOrNull()

        val fragmentClass = runCatching { Unobfuscator.loadFragmentClass(classLoader) }
            .onFailure { logDebug("SeparateGroup: fragment class anchor missing: ${it.message}") }
            .getOrNull()

        if (recreateFragmentMethod != null && fragmentClass != null) {
            val pattern = Pattern.compile("android:switcher:\\d+:(\\d+)")

            XposedBridge.hookMethod(recreateFragmentMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val string: String
                    val arg0 = param.args[0]
                    if (arg0 is Bundle) {
                        @Suppress("DEPRECATION")
                        val state = arg0.getParcelable<android.os.Parcelable>("state") ?: return
                        string = state.toString()
                    } else {
                        string = param.args[2].toString()
                    }
                    val matcher = pattern.matcher(string)
                    if (matcher.find()) {
                        val tabId = matcher.group(1)?.toIntOrNull() ?: return
                        if (tabId == GROUPS || tabId == CHATS) {
                            val fragmentField = ReflectionUtils.getFieldByType(
                                param.thisObject.javaClass,
                                fragmentClass
                            )
                            val convFragment =
                                ReflectionUtils.getObjectField(fragmentField, param.thisObject)
                            tabInstances.remove(tabId)
                            if (convFragment != null) {
                                tabInstances[tabId] = convFragment
                            }
                        }
                    }
                }
            })
        }

        XposedBridge.hookMethod(getTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tabId = resolveTabId(param.args[0] as Int)
                if (tabId == GROUPS || tabId == CHATS) {
                    val convFragment = cFragClass.declaredConstructors.firstOrNull {
                        it.parameterCount == 0
                    }?.newInstance()
                    if (convFragment != null) param.result = convFragment
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val tabId = resolveTabId(param.args[0] as Int)
                tabInstances.remove(tabId)
                param.result?.let { tabInstances[tabId] = it }
            }
        })

        if (methodTabInstance != null) {
            XposedBridge.hookMethod(methodTabInstance, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val chatsList = param.result as? List<*> ?: return
                    param.result = filterChat(param.thisObject, chatsList)
                }
            })
        }

        val fabintMethod = runCatching { Unobfuscator.loadFabMethod(classLoader) }
            .onFailure { logDebug("SeparateGroup: fab anchor missing: ${it.message}") }
            .getOrNull()
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod))

        if (fabintMethod != null) {
            XposedBridge.hookMethod(fabintMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (tabInstances[GROUPS] == param.thisObject) {
                        param.result = GROUPS
                    }
                }
            })
        }

        val publishResultsMethod = runCatching { Unobfuscator.loadGetFiltersMethod(classLoader) }
            .onFailure { logDebug("SeparateGroup: publishResults anchor missing: ${it.message}") }
            .getOrNull()
        logDebug(Unobfuscator.getMethodDescriptor(publishResultsMethod))

        if (publishResultsMethod != null) {
            XposedBridge.hookMethod(publishResultsMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val filters = param.args[1]
                    val chatsList = XposedHelpers.getObjectField(filters, "values") as? List<*> ?: return
                    val baseField = ReflectionUtils.getFieldByExtendType(
                        publishResultsMethod.declaringClass,
                        BaseAdapter::class.java
                    ) ?: return
                    val convField = ReflectionUtils.getFieldByType(baseField.type, cFragClass) ?: return
                    val thiz = convField.get(baseField.get(param.thisObject)) ?: return
                    val resultList = filterChat(thiz, chatsList)
                    XposedHelpers.setObjectField(filters, "values", resultList)
                    XposedHelpers.setIntField(filters, "count", resultList.size)
                }
            })
        }
    }

    private fun filterChat(thiz: Any, chatsList: List<*>): List<*> {
        val tabChat = tabInstances[CHATS]
        val tabGroup = tabInstances[GROUPS]

        if (tabChat != thiz && tabGroup != thiz) {
            return chatsList
        }

        val editableChatList = ArrayListFilter(
            { userJid ->
                if (tabGroup == thiz)
                    userJid.isGroup || userJid.isBroadcast
                else {
                    userJid.isContact
                }
            },
            false
        )
        @Suppress("UNCHECKED_CAST")
        editableChatList.addAll(chatsList as Collection<Any>)
        return editableChatList
    }

    private fun hookTabList() {
        val onCreateTabList = Unobfuscator.loadTabListMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList))

        XposedBridge.hookMethod(onCreateTabList, object : XC_MethodHook() {
            @Suppress("UNCHECKED_CAST")
            override fun afterHookedMethod(param: MethodHookParam) {
                val resultTabs = param.result as? ArrayList<Int> ?: return
                tabs = resultTabs
                if (!tabs.contains(GROUPS)) {
                    tabs.add(if (tabs.isEmpty()) 0 else 1, GROUPS)
                }
            }
        })
    }


    class ArrayListFilter(
        private val filter: Predicate<FMessageWpp.UserJid>,
        private val includeWhenUnresolved: Boolean,
    ) : ArrayList<Any>() {


        fun addAllFromList(elements: List<*>) {
            for (chat in elements) {
                if (chat != null && shouldInclude(chat)) {
                    super.add(chat)
                }
            }
        }

        override fun add(index: Int, element: Any) {
            if (shouldInclude(element)) {
                super.add(index, element)
            }
        }

        override fun add(element: Any): Boolean {
            if (shouldInclude(element)) {
                return super.add(element)
            }
            return true
        }

        override fun addAll(elements: Collection<Any>): Boolean {
            for (chat in elements) {
                if (shouldInclude(chat)) {
                    super.add(chat)
                }
            }
            return true
        }

        private fun shouldInclude(chat: Any): Boolean {
            val userJid = resolveUserJid(chat) ?: return includeWhenUnresolved
            return filter.test(userJid)
        }
    }

    data class UnseenChatCounts(
        val chatCount: Int,
        val groupCount: Int
    )

}
