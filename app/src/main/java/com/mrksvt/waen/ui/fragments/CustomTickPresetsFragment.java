package com.mrksvt.waen.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.mrksvt.waen.xposed.core.db.MessageHistoryDatabase;
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class CustomTickPresetsFragment extends Fragment {

    private RecyclerView rvPresets;
    private FloatingActionButton fabAdd;
    private TextView tvEmpty;
    private List<CustomTickPresetEntity> presets = new ArrayList<>();
    private PresetsAdapter adapter;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_tick_presets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvPresets = view.findViewById(R.id.rv_presets);
        fabAdd = view.findViewById(R.id.fab_add_preset);
        tvEmpty = view.findViewById(R.id.tv_empty);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        adapter = new PresetsAdapter();
        rvPresets.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPresets.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> navigateTo(new CustomTickEditFragment()));

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

        loadPresets();
    }

    private MessageHistoryDatabase openDb() {
        return Room.databaseBuilder(requireContext(), MessageHistoryDatabase.class, "MessageHistory.db")
                .allowMainThreadQueries()
                .addMigrations(MessageHistoryDatabase.Companion.getMIGRATION_6_7(), MessageHistoryDatabase.Companion.getMIGRATION_7_8())
                .build();
    }

    private void loadPresets() {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            seedBuiltinPresets(db);
            List<CustomTickPresetEntity> result = db.customTickPresetDao().getAll();
            db.close();
            mainHandler.post(() -> {
                if (getView() == null) return;
                presets.clear();
                presets.addAll(result);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(presets.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private static class BuiltinPreset {
        final String name;
        final String[] icons;
        final int[] colors;

        BuiltinPreset(String name, String[] icons, int[] colors) {
            this.name = name;
            this.icons = icons;
            this.colors = colors;
        }
    }

    private static final BuiltinPreset[] BUILTIN_PRESETS = {
            new BuiltinPreset("Default",
                    new String[]{"pending.svg", "sent.svg", "delivered.svg", "read.svg", "failed.svg"},
                    new int[]{0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFFE53935}),
            new BuiltinPreset("Material",
                    new String[]{"hourglass_check.svg", "sent.svg", "delivered.svg", "sent.svg", "cloud_alert.svg"},
                    new int[]{0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFFE53935}),
            new BuiltinPreset("Minimal",
                    new String[]{"delivered.svg", "delivered.svg", "delivered.svg", "delivered.svg", "delivered.svg"},
                    new int[]{0xFF9E9E9E, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFFE53935}),
            new BuiltinPreset("Vivid",
                    new String[]{"pending.svg", "sent.svg", "delivered.svg", "read.svg", "failed.svg"},
                    new int[]{0xFFFFB300, 0xFF4CAF50, 0xFF388E3C, 0xFF2979FF, 0xFFD32F2F}),
            new BuiltinPreset("Alert",
                    new String[]{"hourglass_check.svg", "check_alert.svg", "check_alert.svg", "check_alert.svg", "cloud_alert.svg"},
                    new int[]{0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFF4FC3F7, 0xFFE53935}),
            new BuiltinPreset("Toggle",
                    new String[]{"toggle-off.svg", "toggle-on.svg", "toggles.svg", "toggles.svg", "toggle-off.svg"},
                    new int[]{0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFFE53935}),
    };

    private void seedBuiltinPresets(MessageHistoryDatabase db) {
        try {
            List<CustomTickPresetEntity> existing = db.customTickPresetDao().getAll();
            File dir = new File(requireContext().getFilesDir(), "custom_tick");
            if (!dir.exists() && !dir.mkdirs()) return;
            for (BuiltinPreset preset : BUILTIN_PRESETS) {
                boolean exists = false;
                for (CustomTickPresetEntity e : existing) {
                    if (preset.name.equals(e.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;
                String[] paths = new String[5];
                for (int i = 0; i < preset.icons.length; i++) {
                    File out = new File(dir, preset.icons[i]);
                    if (!out.exists()) {
                        try (java.io.InputStream is = requireContext().getAssets().open("tick/" + preset.icons[i]);
                             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                        }
                    }
                    paths[i] = out.getAbsolutePath();
                }
                db.customTickPresetDao().insert(new CustomTickPresetEntity(
                        null, preset.name,
                        paths[0], paths[1], paths[2], paths[3], paths[4],
                        preset.colors[0], preset.colors[1], preset.colors[2], preset.colors[3], preset.colors[4]));
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

    class PresetsAdapter extends RecyclerView.Adapter<PresetsAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvActive;
            ImageView[] previews = new ImageView[5];

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_preset_name);
                tvActive = itemView.findViewById(R.id.tv_preset_active);
                int[] previewIds = {R.id.iv_preview_0, R.id.iv_preview_1, R.id.iv_preview_2,
                        R.id.iv_preview_3, R.id.iv_preview_4};
                for (int i = 0; i < 5; i++) {
                    previews[i] = itemView.findViewById(previewIds[i]);
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_custom_tick_preset, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomTickPresetEntity preset = presets.get(position);
            holder.tvName.setText(preset.getName());

            long activeId = prefs.getLong("custom_tick_active_preset_id", -1L);
            Long presetId = preset.getId();
            holder.tvActive.setVisibility(
                    presetId != null && presetId == activeId ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> showOptionsDialog(preset));

            // Load SVG previews
            String[] paths = {preset.getSvgPendingPath(), preset.getSvgSentPath(),
                    preset.getSvgDeliveredPath(), preset.getSvgReadPath(), preset.getSvgFailedPath()};
            int[] presetColors = {preset.getColorPending(), preset.getColorSent(),
                    preset.getColorDelivered(), preset.getColorRead(), preset.getColorFailed()};
            ImageView[] holderPreviews = holder.previews;
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                final String path = paths[i];
                final int color = presetColors[i];
                if (path == null || path.isEmpty()) {
                    holderPreviews[i].setImageDrawable(null);
                    continue;
                }
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        android.graphics.drawable.Drawable drawable;
                        int size = 48;
                        if (path.endsWith(".svg")) {
                            com.caverock.androidsvg.SVG svg = com.caverock.androidsvg.SVG.getFromInputStream(
                                    new java.io.FileInputStream(new java.io.File(path)));
                            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                            svg.setDocumentWidth(size);
                            svg.setDocumentHeight(size);
                            svg.renderToCanvas(canvas);
                            android.graphics.Bitmap tinted = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas tc = new android.graphics.Canvas(tinted);
                            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                            tc.drawBitmap(bmp, 0f, 0f, paint);
                            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
                            paint.setColor(color);
                            tc.drawRect(0, 0, size, size, paint);
                            drawable = new android.graphics.drawable.BitmapDrawable(null, tinted);
                        } else {
                            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path);
                            drawable = bmp != null ? new android.graphics.drawable.BitmapDrawable(null, bmp) : null;
                        }
                        android.graphics.drawable.Drawable finalDrawable = drawable;
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (holderPreviews[idx] != null) holderPreviews[idx].setImageDrawable(finalDrawable);
                        });
                    } catch (Exception ignored) {}
                });
            }
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }
    }

    private void showOptionsDialog(CustomTickPresetEntity preset) {
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
                            prefs.edit().putLong("custom_tick_active_preset_id", id).apply();
                            savePresetToPrefs(preset);
                        }
                        adapter.notifyDataSetChanged();
                    } else if (which == 1 && BuildConfig.DONATUR) { // Edit
                        Long editId = preset.getId();
                        if (editId != null) {
                            navigateTo(CustomTickEditFragment.newInstance(editId));
                        }
                    } else if (which == 2 && BuildConfig.DONATUR) { // Delete
                        showDeleteConfirm(preset);
                    }
                })
                .show();
    }

    private void showDeleteConfirm(CustomTickPresetEntity preset) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Preset")
                .setMessage("Delete \"" + preset.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        MessageHistoryDatabase db = openDb();
                        Long id = preset.getId();
                        if (id != null) {
                            db.customTickPresetDao().deleteById(id);
                        }
                        db.close();
                        // Clear active if deleted preset was active
                        long activeId = prefs.getLong("custom_tick_active_preset_id", -1L);
                        if (id != null && id == activeId) {
                            prefs.edit().putLong("custom_tick_active_preset_id", -1L).apply();
                        }
                        mainHandler.post(() -> {
                            if (getView() == null) return;
                            loadPresets();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePresetToPrefs(CustomTickPresetEntity preset) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", preset.getId() != null ? preset.getId() : -1);
            json.put("name", preset.getName() != null ? preset.getName() : "");
            json.put("svgPending", readFileOrEmpty(preset.getSvgPendingPath()));
            json.put("svgSent", readFileOrEmpty(preset.getSvgSentPath()));
            json.put("svgDelivered", readFileOrEmpty(preset.getSvgDeliveredPath()));
            json.put("svgRead", readFileOrEmpty(preset.getSvgReadPath()));
            json.put("svgFailed", readFileOrEmpty(preset.getSvgFailedPath()));
            json.put("colorPending", preset.getColorPending());
            json.put("colorSent", preset.getColorSent());
            json.put("colorDelivered", preset.getColorDelivered());
            json.put("colorRead", preset.getColorRead());
            json.put("colorFailed", preset.getColorFailed());
            prefs.edit().putString("custom_tick_active_preset_json", json.toString()).apply();
        } catch (Exception ignored) {}
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
}
