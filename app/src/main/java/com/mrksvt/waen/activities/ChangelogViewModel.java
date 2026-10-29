package com.mrksvt.waen.activities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.ViewModel;

import com.mrksvt.waen.update.ApkDownloader;
import com.mrksvt.waen.update.DownloadState;

import java.io.File;

/**
 * Holds the download job and its last state across configuration changes so a rotation
 * neither cancels nor forgets an in-flight download.
 */
public class ChangelogViewModel extends ViewModel {

    /** Receives download state on the main thread. */
    public interface UiCallback {
        void onDownloadState(DownloadState state);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private ApkDownloader downloader;
    private DownloadState state = DownloadState.Idle.INSTANCE;
    private File downloadedFile;
    private UiCallback uiCallback;

    private final ApkDownloader.Listener downloadListener = new ApkDownloader.Listener() {
        @Override
        public void onState(DownloadState newState) {
            main.post(() -> {
                setState(newState);
                if (uiCallback != null) {
                    uiCallback.onDownloadState(newState);
                }
            });
        }
    };

    public ApkDownloader downloader(Context context) {
        if (downloader == null) {
            downloader = new ApkDownloader(context.getApplicationContext());
        }
        return downloader;
    }

    public ApkDownloader.Listener downloadListener() {
        return downloadListener;
    }

    public void setUiCallback(UiCallback callback) {
        this.uiCallback = callback;
    }

    public DownloadState getState() {
        return state;
    }

    public void setState(DownloadState state) {
        this.state = state;
        if (state instanceof DownloadState.Done) {
            this.downloadedFile = ((DownloadState.Done) state).getFile();
        }
    }

    public File getDownloadedFile() {
        return downloadedFile;
    }

    public void clearDownloadedFile() {
        downloadedFile = null;
    }

    @Override
    protected void onCleared() {
        if (downloader != null) {
            downloader.shutdown();
        }
        if (downloadedFile != null && !(state instanceof DownloadState.Done)) {
            downloadedFile.delete();
        }
        super.onCleared();
    }
}
