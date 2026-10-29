package com.mrksvt.waen.xposed.features.customization

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ListView
import com.mrksvt.waen.adapter.IGStatusAdapter
import com.mrksvt.waen.views.IGStatusView
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

private val mListStatusContainer = ArrayList<IGStatusView>()

class IGStatus(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    companion object {
        @JvmField
        var itens = ArrayList<Any?>()

        private const val RETRY_DELAY_MS = 250L
        private const val MAX_RETRY = 40
        private val RETRY_HANDLER = Handler(Looper.getMainLooper())

        private const val FORCE_LOAD_DELAY_MS = 1500L
        private const val PAGER_RETRY_MS = 500L
        private const val PAGER_MAX_ATTEMPTS = 30
        private const val PAGER_OFFSCREEN_PAGES = 6
        private const val PAGER_RESTORE_DELAY_MS = 2000L
    }

    private var raisedPager: WeakReference<ViewGroup>? = null
    private var originalPageLimit = 1

    // UpdatesFragment instances whose async init already ran, naturally or forced
    private val initializedFragments = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("igstatus", false)) return

        forceStatusLoad()

        val fabintMethod = Unobfuscator.loadFabMethod(classLoader)

        val archivedFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".ArchivedConversationsFragment"
        )
        val folderFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".FolderConversationsFragment"
        )

        val getViewConversationMethod = Unobfuscator.loadGetViewConversationMethod(classLoader)
        val statusInfoClass = Unobfuscator.loadStatusInfoClass(classLoader)
        XposedBridge.hookMethod(getViewConversationMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (archivedFragmentClass.isInstance(param.thisObject)) return
                if (folderFragmentClass.isInstance(param.thisObject)) return
                val view = param.result as? ViewGroup ?: return
                val list = view.findViewById<ViewGroup>(android.R.id.list)
                val mStatusContainer = IGStatusView(WppCore.getCurrentActivity()!!)
                if (list is ListView) {
                    list.isNestedScrollingEnabled = true
                    val layoutParams = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88)
                    )
                    mStatusContainer.layoutParams = layoutParams
                    list.addHeaderView(mStatusContainer)
                } else {
                    val paddingTop = list.paddingTop
                    val parentView = list.parent as ViewGroup
                    val background = list.background
                    mStatusContainer.background = background
                    list.setPadding(0, 0, 0, 0)
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88)
                    )
                    layoutParams.topMargin = paddingTop
                    mStatusContainer.layoutParams = layoutParams
                    parentView.addView(mStatusContainer, 0)
                }
                val id = fabintMethod.invoke(param.thisObject) as Int
                val igStatus = mListStatusContainer.find { it.fragmentId == id }
                if (igStatus != null) {
                    mStatusContainer.adapter = igStatus.adapter
                    mListStatusContainer.remove(igStatus)
                } else {
                    // Adapter harus terpasang sejak container dibuat. `updateList()` no-op selama
                    // adapter null, jadi kalau container lahir sebelum daftar status terisi
                    // (cold start), baris tidak akan pernah muncul sampai view dibuat ulang.
                    mStatusContainer.adapter =
                        IGStatusAdapter(WppCore.getCurrentActivity()!!, statusInfoClass)
                    // `itens` bisa sudah terisi sebelum view lahir (cold start:
                    // konstruktor list status jalan sebelum FragmentView dibuat).
                    // Adapter baru belum pernah dapat notify, jadi panggil sekali
                    // di sini supaya baris langsung tampil tanpa harus pindah menu.
                    mStatusContainer.updateList()
                }
                mStatusContainer.fragmentId = id
                mListStatusContainer.add(mStatusContainer)
            }
        })

        val onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader)

        val updateModel = onUpdateStatusChanged.declaringClass

        XposedBridge.hookAllConstructors(updateModel, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val newList = ArrayList(itens)
                newList.add(0, null)
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    val activity = WppCore.getCurrentActivity()!!
                    if (mStatusContainer.adapter?.context !== activity) {
                        mStatusContainer.adapter = IGStatusAdapter(activity, statusInfoClass)
                    }
                    mStatusContainer.updateList()
                }
            }
        })

        val onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)

        XposedBridge.hookAllConstructors(onStatusListUpdatesClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val lists = param.args.filterIsInstance<List<*>>()
                val newList = ArrayList<Any?>()
                newList.add(0, null)
                newList.addAll(lists[0])
                newList.addAll(lists[1])
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    mStatusContainer.updateList()
                }
            }
        })

        val onGetInvokeField = Unobfuscator.loadGetInvokeField(classLoader)
        XposedBridge.hookMethod(onUpdateStatusChanged, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val holder = onGetInvokeField.get(param.args[0])
                val lists = extractStatusLists(holder)
                if (lists != null) {
                    applyStatusLists(lists.first, lists.second)
                } else {
                    captureWhenReady(holder, 0)
                }
            }
        })
    }

    /**
     * `holder` adalah async holder (field `A04()` mengembalikan null sampai data tiba).
     * Saat `onUpdateStatusChanged` fire, data sering belum siap, jadi tunggu sampai
     * tersedia, bukan langsung batal.
     */
    private fun captureWhenReady(holder: Any?, attempt: Int) {
        if (holder == null || attempt > MAX_RETRY) return
        val lists = extractStatusLists(holder)
        if (lists != null) {
            applyStatusLists(lists.first, lists.second)
            return
        }
        RETRY_HANDLER.postDelayed({ captureWhenReady(holder, attempt + 1) }, RETRY_DELAY_MS)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractStatusLists(holder: Any?): Pair<List<*>, List<*>>? {
        if (holder == null) return null
        return try {
            val getter = ReflectionUtils.findMethodUsingFilter(holder.javaClass) { m ->
                m.returnType == Any::class.java && m.parameterCount == 0
            } ?: return null
            val updates = ReflectionUtils.callMethod(getter, holder) ?: return null
            val lists = ReflectionUtils.findAllFieldsUsingFilter(updates.javaClass) { f ->
                f.type == List::class.java
            }
            if (lists.size < 3) return null
            lists[1].get(updates) as List<*> to (lists[2].get(updates) as List<*>)
        } catch (e: Throwable) {
            log("IGStatus extract failed: ${e.message}")
            null
        }
    }

    private fun applyStatusLists(list1: List<*>, list2: List<*>) {
        val newList = ArrayList<Any?>()
        newList.add(0, null)
        newList.addAll(list1)
        newList.addAll(list2)
        itens = newList
        for (mStatusContainer in mListStatusContainer) {
            mStatusContainer.updateList()
        }
    }

    private fun forceStatusLoad() {
        val onStart = runCatching { Unobfuscator.loadUpdatesFragmentOnStartMethod(classLoader) }
            .onFailure { logDebug(it) }.getOrNull() ?: return
        val asyncInit = runCatching { Unobfuscator.loadUpdatesFragmentAsyncInitMethod(classLoader) }
            .onFailure { logDebug(it) }.getOrNull() ?: return

        XposedBridge.hookMethod(asyncInit, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                initializedFragments.add(param.thisObject)
            }
        })

        val handler = Handler(Looper.getMainLooper())
        keepAllTabsAlive(handler)
        XposedBridge.hookMethod(onStart, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject
                logDebug("UpdatesFragment.onStart", fragment)
                handler.postDelayed({
                    if (fragment !in initializedFragments) {
                        runCatching {
                            initializedFragments.add(fragment)
                            asyncInit.invoke(fragment)
                        }.onFailure { logDebug(it) }
                    }
                    handler.postDelayed({ restorePageLimit() }, PAGER_RESTORE_DELAY_MS)
                }, FORCE_LOAD_DELAY_MS)
            }
        })
    }

    private fun restorePageLimit() {
        val pager = raisedPager?.get() ?: return
        raisedPager = null
        runCatching {
            XposedHelpers.callMethod(pager, "setOffscreenPageLimit", originalPageLimit)
        }.onFailure { logDebug(it) }
        logDebug("home pager", "restored limit=$originalPageLimit")
    }

    private fun keepAllTabsAlive(handler: Handler) {
        XposedBridge.hookAllMethods(
            WppCore.homeActivityClass,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? android.app.Activity ?: return
                    var attempts = 0
                    handler.post(object : Runnable {
                        override fun run() {
                            val pager = findTabsPager(activity.window.decorView)
                            val ready = pager != null &&
                                XposedHelpers.callMethod(pager, "getAdapter") != null
                            if (pager == null || !ready) {
                                if (++attempts < PAGER_MAX_ATTEMPTS && !activity.isFinishing) {
                                    handler.postDelayed(this, PAGER_RETRY_MS)
                                } else {
                                    logDebug("home pager not found", "$attempts pager=${pager?.javaClass?.name}")
                                }
                                return
                            }
                            val limit = XposedHelpers.callMethod(pager, "getOffscreenPageLimit") as? Int ?: 1
                            logDebug("home pager", "limit=$limit attempts=$attempts")
                            if (limit < PAGER_OFFSCREEN_PAGES) {
                                runCatching {
                                    XposedHelpers.callMethod(pager, "setOffscreenPageLimit", PAGER_OFFSCREEN_PAGES)
                                    raisedPager = WeakReference(pager)
                                    originalPageLimit = limit
                                }.onFailure { logDebug(it) }
                            }
                        }
                    })
                }
            })
    }

    private fun isViewPager(view: View): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            if (cls.name == "androidx.viewpager.widget.ViewPager") return true
            cls = cls.superclass
        }
        return false
    }

    private fun findTabsPager(view: View): ViewGroup? {
        if (isViewPager(view)) return view as ViewGroup
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findTabsPager(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    override fun getPluginName(): String {
        return "IGStatus"
    }
}
