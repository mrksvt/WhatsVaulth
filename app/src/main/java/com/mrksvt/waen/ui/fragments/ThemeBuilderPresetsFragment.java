package com.mrksvt.waen.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.R;
import com.mrksvt.waen.xposed.core.db.CustomFontDatabase;
import com.mrksvt.waen.xposed.core.db.CustomFontPresetEntity;
import com.mrksvt.waen.xposed.core.db.MessageHistoryDatabase;
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity;
import com.mrksvt.waen.xposed.core.db.entity.ThemePresetEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ThemeBuilderPresetsFragment extends Fragment {

    private RecyclerView rvThemes;
    private FloatingActionButton fabAdd;
    private TextView tvEmpty;
    private List<ThemePresetEntity> themes = new ArrayList<>();
    private ThemesAdapter adapter;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_theme_presets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvThemes = view.findViewById(R.id.rv_themes);
        fabAdd = view.findViewById(R.id.fab_add_theme);
        tvEmpty = view.findViewById(R.id.tv_empty);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        adapter = new ThemesAdapter();
        rvThemes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvThemes.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> navigateTo(new ThemeBuilderEditFragment()));

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

        loadThemes();
    }

    private MessageHistoryDatabase openDb() {
        return Room.databaseBuilder(requireContext(), MessageHistoryDatabase.class, "MessageHistory.db")
                .allowMainThreadQueries()
                .addMigrations(MessageHistoryDatabase.Companion.getMIGRATION_6_7(), MessageHistoryDatabase.Companion.getMIGRATION_7_8(), MessageHistoryDatabase.Companion.getMIGRATION_8_9(), MessageHistoryDatabase.Companion.getMIGRATION_9_10())
                .build();
    }

    private void loadThemes() {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            seedBuiltinThemes(db);
            List<ThemePresetEntity> result = db.themePresetDao().getAll();
            db.close();
            mainHandler.post(() -> {
                if (getView() == null) return;
                themes.clear();
                themes.addAll(result);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(themes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private static class BuiltinTheme {
        final String name;
        final int primary;
        final int background;
        final int text;
        final boolean monet;

        BuiltinTheme(String name, int primary, int background, int text, boolean monet) {
            this.name = name;
            this.primary = primary;
            this.background = background;
            this.text = text;
            this.monet = monet;
        }
    }

    private static final BuiltinTheme[] BUILTIN_THEMES = {
            new BuiltinTheme("Default", 0xFF00A884, 0xFFECE5DD, 0xFF111B21, false),
            new BuiltinTheme("Blue", 0xFF0B57D0, 0xFFE8F0FE, 0xFF1F1F1F, false),
            new BuiltinTheme("Dark", 0xFF0B141A, 0xFF111B21, 0xFFE9EDEF, false),
            new BuiltinTheme("Pink", 0xFFD81B60, 0xFFFCE4EC, 0xFF1F1F1F, false),
            new BuiltinTheme("Purple", 0xFF6750A4, 0xFFEDE7F6, 0xFF1F1F1F, false),
            new BuiltinTheme("Ocean", 0xFF00696D, 0xFFE0F7FA, 0xFF1F1F1F, false),
            new BuiltinTheme("Monet", 0xFF00A884, 0xFFECE5DD, 0xFF111B21, true),
    };

    private void seedBuiltinThemes(MessageHistoryDatabase db) {
        try {
            List<ThemePresetEntity> existing = db.themePresetDao().getAll();
            for (BuiltinTheme theme : BUILTIN_THEMES) {
                boolean exists = false;
                for (ThemePresetEntity e : existing) {
                    if (theme.name.equals(e.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;
                db.themePresetDao().insert(new ThemePresetEntity(
                        null, theme.name, theme.primary, theme.text, theme.background, theme.monet, null, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateTo(Fragment fragment) {
        requireParentFragment().getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.frag_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    class ThemesAdapter extends RecyclerView.Adapter<ThemesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvActive;
            View swatchPrimary;
            View swatchBackground;
            View swatchText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_theme_name);
                tvActive = itemView.findViewById(R.id.tv_theme_active);
                swatchPrimary = itemView.findViewById(R.id.swatch_primary);
                swatchBackground = itemView.findViewById(R.id.swatch_background);
                swatchText = itemView.findViewById(R.id.swatch_text);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_theme_preset, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ThemePresetEntity preset = themes.get(position);
            holder.tvName.setText(preset.getName());
            holder.swatchPrimary.setBackgroundColor(preset.getPrimaryColor());
            holder.swatchBackground.setBackgroundColor(preset.getBackgroundColor());
            holder.swatchText.setBackgroundColor(preset.getTextColor());

            long activeId = prefs.getLong("theme_builder_active_preset_id", -1L);
            Long presetId = preset.getId();
            holder.tvActive.setVisibility(
                    presetId != null && presetId == activeId ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> showOptionsDialog(preset));
        }

        @Override
        public int getItemCount() {
            return themes.size();
        }
    }

    private void showOptionsDialog(ThemePresetEntity preset) {
        List<String> options = new ArrayList<>();
        options.add("Set Active");
        if (BuildConfig.DONATUR) {
            options.add("Edit");
            options.add("Delete");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(preset.getName())
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) { // Set Active
                        Long id = preset.getId();
                        if (id != null) {
                            prefs.edit().putLong("theme_builder_active_preset_id", id).apply();
                            applyThemePreset(preset);
                        }
                        adapter.notifyDataSetChanged();
                    } else if (which == 1 && BuildConfig.DONATUR) { // Edit
                        Long editId = preset.getId();
                        if (editId != null) {
                            navigateTo(ThemeBuilderEditFragment.newInstance(editId));
                        }
                    } else if (which == 2 && BuildConfig.DONATUR) { // Delete
                        showDeleteConfirm(preset);
                    }
                })
                .show();
    }

    private void applyThemePreset(ThemePresetEntity preset) {
        if (preset.getUseMonet()) {
            prefs.edit()
                    .putBoolean("changecolor", true)
                    .putString("changecolor_mode", "monet")
                    .apply();
        } else {
            prefs.edit()
                    .putBoolean("changecolor", true)
                    .putString("changecolor_mode", "manual")
                    .putInt("primary_color", preset.getPrimaryColor())
                    .putInt("text_color", preset.getTextColor())
                    .putInt("background_color", preset.getBackgroundColor())
                    .apply();
        }

        // Terapkan tick preset jika dipilih
        Long tickId = preset.getTickPresetId();
        if (tickId != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                MessageHistoryDatabase db = openDb();
                CustomTickPresetEntity tick = db.customTickPresetDao().getById(tickId);
                db.close();
                if (tick != null) {
                    prefs.edit().putLong("custom_tick_active_preset_id", tickId).apply();
                    prefs.edit().putString("custom_tick_active_preset_json", tickToJson(tick)).apply();
                }
            });
        }

        // Terapkan font preset jika dipilih
        Long fontId = preset.getFontPresetId();
        if (fontId != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    CustomFontDatabase db = Room.databaseBuilder(requireContext(),
                            CustomFontDatabase.class, "CustomFont.db")
                            .allowMainThreadQueries()
                            .build();
                    CustomFontPresetEntity font = db.customFontPresetDao().getById(fontId);
                    db.close();
                    if (font != null) {
                        prefs.edit().putLong("custom_font_active_preset_id", fontId).apply();
                        prefs.edit().putString("custom_font_active_preset_json", fontToJson(font)).apply();
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    private String tickToJson(CustomTickPresetEntity tick) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", tick.getId() != null ? tick.getId() : -1);
            json.put("name", tick.getName() != null ? tick.getName() : "");
            json.put("svgPending", readFileOrEmpty(tick.getSvgPendingPath()));
            json.put("svgSent", readFileOrEmpty(tick.getSvgSentPath()));
            json.put("svgDelivered", readFileOrEmpty(tick.getSvgDeliveredPath()));
            json.put("svgRead", readFileOrEmpty(tick.getSvgReadPath()));
            json.put("svgFailed", readFileOrEmpty(tick.getSvgFailedPath()));
            json.put("colorPending", tick.getColorPending());
            json.put("colorSent", tick.getColorSent());
            json.put("colorDelivered", tick.getColorDelivered());
            json.put("colorRead", tick.getColorRead());
            json.put("colorFailed", tick.getColorFailed());
            return json.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String fontToJson(CustomFontPresetEntity font) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", font.getId() != null ? font.getId() : -1);
            json.put("name", font.getName() != null ? font.getName() : "");
            json.put("source", font.getSource() != null ? font.getSource() : "bundled");
            json.put("bundledName", font.getBundledName() != null ? font.getBundledName() : "");
            json.put("customPath", font.getCustomPath() != null ? font.getCustomPath() : "");
            return json.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String readFileOrEmpty(String path) {
        if (path == null || path.isEmpty()) return "";
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return "";
            if (path.endsWith(".svg")) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                return "svg:" + sb.toString().trim();
            } else {
                byte[] bytes = new byte[(int) f.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                fis.read(bytes);
                fis.close();
                return "b64:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            }
        } catch (Exception e) {
            return "";
        }
    }

    private void showDeleteConfirm(ThemePresetEntity preset) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Theme")
                .setMessage("Delete \"" + preset.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        MessageHistoryDatabase db = openDb();
                        Long id = preset.getId();
                        if (id != null) {
                            db.themePresetDao().deleteById(id);
                        }
                        db.close();
                        long activeId = prefs.getLong("theme_builder_active_preset_id", -1L);
                        if (id != null && id == activeId) {
                            prefs.edit().putLong("theme_builder_active_preset_id", -1L).apply();
                        }
                        mainHandler.post(() -> {
                            if (getView() == null) return;
                            loadThemes();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
