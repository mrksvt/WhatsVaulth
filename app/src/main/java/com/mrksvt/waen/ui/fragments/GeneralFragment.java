package com.mrksvt.waen.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.R;
import com.mrksvt.waen.ui.fragments.TrashRecoveryFragment;
import com.mrksvt.waen.ui.fragments.base.BaseFragment;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;
import com.mrksvt.waen.xposed.core.HookOverrideStore;
import com.mrksvt.waen.xposed.core.TelegramReporter;

import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            if (!BuildConfig.DEBUG && !BuildConfig.DONATUR) {
                Preference devPref = findPreference("dev_engineering_screen");
                if (devPref != null) devPref.setVisible(false);
            }
            Preference trashPref = findPreference("trash_recovery_screen");
            if (trashPref != null) {
                if (!BuildConfig.DEBUG && !BuildConfig.DONATUR) trashPref.setVisible(false);
                trashPref.setOnPreferenceClickListener(pref -> {
                    requireParentFragment().getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frag_container, new TrashRecoveryFragment())
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

    public static class DevEngineeringFragment extends Fragment {

        private static final String LOG_PATH = "/data/data/com.mrksvt.waen/files/wae_dev_log.txt";

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            Context ctx = requireContext();
            android.content.res.Resources res = ctx.getResources();
            float density = res.getDisplayMetrics().density;
            int dp1  = (int) (1  * density + 0.5f);
            int dp16 = (int) (16 * density + 0.5f);

            ScrollView scrollRoot = new ScrollView(ctx);
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            scrollRoot.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View divider = new View(ctx);
            divider.setBackgroundColor(0x1F000000);
            root.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp1));

            if (BuildConfig.DEBUG || BuildConfig.DONATUR) {
                root.addView(makeMenuItem(ctx,
                        "APK Explorer",
                        "Jelajahi resource & ID dari APK WhatsApp",
                        () -> requireParentFragment().getChildFragmentManager()
                                .beginTransaction()
                                .replace(R.id.frag_container, new ApkExplorerFragment())
                                .addToBackStack(null)
                                .commit()));

                View d2 = new View(ctx);
                d2.setBackgroundColor(0x1F000000);
                root.addView(d2, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp1));

                root.addView(makeMenuItem(ctx,
                        "Kelola Hook Override",
                        "Konfigurasi override hook untuk versi baru WA",
                        () -> requireParentFragment().getChildFragmentManager()
                                .beginTransaction()
                                .replace(R.id.frag_container, new com.mrksvt.waen.ui.fragments.HookOverrideFragment())
                                .addToBackStack(null)
                                .commit()));

                View d3 = new View(ctx);
                d3.setBackgroundColor(0x1F000000);
                root.addView(d3, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp1));
            }

            if (BuildConfig.DONATUR) {
                root.addView(makeMenuItem(ctx,
                        "Kirim Report ke Developer",
                        "Laporkan error & hook fix ke developer",
                        this::showSendReportDialog));

                View d4 = new View(ctx);
                d4.setBackgroundColor(0x1F000000);
                root.addView(d4, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp1));
            }

            root.addView(makeMenuItem(ctx,
                    "Log WhatsVault",
                    "Lihat log klik & aktivitas modul",
                    () -> requireParentFragment().getChildFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frag_container, new DevLogFragment())
                            .addToBackStack(null)
                            .commit()));

            requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                        getParentFragmentManager().popBackStack();
                    } else {
                        requireActivity().finish();
                    }
                }
            });

            return scrollRoot;
        }

        private View makeMenuItem(Context ctx, String title, String subtitle, Runnable onClick) {
            float density = ctx.getResources().getDisplayMetrics().density;
            int dp12 = (int) (12 * density + 0.5f);
            int dp16 = (int) (16 * density + 0.5f);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp16, dp12, dp16, dp12);

            android.util.TypedValue tv = new android.util.TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackground(androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> onClick.run());

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(textColParams);

            TextView tvTitle = new TextView(ctx);
            tvTitle.setText(title);
            tvTitle.setTextSize(16f);
            tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            textCol.addView(tvTitle);

            TextView tvSubtitle = new TextView(ctx);
            tvSubtitle.setText(subtitle);
            tvSubtitle.setTextSize(13f);
            tvSubtitle.setTextColor(0xFF9E9E9E);
            textCol.addView(tvSubtitle);

            row.addView(textCol);

            TextView tvChevron = new TextView(ctx);
            tvChevron.setText("›");
            tvChevron.setTextSize(20f);
            tvChevron.setTextColor(0xFF9E9E9E);
            row.addView(tvChevron);

            return row;
        }

        private void showSendReportDialog() {
            String errorLog = "tidak ada log";
            java.io.File f = logFile();
            if (f.exists()) {
                try {
                    String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()));
                    errorLog = raw.length() > 3000 ? raw.substring(raw.length() - 3000) : raw;
                    if (errorLog.isEmpty()) errorLog = "tidak ada log";
                } catch (Exception ignored) {}
            }

            StringBuilder overridesSb = new StringBuilder();
            java.util.Map<String, String> overrides = HookOverrideStore.getAllOverrides(requireContext());
            if (overrides.isEmpty()) {
                overridesSb.append("(tidak ada override)");
            } else {
                for (java.util.Map.Entry<String, String> e : overrides.entrySet()) {
                    overridesSb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
                }
            }

            String waRegular = "not installed";
            try {
                waRegular = requireContext().getPackageManager()
                        .getPackageInfo("com.whatsapp", 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}

            String waBusiness = "not installed";
            try {
                waBusiness = requireContext().getPackageManager()
                        .getPackageInfo("com.whatsapp.w4b", 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}

            String device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                    + " Android " + android.os.Build.VERSION.RELEASE;

            String preview = "=== WA Regular ===\n" + waRegular
                    + "\n\n=== WA Business ===\n" + waBusiness
                    + "\n\n=== WhatsVault ===\n" + BuildConfig.VERSION_NAME
                    + "\n\n=== Device ===\n" + device
                    + "\n\n=== Hook Overrides ===\n" + overridesSb.toString().trim()
                    + "\n\n=== Error Log ===\n" + errorLog;

            final String finalErrorLog = errorLog;
            final String finalWaVersion = waRegular + " / " + waBusiness;
            final String finalOverrides = overridesSb.toString().trim();

            android.content.res.Resources res = requireContext().getResources();
            float density = res.getDisplayMetrics().density;
            int dp8 = (int) (8 * density + 0.5f);

            ScrollView scrollView = new ScrollView(requireContext());
            TextView previewTv = new TextView(requireContext());
            previewTv.setText(preview);
            previewTv.setTextSize(11f);
            previewTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            previewTv.setPadding(dp8, dp8, dp8, dp8);
            scrollView.addView(previewTv);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (200 * density + 0.5f)));

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Kirim Report ke Developer")
                    .setMessage("Dengan mengirim, kamu setuju data di atas dikirim ke developer.")
                    .setView(scrollView)
                    .setPositiveButton("Kirim", (dialog, which) ->
                            TelegramReporter.INSTANCE.sendHookFixReport(
                                    finalOverrides.isEmpty() ? "-" : finalOverrides,
                                    device,
                                    finalWaVersion,
                                    finalErrorLog,
                                    () -> {
                                        new Handler(Looper.getMainLooper()).post(() ->
                                                Toast.makeText(requireContext(),
                                                        "Report terkirim!", Toast.LENGTH_SHORT).show());
                                        return kotlin.Unit.INSTANCE;
                                    },
                                    errMsg -> {
                                        new Handler(Looper.getMainLooper()).post(() ->
                                                Toast.makeText(requireContext(),
                                                        "Gagal kirim: " + errMsg, Toast.LENGTH_LONG).show());
                                        return kotlin.Unit.INSTANCE;
                                    }
                            ))
                    .setNegativeButton("Batal", null)
                    .show();
        }

        private SharedPreferences getWaePrefs() {
            return PreferenceManager.getDefaultSharedPreferences(requireContext());
        }

        private java.io.File logFile() {
            return new java.io.File(LOG_PATH);
        }
    }

    public static class DevLogFragment extends Fragment {

        private static final String LOG_PATH = "/data/data/com.mrksvt.waen/files/wae_dev_log.txt";

        private TextView logTextView;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable pollRunnable = this::pollLogs;
        private String rawLogContent = "";
        private int selectedFilterIndex = 0;
        private String customFilter = "";
        private android.widget.EditText customFilterEdit;
        private final Runnable debounceRunnable = this::applyFilter;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            Context ctx = requireContext();
            android.content.res.Resources res = ctx.getResources();
            float density = res.getDisplayMetrics().density;
            int dp8  = (int) (8  * density + 0.5f);
            int dp16 = (int) (16 * density + 0.5f);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp16, dp16, dp16, dp16);

            SwitchMaterial devEngSwitch = new SwitchMaterial(ctx);
            devEngSwitch.setText("DevEngineering");
            devEngSwitch.setChecked(getWaePrefs().getBoolean("dev_engineering", false));
            devEngSwitch.setOnCheckedChangeListener((btn, checked) ->
                    getWaePrefs().edit().putBoolean("dev_engineering", checked).apply());
            devEngSwitch.setPadding(0, 0, 0, dp8);
            root.addView(devEngSwitch);

            Button clearBtn = new Button(ctx);
            clearBtn.setText("Clear Log");
            clearBtn.setOnClickListener(v -> {
                logFile().delete();
                logTextView.setText("(belum ada log)");
            });

            Button copyBtn = new Button(ctx);
            copyBtn.setText("Copy Log");
            copyBtn.setOnClickListener(v -> {
                CharSequence text = logTextView.getText();
                if (text == null || text.toString().equals("(belum ada log)")) return;
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("wae_dev_log", text));
                android.widget.Toast.makeText(ctx, "Log disalin", android.widget.Toast.LENGTH_SHORT).show();
            });

            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams btnParam = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnRow.addView(clearBtn, btnParam);
            btnRow.addView(copyBtn, btnParam);
            root.addView(btnRow);

            android.widget.Spinner filterSpinner = new android.widget.Spinner(ctx);
            String[] filterOptions = {"Semua", "Klik View", "Error", "Send Button", "Custom..."};
            android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                    ctx, android.R.layout.simple_spinner_item, filterOptions);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            filterSpinner.setAdapter(spinnerAdapter);
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            spinnerParams.topMargin = dp8;
            root.addView(filterSpinner, spinnerParams);

            customFilterEdit = new android.widget.EditText(ctx);
            customFilterEdit.setHint("Kata kunci filter...");
            customFilterEdit.setSingleLine(true);
            customFilterEdit.setVisibility(android.view.View.GONE);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            editParams.topMargin = dp8;
            root.addView(customFilterEdit, editParams);

            filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    selectedFilterIndex = position;
                    customFilterEdit.setVisibility(position == 4 ? android.view.View.VISIBLE : android.view.View.GONE);
                    applyFilter();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

            customFilterEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    customFilter = s.toString();
                    handler.removeCallbacks(debounceRunnable);
                    handler.postDelayed(debounceRunnable, 300);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });

            ScrollView scrollView = new ScrollView(ctx);
            logTextView = new TextView(ctx);
            logTextView.setTextSize(10f);
            logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
            logTextView.setText("(belum ada log)");
            logTextView.setPadding(dp8, dp8, dp8, dp8);
            scrollView.addView(logTextView);
            root.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                        getParentFragmentManager().popBackStack();
                    } else {
                        requireActivity().finish();
                    }
                }
            });

            return root;
        }

        @Override
        public void onResume() {
            super.onResume();
            handler.postDelayed(pollRunnable, 500);
        }

        @Override
        public void onPause() {
            super.onPause();
            handler.removeCallbacks(pollRunnable);
        }

        private void pollLogs() {
            if (logTextView != null) {
                java.io.File f = logFile();
                if (f.exists()) {
                    try {
                        rawLogContent = new String(java.nio.file.Files.readAllBytes(f.toPath()));
                    } catch (Exception ignored) {}
                }
                applyFilter();
            }
            handler.postDelayed(pollRunnable, 500);
        }

        private void applyFilter() {
            if (logTextView == null) return;
            if (rawLogContent.isEmpty()) {
                logTextView.setText("(belum ada log)");
                return;
            }
            String[] lines = rawLogContent.split("\n", -1);
            StringBuilder filtered = new StringBuilder();
            for (String line : lines) {
                boolean match;
                switch (selectedFilterIndex) {
                    case 1:
                        match = line.contains("performClick") || line.contains("id=");
                        break;
                    case 2:
                        match = line.contains("Error") || line.contains("Exception");
                        break;
                    case 3:
                        match = line.contains("send") || line.contains("composer");
                        break;
                    case 4:
                        match = !customFilter.isEmpty() && line.toLowerCase().contains(customFilter.toLowerCase());
                        break;
                    default:
                        match = true;
                        break;
                }
                if (match) {
                    if (filtered.length() > 0) filtered.append("\n");
                    filtered.append(line);
                }
            }
            if (filtered.length() == 0) {
                logTextView.setText("(tidak ada log yang cocok)");
            } else {
                logTextView.setText(filtered.toString());
            }
        }

        private SharedPreferences getWaePrefs() {
            return PreferenceManager.getDefaultSharedPreferences(requireContext());
        }

        private java.io.File logFile() {
            return new java.io.File(LOG_PATH);
        }
    }

}