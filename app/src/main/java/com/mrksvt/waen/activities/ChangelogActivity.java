package com.mrksvt.waen.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;
import com.mrksvt.waen.UpdateInfoStore;
import com.mrksvt.waen.activities.base.BaseActivity;
import com.mrksvt.waen.databinding.ActivityChangelogBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.noties.markwon.Markwon;

public class ChangelogActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "release_title";
    public static final String EXTRA_HASH = "release_hash";
    public static final String EXTRA_CHANGELOG = "release_changelog";
    public static final String EXTRA_RELEASE_URL = "release_url";
    public static final String EXTRA_PUBLISHED_AT = "release_published_at";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityChangelogBinding binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String hash = intent.getStringExtra(EXTRA_HASH);
        String changelog = intent.getStringExtra(EXTRA_CHANGELOG);
        String releaseUrl = intent.getStringExtra(EXTRA_RELEASE_URL);
        String publishedAt = intent.getStringExtra(EXTRA_PUBLISHED_AT);

        if (TextUtils.isEmpty(hash)) {
            ReleaseInfo newest = UpdateInfoStore.getNewest(this);
            if (newest != null) {
                if (TextUtils.isEmpty(title)) title = newest.displayName();
                hash = newest.hash;
                changelog = newest.changelog;
                releaseUrl = newest.releaseUrl;
                publishedAt = newest.publishedAt;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(
                    TextUtils.isEmpty(title) ? getString(R.string.changelog_title) : title);
        }

        if (TextUtils.isEmpty(hash)) {
            binding.tvVersion.setText(R.string.changelog_no_update);
            binding.tvDate.setVisibility(View.GONE);
            binding.tvChangelog.setText(R.string.changelog_no_update_summary);
        } else {
            binding.tvVersion.setText(getString(R.string.changelog_version, hash));
            String displayDate = formatPublishedDate(publishedAt);
            if (displayDate.isEmpty()) {
                binding.tvDate.setVisibility(View.GONE);
            } else {
                binding.tvDate.setVisibility(View.VISIBLE);
                binding.tvDate.setText(getString(R.string.changelog_released, displayDate));
            }
            if (TextUtils.isEmpty(changelog)) {
                binding.tvChangelog.setText(R.string.changelog_no_update_summary);
            } else {
                Markwon.create(this).setMarkdown(binding.tvChangelog, changelog);
            }
        }

        String targetUrl = TextUtils.isEmpty(releaseUrl)
                ? UpdateInfoStore.FALLBACK_RELEASE_URL : releaseUrl;
        binding.btnDownload.setOnClickListener(v -> openUrl(targetUrl));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            // No browser/default handler: fall back to the always-on releases page.
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(UpdateInfoStore.FALLBACK_RELEASE_URL)));
        }
    }

    private String formatPublishedDate(String isoDate) {
        if (TextUtils.isEmpty(isoDate)) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = iso.parse(isoDate);
            if (date == null) return "";
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date);
        } catch (ParseException e) {
            return isoDate;
        }
    }
}
