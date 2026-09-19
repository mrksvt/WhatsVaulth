package com.mrksvt.waen.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrksvt.waen.R;
import com.mrksvt.waen.activities.CallRecordingSettingsActivity;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;

public class MediaFragment extends BasePreferenceFragment {

    private static final String CONSENT_DIALOG_SHOWN = "screen_capture_consent_shown";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_media, rootKey);

        // Call Recording Settings preference
        var callRecordingSettings = findPreference("call_recording_settings");
        if (callRecordingSettings != null) {
            callRecordingSettings.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), CallRecordingSettingsActivity.class);
                startActivity(intent);
                return true;
            });
        }

        var videoEnable = findPreference("call_recording_video_enable");
        if (videoEnable != null) {
            videoEnable.setOnPreferenceChangeListener((preference, newValue) -> {
                if (Boolean.TRUE.equals(newValue)) {
                    maybeShowConsentDialog();
                    maybeAskOverlayPermission();
                }
                return true;
            });
        }
    }

    /**
     * Rekaman video dipicu saat aplikasi sedang di background (user sedang
     * memakai WhatsApp). Android 10+ menolak membuka activity dari background
     * tanpa izin "Display over other apps", sehingga dialog consent tidak akan
     * muncul dan rekaman video gagal tanpa penjelasan. Karena itu izin ini
     * diminta lebih dulu, saat user masih di layar pengaturan.
     */
    private void maybeAskOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (Settings.canDrawOverlays(requireContext())) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.screen_capture_consent_title)
                .setMessage(R.string.screen_capture_permission_needed)
                .setPositiveButton(R.string.screen_capture_consent_ok, (dialog, which) -> {
                    try {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void maybeShowConsentDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;

        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        if (prefs.getBoolean(CONSENT_DIALOG_SHOWN, false)) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.screen_capture_consent_title)
                .setMessage(R.string.screen_capture_consent_message)
                .setPositiveButton(R.string.screen_capture_consent_ok, (dialog, which) ->
                        prefs.edit().putBoolean(CONSENT_DIALOG_SHOWN, true).apply())
                .show();
    }
}
