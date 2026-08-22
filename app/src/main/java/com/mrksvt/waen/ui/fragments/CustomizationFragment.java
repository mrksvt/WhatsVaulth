package com.mrksvt.waen.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ui.fragments.base.BaseFragment;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;

public class CustomizationFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        var root = super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .add(R.id.frag_container, new CustomizationPreferenceFragment())
                    .commitNow();
        }
        return root;
    }

    public static class CustomizationPreferenceFragment extends BasePreferenceFragment {

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.fragment_customization, rootKey);

            Preference customTickPref = findPreference("custom_tick_settings");
            if (customTickPref != null) {
                customTickPref.setOnPreferenceClickListener(pref -> {
                    requireParentFragment().getChildFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frag_container, new CustomTickSettingsFragment())
                            .addToBackStack(null)
                            .commit();
                    return true;
                });
            }

            Preference themeBuilderPref = findPreference("theme_builder_settings");
            if (themeBuilderPref != null) {
                themeBuilderPref.setOnPreferenceClickListener(pref -> {
                    requireParentFragment().getChildFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frag_container, new ThemeBuilderFragment())
                            .addToBackStack(null)
                            .commit();
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }

        @Override
        public void onViewCreated(@NonNull android.view.View view, @Nullable android.os.Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (getActivity() != null && getActivity().getIntent() != null) {
                String scrollToKey = getActivity().getIntent().getStringExtra("scroll_to_preference");
                if (scrollToKey != null) {
                    scrollToPreference(scrollToKey);
                    getActivity().getIntent().removeExtra("scroll_to_preference");
                }
            }
        }
    }
}
