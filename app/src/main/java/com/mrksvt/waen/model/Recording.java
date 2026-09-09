package com.mrksvt.waen.model;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaMetadataRetriever;

import com.mrksvt.waen.utils.ContactHelper;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * Model class representing a call recording with metadata.
 *
 * Nama kontak di-derive dari nama file dengan regex longgar, lalu
 * di-resolve ke kontak perangkat via ContactsContract (kalau identifier
 * adalah nomor telepon / JID).
 */
@Getter
public class Recording {

    private final File file;
    @Setter private String contactName;
    private long duration;
    private final long date;
    private final long size;

    // Nama file: Call_{identifier}_{yyyyMMdd}_{HHmmss}.(wav|m4a)
    // Identifier bisa apa saja setelah sanitasi (nama, nomor, LID, "Unknown").
    // Regex longgar: match sampai underscore terakhir sebelum timestamp.
    private static final Pattern FILE_PATTERN =
            Pattern.compile("(?i)Call_(.+?)_(\\d{8}_\\d{6})\\.(wav|m4a)");

    // Regex khusus pola tanpa identifier: Call_{timestamp}.{ext}
    private static final Pattern NO_IDENTIFIER_PATTERN =
            Pattern.compile("(?i)Call_(\\d{8}_\\d{6})\\.(wav|m4a)");

    public Recording(File file) {
        this.file = file;
        this.date = file.lastModified();
        this.size = file.length();
        this.contactName = extractContactName();
        parseDuration();
    }

    private String extractContactName() {
        String filename = file.getName();

        // Pola normal: identifier ada
        Matcher matcher = FILE_PATTERN.matcher(filename);
        if (matcher.matches() && matcher.groupCount() >= 1) {
            String extracted = matcher.group(1);
            if (extracted != null && !extracted.isEmpty()) {
                // Restore _ -> spasi untuk tampilan, kecuali kalau terlalu
                // banyak underscore (kemungkinan benar-benar bagian nama file)
                String display = extracted.replace("_", " ");
                return display;
            }
        }

        // Pola tanpa identifier: Call_{timestamp}.{ext}
        if (NO_IDENTIFIER_PATTERN.matcher(filename).matches()) {
            return "Unknown";
        }

        // Bukan pola recording sama sekali — tampilkan nama file apa adanya
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
    }

    public void resolveContactName(Context context) {
        if (contactName == null || contactName.equals("Unknown")) return;

        String identifier = contactName;

        // Deteksi JID / nomor telepon (digits-only, atau ends with @s.whatsapp.net / @lid)
        boolean looksLikeJid = identifier.contains("@")
                || (identifier.matches("\\+?\\d{5,15}"));  // E.164-ish

        if (!looksLikeJid) return;  // Sudah nama asli, skip lookup

        String resolved = ContactHelper.getContactName(context, identifier);
        if (resolved != null && !resolved.isEmpty()) {
            contactName = resolved;
        }
    }

    private void parseDuration() {
        if (!file.exists() || file.length() == 0) {
            duration = 0;
            return;
        }

        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (timeStr != null && !timeStr.isEmpty()) {
                duration = Long.parseLong(timeStr);
            } else {
                duration = 0;
            }
        } catch (Exception e) {
            duration = 0;
        }
    }

    @SuppressLint("DefaultLocale")
    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    @SuppressLint("DefaultLocale")
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recording recording = (Recording) o;
        return file.equals(recording.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}
