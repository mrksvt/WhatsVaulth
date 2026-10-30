package com.mrksvt.waen.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;
import com.mrksvt.waen.UpdateChecker;
import com.mrksvt.waen.UpdateChannel;
import com.mrksvt.waen.UpdateInfoStore;
import com.mrksvt.waen.activities.ChangelogActivity;
import com.mrksvt.waen.adapter.ReleaseListAdapter;
import com.mrksvt.waen.databinding.FragmentUpdateCenterBinding;
import com.mrksvt.waen.update.UpdateNotifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Update Center page: pick the release channel, opt into system notifications and
 * browse the releases on that channel.
 */
public class UpdateCenterFragment extends Fragment {

    private FragmentUpdateCenterBinding binding;
    private ReleaseListAdapter adapter;
    private final List<ReleaseInfo> items = new ArrayList<>();

    /**
     * Registered in {@link #onCreate} because the launcher must exist before the
     * fragment reaches STARTED; registering from the switch listener would throw.
     */
    private ActivityResultLauncher<String> permissionLauncher;

    /** Set while a permission result is being applied, to avoid re-asking on revert. */
    private boolean suppressSwitchCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUpdateCenterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    // Denied: the switch goes back to off and the hint explains why.
                    UpdateNotifier.setInAppNotificationEnabled(requireContext(), granted);
                    syncNotificationSwitch();
                    if (!granted) showPermissionHint();
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ReleaseListAdapter(items, UpdateInfoStore.getReadTags(requireContext()),
                installedHash(), "", this::openChangelogFor);
        binding.updateReleaseList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.updateReleaseList.setAdapter(adapter);

        bindChannelToggle();
        bindNotificationSwitch();

        binding.swipeRefresh.setOnRefreshListener(this::startUpdateCheck);
        binding.btnOpenNotificationSettings.setOnClickListener(v -> openNotificationSettings());

        syncChannelControls();
        loadReleases();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Permission may have changed in system settings while we were away.
        if (binding == null) return;
        syncNotificationSwitch();
        loadReleases();
    }

    private void bindChannelToggle() {
        binding.toggleGroupChannel.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            UpdateChannel channel = checkedId == R.id.btn_channel_prerelease
                    ? UpdateChannel.PRERELEASE : UpdateChannel.RELEASE;
            if (channel == UpdateInfoStore.getChannel(requireContext())) return;
            UpdateInfoStore.setChannel(requireContext(), channel);
            syncChannelControls();
            loadReleases();
        });
    }

    private void bindNotificationSwitch() {
        binding.swInAppNotification.setOnCheckedChangeListener((buttonView, checked) -> {
            if (suppressSwitchCallback) return;
            if (!checked) {
                UpdateNotifier.setInAppNotificationEnabled(requireContext(), false);
                updatePermissionHint();
                return;
            }
            if (UpdateNotifier.canPost(requireContext())) {
                UpdateNotifier.setInAppNotificationEnabled(requireContext(), true);
                updatePermissionHint();
                return;
            }
            if (UpdateNotifier.needsPermissionRequest(requireContext())) {
                // Ask once; the result callback decides the final switch state.
                launchNotificationPermissionRequest();
                return;
            }
            // Permission permanently denied or app notifications blocked in settings.
            UpdateNotifier.setInAppNotificationEnabled(requireContext(), false);
            syncNotificationSwitch();
            showPermissionHint();
        });
    }

    /** Kept API-33-clean: the constant is only touched on TIRAMISU and up. */
    @SuppressWarnings("InlinedApi")
    private void launchNotificationPermissionRequest() {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Mirrors the stored toggle AND the real permission state onto the switch. */
    private void syncNotificationSwitch() {
        boolean enabled = UpdateNotifier.getInAppNotificationEnabled(requireContext());
        boolean allowed = UpdateNotifier.canPost(requireContext());
        if (enabled && !allowed) {
            // Permission was revoked outside the app: reflect reality, do not crash.
            UpdateNotifier.setInAppNotificationEnabled(requireContext(), false);
            enabled = false;
        }
        suppressSwitchCallback = true;
        binding.swInAppNotification.setChecked(enabled);
        suppressSwitchCallback = false;
        updatePermissionHint();
    }

    private void updatePermissionHint() {
        boolean show = UpdateNotifier.needsPermissionRequest(requireContext())
                || !UpdateNotifier.canPost(requireContext());
        binding.tvPermissionHint.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnOpenNotificationSettings.setVisibility(
                UpdateNotifier.needsPermissionRequest(requireContext())
                        ? View.GONE : (show ? View.VISIBLE : View.GONE));
    }

    /** Hint plus a dialog so a denied switch tap does not look like a no-op. */
    private void showPermissionHint() {
        updatePermissionHint();
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.update_notification_permission_denied)
                .setPositiveButton(R.string.update_notification_open_settings,
                        (dialog, which) -> openNotificationSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void syncChannelControls() {
        UpdateChannel channel = UpdateInfoStore.getChannel(requireContext());
        int id = channel == UpdateChannel.PRERELEASE
                ? R.id.btn_channel_prerelease : R.id.btn_channel_release;
        if (binding.toggleGroupChannel.getCheckedButtonId() != id) {
            binding.toggleGroupChannel.check(id);
        }
        binding.tvChannelSummary.setText(channel == UpdateChannel.PRERELEASE
                ? R.string.update_channel_prerelease_sum : R.string.update_channel_release_sum);
    }

    private void loadReleases() {
        if (binding == null) return;
        UpdateChannel channel = UpdateInfoStore.getChannel(requireContext());
        items.clear();
        items.addAll(UpdateInfoStore.getReleasesForChannel(requireContext(), channel));

        ReleaseInfo newest = UpdateInfoStore.getNewest(requireContext(), channel);
        adapter = new ReleaseListAdapter(items, UpdateInfoStore.getReadTags(requireContext()),
                installedHash(), newest == null ? "" : newest.hash, this::openChangelogFor);
        binding.updateReleaseList.setAdapter(adapter);

        binding.swipeRefresh.setRefreshing(false);
        boolean empty = items.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.updateReleaseList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startUpdateCheck() {
        CompletableFuture.runAsync(new UpdateChecker(requireContext().getApplicationContext(),
                hasUpdate -> {
                    if (binding == null || !isAdded()) return;
                    requireActivity().runOnUiThread(this::onCheckFinished);
                }));
    }

    private void onCheckFinished() {
        if (binding == null) return;
        loadReleases();
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.update_center_load_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String installedHash() {
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            if (versionName == null) return "";
            var match = java.util.regex.Pattern.compile("[0-9a-fA-F]{8}").matcher(versionName);
            return match.find() ? match.group().toUpperCase(java.util.Locale.US) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void openChangelogFor(ReleaseInfo release) {
        UpdateInfoStore.markRead(requireContext(), release.tagName);
        Intent intent = new Intent(requireContext(), ChangelogActivity.class)
                .putExtra(ChangelogActivity.EXTRA_TITLE, release.displayName())
                .putExtra(ChangelogActivity.EXTRA_HASH, release.hash)
                .putExtra(ChangelogActivity.EXTRA_CHANGELOG, release.changelog)
                .putExtra(ChangelogActivity.EXTRA_RELEASE_URL, release.releaseUrl)
                .putExtra(ChangelogActivity.EXTRA_PUBLISHED_AT, release.publishedAt)
                .putExtra(ChangelogActivity.EXTRA_APK_URL, release.apkUrl)
                .putExtra(ChangelogActivity.EXTRA_APK_SIZE, release.apkSize);
        startActivity(intent);
        loadReleases();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
