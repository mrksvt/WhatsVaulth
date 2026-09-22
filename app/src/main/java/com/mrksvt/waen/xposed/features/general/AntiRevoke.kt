package com.mrksvt.waen.xposed.features.general

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.components.FStatusWpp
import com.mrksvt.waen.xposed.core.components.StatusItemWpp
import com.mrksvt.waen.xposed.core.components.WaContactWpp
import com.mrksvt.waen.xposed.core.db.DelMessageStore
import com.mrksvt.waen.xposed.core.db.MessageStore
import com.mrksvt.waen.xposed.core.db.entity.DelMessage
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.core.devkit.UnobfuscatorCache
import com.mrksvt.waen.xposed.features.listeners.ConversationItemListener
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import com.mrksvt.waen.BuildConfig
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AntiRevoke(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private val savedMediaPaths = ConcurrentHashMap<String, String>()

        // Per-row async lookup caches. Each visible row does one async point-query
        // on the composite (jid,msgid) index; results are memoized. Both caches are
        // size-bounded, so RAM stays constant regardless of revoke volume.
        private const val TIMESTAMP_CACHE_SIZE = 2048
        private val timestampCache = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, Long>(128, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) =
                    size > TIMESTAMP_CACHE_SIZE
            }
        )

        // Caching "not revoked" is what stops the scroll hot-path from issuing one
        // DB query per visible non-revoked row; do not remove.
        private const val NEGATIVE_CACHE_SIZE = 8192
        private val negativeCache = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, Boolean>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) =
                    size > NEGATIVE_CACHE_SIZE
            }
        )

        private fun cacheKey(jid: String, msgid: String): String = "$jid\u0000$msgid"

        @JvmStatic
        fun clearCaches() {
            timestampCache.clear()
            negativeCache.clear()
        }

        // Coalesce revoke bursts so the NDJSON dump is not rewritten per event.
        private const val TRASH_WRITE_DEBOUNCE_MS = 1500L
        private const val TRASH_DUMP_PAGE_SIZE = 512
        private val trashWriteGeneration = AtomicInteger(0)
        private val trashWriteScheduler =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "WhatsVault-TrashCacheWrite").apply { isDaemon = true }
            }

        private val dateFormatThreadLocal = ThreadLocal.withInitial {
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Utils.application.resources.configuration.locales[0]
            )
        }

        private fun findObjectFMessage(param: XC_MethodHook.MethodHookParam): FMessageWpp? {
            val safeArgs = param.args?.filterNotNull() ?: return null
            safeArgs.firstOrNull { FMessageWpp.TYPE.isInstance(it) }?.let { return FMessageWpp(it) }
            val arg0 = param.args?.getOrNull(0) ?: return null
            val statusItem = StatusItemWpp.from(arg0) ?: return null
            return statusItem.fMessage
        }


        private fun persistRevokedMessage(fMessage: FMessageWpp, messageID: String) {
            val stripJID = fMessage.key.remoteJid.phoneNumber ?: return
            negativeCache.remove(cacheKey(stripJID, messageID))
            DelMessageStore.getInstance(Utils.application).insertMessage(
                stripJID,
                messageID,
                System.currentTimeMillis()
            )
        }

        /**
         * Hentikan eksekusi method revoke dengan cara membatalkan pemanggilan.
         *
         * `param.result` harus bertipe sama dengan `method.returnType`; LSPosed
         * melempar ClassCastException kalau tidak cocok. Karena itu:
         * - return boolean -> `true` (penanda "sudah ditangani")
         * - return primitif lain -> nilai default primitifnya
         * - return void -> `null`
         * - return objek -> instance baru dari returnType (konstruktor default)
         *   supaya pemanggil menerima objek bertipe benar, bukan null.
         *
         * @return true kalau result berhasil dipasang.
         */
        private fun blockRevokeCall(param: XC_MethodHook.MethodHookParam): Boolean {
            val method = param.method as? Method ?: return false
            val returnType = method.returnType
            return try {
                param.result = when {
                    returnType == Void.TYPE -> null
                    returnType == Boolean::class.javaPrimitiveType ||
                        returnType == java.lang.Boolean::class.java -> true
                    returnType.isPrimitive -> ReflectionUtils.getDefaultValue(returnType)
                    else -> {
                        val constructor = returnType.declaredConstructors
                            .filter { !it.isSynthetic }
                            .minByOrNull { it.parameterCount }
                        if (constructor == null) {
                            return false
                        }
                        constructor.isAccessible = true
                        constructor.newInstance(
                            *ReflectionUtils.initArray(constructor.parameterTypes)
                        )
                    }
                }
                true
            } catch (e: Throwable) {
                XposedBridge.log("WAE_ANTIREVOKE: gagal blokir revoke: ${e.message}")
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun doHook() {
        val antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader)
        val unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
        val statusPlaybackClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader)
        val antiRevokeFStatusMethod = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader)

        if (prefs.getBoolean("trash_recovery", false)) {
            hookNewMessageForMedia()
        }

        XposedBridge.hookMethod(antiRevokeFStatusMethod, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val fStatusKey = FStatusWpp.FStatusKey(param.args[1])
                val fstatus = fStatusKey.fStatus ?: return
                val fMessage = fstatus.fMessage ?: return
                if (!fStatusKey.isFromMe && handleRevocationAttempt(fMessage, fStatusKey.messageID) != 0) {
                    blockRevokeCall(param)
                }
            }

        })

        XposedBridge.hookMethod(antiRevokeMessageMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val args = param.args ?: return
                val fMessageObj = ReflectionUtils.getArg(args, FMessageWpp.TYPE, 0)
                if (fMessageObj == null) {
                    logDebug("FMessageObj is null in revoke!")
                    return
                }
                val fMessage = FMessageWpp(fMessageObj)
                val messageKey = fMessage.key
                val messageId = XposedHelpers.getObjectField(fMessage.getObject(), "A01") as? String
                    ?: return
                val deviceJid = fMessage.deviceJid

                val shouldIntercept = if (messageKey.remoteJid.isGroup) {
                    deviceJid != null && handleRevocationAttempt(fMessage, messageId) != 0
                } else {
                    !messageKey.isFromMe && handleRevocationAttempt(fMessage, messageId) != 0
                }
                if (shouldIntercept) {
                    blockRevokeCall(param)
                }
            }
        })

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                val dateTextView = view.findViewById<TextView>(Utils.getID("date", "id"))
                bindRevokedMessageUI(fMessage, dateTextView, "antirevoke", view)
            }
        })

        XposedBridge.hookMethod(unknownStatusPlaybackMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = ReflectionUtils.getArg(param.args, param.method.declaringClass, 0)
                val fMessage = findObjectFMessage(param)
                val field =
                    ReflectionUtils.getFieldByType(param.method.declaringClass, statusPlaybackClass)

                if (obj == null || field == null || fMessage == null) {
                    logDebug("Invalid parameters")
                    return
                }

                val objView = field.get(obj) ?: return
                val textViews =
                    ReflectionUtils.getFieldsByType(statusPlaybackClass, TextView::class.java)

                if (textViews.isEmpty()) {
                    logDebug("No text views found")
                    return
                }

                val dateId = Utils.getID("date", "id")
                for (textViewField in textViews) {
                    val textView = textViewField.get(objView) as? TextView
                    if (textView != null && textView.id == dateId) {
                        bindRevokedMessageUI(fMessage, textView, "antirevokestatus")
                        break
                    }
                }
            }
        })
    }

    private fun bindRevokedMessageUI(
        fMessage: FMessageWpp,
        dateTextView: TextView?,
        antirevokeType: String,
        boundView: View? = null
    ) {
        if (dateTextView == null) return
        val antirevokeValue = prefs.getString(antirevokeType, "0")?.toIntOrNull() ?: 0
        if (antirevokeValue == 0) return

        val key = fMessage.key
        val boundMessageId = key.messageID
        val jid = key.remoteJid.phoneNumber ?: return
        val rowId = fMessage.rowId
        val originalMessage =
            XposedHelpers.getAdditionalInstanceField(dateTextView, "originalMessage") as? String

        // Reset synchronously so a recycled view never shows a stale revoke mark,
        // then resolve the real state off the main thread.
        dateTextView.paint.isUnderlineText = false
        dateTextView.setOnClickListener(null)
        dateTextView.setCompoundDrawables(null, null, null, null)
        if (originalMessage != null) dateTextView.text = originalMessage

        val cacheId = cacheKey(jid, boundMessageId)
        timestampCache[cacheId]?.let { timestamp ->
            applyRevokedUI(dateTextView, antirevokeValue, boundMessageId, boundView, timestamp)
            return
        }
        if (negativeCache.containsKey(cacheId)) return

        Utils.databaseExecutor.execute {
            val timestamp = resolveRevokedTimestamp(jid, boundMessageId, rowId)
            if (timestamp <= 0) return@execute
            mainHandler.post {
                if (!dateTextView.isAttachedToWindow) return@post
                if (boundView != null && !ConversationItemListener.isViewBoundToMessage(boundView, boundMessageId)) return@post
                applyRevokedUI(dateTextView, antirevokeValue, boundMessageId, boundView, timestamp)
            }
        }
    }

    /**
     * Mengembalikan timestamp revoke (> 0) atau 0. Bila tidak ada, jid+msgid
     * dimasukkan ke negativeCache supaya render berikutnya tidak query DB lagi.
     */
    private fun resolveRevokedTimestamp(jid: String, messageID: String, rowId: Long): Long {
        val ownId = cacheKey(jid, messageID)
        timestampCache[ownId]?.let { return it }
        if (negativeCache.containsKey(ownId)) return 0

        val store = DelMessageStore.getInstance(Utils.application)
        store.getTimestampByJidAndMsgId(jid, messageID)
            .takeIf { it > 0 }
            ?.let {
                timestampCache[ownId] = it
                return it
            }

        val originalKey = MessageStore.getInstance().getOriginalMessageKey(rowId)
        if (originalKey.isNotEmpty()) {
            val origCacheId = cacheKey(jid, originalKey)
            store.getTimestampByJidAndMsgId(jid, originalKey)
                .takeIf { it > 0 }
                ?.let {
                    timestampCache[origCacheId] = it
                    timestampCache[ownId] = it
                    return it
                }
        }

        negativeCache[ownId] = true
        return 0
    }

    private fun applyRevokedUI(
        dateTextView: TextView,
        antirevokeValue: Int,
        boundMessageId: String,
        boundView: View?,
        timestamp: Long
    ) {
        val date = dateFormatThreadLocal.get()?.format(Date(timestamp))
        dateTextView.paint.isUnderlineText = true
        dateTextView.setOnClickListener {
            if (boundView != null && !ConversationItemListener.isViewBoundToMessage(boundView, boundMessageId)) return@setOnClickListener
            val toastMessage =
                Utils.application.getString(R.string.message_removed_on).format(date)
            Utils.showToast(toastMessage, Toast.LENGTH_LONG)
        }

        when (antirevokeValue) {
            1 -> {
                val messageText =
                    XposedHelpers.getAdditionalInstanceField(dateTextView, "originalMessage") as? String
                        ?: dateTextView.text
                val newTextData = "${
                    UnobfuscatorCache.getInstance().getString("messagedeleted")
                } | $messageText"
                dateTextView.text = newTextData
                XposedHelpers.setAdditionalInstanceField(
                    dateTextView,
                    "originalMessage",
                    messageText.toString()
                )
            }

            2 -> {
                val drawable = Utils.application.getDrawable(R.drawable.deleted)
                dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
                dateTextView.compoundDrawablePadding = 5
            }
        }
    }

    private fun handleRevocationAttempt(fMessage: FMessageWpp, messageId: String): Int {
        try {
            handleRevocationAlert(fMessage)
        } catch (e: Exception) {
            log(e)
        }

        val revokeBoolean = prefs.getString(
            if (fMessage.key.remoteJid.isStatus) "antirevokestatus" else "antirevoke",
            "0"
        )?.toIntOrNull() ?: 0

        val trashEnabled = prefs.getBoolean("trash_recovery", false)
        logDebug("[TrashRecovery] revoke event: msgId=$messageId trashEnabled=$trashEnabled")
        if (trashEnabled) {
            CompletableFuture.runAsync {
                try {
                    val waPackage = Utils.application.packageName
                    val contact = fMessage.key.remoteJid.phoneNumber
                    val intime = fMessage.timeStamp.takeIf { it > 0 }
                    val deltime = System.currentTimeMillis()
                    val mediaType = fMessage.mediaType ?: -1
                    val isVoice = mediaType == 2 || mediaType == 82
                    val voiceFileName = if (isVoice) fMessage.mediaFile?.name else null
                    val fileId = if (!isVoice && mediaType > 0) fMessage.mediaFile?.name else null
                    val text = fMessage.messageStr
                    val savedPath = savedMediaPaths.remove(messageId)
                    val mediaPath = savedPath ?: fMessage.mediaFile?.absolutePath
                    val jidAuthor = fMessage.key.remoteJid
                    val actualAuthor = if (jidAuthor.isStatus) fMessage.userJid else jidAuthor
                    val senderName = WaContactWpp.getWaContactFromJid(actualAuthor)?.displayName
                    val jid = fMessage.key.remoteJid.phoneNumber ?: return@runAsync
                    DelMessageStore.getInstance(Utils.application).insertFullMessage(
                        jid = jid,
                        msgid = messageId,
                        timestamp = System.currentTimeMillis(),
                        text = text,
                        mediaPath = mediaPath,
                        mediaType = mediaType,
                        senderName = senderName,
                        wa = waPackage,
                        contact = contact,
                        intime = intime,
                        deltime = deltime,
                        voiceFileName = voiceFileName,
                        fileId = fileId
                    )
                    writeTrashCache()
                } catch (e: Exception) {
                    logDebug(e.message)
                }
            }
        }

        if (revokeBoolean == 0) return 0

        CompletableFuture.runAsync {
            try {
                persistRevokedMessage(fMessage, messageId)
                    val waPackage = Utils.application.packageName
                    val contact = fMessage.key.remoteJid.phoneNumber
                    val intime = fMessage.timeStamp.takeIf { it > 0 }
                    val deltime = System.currentTimeMillis()
                    val mediaType = fMessage.mediaType ?: -1
                    val isVoice = mediaType == 2 || mediaType == 82
                    val voiceFileName = if (isVoice) fMessage.mediaFile?.name else null
                    val fileId = if (!isVoice && mediaType > 0) fMessage.mediaFile?.name else null
                    val jid = fMessage.key.remoteJid.phoneNumber ?: ""
                    val text = fMessage.messageStr
                    val savedPath = savedMediaPaths.remove(messageId)
                    val mediaPath = savedPath ?: fMessage.mediaFile?.absolutePath
                    val senderName = run {
                        val jidAuthor = fMessage.key.remoteJid
                        val actualAuthor = if (jidAuthor.isStatus) fMessage.userJid else jidAuthor
                        WaContactWpp.getWaContactFromJid(actualAuthor)?.displayName ?: actualAuthor.phoneNumber
                    }
                    DelMessageStore.getInstance(Utils.application).insertFullMessage(
                        jid = jid,
                        msgid = messageId,
                        timestamp = System.currentTimeMillis(),
                        text = text,
                        mediaPath = mediaPath,
                        mediaType = mediaType,
                        senderName = senderName,
                        wa = waPackage,
                        contact = contact,
                        intime = intime,
                        deltime = deltime,
                        voiceFileName = voiceFileName,
                        fileId = fileId
                    )
                    writeTrashCache()
                    val mConversation = WppCore.getCurrentConversation()
                    if (mConversation != null && fMessage.key.remoteJid.phoneNumber == WppCore.getCurrentUserJid()?.phoneNumber) {
                        mConversation.runOnUiThread {
                            ConversationItemListener.notifyDataSetChanged()
                        }
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
        }
        return revokeBoolean
    }

    private fun formatRevocationMessage(fMessage: FMessageWpp): String? {
        var jidAuthor = fMessage.key.remoteJid
        var messageSuffix = Utils.application.getString(R.string.deleted_message)

        if (jidAuthor.isStatus) {
            messageSuffix = Utils.application.getString(R.string.deleted_status)
            jidAuthor = fMessage.userJid
        }

        val name = resolveAuthorName(jidAuthor)

        return if (jidAuthor.isGroup) {
            var participantJid = fMessage.userJid
            if (participantJid.isNull) {
                val deletedAdminUser = XposedHelpers.getObjectField(fMessage.getObject(), "A00")
                if (deletedAdminUser != null) {
                    participantJid = FMessageWpp.UserJid(deletedAdminUser)
                }
            }
            val participantName = resolveAuthorName(participantJid)

            Utils.application
                .getString(R.string.deleted_a_message_in_group, participantName, name)
        } else {
            "$name $messageSuffix"
        }
    }

    /**
     * Nama penulis revoke, berlapis supaya tidak pernah jatuh ke JID mentah:
     * kontak WhatsApp -> tabel `wa_contacts` -> nomor asli. Untuk JID tanpa
     * nomor (`@lid`, `@newsletter`) nomor tidak ada, jadi dipakai JID user agar
     * teks seperti "kode@newsletter" tidak bocor ke toast.
     */
    private fun resolveAuthorName(jid: FMessageWpp.UserJid): String {
        WaContactWpp.getWaContactFromJid(jid)?.displayName
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        WppCore.getContactName(jid)
            .takeIf { it.isNotBlank() && it != "Whatsapp Contact" }
            ?.let { return it }

        return jid.phoneNumber
            ?: jid.userRawString
            ?: jid.phoneRawString
            ?: "Unknown"
    }

    private fun handleRevocationAlert(fMessage: FMessageWpp) {
        val message = formatRevocationMessage(fMessage) ?: return

        val jidAuthor = fMessage.key.remoteJid
        val actualAuthor = if (jidAuthor.isStatus) fMessage.userJid else jidAuthor

        val name = resolveAuthorName(actualAuthor)

        val taskerAction = if (jidAuthor.isStatus) "deleted_status" else "deleted_message"

        if (prefs.getBoolean("toastdeleted", false)) {
            Utils.showToast(message, Toast.LENGTH_LONG)
        }

        Tasker.sendTaskerEvent(name, actualAuthor.phoneNumber, taskerAction)
    }

    private fun writeTrashCache() {
        scheduleTrashCacheWrite()
    }

    private fun scheduleTrashCacheWrite() {
        // Debounce: coalesce bursts of revoke events into one full-table dump
        val gen = trashWriteGeneration.incrementAndGet()
        trashWriteScheduler.schedule({
            if (trashWriteGeneration.get() != gen) return@schedule
            try {
                doWriteTrashCache()
            } catch (e: Throwable) {
                logDebug("writeTrashCache failed: ${e.message}")
            }
        }, TRASH_WRITE_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun doWriteTrashCache() {
        val path = "/data/data/${BuildConfig.APPLICATION_ID}/files/trash_cache.json"
        val pfd = WppCore.getClientBridge()?.openFile(path, true) ?: return
        pfd.use {
            try {
                java.io.FileOutputStream(it.fileDescriptor).use { rawOut ->
                    // NDJSON, written incrementally per page so RAM stays bounded.
                    val out = rawOut.bufferedWriter(Charsets.UTF_8)
                    var lastId = 0L
                    while (true) {
                        val page = DelMessageStore.getInstance(Utils.application)
                            .getMessagesAfter(lastId, TRASH_DUMP_PAGE_SIZE)
                        if (page.isEmpty()) break
                        for (msg in page) {
                            out.write(toJsonLine(msg))
                            out.write('\n'.code)
                        }
                        lastId = page.last().id
                    }
                    out.flush()
                }
            } catch (e: Throwable) {
                logDebug("writeTrashCache failed: ${e.message}")
            }
        }
    }

    private fun toJsonLine(msg: DelMessage): String {
        val obj = org.json.JSONObject()
        obj.put("id", msg.id)
        obj.put("jid", msg.jid ?: "")
        obj.put("msgid", msg.msgid ?: "")
        obj.put("timestamp", msg.timestamp ?: 0L)
        obj.put("text", msg.text ?: org.json.JSONObject.NULL)
        obj.put("mediaPath", msg.mediaPath ?: org.json.JSONObject.NULL)
        obj.put("mediaType", msg.mediaType ?: -1)
        obj.put("senderName", msg.senderName ?: org.json.JSONObject.NULL)
        obj.put("wa", msg.wa ?: org.json.JSONObject.NULL)
        obj.put("contact", msg.contact ?: org.json.JSONObject.NULL)
        obj.put("intime", msg.intime ?: 0L)
        obj.put("deltime", msg.deltime ?: 0L)
        obj.put("voiceFileName", msg.voiceFileName ?: org.json.JSONObject.NULL)
        obj.put("fileId", msg.fileId ?: org.json.JSONObject.NULL)
        return obj.toString()
    }

    private fun hookNewMessageForMedia() {
        try {
            val newMsgMethod = Unobfuscator.loadNewMessageWithMediaMethod(classLoader)
            log("[TrashRecovery] hookNewMessageForMedia: hooked ${newMsgMethod?.name}")
            XposedBridge.hookMethod(newMsgMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val fMessageObj = if (FMessageWpp.TYPE.isInstance(param.thisObject)) {
                            param.thisObject
                        } else {
                            ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0)
                        } ?: return
                        val fMessage = FMessageWpp(fMessageObj)
                        if (!fMessage.isMediaFile) return
                        val file = fMessage.mediaFile ?: return
                        val msgId = fMessage.key.messageID
                        val ext = file.absolutePath.substringAfterLast('.', "")
                        val dest = Utils.getDestination("Trash Recovery")
                        val name = Utils.generateName(fMessage.userJid, ext)
                        val error = Utils.copyFile(file, dest, name)
                        if (error.isNullOrEmpty()) {
                            savedMediaPaths[msgId] = dest + name
                            log("[TrashRecovery] media saved: $name for msgId=$msgId")
                        } else {
                            log("[TrashRecovery] media copy error: $error")
                        }
                    } catch (e: Exception) {
                        log("[TrashRecovery] hookNewMessageForMedia error: ${e.message}")
                    }
                }
            })
        } catch (e: Exception) {
            log("[TrashRecovery] hookNewMessageForMedia setup error: ${e.message}")
        }
    }

    override fun getPluginName(): String = "Anti Revoke"
}
