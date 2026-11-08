package com.mrksvt.waen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists the GitHub release list fetched by {@link UpdateChecker} so the
 * header notification menu and the changelog screen can read it without a
 * network call. Releases are stored newest-first.
 *
 * <p>The user follows one or more {@link UpdateChannel} tracks. Everything the UI
 * and the notifier show is the union of the followed tracks, and the update
 * candidate is tracked per track so a release never hides a prerelease alert.
 */
public final class UpdateInfoStore {

    public static final String FALLBACK_RELEASE_URL =
            "https://github.com/mrksvt/WhatsVaulth/releases/latest";

    private static final String PREFS = "update_info";
    private static final String KEY_RELEASES = "releases";
    private static final String KEY_IGNORED_HASH = "ignored_hash";
    private static final String KEY_READ_TAGS = "read_tags";
    /** Followed channel set, comma-separated keys. */
    private static final String KEY_FOLLOWED_CHANNELS = "followed_channels";
    /** Update candidate hash per channel key. */
    private static final String KEY_CHANNEL_UPDATES = "channel_updates";
    /** Legacy single-channel key, read once for migration. */
    private static final String KEY_LEGACY_CHANNEL = "channel";

    private UpdateInfoStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Persists the release list plus the update candidate of every channel, so the
     * badge and the notifier never have to re-derive it from the installed build.
     *
     * @param channelUpdates channel to candidate release; a channel without an update
     *                       is simply absent
     */
    public static void saveReleases(Context context, List<ReleaseInfo> releases,
                                    Map<UpdateChannel, ReleaseInfo> channelUpdates) {
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

        JSONObject updates = new JSONObject();
        for (UpdateChannel channel : UpdateChannel.values()) {
            ReleaseInfo candidate = channelUpdates == null ? null : channelUpdates.get(channel);
            try {
                updates.put(channel.key(), candidate == null ? "" : candidate.hash);
            } catch (JSONException ignored) {
                // Plain string value, cannot fail.
            }
        }

        // commit() instead of apply(): a lost 28 KB cache write leaves the whole
        // update centre empty until the next launch, so report the failure instead
        // of swallowing it. This runs off the main thread (UpdateChecker).
        boolean saved = prefs(context).edit()
                .putString(KEY_RELEASES, array.toString())
                .putString(KEY_CHANNEL_UPDATES, updates.toString())
                .commit();
        if (!saved) {
            android.util.Log.w("UpdateInfoStore", "failed to persist release cache");
        }
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

    /**
     * Tracks the user follows. Defaults to stable releases only and migrates the
     * legacy single-channel pref, where {@code prerelease} meant "stable + beta"
     * and therefore maps to both tracks.
     */
    public static Set<UpdateChannel> getFollowedChannels(Context context) {
        String raw = prefs(context).getString(KEY_FOLLOWED_CHANNELS, "");
        if (raw != null && !raw.trim().isEmpty()) {
            return UpdateChannel.setFromKeys(raw);
        }
        String legacy = value(prefs(context).getString(KEY_LEGACY_CHANNEL, ""));
        if (UpdateChannel.PRERELEASE.key().equalsIgnoreCase(legacy)) {
            return EnumSet.of(UpdateChannel.RELEASE, UpdateChannel.PRERELEASE);
        }
        return UpdateChannel.defaultSet();
    }

    /** Replaces the followed tracks. An empty set falls back to the default. */
    public static void setFollowedChannels(Context context, Collection<UpdateChannel> channels) {
        Set<UpdateChannel> next = channels == null || channels.isEmpty()
                ? UpdateChannel.defaultSet() : EnumSet.copyOf(channels);
        prefs(context).edit()
                .putString(KEY_FOLLOWED_CHANNELS, UpdateChannel.setToKeys(next))
                .remove(KEY_LEGACY_CHANNEL)
                .apply();
    }

    /** Stored releases accepted by the followed tracks, newest-first. */
    public static List<ReleaseInfo> getFollowedReleases(Context context) {
        return UpdateChannel.filterAny(getFollowedChannels(context), getReleases(context));
    }

    /** Stored releases accepted by {@code channels}, newest-first. */
    public static List<ReleaseInfo> getReleasesFor(Context context,
                                                   Collection<UpdateChannel> channels) {
        return UpdateChannel.filterAny(channels, getReleases(context));
    }

    /** Newest release with a hash among {@code channels}, or null when nothing matches. */
    public static ReleaseInfo getNewestFor(Context context, Collection<UpdateChannel> channels) {
        for (ReleaseInfo release : getReleasesFor(context, channels)) {
            if (!release.hash.isEmpty()) return release;
        }
        return null;
    }

    /**
     * Release the last check found as an update on {@code channel}, or null when the
     * installed build already is the channel head, the check has not run yet, or the
     * user ignored exactly that build.
     */
    public static ReleaseInfo getChannelUpdate(Context context, UpdateChannel channel) {
        String hash = value(channelUpdateHashes(context).optString(channel.key(), ""));
        if (hash.isEmpty()) return null;
        if (hash.equalsIgnoreCase(value(prefs(context).getString(KEY_IGNORED_HASH, "")))) {
            return null;
        }
        for (ReleaseInfo release : getReleases(context)) {
            if (release.hash.equalsIgnoreCase(hash)) return release;
        }
        return null;
    }

    /** Unread count across the followed tracks, for the header bell badge. */
    public static int unreadCount(Context context, Collection<UpdateChannel> channels) {
        Set<String> read = getReadTags(context);
        int count = 0;
        for (ReleaseInfo release : getReleasesFor(context, channels)) {
            if (!read.contains(release.tagName)) count++;
        }
        return count;
    }

    public static String unreadLabel(Context context, Collection<UpdateChannel> channels) {
        int count = unreadCount(context, channels);
        return count > 9 ? "9+" : String.valueOf(count);
    }

    public static boolean hasData(Context context) {
        return !getReleases(context).isEmpty();
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

    private static JSONObject channelUpdateHashes(Context context) {
        String raw = prefs(context).getString(KEY_CHANNEL_UPDATES, "");
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (JSONException corrupt) {
            return new JSONObject();
        }
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
