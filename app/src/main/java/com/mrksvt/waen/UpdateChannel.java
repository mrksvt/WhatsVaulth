package com.mrksvt.waen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which GitHub release track the update centre follows.
 *
 * <p>Each value is an independent track filter: {@link #RELEASE} keeps stable builds,
 * {@link #PRERELEASE} keeps prerelease builds. A user may follow one track or both,
 * so the union of the followed tracks decides what the update centre shows.
 *
 * <p>The {@code key} strings are the stable tokens persisted in prefs, so they must
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
     * True when {@code release} belongs to this track.
     *
     * <p>Tracks never overlap: a release is either stable or prerelease, so following
     * both tracks yields every release exactly once. Legacy cache entries written
     * before the prerelease flag existed default to {@code false} and therefore stay
     * on {@link #RELEASE}.
     */
    public boolean accepts(ReleaseInfo release) {
        if (release == null) return false;
        return this == PRERELEASE ? release.prerelease : !release.prerelease;
    }

    /**
     * Filters a stored release list down to this channel, preserving order
     * (the cache is newest-first).
     *
     * @return a new list; never null
     */
    public List<ReleaseInfo> filter(List<ReleaseInfo> releases) {
        return filterAny(EnumSet.of(this), releases);
    }

    /**
     * Filters a stored release list to every release accepted by at least one of
     * {@code channels}, preserving order. An empty set yields an empty list.
     *
     * @return a new list; never null
     */
    public static List<ReleaseInfo> filterAny(Collection<UpdateChannel> channels,
                                              List<ReleaseInfo> releases) {
        List<ReleaseInfo> result = new ArrayList<>();
        if (releases == null || channels == null) return result;
        for (ReleaseInfo release : releases) {
            for (UpdateChannel channel : channels) {
                if (channel.accepts(release)) {
                    result.add(release);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Parses the persisted channel set, a comma-separated list of keys such as
     * {@code "release,prerelease"}. Unknown tokens are ignored; a blank or fully
     * unknown value yields {@link #defaultSet()}.
     */
    public static Set<UpdateChannel> setFromKeys(String raw) {
        Set<UpdateChannel> channels = EnumSet.noneOf(UpdateChannel.class);
        if (raw != null) {
            for (String token : raw.split(",")) {
                String trimmed = token.trim();
                for (UpdateChannel channel : values()) {
                    if (channel.key.equalsIgnoreCase(trimmed)) channels.add(channel);
                }
            }
        }
        return channels.isEmpty() ? defaultSet() : channels;
    }

    /** Serializes a channel set to its persisted form, in declaration order. */
    public static String setToKeys(Collection<UpdateChannel> channels) {
        StringBuilder out = new StringBuilder();
        for (UpdateChannel channel : values()) {
            if (channels == null || !channels.contains(channel)) continue;
            if (out.length() > 0) out.append(',');
            out.append(channel.key);
        }
        return out.toString();
    }

    /** Channel set used when nothing is stored: stable builds only. */
    public static Set<UpdateChannel> defaultSet() {
        return EnumSet.of(defaultChannel());
    }

    /**
     * Every track, in declaration order.
     *
     * <p>Used by the update check: the candidate of each track is computed up front so
     * following a track later on finds it without waiting for another network round.
     */
    public static Set<UpdateChannel> allChannels() {
        return EnumSet.allOf(UpdateChannel.class);
    }
}
