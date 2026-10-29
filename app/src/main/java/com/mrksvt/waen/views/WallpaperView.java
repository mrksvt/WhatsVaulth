package com.mrksvt.waen.views;

import static de.robv.android.xposed.XposedBridge.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.mrksvt.waen.preference.ThemePreference;
import com.mrksvt.waen.xposed.core.WppCore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("ViewConstructor")
public class WallpaperView extends FrameLayout {
    private final SharedPreferences prefs;
    private final Properties properties;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WV-Wallpaper");
        t.setDaemon(true);
        return t;
    });

    private static volatile LoadedWallpaper loaded;

    public WallpaperView(@NonNull Context context, SharedPreferences preferences, Properties properties) {
        super(context);
        this.prefs = preferences;
        this.properties = properties;
        init(context);
    }

    private void init(Context context) {
        ImageView bgView = new ImageView(context);
        bgView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgView.setAdjustViewBounds(false);
        addView(bgView);

        LoadedWallpaper shown = loaded;
        if (shown != null && shown.bitmap != null) {
            bgView.setImageDrawable(new BitmapDrawable(getResources(), shown.bitmap));
        }

        Context appContext = context.getApplicationContext();
        LOADER.execute(() -> {
            LoadedWallpaper result = load(appContext, prefs, properties);
            if (result == null || result.bitmap == null) return;
            if (shown != null && result.bitmap == shown.bitmap) return;
            bgView.post(() -> bgView.setImageDrawable(new BitmapDrawable(getResources(), result.bitmap)));
        });
    }

    public static void prefetch(Context context, SharedPreferences prefs, Properties properties) {
        Context appContext = context.getApplicationContext();
        LOADER.execute(() -> load(appContext, prefs, properties));
    }

    private static LoadedWallpaper load(Context context, SharedPreferences prefs, Properties properties) {
        try {
            File file = new File(resolveImagePath(prefs, properties));
            if (!file.exists()) return null;

            String cacheKey = file.getAbsolutePath() + "_" + file.lastModified();
            LoadedWallpaper cached = loaded;
            if (cached != null && cached.key.equals(cacheKey)) return cached;

            Bitmap bitmap = decodeBitmap(context, file, cacheKey);
            if (bitmap == null) return null;
            LoadedWallpaper result = new LoadedWallpaper(cacheKey, bitmap);
            loaded = result;
            return result;
        } catch (Exception e) {
            log("Error loading wallpaper drawable: " + e.getMessage());
            return null;
        }
    }

    private static String resolveImagePath(SharedPreferences prefs, Properties properties) {
        if (prefs.getBoolean("wallpaper", false)) {
            String custom = prefs.getString("wallpaper_file", "");
            return custom != null ? custom : "";
        }
        return ThemePreference.rootDirectory.getAbsolutePath() + "/" + prefs.getString("folder_theme", "") + "/" + properties.getProperty("wallpaper_file");
    }

    private static Bitmap decodeBitmap(Context context, File file, String cacheKey) throws Exception {
        String fileOut = context.getFilesDir().getAbsolutePath() + "/" + "wallpaper.jpg";
        String cachedData = WppCore.getPrivString("wallpaper_data", "");

        if (cacheKey.equals(cachedData) && new File(fileOut).exists()) {
            return BitmapFactory.decodeFile(fileOut);
        }

        String filePath = file.getAbsolutePath();
        Bitmap bitmap;
        if (!file.canRead()) {
            var parcelFile = WppCore.getClientBridge().openFile(filePath, false);
            bitmap = BitmapFactory.decodeStream(new FileInputStream(parcelFile.getFileDescriptor()));
        } else {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        }
        if (bitmap == null) return null;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        var windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
        int width = displayMetrics.widthPixels > 0 ? displayMetrics.widthPixels : bitmap.getWidth();
        int height = displayMetrics.heightPixels > 0 ? displayMetrics.heightPixels : bitmap.getHeight();
        var scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        try (var outputStream = new FileOutputStream(fileOut)) {
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            outputStream.flush();
        } catch (Exception ignored) {
        }

        WppCore.setPrivString("wallpaper_data", cacheKey);
        if (scaledBitmap != bitmap) {
            bitmap.recycle();
        }

        return scaledBitmap;
    }

    private static class LoadedWallpaper {
        final String key;
        final Bitmap bitmap;

        LoadedWallpaper(String key, Bitmap bitmap) {
            this.key = key;
            this.bitmap = bitmap;
        }
    }
}
