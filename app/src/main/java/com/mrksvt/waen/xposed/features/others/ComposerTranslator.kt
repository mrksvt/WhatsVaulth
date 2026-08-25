package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.FeatureLoader
import com.mrksvt.waen.xposed.core.HookOverrideStore
import com.mrksvt.waen.xposed.core.WppCore
import de.robv.android.xposed.XC_MethodHook
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.Utils
import com.mrksvt.waen.xposed.core.db.TranslationCacheStore
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CompletableFuture

class ComposerTranslator(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private val BUTTON_TAG = "wae_composer_translate_btn"
    private val POPUP_TAG = "wae_composer_translate_popup"

    @Volatile
    private var inputFieldRef: WeakReference<EditText>? = null

    @Volatile
    private var client: OkHttpClient? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private val DEBOUNCE_MS = 600L

    @Volatile
    private var activePopupRef: WeakReference<PopupWindow>? = null

    @Volatile
    private var pendingTranslation: String? = null

    @Volatile
    private var waSendListener: View.OnClickListener? = null

    @Volatile
    private var isSendingTranslation = false

    fun getInputField(): EditText? = inputFieldRef?.get()

    // ── Per-chat config helpers ───────────────────────────────────────────────

    private fun getPerChatConfig(jid: String): Pair<Boolean, String> {
        val privPrefs = WppCore.getPrivPrefs()
        val enabled = privPrefs.getBoolean("ct_enabled_$jid", false)
        val lang = privPrefs.getString("ct_lang_$jid", "auto") ?: "auto"
        return Pair(enabled, lang)
    }

    private fun savePerChatConfig(jid: String, enabled: Boolean, lang: String) {
        WppCore.getPrivPrefs().edit().apply {
            putBoolean("ct_enabled_$jid", enabled)
            putString("ct_lang_$jid", lang)
            apply()
        }
    }

    @Throws(Throwable::class)
    override fun doHook() {
        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName == "Conversation" &&
                state == WppCore.ActivityChangeState.ChangeType.STARTED
            ) {
                activity.window.decorView.post {
                    setupComposer(activity)
                }
            }
        }
    }

    private fun setupComposer(activity: Activity) {
        val entryId = Utils.getID("entry", "id")
        val editText = activity.findViewById<EditText>(entryId)
        if (editText == null) {
            XposedBridge.log("[Composer Translator] entry EditText NOT FOUND pkg=${activity.packageName} entryId=$entryId")
            return
        }

        inputFieldRef = WeakReference(editText)

        val rootView = activity.window.decorView

        if (rootView.findViewWithTag<View>(BUTTON_TAG) != null) {
            hookSendButtonWithRetry(rootView, editText, activity)
            return
        }

        injectGlobeIcon(rootView, editText)
        attachTextWatcher(editText, rootView)
        hookSendButtonWithRetry(rootView, editText, activity)
    }

    private fun hookSendButtonWithRetry(rootView: View, editText: EditText, activity: Activity, attempt: Int = 0) {
        val rawBtn = findSendButton(rootView, editText)
        if (rawBtn == null) {
            if (attempt < 10) {
                mainHandler.postDelayed({ hookSendButtonWithRetry(rootView, editText, activity, attempt + 1) }, 300)
            } else {
                XposedBridge.log("[Composer Translator] send button NOT FOUND after 10 retries pkg=${activity.packageName}")
            }
            return
        }

        if (rawBtn is android.view.ViewStub) {
            rootView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val sendBtnId = Utils.getID("send", "id")
                    val inflated: View? = if (sendBtnId != 0) rootView.findViewById(sendBtnId) else null
                    if (inflated == null || inflated is android.view.ViewStub) return
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    tryHookSendButton(inflated, activity.packageName)
                }
            })
            return
        }

        tryHookSendButton(rawBtn, activity.packageName)
    }

    private fun tryHookSendButton(sendBtn: View, pkg: String) {
        val listenerInfoField = try {
            val f = View::class.java.getDeclaredField("mListenerInfo")
            f.isAccessible = true
            f
        } catch (_: Exception) { null }

        fun getListener(): View.OnClickListener? = try {
            val info = listenerInfoField?.get(sendBtn)
            val lf = info?.javaClass?.getDeclaredField("mOnClickListener")
            lf?.isAccessible = true
            lf?.get(info) as? View.OnClickListener
        } catch (_: Exception) { null }

        val candidate = getListener()
        if (candidate != null && candidate.javaClass.name.contains("ComposerTranslator")) return

        if (sendBtn.getTag(R.id.wae_composer_send_btn_tag) == BUTTON_TAG) {
            if (candidate != null) waSendListener = candidate
            return
        }

        if (candidate == null) {
            logDebug("send button: WA listener not captured, skipping hook to keep button functional")
            return
        }
        waSendListener = candidate
        attachSendHook(sendBtn)
        val id = Utils.getIDFromModule("wae_composer_send_btn_tag")
        if (id != 0) {
            sendBtn.setTag(id, BUTTON_TAG)
        } else {
            sendBtn.setTag(R.id.wae_composer_send_btn_tag, BUTTON_TAG)
        }
    }

    private fun replaceComposerText(field: EditText, translation: String) {
        try {
            val editable = field.text ?: return
            logDebug("replaceComposerText: before='${editable}' len=${editable.length} -> translation='$translation' len=${translation.length}")
            editable.replace(0, editable.length, translation)
            logDebug("replaceComposerText: after replace, field.text='${field.text}'")
            try {
                Selection.setSelection(editable, translation.length)
            } catch (e: Exception) {
                logDebug("selection update failed: ${e.message}")
            }
        } catch (e: Exception) {
            logDebug("replaceComposerText editable.replace failed: ${e.message}, fallback setText")
            field.setText(translation)
            field.setSelection(translation.length)
        }
    }

    private fun attachSendHook(sendBtn: View) {
        sendBtn.setOnClickListener { v ->
            val translation = pendingTranslation
            if (!translation.isNullOrBlank()) {
                val field = inputFieldRef?.get()
                if (field != null) {
                    logDebug("send hook: replacing text with translation: $translation")
                    isSendingTranslation = true
                    replaceComposerText(field, translation)
                    pendingTranslation = null
                    dismissActivePopup()
                    val listener = waSendListener
                    if (listener != null) {
                        logDebug("send hook: firing WA listener=$listener")
                        listener.onClick(v)
                        isSendingTranslation = false
                    } else {
                        logDebug("send hook: null listener, releasing hook")
                        sendBtn.setOnClickListener(null)
                        isSendingTranslation = false
                    }
                    return@setOnClickListener
                }
                dismissActivePopup()
            }
            waSendListener?.onClick(v)
        }
        logDebug("send button hooked in composer")
    }

    // ── Globe icon injection ──────────────────────────────────────────────────

    private fun injectGlobeIcon(rootView: View, editText: EditText) {
        val inputLayoutId = Utils.getID("input_layout_content", "id")
        val attachBtnId = Utils.getID("input_attach_button", "id")

        val container: ViewGroup? = if (inputLayoutId != 0) {
            rootView.findViewById(inputLayoutId)
        } else {
            editText.parent as? ViewGroup
        }

        if (container == null) {
            logDebug("input_layout_content not found, skipping globe injection")
            return
        }

        if (container.findViewWithTag<View>(BUTTON_TAG) != null) return

        val context = container.context
        val dp36 = Utils.dipToPixels(36)
        val dp4 = Utils.dipToPixels(4)

        val attachBtn: View? = if (attachBtnId != 0) {
            rootView.findViewById(attachBtnId)
        } else null

        val globeBtn = android.widget.ImageButton(context).apply {
            tag = BUTTON_TAG
            setImageDrawable(android.graphics.drawable.BitmapDrawable(
                context.resources,
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            ))
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.translator_action_translate)
        }

        val tvGlobe = TextView(context).apply {
            tag = BUTTON_TAG
            text = "\uD83C\uDF10"
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp4, 0, dp4, 0)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.translator_action_translate)
        }

        tvGlobe.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.5f
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    showLanguagePicker(context, rootView)
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        val insertIndex: Int = if (attachBtn != null) {
            val directIdx = container.indexOfChild(attachBtn)
            if (directIdx >= 0) {
                directIdx
            } else {
                var ancestor: View? = attachBtn
                var found = -1
                while (ancestor != null) {
                    val parentView = ancestor.parent
                    if (parentView === container) {
                        found = container.indexOfChild(ancestor)
                        break
                    }
                    ancestor = parentView as? View
                }
                if (found >= 0) found else 0
            }
        } else {
            0
        }

        val lp = if (attachBtn != null) {
            val aw = attachBtn.layoutParams?.width?.takeIf { it > 0 } ?: dp36
            val ah = attachBtn.layoutParams?.height?.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
            ViewGroup.LayoutParams(aw, ah)
        } else {
            ViewGroup.LayoutParams(dp36, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        tvGlobe.minimumHeight = attachBtn?.measuredHeight?.takeIf { it > 0 } ?: dp36
        tvGlobe.minimumWidth = attachBtn?.measuredWidth?.takeIf { it > 0 } ?: dp36

        try {
            container.addView(tvGlobe, insertIndex, lp)
            logDebug("globe icon injected at index $insertIndex in ${container.javaClass.simpleName}")
        } catch (e: Exception) {
            logDebug("globe inject fallback: ${e.message}")
            container.addView(tvGlobe, lp)
        }
    }

    // ── TextWatcher for real-time translation popup ───────────────────────────

    private fun attachTextWatcher(editText: EditText, rootView: View) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                debounceRunnable?.let { mainHandler.removeCallbacks(it) }
                if (isSendingTranslation) return
                if (text.isEmpty()) {
                    dismissActivePopup()
                    pendingTranslation = null
                    return
                }
                if (waSendListener == null) {
                    tryCaptureListenerFromInflatedButton(rootView, editText)
                }
                val run = Runnable {
                    fetchAndShowPopup(text, editText, rootView)
                }
                debounceRunnable = run
                mainHandler.postDelayed(run, DEBOUNCE_MS)
            }
        })
    }

    private fun tryCaptureListenerFromInflatedButton(rootView: View, editText: EditText) {
        val sendBtnId = Utils.getID("send", "id")
        val btn: View? = if (sendBtnId != 0) rootView.findViewById(sendBtnId) else null
        val target = btn ?: findSendButton(rootView, editText) ?: return
        if (target is android.view.ViewStub) return
        if (target.tag == BUTTON_TAG) return
        try {
            val f = View::class.java.getDeclaredField("mListenerInfo")
            f.isAccessible = true
            val info = f.get(target) ?: return
            val lf = info.javaClass.getDeclaredField("mOnClickListener")
            lf.isAccessible = true
            val listener = lf.get(info) as? View.OnClickListener ?: return
            XposedBridge.log("[Composer Translator] lazy-captured WA send listener=$listener pkg=${target.context.packageName}")
            waSendListener = listener
            target.tag = BUTTON_TAG
            attachSendHook(target)
        } catch (_: Exception) {}
    }

    private fun fetchAndShowPopup(text: String, editText: EditText, rootView: View) {
        val jid = WppCore.getCurrentUserJid()?.phoneRawString ?: run {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        if (jid.isBlank()) {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        val (enabled, rawLang) = getPerChatConfig(jid)
        XposedBridge.log("[ComposerTranslator] conversation=$jid")
        XposedBridge.log("[ComposerTranslator] enabled=$enabled")
        XposedBridge.log("[ComposerTranslator] language=$rawLang")
        if (!enabled) {
            XposedBridge.log("[ComposerTranslator] bypass")
            return
        }
        XposedBridge.log("[ComposerTranslator] translating")
        val cacheKey = text.hashCode().toString()
        val cached = TranslationCacheStore.getByJid(jid)[cacheKey]
        if (!cached.isNullOrBlank()) {
            showTranslationPopup(editText, rootView, cached)
            pendingTranslation = cached
            return
        }

        val lang = if (rawLang == "auto") Locale.getDefault().language else rawLang
        val provider = prefs.getString("translator_provider", "google") ?: "google"
        val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""

        val future: CompletableFuture<String?> = if (provider == "groq" && groqKey.isNotBlank()) {
            translateGroq(text, lang)
        } else {
            translateGoogle(text, lang)
        }

        future.thenAccept { translated ->
            mainHandler.post {
                if (translated.isNullOrBlank()) return@post
                TranslationCacheStore.upsert(jid, cacheKey, translated)
                pendingTranslation = translated
                showTranslationPopup(editText, editText, translated)
                logDebug("real-time popup: $translated")
            }
        }.exceptionally { err ->
            logDebug("real-time translate error: ${err.message}")
            mainHandler.post {
                val msg = err.cause?.message ?: err.message ?: "Translation failed"
                try {
                    com.google.android.material.snackbar.Snackbar.make(
                        rootView,
                        rootView.context.getString(R.string.translator_failed) + ": $msg",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                    Toast.makeText(rootView.context, "Gagal: $msg", Toast.LENGTH_SHORT).show()
                }
            }
            null
        }
    }

    private fun showTranslationPopup(anchor: View, rootView: View, translation: String) {
        dismissActivePopup()

        val context = anchor.context
        val dp8 = Utils.dipToPixels(8)
        val dp12 = Utils.dipToPixels(12)

        val popupBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Utils.dipToPixels(8).toFloat()
            setColor(Color.parseColor("#CC1A1A2E"))
            setStroke(Utils.dipToPixels(1), Color.parseColor("#4DFFFFFF"))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = popupBg
            setPadding(dp12, dp8, dp12, dp8)
        }

        val label = TextView(context).apply {
            text = context.getString(R.string.translator_loading)
            textSize = 10f
            setTextColor(Color.parseColor("#99FFFFFF"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val translatedView = TextView(context).apply {
            tag = POPUP_TAG
            text = translation
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        container.addView(label)
        container.addView(translatedView)

        // Measure container
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
            elevation = Utils.dipToPixels(4).toFloat()
        }

        try {
            val popupHeight = container.measuredHeight.takeIf { it > 0 }
                ?: Utils.dipToPixels(64)
            popup.showAsDropDown(anchor, 0, -(anchor.height + popupHeight + Utils.dipToPixels(4)))
            activePopupRef = WeakReference(popup)
        } catch (e: Exception) {
            logDebug("popup show error: ${e.message}")
        }
    }

    private fun dismissActivePopup() {
        try {
            activePopupRef?.get()?.dismiss()
        } catch (_: Exception) {}
        activePopupRef = null
    }

    private fun findSendButton(rootView: View, editText: EditText): View? {
        val context = rootView.context
        val overrideName = HookOverrideStore.getResourceOverride(context, "composer_send_btn") as? String
        val sendBtnId = if (!overrideName.isNullOrBlank()) {
            Utils.getID(overrideName, "id").takeIf { it != 0 } ?: Utils.getID("send", "id")
        } else {
            Utils.getID("send", "id")
        }
        
        if (sendBtnId != 0) {
            val v = rootView.findViewById<View>(sendBtnId)
            if (v != null) return v
        }
        
        val candidateIds = listOf("send_btn", "send_button", "btn_send", "compose_send", "conversation_send")
        for (name in candidateIds) {
            val id = Utils.getID(name, "id")
            if (id != 0) {
                val v = rootView.findViewById<View>(id)
                if (v != null) {
                    logDebug("send button found via id: $name")
                    return v
                }
            }
        }
        
        val container = editText.parent as? ViewGroup ?: return null
        val editIndex = container.indexOfChild(editText)
        for (i in editIndex + 1 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.isClickable && child.visibility == View.VISIBLE &&
                (child is android.widget.ImageButton || child is android.widget.ImageView || child is android.widget.Button)) {
                logDebug("send button found via traversal at index $i")
                return child
            }
        }
        
        val parent = container.parent as? ViewGroup
        if (parent != null) {
            val containerIndex = parent.indexOfChild(container)
            for (i in 0 until parent.childCount) {
                if (i == containerIndex) continue
                val sibling = parent.getChildAt(i)
                if (sibling.isClickable && sibling.visibility == View.VISIBLE &&
                    (sibling is android.widget.ImageButton || sibling is android.widget.ImageView)) {
                    logDebug("send button found via sibling traversal at index $i")
                    return sibling
                }
            }
        }
        
        return null
    }

    // ── On-demand translate (globe button click) ──────────────────────────────

    private fun translateAndApply(text: String, editText: EditText, rootView: View) {
        val jid = WppCore.getCurrentUserJid()?.phoneRawString ?: run {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        if (jid.isBlank()) {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        val (enabled, rawLang) = getPerChatConfig(jid)
        XposedBridge.log("[ComposerTranslator] conversation=$jid")
        XposedBridge.log("[ComposerTranslator] enabled=$enabled")
        XposedBridge.log("[ComposerTranslator] language=$rawLang")
        if (!enabled) {
            XposedBridge.log("[ComposerTranslator] bypass")
            return
        }
        XposedBridge.log("[ComposerTranslator] translating")
        val cacheKey = text.hashCode().toString()
        val cached = TranslationCacheStore.getByJid(jid)[cacheKey]
        if (!cached.isNullOrBlank()) {
            logDebug("cache hit for composer translation")
            mainHandler.post {
                editText.setText(cached)
                editText.setSelection(cached.length)
                pendingTranslation = null
                dismissActivePopup()
            }
            return
        }

        val lang = if (rawLang == "auto") Locale.getDefault().language else rawLang
        val provider = prefs.getString("translator_provider", "google") ?: "google"
        val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""

        val future: CompletableFuture<String?> = if (provider == "groq" && groqKey.isNotBlank()) {
            translateGroq(text, lang)
        } else {
            translateGoogle(text, lang)
        }

        future.thenAccept { translated ->
            mainHandler.post {
                if (translated.isNullOrBlank()) return@post
                TranslationCacheStore.upsert(jid, cacheKey, translated)
                editText.setText(translated)
                editText.setSelection(translated.length)
                pendingTranslation = null
                dismissActivePopup()
                logDebug("globe translate applied: $translated")
            }
        }.exceptionally { err ->
            mainHandler.post {
                val msg = err.cause?.message ?: err.message ?: "Translation failed"
                try {
                    com.google.android.material.snackbar.Snackbar.make(
                        rootView,
                        rootView.context.getString(R.string.translator_failed) + ": $msg",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction(rootView.context.getString(R.string.translator_retry)) {
                        translateAndApply(text, editText, rootView)
                    }.show()
                } catch (_: Exception) {
                    Toast.makeText(rootView.context, "Gagal: $msg", Toast.LENGTH_SHORT).show()
                }
            }
            null
        }
    }

    // ── HTTP translation backends ─────────────────────────────────────────────

    private fun translateGroq(text: String, languageDest: String): CompletableFuture<String?> {
        if (client == null) client = OkHttpClient()
        val future = CompletableFuture<String?>()
        val apiKey = prefs.getString("groq_translator_api_key", "") ?: ""
        val model = prefs.getString("groq_translator_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"

        if (apiKey.isBlank()) {
            future.completeExceptionally(RuntimeException("Groq API Key belum diisi"))
            return future
        }

        @Suppress("DEPRECATION")
        val langName = Locale(languageDest).getDisplayLanguage(Locale.ENGLISH).ifBlank { languageDest }

        val systemPrompt = prefs.getString("groq_custom_system_prompt", "")?.takeIf { it.isNotBlank() }
            ?: "You are a professional translator. Translate the given text to $langName. Return ONLY the translated text, no explanations."

        val jsonBody = """
            {
                "model": "$model",
                "messages": [
                    {"role": "system", "content": ${org.json.JSONObject.quote(systemPrompt)}},
                    {"role": "user", "content": ${org.json.JSONObject.quote(text)}}
                ],
                "temperature": 0.3
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(RuntimeException("Groq request failed: ${e.message}"))
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val result = org.json.JSONObject(response.body.string())
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        future.complete(result.trim())
                    } catch (e: Exception) {
                        future.completeExceptionally(RuntimeException("Groq parse error: ${e.message}"))
                    }
                } else {
                    future.completeExceptionally(RuntimeException("Groq response error: ${response.code}"))
                }
            }
        })

        return future
    }

    private fun translateGoogle(text: String, languageDest: String): CompletableFuture<String?> {
        if (client == null) client = OkHttpClient()
        val future = CompletableFuture<String?>()

        val url = try {
            val customEndpoint = prefs.getString("google_translate_endpoint", "") ?: ""
            val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint
                          else "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s"
            String.format(baseUrl, languageDest, URLEncoder.encode(text, "UTF-8"))
        } catch (e: Exception) {
            future.completeExceptionally(RuntimeException("Error encoding URL: ${e.message}"))
            return future
        }

        client!!.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(RuntimeException("Request failed: ${e.message}"))
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val translations = JSONArray(response.body.string()).getJSONArray(0)
                        val sb = StringBuilder()
                        for (i in 0 until translations.length()) {
                            sb.append(translations.getJSONArray(i).getString(0))
                        }
                        future.complete(sb.toString())
                    } catch (e: Exception) {
                        future.completeExceptionally(RuntimeException("Error processing response: ${e.message}"))
                    }
                } else {
                    future.completeExceptionally(RuntimeException("Response was not successful."))
                }
            }
        })

        return future
    }

    private val ALL_LANGUAGES = listOf(
        LangEntry("en",    "English",    "English",         "🇺🇸"),
        LangEntry("id",    "Indonesian", "Indonesia",       "🇮🇩"),
        LangEntry("ms",    "Malay",      "Bahasa Melayu",   "🇲🇾"),
        LangEntry("zh-CN", "Chinese",    "中文",             "🇨🇳"),
        LangEntry("ja",    "Japanese",   "日本語",           "🇯🇵"),
        LangEntry("ko",    "Korean",     "한국어",           "🇰🇷"),
        LangEntry("ar",    "Arabic",     "العربية",         "🇸🇦"),
        LangEntry("es",    "Spanish",    "Español",         "🇪🇸"),
        LangEntry("fr",    "French",     "Français",        "🇫🇷"),
        LangEntry("pt",    "Portuguese", "Português",       "🇧🇷"),
        LangEntry("de",    "German",     "Deutsch",         "🇩🇪"),
        LangEntry("ru",    "Russian",    "Русский",         "🇷🇺"),
        LangEntry("hi",    "Hindi",      "हिन्दी",          "🇮🇳"),
        LangEntry("th",    "Thai",       "ไทย",             "🇹🇭"),
        LangEntry("vi",    "Vietnamese", "Tiếng Việt",      "🇻🇳"),
        LangEntry("tr",    "Turkish",    "Türkçe",          "🇹🇷"),
        LangEntry("pl",    "Polish",     "Polski",          "🇵🇱"),
        LangEntry("nl",    "Dutch",      "Nederlands",      "🇳🇱"),
        LangEntry("sv",    "Swedish",    "Svenska",         "🇸🇪"),
        LangEntry("da",    "Danish",     "Dansk",           "🇩🇰"),
        LangEntry("fi",    "Finnish",    "Suomi",           "🇫🇮"),
        LangEntry("no",    "Norwegian",  "Norsk",           "🇳🇴"),
        LangEntry("cs",    "Czech",      "Čeština",         "🇨🇿"),
        LangEntry("sk",    "Slovak",     "Slovenčina",      "🇸🇰"),
        LangEntry("hu",    "Hungarian",  "Magyar",          "🇭🇺"),
        LangEntry("ro",    "Romanian",   "Română",          "🇷🇴"),
        LangEntry("bg",    "Bulgarian",  "Български",       "🇧🇬"),
        LangEntry("uk",    "Ukrainian",  "Українська",      "🇺🇦"),
        LangEntry("hr",    "Croatian",   "Hrvatski",        "🇭🇷"),
        LangEntry("sr",    "Serbian",    "Српски",          "🇷🇸"),
        LangEntry("sl",    "Slovenian",  "Slovenščina",     "🇸🇮"),
        LangEntry("lt",    "Lithuanian", "Lietuvių",        "🇱🇹"),
        LangEntry("lv",    "Latvian",    "Latviešu",        "🇱🇻"),
        LangEntry("et",    "Estonian",   "Eesti",           "🇪🇪"),
        LangEntry("mt",    "Maltese",    "Malti",           "🇲🇹"),
        LangEntry("ga",    "Irish",      "Gaeilge",         "🇮🇪"),
        LangEntry("sq",    "Albanian",   "Shqip",           "🇦🇱"),
        LangEntry("mk",    "Macedonian", "Македонски",      "🇲🇰"),
        LangEntry("bs",    "Bosnian",    "Bosanski",        "🇧🇦"),
        LangEntry("az",    "Azerbaijani","Azərbaycan",      "🇦🇿"),
        LangEntry("ka",    "Georgian",   "ქართული",        "🇬🇪"),
        LangEntry("hy",    "Armenian",   "Հայերեն",         "🇦🇲"),
        LangEntry("is",    "Icelandic",  "Íslenska",        "🇮🇸"),
        LangEntry("eu",    "Basque",     "Euskara",         "🏴"),
        LangEntry("ca",    "Catalan",    "Català",          "🏴"),
        LangEntry("gl",    "Galician",   "Galego",          "🏴"),
        LangEntry("af",    "Afrikaans",  "Afrikaans",       "🇿🇦"),
        LangEntry("sw",    "Swahili",    "Kiswahili",       "🇰🇪"),
        LangEntry("tl",    "Filipino",   "Filipino",        "🇵🇭"),
        LangEntry("jv",    "Javanese",   "Basa Jawa",       "🇮🇩"),
        LangEntry("su",    "Sundanese",  "Basa Sunda",      "🇮🇩")
    )

    private val DEFAULT_POPULAR = listOf(
        "en", "id", "ms", "zh-CN", "ja", "ko", "ar", "es", "fr",
        "pt", "de", "ru", "hi", "th", "vi", "tr", "pl", "nl",
        "sv", "da", "fi", "no", "cs", "sk", "hu", "ro", "bg",
        "uk", "hr", "sr", "sl", "lt", "lv", "et", "mt", "ga",
        "sq", "mk", "bs", "az", "ka", "hy", "is", "eu", "ca",
        "gl", "af", "sw", "tl", "jv", "su"
    )

    private fun loadRecentLangs(): MutableList<String> {
        val prefs = WppCore.getPrivPrefs()
        return try {
            val arr = org.json.JSONArray(prefs.getString("ct_recent_langs", "[]") ?: "[]")
            MutableList(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveRecentLangs(list: List<String>) {
        WppCore.getPrivPrefs().edit()
            .putString("ct_recent_langs", org.json.JSONArray(list).toString())
            .apply()
    }

    private fun pushRecentLang(code: String) {
        val recent = loadRecentLangs()
        recent.remove(code)
        recent.add(0, code)
        if (recent.size > 9) recent.subList(9, recent.size).clear()
        saveRecentLangs(recent)
    }

    private fun buildGridLanguages(): List<LangEntry> {
        val systemLang = java.util.Locale.getDefault().language
        val recent = loadRecentLangs().filter { code ->
            code != systemLang && ALL_LANGUAGES.any { it.code == code }
        }
        val fallback = DEFAULT_POPULAR.filter { code ->
            code != systemLang && recent.none { it == code }
        }
        val combined = (recent + fallback)
            .mapNotNull { code -> ALL_LANGUAGES.find { it.code == code } }
        return combined.take(9)
    }

    private fun showFullLanguageDialog(
        activity: Activity,
        currentCode: String,
        onPick: (LangEntry) -> Unit
    ) {
        val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.apply {
            setGravity(android.view.Gravity.CENTER)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (activity.resources.displayMetrics.heightPixels * 0.80f).toInt()
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val dp4  = Utils.dipToPixels(4)
        val dp8  = Utils.dipToPixels(8)
        val dp12 = Utils.dipToPixels(12)
        val dp16 = Utils.dipToPixels(16)
        val dp20 = Utils.dipToPixels(20)
        val dp48 = Utils.dipToPixels(48)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2A2A"))
                cornerRadius = dp20.toFloat()
            }
            setPadding(dp16, dp16, dp16, dp16)
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val headerTitle = TextView(activity).apply {
            text = "Pilih Bahasa"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(activity).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp8, dp8, dp8, dp8)
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(headerTitle)
        headerRow.addView(closeBtn)
        container.addView(headerRow)

        val searchBox = android.widget.EditText(activity).apply {
            hint = "Cari bahasa..."
            setHintTextColor(Color.parseColor("#8A8A8A"))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A3A3A"))
                setStroke(Utils.dipToPixels(1), Color.parseColor("#3A3A3A"))
                cornerRadius = dp8.toFloat()
            }
            setPadding(dp12, dp8, dp12, dp8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp12
                bottomMargin = dp8
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        container.addView(searchBox)

        val scrollView = android.widget.ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listLayout)
        container.addView(scrollView)

        fun rebuildList(query: String) {
            listLayout.removeAllViews()
            val filtered = if (query.isBlank()) ALL_LANGUAGES
            else ALL_LANGUAGES.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.nativeName.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true)
            }
            filtered.forEach { lang ->
                val isSelected = lang.code == currentCode
                val rowBg = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#1B3A2A") else Color.parseColor("#1E2A2A"))
                    setStroke(Utils.dipToPixels(1), if (isSelected) Color.parseColor("#25D366") else Color.parseColor("#2A3A3A"))
                    cornerRadius = dp8.toFloat()
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rowBg
                    setPadding(dp12, dp12, dp12, dp12)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp4
                    }
                    isClickable = true
                    isFocusable = true
                }
                val flagTv = TextView(activity).apply {
                    text = lang.flag
                    textSize = 22f
                    layoutParams = LinearLayout.LayoutParams(dp48, LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER
                }
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        leftMargin = dp8
                    }
                }
                val nameTv = TextView(activity).apply {
                    text = lang.name
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
                val nativeTv = TextView(activity).apply {
                    text = lang.nativeName
                    textSize = 12f
                    setTextColor(Color.parseColor("#8A8A8A"))
                }
                textCol.addView(nameTv)
                textCol.addView(nativeTv)
                row.addView(flagTv)
                row.addView(textCol)
                if (isSelected) {
                    val checkTv = TextView(activity).apply {
                        text = "✓"
                        textSize = 16f
                        setTextColor(Color.parseColor("#25D366"))
                        setPadding(dp8, 0, 0, 0)
                    }
                    row.addView(checkTv)
                }
                row.setOnClickListener {
                    onPick(lang)
                    dialog.dismiss()
                }
                listLayout.addView(row)
            }
        }

        rebuildList("")
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                rebuildList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showLanguagePicker(context: android.content.Context, rootView: View) {
        val activity = context as? Activity ?: WppCore.getCurrentActivity() ?: return

        val jid = WppCore.getCurrentUserJid()?.phoneRawString
        if (jid.isNullOrBlank()) {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        val (currentEnabled, currentLang) = getPerChatConfig(jid)

        // Backward compat: "auto" saved pref maps to system locale in triggerTranslate
        // but picker no longer shows it — resolve display lang for pill init
        val displayLang = if (currentLang == "auto") java.util.Locale.getDefault().language else currentLang

        val sheet = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        sheet.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val dp2 = Utils.dipToPixels(2)
        val dp4 = Utils.dipToPixels(4)
        val dp8 = Utils.dipToPixels(8)
        val dp12 = Utils.dipToPixels(12)
        val dp16 = Utils.dipToPixels(16)
        val dp20 = Utils.dipToPixels(20)
        val dp32 = Utils.dipToPixels(32)
        val dp40 = Utils.dipToPixels(40)
        val dp48 = Utils.dipToPixels(48)

        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()

        val scrollView = android.widget.ScrollView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
        }

        val rootLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16 + Utils.dipToPixels(32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2A2A"))
                cornerRadius = dp20.toFloat()
            }
        }
        scrollView.addView(rootLayout)

        val dragHandle = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp4, dp32).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp8
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3A3A3A"))
                cornerRadius = dp2.toFloat()
            }
        }
        rootLayout.addView(dragHandle)

        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val title = TextView(activity).apply {
            text = "Composer Translator"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)

        val closeBtn = TextView(activity).apply {
            text = "✕"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp8, dp8, dp8, dp8)
            setOnClickListener { sheet.dismiss() }
        }
        titleRow.addView(closeBtn)
        rootLayout.addView(titleRow)

        val subtitle = TextView(activity).apply {
            text = "Atur terjemahan khusus untuk obrolan ini."
            textSize = 13f
            setTextColor(Color.parseColor("#8A8A8A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp4
            }
        }
        rootLayout.addView(subtitle)

        val toggleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp16
            }
        }

        val badgeBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1B3A2A"))
            cornerRadius = dp20.toFloat()
        }
        val badgeContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp40, dp40)
        }
        val badgeView = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp40, dp40).apply { gravity = Gravity.CENTER }
            background = badgeBg
        }
        val badgeText = TextView(activity).apply {
            text = "A⇄"
            textSize = 16f
            setTextColor(Color.parseColor("#25D366"))
            layoutParams = FrameLayout.LayoutParams(dp40, dp40).apply { gravity = Gravity.CENTER }
            gravity = Gravity.CENTER
        }
        badgeContainer.addView(badgeView)
        badgeContainer.addView(badgeText)
        toggleRow.addView(badgeContainer)

        val toggleTexts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp12
                rightMargin = dp12
            }
        }
        val toggleTitle = TextView(activity).apply {
            text = "Aktifkan untuk obrolan ini"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val toggleDesc = TextView(activity).apply {
            text = "Composer akan otomatis menerjemahkan pesan sebelum dikirim."
            textSize = 13f
            setTextColor(Color.parseColor("#8A8A8A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp2
            }
        }
        toggleTexts.addView(toggleTitle)
        toggleTexts.addView(toggleDesc)
        toggleRow.addView(toggleTexts)

        val toggleSwitch = android.widget.Switch(activity).apply {
            isChecked = currentEnabled
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val green = Color.parseColor("#25D366")
            toggleSwitch.thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(green, Color.LTGRAY)
            )
            toggleSwitch.trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.parseColor("#80C8F0C0"), Color.LTGRAY)
            )
        }
        toggleRow.addView(toggleSwitch)
        rootLayout.addView(toggleRow)

        val divider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(1)).apply {
                topMargin = dp8
                bottomMargin = dp8
            }
            setBackgroundColor(Color.parseColor("#3A3A3A"))
        }
        rootLayout.addView(divider)

        val langSection = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val langLabel = TextView(activity).apply {
            text = "Bahasa target"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val langDesc = TextView(activity).apply {
            text = "Pilih bahasa untuk menerjemahkan pesan yang Anda ketik."
            textSize = 13f
            setTextColor(Color.parseColor("#8A8A8A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp2
            }
        }
        langSection.addView(langLabel)
        langSection.addView(langDesc)

        val gridLanguages = buildGridLanguages()
        var selectedLang: LangEntry = gridLanguages.firstOrNull { it.code == displayLang }
            ?: ALL_LANGUAGES.firstOrNull { it.code == displayLang }
            ?: gridLanguages.firstOrNull()
            ?: ALL_LANGUAGES.first()

        val langPillBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1E2A2A"))
            setStroke(Utils.dipToPixels(1), Color.parseColor("#3A3A3A"))
            cornerRadius = dp20.toFloat()
        }
        val langPill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = langPillBg
            setPadding(dp12, dp8, dp12, dp8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp8
            }
            isClickable = true
            isFocusable = true
        }
        val langPillText = TextView(activity).apply {
            text = "🌐 ${selectedLang.flag} ${selectedLang.name} ▾"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        langPill.addView(langPillText)
        langSection.addView(langPill)
        rootLayout.addView(langSection)

        val gridLayout = GridLayout(activity).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp8
            }
        }

        val cellRefs = mutableListOf<Pair<View, View>>()

        fun refreshGridSelection() {
            cellRefs.forEach { (c, d) ->
                val code = c.tag as? String ?: ""
                val sel = code == selectedLang.code
                (c.background as? GradientDrawable)?.setStroke(
                    Utils.dipToPixels(1),
                    if (sel) Color.parseColor("#25D366") else Color.parseColor("#3A3A3A")
                )
                (d.background as? GradientDrawable)?.apply {
                    setColor(if (sel) Color.parseColor("#25D366") else Color.parseColor("#1E2A2A"))
                    setStroke(Utils.dipToPixels(2), if (sel) Color.parseColor("#25D366") else Color.parseColor("#3A3A3A"))
                }
            }
            langPillText.text = "🌐 ${selectedLang.flag} ${selectedLang.name} ▾"
        }

        langPill.setOnClickListener {
            showFullLanguageDialog(activity, selectedLang.code) { picked ->
                selectedLang = picked
                refreshGridSelection()
            }
        }

        gridLanguages.forEachIndexed { idx, lang ->
            val isSelected = lang.code == selectedLang.code
            val cellBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2A2A"))
                setStroke(Utils.dipToPixels(1), if (isSelected) Color.parseColor("#25D366") else Color.parseColor("#3A3A3A"))
                cornerRadius = Utils.dipToPixels(12).toFloat()
            }
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cellBg
                setPadding(dp12, dp12, dp12, dp12)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    leftMargin = if (idx % 3 != 0) dp4 else 0
                    rightMargin = if (idx % 3 != 2) dp4 else 0
                    topMargin = if (idx >= 3) dp4 else 0
                    bottomMargin = dp4
                }
                isClickable = true
                isFocusable = true
            }

            val flagView = TextView(activity).apply {
                text = lang.flag
                textSize = 22f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp8
                }
            }
            val nameView = TextView(activity).apply {
                text = lang.name
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val nativeView = TextView(activity).apply {
                text = lang.nativeName
                textSize = 12f
                setTextColor(Color.parseColor("#8A8A8A"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp2
                }
            }
            textCol.addView(nameView)
            textCol.addView(nativeView)

            val dotSize = Utils.dipToPixels(28)
            val dotBg = GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#25D366") else Color.parseColor("#1E2A2A"))
                setStroke(Utils.dipToPixels(2), if (isSelected) Color.parseColor("#25D366") else Color.parseColor("#3A3A3A"))
                cornerRadius = (dotSize / 2).toFloat()
            }
            val radioDot = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize)
                background = dotBg
            }

            cell.addView(flagView)
            cell.addView(textCol)
            cell.addView(radioDot)

            cell.setOnClickListener {
                selectedLang = lang
                refreshGridSelection()
            }
            cell.tag = lang.code
            cellRefs.add(Pair(cell, radioDot))
            gridLayout.addView(cell)
        }
        rootLayout.addView(gridLayout)

        val privacyRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp12
            }
        }
        val privacyBadge = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp32, dp32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B3A2A"))
                cornerRadius = dp16.toFloat()
            }
        }
        val privacyBadgeContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp32, dp32)
        }
        val privacyBadgeText = TextView(activity).apply {
            text = "🛡️"
            textSize = 16f
            layoutParams = FrameLayout.LayoutParams(dp32, dp32).apply { gravity = Gravity.CENTER }
            gravity = Gravity.CENTER
        }
        privacyBadgeContainer.addView(privacyBadge, FrameLayout.LayoutParams(dp32, dp32).apply { gravity = Gravity.CENTER })
        privacyBadgeContainer.addView(privacyBadgeText)
        privacyRow.addView(privacyBadgeContainer)

        val privacyTexts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp12
            }
        }
        val privacyTitle = TextView(activity).apply {
            text = "Privasi terjamin"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#25D366"))
        }
        val privacyDesc = TextView(activity).apply {
            text = "Pesan Anda hanya diproses saat dikirim dan tidak disimpan secara permanen."
            textSize = 12f
            setTextColor(Color.parseColor("#8A8A8A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp2
            }
        }
        privacyTexts.addView(privacyTitle)
        privacyTexts.addView(privacyDesc)
        privacyRow.addView(privacyTexts)
        rootLayout.addView(privacyRow)

        val saveBtn = android.widget.Button(activity).apply {
            text = "💾 SIMPAN"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#25D366"))
                cornerRadius = dp12.toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp48
            ).apply { topMargin = dp16 }
            setOnClickListener {
                val chosenLang = selectedLang.code
                val chosenEnabled = toggleSwitch.isChecked
                pushRecentLang(chosenLang)
                savePerChatConfig(jid, chosenEnabled, chosenLang)
                XposedBridge.log("[ComposerTranslator] conversation=$jid")
                XposedBridge.log("[ComposerTranslator] enabled=$chosenEnabled")
                XposedBridge.log("[ComposerTranslator] language=$chosenLang")
                sheet.dismiss()
            }
        }
        rootLayout.addView(saveBtn)

        sheet.setContentView(scrollView)
        sheet.show()
    }

    private data class LangEntry(
        val code: String,
        val name: String,
        val nativeName: String,
        val flag: String
    )

    override fun getPluginName(): String = "Composer Translator"
}
