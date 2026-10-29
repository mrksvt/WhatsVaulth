package com.mrksvt.waen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the GitHub release list fetched by {@link UpdateChecker} so the
 * header notification menu and the changelog screen can read it without a
 * network call. Releases are stored newest-first.
 */
public final class UpdateInfoStore {

    public static final String FALLBACK_RELEASE_URL =
            "https://github.com/mrksvt/WhatsVaulth/releases/latest";

    private static final String PREFS = "update_info";
    private static final String KEY_RELEASES = "releases";
    private static final String KEY_UPDATE_AVAILABLE = "update_available";
    private static final String KEY_IGNORED_HASH = "ignored_hash";
    private static final String KEY_READ_TAGS = "read_tags";

    private UpdateInfoStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveReleases(Context context, List<ReleaseInfo> releases,
                                    boolean updateAvailable) {
        JSONArray array = new JSONArray();
        for (ReleaseInfo release : releases) {
            JSONObject object = new JSONObject();
            try {
                object.put("tag", release.tagName);
                object.put("title", release.title);
                object.put("hash", release.hash);
                object.put("changelog", release.changelog);
                object.put("url", release.releaseUrl);
                object.put("published", release.publishedAt);
                object.put("prerelease", release.prerelease);
                object.put("apkUrl", release.apkUrl);
                object.put("apkSize", release.apkSize);
                array.put(object);
            } catch (JSONException ignored) {
                // Every key is a plain string/boolean, so this cannot happen.
            }
        }
        prefs(context).edit()
                .putString(KEY_RELEASES, array.toString())
                .putBoolean(KEY_UPDATE_AVAILABLE, updateAvailable)
                .apply();
    }

    public static List<ReleaseInfo> getReleases(Context context) {
        String raw = prefs(context).getString(KEY_RELEASES, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        try {
            return ReleaseCacheParser.parseReleases(raw);
        } catch (RuntimeException corrupt) {
            // Corrupt cache: treat as empty and refetch next launch.
            return new ArrayList<>();
        }
    }

    /** Newest release with a hash, or null when nothing was fetched yet. */
    public static ReleaseInfo getNewest(Context context) {
        for (ReleaseInfo release : getReleases(context)) {
            if (!release.hash.isEmpty()) return release;
        }
        return null;
    }

    public static boolean hasData(Context context) {
        return !getReleases(context).isEmpty();
    }

    /** True when the newest release build differs from the installed build. */
    public static boolean hasUpdate(Context context) {
        if (!prefs(context).getBoolean(KEY_UPDATE_AVAILABLE, false)) return false;
        ReleaseInfo newest = getNewest(context);
        if (newest == null) return false;
        String ignored = value(prefs(context).getString(KEY_IGNORED_HASH, ""));
        return !newest.hash.equalsIgnoreCase(ignored);
    }

    public static void ignore(Context context, String hash) {
        prefs(context).edit()
                .putString(KEY_IGNORED_HASH, value(hash))
                .apply();
    }

    /** Release tags the user has already opened or marked as read. */
    public static Set<String> getReadTags(Context context) {
        Set<String> tags = new HashSet<>();
        String raw = prefs(context).getString(KEY_READ_TAGS, "");
        if (raw == null || raw.isEmpty()) return tags;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                tags.add(array.optString(i, ""));
            }
        } catch (JSONException ignored) {
            // Corrupt cache: treat as nothing read yet.
        }
        return tags;
    }

    public static boolean isRead(Context context, ReleaseInfo release) {
        return getReadTags(context).contains(release.tagName);
    }

    public static void markRead(Context context, String tag) {
        if (tag == null || tag.isEmpty()) return;
        Set<String> tags = getReadTags(context);
        if (tags.add(tag)) {
            writeReadTags(context, tags);
        }
    }

    public static void markAllRead(Context context) {
        Set<String> tags = getReadTags(context);
        boolean changed = false;
        for (ReleaseInfo release : getReleases(context)) {
            if (tags.add(release.tagName)) {
                changed = true;
            }
        }
        if (changed) {
            writeReadTags(context, tags);
        }
    }

    /** Unread release count shown on the header bell badge. */
    public static int unreadCount(Context context) {
        Set<String> read = getReadTags(context);
        int count = 0;
        for (ReleaseInfo release : getReleases(context)) {
            if (!read.contains(release.tagName)) count++;
        }
        return count;
    }

    public static String unreadLabel(Context context) {
        int count = unreadCount(context);
        return count > 9 ? "9+" : String.valueOf(count);
    }

    private static void writeReadTags(Context context, Set<String> tags) {
        JSONArray array = new JSONArray();
        for (String tag : tags) {
            array.put(tag);
        }
        prefs(context).edit()
                .putString(KEY_READ_TAGS, array.toString())
                .apply();
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
