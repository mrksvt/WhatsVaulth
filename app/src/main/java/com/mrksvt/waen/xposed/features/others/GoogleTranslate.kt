package com.mrksvt.waen.xposed.features.others

import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator.loadCheckSupportLanguage
import com.mrksvt.waen.xposed.features.listeners.ConversationItemListener
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences
import com.mrksvt.waen.R
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.IOException
import java.lang.reflect.Method
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CompletableFuture

class GoogleTranslate(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {
    private var client: OkHttpClient? = null

    override fun doHook() {
        if (!prefs.getBoolean("google_translate", false)) return

        val checkSupportLanguage = loadCheckSupportLanguage(classLoader)

        XposedBridge.hookMethod(checkSupportLanguage, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = "pt"
                param.args[1] = "en"
            }
        })

        try {
            val translatorClazz = findFirstClassUsingName(
                classLoader,
                StringMatchType.EndsWith,
                "UnityMessageTranslation"
            )

            XposedBridge.hookAllMethods(translatorClazz, "translate", object : XC_MethodReplacement() {

                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    val currentMethod = param.method as Method
                    val unityTranslationResultClass = currentMethod.returnType
                    val provider = prefs.getString("translator_provider", "google") ?: "google"
                    val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""
                    val prefLang = prefs.getString("translator_target_lang", "auto") ?: "auto"
                    val lang = if (prefLang == "auto") Locale.getDefault().language else prefLang
                    if (currentMethod.parameterTypes[0] == String::class.java) {
                        val text = param.args[0] as String?
                        val translation = if (provider == "groq" && groqKey.isNotBlank()) {
                            translateGroq(text, lang).get()
                        } else {
                            translateGoogle(text, lang).get()
                        }
                        return unityTranslationResultClass.getConstructor(
                            String::class.java,
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        ).newInstance(translation, 1, 0)
                    } else {
                        val list = param.args[0] as List<*>
                        val translations = list.map { item ->
                            val text2 = item as String?
                            if (provider == "groq" && groqKey.isNotBlank()) {
                                translateGroq(text2, lang).get()
                            } else {
                                translateGoogle(text2, lang).get()
                            }
                        }
                        return unityTranslationResultClass.getConstructor(
                            Array<String?>::class.java,
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        ).newInstance(translations.toTypedArray(), 1, 0)
                    }
                }
            })
        } catch (e: Exception) {
            XposedBridge.log("GoogleTranslate: UnityMessageTranslation hook skipped: ${e.message}")
        }

        hookBubbleTap()
    }

    private fun hookBubbleTap() {
        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(fMessage: FMessageWpp, view: ViewGroup, position: Int, convertView: View?) {
                val messageText = fMessage.messageStr ?: return
                if (messageText.isBlank()) return

                val messageId = fMessage.key.messageID
                val isFromMe = fMessage.key.isFromMe
                val conversationJid = try {
                    de.robv.android.xposed.XposedHelpers.callMethod(
                        fMessage.key.remoteJid.userJid, "getRawString"
                    ) as? String ?: ""
                } catch (_: Exception) { "" }

                TranslatorWrapperAdapter.registerJidForCurrentAdapter(conversationJid)

                val messageTextView = view.findViewById<TextView>(Utils.getID("message_text", "id"))
                val anchor = messageTextView ?: view

                val gestureDetector = GestureDetector(anchor.context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return false
                        showTranslatePopup(anchor, view, messageText, messageId, isFromMe, conversationJid)
                        return true
                    }
                })

                view.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            }
        })
    }

    private fun showTranslatePopup(anchor: View, rootView: ViewGroup, messageText: String, messageId: String, isFromMe: Boolean, conversationJid: String) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.gravity = if (isFromMe) android.view.Gravity.END else android.view.Gravity.START
        popup.menu.add(0, 1, 0, anchor.context.getString(R.string.translator_action_translate))

        if (TranslatorWrapperAdapter.hasTranslation(conversationJid, messageId)) {
            popup.menu.add(0, 2, 1, anchor.context.getString(R.string.translator_action_hide))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    triggerTranslate(rootView, messageText, messageId, conversationJid)
                    true
                }
                2 -> {
                    TranslatorWrapperAdapter.hideTranslation(conversationJid, messageId)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun triggerTranslate(rootView: ViewGroup, messageText: String, messageId: String, conversationJid: String) {
        val prefLang = prefs.getString("translator_target_lang", "auto") ?: "auto"
        val lang = if (prefLang == "auto") Locale.getDefault().language else prefLang
        val provider = prefs.getString("translator_provider", "google") ?: "google"
        val groqKey = prefs.getString("groq_translator_api_key", "") ?: ""

        if (provider == "groq" && groqKey.isBlank()) {
            TranslatorWrapperAdapter.showGroqFallbackNotification(conversationJid, rootView)
        }

        TranslatorWrapperAdapter.startLoading(conversationJid, messageId)

        val future = if (provider == "groq" && groqKey.isNotBlank()) translateGroq(messageText, lang)
                     else translateGoogle(messageText, lang)

        future.thenAccept { translated ->
            Handler(Looper.getMainLooper()).post {
                TranslatorWrapperAdapter.clearLoading(conversationJid, messageId)
                if (translated.isNullOrBlank()) return@post
                TranslatorWrapperAdapter.showTranslation(conversationJid, messageId, translated)
            }
        }.exceptionally { err ->
            Handler(Looper.getMainLooper()).post {
                TranslatorWrapperAdapter.clearLoading(conversationJid, messageId)
                try {
                    com.google.android.material.snackbar.Snackbar.make(
                        rootView,
                        rootView.context.getString(R.string.translator_failed) + ": ${err.cause?.message ?: err.message}",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction(rootView.context.getString(R.string.translator_retry)) {
                        triggerTranslate(rootView, messageText, messageId, conversationJid)
                    }.show()
                } catch (e: Exception) {
                    Toast.makeText(rootView.context, "Gagal: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
            null
        }
    }

    fun translateGroq(text: String?, languageDest: String): CompletableFuture<String?> {
        if (client == null) client = OkHttpClient()
        val future = CompletableFuture<String?>()
        val apiKey = prefs.getString("groq_translator_api_key", "") ?: ""
        val model = prefs.getString("groq_translator_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant"

        if (apiKey.isBlank()) {
            future.completeExceptionally(RuntimeException("Groq API Key belum diisi"))
            return future
        }

        @Suppress("DEPRECATION")
        val locale = Locale(languageDest)
        val langName = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { languageDest }

        val customSystemPrompt = prefs.getString("groq_custom_system_prompt", "") ?: ""
        val customSlang = prefs.getString("groq_custom_slang", "") ?: ""

        val defaultSlang = """
Common Indonesian/Javanese slang:
- yank/yang = sayang (dear/honey, term of endearment) — BUT "yang" as relative pronoun in a sentence means "that/which/who", NOT sayang
- sampean/panjenengan = kamu/Anda (you, formal Javanese)
- awakmu/kowe = kamu (you, informal Javanese)
- wis/udah = sudah (already/done) [Javanese]
- maem = makan (eat) [Javanese/child speech]
- metu = keluar (go out) [Javanese]
- gw/gue = saya/aku (I/me) [Jakarta slang]
- lu/lo = kamu (you) [Jakarta slang]
- dong/deh/sih/nih/lah = filler particles, translate contextually or omit
- mantap/mantul = great/awesome
- gabut = bored/nothing to do
- baper = overly emotional/sensitive
- kepo = nosy/curious
- mager = malas gerak (too lazy to move)
- japri = jalur pribadi (private message)
- OTW = on the way""".trimIndent()

        val slangSection = if (customSlang.isNotBlank())
            "$defaultSlang\n\nUser-defined slang:\n$customSlang"
        else defaultSlang

        val defaultSystemPrompt = """
You are a professional translator specializing in Indonesian, Javanese, Sundanese, and Indonesian internet slang.

Rules:
1. ALWAYS translate. NEVER ask questions, NEVER request clarification, NEVER apologize.
2. If text is already in the target language ($langName), return it EXACTLY as-is without any changes.
3. If text is a proper noun, name, greeting, or single word with no translatable meaning, return it EXACTLY as-is.
4. If text contains slang, abbreviations, or regional dialect (Javanese, Sundanese, Betawi, etc.), infer meaning from context and translate.
5. Return ONLY the translated text. No explanations, no questions, no alternatives, no AI commentary.
6. NEVER produce phrases like "Maaf", "saya tidak mengerti", "I don't understand", or any meta-response. Just translate or return as-is.
7. Target language: $langName

$slangSection

Few-shot disambiguation examples:
- "yank mau kemana?" → "where are you going, dear?" (yank = endearment)
- "saya yang akan melakukan itu" → "I will be the one to do it" (yang = relative pronoun, NOT endearment)
- "dia yang terbaik" → "he/she is the best" (yang = relative pronoun)
- "coba sampean metu" → "try to go out" (sampean = kamu/you, metu = keluar)
- "mas khil" → "Mas Khil" (proper name/greeting, return as-is)
- "cek" → "cek" (already Indonesian, return as-is)
- "ok" → "ok" (universal, return as-is)
        """.trimIndent()

        val systemPrompt = if (customSystemPrompt.isNotBlank()) customSystemPrompt else defaultSystemPrompt

        val jsonBody = """
            {
                "model": "$model",
                "messages": [
                    {
                        "role": "system",
                        "content": ${org.json.JSONObject.quote(systemPrompt)}
                    },
                    {
                        "role": "user",
                        "content": ${org.json.JSONObject.quote(text ?: "")}
                    }
                ],
                "temperature": 0.3
            }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(RuntimeException("Groq request failed: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val json = org.json.JSONObject(response.body.string())
                        val result = json
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

    fun translateGoogle(text: String?, languageDest: String): CompletableFuture<String?> {
        if (client == null) {
            client = OkHttpClient()
        }
        val future = CompletableFuture<String?>()
        val url: String?
        try {
            val customEndpoint = prefs.getString("google_translate_endpoint", "") ?: ""
            val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint
                          else "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s"
            url = String.format(
                baseUrl,
                languageDest,
                URLEncoder.encode(text, "UTF-8")
            )
        } catch (e: Exception) {
            future.completeExceptionally(RuntimeException("Error encoding URL: " + e.message))
            return future
        }

        val request = Request.Builder()
            .url(url)
            .build()

        client!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(RuntimeException("Request failed: " + e.message))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body.string()
                    try {
                        val jsonArray = JSONArray(responseData)
                        val translations = jsonArray.getJSONArray(0)
                        val translation = StringBuilder()

                        for (i in 0..<translations.length()) {
                            val item = translations.getJSONArray(i)
                            translation.append(item.getString(0))
                        }

                        future.complete(translation.toString())
                    } catch (e: Exception) {
                        future.completeExceptionally(RuntimeException("Error processing response: " + e.message))
                    }
                } else {
                    future.completeExceptionally(RuntimeException("Response was not successful."))
                }
            }
        })

        return future
    }

    override fun getPluginName(): String {
        return "Google Translate"
    }
}
