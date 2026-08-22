package com.mrksvt.waen.ui.fragments;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.R;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;

public class CustomFontSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preference_custom_font, rootKey);
        setDisplayHomeAsUpEnabled(true);

        if (!BuildConfig.DONATUR && !BuildConfig.DEBUG) {
            SwitchPreferenceCompat enablePref = findPreference("custom_font_enable");
            if (enablePref != null) {
                enablePref.setEnabled(false);
                enablePref.setSummary(R.string.custom_font_donatur_only);
            }
            Preference managePref = findPreference("custom_font_manage_presets");
            if (managePref != null) {
                managePref.setEnabled(false);
                managePref.setSummary(R.string.custom_font_donatur_only);
            }
            return;
        }

        Preference managePresets = findPreference("custom_font_manage_presets");
        if (managePresets != null) {
            managePresets.setOnPreferenceClickListener(pref -> {
                if (getParentFragment() != null) {
                    getParentFragment().getChildFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frag_container, new CustomFontPresetsFragment())
                            .addToBackStack(null)
                            .commit();
                }
                return true;
            });
        }
    }
}
