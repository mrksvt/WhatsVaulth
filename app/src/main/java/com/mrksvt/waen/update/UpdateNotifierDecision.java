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
     * @param toggleEnabled     user turned "push in-app notification" on
     * @param permissionGranted notifications may be posted right now
     * @param hasUpdate         the active channel found a build the user does not run
     * @param lastNotifiedHash  hash already notified, empty when never notified
     * @param newestHash        hash of the candidate release, empty when unknown
     * @return true only when every gate passes and this hash was not notified before
     */
    public static boolean shouldNotify(boolean toggleEnabled,
                                       boolean permissionGranted,
                                       boolean hasUpdate,
                                       String lastNotifiedHash,
                                       String newestHash) {
        if (!toggleEnabled) return false;
        if (!permissionGranted) return false;
        if (!hasUpdate) return false;
        String candidate = trim(newestHash);
        if (candidate.isEmpty()) return false;
        return !candidate.equalsIgnoreCase(trim(lastNotifiedHash));
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
