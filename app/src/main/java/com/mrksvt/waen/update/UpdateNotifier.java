package com.mrksvt.waen.update;

import android.Manifest;
import android.app.Notification;
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
import com.mrksvt.waen.UpdateChannel;
import com.mrksvt.waen.UpdateInfoStore;
import com.mrksvt.waen.activities.ChangelogActivity;

import java.util.Collection;

/**
 * System notification for a release the user has not installed yet.
 *
 * <p>Follows the channel pattern of {@code ScreenCaptureService}: create the
 * {@link NotificationChannel} once, then build with {@link NotificationCompat}. Each
 * followed update channel gets its own notification id, so following both tracks can
 * announce a stable and a prerelease build at the same time instead of one replacing
 * the other.
 */
public final class UpdateNotifier {

    /** Stable channel id; renaming it would orphan the user's existing channel settings. */
    public static final String CHANNEL_ID = "update_center";
    /** Base notification id; one slot per {@link UpdateChannel} follows it. */
    public static final int UPDATE_NOTIFICATION_ID = 1002;
    /** Fixed requestCode keeps the PendingIntent stable across releases. */
    private static final int REQUEST_CODE = 1001;

    /** Pref key: user enabled in-app update notifications. Default off. */
    public static final String PREF_IN_APP_NOTIFICATION = "update_in_app_notification_enabled";
    private static final String PREFS = "update_info";
    /** Per-channel suffix: the announced hash of that channel. */
    private static final String KEY_LAST_NOTIFIED_HASH = "last_notified_hash_";

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

    /** Notification slot of {@code channel}; stable across releases. */
    public static int notificationId(UpdateChannel channel) {
        return UPDATE_NOTIFICATION_ID + (channel == null ? 0 : channel.ordinal());
    }

    private static String lastNotifiedKey(UpdateChannel channel) {
        return KEY_LAST_NOTIFIED_HASH
                + (channel == null ? UpdateChannel.defaultChannel().key() : channel.key());
    }

    /** Hash already announced on {@code channel}, empty when nothing was announced yet. */
    public static String getLastNotifiedHash(Context context, UpdateChannel channel) {
        String raw = prefs(context).getString(lastNotifiedKey(channel), "");
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
     * Posts the notification for [release] on [channel] when every gate allows it.
     *
     * @param release the update candidate of [channel], or null
     * @return true when a notification was posted
     */
    public static boolean notifyIfNeeded(Context context, UpdateChannel channel,
                                         ReleaseInfo release, boolean toggleEnabled) {
        if (release == null) return false;
        boolean permission = canPost(context);
        boolean allowed = UpdateNotifierDecision.shouldNotify(
                toggleEnabled, permission,
                getLastNotifiedHash(context, channel), release.hash);
        if (!allowed) return false;

        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            ensureChannel(context, manager);
            manager.notify(notificationId(channel), build(context, channel, release));
            prefs(context).edit()
                    .putString(lastNotifiedKey(channel), release.hash)
                    .apply();
            return true;
        } catch (RuntimeException e) {
            // Missing icon, revoked permission mid-flight, ROM quirks: never crash the UI.
            return false;
        }
    }

    /**
     * Posts one notification per channel in [channels] that has a build the user does
     * not run, so following both tracks announces both without either replacing the
     * other.
     */
    public static void notifyFollowedChannels(Context context,
                                              Collection<UpdateChannel> channels) {
        if (channels == null) return;
        boolean enabled = getInAppNotificationEnabled(context);
        for (UpdateChannel channel : channels) {
            ReleaseInfo candidate = UpdateInfoStore.getChannelUpdate(context, channel);
            if (candidate == null) continue;
            notifyIfNeeded(context, channel, candidate, enabled);
        }
    }

    /** Forgets the announced hash of [channel], so the same release can be announced again. */
    public static void clearLastNotifiedHash(Context context, UpdateChannel channel) {
        prefs(context).edit().remove(lastNotifiedKey(channel)).apply();
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

    /** Track label shown under the notification title, so two tracks stay distinguishable. */
    private static String channelLabel(Context context, UpdateChannel channel) {
        return context.getString(channel == UpdateChannel.PRERELEASE
                ? R.string.update_channel_prerelease : R.string.update_channel_release);
    }

    private static Notification build(Context context, UpdateChannel channel,
                                      ReleaseInfo release) {
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
                .setSubText(channelLabel(context, channel))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(release.displayName()))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .build();
    }
}
