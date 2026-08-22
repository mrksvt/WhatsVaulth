package com.mrksvt.waen.ui.fragments;

import android.content.SharedPreferences;
import android.graphics.Typeface;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class CustomFontPresetsFragment extends Fragment {

    private RecyclerView rvFonts;
    private FloatingActionButton fabAdd;
    private TextView tvEmpty;
    private List<CustomFontPresetEntity> fonts = new ArrayList<>();
    private FontsAdapter adapter;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_font_presets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvFonts = view.findViewById(R.id.rv_fonts);
        fabAdd = view.findViewById(R.id.fab_add_font);
        tvEmpty = view.findViewById(R.id.tv_empty);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        adapter = new FontsAdapter();
        rvFonts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFonts.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> navigateTo(new CustomFontEditFragment()));

        if (!BuildConfig.DONATUR) {
            fabAdd.setVisibility(View.GONE);
        }

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

        loadFonts();
    }

    private CustomFontDatabase openDb() {
        return Room.databaseBuilder(requireContext(), CustomFontDatabase.class, "CustomFont.db")
                .allowMainThreadQueries()
                .build();
    }

    private void loadFonts() {
        Executors.newSingleThreadExecutor().execute(() -> {
            CustomFontDatabase db = openDb();
            seedBuiltinFonts(db);
            List<CustomFontPresetEntity> result = db.customFontPresetDao().getAll();
            db.close();
            mainHandler.post(() -> {
                if (getView() == null) return;
                fonts.clear();
                fonts.addAll(result);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(fonts.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void seedBuiltinFonts(CustomFontDatabase db) {
        try {
            List<CustomFontPresetEntity> existing = db.customFontPresetDao().getAll();
            String[] bundledNames = getResources().getStringArray(R.array.custom_font_bundled_entries);
            String[] bundledFiles = getResources().getStringArray(R.array.custom_font_bundled_values);
            for (int i = 0; i < bundledNames.length && i < bundledFiles.length; i++) {
                boolean exists = false;
                for (CustomFontPresetEntity e : existing) {
                    if (bundledNames[i].equals(e.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;
                db.customFontPresetDao().insert(new CustomFontPresetEntity(
                        null, bundledNames[i], "bundled", bundledFiles[i], null));
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

    class FontsAdapter extends RecyclerView.Adapter<FontsAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvPreview;
            TextView tvActive;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_font_name);
                tvPreview = itemView.findViewById(R.id.tv_font_preview);
                tvActive = itemView.findViewById(R.id.tv_font_active);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_custom_font_preset, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomFontPresetEntity preset = fonts.get(position);
            holder.tvName.setText(preset.getName());

            long activeId = prefs.getLong("custom_font_active_preset_id", -1L);
            Long presetId = preset.getId();
            holder.tvActive.setVisibility(
                    presetId != null && presetId == activeId ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> showOptionsDialog(preset));

            // Load preview typeface
            final int size = (int) (24 * getResources().getDisplayMetrics().scaledDensity);
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    Typeface tf = loadTypeface(preset);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (holder.tvPreview != null) {
                            holder.tvPreview.setTypeface(tf);
                            holder.tvPreview.setTextSize(size);
                        }
                    });
                } catch (Exception ignored) {}
            });
        }

        @Override
        public int getItemCount() {
            return fonts.size();
        }
    }

    private Typeface loadTypeface(CustomFontPresetEntity preset) {
        try {
            if ("custom".equals(preset.getSource()) && preset.getCustomPath() != null
                    && !preset.getCustomPath().isEmpty()) {
                File f = new File(preset.getCustomPath());
                if (f.exists()) return Typeface.createFromFile(f);
            }
            String bundled = preset.getBundledName();
            if (bundled != null && !bundled.isEmpty()) {
                return Typeface.createFromAsset(requireContext().getAssets(), "fonts/" + bundled);
            }
        } catch (Exception ignored) {}
        return Typeface.DEFAULT;
    }

    private void showOptionsDialog(CustomFontPresetEntity preset) {
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
                            prefs.edit().putLong("custom_font_active_preset_id", id).apply();
                            savePresetToPrefs(preset);
                        }
                        adapter.notifyDataSetChanged();
                    } else if (which == 1 && BuildConfig.DONATUR) { // Edit
                        Long editId = preset.getId();
                        if (editId != null) {
                            navigateTo(CustomFontEditFragment.newInstance(editId));
                        }
                    } else if (which == 2 && BuildConfig.DONATUR) { // Delete
                        showDeleteConfirm(preset);
                    }
                })
                .show();
    }

    private void showDeleteConfirm(CustomFontPresetEntity preset) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Font")
                .setMessage("Delete \"" + preset.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        CustomFontDatabase db = openDb();
                        Long id = preset.getId();
                        if (id != null) {
                            db.customFontPresetDao().deleteById(id);
                        }
                        db.close();
                        long activeId = prefs.getLong("custom_font_active_preset_id", -1L);
                        if (id != null && id == activeId) {
                            prefs.edit().putLong("custom_font_active_preset_id", -1L).apply();
                            prefs.edit().remove("custom_font_active_preset_json").apply();
                        }
                        mainHandler.post(() -> {
                            if (getView() == null) return;
                            loadFonts();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePresetToPrefs(CustomFontPresetEntity preset) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", preset.getId() != null ? preset.getId() : -1);
            json.put("name", preset.getName() != null ? preset.getName() : "");
            json.put("source", preset.getSource() != null ? preset.getSource() : "bundled");
            json.put("bundledName", preset.getBundledName() != null ? preset.getBundledName() : "");
            json.put("customPath", preset.getCustomPath() != null ? preset.getCustomPath() : "");
            prefs.edit().putString("custom_font_active_preset_json", json.toString()).apply();
        } catch (Exception ignored) {}
    }
}
