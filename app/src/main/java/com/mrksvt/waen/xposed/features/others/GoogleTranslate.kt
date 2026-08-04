package com.mrksvt.waen.xposed.features.others

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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

    companion object {
        private const val TAG_TRANSLATION_VIEW = "wae_translation_bubble"
        private const val FIELD_BUBBLE_REF = "wae_translate_bubble_ref"
    }

    override fun doHook() {
        if (!prefs.getBoolean("google_translate", false)) return

        val checkSupportLanguage = loadCheckSupportLanguage(classLoader)

        XposedBridge.hookMethod(checkSupportLanguage, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = "pt"
                param.args[1] = "en"
            }
        })

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
                if (currentMethod.parameterTypes[0] == String::class.java) {
                    val text = param.args[0] as String?
                    val translation = if (provider == "groq") {
                        translateGroq(text, Locale.getDefault().language).get()
                    } else {
                        translateGoogle(text, Locale.getDefault().language).get()
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
                        if (provider == "groq") {
                            translateGroq(text2, Locale.getDefault().language).get()
                        } else {
                            translateGoogle(text2, Locale.getDefault().language).get()
                        }
                    }
                    return unityTranslationResultClass.getConstructor(
                        Array<String?>::class.java,
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).newInstance(translations.toTypedArray<String?>(), 1, 0)
                }
            }
        })

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

                val previousId = XposedHelpers.getAdditionalInstanceField(view, "wae_bound_id") as? String
                if (previousId != messageId) {
                    removeTranslationBubble(view)
                    XposedHelpers.setAdditionalInstanceField(view, "wae_bound_id", messageId)
                }

                val messageTextView = view.findViewById<TextView>(Utils.getID("message_text", "id"))
                val anchor = messageTextView ?: view

                view.setOnClickListener {
                    if (!ConversationItemListener.isViewBoundToMessage(view, messageId)) return@setOnClickListener

                    val popup = PopupMenu(anchor.context, anchor)
                    popup.gravity = if (isFromMe) android.view.Gravity.END else android.view.Gravity.START
                    popup.menu.add(0, 1, 0, "Terjemahkan")

                    val existingBubble = XposedHelpers.getAdditionalInstanceField(view, FIELD_BUBBLE_REF) as? TextView
                    if (existingBubble != null) {
                        popup.menu.add(0, 2, 1, "Sembunyikan terjemahan")
                    }

                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                triggerTranslate(view, messageText, messageId)
                                true
                            }
                            2 -> {
                                removeTranslationBubble(view)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        })
    }

    private fun triggerTranslate(rootView: ViewGroup, messageText: String, messageId: String) {
        val existing = XposedHelpers.getAdditionalInstanceField(rootView, FIELD_BUBBLE_REF) as? TextView
        if (existing != null) {
            existing.visibility = if (existing.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return
        }

        val messageTextView = rootView.findViewById<TextView>(Utils.getID("message_text", "id")) ?: return

        val sb = StringBuilder("WAE hierarchy from message_text:\n")
        var cur: android.view.View? = messageTextView
        for (i in 0..6) {
            sb.append("  [$i] ${cur?.javaClass?.simpleName} id=${cur?.id}\n")
            cur = (cur?.parent as? android.view.View)
        }
        XposedBridge.log(sb.toString())

        val container = (messageTextView.parent as? ViewGroup)?.parent as? ViewGroup ?: return

        val bubble = createTranslationBubble(container)
        container.addView(bubble)
        XposedHelpers.setAdditionalInstanceField(rootView, FIELD_BUBBLE_REF, bubble)

        val lang = Locale.getDefault().language
        val provider = prefs.getString("translator_provider", "google") ?: "google"
        val future = if (provider == "groq") translateGroq(messageText, lang)
                     else translateGoogle(messageText, lang)

        future.thenAccept { translated ->
            Handler(Looper.getMainLooper()).post {
                if (!ConversationItemListener.isViewBoundToMessage(rootView, messageId)) {
                    container.removeView(bubble)
                    XposedHelpers.removeAdditionalInstanceField(rootView, FIELD_BUBBLE_REF)
                    return@post
                }
                if (translated.isNullOrBlank()) {
                    container.removeView(bubble)
                    XposedHelpers.removeAdditionalInstanceField(rootView, FIELD_BUBBLE_REF)
                } else {
                    bubble.text = translated
                }
            }
        }.exceptionally { err ->
            Handler(Looper.getMainLooper()).post {
                container.removeView(bubble)
                XposedHelpers.removeAdditionalInstanceField(rootView, FIELD_BUBBLE_REF)
                Toast.makeText(rootView.context, "Gagal: ${err.message}", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private fun createTranslationBubble(container: ViewGroup): TextView {
        val context = container.context
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)

        val lp: ViewGroup.LayoutParams = when (container) {
            is LinearLayout -> LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp4 }
            is android.widget.RelativeLayout -> android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp4 }
            else -> android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp4 }
        }

        return TextView(context).apply {
            tag = TAG_TRANSLATION_VIEW
            text = "Menerjemahkan..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#E3F2FD"))
            setBackgroundColor(Color.parseColor("#1565C0"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp8, dp4, dp8, dp4)
            layoutParams = lp
        }
    }

    private fun removeTranslationBubble(rootView: ViewGroup) {
        removeBubbleRecursive(rootView)
        XposedHelpers.removeAdditionalInstanceField(rootView, FIELD_BUBBLE_REF)
    }

    private fun removeBubbleRecursive(vg: ViewGroup) {
        for (i in vg.childCount - 1 downTo 0) {
            val child = vg.getChildAt(i)
            if (child.tag == TAG_TRANSLATION_VIEW) {
                vg.removeViewAt(i)
            } else if (child is ViewGroup) {
                removeBubbleRecursive(child)
            }
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

        val locale = Locale(languageDest)
        val langName = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { languageDest }

        val jsonBody = """
            {
                "model": "$model",
                "messages": [
                    {
                        "role": "system",
                        "content": "You are a translator. Translate the user's text to $langName. Return ONLY the translated text, no explanation."
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
            url = String.format(
                "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s",
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
