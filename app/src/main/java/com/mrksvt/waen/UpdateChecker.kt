package com.mrksvt.waen

import android.content.Context
import android.util.Log
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
 * Releases that belong to [channel], order preserved (the cache is newest-first).
 *
 * Pure so the channel rules stay unit testable on the JVM.
 */
fun selectChannelReleases(releases: List<ReleaseInfo>, channel: UpdateChannel): List<ReleaseInfo> =
    channel.filter(releases)

/** Releases accepted by any of [channels], order preserved. */
fun selectFollowedReleases(releases: List<ReleaseInfo>,
                           channels: Collection<UpdateChannel>): List<ReleaseInfo> =
    UpdateChannel.filterAny(channels, releases)

/**
 * Update candidate per channel: the newest release on that channel whose build hash
 * differs from the installed build. Channels already up to date are absent, so an
 * empty map means "nothing to announce on any channel".
 *
 * Per-channel so following both tracks can announce a beta without ever announcing
 * the stable build the user already runs.
 */
fun channelUpdates(releases: List<ReleaseInfo>, channels: Collection<UpdateChannel>,
                   installedVersionName: String): Map<UpdateChannel, ReleaseInfo> {
    val result = LinkedHashMap<UpdateChannel, ReleaseInfo>()
    for (channel in channels) {
        val head = selectChannelReleases(releases, channel)
            .firstOrNull { it.hash.isNotEmpty() } ?: continue
        if (isUpdateForChannel(installedVersionName, head)) result[channel] = head
    }
    return result
}

/**
 * Numeric version triple of [raw], e.g. `v1.6.4-beta` or `1.6.4 (26CB54F0)_mrksvt`
 * both give `1.6.4`.
 *
 * @return the triple, or null when [raw] carries no version at all
 */
private fun versionTriple(raw: String): IntArray? {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(raw) ?: return null
    val (major, minor, patch) = match.destructured
    return intArrayOf(major.toInt(), minor.toInt(), patch.toInt())
}

/**
 * True when [candidate] is a build the user does not run yet.
 *
 * Two checks, because the git hash alone is not enough:
 *
 * - Hash match means the installed build was cut from the same commit, so no update.
 * - Version comparison keeps a newer installed build from being offered an older
 *   release. The installed build `1.6.4` must not see `v1.6.3` as an update, even
 *   though the hashes differ.
 *
 * The git hash is the build identity: the installed versionName carries the hash of
 * the build it was cut from (see `applicationVariants` in build.gradle.kts).
 */
fun isUpdateForChannel(installedVersionName: String, candidate: ReleaseInfo): Boolean {
    if (candidate.hash.isEmpty()) return false
    if (installedVersionName.lowercase().contains(candidate.hash.lowercase())) return false

    val installed = versionTriple(installedVersionName)
    val offered = versionTriple(candidate.tagName)
    if (installed != null && offered != null) {
        for (index in 0 until 3) {
            if (installed[index] != offered[index]) {
                // A strictly older release is a downgrade, not an update.
                return installed[index] < offered[index]
            }
        }
    }
    return true
}

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

        private const val TAG = "UpdateChecker"
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
                if (!response.isSuccessful) {
                    Log.w(TAG, "github responded ${response.code}")
                    return
                }
                val array = JSONArray(response.body.string())
                buildReleases(array)
            }

            if (releases.isEmpty()) {
                Log.w(TAG, "no usable release in api response")
                return
            }

            val versionName = try {
                context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                    .versionName ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "versionName lookup failed", e)
                return
            }

            // Every channel gets its own candidate, even the ones the user does not
            // follow right now: switching a channel on later must find the candidate
            // already known instead of waiting for the next check. The followed set
            // only gates what is announced and listed.
            val updates = channelUpdates(releases, UpdateChannel.allChannels(), versionName)
            UpdateInfoStore.saveReleases(context, releases, updates)

            // The callback reports an update the user actually cares about, so it
            // counts only the followed tracks.
            val followed = UpdateInfoStore.getFollowedChannels(context)
            updateAvailable = followed.any { updates.containsKey(it) }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
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
