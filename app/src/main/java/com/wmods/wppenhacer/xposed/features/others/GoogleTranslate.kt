package com.wmods.wppenhacer.xposed.features.others

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCheckSupportLanguage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
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

class GoogleTranslate(classLoader: ClassLoader, preferences:SharedPreferences) :
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
                    val list = param.args[0] as MutableList<*>
                    val translated = ArrayList<String?>()
                    for (text in list) {
                        val translation = if (provider == "groq") {
                            translateGroq(text as String?, Locale.getDefault().language).get()
                        } else {
                            translateGoogle(text as String?, Locale.getDefault().language).get()
                        }
                        translated.add(translation)
                    }
                    return unityTranslationResultClass.getConstructor(
                        Array<String>::class.java,
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).newInstance(translated.toTypedArray<String?>(), 1, 0)
                }
            }
        })
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
                future.completeExceptionally(RuntimeException("Error translating text: " + e.message))
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
