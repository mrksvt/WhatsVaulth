package com.mrksvt.waen.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * APK Explorer — browse resource names & IDs from installed WhatsApp APK.
 * Donatur-only feature. Data loaded async via background thread.
 */
public class ApkExplorerFragment extends Fragment {

    // Resource type class names inside android.R (mirrored in app R)
    private static final String[] RESOURCE_TYPES = {
        "id", "layout", "string", "drawable", "color",
        "dimen", "style", "attr", "menu", "anim", "raw",
        "plurals", "bool", "integer", "array", "interpolator",
        "xml", "font", "mipmap"
    };

    private static final String[] PACKAGES = {
        "com.whatsapp",
        "com.whatsapp.w4b"
    };

    // --- UI ---
    private Spinner packageSpinner;
    private Spinner typeSpinner;
    private EditText searchEdit;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;

    // --- State ---
    private String selectedPackage = PACKAGES[0];
    private String selectedType = RESOURCE_TYPES[0];
    private Resources waRes = null;
    // Map<type, List<ResourceEntry>>
    private final Map<String, List<ResourceEntry>> cache = new HashMap<>();
    private List<ResourceEntry> currentList = new ArrayList<>();
    private final ResourceAdapter adapter = new ResourceAdapter();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // -------------------------------------------------------------------------
    // Fragment lifecycle
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        int dp4  = dp(d, 4);
        int dp8  = dp(d, 8);
        int dp12 = dp(d, 12);
        int dp16 = dp(d, 16);
        int dp48 = dp(d, 48);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp16, dp8, dp16, dp8);

        // --- Title ---
        TextView title = new TextView(ctx);
        title.setText("APK Explorer");
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp8, 0, dp8);
        root.addView(title);

        // --- Package spinner ---
        TextView pkgLabel = new TextView(ctx);
        pkgLabel.setText("Package:");
        pkgLabel.setTextSize(12f);
        root.addView(pkgLabel);

        packageSpinner = new Spinner(ctx);
        ArrayAdapter<String> pkgAdapter = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_item, PACKAGES);
        pkgAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        packageSpinner.setAdapter(pkgAdapter);
        root.addView(packageSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp48));

        // --- Type spinner ---
        TextView typeLabel = new TextView(ctx);
        typeLabel.setText("Resource type:");
        typeLabel.setTextSize(12f);
        typeLabel.setPadding(0, dp4, 0, 0);
        root.addView(typeLabel);

        typeSpinner = new Spinner(ctx);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_item, RESOURCE_TYPES);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        root.addView(typeSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp48));

        // --- Search ---
        searchEdit = new EditText(ctx);
        searchEdit.setHint("Filter by name...");
        searchEdit.setSingleLine(true);
        searchEdit.setPadding(dp8, dp8, dp8, dp8);
        root.addView(searchEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Progress + RecyclerView in FrameLayout ---
        FrameLayout frame = new FrameLayout(ctx);

        progressBar = new ProgressBar(ctx);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(dp48, dp48);
        pbParams.gravity = Gravity.CENTER;
        frame.addView(progressBar, pbParams);

        emptyView = new TextView(ctx);
        emptyView.setText("Select package & type");
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.VISIBLE);
        frame.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        recyclerView.addItemDecoration(new DividerItemDecoration(ctx, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
        recyclerView.setVisibility(View.GONE);
        frame.addView(recyclerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // --- Listeners ---
        packageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedPackage = PACKAGES[pos];
                cache.clear();
                waRes = null;
                loadResources();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedType = RESOURCE_TYPES[pos];
                loadResources();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filterAndShow(s.toString()); }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    requireActivity().finish();
                }
            }
        });

        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private void loadResources() {
        // Check cache first
        if (cache.containsKey(selectedPackage + ":" + selectedType)) {
            currentList = cache.get(selectedPackage + ":" + selectedType);
            filterAndShow(searchEdit != null ? searchEdit.getText().toString() : "");
            return;
        }

        showLoading(true);

        executor.execute(() -> {
            List<ResourceEntry> result = new ArrayList<>();
            try {
                Resources res = getOrCreateResources();
                if (res == null) {
                    mainHandler.post(() -> showError("Package tidak terinstall: " + selectedPackage));
                    return;
                }
                result = enumerateResources(res, selectedType);
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> showError("Error: " + msg));
                return;
            }

            final List<ResourceEntry> finalResult = result;
            final String cacheKey = selectedPackage + ":" + selectedType;
            mainHandler.post(() -> {
                cache.put(cacheKey, finalResult);
                currentList = finalResult;
                showLoading(false);
                filterAndShow(searchEdit != null ? searchEdit.getText().toString() : "");
            });
        });
    }

    @Nullable
    private Resources getOrCreateResources() {
        if (waRes != null) return waRes;
        try {
            PackageManager pm = requireContext().getPackageManager();
            waRes = pm.getResourcesForApplication(selectedPackage);
            return waRes;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private List<ResourceEntry> enumerateResources(Resources res, String type) {
        List<ResourceEntry> list = new ArrayList<>();
        try {
            android.content.pm.ApplicationInfo appInfo = requireContext()
                    .getPackageManager().getApplicationInfo(selectedPackage, 0);
            java.io.File apkFile = new java.io.File(appInfo.sourceDir);

            byte[] arscBytes = com.google.devrel.gmscore.tools.common.ApkUtils.getFile(apkFile, "resources.arsc");
            if (arscBytes == null) {
                return fallbackEnumerate(res, type);
            }

            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(arscBytes)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            com.google.devrel.gmscore.tools.apk.arsc.Chunk chunk =
                    com.google.devrel.gmscore.tools.apk.arsc.Chunk.newInstance(buf);
            if (!(chunk instanceof com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk)) {
                return fallbackEnumerate(res, type);
            }
            com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk table =
                    (com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk) chunk;

            java.util.Set<String> seen = new java.util.HashSet<>();
            for (com.google.devrel.gmscore.tools.apk.arsc.PackageChunk pkg : table.getPackages()) {
                for (com.google.devrel.gmscore.tools.apk.arsc.TypeChunk tc : pkg.getTypeChunks(type)) {
                    for (java.util.Map.Entry<Integer, com.google.devrel.gmscore.tools.apk.arsc.TypeChunk.Entry> e
                            : tc.getEntries().entrySet()) {
                        String name = e.getValue().key();
                        if (name != null && seen.add(name)) {
                            int resId = (pkg.getId() << 24) | (tc.getId() << 16) | e.getKey();
                            list.add(new ResourceEntry(name, resId));
                        }
                    }
                }
            }

            if (list.isEmpty()) {
                return enumerateFromAssets(res, type);
            }
        } catch (Exception e) {
            return fallbackEnumerate(res, type);
        }
        Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    private List<ResourceEntry> fallbackEnumerate(Resources res, String type) {
        List<ResourceEntry> list = new ArrayList<>();
        try {
            list = enumerateFromAssets(res, type);
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Enumerate file-backed resource types via AssetManager path listing.
     * Works for layout, drawable, anim, menu, raw, xml, font, mipmap.
     */
    private List<ResourceEntry> enumerateFromAssets(Resources res, String type) {
        List<ResourceEntry> list = new ArrayList<>();
        try {
            android.content.res.AssetManager assets = res.getAssets();
            // AssetManager.list() returns files in assets/ folder, not res/
            // For res/ files, use openNonAssetFd — but we need the path.
            // Use reflection to call list on the res directory
            java.lang.reflect.Method listMethod = android.content.res.AssetManager.class
                    .getDeclaredMethod("list", String.class);
            listMethod.setAccessible(true);

            String[] folders = { "res/" + type, "res/" + type + "-v4",
                    "res/" + type + "-hdpi", "res/" + type + "-xhdpi",
                    "res/" + type + "-xxhdpi", "res/" + type + "-xxxhdpi",
                    "res/" + type + "-night" };

            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String folder : folders) {
                try {
                    String[] files = (String[]) listMethod.invoke(assets, folder);
                    if (files != null) {
                        for (String file : files) {
                            // Strip extension
                            String name = file.contains(".")
                                    ? file.substring(0, file.lastIndexOf('.'))
                                    : file;
                            if (seen.add(name)) {
                                int id = res.getIdentifier(name, type, selectedPackage);
                                list.add(new ResourceEntry(name, id));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return list;
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void showLoading(boolean loading) {
        if (progressBar == null) return;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(loading ? View.GONE : View.VISIBLE);
        emptyView.setText("Memuat...");
    }

    private void showError(String msg) {
        if (progressBar == null) return;
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(msg);
    }

    private void filterAndShow(String query) {
        if (currentList == null) return;
        List<ResourceEntry> filtered;
        if (query == null || query.isEmpty()) {
            filtered = currentList;
        } else {
            filtered = new ArrayList<>();
            String q = query.toLowerCase();
            for (ResourceEntry e : currentList) {
                if (e.name.toLowerCase().contains(q)) filtered.add(e);
            }
        }
        adapter.setData(filtered);

        if (recyclerView == null) return;
        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(currentList.isEmpty()
                    ? "Tidak ada resource ditemukan untuk type '" + selectedType + "'"
                    : "Tidak ada hasil untuk \"" + query + "\"");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }
    }

    private static int dp(float density, int value) {
        return (int) (value * density + 0.5f);
    }

    // -------------------------------------------------------------------------
    // Data model
    // -------------------------------------------------------------------------

    static class ResourceEntry {
        final String name;
        final int id;

        ResourceEntry(String name, int id) {
            this.name = name;
            this.id = id;
        }

        String hexId() {
            return id == 0 ? "0x00000000" : String.format("0x%08X", id);
        }
    }

    // -------------------------------------------------------------------------
    // RecyclerView adapter
    // -------------------------------------------------------------------------

    class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.VH> {
        private List<ResourceEntry> data = new ArrayList<>();

        void setData(List<ResourceEntry> list) {
            data = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;
            int dp4  = dp(d, 4);
            int dp8  = dp(d, 8);
            int dp12 = dp(d, 12);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp8, dp12, dp8, dp12);
            row.setClickable(true);
            row.setFocusable(true);

            // Ripple/press feedback via selectableItemBackground
            int[] attrs = { android.R.attr.selectableItemBackground };
            android.content.res.TypedArray ta = ctx.obtainStyledAttributes(attrs);
            row.setBackground(ta.getDrawable(0));
            ta.recycle();

            TextView nameTv = new TextView(ctx);
            nameTv.setTextSize(14f);
            nameTv.setTag("name");
            row.addView(nameTv);

            TextView idTv = new TextView(ctx);
            idTv.setTextSize(11f);
            idTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            idTv.setTag("id");
            int textColorSecondary;
            android.content.res.TypedArray ta2 = ctx.obtainStyledAttributes(
                    new int[]{ android.R.attr.textColorSecondary });
            textColorSecondary = ta2.getColor(0, 0xFF888888);
            ta2.recycle();
            idTv.setTextColor(textColorSecondary);
            row.addView(idTv);

            row.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));

            return new VH(row, nameTv, idTv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ResourceEntry entry = data.get(position);
            holder.nameTv.setText(entry.name);
            holder.idTv.setText(entry.hexId());

            // Single tap → copy name
            holder.row.setOnClickListener(v -> copyToClipboard(entry.name, "name: " + entry.name));

            // Long tap → choose name or ID
            holder.row.setOnLongClickListener(v -> {
                showCopyMenu(entry);
                return true;
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView nameTv;
            final TextView idTv;

            VH(LinearLayout row, TextView nameTv, TextView idTv) {
                super(row);
                this.row = row;
                this.nameTv = nameTv;
                this.idTv = idTv;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Copy helpers
    // -------------------------------------------------------------------------

    private void showCopyMenu(ResourceEntry entry) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(entry.name)
                .setItems(new String[]{
                        "Copy name: " + entry.name,
                        "Copy ID: " + entry.hexId(),
                        "Copy keduanya"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: copyToClipboard(entry.name, "Nama disalin"); break;
                        case 1: copyToClipboard(entry.hexId(), "ID disalin"); break;
                        case 2: copyToClipboard(entry.name + " = " + entry.hexId(), "Disalin"); break;
                    }
                })
                .show();
    }

    private void copyToClipboard(String text, String toastMsg) {
        ClipboardManager cb = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("apk_resource", text));
        Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show();
    }
}
