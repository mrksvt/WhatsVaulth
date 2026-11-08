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
import com.mrksvt.waen.ReleaseMetaFormatter;

import java.util.List;
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
        holder.meta.setText(buildMeta(context, release));

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

    /**
     * Hash, date and the track name. The track marker matters once stable and beta
     * releases are listed together, so the row is built by {@link ReleaseMetaFormatter}
     * and looks the same here as in the header popup.
     */
    private String buildMeta(Context context, ReleaseInfo release) {
        return ReleaseMetaFormatter.format(release.hash, release.publishedAt,
                release.prerelease,
                context.getString(R.string.update_center_release_prerelease));
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
