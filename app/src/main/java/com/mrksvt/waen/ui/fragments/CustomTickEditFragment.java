package com.mrksvt.waen.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.room.Room;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.R;
import com.mrksvt.waen.xposed.core.db.MessageHistoryDatabase;
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class CustomTickEditFragment extends Fragment {

    private static final String ARG_PRESET_ID = "preset_id";

    private long presetId = -1L;

    private TextInputEditText etPresetName;

    // SVG paths for 5 tick states: 0=Pending, 1=Sent, 2=Delivered, 3=Read, 4=Failed
    private final String[] svgPaths = new String[5];

    // Default colors matching entity defaults
    private final int[] colors = new int[]{
            0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF4FC3F7, 0xFFE53935
    };

    // View arrays indexed by tick state
    private final View[] colorSwatches = new View[5];
    private final Button[] pickButtons = new Button[5];
    private final Button[] svgStringButtons = new Button[5];
    private final TextView[] pathViews = new TextView[5];
    private final ImageView[] svgPreviews = new ImageView[5];

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static CustomTickEditFragment newInstance(long presetId) {
        CustomTickEditFragment fragment = new CustomTickEditFragment();
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
        if (!BuildConfig.DONATUR) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return inflater.inflate(R.layout.fragment_custom_tick_edit, container, false);
        }
        return inflater.inflate(R.layout.fragment_custom_tick_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        etPresetName = view.findViewById(R.id.et_preset_name);

        // Wire up per-state views
        int[] swatchIds = {R.id.view_color_0, R.id.view_color_1, R.id.view_color_2,
                R.id.view_color_3, R.id.view_color_4};
        int[] pickIds = {R.id.btn_pick_svg_0, R.id.btn_pick_svg_1, R.id.btn_pick_svg_2,
                R.id.btn_pick_svg_3, R.id.btn_pick_svg_4};
        int[] pathIds = {R.id.tv_svg_path_0, R.id.tv_svg_path_1, R.id.tv_svg_path_2,
                R.id.tv_svg_path_3, R.id.tv_svg_path_4};
        int[] svgStringIds = {R.id.btn_svg_string_0, R.id.btn_svg_string_1, R.id.btn_svg_string_2,
                R.id.btn_svg_string_3, R.id.btn_svg_string_4};

        for (int i = 0; i < 5; i++) {
            colorSwatches[i] = view.findViewById(swatchIds[i]);
            pickButtons[i] = view.findViewById(pickIds[i]);
            pathViews[i] = view.findViewById(pathIds[i]);
            svgStringButtons[i] = view.findViewById(svgStringIds[i]);

            final int stateIndex = i;

            colorSwatches[i].setBackgroundColor(colors[i]);
            colorSwatches[i].setOnClickListener(v -> showColorDialog(stateIndex));
            pickButtons[i].setOnClickListener(v -> launchFilePicker(stateIndex));
            svgStringButtons[i].setOnClickListener(v -> showSvgStringDialog(stateIndex));
        }

        // Bind preview ImageViews
        int[] previewIds = {R.id.iv_svg_preview_0, R.id.iv_svg_preview_1, R.id.iv_svg_preview_2,
                R.id.iv_svg_preview_3, R.id.iv_svg_preview_4};
        for (int i = 0; i < 5; i++) {
            svgPreviews[i] = view.findViewById(previewIds[i]);
        }

        Button btnSave = view.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> savePreset());

        // Load existing preset if editing
        if (presetId != -1L) {
            loadPreset();
        }
    }

    private void loadPreset() {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            CustomTickPresetEntity preset = db.customTickPresetDao().getById(presetId);
            db.close();
            if (preset == null) return;
            mainHandler.post(() -> {
                if (getView() == null) return;
                etPresetName.setText(preset.getName());

                svgPaths[0] = preset.getSvgPendingPath();
                svgPaths[1] = preset.getSvgSentPath();
                svgPaths[2] = preset.getSvgDeliveredPath();
                svgPaths[3] = preset.getSvgReadPath();
                svgPaths[4] = preset.getSvgFailedPath();

                colors[0] = preset.getColorPending();
                colors[1] = preset.getColorSent();
                colors[2] = preset.getColorDelivered();
                colors[3] = preset.getColorRead();
                colors[4] = preset.getColorFailed();

                for (int i = 0; i < 5; i++) {
                    colorSwatches[i].setBackgroundColor(colors[i]);
                    String path = svgPaths[i];
                    pathViews[i].setText(path != null && !path.isEmpty()
                            ? new File(path).getName() : "None");
                }

                // Update previews after populating svgPaths
                for (int i = 0; i < 5; i++) {
                    final int idx = i;
                    mainHandler.post(() -> updatePreview(idx));
                }
            });
        });
    }

    private void launchFilePicker(int stateIndex) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, 100 + stateIndex);
    }

    private void showColorDialog(int stateIndex) {
        new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                requireContext(),
                colors[stateIndex],
                color -> {
                    colors[stateIndex] = color;
                    colorSwatches[stateIndex].setBackgroundColor(color);
                    updatePreview(stateIndex);
                }
        ).setSvgPreviewPath(svgPaths[stateIndex] != null ? svgPaths[stateIndex] : "")
         .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return;
        if (requestCode < 100 || requestCode > 104) return;

        int stateIndex = requestCode - 100;
        Uri uri = data.getData();
        if (uri == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            String path = copyUriToPrivateDir(requireContext(), uri, "tick_" + stateIndex);
            mainHandler.post(() -> {
                if (getView() == null) return;
                svgPaths[stateIndex] = path;
                pathViews[stateIndex].setText(path != null ? new File(path).getName() : "None");
                updatePreview(stateIndex);
            });
        });
    }

    private void savePreset() {
        String name = etPresetName.getText() != null ? etPresetName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Preset name required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build entity — null id = new insert; existing id = update
        Long entityId = presetId == -1L ? null : presetId;
        CustomTickPresetEntity preset = new CustomTickPresetEntity(
                entityId,
                name,
                svgPaths[0] != null ? svgPaths[0] : "",
                svgPaths[1] != null ? svgPaths[1] : "",
                svgPaths[2] != null ? svgPaths[2] : "",
                svgPaths[3] != null ? svgPaths[3] : "",
                svgPaths[4] != null ? svgPaths[4] : "",
                colors[0], colors[1], colors[2], colors[3], colors[4]
        );

        Executors.newSingleThreadExecutor().execute(() -> {
            MessageHistoryDatabase db = openDb();
            if (entityId == null) {
                db.customTickPresetDao().insert(preset);
            } else {
                db.customTickPresetDao().update(preset);
            }
            db.close();
            mainHandler.post(() -> {
                if (requireParentFragment().getChildFragmentManager().getBackStackEntryCount() > 0) {
                    requireParentFragment().getChildFragmentManager().popBackStack();
                }
            });
        });
    }

    /**
     * Copies URI content to app-private files/ticks/ directory.
     * Returns the absolute path of the copied file, or null on failure.
     */
    @Nullable
    private String copyUriToPrivateDir(Context context, Uri uri, String baseName) {
        try {
            File ticksDir = new File(context.getFilesDir(), "ticks");
            if (!ticksDir.exists()) {
                ticksDir.mkdirs();
            }

            // Derive extension from MIME type if possible
            String mimeType = context.getContentResolver().getType(uri);
            String ext = ".bin";
            if (mimeType != null) {
                if (mimeType.equals("image/svg+xml")) ext = ".svg";
                else if (mimeType.equals("image/png")) ext = ".png";
                else if (mimeType.equals("image/webp")) ext = ".webp";
                else if (mimeType.contains("/")) {
                    String sub = mimeType.substring(mimeType.lastIndexOf('/') + 1);
                    if (!sub.isEmpty()) ext = "." + sub;
                }
            }

            File dest = new File(ticksDir, baseName + "_" + System.currentTimeMillis() + ext);

            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;

            OutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void showSvgStringDialog(int stateIndex) {
        EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.custom_tick_svg_hint));
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (svgPaths[stateIndex] != null) {
            try {
                File f = new File(svgPaths[stateIndex]);
                if (f.exists() && f.getName().endsWith(".svg")) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    br.close();
                    input.setText(sb.toString().trim());
                }
            } catch (Exception ignored) {}
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.custom_tick_input_svg))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String svgContent = input.getText() != null ? input.getText().toString().trim() : "";
                    if (svgContent.isEmpty()) return;
                    saveSvgStringToFile(stateIndex, svgContent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveSvgStringToFile(int stateIndex, String svgContent) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File ticksDir = new File(requireContext().getFilesDir(), "ticks");
                if (!ticksDir.exists()) ticksDir.mkdirs();
                File dest = new File(ticksDir, "tick_svg_" + stateIndex + "_" + System.currentTimeMillis() + ".svg");
                java.io.FileWriter fw = new java.io.FileWriter(dest);
                fw.write(svgContent);
                fw.close();
                String path = dest.getAbsolutePath();
                mainHandler.post(() -> {
                    svgPaths[stateIndex] = path;
                    pathViews[stateIndex].setText(dest.getName());
                    updatePreview(stateIndex);
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(requireContext(), "Failed to save SVG", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updatePreview(int stateIndex) {
        String path = svgPaths[stateIndex];
        if (path == null || path.isEmpty()) {
            if (svgPreviews[stateIndex] != null) svgPreviews[stateIndex].setImageDrawable(null);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                android.graphics.drawable.Drawable drawable;
                int size = 96;
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
                    paint.setColor(colors[stateIndex]);
                    tc.drawRect(0, 0, size, size, paint);
                    drawable = new android.graphics.drawable.BitmapDrawable(getResources(), tinted);
                } else {
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path);
                    drawable = bmp != null ? new android.graphics.drawable.BitmapDrawable(getResources(), bmp) : null;
                }
                android.graphics.drawable.Drawable finalDrawable = drawable;
                mainHandler.post(() -> {
                    if (svgPreviews[stateIndex] != null) svgPreviews[stateIndex].setImageDrawable(finalDrawable);
                });
            } catch (Exception ignored) {}
        });
    }

    private MessageHistoryDatabase openDb() {
        return Room.databaseBuilder(requireContext(), MessageHistoryDatabase.class, "MessageHistory.db")
                .allowMainThreadQueries()
                .addMigrations(MessageHistoryDatabase.Companion.getMIGRATION_6_7(), MessageHistoryDatabase.Companion.getMIGRATION_7_8(), MessageHistoryDatabase.Companion.getMIGRATION_8_9(), MessageHistoryDatabase.Companion.getMIGRATION_9_10(), MessageHistoryDatabase.Companion.getMIGRATION_10_11())
                .build();
    }
}
