package com.mrksvt.waen;

import java.util.ArrayList;
import java.util.List;

/**
 * Which GitHub release track the update centre follows.
 *
 * <p>{@link #RELEASE} keeps the pre-existing behaviour (stable builds only).
 * {@link #PRERELEASE} additionally accepts every prerelease the API returns.
 * The {@code key} strings are the stable tokens persisted in prefs, so they must
 * never be renamed.
 *
 * <p>Pure Java with no {@code android.*} imports so the parsing and filtering rules
 * can be unit tested on the JVM.
 */
public enum UpdateChannel {

    RELEASE("release"),
    PRERELEASE("prerelease");

    private final String key;

    UpdateChannel(String key) {
        this.key = key;
    }

    /** Stable prefs token for this channel. */
    public String key() {
        return key;
    }

    /** Channel used when nothing is stored yet, or when the stored value is corrupt. */
    public static UpdateChannel defaultChannel() {
        return RELEASE;
    }

    /**
     * Parses a stored token.
     *
     * @param raw     persisted value, may be null/blank/garbage
     * @param fallback value returned when {@code raw} matches no channel
     */
    public static UpdateChannel fromKey(String raw, UpdateChannel fallback) {
        if (raw != null) {
            String trimmed = raw.trim();
            for (UpdateChannel channel : values()) {
                if (channel.key.equalsIgnoreCase(trimmed)) return channel;
            }
        }
        return fallback == null ? defaultChannel() : fallback;
    }

    /** Same as {@link #fromKey(String, UpdateChannel)} with the default fallback. */
    public static UpdateChannel fromKey(String raw) {
        return fromKey(raw, defaultChannel());
    }

    /**
     * True when {@code release} belongs to this channel.
     *
     * <p>A release counts as stable unless the API flagged it as prerelease, so
     * legacy cache entries written before the flag existed stay visible on
     * {@link #RELEASE}.
     */
    public boolean accepts(ReleaseInfo release) {
        if (release == null) return false;
        return this == PRERELEASE || !release.prerelease;
    }

    /**
     * Filters a stored release list down to this channel, preserving order
     * (the cache is newest-first).
     *
     * @return a new list; never null
     */
    public List<ReleaseInfo> filter(List<ReleaseInfo> releases) {
        List<ReleaseInfo> result = new ArrayList<>();
        if (releases == null) return result;
        for (ReleaseInfo release : releases) {
            if (accepts(release)) result.add(release);
        }
        return result;
    }
}
