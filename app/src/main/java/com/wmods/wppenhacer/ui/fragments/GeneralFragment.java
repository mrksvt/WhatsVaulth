package com.wmods.wppenhacer.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GeneralFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        var root = super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction().add(R.id.frag_container, new GeneralPreferenceFragment()).commitNow();
        }
        
        // Handle scroll to preference from search
        if (getActivity() != null && getActivity().getIntent() != null) {
            String scrollToKey = getActivity().getIntent().getStringExtra("scroll_to_preference");
            if (scrollToKey != null) {
                getView().postDelayed(() -> {
                    BasePreferenceFragment activeFragment = (BasePreferenceFragment) getChildFragmentManager().findFragmentById(R.id.frag_container);
                    if (activeFragment != null) {
                        activeFragment.scrollToPreference(scrollToKey);
                    }
                }, 300);
                // Clear the intent extra
                getActivity().getIntent().removeExtra("scroll_to_preference");
            }
        }
        
        return root;
    }

    public static class GeneralPreferenceFragment extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.fragment_general, rootKey);
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }

    public static class HomeGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_home, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class HomeScreenGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class ConversationGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class TranslatorGeneralPreference extends BasePreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private ListPreference providerPref;
        private EditTextPreference apiKeyPref;
        private ListPreference modelPref;
        private Preference fetchModelsPref;
        private PreferenceCategory groqCategory;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_translator, rootKey);
            setDisplayHomeAsUpEnabled(true);

            providerPref = findPreference("translator_provider");
            apiKeyPref = findPreference("groq_translator_api_key");
            modelPref = findPreference("groq_translator_model");
            fetchModelsPref = findPreference("groq_fetch_models");
            groqCategory = findPreference("groq_settings_category");

            if (apiKeyPref != null) {
                apiKeyPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(
                        android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    );
                    editText.setSelection(editText.getText().length());
                });
                String currentKey = apiKeyPref.getSharedPreferences() != null
                    ? apiKeyPref.getSharedPreferences().getString("groq_translator_api_key", "")
                    : "";
                updateApiKeySummary(currentKey);
            }

            if (fetchModelsPref != null) {
                fetchModelsPref.setOnPreferenceClickListener(pref -> {
                    fetchGroqModels();
                    return true;
                });
            }

            updateGroqVisibility();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (providerPref != null && providerPref.getSharedPreferences() != null) {
                providerPref.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (providerPref != null && providerPref.getSharedPreferences() != null) {
                providerPref.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("translator_provider".equals(key)) {
                updateGroqVisibility();
            } else if ("groq_translator_api_key".equals(key)) {
                updateApiKeySummary(sharedPreferences.getString(key, ""));
            } else if ("groq_translator_model".equals(key)) {
                updateModelSummary();
            }
        }

        private void updateGroqVisibility() {
            if (providerPref == null || groqCategory == null) return;
            String provider = providerPref.getValue();
            boolean isGroq = "groq".equals(provider);
            groqCategory.setVisible(isGroq);

            String[] entries = getResources().getStringArray(R.array.translator_provider_entries);
            String[] values = getResources().getStringArray(R.array.translator_provider_values);
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(provider)) {
                    providerPref.setSummary(entries[i]);
                    break;
                }
            }
        }

        private void updateApiKeySummary(String apiKey) {
            if (apiKeyPref == null) return;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKeyPref.setSummary(getString(R.string.groq_translator_api_key_sum));
            } else {
                String masked = "••••••••" + apiKey.substring(Math.max(0, apiKey.length() - 4));
                apiKeyPref.setSummary(masked);
            }
        }

        private void updateModelSummary() {
            if (modelPref == null) return;
            String model = modelPref.getValue();
            if (model != null && !model.isEmpty()) {
                modelPref.setSummary(model);
            }
        }

        private void fetchGroqModels() {
            if (fetchModelsPref == null || modelPref == null) return;

            SharedPreferences sharedPrefs = providerPref != null ? providerPref.getSharedPreferences() : null;
            String apiKey = sharedPrefs != null ? sharedPrefs.getString("groq_translator_api_key", "") : "";

            if (apiKey == null || apiKey.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.groq_translator_api_key_required),
                    android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            fetchModelsPref.setSummary(getString(R.string.groq_translator_model_loading));
            fetchModelsPref.setEnabled(false);

            OkHttpClient client = new OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer " + apiKey)
                .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@androidx.annotation.NonNull okhttp3.Call call,
                                      @androidx.annotation.NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        fetchModelsPref.setSummary(getString(R.string.groq_translator_model_error));
                        fetchModelsPref.setEnabled(true);
                    });
                }

                @Override
                public void onResponse(@androidx.annotation.NonNull okhttp3.Call call,
                                       @androidx.annotation.NonNull okhttp3.Response response) {
                    try {
                        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        JSONArray data = json.getJSONArray("data");

                        List<String> modelIds = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            String id = data.getJSONObject(i).getString("id");
                if (!id.contains("whisper") && !id.contains("tts")) {
                                modelIds.add(id);
                            }
                        }
                        modelIds.sort(String::compareTo);

                        String[] entries = modelIds.toArray(new String[0]);
                        String[] values = modelIds.toArray(new String[0]);

                        requireActivity().runOnUiThread(() -> {
                            modelPref.setEntries(entries);
                            modelPref.setEntryValues(values);
                            String current = modelPref.getValue();
                            if (current == null || current.isEmpty() || !modelIds.contains(current)) {
                                String fallback = modelIds.contains("llama-3.1-8b-instant")
                                    ? "llama-3.1-8b-instant"
                                    : (modelIds.isEmpty() ? "" : modelIds.get(0));
                                modelPref.setValue(fallback);
                                modelPref.setSummary(fallback);
                            } else {
                                modelPref.setSummary(current);
                            }
                            fetchModelsPref.setSummary(
                                getString(R.string.groq_translator_fetch_models_sum)
                                + " (" + modelIds.size() + " models)"
                            );
                            fetchModelsPref.setEnabled(true);
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            fetchModelsPref.setSummary(getString(R.string.groq_translator_model_error));
                            fetchModelsPref.setEnabled(true);
                        });
                    }
                }
            });
        }
    }

}