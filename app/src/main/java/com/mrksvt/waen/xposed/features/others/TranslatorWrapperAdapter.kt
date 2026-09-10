package com.mrksvt.waen.xposed.features.others

import android.database.DataSetObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SectionIndexer
import android.widget.TextView
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.db.TranslationCacheStore
import com.mrksvt.waen.xposed.features.voice_tts.core.BubbleState
import com.mrksvt.waen.xposed.features.voice_tts.core.BubbleType
import com.mrksvt.waen.xposed.features.voice_tts.core.SyntheticBubble
import com.mrksvt.waen.xposed.features.voice_tts.core.SyntheticBubbleStore
import com.mrksvt.waen.xposed.features.voice_tts.hooks.VoiceNoteViewCloner
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic wrapper adapter for WhatsApp chat ListView.
 *
 * Refactored from translation-only to a generic synthetic-bubble system:
 * any feature (Translation, TTS, future types) registers bubbles in the
 * per-conversation [SyntheticBubbleStore]; this adapter merges them into the
 * flat list bound to WhatsApp's RecyclerView/ListView.
 *
 * Invariants:
 *  - for one original message: the message itself first, then its bubbles in
 *    insertion order (never reordered by type)
 *  - deleting a bubble regenerates the flattened index, so following bubbles
 *    shift up automatically (no manual re-link)
 *  - no bubble references another bubble; each only knows its originalMessageId
 */
