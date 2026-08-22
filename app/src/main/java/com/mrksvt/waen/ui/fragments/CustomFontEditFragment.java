package com.mrksvt.waen.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.room.Room;

import com.google.android.material.textfield.TextInputEditText;
import com.mrksvt.waen.R;
import com.mrksvt.waen.xposed.core.db.CustomFontDatabase;
import com.mrksvt.waen.xposed.core.db.CustomFontPresetEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class CustomFontEditFragment extends Fragment {

    private static final String ARG_PRESET_ID = "preset_id";
    private static final int REQUEST_PICK_FONT = 201;

    private long presetId = -1L;
    private String source = "bundled";
    private String bundledName = "";
    private String customPath = "";

    private TextInputEditText etName;
    private Spinner spSource;
    private Spinner spBundled;
    private View layoutBundled;
    private View layoutCustom;
    private TextView tvCustomPath;
    private TextView tvPreview;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static CustomFontEditFragment newInstance(long presetId) {
        CustomFontEditFragment fragment = new CustomFontEditFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PRESET_ID, presetId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            presetId = getArguments().getLong(ARG_PRESET_ID, -1L);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_font_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etName = view.findViewById(R.id.et_font_name);
        spSource = view.findViewById(R.id.sp_font_source);
        spBundled = view.findViewById(R.id.sp_font_bundled);
        layoutBundled = view.findViewById(R.id.layout_bundled);
        layoutCustom = view.findViewById(R.id.layout_custom);
        tvCustomPath = view.findViewById(R.id.tv_custom_path);
        tvPreview = view.findViewById(R.id.tv_font_preview);
        Button btnPick = view.findViewById(R.id.btn_pick_font);
        Button btnSave = view.findViewById(R.id.btn_save_font);

        String[] bundledEntries = getResources().getStringArray(R.array.custom_font_bundled_entries);
        String[] bundledValues = getResources().getStringArray(R.array.custom_font_bundled_values);
        ArrayAdapter<String> bundledAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, bundledEntries);
        bundledAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBundled.setAdapter(bundledAdapter);

        spSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                source = position == 0 ? "bundled" : "custom";
                layoutBundled.setVisibility("bundled".equals(source) ? View.VISIBLE : View.GONE);
                layoutCustom.setVisibility("custom".equals(source) ? View.VISIBLE : View.GONE);
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spBundled.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                bundledName = bundledValues[position];
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnPick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype",
                            "application/vnd.ms-opentype", "application/octet-stream"});
            try {
                startActivityForResult(Intent.createChooser(intent, getString(R.string.custom_font_pick_file)), REQUEST_PICK_FONT);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No font picker app found", Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v -> savePreset());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager mgr = getParentFragment() != null
                        ? getParentFragment().getChildFragmentManager()
                        : getParentFragmentManager();
                if (mgr.getBackStackEntryCount() > 0) {
                    mgr.popBackStack();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (presetId != -1L) {
            loadPreset();
        }
    }

    private void loadPreset() {
        Executors.newSingleThreadExecutor().execute(() -> {
            CustomFontDatabase db = openDb();
            CustomFontPresetEntity preset = db.customFontPresetDao().getById(presetId);
            db.close();
            if (preset == null) return;
            mainHandler.post(() -> {
                if (getView() == null) return;
                etName.setText(preset.getName());
                source = preset.getSource() != null ? preset.getSource() : "bundled";
                bundledName = preset.getBundledName() != null ? preset.getBundledName() : "";
                customPath = preset.getCustomPath() != null ? preset.getCustomPath() : "";
                spSource.setSelection("custom".equals(source) ? 1 : 0);
                if (!bundledName.isEmpty()) {
                    String[] values = getResources().getStringArray(R.array.custom_font_bundled_values);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(bundledName)) {
                            spBundled.setSelection(i);
                            break;
                        }
                    }
                }
                layoutBundled.setVisibility("bundled".equals(source) ? View.VISIBLE : View.GONE);
                layoutCustom.setVisibility("custom".equals(source) ? View.VISIBLE : View.GONE);
                if (!customPath.isEmpty()) {
                    tvCustomPath.setText(new File(customPath).getName());
                    tvCustomPath.setVisibility(View.VISIBLE);
                }
                updatePreview();
            });
        });
    }

    private CustomFontDatabase openDb() {
        return Room.databaseBuilder(requireContext(), CustomFontDatabase.class, "CustomFont.db")
                .allowMainThreadQueries()
                .build();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FONT && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            copyFontToPrivateDir(uri);
        }
    }

    private void copyFontToPrivateDir(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File fontsDir = new File(requireContext().getFilesDir(), "custom_fonts");
                if (!fontsDir.exists() && !fontsDir.mkdirs()) return;
                String ext = ".ttf";
                String uriPath = uri.toString();
                int dotIdx = uriPath.lastIndexOf('.');
                if (dotIdx >= 0) {
                    String e = uriPath.substring(dotIdx).toLowerCase();
                    if (e.contains("otf")) ext = ".otf";
                    else if (e.contains("ttf")) ext = ".ttf";
                }
                String fileName = presetId != -1L ? "font_" + presetId : "font_" + System.currentTimeMillis();
                File destFile = new File(fontsDir, fileName + ext);

                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(destFile)) {
                    if (in == null) return;
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }
                customPath = destFile.getAbsolutePath();
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    tvCustomPath.setText(destFile.getName());
                    tvCustomPath.setVisibility(View.VISIBLE);
                    updatePreview();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(requireContext(),
                        "Failed to copy font: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updatePreview() {
        Executors.newSingleThreadExecutor().execute(() -> {
            final Typeface tf = loadTypeface();
            mainHandler.post(() -> {
                if (getView() == null) return;
                tvPreview.setTypeface(tf);
            });
        });
    }

    private Typeface loadTypeface() {
        try {
            if ("custom".equals(source) && !customPath.isEmpty()) {
                File f = new File(customPath);
                if (f.exists()) return Typeface.createFromFile(f);
            }
            if (!bundledName.isEmpty()) {
                return Typeface.createFromAsset(requireContext().getAssets(), "fonts/" + bundledName);
            }
        } catch (Exception ignored) {}
        return Typeface.DEFAULT;
    }

    private void savePreset() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("custom".equals(source) && TextUtils.isEmpty(customPath)) {
            Toast.makeText(requireContext(), "Pick a font file first", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("bundled".equals(source) && TextUtils.isEmpty(bundledName)) {
            Toast.makeText(requireContext(), "Select a bundled font", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            CustomFontDatabase db = openDb();
            if (presetId != -1L) {
                CustomFontPresetEntity existing = db.customFontPresetDao().getById(presetId);
                if (existing != null) {
                    db.customFontPresetDao().update(new CustomFontPresetEntity(
                            existing.getId(), name, source, bundledName, customPath));
                }
            } else {
                db.customFontPresetDao().insert(new CustomFontPresetEntity(
                        null, name, source, bundledName, customPath));
            }
            db.close();
            mainHandler.post(() -> {
                if (getView() == null) return;
                Toast.makeText(requireContext(), "Font saved", Toast.LENGTH_SHORT).show();
                FragmentManager mgr = getParentFragment() != null
                        ? getParentFragment().getChildFragmentManager()
                        : getParentFragmentManager();
                if (mgr.getBackStackEntryCount() > 0) {
                    mgr.popBackStack();
                }
            });
        });
    }
}
