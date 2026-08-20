package com.mrksvt.waen.ui.fragments.base;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public final class BackNavHelper {

    private BackNavHelper() {
    }

    public static void install(Fragment fragment) {
        fragment.requireActivity().getOnBackPressedDispatcher().addCallback(
                fragment.getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        FragmentManager mgr = fragment.getParentFragment() != null
                                ? fragment.getParentFragment().getChildFragmentManager()
                                : fragment.getParentFragmentManager();
                        if (mgr.getBackStackEntryCount() > 0) {
                            mgr.popBackStack();
                        } else {
                            setEnabled(false);
                            fragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }
}
