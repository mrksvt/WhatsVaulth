package com.mrksvt.waen.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mrksvt.waen.ui.fragments.CustomizationFragment;
import com.mrksvt.waen.ui.fragments.GeneralFragment;
import com.mrksvt.waen.ui.fragments.HomeFragment;
import com.mrksvt.waen.ui.fragments.MediaFragment;
import com.mrksvt.waen.ui.fragments.PrivacyFragment;
import com.mrksvt.waen.ui.fragments.RecordingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    private final boolean isRecordingEnabled;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        var prefs = PreferenceManager.getDefaultSharedPreferences(fragmentActivity);
        isRecordingEnabled = prefs.getBoolean("call_recording_enable", false);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 0 -> new GeneralFragment();
            case 1 -> new PrivacyFragment();
            case 3 -> new MediaFragment();
            case 4 -> new CustomizationFragment();
            case 5 -> new RecordingsFragment();
            default -> new HomeFragment();
        };
    }

    @Override
    public int getItemCount() {
        return isRecordingEnabled ? 6 : 5;
    }
}