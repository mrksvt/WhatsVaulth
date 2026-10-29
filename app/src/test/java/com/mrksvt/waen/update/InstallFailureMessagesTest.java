package com.mrksvt.waen.update;

import com.mrksvt.waen.R;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class InstallFailureMessagesTest {

    @Test
    public void everyFailureHasAMessage() {
        for (InstallFailure failure : InstallFailure.values()) {
            assertEquals(false, InstallFailureMessages.resourceId(failure) == 0);
        }
    }

    @Test
    public void nullAndUnknownShareTheGenericMessage() {
        assertEquals(R.string.changelog_install_unknown_error,
                InstallFailureMessages.resourceId(null));
        assertEquals(R.string.changelog_install_unknown_error,
                InstallFailureMessages.resourceId(InstallFailure.UNKNOWN));
    }

    @Test
    public void everyKnownFailureHasItsOwnMessage() {
        Set<Integer> ids = new HashSet<>();
        for (InstallFailure failure : InstallFailure.values()) {
            ids.add(InstallFailureMessages.resourceId(failure));
        }
        // All 7 enum values map to distinct resources, with no shared fallback to hide a case.
        assertEquals("ids=" + ids, InstallFailure.values().length, ids.size());
    }

    @Test
    public void signatureMismatchHasItsOwnMessage() {
        assertEquals(R.string.changelog_install_signature_mismatch,
                InstallFailureMessages.resourceId(InstallFailure.SIGNATURE_MISMATCH));
    }
}
