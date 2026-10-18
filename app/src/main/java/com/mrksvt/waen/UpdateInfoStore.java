package com.mrksvt.waen;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the latest GitHub release info fetched by {@link UpdateChecker} so the
 * header notification and the changelog screen can read it without a network call.
 */
public final class UpdateInfoStore {

    public static final String FALLBACK_RELEASE_URL =
            "https://github.com/mrksvt/WhatsVaulth/releases/latest";

    private static final String PREFS = "update_info";
    private static final String KEY_HASH = "latest_hash";
    private static final String KEY_CHANGELOG = "latest_changelog";
    private static final String KEY_RELEASE_URL = "latest_release_url";
    private static final String KEY_PUBLISHED_AT = "latest_published_at";
    private static final String KEY_UPDATE_AVAILABLE = "update_available";
    private static final String KEY_IGNORED_HASH = "ignored_hash";

    private UpdateInfoStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String hash, String changelog,
                            String releaseUrl, String publishedAt, boolean updateAvailable) {
        prefs(context).edit()
                .putString(KEY_HASH, hash)
                .putString(KEY_CHANGELOG, changelog)
                .putString(KEY_RELEASE_URL, releaseUrl)
                .putString(KEY_PUBLISHED_AT, publishedAt)
                .putBoolean(KEY_UPDATE_AVAILABLE, updateAvailable)
                .apply();
    }

    public static String getHash(Context context) {
        return value(prefs(context).getString(KEY_HASH, ""));
    }

    public static String getChangelog(Context context) {
        return value(prefs(context).getString(KEY_CHANGELOG, ""));
    }

    public static String getPublishedAt(Context context) {
        return value(prefs(context).getString(KEY_PUBLISHED_AT, ""));
    }

    public static String getReleaseUrl(Context context) {
        String url = value(prefs(context).getString(KEY_RELEASE_URL, ""));
        return url.isEmpty() ? FALLBACK_RELEASE_URL : url;
    }

    public static boolean hasData(Context context) {
        return !getHash(context).isEmpty();
    }

    /** True only when a newer release exists and the user has not ignored that hash. */
    public static boolean hasUpdate(Context context) {
        if (!prefs(context).getBoolean(KEY_UPDATE_AVAILABLE, false)) return false;
        String hash = getHash(context);
        if (hash.isEmpty()) return false;
        String ignored = value(prefs(context).getString(KEY_IGNORED_HASH, ""));
        return !hash.equalsIgnoreCase(ignored);
    }

    public static void ignore(Context context, String hash) {
        prefs(context).edit()
                .putString(KEY_IGNORED_HASH, value(hash))
                .apply();
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
