package com.mrksvt.waen.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;
import com.mrksvt.waen.UpdateInfoStore;
import com.mrksvt.waen.activities.base.BaseActivity;
import com.mrksvt.waen.databinding.ActivityChangelogBinding;
import com.mrksvt.waen.update.ApkCleanupPolicy;
import com.mrksvt.waen.update.ApkDownloader;
import com.mrksvt.waen.update.ApkInstaller;
import com.mrksvt.waen.update.DownloadState;
import com.mrksvt.waen.update.InstallFailure;
import com.mrksvt.waen.update.InstallFailureMessages;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

public class ChangelogActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "release_title";
    public static final String EXTRA_HASH = "release_hash";
    public static final String EXTRA_CHANGELOG = "release_changelog";
    public static final String EXTRA_RELEASE_URL = "release_url";
    public static final String EXTRA_PUBLISHED_AT = "release_published_at";
    public static final String EXTRA_APK_URL = "release_apk_url";
    public static final String EXTRA_APK_SIZE = "release_apk_size";

    private ChangelogViewModel viewModel;
    private ActivityChangelogBinding binding;
    private String apkUrl = "";
    private String releaseUrl = "";
    private String releaseKey = "";
    private boolean waitingForInstallPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangelogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(ChangelogViewModel.class);

        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String hash = intent.getStringExtra(EXTRA_HASH);
        String changelog = intent.getStringExtra(EXTRA_CHANGELOG);
        releaseUrl = intent.getStringExtra(EXTRA_RELEASE_URL);
        String publishedAt = intent.getStringExtra(EXTRA_PUBLISHED_AT);
        apkUrl = intent.getStringExtra(EXTRA_APK_URL);

        if (TextUtils.isEmpty(hash)) {
            ReleaseInfo newest = UpdateInfoStore.getNewest(this);
            if (newest != null) {
                if (TextUtils.isEmpty(title)) title = newest.displayName();
                hash = newest.hash;
                changelog = newest.changelog;
                releaseUrl = newest.releaseUrl;
                publishedAt = newest.publishedAt;
                if (TextUtils.isEmpty(apkUrl)) apkUrl = newest.apkUrl;
            }
        }
        releaseKey = TextUtils.isEmpty(hash) ? "release" : hash;

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

        binding.btnCancelDownload.setOnClickListener(v -> cancelDownload());
        binding.btnDownload.setOnClickListener(v -> onPrimaryAction());
        viewModel.setUiCallback(state -> renderState());
        cleanupStaleApksAsync();
        renderState();
    }

    /**
     * Removes APKs that no live download or install-ready state owns. Runs on the downloader's
     * background executor because it touches the filesystem (NFR-01).
     *
     * <p>A successful install replaces this process, so the in-memory state is gone by the
     * time the user returns. Any APK left on disk at that point has no owner and is deleted.
     */
    private void cleanupStaleApksAsync() {
        DownloadState state = viewModel.getState();
        boolean ownsFile = state instanceof DownloadState.Done || state instanceof DownloadState.Progress;
        String keep = ApkCleanupPolicy.keepFileKey(ownsFile, releaseKey);
        ApkDownloader downloader = viewModel.downloader(this);
        viewModel.runInBackground(() -> {
            try {
                ApkDownloader.cleanupStale(downloader.updateDir(), keep);
            } catch (Exception e) {
                // Cleanup is best effort: a leftover file is harmless.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission && ApkInstaller.canInstall(this)) {
            waitingForInstallPermission = false;
            File file = viewModel.getDownloadedFile();
            if (file != null) {
                startInstall(file);
            }
            return;
        }
        // Safety net for an app restart after a successful install: the installer result
        // covers installs finished during this session, this covers the rest.
        if (viewModel.getState() instanceof DownloadState.Done && isInstalledVersionNewer()) {
            onInstallSucceeded();
        }
    }

    /** Drops the downloaded APK once the new build is installed. */
    private void onInstallSucceeded() {
        removeDownloadedApk();
        viewModel.setState(DownloadState.Idle.INSTANCE);
        renderState();
    }

    /** Deletes the downloaded APK, including its `.part` sidecar, off the main thread. */
    private void removeDownloadedApk() {
        File file = viewModel.getDownloadedFile();
        viewModel.clearDownloadedFile();
        if (file == null) return;
        File partial = new File(file.getParentFile(), file.getName() + ".part");
        viewModel.runInBackground(() -> {
            if (file.exists()) file.delete();
            if (partial.exists()) partial.delete();
        });
    }

    private final ActivityResultLauncher<Intent> installLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (ApkCleanupPolicy.shouldDeleteAfterInstall(result.getResultCode() == RESULT_OK)) {
                    onInstallSucceeded();
                    return;
                }
                // The installer was cancelled or failed: keep the APK so the user can retry.
                renderState();
            });

    /** True when the installed package version is newer than the running build. */
    private boolean isInstalledVersionNewer() {
        try {
            var info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.getLongVersionCode() > com.mrksvt.waen.BuildConfig.VERSION_CODE;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        viewModel.setUiCallback(null);
        // The system installer still reads the file while it is open, so keep it when the
        // APK is install-ready. Stale files are removed on the next open.
        if (isFinishing() && !(viewModel.getState() instanceof DownloadState.Done)) {
            removeDownloadedApk();
        }
        super.onDestroy();
    }

    private void onPrimaryAction() {
        DownloadState state = viewModel.getState();
        if (state instanceof DownloadState.Done) {
            File file = viewModel.getDownloadedFile();
            if (file == null) {
                showBrowserFallback(getString(R.string.changelog_install_asset_missing));
                return;
            }
            startInstall(file);
            return;
        }
        if (state instanceof DownloadState.Failed) {
            openUrl(TextUtils.isEmpty(releaseUrl)
                    ? UpdateInfoStore.FALLBACK_RELEASE_URL : releaseUrl);
            return;
        }
        if (state instanceof DownloadState.Cancelled) {
            startDownload();
            return;
        }
        if (state instanceof DownloadState.Progress) {
            return;
        }
        if (TextUtils.isEmpty(apkUrl)) {
            showBrowserFallback(getString(R.string.changelog_install_asset_missing));
            return;
        }
        startDownload();
    }

    private void startDownload() {
        if (TextUtils.isEmpty(apkUrl)) {
            showBrowserFallback(getString(R.string.changelog_install_asset_missing));
            return;
        }
        ApkDownloader downloader = viewModel.downloader(this);
        downloader.start(apkUrl, releaseKey, viewModel.downloadListener());
    }

    private void cancelDownload() {
        // The worker thread reports Cancelled once it stops; setting it here would race with
        // the terminal state and could leave a stale label on the button.
        viewModel.downloader(this).cancel();
        renderState();
    }

    private void startInstall(File file) {
        // Package archive parsing reads from disk, so keep it off the main thread.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                InstallFailure failure = ApkInstaller.verifyApk(this, file);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (failure != null) {
                        showBrowserFallback(messageFor(failure));
                        return;
                    }
                    if (!ApkInstaller.canInstall(this)) {
                        waitingForInstallPermission = true;
                        try {
                            startActivity(ApkInstaller.unknownSourcesIntent(this));
                        } catch (Exception e) {
                            waitingForInstallPermission = false;
                            showBrowserFallback(getString(R.string.changelog_install_needs_permission));
                        }
                        return;
                    }
                    Intent intent = ApkInstaller.installIntent(this, file);
                    if (intent == null) {
                        showBrowserFallback(getString(R.string.changelog_install_unknown_error));
                        return;
                    }
                    try {
                        installLauncher.launch(intent);
                    } catch (Exception e) {
                        showBrowserFallback(getString(R.string.changelog_install_unknown_error));
                    }
                });
            });
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Reports a failure and switches the primary action to the browser fallback, so every
     * [InstallFailure] path (including SIGNATURE_MISMATCH and UNKNOWN) really ends up on the
     * browser label instead of a dead-end message.
     */
    private void showBrowserFallback(String message) {
        viewModel.setState(new DownloadState.Failed(message));
        renderState();
        binding.tvChangelog.setText(message);
    }

    private String messageFor(InstallFailure failure) {
        return getString(InstallFailureMessages.resourceId(failure));
    }

    private void renderState() {
        DownloadState state = viewModel.getState();
        if (state instanceof DownloadState.Progress) {
            DownloadState.Progress progress = (DownloadState.Progress) state;
            binding.btnDownload.setEnabled(false);
            if (progress.getPercent() >= 0) {
                binding.btnDownload.setText(
                        getString(R.string.changelog_downloading, progress.getPercent()));
            } else {
                binding.btnDownload.setText(R.string.changelog_downloading_unknown);
            }
            binding.btnCancelDownload.setVisibility(View.VISIBLE);
            return;
        }
        if (state instanceof DownloadState.Done) {
            binding.btnDownload.setEnabled(true);
            binding.btnDownload.setText(R.string.changelog_install);
            binding.btnCancelDownload.setVisibility(View.GONE);
            return;
        }
        if (state instanceof DownloadState.Failed) {
            binding.btnDownload.setEnabled(true);
            binding.btnDownload.setText(R.string.changelog_open_in_browser);
            binding.btnCancelDownload.setVisibility(View.GONE);
            binding.tvChangelog.setText(R.string.changelog_download_failed);
            return;
        }
        if (state instanceof DownloadState.Cancelled) {
            binding.btnDownload.setEnabled(true);
            binding.btnDownload.setText(R.string.download);
            binding.btnCancelDownload.setVisibility(View.GONE);
            return;
        }
        binding.btnDownload.setEnabled(true);
        binding.btnCancelDownload.setVisibility(View.GONE);
        if (TextUtils.isEmpty(apkUrl)) {
            binding.btnDownload.setText(R.string.changelog_open_in_browser);
        } else {
            binding.btnDownload.setText(R.string.download);
        }
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
