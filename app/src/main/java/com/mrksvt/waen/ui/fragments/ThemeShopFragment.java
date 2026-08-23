package com.mrksvt.waen.ui.fragments;

import android.content.Intent;
import android.net.Uri;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mrksvt.waen.R;
import com.mrksvt.waen.activities.TextEditorActivity;
import com.mrksvt.waen.preference.ThemePreference;
import com.mrksvt.waen.utils.FilePicker;
import com.mrksvt.waen.xposed.utils.Utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ThemeShopFragment extends Fragment implements FilePicker.OnUriPickedListener {

    private RecyclerView rvThemes;
    private TextView tvEmpty;
    private List<File> themes = new ArrayList<>();
    private ThemesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_theme_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        com.google.android.material.appbar.MaterialToolbar toolbar =
                view.findViewById(R.id.theme_shop_toolbar);
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

        toolbar.inflateMenu(R.menu.theme_shop_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_reload) {
                loadThemes();
                Toast.makeText(requireContext(), "Reloaded", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_restore) {
                resetToDefault();
                return true;
            }
            return false;
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

        rvThemes = view.findViewById(R.id.rv_themes);
        tvEmpty = view.findViewById(R.id.tv_empty);

        adapter = new ThemesAdapter();
        rvThemes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvThemes.setAdapter(adapter);

        view.findViewById(R.id.btn_import_theme).setOnClickListener(v -> {
            FilePicker.setOnUriPickedListener(this);
            FilePicker.fileCapture.launch(new String[]{"application/zip"});
        });

        view.findViewById(R.id.btn_restore_theme).setOnClickListener(v -> resetToDefault());

        loadThemes();
    }

    private void resetToDefault() {
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
                .putBoolean("bubble_color", false)
                .putInt("bubble_left", 0)
                .putInt("bubble_right", 0)
                .putBoolean("custom_filters", false)
                .apply();
        Toast.makeText(requireContext(), R.string.theme_reset_toast, Toast.LENGTH_SHORT).show();
        com.mrksvt.waen.App.instance.sendBroadcast(
                new android.content.Intent(com.mrksvt.waen.BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"));
    }

    private static final String DEFAULT_TAG = "__DEFAULT__";

    private void loadThemes() {
        themes.clear();
        themes.add(new File(DEFAULT_TAG)); // Default Theme selalu di atas
        File dir = ThemePreference.rootDirectory;
        if (dir.exists() && dir.isDirectory()) {
            File[] folders = dir.listFiles(File::isDirectory);
            if (folders != null) {
                for (File f : folders) {
                    if (new File(f, "style.css").exists()) {
                        themes.add(f);
                    }
                }
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(themes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onUriPicked(Uri uri) {
        if (uri == null) return;
        Toast.makeText(requireContext(), "Importing theme...", Toast.LENGTH_SHORT).show();
        CompletableFuture.runAsync(() -> {
            try (var inputStream = requireContext().getContentResolver().openInputStream(uri)) {
                var zipInputStream = new ZipInputStream(inputStream);
                ZipEntry zipEntry;
                var rootDirectory = ThemePreference.rootDirectory;
                if (!rootDirectory.exists()) rootDirectory.mkdirs();

                String zipFileName = getZipFileName(uri);
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    var entryName = zipEntry.getName();
                    String folderName;
                    String targetPath;
                    int lastSlash = entryName.lastIndexOf('/');
                    if (lastSlash > 0) {
                        folderName = entryName.substring(0, lastSlash);
                        targetPath = entryName;
                    } else {
                        folderName = zipFileName;
                        targetPath = zipFileName + "/" + entryName;
                    }
                    var newFolder = new File(rootDirectory, folderName);
                    if (!newFolder.exists()) newFolder.mkdirs();
                    if (entryName.endsWith("/")) continue;
                    var file = new File(rootDirectory, targetPath);
                    Files.copy(zipInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                requireActivity().runOnUiThread(() -> {
                    convertFouadXmlThemes();
                    Toast.makeText(requireContext(), R.string.theme_imported_successfully, Toast.LENGTH_SHORT).show();
                    loadThemes();
                });
            } catch (Exception ignored) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Import failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void convertFouadXmlThemes() {
        File dir = ThemePreference.rootDirectory;
        File[] folders = dir.listFiles(File::isDirectory);
        if (folders == null) return;
        for (File folder : folders) {
            File css = new File(folder, "style.css");
            if (css.exists()) continue;
            File[] xmls = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".xml"));
            if (xmls != null && xmls.length > 0) {
                String themeName = folder.getName().replaceAll("[^A-Za-z0-9-_]", "-").toLowerCase();
                com.mrksvt.waen.utils.FouadThemeConverter.INSTANCE.convert(xmls[0], css, themeName);
            }
        }
    }

    private String getZipFileName(Uri uri) {
        String fileName = null;
        if (android.text.TextUtils.equals(uri.getScheme(), "content")) {
            try (var cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                }
            } catch (Exception ignored) {}
        }
        if (fileName == null) fileName = uri.getLastPathSegment();
        if (fileName != null && fileName.toLowerCase().endsWith(".zip"))
            fileName = fileName.substring(0, fileName.length() - 4);
        if (fileName == null || fileName.isEmpty())
            fileName = "imported_theme_" + System.currentTimeMillis();
        return fileName;
    }

    class ThemesAdapter extends RecyclerView.Adapter<ThemesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView name;
            TextView author;
            MaterialButton badge;
            MaterialCardView card;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.theme_thumb);
                name = itemView.findViewById(R.id.theme_name);
                author = itemView.findViewById(R.id.theme_author);
                badge = itemView.findViewById(R.id.theme_badge);
                card = (MaterialCardView) itemView;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_theme_shop, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File themeDir = themes.get(position);
            String folder = themeDir.getName();

            if (DEFAULT_TAG.equals(folder)) {
                holder.name.setText(R.string.theme_default);
                holder.author.setText(R.string.theme_default_sum);
                holder.thumb.setImageResource(android.R.drawable.ic_menu_revert);
                holder.badge.setText(R.string.theme_free);
                holder.badge.setTextColor(requireContext().getColor(R.color.material_state_green));
                holder.badge.setVisibility(View.GONE);
                holder.card.setOnClickListener(v -> resetToDefault());
                return;
            }

            holder.name.setText(folder);

            File cssFile = new File(themeDir, "style.css");
            String author = "";
            if (cssFile.exists()) {
                String parsed = Utils.getAuthorFromCss(Utils.readFileText(cssFile));
                if (parsed != null) author = parsed;
            }
            holder.author.setText(author.isEmpty() ? "unknown" : author);

            File[] images = themeDir.listFiles((dir, n) ->
                    n.toLowerCase().endsWith(".png") || n.toLowerCase().endsWith(".jpg"));
            if (images != null && images.length > 0) {
                try {
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    android.graphics.Bitmap bmp =
                            android.graphics.BitmapFactory.decodeFile(images[0].getAbsolutePath(), opts);
                    if (bmp != null) holder.thumb.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }

            boolean premium = new File(themeDir, ".premium").exists();
            holder.badge.setText(premium ? R.string.theme_premium : R.string.theme_free);
            holder.badge.setTextColor(requireContext().getColor(
                    premium ? R.color.material_state_yellow : R.color.material_state_green));
            holder.badge.setVisibility(View.VISIBLE);

            holder.card.setOnClickListener(v -> {
                FragmentManager mgr = getParentFragment() != null
                        ? getParentFragment().getChildFragmentManager()
                        : getParentFragmentManager();
                mgr.beginTransaction()
                        .replace(R.id.frag_container, ThemeDetailFragment.newInstance(folder))
                        .addToBackStack(null)
                        .commit();
            });
        }

        @Override
        public int getItemCount() {
            return themes.size();
        }
    }
}
