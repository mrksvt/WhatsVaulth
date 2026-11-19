package com.mrksvt.waen.xposed.features.customization

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
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
        @Volatile
        var itens = ArrayList<Any?>()

        private const val RETRY_DELAY_MS = 250L
        private const val MAX_RETRY = 40
        private val RETRY_HANDLER = Handler(Looper.getMainLooper())

        private const val COLD_START_REFRESH_ATTEMPTS = 6
        private const val COLD_START_REFRESH_STEP_MS = 500L

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
        if (prefs.getBoolean("native_igstatus", false)) return

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
        logDebug("IGStatus hook", Unobfuscator.getMethodDescriptor(getViewConversationMethod))
        XposedBridge.hookMethod(getViewConversationMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Jangan biarkan satu kegagalan layout mematikan seluruh baris status.
                runCatching {
                    attachStatusRow(
                        param,
                        archivedFragmentClass,
                        folderFragmentClass,
                        fabintMethod,
                        statusInfoClass
                    )
                }.onFailure { logDebug("IGStatus: gagal memasang baris status", it) }
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
                    mStatusContainer.updateList(itens)
                }
            }
        })

        val onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)

        XposedBridge.hookAllConstructors(onStatusListUpdatesClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val lists = param.args.filterIsInstance<List<*>>()
                // Snapshot awal bisa kosong (data belum masuk). Kalau daftar yang sudah
                // terisi ditimpa snapshot kosong, baris IG Status jadi kosong sampai
                // pengguna pindah tab. Lihat applyStatusLists().
                if (lists[0].isEmpty() && lists[1].isEmpty() && itens.size > 1) {
                    logDebug("StatusListUpdates skip empty snapshot", itens.size)
                    return
                }
                val newList = ArrayList<Any?>()
                newList.add(0, null)
                newList.addAll(lists[0])
                newList.addAll(lists[1])
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    mStatusContainer.updateList(itens)
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
     * Memasang baris IG Status di atas daftar chat.
     *
     * Sampai 2.26.45 daftar chat masih `ListView`, jadi `addHeaderView()` cukup. Di
     * 2.26.46 daftar chat adalah `RecyclerView` dan kode lama menambahkan container ke
     * `parent` pada index 0. Pada parent bertipe FrameLayout, child index 0 digambar
     * paling bawah, jadi baris status tertutup RecyclerView dan fitur tampak mati tanpa
     * error apa pun.
     */
    private fun attachStatusRow(
        param: XC_MethodHook.MethodHookParam,
        archivedFragmentClass: Class<*>?,
        folderFragmentClass: Class<*>?,
        fabintMethod: Method,
        statusInfoClass: Class<*>
    ) {
        if (archivedFragmentClass?.isInstance(param.thisObject) == true) return
        if (folderFragmentClass?.isInstance(param.thisObject) == true) return
        val view = param.result as? ViewGroup ?: return
        val list = view.findViewById<View>(android.R.id.list) as? ViewGroup
        val activity = WppCore.getCurrentActivity() ?: return
        if (list == null) {
            logDebug("IGStatus: daftar chat tidak ditemukan", view.javaClass.name)
            return
        }
        val parent = list.parent as? ViewGroup ?: return
        // Hook bisa jalan lebih dari sekali untuk fragment yang sama (view recreate).
        if ((0 until parent.childCount).any { parent.getChildAt(it) is IGStatusView }) return

        val statusHeight = Utils.dipToPixels(88)
        val mStatusContainer = IGStatusView(activity)
        val background = list.background
        mStatusContainer.background = background

        if (list is ListView) {
            list.isNestedScrollingEnabled = true
            mStatusContainer.layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT, statusHeight
            )
            list.addHeaderView(mStatusContainer)
            logDebug("IGStatus: baris status dipasang sebagai header ListView")
        } else if (parent is LinearLayout && parent.orientation == LinearLayout.VERTICAL) {
            // Parent vertikal: sisipkan tepat sebelum daftar, tidak perlu padding.
            parent.addView(
                mStatusContainer,
                parent.indexOfChild(list),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, statusHeight
                )
            )
            logDebug("IGStatus: baris status disisipkan di LinearLayout")
        } else {
            // Parent FrameLayout dan sejenisnya: geser isi daftar dengan padding atas,
            // lalu tambahkan container sebagai child terakhir supaya digambar di atas.
            val originalTop = list.paddingTop
            list.setPadding(
                list.paddingLeft,
                originalTop + statusHeight,
                list.paddingRight,
                list.paddingBottom
            )
            if (list is RecyclerView) {
                list.clipToPadding = true
                list.isNestedScrollingEnabled = true
            }
            parent.addView(mStatusContainer)
            mStatusContainer.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = statusHeight
            }
            mStatusContainer.translationY = originalTop.toFloat()
            logDebug("IGStatus: baris status overlay di atas daftar", parent.javaClass.name)
        }

        val id = fabintMethod.invoke(param.thisObject) as Int
        val igStatus = mListStatusContainer.find { it.fragmentId == id }
        if (igStatus != null) {
            mStatusContainer.adapter = igStatus.adapter
            igStatus.adapter = null
            mListStatusContainer.remove(igStatus)
        } else {
            // Adapter harus terpasang sejak container dibuat. `updateList()` no-op selama
            // adapter null, jadi kalau container lahir sebelum daftar status terisi
            // (cold start), baris tidak akan pernah muncul sampai view dibuat ulang.
            mStatusContainer.adapter = IGStatusAdapter(activity, statusInfoClass)
            // `itens` bisa sudah terisi sebelum view lahir (cold start:
            // konstruktor list status jalan sebelum FragmentView dibuat).
            // Adapter baru belum pernah dapat notify, jadi panggil sekali
            // di sini supaya baris langsung tampil tanpa harus pindah menu.
            mStatusContainer.updateList(itens)
        }
        mStatusContainer.fragmentId = id
        mListStatusContainer.add(mStatusContainer)
        scheduleColdStartRefresh(mStatusContainer)
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
        // Snapshot dari holder bisa kosong walau daftar yang benar sudah masuk lewat
        // StatusListUpdates. Menimpanya dengan snapshot kosong membuat IG Status hanya
        // menampilkan "Status saya" sampai pengguna pindah tab (cold start 2.26.45.xx).
        if (list1.isEmpty() && list2.isEmpty() && itens.size > 1) {
            logDebug("applyStatusLists skip empty snapshot", itens.size)
            return
        }
        val newList = ArrayList<Any?>()
        newList.add(0, null)
        newList.addAll(list1)
        newList.addAll(list2)
        itens = newList
        for (mStatusContainer in mListStatusContainer) {
            mStatusContainer.updateList(itens)
        }
    }

    /**
     * Cold start: daftar status sering tiba sesudah view lahir dan notify dari hook data
     * bisa terlewat (hook jalan di thread lain). Beberapa refresh murah memastikan baris
     * terisi tanpa perlu pindah tab.
     */
    private fun scheduleColdStartRefresh(container: IGStatusView) {
        for (attempt in 1..COLD_START_REFRESH_ATTEMPTS) {
            RETRY_HANDLER.postDelayed({
                if (itens.size > 1) container.updateList(itens)
            }, COLD_START_REFRESH_STEP_MS * attempt)
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
