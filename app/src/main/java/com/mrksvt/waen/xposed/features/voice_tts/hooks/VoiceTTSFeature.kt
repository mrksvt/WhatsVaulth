package com.mrksvt.waen.xposed.features.voice_tts.hooks

import android.app.Activity
import android.content.SharedPreferences
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
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
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun doHook() {
        if (!prefs.getBoolean("contact_voice_tts", false)) return

        VoiceNoteViewCloner.installCaptureHook()
        try { NotificationPlayHelper.install(Utils.application) } catch (t: Throwable) {
            logDebug("NotificationPlayHelper.install failed: ${t.message}")
        }

        // --- Tugas A: hook incoming media messages ---
        try { hookVoiceNoteCapture() } catch (t: Throwable) {
            logDebug("hookVoiceNoteCapture setup failed: ${t.message}")
        }

        // --- Tugas D: synthetic bubble injection on item bind ---
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

                override fun onAttachAdapter(adapter: ListAdapter?) {
                    // store persists per jid; wrapper adapter rebuilds its index on attach
                }
            }
        )

        // --- Tugas E#1: tap popup with "Dengarkan" option ---
        hookBubbleTap()

        // --- Tugas E#2: conversation header3-dot menu items ---
        try { hookConversationOptionsMenu() } catch (t: Throwable) {
            logDebug("hookConversationOptionsMenu setup failed: ${t.message}")
        }

        // --- Tugas E#3: auto-TTS on incoming message ---
        try { hookAutoTtsOnIncoming() } catch (t: Throwable) {
            logDebug("hookAutoTtsOnIncoming setup failed: ${t.message}")
        }
    }

    // ========================================================================
    // Tugas A: Voice Note Capture Hook
    // ========================================================================

    /**
     * Hooks loadNewMessageWithMediaMethod (same pattern as AntiRevoke) to
     * intercept incoming voice notes before they are further processed by WA.
     *
     * For every incoming voice note from a contact:
     *   1. Compute SHA-256 of audio file (dedup guard)
     *   2. Copy file to app-side storage via bridge (Utils.copyFile)
     *   3. Call bridge.registerIncomingVoiceNote(metadata) -> WorkManager enqueue
     */
    private fun hookVoiceNoteCapture() {
        val method = Unobfuscator.loadNewMessageWithMediaMethod(classLoader)
        log("hookVoiceNoteCapture: ${method.declaringClass.name}.${method.name}")

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val fMessageObj = if (FMessageWpp.TYPE.isInstance(param.thisObject)) {
                        param.thisObject
                    } else {
                        ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0)
                    } ?: return
                    val fMessage = FMessageWpp(fMessageObj)

                    if (!fMessage.isMediaFile) return
                    val mediaType = fMessage.mediaType ?: return
                    // voice note: mediaType == 2 (voice) or 82 (view-once voice)
                    if (mediaType != 2 && mediaType != 82) return

                    // only incoming messages (not from us)
                    if (fMessage.key.isFromMe) return

                    val contactId = extractContactId(fMessage) ?: return
                    val msgId = fMessage.key.messageID ?: return
                    val file = fMessage.mediaFile ?: return

                    val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
                    if (bridge == null) {
                        logDebug("Bridge unavailable, cannot copy voice note")
                        return
                    }

                    // Copy + register di background thread: hash membaca seluruh
                    // file, tidak boleh memblokir thread pemanggil WhatsApp.
                    val destFolder = "/data/data/${com.mrksvt.waen.BuildConfig.APPLICATION_ID}/files/voice_notes"
                    val destName = "${contactId}_${msgId}.opus"
                    val destPath = "$destFolder/$destName"
                    val durationMs = 0L

                    Utils.executor.execute {
                        try {
                            // Guard duplikat sisi hook: file tujuan sudah ada ->
                            // voice note ini pernah diproses, lewati copy & register.
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
                                contactId, fileHash, destPath, durationMs, System.currentTimeMillis()
                            )
                            if (err.isNotEmpty()) {
                                logDebug("registerIncomingVoiceNote error: $err")
                            } else {
                                logDebug("Voice note registered: contact=$contactId hash=${fileHash.take(12)}")
                            }
                        } catch (t: Throwable) {
                            logDebug("Voice note capture background error: ${t.message}")
                        }
                    }
                } catch (t: Throwable) {
                    logDebug("hookVoiceNoteCapture afterHookedMethod error: ${t.message}")
                }
            }
        })
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
        val messageText = fMessage.messageStr ?: return
        if (messageText.isBlank()) return

        val jid = extractContactId(fMessage) ?: ""

        val anchor = view.findViewById<TextView>(getAudioViewId("message_text")) ?: return

        anchor.setOnClickListener {
            if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return@setOnClickListener
            showTtsPopup(anchor, view, messageText, messageId, fMessage.key.isFromMe, jid)
        }
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

        popup.menu.add(0, 1, 0, anchor.context.getString(R.string.voice_tts_listen))

        if (bubbleStoreFor(conversationJid).hasBubbleFor(messageId, BubbleType.TTS)) {
            popup.menu.add(0, 2, 1, anchor.context.getString(R.string.voice_tts_hide))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    triggerTts(rootView, messageText, messageId, conversationJid, isFromMe)
                    true
                }
                2 -> {
                    bubbleStoreFor(conversationJid).removeBubblesForMessage(messageId)
                    TranslatorWrapperAdapter.refreshBubbles(conversationJid)
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
        bubble: SyntheticBubble
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
                            bubble.originalMessageId, text, expectedPath
                        )
                    } catch (t: Throwable) {
                        logDebug("cacheForNotificationPlayback EX: ${t.message}")
                    }
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

        // Hook onCreateOptionsMenu to inject "Auto TTS" menu item
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

                    // JID percakapan aktif via WppCore (sudah dipakai fitur lain)
                    val conversationJid = try {
                        WppCore.getCurrentUserJid()?.userRawString
                    } catch (_: Exception) { null }
                    if (conversationJid.isNullOrBlank()) return

                    val group = Menu.FIRST + 99 // unique group
                    val autoTtsItem = menu.add(group, 1001, 100,
                        activity.getString(R.string.voice_tts_auto_for_contact))

                    // Check current state
                    val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
                    val isEnabled = try {
                        bridge?.isAutoTtsEnabled(conversationJid) == 1
                    } catch (_: Exception) { false }

                    autoTtsItem.setCheckable(true)
                    autoTtsItem.setChecked(isEnabled)

                    // Show voice profile status hint
                    val hasProfile = try {
                        bridge?.getContactVoiceProfileStatus(conversationJid) == 1
                    } catch (_: Exception) { false }
                    autoTtsItem.setTitle(if (hasProfile) {
                        activity.getString(R.string.voice_tts_auto_for_contact)
                    } else {
                        activity.getString(R.string.voice_tts_no_profile_hint)
                    })

                    autoTtsItem.setOnMenuItemClickListener { _ ->
                        try {
                            val newState = if (isEnabled) 0 else 1
                            bridge?.setAutoTtsEnabled(conversationJid, newState)
                            autoTtsItem.setChecked(!isEnabled)
                            Utils.showToast(
                                if (!isEnabled)
                                    activity.getString(R.string.voice_tts_profile_hint)
                                else
                                    "Auto TTS dinonaktifkan"
                            )
                        } catch (t: Throwable) {
                            logDebug("Auto TTS toggle error: ${t.message}")
                        }
                        true
                    }
                } catch (t: Throwable) {
                    logDebug("onCreateOptionsMenu hook error: ${t.message}")
                }
            }
        })
    }

    // ========================================================================
    // Tugas E#3: Auto-TTS on incoming message
    // ========================================================================

    private fun hookAutoTtsOnIncoming() {
        val method = Unobfuscator.loadNewMessageWithMediaMethod(classLoader)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val fMessageObj = if (FMessageWpp.TYPE.isInstance(param.thisObject)) {
                        param.thisObject
                    } else {
                        ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0)
                    } ?: return
                    val fMessage = FMessageWpp(fMessageObj)

                    if (fMessage.key.isFromMe) return
                    val contactId = extractContactId(fMessage) ?: return
                    val messageText = fMessage.messageStr ?: return
                    if (messageText.isBlank()) return
                    val messageId = fMessage.key.messageID ?: return

                    val bridge = try { WppCore.getClientBridge() } catch (_: Exception) { null }
                    val isAuto = try {
                        bridge?.isAutoTtsEnabled(contactId) == 1
                    } catch (_: Exception) { false }
                    if (!isAuto) return

                    logDebug("Auto TTS triggered for contact=$contactId msg=$messageId")

                    rememberRequestedText(messageId, messageText)
                    NotificationPlayHelper.markRequested(messageId, messageText)
                    val store = bubbleStoreFor(contactId)
                    val bubble = store.addBubble(messageId, BubbleType.TTS, BubbleState.LOADING)
                    TranslatorWrapperAdapter.refreshBubbles(contactId)

                    try {
                        bridge?.requestTTS(contactId, messageId, messageText)
                    } catch (t: Throwable) {
                        logDebug("Auto TTS requestTTS failed: ${t.message}")
                        store.updateBubble(bubble.bubbleId, BubbleState.ERROR, t.message ?: "IPC error")
                        TranslatorWrapperAdapter.requestBubbleRefresh(contactId)
                        return
                    }

                    startAudioPoll(contactId, messageText, bubble)
                } catch (t: Throwable) {
                    logDebug("hookAutoTtsOnIncoming error: ${t.message}")
                }
            }
        })
    }

    // ========================================================================
    // Tugas E#1 (continued): onFinishInflate hook for bubble tap
    // ========================================================================

    private fun hookBubbleTap() {
        val targetId = Utils.getID("conversation_text_row", "id")
        if (targetId == 0) return

        XposedBridge.hookAllMethods(
            View::class.java, "onFinishInflate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val frameLayout = param.thisObject as? ViewGroup ?: return
                    if (frameLayout.id != targetId) return
                    attachTtsTrigger(frameLayout)
                }
            }
        )
    }

    private fun attachTtsTrigger(frameLayout: ViewGroup) {
        val messageTextView = frameLayout.findViewById<TextView>(getAudioViewId("message_text"))
            ?: return
        val messageText = messageTextView.text?.toString()
        if (messageText.isNullOrBlank()) return

        messageTextView.setOnClickListener {
            val currentText = messageTextView.text?.toString() ?: return@setOnClickListener
            if (currentText.isBlank()) return@setOnClickListener

            val boundItem = ConversationItemListener.listItems[frameLayout]
            val messageId = boundItem?.messageId ?: return@setOnClickListener
            val isFromMe = boundItem.message.key.isFromMe
            val conversationJid = extractContactId(boundItem.message) ?: return@setOnClickListener

            showTtsPopup(messageTextView, frameLayout, currentText, messageId, isFromMe, conversationJid)
        }
    }

    override fun getPluginName(): String = "Contact Voice TTS"

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
