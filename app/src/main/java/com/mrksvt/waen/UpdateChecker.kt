package com.mrksvt.waen

import android.content.Context
import de.robv.android.xposed.XposedBridge
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fetches the GitHub release list, persists it through [UpdateInfoStore], and
 * reports whether a newer build is available. Runs off the main thread.
 */
class UpdateChecker(
    private val context: Context,
    private val onFinished: java.util.function.Consumer<Boolean>? = null
) : Runnable {

    companion object {
        private const val RELEASES_API =
            "https://api.github.com/repos/mrksvt/WhatsVaulth/releases?per_page=20"

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
                .url(RELEASES_API)
                .build()

            val releases = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val array = JSONArray(response.body.string())
                buildReleases(array)
            }

            if (releases.isEmpty()) return

            val versionName = try {
                context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                    .versionName ?: ""
            } catch (e: Exception) {
                XposedBridge.log(e)
                return
            }

            val newest = releases.firstOrNull { it.hash.isNotEmpty() }
            if (newest == null) {
                UpdateInfoStore.saveReleases(context, releases, false)
                return
            }
            updateAvailable = !versionName.lowercase().contains(newest.hash.lowercase())
            UpdateInfoStore.saveReleases(context, releases, updateAvailable)
        } catch (e: Exception) {
            XposedBridge.log(e)
        } finally {
            onFinished?.accept(updateAvailable)
        }
    }

    private fun buildReleases(array: JSONArray): List<ReleaseInfo> {
        val releases = ArrayList<ReleaseInfo>()
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            val tagName = release.optString("tag_name", "")
            val hash = extractHash(tagName, release.optJSONArray("assets"))
                ?: continue
            releases.add(ReleaseInfo(
                tagName,
                release.optString("name", ""),
                hash,
                release.optString("body", "").trim(),
                release.optString("html_url", ""),
                release.optString("published_at", ""),
                release.optBoolean("prerelease", false)
            ))
        }
        // Newest first; the API already returns newest-first, but be safe.
        releases.sortByDescending { it.publishedAt }
        return releases
    }
}
