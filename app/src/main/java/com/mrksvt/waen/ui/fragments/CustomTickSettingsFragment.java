package com.mrksvt.waen.ui.fragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;

public class CustomTickSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preference_custom_tick, rootKey);
        setDisplayHomeAsUpEnabled(true);

        Preference managePresets = findPreference("custom_tick_manage_presets");
        if (managePresets != null) {
            managePresets.setOnPreferenceClickListener(pref -> {
                if (getParentFragment() != null) {
                    getParentFragment().getChildFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frag_container, new CustomTickPresetsFragment())
                            .addToBackStack(null)
                            .commit();
                }
                return true;
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences sharedPreferences, String key) {
        if ("custom_tick_enable".equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            if (enabled && sharedPreferences.getLong("custom_tick_active_preset_id", -1L) == -1L) {
                Toast.makeText(requireContext(), "Select a tick preset first", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
