package com.mrksvt.waen.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.mrksvt.waen.R;
import com.mrksvt.waen.services.RootScreenRecord;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class CallRecordingSettingsActivity extends AppCompatActivity {

    private static final String TAG = "WaEnhancer";
    private SharedPreferences prefs;
    private RadioGroup radioGroupMode;
    private RadioButton radioRoot;
    private RadioButton radioNonRoot;
    private RadioButton radioScreenProjection;
    private RadioButton radioScreenRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_recording_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.call_recording_settings);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        radioGroupMode = findViewById(R.id.radio_group_mode);
        radioRoot = findViewById(R.id.radio_root);
        radioNonRoot = findViewById(R.id.radio_non_root);

        // Load saved preference
        boolean useRoot = prefs.getBoolean("call_recording_use_root", false);
        Log.d(TAG, "Loaded call_recording_use_root: " + useRoot);
        
        if (useRoot) {
            radioRoot.setChecked(true);
        } else {
            radioNonRoot.setChecked(true);
        }

        // Direct click listeners on radio buttons
        radioRoot.setOnClickListener(v -> {
            Log.d(TAG, "Root mode clicked");
            radioRoot.setChecked(true);
            radioNonRoot.setChecked(false);
            Toast.makeText(this, "Checking root access...", Toast.LENGTH_SHORT).show();
            checkRootAccess();
        });
        
        radioNonRoot.setOnClickListener(v -> {
            Log.d(TAG, "Non-root mode clicked");
            radioNonRoot.setChecked(true);
            radioRoot.setChecked(false);
            boolean saved = prefs.edit().putBoolean("call_recording_use_root", false).commit();
            Log.d(TAG, "Saved non-root preference: " + saved);
            Toast.makeText(this, R.string.non_root_mode_enabled, Toast.LENGTH_SHORT).show();
        });

        setupScreenRecordMode();
    }

    private void setupScreenRecordMode() {
        radioScreenProjection = findViewById(R.id.radio_screen_projection);
        radioScreenRoot = findViewById(R.id.radio_screen_root);

        String mode = prefs.getString("call_recording_screen_mode", RootScreenRecord.MODE_PROJECTION);
        radioScreenProjection.setChecked(!RootScreenRecord.MODE_ROOT.equals(mode));

        radioScreenProjection.setOnClickListener(v -> {
            radioScreenProjection.setChecked(true);
            radioScreenRoot.setChecked(false);
            prefs.edit().putString("call_recording_screen_mode", RootScreenRecord.MODE_PROJECTION).apply();
        });

        radioScreenRoot.setOnClickListener(v -> {
            radioScreenRoot.setChecked(true);
            radioScreenProjection.setChecked(false);
            prefs.edit().putString("call_recording_screen_mode", RootScreenRecord.MODE_ROOT).apply();
            Toast.makeText(this, R.string.screen_mode_root, Toast.LENGTH_SHORT).show();
        });

        // Cek root memanggil shell, jadi harus di luar main thread.
        new Thread(() -> {
            boolean available = RootScreenRecord.isAvailable();
            runOnUiThread(() -> {
                if (available) {
                    radioScreenRoot.setChecked(RootScreenRecord.MODE_ROOT.equals(mode));
                    return;
                }
                radioScreenProjection.setChecked(true);
                radioScreenRoot.setEnabled(false);
                radioScreenRoot.setVisibility(android.view.View.GONE);
            });
        }).start();
    }

    private void checkRootAccess() {
        new Thread(() -> {
            boolean hasRoot = false;
            String rootOutput = "";
            
            try {
                Log.d(TAG, "Executing su command...");
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();
                
                // Read output
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                rootOutput = sb.toString();
                
                int exitCode = process.waitFor();
                Log.d(TAG, "Root check exit code: " + exitCode + ", output: " + rootOutput);
                
                hasRoot = (exitCode == 0 && rootOutput.contains("uid=0"));
            } catch (Exception e) {
                Log.e(TAG, "Root check exception: " + e.getMessage());
                hasRoot = false;
            }

            final boolean rootGranted = hasRoot;
            final String output = rootOutput;
            
            runOnUiThread(() -> {
                if (rootGranted) {
                    boolean saved = prefs.edit().putBoolean("call_recording_use_root", true).commit();
                    Log.d(TAG, "Root granted, saved preference: " + saved);
                    Toast.makeText(this, R.string.root_access_granted, Toast.LENGTH_SHORT).show();
                } else {
                    boolean saved = prefs.edit().putBoolean("call_recording_use_root", false).commit();
                    Log.d(TAG, "Root denied, saved preference: " + saved + ", output: " + output);
                    radioNonRoot.setChecked(true);
                    Toast.makeText(this, R.string.root_access_denied, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

