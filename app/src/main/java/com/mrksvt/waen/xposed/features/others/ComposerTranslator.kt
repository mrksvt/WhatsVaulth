package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
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
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private val CACHE_JID = "composer_outgoing"

    @Volatile
    private var inputFieldRef: WeakReference<EditText>? = null

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var selectedLang: String = "auto"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private val DEBOUNCE_MS = 600L

    @Volatile
    private var activePopupRef: WeakReference<PopupWindow>? = null

    @Volatile
    private var pendingTranslation: String? = null

    fun getInputField(): EditText? = inputFieldRef?.get()

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("composer_translator_enabled", true)) return
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
        val editText = activity.findViewById<EditText>(entryId) ?: return

        inputFieldRef = WeakReference(editText)

        val rootView = activity.window.decorView

        if (rootView.findViewWithTag<View>(BUTTON_TAG) != null) return

        injectGlobeIcon(rootView, editText)
        attachTextWatcher(editText, rootView)
        hookSendButton(rootView, editText)
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
                // Cancel previous debounce
                debounceRunnable?.let { mainHandler.removeCallbacks(it) }
                // Dismiss popup if text cleared
                if (text.isEmpty()) {
                    dismissActivePopup()
                    pendingTranslation = null
                    return
                }
                // Debounce: only fetch after user pauses
                val run = Runnable {
                    fetchAndShowPopup(text, editText, rootView)
                }
                debounceRunnable = run
                mainHandler.postDelayed(run, DEBOUNCE_MS)
            }
        })
    }

    private fun fetchAndShowPopup(text: String, editText: EditText, rootView: View) {
        val cacheKey = text.hashCode().toString()
        val cached = TranslationCacheStore.getByJid(CACHE_JID)[cacheKey]
        if (!cached.isNullOrBlank()) {
            showTranslationPopup(editText, rootView, cached)
            pendingTranslation = cached
            return
        }

        val lang = if (selectedLang == "auto") Locale.getDefault().language else selectedLang
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
                TranslationCacheStore.upsert(CACHE_JID, cacheKey, translated)
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
            // Show below the input field
            popup.showAsDropDown(anchor, 0, Utils.dipToPixels(4))
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

    // ── Send button hook — send translated text ───────────────────────────────

    private fun hookSendButton(rootView: View, editText: EditText) {
        val sendBtnId = Utils.getID("send", "id")
        val sendBtn: View? = if (sendBtnId != 0) rootView.findViewById(sendBtnId) else null

        if (sendBtn == null) {
            logDebug("send button not found, skip send hook")
            return
        }

        val listenerInfoField = try {
            val f = View::class.java.getDeclaredField("mListenerInfo")
            f.isAccessible = true
            f
        } catch (_: Exception) { null }

        val originalListener: View.OnClickListener? = try {
            val info = listenerInfoField?.get(sendBtn)
            val listenerField = info?.javaClass?.getDeclaredField("mOnClickListener")
            listenerField?.isAccessible = true
            listenerField?.get(info) as? View.OnClickListener
        } catch (_: Exception) { null }

        sendBtn.setOnClickListener { v ->
            val translation = pendingTranslation
            if (!translation.isNullOrBlank()) {
                val field = inputFieldRef?.get()
                if (field != null) {
                    logDebug("send hook: replacing text with translation: $translation")
                    field.setText(translation)
                    field.setSelection(translation.length)
                    pendingTranslation = null
                }
                dismissActivePopup()
            }
            originalListener?.onClick(v)
        }

        logDebug("send button hooked in composer")
    }

    // ── On-demand translate (globe button click) ──────────────────────────────

    private fun translateAndApply(text: String, editText: EditText, rootView: View) {
        val cacheKey = text.hashCode().toString()
        val cached = TranslationCacheStore.getByJid(CACHE_JID)[cacheKey]
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

        val lang = if (selectedLang == "auto") Locale.getDefault().language else selectedLang
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
                TranslationCacheStore.upsert(CACHE_JID, cacheKey, translated)
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
        val currentLang = prefs.getString("translator_target_lang", "auto") ?: "auto"

        val entries = arrayOf(
            "Otomatis (Locale sistem)", "Indonesia", "English", "Jawa (Javanese)",
            "Sunda (Sundanese)", "Melayu (Malay)", "日本語 (Japanese)", "한국어 (Korean)",
            "中文 (Chinese Simplified)", "Español (Spanish)", "Français (French)", "العربية (Arabic)"
        )
        val values = arrayOf("auto", "id", "en", "jv", "su", "ms", "ja", "ko", "zh-CN", "es", "fr", "ar")

        val checkedItem = values.indexOfFirst { it == currentLang }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(activity)
            .setTitle("Pilih Bahasa Tujuan")
            .setSingleChoiceItems(entries, checkedItem) { dialog, which ->
                val langValue = values.getOrNull(which) ?: return@setSingleChoiceItems
                selectedLang = langValue
                val field = inputFieldRef?.get()
                val text = field?.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    val prefLang = if (langValue == "auto") Locale.getDefault().language else langValue
                    val provider = prefs.getString("translator_provider", "google") ?: "google"
                    val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""
                    val future = if (provider == "groq" && groqKey.isNotBlank()) {
                        translateGroq(text, prefLang)
                    } else {
                        translateGoogle(text, prefLang)
                    }
                    future.thenAccept { translated ->
                        mainHandler.post {
                            if (!translated.isNullOrBlank() && field != null) {
                                pendingTranslation = translated
                                showTranslationPopup(field, rootView, translated)
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun getPluginName(): String = "Composer Translator"
}
