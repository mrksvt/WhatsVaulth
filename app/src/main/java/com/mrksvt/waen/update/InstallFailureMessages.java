package com.mrksvt.waen.update;

import com.mrksvt.waen.R;

/**
 * Maps an install failure to the string resource shown to the user. Kept in one place so the
 * mapping can be unit tested on the JVM without an Android Context.
 */
public final class InstallFailureMessages {

    private InstallFailureMessages() {
    }

    /** String resource for [failure]. A null or unknown failure falls back to the generic message. */
    public static int resourceId(InstallFailure failure) {
        if (failure == null) return R.string.changelog_install_unknown_error;
        switch (failure) {
            case ASSET_MISSING:
                return R.string.changelog_install_asset_missing;
            case NOT_AN_APK:
                return R.string.changelog_install_invalid_apk;
            case WRONG_PACKAGE:
                return R.string.changelog_install_wrong_package;
            case NOT_NEWER:
                return R.string.changelog_install_not_newer;
            case SIGNATURE_MISMATCH:
                return R.string.changelog_install_signature_mismatch;
            case PERMISSION_DENIED:
                return R.string.changelog_install_needs_permission;
            default:
                return R.string.changelog_install_unknown_error;
        }
    }
}
