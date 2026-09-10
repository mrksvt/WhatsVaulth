package com.mrksvt.waen.xposed.features.voice_tts.hooks

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.features.listeners.ConversationItemListener
import com.mrksvt.waen.xposed.features.others.TranslatorWrapperAdapter
import com.mrksvt.waen.xposed.features.voice_tts.core.BubbleState
import com.mrksvt.waen.xposed.features.voice_tts.core.BubbleType
import com.mrksvt.waen.xposed.features.voice_tts.core.SyntheticBubble
import com.mrksvt.waen.xposed.features.voice_tts.core.SyntheticBubbleStore
import com.mrksvt.waen.xposed.features.voice_tts.core.TtsCachePaths
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import com.mrksvt.waen.xposed.features.others.GoogleTranslate
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Contact Voice TTS (Tugas A, D, E)
 *
 * Runs in the com.whatsapp process. Uses the same SyntheticBubbleStore
 * architecture as the generic bubble system.
 *
 * Hook points:
 *   - loadNewMessageWithMediaMethod        (voice note capture, Tugas A)
 *   - ConversationItemListener              (balon sintetis, Tugas D)
 *   - conversation_text_row onFinishInflate (popup "Dengarkan", Tugas E#1)
 *   - Conversation Activity onCreateOptionsMenu (menu titik-3, Tugas E#2)
 *   - loadNotificationMethod               (auto-TTS, Tugas E#3)
 */
class VoiceTTSFeature(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    companion object {
        private const val MAX_INCOMING_HOOK_DEPTH = 3

        private const val ITEM_LISTEN = 1
        private const val ITEM_HIDE_TTS = 2
        private const val ITEM_TRANSLATE = 3
        private const val ITEM_HIDE_TRANSLATION = 4
        private const val ITEM_TRAIN_VOICE = 5
        private const val AUTO_TTS_MENU_ID = 1001

        /** Shared generic bubble store per conversation (same one the wrapper adapter renders). */
        @JvmStatic
        fun bubbleStoreFor(jid: String): SyntheticBubbleStore =
            TranslatorWrapperAdapter.getBubbleStore(jid)

        /** messageId -> text for auto-TTS requests in flight (notif matching). */
        private val requestedTexts = ConcurrentHashMap<String, String>()

        @JvmStatic
        fun bubbleTextFor(messageId: String): String? = requestedTexts[messageId]

        private fun rememberRequestedText(messageId: String, text: String) {
            requestedTexts[messageId] = text
            if (requestedTexts.size > 300) {
                val it = requestedTexts.keys.iterator()
                while (it.hasNext() && requestedTexts.size > 200) {
                    it.next()
                    it.remove()
                }
            }
        }

        private val audioIds = ConcurrentHashMap<String, Int>()

        fun getAudioViewId(name: String): Int {
            return audioIds.getOrPut(name) {
                val id = Utils.getID(name, "id")
                if (id == 0) -1 else id
            }
        }

        private val autoTtsCache = ConcurrentHashMap<String, Boolean>()

        @JvmStatic
        fun putAutoTtsCache(contactId: String, enabled: Boolean) {
            autoTtsCache[contactId] = enabled
        }
    }

    private fun isAutoTtsCached(contactId: String): Boolean {
        autoTtsCacheHit(contactId)?.let { return it }
        val enabled = try {
            WppCore.getClientBridge()?.isAutoTtsEnabled(contactId) == 1
        } catch (_: Exception) { false }
        Companion.autoTtsCache[contactId] = enabled
        return enabled
    }

    private fun autoTtsCacheHit(contactId: String): Boolean? = Companion.autoTtsCache[contactId]

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Guard re-entrancy: FMessageWpp.messageStr() meng-invoke method yang sama
    // dengan target hook ini (loadNewMessageWithMediaMethod dipakai di
    // FMessageWpp.messageWithMediaMethod). Method.invoke dari getter tidak
    // bypass hook chain LSPosed, tanpa guard afterHookedMethod saling panggil
    // terus-menerus sampai StackOverflow.
    private val incomingHookDepth = ThreadLocal.withInitial { 0 }

    override fun doHook() {
        if (!prefs.getBoolean("contact_voice_tts", false)) return

        VoiceNoteViewCloner.installCaptureHook()
        try { NotificationPlayHelper.install(Utils.application) } catch (t: Throwable) {
            logDebug("NotificationPlayHelper.install failed: ${t.message}")
        }

        ConversationItemListener.conversationListeners.add(
            object : ConversationItemListener.OnConversationItemListener() {
                override fun onItemBind(
                    fMessage: FMessageWpp,
                    view: ViewGroup,
                    position: Int,
                    convertView: View?
                ) {
                    try {
                        handleItemBind(fMessage, view, position)
                    } catch (t: Throwable) {
                        logDebug("handleItemBind EX: ${t.message}")
                    }
                }

                override fun onAttachAdapter(adapter: ListAdapter?) {}
            }
        )

        // --- Tugas A + E#3: satu hook gabungan untuk media masuk & pesan teks masuk ---
        try { hookIncomingMessages() } catch (t: Throwable) {
            logDebug("hookIncomingMessages setup failed: ${t.message}")
        }

        // --- Tugas E#2: conversation header 3-dot menu items ---
        try { hookConversationOptionsMenu() } catch (t: Throwable) {
            logDebug("hookConversationOptionsMenu setup failed: ${t.message}")
        }
    }

    private fun hookIncomingMessages() {
        val targets: List<java.lang.reflect.Method> = try {
            Unobfuscator.loadMessageTextGetterOverrides(classLoader).toList()
        } catch (_: Throwable) {
            listOf(Unobfuscator.loadNewMessageWithMediaMethod(classLoader))
        }
        log("hookIncomingMessages: ${targets.joinToString { "${it.declaringClass.name}.${it.name}" }}")

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val depth = incomingHookDepth.get() ?: 0
                if (depth >= MAX_INCOMING_HOOK_DEPTH) {
                    log("hookIncomingMessages: maximum re-entry depth reached, aborting nested TTS processing")
                    return
                }
                if (depth > 0) {
                    logDebug("hookIncomingMessages: re-entry detected at depth=$depth, skipping nested invocation")
                    return
                }
                incomingHookDepth.set(depth + 1)
                try {
                    processIncomingMessage(param)
                } catch (t: Throwable) {
                    logDebug("hookIncomingMessages error: ${t.message}")
                } finally {
                    incomingHookDepth.set(depth)
                }
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, callback) }
    }

    private fun processIncomingMessage(param: XC_MethodHook.MethodHookParam) {
        val fMessageObj = if (FMessageWpp.TYPE.isInstance(param.thisObject)) {
            param.thisObject
        } else {
            ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0)
        } ?: run {
            logDebug(
                "hookIncomingMessages: no FMessage in this/args (this=${param.thisObject?.javaClass?.name}, args=${param.args?.size})"
            )
            return
        }
        val fMessage = FMessageWpp(fMessageObj)
        if (fMessage.key.isFromMe) return
        val contactId = extractContactId(fMessage) ?: return

        logDebug("hookIncomingMessages: processing contact=$contactId")
        if (fMessage.isMediaFile) return
        autoTtsIfEnabled(fMessage, contactId)
    }

    private fun captureVoiceNote(
        fMessage: FMessageWpp,
        contactId: String,
        notifyUser: Boolean = false
    ) {
        val mediaType = fMessage.mediaType ?: return
        if (mediaType != 2 && mediaType != 82) return
        val msgId = fMessage.key.messageID ?: return
        val file = fMessage.mediaFile ?: run {
            if (notifyUser) toastOnMain(R.string.voice_tts_train_voice_note_failed)
            return
        }
        val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
        if (bridge == null) {
            logDebug("Bridge unavailable, cannot copy voice note")
            if (notifyUser) toastOnMain(R.string.voice_tts_train_voice_note_failed)
            return
        }
        val destFolder = "/data/data/${com.mrksvt.waen.BuildConfig.APPLICATION_ID}/files/voice_notes"
        val destName = "${contactId}_${msgId}.opus"
        val destPath = "$destFolder/$destName"

        Utils.executor.execute {
            try {
                if (bridge.exists(destPath)) {
                    logDebug("Voice note already captured, skip: $destName")
                    return@execute
                }
                val fileHash = computeFileHash(file)
                val error = Utils.copyFile(file, destFolder, destName)
                if (!error.isNullOrEmpty()) {
                    logDebug("Voice note copy error: $error")
                    return@execute
                }
                val err = bridge.registerIncomingVoiceNote(
                    contactId, fileHash, destPath, 0L, System.currentTimeMillis()
                )
                if (err.isNotEmpty()) {
                    logDebug("registerIncomingVoiceNote error: $err")
                    if (notifyUser) toastOnMain(R.string.voice_tts_train_voice_note_failed)
                } else {
                    logDebug("Voice note registered: contact=$contactId hash=${fileHash.take(12)}")
                    if (notifyUser) toastOnMain(R.string.voice_tts_train_voice_note_saved)
                }
            } catch (t: Throwable) {
                logDebug("Voice note capture background error: ${t.message}")
            }
        }
    }

    private fun toastOnMain(msgRes: Int) {
        mainHandler.post {
            Toast.makeText(Utils.application, msgRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoTtsIfEnabled(fMessage: FMessageWpp, contactId: String) {
        val messageId = fMessage.key.messageID ?: return
        // messageStr meng-invoke method yang sama dengan target hook: baca
        // teks LAST, setelah semua guard murah, supaya panggilan getter
        // ke method ter-hook tidak terjadi untuk pesan yang pasti dilewati.
        if (!isAutoTtsCached(contactId)) return
        val store = bubbleStoreFor(contactId)
        if (store.hasBubbleFor(messageId, BubbleType.TTS)) return
        val messageText = fMessage.messageStr ?: return
        if (messageText.isBlank()) return

        logDebug("Auto TTS triggered for contact=$contactId msg=$messageId")
        rememberRequestedText(messageId, messageText)
        NotificationPlayHelper.markRequested(messageId, messageText)
        val bubble = store.addBubble(messageId, BubbleType.TTS, BubbleState.LOADING)
        TranslatorWrapperAdapter.refreshBubbles(contactId)

        val contactName = try {
            val n = WppCore.getContactName(fMessage.key.remoteJid)
            if (n.isBlank() || n == "Whatsapp Contact")
                WppCore.getAddressBookName(fMessage.key.remoteJid.userRawString)
            else n
        } catch (_: Exception) { null }
        val speakText = if (contactName.isNullOrBlank() || contactName == "Whatsapp Contact")
            messageText
        else
            "$contactName, $messageText"

        val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
        try {
            bridge?.requestTTS(contactId, messageId, speakText)
        } catch (t: Throwable) {
            logDebug("Auto TTS requestTTS failed: ${t.message}")
            store.updateBubble(bubble.bubbleId, BubbleState.ERROR, t.message ?: "IPC error")
            TranslatorWrapperAdapter.requestBubbleRefresh(contactId)
            return
        }

        startAudioPoll(contactId, speakText, bubble, matchText = messageText, autoPlay = true)
    }

    private fun extractContactId(fMessage: FMessageWpp): String? {
        return try {
            val raw = fMessage.key.remoteJid.userRawString
            if (raw.isNullOrBlank()) null else raw
        } catch (_: Exception) { null }
    }

    private fun computeFileHash(file: java.io.File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(8192)
                var n: Int
                while (true) {
                    n = ins.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "${file.name}_${file.length()}" }
    }

    // ========================================================================
    // Tugas D: Item bind -> inject synthetic TTS bubbles
    // ========================================================================

    private fun handleItemBind(fMessage: FMessageWpp, view: ViewGroup, position: Int) {
        val messageId = fMessage.key.messageID ?: return
        val jid = extractContactId(fMessage) ?: ""

        // Trigger di label durasi, bukan bubble root: long-press di root
        // dipakai WA untuk menu konteks native (reply/forward) - jangan dibajak.
        val durationView = view.findViewById<View>(getAudioViewId("audio_file_duration"))
        if (durationView != null) {
            val mediaType = fMessage.mediaType ?: -1
            if ((mediaType == 2 || mediaType == 82) && !fMessage.key.isFromMe && jid.isNotEmpty()) {
                durationView.setOnLongClickListener {
                    if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return@setOnLongClickListener false
                    showVoiceNotePopup(durationView, fMessage, jid)
                    true
                }
            } else {
                durationView.setOnLongClickListener(null)
            }
        }

        val messageText = fMessage.messageStr ?: return
        if (messageText.isBlank()) return

        val anchor = view.findViewById<TextView>(getAudioViewId("message_text")) ?: return

        anchor.setOnClickListener {
            if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return@setOnClickListener
            showTtsPopup(anchor, view, messageText, messageId, fMessage.key.isFromMe, jid)
        }
    }

    private fun showVoiceNotePopup(anchor: View, fMessage: FMessageWpp, contactId: String) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menu.add(
            0, ITEM_TRAIN_VOICE, 0,
            anchor.context.getString(R.string.voice_tts_train_voice_note)
        )
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == ITEM_TRAIN_VOICE) {
                captureVoiceNote(fMessage, contactId, notifyUser = true)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    // ========================================================================
    // Tugas E#1: Popup "Dengarkan" / "Baca dengan Suara"
    // ========================================================================

    private fun showTtsPopup(
        anchor: View,
        rootView: ViewGroup,
        messageText: String,
        messageId: String,
        isFromMe: Boolean,
        conversationJid: String
    ) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.gravity = if (isFromMe) Gravity.END else Gravity.START

        var order = 0
        val gt = GoogleTranslate.instance
        if (gt != null && prefs.getBoolean("google_translate", false)) {
            popup.menu.add(0, ITEM_TRANSLATE, order++,
                anchor.context.getString(R.string.translator_action_translate))
            if (TranslatorWrapperAdapter.hasTranslation(conversationJid, messageId)) {
                popup.menu.add(0, ITEM_HIDE_TRANSLATION, order++,
                    anchor.context.getString(R.string.translator_action_hide))
            }
        }

        popup.menu.add(0, ITEM_LISTEN, order++, anchor.context.getString(R.string.voice_tts_listen))

        if (bubbleStoreFor(conversationJid).hasBubbleFor(messageId, BubbleType.TTS)) {
            popup.menu.add(0, ITEM_HIDE_TTS, order++, anchor.context.getString(R.string.voice_tts_hide))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ITEM_LISTEN -> {
                    triggerTts(rootView, messageText, messageId, conversationJid, isFromMe)
                    true
                }
                ITEM_HIDE_TTS -> {
                    bubbleStoreFor(conversationJid).removeBubblesForMessage(messageId)
                    TranslatorWrapperAdapter.refreshBubbles(conversationJid)
                    true
                }
                ITEM_TRANSLATE -> {
                    gt?.triggerTranslate(rootView, messageText, messageId, conversationJid, isFromMe)
                    true
                }
                ITEM_HIDE_TRANSLATION -> {
                    TranslatorWrapperAdapter.hideTranslation(conversationJid, messageId)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun triggerTts(
        rootView: ViewGroup,
        messageText: String,
        messageId: String,
        conversationJid: String,
        isFromMe: Boolean
    ) {
        logDebug("triggerTts jid=$conversationJid msg=${messageText.take(30)}")

        val store = bubbleStoreFor(conversationJid)
        val bubble = store.addBubble(messageId, BubbleType.TTS, BubbleState.LOADING)
        TranslatorWrapperAdapter.refreshBubbles(conversationJid)
        rememberRequestedText(messageId, messageText)
        NotificationPlayHelper.markRequested(messageId, messageText)

        val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
        if (bridge == null) {
            store.updateBubble(bubble.bubbleId, BubbleState.ERROR, "Bridge not connected")
            TranslatorWrapperAdapter.requestBubbleRefresh(conversationJid)
            return
        }

        try {
            val err = bridge.requestTTS(conversationJid, messageId, messageText)
            if (err.isNotBlank()) {
                store.updateBubble(bubble.bubbleId, BubbleState.ERROR, err)
                TranslatorWrapperAdapter.requestBubbleRefresh(conversationJid)
                return
            }
        } catch (t: Throwable) {
            store.updateBubble(bubble.bubbleId, BubbleState.ERROR, t.message ?: "IPC error")
            TranslatorWrapperAdapter.requestBubbleRefresh(conversationJid)
            return
        }

        startAudioPoll(conversationJid, messageText, bubble)
    }

    private fun startAudioPoll(
        contactId: String,
        text: String,
        bubble: SyntheticBubble,
        matchText: String = text,
        autoPlay: Boolean = false
    ) {
        val textHash = sha256Hex(text)
        val expectedPath = TtsCachePaths.cacheFile(contactId, textHash)
        val store = bubbleStoreFor(contactId)

        val pollRunnable = object : Runnable {
            var attempts = 0
            override fun run() {
                attempts++
                if (audioReady(expectedPath)) {
                    store.updateBubble(bubble.bubbleId, BubbleState.READY, expectedPath)
                    TranslatorWrapperAdapter.requestBubbleRefresh(contactId)
                    try {
                        NotificationPlayHelper.cacheForNotificationPlayback(
                            bubble.originalMessageId, matchText, expectedPath
                        )
                    } catch (t: Throwable) {
                        logDebug("cacheForNotificationPlayback EX: ${t.message}")
                    }
                    if (autoPlay) playViaModule(expectedPath, bubble.originalMessageId)
                    return
                }
                if (attempts > 60) { // 30 seconds max
                    store.updateBubble(bubble.bubbleId, BubbleState.ERROR, "Timeout generating audio")
                    TranslatorWrapperAdapter.requestBubbleRefresh(contactId)
                    return
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        mainHandler.postDelayed(pollRunnable, 500)
    }

    private fun playViaModule(audioPath: String, messageId: String) {
        try {
            val intent = android.content.Intent(
                com.mrksvt.waen.receivers.TtsPlayReceiver.ACTION_PLAY_TTS
            ).setClassName(
                com.mrksvt.waen.BuildConfig.APPLICATION_ID,
                com.mrksvt.waen.receivers.TtsPlayReceiver::class.java.name
            ).putExtra(com.mrksvt.waen.receivers.TtsPlayReceiver.EXTRA_AUDIO_PATH, audioPath)
                .putExtra(com.mrksvt.waen.receivers.TtsPlayReceiver.EXTRA_MESSAGE_ID, messageId)
            Utils.application.sendBroadcast(intent)
            logDebug("Auto TTS play broadcast sent for msg=$messageId")
        } catch (t: Throwable) {
            logDebug("playViaModule EX: ${t.message}")
        }
    }

    /**
     * Cek keberadaan file cache via bridge.exists() (IPC ringan, tanpa listFiles
     * yang bisa kena stale cache). Worker menulis .wav lalu men-rename dari .tmp,
     * jadi file yang sudah muncul dijamin utuh.
     */
    private fun audioReady(path: String): Boolean {
        return try {
            val bridge = WppCore.getClientBridge() ?: return false
            bridge.exists(path)
        } catch (_: Exception) { false }
    }

    // ========================================================================
    // Tugas E#2: Conversation header menu (3-dot menu)
    // ========================================================================

    private fun hookConversationOptionsMenu() {
        val conversationClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, "Conversation"
        ) ?: run {
            logDebug("Conversation class not found for menu hook")
            return
        }

        val onCreateOptionsMenu = try {
            conversationClass.getDeclaredMethod("onCreateOptionsMenu", Menu::class.java)
        } catch (_: NoSuchMethodException) {
            logDebug("onCreateOptionsMenu not found on Conversation")
            return
        }

        XposedBridge.hookMethod(onCreateOptionsMenu, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val activity = param.thisObject as? Activity ?: return
                    val menu = param.args[0] as? Menu ?: return
                    val conversationJid = currentConversationJid() ?: return

                    val autoTtsItem = menu.add(Menu.FIRST + 99, AUTO_TTS_MENU_ID, 100,
                        activity.getString(R.string.voice_tts_auto_for_contact))
                    autoTtsItem.setCheckable(true)
                    refreshAutoTtsItem(autoTtsItem, conversationJid, activity)

                    autoTtsItem.setOnMenuItemClickListener {
                        toggleAutoTts(conversationJid, autoTtsItem, activity)
                        true
                    }
                } catch (t: Throwable) {
                    logDebug("onCreateOptionsMenu hook error: ${t.message}")
                }
            }
        })

        // WA biasanya hanya memanggil onCreate sekali per Activity; tanpa refresh
        // di prepare, checklist menampilkan state basi setelah toggle/return.
        try {
            val onPrepare = conversationClass.getDeclaredMethod(
                "onPrepareOptionsMenu", Menu::class.java
            )
            XposedBridge.hookMethod(onPrepare, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val activity = param.thisObject as? Activity ?: return
                        val menu = param.args[0] as? Menu ?: return
                        val item = menu.findItem(AUTO_TTS_MENU_ID) ?: return
                        val jid = currentConversationJid() ?: return
                        refreshAutoTtsItem(item, jid, activity)
                    } catch (_: Throwable) { }
                }
            })
        } catch (_: NoSuchMethodException) {
            logDebug("onPrepareOptionsMenu not found; checklist tidak auto-refresh")
        }
    }

    private fun currentConversationJid(): String? {
        return try {
            WppCore.getCurrentUserJid()?.userRawString?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isAutoTtsNow(jid: String): Boolean {
        autoTtsCacheHit(jid)?.let { return it }
        val db = try {
            WppCore.getClientBridge()?.isAutoTtsEnabled(jid) == 1
        } catch (_: Throwable) {
            false
        }
        putAutoTtsCache(jid, db)
        return db
    }

    private fun refreshAutoTtsItem(item: MenuItem, jid: String, activity: Activity) {
        item.setChecked(isAutoTtsNow(jid))
        val hasProfile = try {
            WppCore.getClientBridge()?.getContactVoiceProfileStatus(jid) == 1
        } catch (_: Throwable) {
            false
        }
        item.setTitle(if (hasProfile) {
            activity.getString(R.string.voice_tts_auto_for_contact)
        } else {
            activity.getString(R.string.voice_tts_no_profile_hint)
        })
    }

    private fun toggleAutoTts(jid: String, item: MenuItem, activity: Activity) {
        val bridge = try { WppCore.getClientBridge() } catch (_: Throwable) { null }
        if (bridge == null) {
            logDebug("AutoTts toggle: bridge NULL jid=$jid")
            Utils.showToast("Gagal menyimpan pengaturan Auto TTS")
            return
        }
        try {
            val newState = if (isAutoTtsNow(jid)) 0 else 1
            val rc = bridge.setAutoTtsEnabled(jid, newState)
            val readBack = try { bridge.isAutoTtsEnabled(jid) } catch (_: Throwable) { -2 }
            logDebug("AutoTts toggle jid=$jid write=$newState rc=$rc readBack=$readBack")
            if (rc != 0 || readBack != newState) {
                Utils.showToast("Gagal menyimpan pengaturan Auto TTS")
                return
            }
            putAutoTtsCache(jid, newState == 1)
            item.setChecked(newState == 1)
            Utils.showToast(
                if (newState == 1)
                    activity.getString(R.string.voice_tts_profile_hint)
                else
                    activity.getString(R.string.voice_tts_auto_off)
            )
        } catch (t: Throwable) {
            logDebug("Auto TTS toggle error: ${t.message}")
        }
    }

    override fun getPluginName(): String = "Contact Voice TTS"

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
