package com.mrksvt.waen;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds the second line of a release row: hash, publish date and the update track.
 *
 * <p>Shared by the changelog list and the header popup so a row looks the same
 * wherever it appears. The track marker matters once stable and prerelease builds are
 * listed together, which happens as soon as the user follows both tracks.
 *
 * <p>Pure Java with no {@code android.*} imports so the formatting can be unit tested
 * on the JVM.
 */
public final class ReleaseMetaFormatter {

    private static final String SEPARATOR = "  \u00B7  ";

    private ReleaseMetaFormatter() {
    }

    /**
     * @param hash            build hash, may be empty
     * @param isoDate         GitHub publish timestamp, may be null/blank/malformed
     * @param prerelease      true when the row belongs to the prerelease track
     * @param prereleaseLabel localized track label, ignored when {@code prerelease} is false
     * @return the meta line, never null
     */
    public static String format(String hash, String isoDate, boolean prerelease,
                                String prereleaseLabel) {
        StringBuilder meta = new StringBuilder(hash == null ? "" : hash.trim());
        String date = formatDate(isoDate);
        if (!date.isEmpty()) {
            if (meta.length() > 0) meta.append(SEPARATOR);
            meta.append(date);
        }
        if (prerelease && prereleaseLabel != null && !prereleaseLabel.trim().isEmpty()) {
            if (meta.length() > 0) meta.append(SEPARATOR);
            meta.append(prereleaseLabel.trim());
        }
        return meta.toString();
    }

    /** {@code 2026-11-08T03:47:07Z} to {@code Nov 08, 2026}; empty when unparseable. */
    public static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = iso.parse(isoDate.trim());
            if (date == null) return "";
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date);
        } catch (ParseException malformed) {
            return "";
        }
    }
}
