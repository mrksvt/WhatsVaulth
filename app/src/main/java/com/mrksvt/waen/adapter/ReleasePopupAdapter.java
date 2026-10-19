package com.mrksvt.waen.adapter;

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
 * Rows for the header notification popup: one release per row with a build
 * hash, publish date and an unread dot.
 */
public class ReleasePopupAdapter extends RecyclerView.Adapter<ReleasePopupAdapter.Holder> {

    public interface OnReleaseClickListener {
        void onReleaseClick(ReleaseInfo release);
    }

    private final List<ReleaseInfo> releases;
    private final Set<String> readTags;
    private final OnReleaseClickListener listener;

    public ReleasePopupAdapter(List<ReleaseInfo> releases, Set<String> readTags,
                               OnReleaseClickListener listener) {
        this.releases = releases;
        this.readTags = readTags;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_release_popup, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ReleaseInfo release = releases.get(position);
        holder.title.setText(release.displayName());
        holder.meta.setText(buildMeta(release));
        holder.dot.setVisibility(readTags.contains(release.tagName)
                ? View.INVISIBLE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> listener.onReleaseClick(release));
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
        final View dot;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.release_title);
            meta = itemView.findViewById(R.id.release_meta);
            dot = itemView.findViewById(R.id.release_unread_dot);
        }
    }
}
