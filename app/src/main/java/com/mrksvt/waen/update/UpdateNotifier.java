package com.mrksvt.waen.update;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;
import com.mrksvt.waen.activities.ChangelogActivity;

/**
 * System notification for a release the user has not installed yet.
 *
 * <p>Follows the channel pattern of {@code ScreenCaptureService}: create the
 * {@code NotificationChannel} once, then build with {@link NotificationCompat}. The
 * notification sets {@link #CHANNEL_ID} so a newer release replaces the previous one
 * instead of stacking.
 */
public final class UpdateNotifier {

    /** Stable channel id; renaming it would orphan the user's existing channel settings. */
    public static final String CHANNEL_ID = "update_center";
    /** Fixed id so each new release replaces the previous notification. */
    public static final int UPDATE_NOTIFICATION_ID = 1002;
    /** Fixed requestCode keeps the PendingIntent stable across releases. */
    private static final int REQUEST_CODE = 1001;

    /** Pref key: user enabled in-app update notifications. Default off. */
    public static final String PREF_IN_APP_NOTIFICATION = "update_in_app_notification_enabled";
    private static final String PREFS = "update_info";
    private static final String KEY_LAST_NOTIFIED_HASH = "last_notified_hash";

    private UpdateNotifier() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean getInAppNotificationEnabled(Context context) {
        return prefs(context).getBoolean(PREF_IN_APP_NOTIFICATION, false);
    }

    public static void setInAppNotificationEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(PREF_IN_APP_NOTIFICATION, enabled).apply();
    }

    /** Hash already announced, empty when nothing was announced yet. */
    public static String getLastNotifiedHash(Context context) {
        String raw = prefs(context).getString(KEY_LAST_NOTIFIED_HASH, "");
        return raw == null ? "" : raw;
    }

    /**
     * True when notifications may actually be posted: the app-level switch is on and,
     * on Android 13+, {@code POST_NOTIFICATIONS} is granted.
     */
    public static boolean canPost(Context context) {
        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (!manager.areNotificationsEnabled()) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the runtime permission still has to be requested (Android 13+). */
    public static boolean needsPermissionRequest(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Posts the notification for [release] when every gate allows it.
     *
     * @return true when a notification was posted
     */
    public static boolean notifyIfNeeded(Context context, ReleaseInfo release,
                                         boolean hasUpdate, boolean toggleEnabled) {
        if (release == null) return false;
        boolean permission = canPost(context);
        boolean allowed = UpdateNotifierDecision.shouldNotify(
                toggleEnabled, permission, hasUpdate,
                getLastNotifiedHash(context), release.hash);
        if (!allowed) return false;

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            ensureChannel(context, manager);
            manager.notify(UPDATE_NOTIFICATION_ID, build(context, release));
            prefs(context).edit()
                    .putString(KEY_LAST_NOTIFIED_HASH, release.hash)
                    .apply();
            return true;
        } catch (RuntimeException e) {
            // Missing icon, revoked permission mid-flight, ROM quirks: never crash the UI.
            return false;
        }
    }

    /** Forgets the announced hash, so the same release can be announced again. */
    public static void clearLastNotifiedHash(Context context) {
        prefs(context).edit().remove(KEY_LAST_NOTIFIED_HASH).apply();
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        // minSdk is 28, so the channel API is always available here.
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.update_notification_channel_desc));
        manager.createNotificationChannel(channel);
    }

    private static android.app.Notification build(Context context, ReleaseInfo release) {
        Intent intent = new Intent(context, ChangelogActivity.class)
                .putExtra(ChangelogActivity.EXTRA_TITLE, release.displayName())
                .putExtra(ChangelogActivity.EXTRA_HASH, release.hash)
                .putExtra(ChangelogActivity.EXTRA_CHANGELOG, release.changelog)
                .putExtra(ChangelogActivity.EXTRA_RELEASE_URL, release.releaseUrl)
                .putExtra(ChangelogActivity.EXTRA_PUBLISHED_AT, release.publishedAt)
                .putExtra(ChangelogActivity.EXTRA_APK_URL, release.apkUrl)
                .putExtra(ChangelogActivity.EXTRA_APK_SIZE, release.apkSize)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_round_update_24)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(release.displayName())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(release.displayName()))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .build();
    }
}
