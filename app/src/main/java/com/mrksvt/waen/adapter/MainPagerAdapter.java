package com.mrksvt.waen.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mrksvt.waen.ui.fragments.CustomizationFragment;
import com.mrksvt.waen.ui.fragments.GeneralFragment;
import com.mrksvt.waen.ui.fragments.HomeFragment;
import com.mrksvt.waen.ui.fragments.MediaFragment;
import com.mrksvt.waen.ui.fragments.PrivacyFragment;
import com.mrksvt.waen.ui.fragments.RecordingsFragment;
import com.mrksvt.waen.ui.fragments.TtsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int POSITION_RECORDINGS = 5;
    public static final int POSITION_TTS = 6;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
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
            case 6 -> new TtsFragment();
            default -> new HomeFragment();
        };
    }

    @Override
    public int getItemCount() {
        return 7;
    }
}