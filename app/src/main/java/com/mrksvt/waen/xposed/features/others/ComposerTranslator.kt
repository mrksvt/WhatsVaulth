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
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.FeatureLoader
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.core.db.TranslationCacheStore
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
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
        waSendListener = null

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
        if (sendBtn.getTag(android.R.id.text1) == BUTTON_TAG) return
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
        if (candidate != null) waSendListener = candidate
        attachSendHook(sendBtn)
        sendBtn.setTag(android.R.id.text1, BUTTON_TAG)
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
                        logDebug("send hook: null listener, remove hook then performClick")
                        sendBtn.setOnClickListener(null)
                        isSendingTranslation = false
                        sendBtn.performClick()
                        sendBtn.postDelayed({ attachSendHook(sendBtn) }, 200)
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

        XposedHelpers.findAndHookMethod(
            android.view.ViewGroup::class.java,
            "onInterceptTouchEvent",
            android.view.MotionEvent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val vg = param.thisObject as? ViewGroup ?: return
                    val globe = vg.findViewWithTag<View>(BUTTON_TAG) ?: return
                    val ev = param.args[0] as? android.view.MotionEvent ?: return
                    val rect = android.graphics.Rect()
                    globe.getHitRect(rect)
                    if (rect.contains(ev.x.toInt(), ev.y.toInt())) {
                        param.result = false
                    }
                }
            }
        )

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
        val sendBtnId = Utils.getID("send", "id")
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
        val model = prefs.getString("groq_translator_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant"

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

    private fun showLanguagePicker(context: android.content.Context, rootView: View) {
        val activity = context as? Activity ?: WppCore.getCurrentActivity() ?: return

        val jid = WppCore.getCurrentUserJid()?.phoneRawString
        if (jid.isNullOrBlank()) {
            XposedBridge.log("[ComposerTranslator] config=not_found defaulting to disabled")
            return
        }
        val (currentEnabled, currentLang) = getPerChatConfig(jid)

        val entries = arrayOf(
            "Otomatis (Locale sistem)", "Indonesia", "English", "Jawa (Javanese)",
            "Sunda (Sundanese)", "Melayu (Malay)", "日本語 (Japanese)", "한국어 (Korean)",
            "中文 (Chinese Simplified)", "Español (Spanish)", "Français (French)", "العربية (Arabic)"
        )
        val values = arrayOf("auto", "id", "en", "jv", "su", "ms", "ja", "ko", "zh-CN", "es", "fr", "ar")

        val sheet = android.app.Dialog(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)

        val dp16 = Utils.dipToPixels(16)
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)

        val sheetLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(activity).apply {
            text = activity.getString(R.string.composer_translator_enabled)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, dp16)
        }
        sheetLayout.addView(title)

        val divider1 = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(1))
                .also { it.bottomMargin = dp8 }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        sheetLayout.addView(divider1)

        val toggleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp8, 0, dp8)
        }
        val toggleLabel = TextView(activity).apply {
            text = activity.getString(R.string.composer_translator_enable_for_chat)
            textSize = 15f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggleSwitch = android.widget.Switch(activity).apply {
            isChecked = currentEnabled
        }
        toggleRow.addView(toggleLabel)
        toggleRow.addView(toggleSwitch)
        sheetLayout.addView(toggleRow)

        val divider2 = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(1))
                .also { it.topMargin = dp4; it.bottomMargin = dp8 }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        sheetLayout.addView(divider2)

        val langLabel = TextView(activity).apply {
            text = activity.getString(R.string.translator_target_lang)
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, 0, 0, dp8)
        }
        sheetLayout.addView(langLabel)

        var selectedIndex = values.indexOfFirst { it == currentLang }.coerceAtLeast(0)

        val radioGroup = android.widget.RadioGroup(activity).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        entries.forEachIndexed { idx, label ->
            val rb = android.widget.RadioButton(activity).apply {
                text = label
                id = idx
                isChecked = idx == selectedIndex
                setTextColor(Color.BLACK)
                textSize = 14f
                setPadding(dp4, dp4, dp4, dp4)
            }
            radioGroup.addView(rb)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedIndex = checkedId
        }

        val scrollView = android.widget.ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Utils.dipToPixels(280)
            )
        }
        scrollView.addView(radioGroup)
        sheetLayout.addView(scrollView)

        val saveBtn = android.widget.Button(activity).apply {
            text = activity.getString(R.string.save)
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#25D366"))
                cornerRadius = Utils.dipToPixels(6).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp16 }
        }
        saveBtn.setOnClickListener {
            val chosenLang = values.getOrElse(selectedIndex) { "auto" }
            val chosenEnabled = toggleSwitch.isChecked
            savePerChatConfig(jid, chosenEnabled, chosenLang)
            XposedBridge.log("[ComposerTranslator] conversation=$jid")
            XposedBridge.log("[ComposerTranslator] enabled=$chosenEnabled")
            XposedBridge.log("[ComposerTranslator] language=$chosenLang")
            sheet.dismiss()
        }
        sheetLayout.addView(saveBtn)

        sheet.setContentView(sheetLayout)
        sheet.show()
    }

    override fun getPluginName(): String = "Composer Translator"
}
