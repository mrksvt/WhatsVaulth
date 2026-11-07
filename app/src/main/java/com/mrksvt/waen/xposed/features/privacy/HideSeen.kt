package com.mrksvt.waen.xposed.features.privacy

import android.os.Message
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.components.ProtocolTreeNodeWpp
import com.mrksvt.waen.xposed.core.components.WaContactWpp
import com.mrksvt.waen.xposed.core.db.MessageHistoryStore
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.features.general.Others
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType

class HideSeen(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val MEDIA_TYPE_VOICE_NOTE = 2

        // Children that mark a receipt as a call signalling stanza instead of a delivery
        // receipt. Dropping those would break call setup.
        private val CALL_RECEIPT_TAGS = setOf("offer", "accept", "reject", "enc_rekey")

        @JvmStatic
        fun generateFMessageKey(protocolTreeNodeWpp: ProtocolTreeNodeWpp): FMessageWpp.Key? {
            val fromKV = protocolTreeNodeWpp.attributes.first { it.key == "to" }
            val userJid = fromKV.userJid ?: return null
            val idKV = protocolTreeNodeWpp.attributes.first { it.key == "id" }
            return FMessageWpp.Key(idKV.value!!, userJid, false)
        }
    }

    private var hideReceipt = false
    private var hideReceiptUnknown = false
    private var ghostMode = false
    private var hideRead = false
    private var hideAudioSeen = false
    private var hideOnceSeen = false
    private var hideReadGroup = false
    private var hideStatusView = false

    override fun doHook() {
        loadPreferences()
        hookSendReadReceiptJob()
        hookReceiptMethod()
        hookSenderPlayed()
        hookSenderPlayedBusiness()
        hookStanzaWriter()
        hookEnforceHiding()
    }

    private fun loadPreferences() {
        ghostMode = WppCore.getPrivBoolean("ghostmode", false)
        hideRead = prefs.getBoolean("hideread", false)
        hideAudioSeen = prefs.getBoolean("hideaudioseen", false)
        hideOnceSeen = prefs.getBoolean("hideonceseen", false)
        hideReadGroup = prefs.getBoolean("hideread_group", false)
        hideStatusView = prefs.getBoolean("hidestatusview", false)
        hideReceipt = prefs.getBoolean("hidereceipt", false)
        hideReceiptUnknown = prefs.getBoolean("hidereceipt_unknown", false)

    }

    /**
     * Drops delivery receipts that WhatsApp writes to the socket directly, bypassing the
     * receipt builder. Without this the other side can still see "delivered" while
     * "read" is hidden.
     */
    private fun hookStanzaWriter() {
        val stanzaWriterMethod = try {
            Unobfuscator.loadStanzaWriterMethod(classLoader)
        } catch (t: Throwable) {
            logDebug("HideSeen: stanza writer not found, delivery receipts not filtered: ${t.message}")
            return
        }

        XposedBridge.hookMethod(stanzaWriterMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val node = param.args.getOrNull(0) ?: return
                    if (!ProtocolTreeNodeWpp.TYPE.isInstance(node)) return
                    val receipt = ProtocolTreeNodeWpp(node)
                    if (receipt.tag != "receipt" || !revealsDelivery(receipt)) return

                    val userJid =
                        receipt.attributes.firstOrNull { it.key == "to" }?.userJid ?: return
                    val messageId = receipt.getFirstKeyValue("id") ?: return
                    if (userJid.isNull || userJid.isStatus || userJid.isNewsletter) return

                    val fmessageKey = FMessageWpp.Key(messageId, userJid, false)
                    if (!shouldBlockReceipt(fmessageKey)) return

                    param.result = null
                    insertHiddenIfAbsent(fmessageKey)
                } catch (t: Throwable) {
                    logDebug("HideSeen: stanza writer hook error: ${t.message}")
                }
            }
        })
    }

    private fun revealsDelivery(receipt: ProtocolTreeNodeWpp): Boolean {
        if (receipt.children.any { it.tag in CALL_RECEIPT_TAGS }) return false
        return when (receipt.getFirstKeyValue("type")) {
            null, "inactive" -> true
            else -> false
        }
    }

    private fun hookSendReadReceiptJob() {
        val sendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader)
        val sendJobClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "SendReadReceiptJob"
        )

        XposedBridge.hookMethod(sendReadReceiptJobMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val job = param.thisObject
                val hasBlueOnReply =
                    XposedHelpers.getAdditionalInstanceField(job, "blue_on_reply") as? Boolean
                        ?: false

                if (!sendJobClass.isInstance(job) || hasBlueOnReply) return

                val lid = XposedHelpers.getObjectField(job, "jid") as? String
                val isInvalidJid =
                    lid.isNullOrEmpty() || lid.contains("lid_me") || lid.contains("status_me")

                if (isInvalidJid) return

                val userJid = FMessageWpp.UserJid(lid)
                if (userJid.isNull) return

                val privacy = CustomPrivacy.getJSON(userJid)
                val isHide = processReadReceiptByType(param, job, userJid, privacy)

                if (isHide) {
                    recordHiddenMessages(job, userJid)
                }
            }
        })
    }

    private fun processReadReceiptByType(
        param: XC_MethodHook.MethodHookParam,
        job: Any,
        userJid: FMessageWpp.UserJid,
        privacy: JSONObject
    ): Boolean {
        val hideReceiptRead = checkPrivacyAndHideReceipt(userJid)
        return when {
            userJid.isGroup -> {
                if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode || hideReceiptRead) {
                    param.result = null
                    true
                } else false
            }

            userJid.isStatus -> {
                val participant = XposedHelpers.getObjectField(job, "participant") as? String
                val statusJid = FMessageWpp.UserJid(participant)
                val customHideStatusView = CustomPrivacy.getJSON(statusJid)
                    .optBoolean("HideViewStatus", hideStatusView)

                if (customHideStatusView || ghostMode) {
                    param.result = null
                }
                false
            }

            else -> {
                if (privacy.optBoolean("HideSeen", hideRead) || ghostMode || hideReceiptRead) {
                    param.result = null
                    true
                } else false
            }
        }
    }

    private fun recordHiddenMessages(sendReadReceiptJob: Any, userJid: FMessageWpp.UserJid) {
        val messageIds =
            XposedHelpers.getObjectField(sendReadReceiptJob, "messageIds") as? Array<*> ?: return
        val ids = messageIds.filterIsInstance<String>()
        if (ids.isEmpty()) return
        for (messageId in ids) {
            MessageHistoryStore.getInstance().insertHideSeenMessage(
                userJid.phoneRawString,
                userJid.userRawString,
                messageId,
                MessageHistoryStore.ReceiptType.READ,
                false
            )
        }
    }

    private fun hookReceiptMethod() {

        val receiptMethod = Unobfuscator.loadReceiptMethod(classLoader)
        val receiptMessageInfoClass = Unobfuscator.loadReceiptMessageInfoClass(classLoader)
        val onDispatchMessage = Unobfuscator.loadOndispatchMessage(classLoader)

        onDispatchMessage.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val message = param.args[0] as Message
                        val type = message.arg1
                        val obj = message.obj
                        if (type != 419 && type != 89) return
                        if (!receiptMessageInfoClass.isInstance(obj)) return
                        // We check if the message is duplicated to avoid sending a tick twice causing congestion in the IQ queue
                        val fmessageKeyField = ReflectionUtils.findFieldUsingFilter(obj.javaClass){
                            FMessageWpp.Key.TYPE.isAssignableFrom(it.type)
                        }
                        val fmessageKey = FMessageWpp.Key(fmessageKeyField.get(obj))

                        // Delivery receipts must not leak when receipts are hidden for this
                        // contact, otherwise the sender still sees "delivered".
                        if (shouldBlockReceipt(fmessageKey)) {
                            message.arg1 = -1
                            insertHiddenIfAbsent(fmessageKey)
                            return
                        }

                        val hideSeenItem = MessageHistoryStore.getInstance().getHideSeenMessage(
                            fmessageKey.remoteJid.phoneRawString,
                            fmessageKey.remoteJid.userRawString,
                            fmessageKey.messageID,
                            MessageHistoryStore.ReceiptType.READ
                        )

                        if (hideSeenItem?.viewed ?: false) return

                        hideSeenItem?.let {
                            message.arg1 = -1 // We change the type [IMPORTANT]IA Agent use 9 for best work[/IMPORTANT]
                            return
                        }
                    }
                })
        }


        Others.propsBoolean[19148] = false // Change route IQ

        XposedBridge.hookMethod(receiptMethod, object : XC_MethodHook() {

            override fun afterHookedMethod(param: MethodHookParam) {

                val protocolTreeNodeWpp = ProtocolTreeNodeWpp(param.result)

                val typeKV = protocolTreeNodeWpp.attributes.firstOrNull {
                    it.key == "type"
                }

                val fmessageKey = generateFMessageKey(protocolTreeNodeWpp) ?: return

                if (fmessageKey.remoteJid.isStatus)return

                val isReadType = typeKV?.value == "read"
                val blockSeen = shouldBlockSeen(fmessageKey, isReadType)
                val blockReceipt = shouldBlockReceipt(fmessageKey)

                if (!blockSeen && !blockReceipt) return

                if (blockReceipt) {
                    if (typeKV == null) {
                        protocolTreeNodeWpp.addKeyValue("type", "inactive")
                    } else {
                        typeKV.value = "inactive"
                    }
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                } else {
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("type")
                }

                insertHidden(fmessageKey)
            }
        })
    }

    private fun insertHiddenIfAbsent(fmessageKey: FMessageWpp.Key) {
        if (getHiddenItem(fmessageKey) == null) insertHidden(fmessageKey)
    }

    private fun insertHidden(fmessageKey: FMessageWpp.Key) {
        MessageHistoryStore.getInstance().insertHideSeenMessage(
            fmessageKey.remoteJid.phoneRawString,
            fmessageKey.remoteJid.userRawString,
            fmessageKey.messageID,
            MessageHistoryStore.ReceiptType.READ,
            false
        )
    }

    private fun getHiddenItem(fmessageKey: FMessageWpp.Key): MessageHistoryStore.MessageSeenItem? {
        return MessageHistoryStore.getInstance().getHideSeenMessage(
            fmessageKey.remoteJid.phoneRawString,
            fmessageKey.remoteJid.userRawString,
            fmessageKey.messageID,
            MessageHistoryStore.ReceiptType.READ
        )
    }

    private fun isAlreadyViewed(fmessageKey: FMessageWpp.Key): Boolean {
        return getHiddenItem(fmessageKey)?.viewed == true
    }

    private fun shouldBlockSeen(
        fmessageKey: FMessageWpp.Key,
        isReadType: Boolean = true
    ): Boolean {
        if (!isReadType || !checkPrivacyAndHideSeen(fmessageKey)) return false
        return !isAlreadyViewed(fmessageKey)
    }

    private fun shouldBlockReceipt(fmessageKey: FMessageWpp.Key): Boolean {
        if (!checkPrivacyAndHideReceipt(fmessageKey)) return false
        return !isAlreadyViewed(fmessageKey)
    }

    private fun isUnknownNumber(userJid: FMessageWpp.UserJid): Boolean {
        if (!hideReceiptUnknown || userJid.isGroup || userJid.isStatus || userJid.isNewsletter) return false
        return WaContactWpp.getWaContactFromJid(userJid)?.isSavedContact() == false
    }

    private fun checkPrivacyAndHideReceipt(userJid: FMessageWpp.UserJid): Boolean {
        val privacy = CustomPrivacy.getJSON(userJid)
        return privacy.optBoolean("HideReceipt", hideReceipt) || ghostMode || isUnknownNumber(userJid)
    }

    private fun checkPrivacyAndHideReceipt(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid)
        val customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt)
        return customHideReceipt || ghostMode || isUnknownNumber(fmessageKey.remoteJid)
    }

    private fun checkPrivacyAndHideSeen(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid)
        val hideKey = if (fmessageKey.remoteJid.isGroup) hideReadGroup else hideRead
        val shouldHide = privacy.optBoolean("HideSeen", hideKey) || ghostMode
        return shouldHide
    }

    private fun hookSenderPlayed() {
        val loadSenderPlayed = Unobfuscator.loadSenderPlayedMethod(classLoader)

        XposedBridge.hookMethod(loadSenderPlayed, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fMessage = FMessageWpp(param.args[0])
                processSenderPlayed(param, fMessage)
            }
        })
    }

    private fun hookSenderPlayedBusiness() {
        val loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader)

        XposedBridge.hookMethod(loadSenderPlayedBusiness, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val set = param.args[0] as? Set<*>
                if (set.isNullOrEmpty()) return

                val fMessage = FMessageWpp(set.first())
                processSenderPlayed(param, fMessage)
            }
        })
    }

    private fun processSenderPlayed(param: XC_MethodHook.MethodHookParam, fMessage: FMessageWpp) {
        val isHideViewOnce = (hideOnceSeen || ghostMode) && fMessage.isViewOnce
        val isHideVoiceNote =
            (hideAudioSeen || ghostMode) && fMessage.mediaType == MEDIA_TYPE_VOICE_NOTE
        val key = fMessage.key

        if (isHideViewOnce || isHideVoiceNote) {
            param.result = null
            MessageHistoryStore.getInstance().insertHideSeenMessage(
                key.remoteJid.phoneRawString,
                key.remoteJid.userRawString,
                key.messageID,
                MessageHistoryStore.ReceiptType.PLAYED,
                false
            )
        }

        if (fMessage.isViewOnce && !hideOnceSeen && !ghostMode) {
            val phoneRaw = key.remoteJid.phoneRawString
            val userRaw = key.remoteJid.userRawString
            val messageId = key.messageID
            MessageHistoryStore.getInstance().apply {
                updateViewedMessage(
                    phoneRaw,
                    userRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.PLAYED,
                    true
                )
                updateViewedMessage(
                    phoneRaw,
                    userRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.READ,
                    true
                )
            }
        }
    }

    private fun hookEnforceHiding() {
        try {
            val messageClass = classLoader.loadClass("com.whatsapp.protocol.Message")
            val methods = messageClass.declaredMethods
            for (method in methods) {
                if (method.returnType == Boolean::class.java &&
                    method.name.contains("isRead", ignoreCase = true)) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (hideRead || ghostMode) {
                                param.result = null
                                val message = param.thisObject
                                val msgId = XposedHelpers.getObjectField(message, "id")?.toString()
                                logDebug("Enforce hiding read receipt for message: $msgId")
                            }
                        }
                    })
                    logDebug("HideSeen enforceHiding installed on: ${method.name}")
                    break
                }
            }
        } catch (e: Exception) {
            logDebug("HideSeen enforceHiding failed: ${e.message}")
        }
    }

    override fun getPluginName(): String {
        return "Hide Seen"
    }
}
