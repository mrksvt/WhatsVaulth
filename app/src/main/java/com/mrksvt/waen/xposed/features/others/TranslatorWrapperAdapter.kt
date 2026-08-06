package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.database.DataSetObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.SectionIndexer
import android.widget.TextView
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.utils.Utils
import java.lang.ref.WeakReference

class TranslatorWrapperAdapter(
    val realAdapter: ListAdapter,
    private val prefs: SharedPreferences
) : BaseAdapter(), SectionIndexer {

    companion object {
        private var instance: TranslatorWrapperAdapter? = null

        fun showTranslation(messageId: String, text: String) {
            de.robv.android.xposed.XposedBridge.log("WAE_TRANS: showTranslation id=$messageId instance=${instance != null}")
            val adapter = instance ?: return
            Handler(Looper.getMainLooper()).post {
                adapter.translationMap[messageId] = text
                adapter.rebuildIndex()
                val targetRealPos = adapter.messageIdToRealPos[messageId]
                de.robv.android.xposed.XposedBridge.log("WAE_TRANS: rebuiltIndex sorted=${adapter.realPositionsSorted.size} count=${adapter.count} targetRealPos=$targetRealPos")
                adapter.notifyDataSetChanged()
                if (targetRealPos != null) {
                    val translationWrappedPos = targetRealPos +
                        adapter.realPositionsSorted.indexOfFirst { rp -> rp == targetRealPos } + 1
                    val lv = adapter.listViewRef?.get()
                    val headerCount = lv?.headerViewsCount ?: 0
                    val scrollPos = translationWrappedPos + headerCount
                    de.robv.android.xposed.XposedBridge.log("WAE_SCROLL: translationWrappedPos=$translationWrappedPos headerCount=$headerCount scrollPos=$scrollPos")
                    if (lv != null) {
                        lv.post {
                            lv.smoothScrollToPosition(translationWrappedPos)
                        }
                    }
                }
            }
        }

        fun hideTranslation(messageId: String) {
            val adapter = instance ?: return
            Handler(Looper.getMainLooper()).post {
                adapter.translationMap.remove(messageId)
                adapter.rebuildIndex()
                adapter.notifyDataSetChanged()
            }
        }

        fun hasTranslation(messageId: String): Boolean =
            instance?.translationMap?.containsKey(messageId) == true
    }

    var listViewRef: WeakReference<ListView>? = null
    private var listViewObserver: DataSetObserver? = null

    fun attachListViewObserver(lv: ListView) {
        listViewRef = WeakReference(lv)
        try {
            val field = AbsListView::class.java.getDeclaredField("mDataSetObserver")
            field.isAccessible = true
            val observer = field.get(lv) as? DataSetObserver ?: return
            listViewObserver?.let { unregisterDataSetObserver(it) }
            listViewObserver = observer
            registerDataSetObserver(observer)
            de.robv.android.xposed.XposedBridge.log("WAE_WRAP: attachListViewObserver ok")
        } catch (e: Exception) {
            de.robv.android.xposed.XposedBridge.log("WAE_WRAP: attachListViewObserver fail ${e.message}")
        }
    }

    fun detachListViewObserver() {
        listViewObserver?.let { unregisterDataSetObserver(it) }
        listViewObserver = null
    }

    private val translationMap = HashMap<String, String>()

    private var messageIdToRealPos = HashMap<String, Int>()
    private var realPosToMessageId = HashMap<Int, String>()
    private var realPositionsSorted = IntArray(0)
    private var isNotifying = false

    private val realAdapterObserver = object : android.database.DataSetObserver() {
        override fun onChanged() {
            if (isNotifying) return
            de.robv.android.xposed.XposedBridge.log("WAE_OBS: realAdapter.onChanged slots=${realPositionsSorted.size}")
            notifyDataSetChanged()
        }
        override fun onInvalidated() {
            notifyDataSetInvalidated()
        }
    }

    init {
        instance = this
        try { realAdapter.registerDataSetObserver(realAdapterObserver) } catch (_: Exception) {}
    }

    private fun rebuildMessageIndex() {
        val map = HashMap<String, Int>()
        val count = realAdapter.count
        for (i in 0 until count) {
            try {
                val raw = realAdapter.getItem(i) ?: continue
                val msgId = FMessageWpp(raw).key.messageID ?: continue
                map[msgId] = i
            } catch (_: Exception) {}
        }
        messageIdToRealPos = map
    }

    fun rebuildIndex() {
        rebuildMessageIndex()
        realPositionsSorted = translationMap.keys
            .mapNotNull { messageIdToRealPos[it] }
            .sorted()
            .toIntArray()
        realPosToMessageId = HashMap<Int, String>().also { map ->
            translationMap.keys.forEach { msgId ->
                messageIdToRealPos[msgId]?.let { pos -> map[pos] = msgId }
            }
        }
    }

    fun resolve(wrappedPos: Int): Pair<Boolean, Int> {
        var offset = 0
        for (i in realPositionsSorted.indices) {
            val rp = realPositionsSorted[i]
            val slotStart = rp + offset
            if (wrappedPos == slotStart) return Pair(false, rp)
            if (wrappedPos == slotStart + 1) return Pair(true, rp)
            if (wrappedPos < slotStart) return Pair(false, wrappedPos - offset)
            offset++
        }
        return Pair(false, wrappedPos - offset)
    }

    val realCount: Int get() = realAdapter.count

    override fun getCount(): Int {
        val c = realCount + realPositionsSorted.size
        if (realPositionsSorted.isNotEmpty()) {
            de.robv.android.xposed.XposedBridge.log("WAE_COUNT: count=$c real=$realCount slots=${realPositionsSorted.size}")
        }
        return c
    }

    override fun notifyDataSetChanged() {
        isNotifying = true
        super.notifyDataSetChanged()
        isNotifying = false
    }

    override fun getItem(pos: Int): Any? {
        val (isTranslation, realPos) = resolve(pos)
        return if (isTranslation) null else realAdapter.getItem(realPos)
    }

    override fun getItemId(pos: Int): Long {
        val (isTranslation, realPos) = resolve(pos)
        return if (isTranslation) Long.MIN_VALUE + realPos.toLong()
        else realAdapter.getItemId(realPos)
    }

    override fun hasStableIds(): Boolean = realAdapter.hasStableIds()

    override fun getViewTypeCount(): Int = realAdapter.viewTypeCount

    override fun getItemViewType(pos: Int): Int {
        val (isTranslation, realPos) = resolve(pos)
        return if (isTranslation) android.widget.Adapter.IGNORE_ITEM_VIEW_TYPE
        else realAdapter.getItemViewType(realPos)
    }

    override fun isEnabled(pos: Int): Boolean {
        val (isTranslation, realPos) = resolve(pos)
        return if (isTranslation) false else realAdapter.isEnabled(realPos)
    }

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val (isTranslation, realPos) = resolve(pos)

        de.robv.android.xposed.XposedBridge.log("WAE_VIEW: pos=$pos isTranslation=$isTranslation realPos=$realPos slots=${realPositionsSorted.size}")

        if (!isTranslation) {
            return realAdapter.getView(realPos, convertView, parent)
        }

        val context = parent.context
        val messageId = realPosToMessageId[realPos]
        val translation = messageId?.let { translationMap[it] } ?: run {
            de.robv.android.xposed.XposedBridge.log("WAE_VIEW: miss realPos=$realPos map=${realPosToMessageId.keys} transMap=${translationMap.keys}")
            return View(context).apply {
                layoutParams = ViewGroup.LayoutParams(0, 0)
                visibility = View.GONE
            }
        }
        de.robv.android.xposed.XposedBridge.log("WAE_VIEW: hit realPos=$realPos translation=${translation.take(20)}")

        val isFromMe = try {
            val raw = realAdapter.getItem(realPos) ?: return View(context)
            FMessageWpp(raw).key.isFromMe
        } catch (_: Exception) { false }

        return buildBubbleView(context, translation, isFromMe, realPos)
    }

    private fun buildBubbleView(context: android.content.Context, translation: String, isFromMe: Boolean, realPos: Int): View {
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)
        val dp12 = Utils.dipToPixels(12)

        val rawBubbleColor = if (isFromMe) prefs.getInt("bubble_right", 0)
                             else prefs.getInt("bubble_left", 0)

        val bubbleBgColor: Int
        val bubbleTextColor: Int
        if (rawBubbleColor != 0) {
            val hsv = FloatArray(3)
            Color.colorToHSV(rawBubbleColor, hsv)
            hsv[2] = (hsv[2] * 0.75f).coerceIn(0.1f, 1.0f)
            bubbleBgColor = Color.HSVToColor(hsv)
            bubbleTextColor = if (hsv[2] < 0.5f) Color.WHITE else Color.BLACK
        } else {
            if (isFromMe) {
                bubbleBgColor = Color.parseColor("#1A237E")
                bubbleTextColor = Color.parseColor("#E8EAF6")
            } else {
                bubbleBgColor = Color.parseColor("#1B5E20")
                bubbleTextColor = Color.parseColor("#E8F5E9")
            }
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bubbleBgColor)
            cornerRadius = dp12.toFloat()
        }

        val gravity = if (isFromMe) Gravity.END else Gravity.START

        val bubbleResId = if (isFromMe)
            Utils.getIDFromModule("groq_translator_bubble_outgoing")
        else
            Utils.getIDFromModule("groq_translator_bubble_incoming")

        val tv = TextView(context).apply {
            if (bubbleResId > 0) id = bubbleResId
            text = "🌐 $translation"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(bubbleTextColor)
            background = bgDrawable
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp8, dp4, dp8, dp4)
            this.gravity = gravity
        }

        val tvLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.gravity = gravity }
        tv.layoutParams = tvLp

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp4, dp8, dp4)
            addView(tv)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun getSections(): Array<Any> =
        (realAdapter as? SectionIndexer)?.sections ?: emptyArray()

    override fun getPositionForSection(sectionIndex: Int): Int {
        val realPos = (realAdapter as? SectionIndexer)?.getPositionForSection(sectionIndex) ?: 0
        var offset = 0
        for (rp in realPositionsSorted) {
            if (rp >= realPos) break
            offset++
        }
        return realPos + offset
    }

    override fun getSectionForPosition(position: Int): Int {
        val (_, realPos) = resolve(position)
        return (realAdapter as? SectionIndexer)?.getSectionForPosition(realPos) ?: 0
    }
}
