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
        // Placeholder "Status saya" selalu ada di index 0. Kalau anchor data gagal
        // dimuat, baris status tetap tampil dan bisa dibedakan dari kasus view yang
        // tidak terpasang sama sekali.
        var itens = arrayListOf<Any?>(null)

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

        logDebug("IGStatus doHook", "start")

        forceStatusLoad()

        val fabintMethod = runCatching { Unobfuscator.loadFabMethod(classLoader) }
            .onFailure { logDebug("IGStatus fab gagal", it) }
            .getOrNull() ?: return
        logDebug("IGStatus fab", Unobfuscator.getMethodDescriptor(fabintMethod))

        // Kelas ini hanya dipakai untuk melewati tab arsip dan folder. Kalau anchor-nya
        // hilang, jangan batalkan seluruh fitur, cukup lewati penyaringan.
        val archivedFragmentClass = runCatching {
            Unobfuscator.findFirstClassUsingName(
                classLoader, StringMatchType.EndsWith, ".ArchivedConversationsFragment"
            )
        }.onFailure { logDebug("IGStatus archived class gagal", it) }.getOrNull()
        val folderFragmentClass = runCatching {
            Unobfuscator.findFirstClassUsingName(
                classLoader, StringMatchType.EndsWith, ".FolderConversationsFragment"
            )
        }.onFailure { logDebug("IGStatus folder class gagal", it) }.getOrNull()
        logDebug(
            "IGStatus skip classes",
            "archived=${archivedFragmentClass?.name} folder=${folderFragmentClass?.name}"
        )

        val getViewConversationMethod =
            runCatching { Unobfuscator.loadGetViewConversationMethod(classLoader) }
                .onFailure { logDebug("IGStatus getViewConversation gagal", it) }
                .getOrNull() ?: return
        val statusInfoClass = runCatching { Unobfuscator.loadStatusInfoClass(classLoader) }
            .onFailure { logDebug("IGStatus statusInfo gagal", it) }
            .getOrNull() ?: return
        logDebug("IGStatus hook", Unobfuscator.getMethodDescriptor(getViewConversationMethod))
        logDebug("IGStatus statusInfo", statusInfoClass.name)
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

        // Tiga anchor data di bawah berdiri sendiri. Kalau salah satunya hilang di versi
        // WhatsApp baru, hook lain tetap terpasang supaya baris status masih bisa terisi
        // dari jalur yang tersisa, dan penyebabnya tercatat di log.
        runCatching {
            val onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader)
            logDebug(
                "IGStatus onUpdateStatusChanged",
                Unobfuscator.getMethodDescriptor(onUpdateStatusChanged)
            )

            val updateModel = onUpdateStatusChanged.declaringClass
            logDebug("IGStatus updateModel", updateModel.name)

            XposedBridge.hookAllConstructors(updateModel, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // Normalisasi: placeholder "Status saya" hanya boleh satu di index 0.
                    // Versi lama menambah null baru setiap konstruktor jalan sehingga daftar
                    // bisa berisi beberapa placeholder.
                    val newList = ArrayList<Any?>()
                    newList.add(null)
                    newList.addAll(itens.filterNotNull())
                    itens = newList
                    logDebug("IGStatus constructor updateModel", "itens=${itens.size}")
                    for (mStatusContainer in mListStatusContainer) {
                        runCatching {
                            val activity = WppCore.getCurrentActivity() ?: return@runCatching
                            if (mStatusContainer.adapter?.context !== activity) {
                                mStatusContainer.adapter = IGStatusAdapter(activity, statusInfoClass)
                            }
                            mStatusContainer.updateList(itens)
                        }.onFailure { logDebug("IGStatus constructor updateModel gagal", it) }
                    }
                }
            })
        }.onFailure { logDebug("IGStatus updateModel gagal", it) }

        runCatching {
            val onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            logDebug("IGStatus statusListUpdates", onStatusListUpdatesClass.name)

            XposedBridge.hookAllConstructors(onStatusListUpdatesClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val lists = param.args.filterIsInstance<List<*>>()
                    logDebug(
                        "IGStatus StatusListUpdates",
                        "args=${param.args.size} lists=${lists.size} " +
                                "size0=${lists.getOrNull(0)?.size} size1=${lists.getOrNull(1)?.size}"
                    )
                    if (lists.size < 2) return
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
                    logDebug("IGStatus StatusListUpdates", "itens=${itens.size}")
                    for (mStatusContainer in mListStatusContainer) {
                        mStatusContainer.updateList(itens)
                    }
                }
            })
        }.onFailure { logDebug("IGStatus statusListUpdates gagal", it) }

        runCatching {
            val onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader)
            val onGetInvokeField = Unobfuscator.loadGetInvokeField(classLoader)
            logDebug("IGStatus invokeField", Unobfuscator.getFieldDescriptor(onGetInvokeField))
            XposedBridge.hookMethod(onUpdateStatusChanged, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val holder = onGetInvokeField.get(param.args[0])
                        val lists = extractStatusLists(holder)
                        logDebug(
                            "IGStatus onUpdateStatusChanged",
                            "holder=${holder != null} lists=${lists != null}"
                        )
                        if (lists != null) {
                            applyStatusLists(lists.first, lists.second)
                        } else {
                            captureWhenReady(holder, 0)
                        }
                    }.onFailure { logDebug("IGStatus onUpdateStatusChanged gagal", it) }
                }
            })
        }.onFailure { logDebug("IGStatus onUpdateStatusChanged gagal", it) }
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
        if (archivedFragmentClass?.isInstance(param.thisObject) == true) {
            logDebug("IGStatus skip", "archived fragment")
            return
        }
        if (folderFragmentClass?.isInstance(param.thisObject) == true) {
            logDebug("IGStatus skip", "folder fragment")
            return
        }
        val view = param.result as? ViewGroup
        if (view == null) {
            logDebug("IGStatus skip", "result bukan ViewGroup: ${param.result?.javaClass?.name}")
            return
        }
        val list = view.findViewById<View>(android.R.id.list) as? ViewGroup
        val activity = WppCore.getCurrentActivity()
        if (activity == null) {
            logDebug("IGStatus skip", "current activity null")
            return
        }
        if (list == null) {
            logDebug("IGStatus: daftar chat tidak ditemukan", view.javaClass.name)
            return
        }
        val parent = list.parent as? ViewGroup
        if (parent == null) {
            logDebug("IGStatus skip", "parent daftar chat null")
            return
        }
        logDebug(
            "IGStatus view",
            "fragment=${param.thisObject.javaClass.name} list=${list.javaClass.name} " +
                    "parent=${parent.javaClass.name} parentChildren=${parent.childCount}"
        )
        // Hook bisa jalan lebih dari sekali untuk fragment yang sama (view recreate).
        if ((0 until parent.childCount).any { parent.getChildAt(it) is IGStatusView }) {
            logDebug("IGStatus skip", "baris status sudah terpasang di parent")
            return
        }

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
            // IGStatusView.setTranslationY mengabaikan nilai selama tinggi view masih 0,
            // jadi posisi diatur lewat margin atas pada layout params, bukan translation.
            val params = mStatusContainer.layoutParams
            if (params is ViewGroup.MarginLayoutParams) {
                params.topMargin = originalTop
                mStatusContainer.layoutParams = params
            } else {
                mStatusContainer.translationY = originalTop.toFloat()
            }
            logDebug("IGStatus: baris status overlay di atas daftar", parent.javaClass.name)
        }

        val id = fabintMethod.invoke(param.thisObject) as Int
        val igStatus = mListStatusContainer.find { it.fragmentId == id }
        if (igStatus != null) {
            mStatusContainer.adapter = igStatus.adapter
            igStatus.adapter = null
            mListStatusContainer.remove(igStatus)
            logDebug("IGStatus adapter", "pakai ulang adapter lama untuk tab $id")
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
            logDebug("IGStatus adapter", "adapter baru untuk tab $id, itens=${itens.size}")
        }
        mStatusContainer.fragmentId = id
        mListStatusContainer.add(mStatusContainer)
        scheduleColdStartRefresh(mStatusContainer)

        // Konfirmasi setelah layout: kalau tinggi 0 atau tidak terlihat, penyebabnya
        // bukan hook yang tidak jalan.
        mStatusContainer.post {
            logDebug(
                "IGStatus layout",
                "w=${mStatusContainer.width} h=${mStatusContainer.height} " +
                        "visible=${mStatusContainer.visibility == View.VISIBLE} " +
                        "shown=${mStatusContainer.isShown} " +
                        "parent=${parent.javaClass.simpleName} " +
                        "items=${mStatusContainer.adapter?.itemCount}"
            )
        }
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
        logDebug("IGStatus applyStatusLists", "size0=${list1.size} size1=${list2.size} itens=${itens.size}")
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
            .onFailure { logDebug("IGStatus forceStatusLoad onStart", it) }.getOrNull()
        val asyncInit = runCatching { Unobfuscator.loadUpdatesFragmentAsyncInitMethod(classLoader) }
            .onFailure { logDebug("IGStatus forceStatusLoad asyncInit", it) }.getOrNull()
        if (onStart == null && asyncInit == null) {
            logDebug("IGStatus forceStatusLoad", "kedua anchor tidak ditemukan, dilewati")
            return
        }
        logDebug(
            "IGStatus forceStatusLoad",
            "onStart=${Unobfuscator.getMethodDescriptor(onStart)} " +
                    "asyncInit=${Unobfuscator.getMethodDescriptor(asyncInit)}"
        )

        if (asyncInit != null) {
            XposedBridge.hookMethod(asyncInit, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    initializedFragments.add(param.thisObject)
                }
            })
        }

        val handler = Handler(Looper.getMainLooper())
        keepAllTabsAlive(handler)
        if (onStart == null) return
        XposedBridge.hookMethod(onStart, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject
                logDebug("UpdatesFragment.onStart", fragment)
                handler.postDelayed({
                    if (fragment !in initializedFragments) {
                        runCatching {
                            initializedFragments.add(fragment)
                            asyncInit?.invoke(fragment)
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
