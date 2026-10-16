package com.mrksvt.waen.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mrksvt.waen.R;
import com.mrksvt.waen.adapter.RecordingsAdapter;
import com.mrksvt.waen.databinding.FragmentRecordingsBinding;
import com.mrksvt.waen.model.Recording;
import com.mrksvt.waen.model.RecordingsCache;
import com.mrksvt.waen.ui.dialogs.AudioPlayerDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingActionListener {

    private FragmentRecordingsBinding binding;
    private RecordingsAdapter adapter;
    private List<Recording> allRecordings = new ArrayList<>();
    private List<File> baseDirs = new ArrayList<>();
    private boolean isGroupByContact = false;
    private int currentSortType = 1; // 1=date, 2=name, 3=duration, 4=contact
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private int loadToken = 0;
    private boolean firstResume = true;

    // Rekaman ditulis proses WhatsApp (UID berbeda), jadi FileObserver di
    // proses app ini adalah sinyal cache-stale tanpa ubah sisi Xposed.
    private final List<FileObserver> dirObservers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecordingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new RecordingsAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        // Set up selection change listener
        adapter.setSelectionChangeListener(count -> {
            if (count > 0) {
                binding.selectionBar.setVisibility(View.VISIBLE);
                binding.tvSelectionCount.setText(getString(R.string.selected_count, count));
            } else {
                binding.selectionBar.setVisibility(View.GONE);
            }
        });

        // Initialize base directories
        initializeBaseDirs();

        // View mode toggle
        binding.chipList.setOnClickListener(v -> {
            isGroupByContact = false;
            loadRecordings();
        });
        
        binding.chipGroupByContact.setOnClickListener(v -> {
            isGroupByContact = true;
            loadRecordings();
        });

        // Selection bar buttons
        binding.btnCloseSelection.setOnClickListener(v -> adapter.clearSelection());
        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnShareSelected.setOnClickListener(v -> shareSelectedRecordings());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedRecordings());

        // Sort FAB
        binding.fabSort.setOnClickListener(v -> showSortMenu());

        binding.swipeRefresh.setOnRefreshListener(() -> {
            initializeBaseDirs();
            forceReload();
        });

        // Tampilkan isi cache lebih dulu supaya screen terbuka instan.
        renderSnapshot(RecordingsCache.snapshot());
        if (RecordingsCache.isDirty()) {
            loadRecordings();
        } else {
            showLoading(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (binding == null) return;
        // Resume biasa (balik dari dialog/hasil share) tidak perlu rescan.
        // Reload hanya kalau cache sudah tidak akurat.
        if (!RecordingsCache.isDirty()) return;
        initializeBaseDirs();
        loadRecordings();
    }

    @Override
    public void onStart() {
        super.onStart();
        registerDirObservers();
    }

    @Override
    public void onStop() {
        unregisterDirObservers();
        super.onStop();
    }

    private void initializeBaseDirs() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String configuredPath = prefs.getString("call_recording_path", null);

        baseDirs.clear();
        Set<String> addedPaths = new LinkedHashSet<>();

        // Lokasi baru: satu subfolder per jenis panggilan.
        addBaseDir(addedPaths, com.mrksvt.waen.App.getVideoCallRecordingsFolder());
        addBaseDir(addedPaths, com.mrksvt.waen.App.getVoiceCallRecordingsFolder());

        // Lokasi lama, tetap dipindai supaya rekaman versi sebelumnya tidak hilang.
        List<File> legacy = com.mrksvt.waen.media.RecordingStorage.legacyBaseDirs(
                configuredPath,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStorageDirectory(),
                List.of(
                        new File("/sdcard/Android/data/com.whatsapp/files/Recordings"),
                        new File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings")
                )
        );
        for (File dir : legacy) {
            addBaseDir(addedPaths, dir);
        }

        addBaseDir(addedPaths, com.mrksvt.waen.App.getRecordingsFolder());
    }

    /**
     * Folder yang dijaga FileObserver. FileObserver tidak rekursif, jadi
     * subfolder harus ada sebelum startWatching; kalau tidak, rekaman baru
     * di folder itu tidak pernah menandai cache dirty.
     *
     * Sengaja hanya folder milik app (video_call/voice_call), bukan folder
     * legacy: mkdirs pada path legacy akan membuat folder basi yang lalu
     * ikut dipindai.
     */
    @NonNull
    private List<File> observedDirs() {
        List<File> dirs = new ArrayList<>(2);
        dirs.add(com.mrksvt.waen.App.getVideoCallRecordingsFolder());
        dirs.add(com.mrksvt.waen.App.getVoiceCallRecordingsFolder());
        for (File dir : dirs) {
            if (!dir.isDirectory()) dir.mkdirs();
        }
        return dirs;
    }

    private void addBaseDir(@NonNull Set<String> addedPaths, @NonNull File dir) {
        String normalizedPath = normalizePath(dir);
        if (addedPaths.add(normalizedPath)) {
            baseDirs.add(dir);
        }
    }

    @NonNull
    private String normalizePath(@NonNull File dir) {
        try {
            return dir.getCanonicalPath();
        } catch (IOException ignored) {
            return dir.getAbsolutePath();
        }
    }

    private void loadRecordings() {
        if (binding == null) {
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        final List<File> dirs = new ArrayList<>(baseDirs);
        final int token = ++loadToken;
        final long generation = RecordingsCache.currentGeneration();

        // Cache ada: render instan, refresh diam-diam tanpa spinner.
        showLoading(RecordingsCache.snapshot().isEmpty());
        loadExecutor.execute(() -> {
            List<Recording> scanned = new ArrayList<>();
            for (File baseDir : dirs) {
                if (baseDir.exists() && baseDir.isDirectory()) {
                    traverseDirectory(baseDir, scanned);
                }
            }

            // Resolve nomor telepon / JID ke nama kontak perangkat.
            // Cache per identifier supaya tidak query konten provider
            // berkali-kali untuk file yang sama dari kontak yang sama.
            Map<String, String> resolvedCache = new HashMap<>();
            for (Recording r : scanned) {
                String key = r.getContactName();
                if (key == null) continue;
                String cached = resolvedCache.get(key);
                if (cached != null) {
                    r.setContactName(cached);
                } else {
                    r.resolveContactName(appContext);
                    resolvedCache.put(key, r.getContactName());
                }
            }

            View root = getView();
            if (root == null) return;
            root.post(() -> {
                if (binding == null || token != loadToken) return;
                RecordingsCache.store(scanned, generation);
                renderSnapshot(scanned);
                showLoading(false);
                binding.swipeRefresh.setRefreshing(false);
            });
        });
    }

    /**
     * Bind daftar hasil scan (atau cache) ke adapter + atur visibility
     * empty/list. Dipisah dari load supaya cache bisa dirender tanpa scan.
     */
    private void renderSnapshot(List<Recording> list) {
        // Copy: applySort() mengurutkan in-place, cache tidak boleh ikut berubah.
        allRecordings = new ArrayList<>(list);
        applySort();
        adapter.setRecordings(allRecordings);
        if (allRecordings.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /** Paksa rescan penuh, dipakai swipe-to-refresh (abaikan flag dirty). */
    private void forceReload() {
        RecordingsCache.markDirty();
        loadRecordings();
    }

    private void registerDirObservers() {
        unregisterDirObservers();
        final int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.DELETE;
        for (File dir : observedDirs()) {
            // minSdk 28: FileObserver(File, int) baru ada di API 29.
            FileObserver observer = new FileObserver(dir.getAbsolutePath(), mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    RecordingsCache.markDirty();
                }
            };
            observer.startWatching();
            dirObservers.add(observer);
        }
    }

    private void unregisterDirObservers() {
        for (FileObserver observer : dirObservers) {
            observer.stopWatching();
        }
        dirObservers.clear();
    }

    private void showLoading(boolean loading) {
        if (binding == null) return;
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading && RecordingsCache.snapshot().isEmpty()) {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.GONE);
        }
    }

    private void traverseDirectory(File dir, List<Recording> out) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    traverseDirectory(file, out);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac")
                            || name.endsWith(".m4a") || name.endsWith(".mp4")) {
                        out.add(new Recording(file));
                    }
                }
            }
        }
    }

    private void applySort() {
        switch (currentSortType) {
            case 1 -> allRecordings.sort((r1, r2) -> Long.compare(r2.getDate(), r1.getDate())); // Date desc
            case 2 -> allRecordings.sort(Comparator.comparing(Recording::getContactName)); // Name
            case 3 -> allRecordings.sort((r1, r2) -> Long.compare(r2.getDuration(), r1.getDuration())); // Duration desc
            case 4 -> allRecordings.sort(Comparator.comparing(Recording::getContactName)
                    .thenComparing((r1, r2) -> Long.compare(r2.getDate(), r1.getDate()))); // Contact then date
        }
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), binding.fabSort);
        popup.getMenu().add(0, 1, 0, R.string.sort_date);
        popup.getMenu().add(0, 2, 0, R.string.sort_name);
        popup.getMenu().add(0, 3, 0, R.string.sort_duration);
        popup.getMenu().add(0, 4, 0, R.string.sort_contact);
        
        popup.setOnMenuItemClickListener(item -> {
            currentSortType = item.getItemId();
            applySort();
            adapter.setRecordings(allRecordings);
            return true;
        });
        popup.show();
    }

    // RecordingsAdapter.OnRecordingActionListener implementation

    @Override
    public void onPlay(Recording recording) {
        // Use in-app audio player
        if (recording.isVideo()) {
            openVideo(recording.getFile());
            return;
        }
        AudioPlayerDialog dialog = new AudioPlayerDialog(requireContext(), recording.getFile());
        dialog.show();
    }

    @Override
    public void onShare(Recording recording) {
        shareRecording(recording.getFile());
    }

    @Override
    public void onDelete(Recording recording) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(recording.getFile().getName())
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    if (recording.getFile().delete()) {
                        loadRecordings();
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onLongPress(Recording recording, int position) {
        // Enter selection mode
        adapter.setSelectionMode(true);
        adapter.toggleSelection(position);
    }

    private void openVideo(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error opening video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareRecording(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), 
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        if (selected.size() == 1) {
            shareRecording(selected.get(0).getFile());
            adapter.clearSelection();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (Recording rec : selected) {
            try {
                Uri uri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider", rec.getFile());
                uris.add(uri);
            } catch (Exception ignored) {}
        }

        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("audio/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recordings)));
        }
        adapter.clearSelection();
    }

    private void deleteSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(getString(R.string.delete_multiple_confirmation, selected.size()))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    int deleted = 0;
                    for (Recording rec : selected) {
                        if (rec.getFile().delete()) {
                            deleted++;
                        }
                    }
                    Toast.makeText(requireContext(), "Deleted " + deleted + " recordings", Toast.LENGTH_SHORT).show();
                    adapter.clearSelection();
                    loadRecordings();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        loadToken++;
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loadExecutor.shutdownNow();
    }
}
