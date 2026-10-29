package com.mrksvt.waen.update;

/**
 * Pure rules for the temporary APK lifecycle, so the decisions can be unit tested on the JVM
 * without an Android device.
 */
public final class ApkCleanupPolicy {

    private ApkCleanupPolicy() {
    }

    /**
     * True when the downloaded APK must be deleted because the install finished.
     *
     * @param installSucceeded true when the system installer reported success
     */
    public static boolean shouldDeleteAfterInstall(boolean installSucceeded) {
        return installSucceeded;
    }

    /**
     * Key of the APK that must survive a cleanup, or null when every file may be removed.
     *
     * @param ownsFile true while a download is running or an install-ready APK is held
     * @param releaseKey key of the release currently shown
     */
    public static String keepFileKey(boolean ownsFile, String releaseKey) {
        if (!ownsFile) return null;
        if (releaseKey == null || releaseKey.isEmpty()) return null;
        return releaseKey;
    }
}
