package com.mrksvt.waen;

/**
 * A single GitHub release as shown in the notification menu and changelog screen.
 * Immutable value object parsed from the GitHub releases API.
 */
public final class ReleaseInfo {

    public final String tagName;
    public final String title;
    public final String hash;
    public final String changelog;
    public final String releaseUrl;
    public final String publishedAt;
    public final boolean prerelease;

    public ReleaseInfo(String tagName, String title, String hash, String changelog,
                       String releaseUrl, String publishedAt, boolean prerelease) {
        this.tagName = trim(tagName);
        this.title = trim(title);
        this.hash = trim(hash);
        this.changelog = trim(changelog);
        this.releaseUrl = trim(releaseUrl);
        this.publishedAt = trim(publishedAt);
        this.prerelease = prerelease;
    }

    /** Human label for a menu row / changelog heading. */
    public String displayName() {
        if (!title.isEmpty()) return title;
        if (!tagName.isEmpty()) return tagName;
        return hash;
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
