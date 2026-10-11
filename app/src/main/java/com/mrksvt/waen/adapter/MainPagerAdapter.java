package com.mrksvt.waen.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mrksvt.waen.activities.MainNavigationRoutes;
import com.mrksvt.waen.ui.fragments.CustomizationFragment;
import com.mrksvt.waen.ui.fragments.GeneralFragment;
import com.mrksvt.waen.ui.fragments.HomeFragment;
import com.mrksvt.waen.ui.fragments.MediaFragment;
import com.mrksvt.waen.ui.fragments.PrivacyFragment;
import com.mrksvt.waen.ui.fragments.RecordingsFragment;
import com.mrksvt.waen.ui.fragments.TtsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int POSITION_GENERAL = MainNavigationRoutes.POSITION_GENERAL;
    public static final int POSITION_PRIVACY = MainNavigationRoutes.POSITION_PRIVACY;
    public static final int POSITION_HOME = MainNavigationRoutes.POSITION_HOME;
    public static final int POSITION_MEDIA = MainNavigationRoutes.POSITION_MEDIA;
    public static final int POSITION_CUSTOMIZATION = MainNavigationRoutes.POSITION_CUSTOMIZATION;
    public static final int POSITION_RECORDINGS = MainNavigationRoutes.POSITION_RECORDINGS;
    public static final int POSITION_TTS = MainNavigationRoutes.POSITION_TTS;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case POSITION_GENERAL -> new GeneralFragment();
            case POSITION_PRIVACY -> new PrivacyFragment();
            case POSITION_MEDIA -> new MediaFragment();
            case POSITION_CUSTOMIZATION -> new CustomizationFragment();
            case POSITION_RECORDINGS -> new RecordingsFragment();
            case POSITION_TTS -> new TtsFragment();
            default -> new HomeFragment();
        };
    }

    @Override
    public int getItemCount() {
        return MainNavigationRoutes.PAGE_COUNT;
    }
}
