package com.mrksvt.waen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Update Center rows: one release per row with hash, publish date and a state badge
 * (installed / update available / new). Uses the same row shape as the header popup.
 */
public class ReleaseListAdapter extends RecyclerView.Adapter<ReleaseListAdapter.Holder> {

    public interface OnReleaseClickListener {
        void onReleaseClick(ReleaseInfo release);
    }

    private final List<ReleaseInfo> releases;
    private final Set<String> readTags;
    private final String installedHash;
    private final String newestHash;
    private final OnReleaseClickListener listener;

    /**
     * @param installedHash hash of the build the user runs, empty when unknown
     * @param newestHash    hash of the newest release on the active channel, empty when none
     */
    public ReleaseListAdapter(List<ReleaseInfo> releases, Set<String> readTags,
                              String installedHash, String newestHash,
                              OnReleaseClickListener listener) {
        this.releases = releases;
        this.readTags = readTags;
        this.installedHash = installedHash == null ? "" : installedHash;
        this.newestHash = newestHash == null ? "" : newestHash;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_release_list, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ReleaseInfo release = releases.get(position);
        Context context = holder.itemView.getContext();
        holder.title.setText(release.displayName());
        holder.meta.setText(buildMeta(release));

        int badge = badgeFor(context, release);
        if (badge == 0) {
            holder.badge.setVisibility(View.GONE);
        } else {
            holder.badge.setText(badge);
            holder.badge.setVisibility(View.VISIBLE);
        }

        holder.dot.setVisibility(readTags.contains(release.tagName)
                ? View.INVISIBLE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> listener.onReleaseClick(release));
    }

    /** Badge text for a row, or 0 when no badge applies. */
    private int badgeFor(Context context, ReleaseInfo release) {
        if (!installedHash.isEmpty() && installedHash.equalsIgnoreCase(release.hash)) {
            return R.string.update_center_release_installed;
        }
        if (!newestHash.isEmpty() && newestHash.equalsIgnoreCase(release.hash)) {
            return R.string.update_center_release_available;
        }
        return readTags.contains(release.tagName) ? 0 : R.string.update_center_release_new;
    }

    @Override
    public int getItemCount() {
        return releases.size();
    }

    private String buildMeta(ReleaseInfo release) {
        String date = formatDate(release.publishedAt);
        return date.isEmpty() ? release.hash : release.hash + "  \u00B7  " + date;
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = iso.parse(isoDate);
            if (date == null) return "";
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date);
        } catch (ParseException e) {
            return "";
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView badge;
        final View dot;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.release_title);
            meta = itemView.findViewById(R.id.release_meta);
            badge = itemView.findViewById(R.id.release_badge);
            dot = itemView.findViewById(R.id.release_unread_dot);
        }
    }
}
