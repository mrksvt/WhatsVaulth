package com.mrksvt.waen.xposed.features.customization

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.DesignUtils
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class ContactVerify(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val TAG_CONTACT_CHECKER = "contact_checker"
        private const val STATUS_NOT_ADDED = 401

        /** Helper protokol bisa telat dibuat WhatsApp; 4 percobaan ~7 detik. */
        private const val PROTOCOL_RETRY_MAX = 4
        private const val PROTOCOL_RETRY_DELAY_MS = 700L
        private val RETRY_HANDLER = Handler(Looper.getMainLooper())
    }

    private val state = ContactCheckState()

    @SuppressLint("ResourceType")
    private fun createTextMessageView(activity: Activity): TextView {
        return TextView(activity).apply {
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isSingleLine = true
            marqueeRepeatLimit = -1
            textSize = 10f
            isFocusable = true
            isFocusableInTouchMode = true
            isSelected = true
            setTextColor(DesignUtils.getPrimaryTextColor())
            setText(R.string.checking_if_contact_added)
        }
    }

    @SuppressLint("ResourceType")
    override fun doHook() {
        if (!prefs.getBoolean("verify_blocked_contact", false)) return

        val sendGetProfilePhoto = Unobfuscator.loadGetProfilePhoto(classLoader)
        val constructor = sendGetProfilePhoto.constructors.firstOrNull { it.parameterCount > 3 }
            ?: throw IllegalStateException("GetProfilePhoto constructor with required parameters not found")
        state.sendGetProfilePhotoConstructor = constructor
        state.protocolParamIndex = resolveProtocolParamIndex(constructor)
        if (state.protocolParamIndex < 0) {
            throw IllegalStateException(
                "ProfilePhotoProtocol helper parameter not found in ${sendGetProfilePhoto.name}"
            )
        }
        val profilePhotoProtocolType = constructor.parameterTypes[state.protocolParamIndex]
        state.profilePhotoProtocolType = profilePhotoProtocolType
        logDebug(
            "ContactVerify protocol helper type: ${profilePhotoProtocolType.name} " +
                "(ctor params=${constructor.parameterTypes.joinToString { it.name }})"
        )
        initProfilePhotoProtocolHooks(profilePhotoProtocolType)
        val dialerProfilePictureLoader = Unobfuscator.loadDialerProfilePictureLoader(classLoader)
        initProfilePhotoCallbacks(dialerProfilePictureLoader)
        registerActivityListener()
    }

    private fun registerActivityListener() {
        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName == "Conversation" && state == WppCore.ActivityChangeState.ChangeType.STARTED) {
                CompletableFuture.runAsync { onConversationStarted(activity) }
            }
        }
    }

    private fun onConversationStarted(activity: Activity) {
        try {
            val userJid = WppCore.getCurrentUserJid() ?: return
            if (!userJid.isContact) return
            val view =
                activity.findViewById<ViewGroup>(Utils.getID("conversation_contact", "id")) ?: return
            val textView = resolveContactChecker(activity, view)
            showChecking(textView)
            checkContactPhotoProfile(userJid)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    private fun initProfilePhotoProtocolHooks(profilePhotoProtocolType: Class<*>) {
        XposedBridge.hookAllConstructors(profilePhotoProtocolType, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (profilePhotoProtocolType.isInstance(param.thisObject)) {
                    state.profilePhotoProtocol = param.thisObject
                }
            }
        })
    }

    /**
     * Helper protokol adalah parameter konstruktor SendGetProfilePhoto yang bertipe
     * interface callback (punya method menerima int status, mis. 401 = not added).
     * Sebelumnya di-hardcode ke indeks 2, padahal indeks 2 adalah JID sehingga hook
     * tidak pernah menangkap helper dan verifikasi selalu "not available".
     * Indeks dicari dari bentuk tipe, bukan dari nama obfuscated.
     */
    private fun resolveProtocolParamIndex(constructor: Constructor<*>): Int {
        val types = constructor.parameterTypes
        val byShape = types.indexOfFirst { type ->
            type.isInterface && type.declaredMethods.any { method ->
                method.parameterCount == 1 && method.parameterTypes[0] == Int::class.javaPrimitiveType
            }
        }
        if (byShape >= 0) return byShape

        val byUnobfuscator = runCatching {
            val helper = Unobfuscator.loadProfilePhotoProtocolHelperClass(classLoader)
            types.indexOfFirst { it == helper }
        }.getOrDefault(-1)
        if (byUnobfuscator >= 0) return byUnobfuscator

        logDebug("ContactVerify protocol helper not found, ctor params=${types.joinToString { it.name }}")
        return -1
    }

    private fun initProfilePhotoCallbacks(dialerProfilePictureLoader: Class<*>) {
        val methods = ReflectionUtils.findAllMethodsUsingFilter(dialerProfilePictureLoader) { method ->
            method.name.length == 3 && method.parameterCount > 1
        }
        val onSuccess = methods.first { method ->
            String::class.java !in method.parameterTypes
        }
        val onError = methods.first { method ->
            String::class.java in method.parameterTypes
        }
        XposedBridge.hookMethod(onSuccess, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fieldUserJid = ReflectionUtils.getFieldByExtendType(
                    param.args[0].javaClass,
                    FMessageWpp.UserJid.TYPE_JID
                )
                val userJid = FMessageWpp.UserJid(fieldUserJid!!.get(param.args[0]))
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val tv = state.checkerView.get() ?: return
                showContactAdded(tv)
            }
        })
        XposedBridge.hookMethod(onError, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val userJid = FMessageWpp.UserJid(param.args[0])
                if (state.pendingUserJid.get() != userJid.userRawString) return
                state.pendingUserJid.set(null)
                val tv = state.checkerView.get() ?: return
                val status = ReflectionUtils.getArg(param.args, Int::class.javaObjectType, 0)
                if (status == STATUS_NOT_ADDED) {
                    showProbablyNotAdded(tv)
                } else {
                    showUnavailable(tv)
                }
            }
        })
    }

    private fun resolveContactChecker(activity: Activity, view: ViewGroup): TextView {
        val textView = view.findViewWithTag(TAG_CONTACT_CHECKER) as? TextView
        if (textView != null) {
            state.checkerView.set(textView)
            return textView
        }
        return createTextMessageView(activity).also {
            state.checkerView.set(it)
            it.tag = TAG_CONTACT_CHECKER
            view.post { view.addView(it) }
        }
    }

    private fun showChecking(textView: TextView) {
        textView.post {
            textView.setText(R.string.checking_if_contact_added)
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
        }
    }

    private fun showContactAdded(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.GREEN)
            textView.setText(R.string.contact_added)
        }
    }

    private fun showProbablyNotAdded(textView: TextView) {
        textView.post {
            textView.setTextColor(Color.YELLOW)
            textView.setText(R.string.contact_probably_not_added)
        }
    }

    private fun showUnavailable(textView: TextView) {
        textView.post {
            textView.setTextColor(DesignUtils.getPrimaryTextColor())
            textView.setText(R.string.not_available)
        }
    }

    private fun checkContactPhotoProfile(userJid: FMessageWpp.UserJid) {
        checkContactPhotoProfile(userJid, 0)
    }

    /**
     * Helper protokol baru ada setelah WhatsApp membuat satu instance SendGetProfilePhoto
     * (mis. saat membuka profil kontak). Percobaan pertama sering terjadi sebelum itu,
     * jadi diulang beberapa kali alih-alih langsung menyerah dan menandai "not available".
     */
    private fun checkContactPhotoProfile(userJid: FMessageWpp.UserJid, attempt: Int) {
        try {
            val constructor = state.sendGetProfilePhotoConstructor ?: return
            val protocolType = state.profilePhotoProtocolType ?: return
            val protocol = state.profilePhotoProtocol
            if (protocol == null || !protocolType.isInstance(protocol)) {
                if (attempt < PROTOCOL_RETRY_MAX) {
                    RETRY_HANDLER.postDelayed({
                        // Lanjutkan hanya kalau view pemeriksa masih terpasang (user belum
                        // pindah chat). Kalau view hilang, percobaan dihentikan.
                        val stillValid = state.checkerView.get()?.isAttachedToWindow == true
                        if (stillValid) {
                            checkContactPhotoProfile(userJid, attempt + 1)
                        } else {
                            logDebug("ContactVerify retry stopped: checker view detached")
                        }
                    }, PROTOCOL_RETRY_DELAY_MS * (attempt + 1).toLong())
                    if (attempt == 0) {
                        logDebug(
                            "ContactVerify protocol helper not ready, retry up to " +
                                "$PROTOCOL_RETRY_MAX x, expected: ${protocolType.name}"
                        )
                    }
                } else {
                    logDebug("ContactVerify protocol helper unavailable after retries: ${protocolType.name}")
                }
                return
            }
            val params = ReflectionUtils.initArray(constructor.parameterTypes)
            params[state.protocolParamIndex] = protocol
            params[state.protocolParamIndex + 1] = userJid.userJid
            val runnable = constructor.newInstance(*params) as Runnable
            state.pendingUserJid.set(userJid.userRawString)
            CompletableFuture.runAsync(runnable)
        } catch (throwable: Throwable) {
            logDebug(throwable)
        }
    }

    override fun getPluginName(): String {
        return "Contact Added Verify"
    }

    private class ContactCheckState {
        @Volatile
        var profilePhotoProtocol: Any? = null
        var profilePhotoProtocolType: Class<*>? = null
        var sendGetProfilePhotoConstructor: Constructor<*>? = null

        /** Indeks parameter helper protokol pada konstruktor SendGetProfilePhoto. */
        @Volatile
        var protocolParamIndex: Int = -1
        val checkerView = AtomicReference<TextView>()
        val pendingUserJid = AtomicReference<String>()
    }
}
