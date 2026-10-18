package com.mrksvt.waen

import android.content.Context
import de.robv.android.xposed.XposedBridge
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest GitHub release, persists it through [UpdateInfoStore], and
 * reports whether a newer build is available. Runs off the main thread.
 */
class UpdateChecker(
    private val context: Context,
    private val onFinished: java.util.function.Consumer<Boolean>? = null
) : Runnable {

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/mrksvt/WhatsVaulth/releases/latest"

        private val HASH_PATTERN = Regex("[0-9a-fA-F]{8}")

        /**
         * The git hash identifies a build. It lives in the CI tag (1.5.5-A94A2CE3) but
         * manual release tags (v1.5.5-r40) carry it only in the asset filenames
         * (WhatsVault-1.5.5-debug.A94A2CE3._mrksvt.apk), so check both.
         */
        private fun extractHash(tagName: String, assetNames: JSONArray?): String? {
            HASH_PATTERN.find(tagName)?.let { return it.value.uppercase(Locale.US) }
            if (assetNames != null) {
                for (i in 0 until assetNames.length()) {
                    val found = HASH_PATTERN.find(assetNames.optString(i, ""))
                    if (found != null) return found.value.uppercase(Locale.US)
                }
            }
            return null
        }

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    override fun run() {
        var updateAvailable = false
        try {
            val request = okhttp3.Request.Builder()
                .url(LATEST_RELEASE_API)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return

                val content = response.body.string()
                val release = JSONObject(content)
                val tagName = release.optString("tag_name", "")

                if (tagName.isBlank()) return

                val hash = extractHash(tagName, release.optJSONArray("assets")) ?: return
                val changelog = release.optString("body", "").trim()
                val releaseUrl = release.optString("html_url", "")
                val publishedAt = release.optString("published_at", "")

                val versionName = try {
                    context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                        .versionName ?: ""
                } catch (e: Exception) {
                    XposedBridge.log(e)
                    return
                }

                updateAvailable = !versionName.lowercase().contains(hash.lowercase().trim())
                UpdateInfoStore.save(
                    context, hash, changelog, releaseUrl, publishedAt, updateAvailable
                )
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        } finally {
            onFinished?.accept(updateAvailable)
        }
    }
}