class TranslatorWrapperAdapter(
    val realAdapter: ListAdapter,
    private val prefs: android.content.SharedPreferences,
    private val conversationJid: String = ""
) : BaseAdapter(), SectionIndexer {

    companion object {
        private val instances = ConcurrentHashMap<String, WeakReference<TranslatorWrapperAdapter>>()
        private var lastCreated: WeakReference<TranslatorWrapperAdapter>? = null

        // ---- Generic bubble store per conversation JID ----

        private val bubbleStores = ConcurrentHashMap<String, SyntheticBubbleStore>()

        @JvmStatic
        fun getBubbleStore(jid: String): SyntheticBubbleStore =
            bubbleStores.getOrPut(jid) { SyntheticBubbleStore() }

        /**
         * Rebuild flattened index for a conversation and refresh its list.
         * Called by features after add/remove/update synthetic bubbles.
         * No-op when the conversation has no live adapter yet (the bubble stays
         * in the store and shows up when an adapter is created for the jid).
         */
        @JvmStatic
        fun refreshBubbles(conversationJid: String) {
            val adapter = instances[conversationJid]?.get() ?: return
            adapter.rebuildIndex()
            Handler(Looper.getMainLooper()).post {
                adapter.notifyDataSetChanged()
            }
        }

        /** Cross-thread refresh used by the TTS poller (posts to main looper). */
        @JvmStatic
        fun requestBubbleRefresh(conversationJid: String) {
            Handler(Looper.getMainLooper()).post { refreshBubbles(conversationJid) }
        }

        // ---- Translation helpers (forwarded to the generic store) ----

        fun showTranslation(conversationJid: String, messageId: String, text: String) {
            val store = getBubbleStore(conversationJid)
            store.addBubble(messageId, BubbleType.TRANSLATION, BubbleState.READY, text)
            instances[conversationJid]?.get()?.let { a ->
                Utils.executor.execute { try { a.saveCacheToDb(messageId, text) } catch (_: Exception) {} }
            }
            refreshBubbles(conversationJid)
        }

        fun hideTranslation(conversationJid: String, messageId: String) {
            val store = getBubbleStore(conversationJid)
            store.snapshot()
                .filter { it.originalMessageId == messageId && it.type == BubbleType.TRANSLATION }
                .forEach { store.removeBubble(it.bubbleId) }
            instances[conversationJid]?.get()?.let { a ->
                Utils.executor.execute { try { a.deleteCacheFromDb(messageId) } catch (_: Exception) {} }
            }
            refreshBubbles(conversationJid)
        }

        fun hasTranslation(conversationJid: String, messageId: String): Boolean =
            getBubbleStore(conversationJid).hasBubbleFor(messageId, BubbleType.TRANSLATION)

        fun startLoading(conversationJid: String, messageId: String) {
            getBubbleStore(conversationJid)
                .addBubble(messageId, BubbleType.TRANSLATION, BubbleState.LOADING)
            refreshBubbles(conversationJid)
        }

        fun clearLoading(conversationJid: String, messageId: String) {
            val store = getBubbleStore(conversationJid)
            store.snapshot()
                .filter {
                    it.originalMessageId == messageId &&
                        it.type == BubbleType.TRANSLATION &&
                        it.state == BubbleState.LOADING
                }
                .forEach { store.removeBubble(it.bubbleId) }
            refreshBubbles(conversationJid)
        }

        fun showGroqFallbackNotification(conversationJid: String, rootView: View) {
            try {
                com.google.android.material.snackbar.Snackbar.make(
                    rootView,
                    rootView.context.getString(R.string.translator_groq_key_missing),
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            } catch (_: Exception) {}
        }

        // ---- Registration per JID ----

        fun registerJid(jid: String, adapter: TranslatorWrapperAdapter) {
            if (jid.isBlank()) return
            instances[jid] = WeakReference(adapter)
        }

        fun registerJidForCurrentAdapter(jid: String) {
            if (jid.isBlank()) return
            val adapter = lastCreated?.get() ?: return
            if (instances[jid]?.get() == adapter) return
            adapter.setConversationJid(jid)
        }

        private fun getOrRegister(jid: String): TranslatorWrapperAdapter? {
            val existing = instances[jid]?.get()
            if (existing != null) return existing
            val fallback = lastCreated?.get() ?: return null
            if (fallback.jid.isNotBlank() && fallback.jid != jid) return null
            fallback.setConversationJid(jid)
            return fallback
        }

        private val stubAdapters = HashMap<String, TranslatorWrapperAdapter>()

        fun getOrCreateForRealAdapter(
            realAdapter: ListAdapter,
            prefs: android.content.SharedPreferences
        ): TranslatorWrapperAdapter {
            val existing = instances.values.mapNotNull { it.get() }
                .firstOrNull { it.realAdapter === realAdapter }
            if (existing != null) return existing
            val fallback = lastCreated?.get()
            if (fallback != null && fallback.realAdapter === realAdapter) return fallback
            return TranslatorWrapperAdapter(realAdapter, prefs)
        }

        fun getOrCreateForJid(
            jid: String,
            prefs: android.content.SharedPreferences
        ): TranslatorWrapperAdapter {
            val existing = instances[jid]?.get()
            if (existing != null) return existing
            val fallback = lastCreated?.get()
            if (fallback != null) {
                fallback.setConversationJid(jid)
                return fallback
            }
            val cached = stubAdapters[jid]
            if (cached != null) return cached
            val stub = object : BaseAdapter() {
                override fun getCount() = 0
                override fun getItem(pos: Int): Any? = null
                override fun getItemId(pos: Int) = 0L
                override fun getView(pos: Int, v: View?, p: ViewGroup) =
                    v ?: View(p.context)
            }
            val adapter = TranslatorWrapperAdapter(stub, prefs, jid)
            stubAdapters[jid] = adapter
            return adapter
        }
    }

    var listViewRef: WeakReference<ListView>? = null
    private var listViewObserver: DataSetObserver? = null
    private var jid: String = conversationJid

    fun setConversationJid(newJid: String) {
        if (newJid.isBlank() || jid == newJid) return
        instances.values.removeAll { it.get() == this }
        jid = newJid
        instances[newJid] = WeakReference(this)
        loadCacheFromDb()
        if (getBubbleStore(newJid).snapshot().isNotEmpty()) {
            rebuildIndex()
            notifyDataSetChanged()
        }
    }

    fun attachListViewObserver(lv: ListView) {
        listViewRef = WeakReference(lv)
        try {
            val field = AbsListView::class.java.getDeclaredField("mDataSetObserver")
            field.isAccessible = true
            val observer = field.get(lv) as? DataSetObserver ?: return
            listViewObserver?.let { unregisterDataSetObserver(it) }
            listViewObserver = observer
            registerDataSetObserver(observer)
            if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log("WAE_WRAP: attachListViewObserver ok")
        } catch (e: Exception) {
            if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log("WAE_WRAP: attachListViewObserver fail ${e.message}")
        }
    }

    fun detachListViewObserver() {
        listViewObserver?.let { unregisterDataSetObserver(it) }
        listViewObserver = null
    }

    fun destroy() {
        if (jid.isBlank()) return
        instances.remove(jid)
        VoiceNoteViewCloner.releaseAll()
        Utils.executor.execute {
            try {
                TranslationCacheStore.deleteByJid(jid)
                if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log("WAE_WRAP: destroy cleaned orphan cache jid=$jid")
            } catch (_: Exception) {}
        }
    }

    // ---- Flattened index ----

    private var messageIdToRealPos = HashMap<String, Int>()
    private var realPosToMessageId = HashMap<Int, String>()
    private var realPositionsSorted = IntArray(0)
    private var isNotifying = false

    private val realAdapterObserver = object : DataSetObserver() {
        override fun onChanged() {
            if (isNotifying) return
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            notifyDataSetInvalidated()
        }
    }

    init {
        lastCreated = WeakReference(this)
        if (jid.isNotBlank()) {
            instances[jid] = WeakReference(this)
            loadCacheFromDb()
        }
        try {
            realAdapter.registerDataSetObserver(realAdapterObserver)
        } catch (_: Exception) {}
    }

    private fun loadCacheFromDb() {
        if (jid.isBlank()) return
        try {
            val cached = TranslationCacheStore.getByJid(jid)
            val store = getBubbleStore(jid)
            cached.forEach { (msgId, translation) ->
                if (!store.hasBubbleFor(msgId, BubbleType.TRANSLATION)) {
                    store.addBubble(msgId, BubbleType.TRANSLATION, BubbleState.READY, translation)
                }
            }
        } catch (_: Exception) {}
    }

    fun saveCacheToDb(messageId: String, translation: String) {
        if (jid.isBlank()) return
        try { TranslationCacheStore.upsert(jid, messageId, translation) } catch (_: Exception) {}
    }

    fun deleteCacheFromDb(messageId: String) {
        if (jid.isBlank()) return
        try { TranslationCacheStore.delete(jid, messageId) } catch (_: Exception) {}
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
        val store = getBubbleStore(jid)
        if (jid.isNotBlank()) {
            // prune bubbles whose original message left the list (revoked/deleted)
            store.buildFlattenedIndex(messageIdToRealPos.keys.toList())
        }
        val anchorMessageIds = store.snapshot()
            .map { it.originalMessageId }
            .distinct()
        realPositionsSorted = anchorMessageIds
            .mapNotNull { messageIdToRealPos[it] }
            .sorted()
            .toIntArray()
        realPosToMessageId = HashMap<Int, String>().also { map ->
            anchorMessageIds.forEach { msgId ->
                messageIdToRealPos[msgId]?.let { pos -> map[pos] = msgId }
            }
        }
    }

    /**
     * Map wrapped position -> (isSynthetic, realPosition, bubble).
     * Synthetic slots sit immediately after their anchor message, in insertion
     * order; the offset accumulates the bubble counts of earlier anchors.
     */
    fun resolve(wrappedPos: Int): Triple<Boolean, Int, SyntheticBubble?> {
        val store = getBubbleStore(jid)
        var offset = 0
        for (i in realPositionsSorted.indices) {
            val rp = realPositionsSorted[i]
            val slotStart = rp + offset
            if (wrappedPos == slotStart) return Triple(false, rp, null)

            val msgId = realPosToMessageId[rp]
            val bubbles = if (msgId != null) store.bubblesFor(msgId) else emptyList()
            if (wrappedPos > slotStart && wrappedPos < slotStart + 1 + bubbles.size) {
                val localIdx = wrappedPos - slotStart - 1
                return Triple(true, rp, bubbles[localIdx])
            }
            // Ordering matters: subtract offset BEFORE adding this anchor's bubbles,
            // otherwise positions between anchors map to a shifted realPos (blank rows).
            if (wrappedPos < slotStart) return Triple(false, (wrappedPos - offset).coerceAtLeast(0), null)
            offset += bubbles.size
        }
        return Triple(false, (wrappedPos - offset).coerceAtLeast(0), null)
    }

    val realCount: Int get() = realAdapter.count

    override fun getCount(): Int {
        val extra = if (jid.isNotBlank()) getBubbleStore(jid).snapshot().size else 0
        return realCount + extra
    }

    override fun notifyDataSetChanged() {
        isNotifying = true
        super.notifyDataSetChanged()
        isNotifying = false
    }

    override fun getItem(pos: Int): Any? {
        val (isSynthetic, realPos, _) = resolve(pos)
        return if (isSynthetic) null else realAdapter.getItem(realPos)
    }

    override fun getItemId(pos: Int): Long {
        val (isSynthetic, realPos, _) = resolve(pos)
        return if (isSynthetic) Long.MIN_VALUE + realPos.toLong()
        else realAdapter.getItemId(realPos)
    }

    override fun hasStableIds(): Boolean = realAdapter.hasStableIds()

    override fun getViewTypeCount(): Int = realAdapter.viewTypeCount + 1

    override fun getItemViewType(pos: Int): Int {
        val (isSynthetic, realPos, _) = resolve(pos)
        return if (isSynthetic) android.widget.Adapter.IGNORE_ITEM_VIEW_TYPE
        else realAdapter.getItemViewType(realPos)
    }

    override fun isEnabled(pos: Int): Boolean {
        val (isSynthetic, realPos, _) = resolve(pos)
        return if (isSynthetic) false else realAdapter.isEnabled(realPos)
    }

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val (isSynthetic, realPos, bubble) = resolve(pos)

        if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log(
            "WAE_VIEW: pos=$pos isSynthetic=$isSynthetic realPos=$realPos type=${bubble?.type} state=${bubble?.state}"
        )

        if (!isSynthetic || bubble == null) {
            val clamped = realPos.coerceIn(0, (realCount - 1).coerceAtLeast(0))
            return realAdapter.getView(clamped, convertView, parent)
        }

        val context = parent.context
        val isFromMe = try {
            val raw = realAdapter.getItem(realPos) ?: return View(context)
            FMessageWpp(raw).key.isFromMe
        } catch (_: Exception) { false }

        return renderBubble(context, bubble, isFromMe, parent)
    }

    // ---- Bubble rendering (generic dispatch by BubbleType) ----

    private fun renderBubble(
        context: android.content.Context,
        bubble: SyntheticBubble,
        isFromMe: Boolean,
        parent: ViewGroup
    ): View {
        return try {
            when (bubble.type) {
                BubbleType.TRANSLATION -> when (bubble.state) {
                    BubbleState.LOADING -> buildTextBubble(context, "\u23F3 Menerjemahkan...", isFromMe)
                    BubbleState.READY -> buildTextBubble(context, "\uD83C\uDF10 ${bubble.content}", isFromMe)
                    BubbleState.ERROR -> buildTextBubble(
                        context,
                        "\u274C ${bubble.content.ifBlank { context.getString(R.string.translator_failed) }}",
                        isFromMe
                    )
                }

                BubbleType.TTS -> when (bubble.state) {
                    BubbleState.LOADING -> buildTtsLoadingBubble(context, isFromMe)
                    BubbleState.READY -> {
                        // reuse WhatsApp's own voice-note bubble when captured;
                        // otherwise a minimal built-in player
                        VoiceNoteViewCloner.bind(parent, bubble.content)
                            ?: buildTtsFallbackBubble(context, bubble.content, isFromMe)
                    }
                    BubbleState.ERROR -> buildTtsErrorBubble(context, bubble.content, isFromMe)
                }
            }
        } catch (t: Throwable) {
            if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log("WAE_VIEW render EX: ${t.message}")
            View(context).apply {
                layoutParams = ViewGroup.LayoutParams(0, 0)
                visibility = View.GONE
            }
        }
    }

    private fun buildTtsLoadingBubble(context: android.content.Context, isFromMe: Boolean): View {
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)

        val spinner = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp8 * 2, dp8 * 2)
        }

        val label = TextView(context).apply {
            text = context.getString(R.string.voice_tts_loading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val gravity = if (isFromMe) Gravity.END else Gravity.START
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = Gravity.CENTER_VERTICAL or gravity
            setPadding(dp8, dp4, dp8, dp4)
            addView(spinner)
            addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp4 }
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Fallback TTS player used until the WhatsApp voice-note layout is
     * captured by [VoiceNoteViewCloner]. Deliberately minimal: play/pause +
     * duration label, MediaPlayer bound to the cached file path.
     */
    private fun buildTtsFallbackBubble(
        context: android.content.Context,
        audioPath: String,
        isFromMe: Boolean
    ): View {
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)
        val dp12 = Utils.dipToPixels(12)

        val bgColor = if (isFromMe) Color.parseColor("#1A237E") else Color.parseColor("#1B5E20")
        val textColor = if (isFromMe) Color.parseColor("#E8EAF6") else Color.parseColor("#E8F5E9")

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = dp12.toFloat()
        }

        val playBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = context.getString(R.string.voice_tts_play)
        }

        val durationLabel = TextView(context).apply {
            text = "0:00"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textColor)
        }

        val player = MediaPlayer()
        var prepared = false
        var playing = false
        var fdHolder: ParcelFileDescriptor? = null

        playBtn.setOnClickListener {
            try {
                if (!playing) {
                    if (!prepared) {
                        player.reset()
                        // File di private dir modul: WhatsApp UID tidak bisa buka
                        // path langsung (ENOENT). Ambil fd dari proses app via bridge.
                        var openErr: String? = null
                        val bridge = try {
                            WppCore.getClientBridge()?.openFile(audioPath, false)
                        } catch (t: Throwable) {
                            openErr = t.message
                            null
                        }
                        if (com.mrksvt.waen.BuildConfig.DEBUG)
                            XposedBridge.log("WAE_TTS fallback src=${if (bridge != null) "bridge-fd" else "direct"} err=$openErr")
                        runCatching { fdHolder?.close() }
                        fdHolder = bridge
                        if (bridge != null) player.setDataSource(bridge.fileDescriptor)
                        else player.setDataSource(audioPath)
                        player.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        player.setOnCompletionListener {
                            playing = false
                            playBtn.setImageResource(android.R.drawable.ic_media_play)
                        }
                        player.setOnPreparedListener { mp ->
                            val totalSec = mp.duration / 1000
                            durationLabel.text = "%d:%02d".format(totalSec / 60, totalSec % 60)
                        }
                        player.prepare()
                        prepared = true
                    }
                    player.start()
                    playing = true
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    player.pause()
                    playing = false
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                }
            } catch (t: Throwable) {
                if (com.mrksvt.waen.BuildConfig.DEBUG) XposedBridge.log("WAE_TTS fallback play EX: ${t.message}")
                playing = false
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp8, dp4, dp8, dp4)
            background = bgDrawable
            addView(playBtn)
            addView(
                durationLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp4 }
            )
        }

        val gravity = if (isFromMe) Gravity.END else Gravity.START
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
            setPadding(dp8, dp4, dp8, dp4)
            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { this.gravity = gravity }
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildTtsErrorBubble(
        context: android.content.Context,
        reason: String,
        isFromMe: Boolean
    ): View {
        val msg = "\u274C ${reason.ifBlank { context.getString(R.string.voice_tts_failed) }}"
        return buildTextBubble(context, msg, isFromMe)
    }

    private fun buildTextBubble(
        context: android.content.Context,
        text: String,
        isFromMe: Boolean
    ): View {
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

        val tv = TextView(context).apply {
            this.text = text
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
            this.gravity = gravity
            setPadding(dp8, dp4, dp8, dp4)
            addView(tv)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ---- SectionIndexer ----

    override fun getSections(): Array<Any> =
        (realAdapter as? SectionIndexer)?.sections ?: emptyArray()

    override fun getPositionForSection(sectionIndex: Int): Int {
        val realPos = (realAdapter as? SectionIndexer)?.getPositionForSection(sectionIndex) ?: 0
        val store = getBubbleStore(jid)
        var offset = 0
        for (rp in realPositionsSorted) {
            if (rp >= realPos) break
            val msgId = realPosToMessageId[rp]
            offset += if (msgId != null) store.bubblesFor(msgId).size else 0
        }
        return realPos + offset
    }

    override fun getSectionForPosition(position: Int): Int {
        val (_, realPos, _) = resolve(position)
        return (realAdapter as? SectionIndexer)?.getSectionForPosition(realPos) ?: 0
    }
}
