package com.mrksvt.waen

import android.content.Context
import de.robv.android.xposed.XposedBridge
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A downloadable release asset. Plain data so the selection rules can be unit tested
 * without the Android JSON stubs.
 */
data class ApkAsset(val name: String, val url: String, val size: Long)

/** True for the business flavor application id (`com.mrksvt.waen.w4b`). */
fun isBusinessApplicationId(applicationId: String): Boolean =
    applicationId.endsWith(".w4b")

/**
 * Picks the APK asset that matches the installed flavor.
 *
 * Business only accepts `WhatsVault-Business` assets and whatsapp only accepts plain
 * `WhatsVault` assets, so one flavor never installs the other one. Returns null when the
 * release has no matching APK, which the UI turns into the browser fallback.
 */
fun selectApkAsset(assets: List<ApkAsset>, isBusiness: Boolean): ApkAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.url.isNotEmpty() }
    val business = apks.filter { it.name.contains(BUSINESS_MARKER, ignoreCase = true) }
    val plain = apks.filter {
        it.name.contains(PLAIN_MARKER, ignoreCase = true) &&
            !it.name.contains(BUSINESS_MARKER, ignoreCase = true)
    }
    return if (isBusiness) business.firstOrNull() else plain.firstOrNull()
}

private const val PLAIN_MARKER = "WhatsVault"
private const val BUSINESS_MARKER = "-Business"

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
        private fun extractHash(tagName: String, assets: JSONArray?): String? {
            HASH_PATTERN.find(tagName)?.let { return it.value.uppercase(Locale.US) }
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val found = HASH_PATTERN.find(asset.optString("name", ""))
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
        val isBusiness = isBusinessApplicationId(BuildConfig.APPLICATION_ID)
        val releases = ArrayList<ReleaseInfo>()
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            val tagName = release.optString("tag_name", "")
            val assets = release.optJSONArray("assets")
            val hash = extractHash(tagName, assets) ?: continue
            val apk = selectApkAsset(parseAssets(assets), isBusiness)
            releases.add(ReleaseInfo(
                tagName,
                release.optString("name", ""),
                hash,
                release.optString("body", "").trim(),
                release.optString("html_url", ""),
                release.optString("published_at", ""),
                release.optBoolean("prerelease", false),
                apk?.url ?: "",
                apk?.size ?: 0L
            ))
        }
        // Newest first; the API already returns newest-first, but be safe.
        releases.sortByDescending { it.publishedAt }
        return releases
    }

    private fun parseAssets(assets: JSONArray?): List<ApkAsset> {
        if (assets == null) return emptyList()
        val result = ArrayList<ApkAsset>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            result.add(ApkAsset(
                asset.optString("name", ""),
                asset.optString("browser_download_url", ""),
                asset.optLong("size", 0L)
            ))
        }
        return result
    }
}
