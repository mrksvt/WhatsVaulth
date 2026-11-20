package com.mrksvt.waen.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.ui.fragments.base.BackNavHelper;
import com.mrksvt.waen.xposed.core.RuntimeLogPolicy;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pengelola log runtime debug di /storage/emulated/0/WhatsVault/logs. */
public class RuntimeLogManagerFragment extends Fragment {
    private final Set<File> selected = new LinkedHashSet<>();
    private LinearLayout listContainer;
    private Button permissionButton;
    private Button deleteButton;
    private Button shareButton;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        int pad = dp(context, 16);
        int gap = dp(context, 8);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        SwitchMaterial enabled = new SwitchMaterial(context);
        enabled.setText("Rekam log runtime WhatsVault");
        enabled.setChecked(PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(RuntimeLogPolicy.PREF_KEY, true));
        enabled.setEnabled(BuildConfig.DEBUG);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(RuntimeLogPolicy.PREF_KEY, checked).apply();
            Toast.makeText(context,
                    "Berlaku setelah WhatsApp dimulai ulang", Toast.LENGTH_SHORT).show();
            if (checked) updatePermissionButton();
        });
        root.addView(enabled);

        TextView path = new TextView(context);
        path.setText("Lokasi: /storage/emulated/0/WhatsVault/logs");
        path.setTextSize(12f);
        path.setPadding(0, 0, 0, gap);
        root.addView(path);

        permissionButton = new Button(context);
        permissionButton.setText("Izinkan akses semua file");
        permissionButton.setOnClickListener(v -> requestAllFilesAccess());
        root.addView(permissionButton);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        deleteButton = new Button(context);
        deleteButton.setText("Hapus terpilih");
        deleteButton.setOnClickListener(v -> deleteSelected());
        shareButton = new Button(context);
        shareButton.setText("Bagikan terpilih");
        shareButton.setOnClickListener(v -> shareSelected());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(deleteButton, actionParams);
        actions.addView(shareButton, actionParams);
        root.addView(actions);

        TextView hint = new TextView(context);
        hint.setText("Pilih satu atau beberapa file log:");
        hint.setPadding(0, gap, 0, gap);
        root.addView(hint);

        emptyView = new TextView(context);
        emptyView.setText("Belum ada log runtime.");
        emptyView.setPadding(0, gap, 0, gap);
        root.addView(emptyView);

        ScrollView scroll = new ScrollView(context);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        updateActionButtons();
        BackNavHelper.install(this);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionButton();
        refreshLogs();
    }

    private void updatePermissionButton() {
        if (permissionButton == null) return;
        boolean needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager();
        permissionButton.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private File logDirectory() {
        return new File(Environment.getExternalStorageDirectory(),
                RuntimeLogPolicy.RELATIVE_DIRECTORY);
    }

    private void refreshLogs() {
        selected.clear();
        listContainer.removeAllViews();
        File[] files = logDirectory().listFiles(file -> file.isFile()
                && file.getName().startsWith("whatsvault-")
                && file.getName().endsWith(".log"));
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        emptyView.setVisibility(files.length == 0 ? View.VISIBLE : View.GONE);

        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault());
        for (File file : files) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(file.getName() + "\n"
                    + humanSize(file.length()) + " • "
                    + dateFormat.format(new Date(file.lastModified())));
            checkBox.setPadding(0, dp(requireContext(), 4), 0, dp(requireContext(), 4));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(file); else selected.remove(file);
                updateActionButtons();
            });
            listContainer.addView(checkBox);
        }
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean hasSelection = !selected.isEmpty();
        if (deleteButton != null) deleteButton.setEnabled(hasSelection);
        if (shareButton != null) shareButton.setEnabled(hasSelection);
    }

    private void deleteSelected() {
        int deleted = 0;
        for (File file : new ArrayList<>(selected)) {
            if (file.delete()) deleted++;
        }
        Toast.makeText(requireContext(), deleted + " log dihapus", Toast.LENGTH_SHORT).show();
        refreshLogs();
    }

    private void shareSelected() {
        if (selected.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        String authority = BuildConfig.APPLICATION_ID + ".fileprovider";
        for (File file : selected) {
            uris.add(FileProvider.getUriForFile(requireContext(), authority, file));
        }
        Intent intent = new Intent(uris.size() == 1
                ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        startActivity(Intent.createChooser(intent, "Bagikan log WhatsVault"));
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
