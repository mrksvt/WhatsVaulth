package com.mrksvt.waen.ui.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrksvt.waen.App;
import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.R;
import com.mrksvt.waen.activities.MainActivity;
import com.mrksvt.waen.adapter.LogLineAdapter;
import com.mrksvt.waen.databinding.DialogDiagnosticsLogBinding;
import com.mrksvt.waen.databinding.FragmentHomeBinding;
import com.mrksvt.waen.ui.fragments.base.BaseFragment;
import com.mrksvt.waen.utils.FilePicker;
import com.mrksvt.waen.utils.RootDiagnostics;
import com.mrksvt.waen.xposed.core.FeatureLoader;
import com.mrksvt.waen.xposed.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.UnknownHostException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import rikka.core.util.IOUtils;

public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var intentFilter = new IntentFilter(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    if (FeatureLoader.PACKAGE_WPP.equals(intent.getStringExtra("PKG")))
                        receiverBroadcastWpp(context, intent);
                    else
                        receiverBroadcastBusiness(context, intent);
                } catch (Exception ignored) {
                }
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        checkStateWpp(requireActivity());

        binding.rebootBtn.setOnClickListener(view -> {
            animateClick(view);
            App.instance.restartApp(FeatureLoader.PACKAGE_WPP);
            disableWpp(requireActivity());
        });

        binding.toggleWppCardsBtn.setOnClickListener(view -> {
            animateClick(view);
            boolean isExpanded = binding.status2.getVisibility() == View.VISIBLE;
            int targetVisibility = isExpanded ? View.GONE : View.VISIBLE;
            binding.status2.setVisibility(targetVisibility);
            binding.status3.setVisibility(targetVisibility);
            binding.toggleWppCardsBtn.animate()
                .rotation(isExpanded ? 0f : 180f)
                .setDuration(250)
                .start();
        });

        binding.rebootBtn2.setOnClickListener(view -> {
            animateClick(view);
            App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS);
            disableBusiness(requireActivity());
        });

        binding.exportBtn.setOnClickListener(view -> {
            animateClick(view);
            saveConfigs(this.getContext());
        });

        binding.importBtn.setOnClickListener(view -> {
            animateClick(view);
            importConfigs(this.getContext());
        });

        binding.resetBtn.setOnClickListener(view -> {
            animateClick(view);
            resetConfigs(this.getContext());
        });



        binding.diagBtn.setOnClickListener(view -> {
            animateClick(view);

        });

        // Always hide update card (AntiUpdater removed)

        startCardAnimations();

        return binding.getRoot();
    }

    private void startCardAnimations() {
        var context = getContext();
        if (context == null) return;
        var slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_up);
        var fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in);

        binding.status.startAnimation(slideUp);

        binding.infoCard.postDelayed(() -> {
            if (!isAdded() || binding == null) return;
            binding.infoCard.startAnimation(fadeIn);
        }, 300);

    }

    private void animateClick(View view) {
        var scaleIn = AnimationUtils.loadAnimation(getContext(), R.anim.scale_in);
        view.startAnimation(scaleIn);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastBusiness(Context context, Intent intent) {
        binding.statusTitle3.setText(R.string.business_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_business));
        if (version != null && supported_list.stream().anyMatch(s -> version.startsWith(s.replace(".xx", "")))) {
            binding.statusSummary3.setText(getString(R.string.version_s, version));
            binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_success);
        } else {
            binding.statusSummary3.setText(getString(R.string.version_s_not_listed, version));
            binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_warning);
        }
        binding.rebootBtn2.setVisibility(View.VISIBLE);
        binding.statusSummary3.setVisibility(View.VISIBLE);
        binding.statusIcon3.setImageResource(R.drawable.ic_round_check_circle_24);
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastWpp(Context context, Intent intent) {
        binding.statusTitle2.setText(R.string.whatsapp_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_wpp));

        if (version != null && supported_list.stream().anyMatch(s -> version.startsWith(s.replace(".xx", "")))) {
            binding.statusSummary1.setText(getString(R.string.version_s, version));
            binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_success);
        } else {
            binding.statusSummary1.setText(getString(R.string.version_s_not_listed, version));
            binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_warning);
        }
        binding.rebootBtn.setVisibility(View.VISIBLE);
        binding.statusSummary1.setVisibility(View.VISIBLE);
        binding.statusIcon2.setImageResource(R.drawable.ic_round_check_circle_24);
    }

    private void resetConfigs(Context context) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.getAll().forEach((key, value) -> prefs.edit().remove(key).apply());
        App.instance.restartApp(FeatureLoader.PACKAGE_WPP);
        App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS);
        Utils.showToast(context.getString(R.string.configs_reset), Toast.LENGTH_SHORT);
    }

    private static @NonNull JSONObject getJsonObject(SharedPreferences prefs) throws JSONException {
        var entries = prefs.getAll();
        var JSOjsonObject = new JSONObject();
        for (var entry : entries.entrySet()) {
            var type = new JSONObject();
            var keyValue = entry.getValue();
            if (keyValue instanceof HashSet<?> hashSet) {
                keyValue = new JSONArray(new ArrayList<>(hashSet));
            }
            type.put("type", keyValue.getClass().getSimpleName());
            type.put("value", keyValue);
            JSOjsonObject.put(entry.getKey(), type);
        }
        return JSOjsonObject;
    }

    private void saveConfigs(Context context) {
        FilePicker.setOnUriPickedListener((uri) -> {
            try {
                try (var output = context.getContentResolver().openOutputStream(uri)) {
                    var prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    var JSOjsonObject = getJsonObject(prefs);
                    Objects.requireNonNull(output).write(JSOjsonObject.toString(4).getBytes());
                }
                Toast.makeText(context, context.getString(R.string.configs_saved), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        String formattedDate = dateFormat.format(new Date());
        FilePicker.fileSalve.launch("wpp_enhacer_configs_" + formattedDate + ".json");
    }

    private void importConfigs(Context context) {
        FilePicker.setOnUriPickedListener((uri) -> {
            try {
                try (var input = context.getContentResolver().openInputStream(uri)) {
                    var data = IOUtils.toString(input);
                    var prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    var jsonObject = new JSONObject(data);
                    prefs.getAll().forEach((key, value) -> prefs.edit().remove(key).apply());
                    var key = jsonObject.keys();
                    while (key.hasNext()) {
                        var keyName = key.next();
                        var value = jsonObject.get(keyName);
                        var type = value.getClass().getSimpleName();
                        if (value instanceof JSONObject valueJson) {
                            value = valueJson.get("value");
                            type = valueJson.getString("type");
                        }

                        if (type.equals(JSONArray.class.getSimpleName())) {
                            var jsonArray = (JSONArray) value;
                            HashSet<String> hashSet = new HashSet<>();
                            for (var i = 0; i < jsonArray.length(); i++) {
                                hashSet.add(jsonArray.getString(i));
                            }
                            prefs.edit().putStringSet(keyName, hashSet).apply();
                        } else if (type.equals(String.class.getSimpleName())) {
                            prefs.edit().putString(keyName, (String) value).apply();
                        } else if (type.equals(Boolean.class.getSimpleName())) {
                            prefs.edit().putBoolean(keyName, (boolean) value).apply();
                        } else if (type.equals(Integer.class.getSimpleName())) {
                            prefs.edit().putInt(keyName, (int) value).apply();
                        } else if (type.equals(Long.class.getSimpleName())) {
                            prefs.edit().putLong(keyName, (long) value).apply();
                        } else if (type.equals(Double.class.getSimpleName())) {
                            prefs.edit().putFloat(keyName, Float.parseFloat(String.valueOf(value))).apply();
                        } else if (type.equals(Float.class.getSimpleName())) {
                            prefs.edit().putFloat(keyName, Float.parseFloat(String.valueOf(value))).apply();
                        }
                    }
                }
                Toast.makeText(context, context.getString(R.string.configs_imported), Toast.LENGTH_SHORT).show();
                App.instance.restartApp(FeatureLoader.PACKAGE_WPP);
                App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS);
            } catch (Exception e) {
                Log.e("importConfigs", e.getMessage(), e);
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        FilePicker.fileCapture.launch(new String[]{"application/json"});
    }

    @SuppressLint("StringFormatInvalid")
    private void checkStateWpp(FragmentActivity activity) {

        if (MainActivity.isXposedEnabled()) {
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24);
            binding.statusTitle.setText(R.string.module_enabled);
            binding.statusSummary.setText(String.format(getString(R.string.version_s), BuildConfig.VERSION_NAME));
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.gradient_success);
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            binding.statusTitle.setText(R.string.module_disabled);
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.gradient_error);
            binding.statusSummary.setVisibility(View.GONE);
        }
        if (isInstalled(FeatureLoader.PACKAGE_WPP) && App.isOriginalPackage()) {
            disableWpp(activity);
        }
        checkWpp(activity);
        binding.deviceName.setText(Build.MANUFACTURER);
        binding.sdk.setText(String.valueOf(Build.VERSION.SDK_INT));
        binding.modelName.setText(Build.DEVICE);
        if (App.isOriginalPackage()) {
            binding.listWpp.setText(Arrays.toString(activity.getResources().getStringArray(R.array.supported_versions_wpp)));
        } else {
            binding.listWppTitle.setVisibility(View.GONE);
            binding.listWpp.setVisibility(View.GONE);
        }
        binding.listBusiness.setText(Arrays.toString(activity.getResources().getStringArray(R.array.supported_versions_business)));
    }

    private boolean isInstalled(String packageWpp) {
        try {
            App.instance.getPackageManager().getPackageInfo(packageWpp, 0);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private void disableBusiness(FragmentActivity activity) {
        binding.statusIcon3.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle3.setText(R.string.business_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_error);
        binding.statusSummary3.setVisibility(View.GONE);
        binding.rebootBtn2.setVisibility(View.GONE);
    }

    private void disableWpp(FragmentActivity activity) {
        binding.statusIcon2.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle2.setText(R.string.whatsapp_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_error);
        binding.statusSummary1.setVisibility(View.GONE);
        binding.rebootBtn.setVisibility(View.GONE);
    }

    private static void checkWpp(FragmentActivity activity) {
        Intent checkWpp = new Intent(BuildConfig.APPLICATION_ID + ".CHECK_WPP");
        activity.sendBroadcast(checkWpp);
    }

}
