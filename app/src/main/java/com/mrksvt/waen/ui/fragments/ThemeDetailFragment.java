package com.mrksvt.waen.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.mrksvt.waen.R;
import com.mrksvt.waen.activities.TextEditorActivity;
import com.mrksvt.waen.preference.ThemePreference;
import com.mrksvt.waen.xposed.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.util.Properties;

public class ThemeDetailFragment extends Fragment {

    private static final String ARG_FOLDER = "folder_name";
    private String folderName;

    public static ThemeDetailFragment newInstance(String folderName) {
        ThemeDetailFragment f = new ThemeDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_FOLDER, folderName);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folderName = getArguments().getString(ARG_FOLDER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_theme_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        com.google.android.material.appbar.MaterialToolbar toolbar =
                view.findViewById(R.id.theme_detail_toolbar);
        toolbar.setTitle(folderName);
        toolbar.setNavigationOnClickListener(v -> {
            FragmentManager mgr = getParentFragment() != null
                    ? getParentFragment().getChildFragmentManager()
                    : getParentFragmentManager();
            if (mgr.getBackStackEntryCount() > 0) {
                mgr.popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });

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

        File themeDir = new File(ThemePreference.rootDirectory, folderName);
        ImageView wallpaperView = view.findViewById(R.id.preview_wallpaper);

        // Screenshot dari folder .preview jika ada, fallback mockup
        File previewDir = new File(themeDir, ".preview");
        File[] screenshots = previewDir.isDirectory()
                ? previewDir.listFiles((dir, name) ->
                        name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"))
                : null;
        if (screenshots != null && screenshots.length > 0) {
            showScreenshot(wallpaperView, screenshots[0]);
            hideMockup(view);
        } else {
            showMockup(themeDir, view, wallpaperView);
        }

        view.findViewById(R.id.btn_apply_theme).setOnClickListener(v -> applyTheme(themeDir));
        view.findViewById(R.id.btn_edit_theme).setOnClickListener(v -> editTheme(themeDir));
        view.findViewById(R.id.btn_delete_theme).setOnClickListener(v -> deleteTheme(themeDir));
    }

    private void showScreenshot(ImageView wallpaperView, File img) {
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = 2;
            android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeFile(img.getAbsolutePath(), opts);
            if (bmp != null) wallpaperView.setImageBitmap(bmp);
        } catch (Exception ignored) {}
    }

    private void hideMockup(View view) {
        view.findViewById(R.id.preview_toolbar).setVisibility(View.GONE);
        view.findViewById(R.id.preview_bubble_incoming).setVisibility(View.GONE);
        view.findViewById(R.id.preview_bubble_outgoing).setVisibility(View.GONE);
    }

    private void showMockup(File themeDir, View view, ImageView wallpaperView) {
        File cssFile = new File(themeDir, "style.css");
        Properties props = parseCssProperties(Utils.readFileText(cssFile));
        String primaryStr = props.getProperty("primary_color", "");
        String bgStr = props.getProperty("background_color", "");
        String textStr = props.getProperty("text_color", "");
        String bubbleRight = props.getProperty("bubble_right", "");
        String bubbleLeft = props.getProperty("bubble_left", "");

        int primary = parseColor(primaryStr, 0xFF00A884);
        int bg = parseColor(bgStr, 0xFFECE5DD);
        int text = parseColor(textStr, 0xFF111B21);
        int rightBubble = parseColor(bubbleRight, primary);
        int leftBubble = parseColor(bubbleLeft, 0xFFFFFFFF);

        view.findViewById(R.id.preview_toolbar).setBackgroundColor(primary);
        TextView toolbarTitle = view.findViewById(R.id.preview_toolbar_title);
        toolbarTitle.setTextColor(contrastOn(primary));
        TextView bubbleIn = view.findViewById(R.id.preview_bubble_incoming);
        bubbleIn.setBackgroundColor(leftBubble);
        bubbleIn.setTextColor(parseColor(bubbleLeft, 0xFF111B21));
        TextView bubbleOut = view.findViewById(R.id.preview_bubble_outgoing);
        bubbleOut.setBackgroundColor(rightBubble);
        bubbleOut.setTextColor(contrastOn(rightBubble));
        wallpaperView.setColorFilter(bg);

        File[] images = themeDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
        if (images != null && images.length > 0) {
            try {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = 2;
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeFile(images[0].getAbsolutePath(), opts);
                if (bmp != null) wallpaperView.setImageBitmap(bmp);
            } catch (Exception ignored) {}
        }
    }

    private void applyTheme(File themeDir) {
        File cssFile = new File(themeDir, "style.css");
        String cssCode = cssFile.exists() ? Utils.readFileText(cssFile) : "";
        android.content.SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(requireContext()).edit();

        File jsonFile = findThemeJson(themeDir);
        if (jsonFile != null) {
            try {
                JSONObject json = new JSONObject(Utils.readFileText(jsonFile));
                for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONObject entry = json.optJSONObject(key);
                    if (entry == null) continue;
                    String type = entry.optString("type", "");
                    switch (type) {
                        case "Boolean":
                            editor.putBoolean(key, entry.optBoolean("value"));
                            break;
                        case "Integer":
                            editor.putInt(key, entry.optInt("value"));
                            break;
                        case "String":
                            editor.putString(key, entry.optString("value"));
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        editor.putString("folder_theme", themeDir.getName());
        editor.putString("custom_css", cssCode);
        editor.putBoolean("custom_filters", true);
        editor.apply();

        android.widget.Toast.makeText(requireContext(), R.string.theme_applied_toast, android.widget.Toast.LENGTH_SHORT).show();
        com.mrksvt.waen.App.instance.sendBroadcast(
                new android.content.Intent(com.mrksvt.waen.BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"));
    }

    private File findThemeJson(File themeDir) {
        File[] jsons = themeDir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        if (jsons != null && jsons.length > 0) return jsons[0];
        return null;
    }

    private void deleteTheme(File themeDir) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_theme_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteRecursive(themeDir);
                    String active = PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .getString("folder_theme", "");
                    if (themeDir.getName().equals(active)) {
                        resetThemePrefs();
                    }
                    Toast.makeText(requireContext(), "Theme deleted", Toast.LENGTH_SHORT).show();
                    FragmentManager mgr = getParentFragment() != null
                            ? getParentFragment().getChildFragmentManager()
                            : getParentFragmentManager();
                    if (mgr.getBackStackEntryCount() > 0) {
                        mgr.popBackStack();
                    } else {
                        requireActivity().onBackPressed();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        file.delete();
    }

    private void resetThemePrefs() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString("folder_theme", "Default Theme")
                .putString("custom_css", "")
                .putBoolean("changecolor", false)
                .putString("changecolor_mode", "manual")
                .putInt("primary_color", 0)
                .putInt("text_color", 0)
                .putInt("background_color", 0)
                .putBoolean("wallpaper", false)
                .putBoolean("custom_filters", false)
                .apply();
        com.mrksvt.waen.App.instance.sendBroadcast(
                new android.content.Intent(com.mrksvt.waen.BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"));
    }

    private void editTheme(File themeDir) {
        if (!new File(themeDir, "style.css").exists()) return;
        Intent intent = new Intent(requireContext(), TextEditorActivity.class);
        intent.putExtra("folder_name", folderName);
        intent.putExtra("key", "folder_theme");
        ContextCompat.startActivity(requireContext(), intent, null);
    }

    private Properties parseCssProperties(String css) {
        Properties props = new Properties();
        if (css == null || css.isEmpty()) return props;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^/\\*\\s*(.*?)\\s*\\*/", java.util.regex.Pattern.DOTALL)
                .matcher(css);
        if (matcher.find()) {
            String[] lines = matcher.group(1).split("\\n");
            for (String line : lines) {
                String[] kv = line.split("=");
                if (kv.length == 2) {
                    props.setProperty(kv[0].trim(), kv[1].trim().replace("\"", ""));
                }
            }
        }
        return props;
    }

    private int parseColor(String value, int def) {
        if (value == null || value.isEmpty()) return def;
        try {
            if (value.startsWith("#")) {
                if (value.length() == 7) {
                    return android.graphics.Color.parseColor(value);
                } else if (value.length() == 9) {
                    return android.graphics.Color.parseColor("#" + value.substring(3));
                }
            } else {
                return (int) Long.parseLong(value, 16);
            }
        } catch (Exception ignored) {}
        return def;
    }

    private int contrastOn(int color) {
        double luminance = (0.299 * android.graphics.Color.red(color)
                + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) / 255.0;
        return luminance > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }
}
