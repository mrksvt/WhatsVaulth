package com.mrksvt.waen.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.room.Room;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.mrksvt.waen.R;
import com.mrksvt.waen.xposed.core.db.CustomFontDatabase;
import com.mrksvt.waen.xposed.core.db.CustomFontPresetEntity;
import com.mrksvt.waen.xposed.core.db.MessageHistoryDatabase;
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity;
import com.mrksvt.waen.xposed.core.db.entity.ThemePresetEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ThemeBuilderEditFragment extends Fragment {

    private static final String ARG_PRESET_ID = "preset_id";

    private long presetId = -1L;
    private TextInputEditText etName;
    private SwitchMaterial switchMonet;
    private View swatchPrimary;
    private View swatchBackground;
    private View swatchText;
    private Spinner spinnerTick;
    private Spinner spinnerFont;

    private int primaryColor = 0xFF00A884;
    private int backgroundColor = 0xFFECE5DD;
    private int textColor = 0xFF111B21;

    private final List<CustomTickPresetEntity> ticks = new ArrayList<>();
    private final List<CustomFontPresetEntity> fonts = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ThemeBuilderEditFragment newInstance(long presetId) {
        ThemeBuilderEditFragment fragment = new ThemeBuilderEditFragment();
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
        return inflater.inflate(R.layout.fragment_theme_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etName = view.findViewById(R.id.et_theme_name);
        switchMonet = view.findViewById(R.id.switch_monet);
        swatchPrimary = view.findViewById(R.id.swatch_primary);
        swatchBackground = view.findViewById(R.id.swatch_background);
        swatchText = view.findViewById(R.id.swatch_text);
        spinnerTick = view.findViewById(R.id.spinner_tick);
        spinnerFont = view.findViewById(R.id.spinner_font);

        view.findViewById(R.id.row_primary).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), primaryColor,
                        color -> {
                            primaryColor = color;
                            swatchPrimary.setBackgroundColor(color);
                        }).show());

        view.findViewById(R.id.row_background).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), backgroundColor,
                        color -> {
                            backgroundColor = color;
                            swatchBackground.setBackgroundColor(color);
                        }).show());

        view.findViewById(R.id.row_text).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), textColor,
                        color -> {
                            textColor = color;
                            swatchText.setBackgroundColor(color);
                        }).show());

        view.findViewById(R.id.btn_save_theme).setOnClickListener(v -> savePreset());

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

        loadDropdowns();
    }

    private void loadDropdowns() {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            List<CustomTickPresetEntity> tickList = db.customTickPresetDao().getAll();
            db.close();
            final List<CustomFontPresetEntity> fontList = loadFontList();

            mainHandler.post(() -> {
                if (getView() == null) return;
                ticks.clear();
                ticks.addAll(tickList);
                fonts.clear();
                fonts.addAll(fontList);
                setupSpinners();
                if (presetId != -1L) loadPreset();
            });
        });
    }

    private List<CustomFontPresetEntity> loadFontList() {
        try {
            CustomFontDatabase fdb = Room.databaseBuilder(requireContext(),
                    CustomFontDatabase.class, "CustomFont.db")
                    .allowMainThreadQueries()
                    .build();
            List<CustomFontPresetEntity> result = fdb.customFontPresetDao().getAll();
            fdb.close();
            return result;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private void setupSpinners() {
        List<String> tickNames = new ArrayList<>();
        tickNames.add(getString(R.string.theme_builder_none));
        for (CustomTickPresetEntity t : ticks) tickNames.add(t.getName());
        ArrayAdapter<String> tickAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, tickNames);
        tickAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTick.setAdapter(tickAdapter);
        spinnerTick.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {}

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        List<String> fontNames = new ArrayList<>();
        fontNames.add(getString(R.string.theme_builder_none));
        for (CustomFontPresetEntity f : fonts) fontNames.add(f.getName());
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, fontNames);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFont.setAdapter(fontAdapter);
    }

    private void loadPreset() {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            ThemePresetEntity preset = db.themePresetDao().getById(presetId);
            db.close();
            if (preset == null) return;
            mainHandler.post(() -> {
                if (getView() == null) return;
                etName.setText(preset.getName());
                switchMonet.setChecked(preset.getUseMonet());
                primaryColor = preset.getPrimaryColor();
                backgroundColor = preset.getBackgroundColor();
                textColor = preset.getTextColor();
                swatchPrimary.setBackgroundColor(primaryColor);
                swatchBackground.setBackgroundColor(backgroundColor);
                swatchText.setBackgroundColor(textColor);
                if (preset.getTickPresetId() != null) {
                    for (int i = 0; i < ticks.size(); i++) {
                        if (ticks.get(i).getId() != null
                                && ticks.get(i).getId().equals(preset.getTickPresetId())) {
                            spinnerTick.setSelection(i + 1);
                            break;
                        }
                    }
                }
                if (preset.getFontPresetId() != null) {
                    for (int i = 0; i < fonts.size(); i++) {
                        if (fonts.get(i).getId() != null
                                && fonts.get(i).getId().equals(preset.getFontPresetId())) {
                            spinnerFont.setSelection(i + 1);
                            break;
                        }
                    }
                }
            });
        });
    }

    private MessageHistoryDatabase openDb() {
        return Room.databaseBuilder(requireContext(), MessageHistoryDatabase.class, "MessageHistory.db")
                .allowMainThreadQueries()
                .addMigrations(MessageHistoryDatabase.Companion.getMIGRATION_6_7(), MessageHistoryDatabase.Companion.getMIGRATION_7_8(), MessageHistoryDatabase.Companion.getMIGRATION_8_9(), MessageHistoryDatabase.Companion.getMIGRATION_9_10())
                .build();
    }

    private void savePreset() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        Long tickId = spinnerTick.getSelectedItemPosition() > 0
                ? ticks.get(spinnerTick.getSelectedItemPosition() - 1).getId() : null;
        Long fontId = spinnerFont.getSelectedItemPosition() > 0
                ? fonts.get(spinnerFont.getSelectedItemPosition() - 1).getId() : null;

        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            if (presetId != -1L) {
                ThemePresetEntity existing = db.themePresetDao().getById(presetId);
                if (existing != null) {
                    db.themePresetDao().update(new ThemePresetEntity(
                            existing.getId(), name, primaryColor, textColor, backgroundColor,
                            switchMonet.isChecked(), tickId, fontId));
                }
            } else {
                db.themePresetDao().insert(new ThemePresetEntity(
                        null, name, primaryColor, textColor, backgroundColor,
                        switchMonet.isChecked(), tickId, fontId));
            }
            db.close();
            mainHandler.post(() -> {
                if (getView() == null) return;
                Toast.makeText(requireContext(), "Theme saved", Toast.LENGTH_SHORT).show();
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
