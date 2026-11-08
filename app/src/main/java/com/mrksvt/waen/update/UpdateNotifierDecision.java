package com.mrksvt.waen.update;

/**
 * Pure decision rule for the "update available" system notification.
 *
 * <p>Separated from {@link UpdateNotifier} so the rule stays unit testable on the JVM
 * without a {@code Context} or {@code NotificationManager}.
 */
public final class UpdateNotifierDecision {

    private UpdateNotifierDecision() {
    }

    /**
     * Whether the user must be notified about [newestHash].
     *
     * <p>An empty [newestHash] means the channel has no build the user does not run
     * yet, so the caller does not need a separate "has update" gate.
     *
     * @param toggleEnabled     user turned "push in-app notification" on
     * @param permissionGranted notifications may be posted right now
     * @param lastNotifiedHash  hash already notified on this channel, empty when never
     * @param newestHash        hash of the channel's update candidate, empty when none
     * @return true only when every gate passes and this hash was not notified before
     */
    public static boolean shouldNotify(boolean toggleEnabled,
                                       boolean permissionGranted,
                                       String lastNotifiedHash,
                                       String newestHash) {
        if (!toggleEnabled) return false;
        if (!permissionGranted) return false;
        String candidate = trim(newestHash);
        if (candidate.isEmpty()) return false;
        return !candidate.equalsIgnoreCase(trim(lastNotifiedHash));
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
