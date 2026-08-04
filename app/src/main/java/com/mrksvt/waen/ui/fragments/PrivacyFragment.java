package com.mrksvt.waen.ui.fragments;

import static com.mrksvt.waen.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrksvt.waen.R;
import com.mrksvt.waen.preference.ContactPickerPreference;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;

public class PrivacyFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_privacy, rootKey);

    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("onActivityResult: " + requestCode + " " + resultCode + " " + data);
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            ContactPickerPreference contactPickerPref = findPreference(data.getStringExtra("key"));
            if (contactPickerPref != null) {
                contactPickerPref.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Handle scroll to preference from search
        if (getActivity() != null && getActivity().getIntent() != null) {
            String scrollToKey = getActivity().getIntent().getStringExtra("scroll_to_preference");
            if (scrollToKey != null) {
                scrollToPreference(scrollToKey);
                // Clear the intent extra
                getActivity().getIntent().removeExtra("scroll_to_preference");
            }
        }
    }

}
