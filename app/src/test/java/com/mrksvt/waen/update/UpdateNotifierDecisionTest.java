package com.mrksvt.waen.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateNotifierDecisionTest {

    private static final String NEWEST = "B2331EBC";

    private boolean shouldNotify(boolean toggle, boolean permission, String lastNotified,
                                 String newest) {
        return UpdateNotifierDecision.shouldNotify(toggle, permission, lastNotified, newest);
    }

    @Test
    public void everythingElseBeingRightNotifies() {
        assertTrue(shouldNotify(true, true, "", NEWEST));
    }

    @Test
    public void toggleOffNeverNotifies() {
        assertFalse(shouldNotify(false, true, "", NEWEST));
    }

    @Test
    public void permissionDeniedNeverNotifies() {
        assertFalse(shouldNotify(true, false, "", NEWEST));
    }

    @Test
    public void noCandidateNeverNotifies() {
        assertFalse(shouldNotify(true, true, "", ""));
        assertFalse(shouldNotify(true, true, "", null));
        assertFalse(shouldNotify(true, true, "", "   "));
    }

    @Test
    public void alreadyNotifiedHashNeverNotifiesAgain() {
        assertFalse(shouldNotify(true, true, NEWEST, NEWEST));
    }

    @Test
    public void sameHashInDifferentCaseIsStillTheSameNotification() {
        assertFalse(shouldNotify(true, true, NEWEST.toLowerCase(), NEWEST));
    }

    @Test
    public void differingHashNotifiesOnce() {
        assertTrue(shouldNotify(true, true, "A969C54C", NEWEST));
    }

    @Test
    public void blankStoredHashWithRealCandidateNotifies() {
        assertTrue(shouldNotify(true, true, null, NEWEST));
        assertTrue(shouldNotify(true, true, "  ", NEWEST));
    }

    @Test
    public void storedHashIsTrimmedBeforeComparing() {
        assertFalse(shouldNotify(true, true, "  " + NEWEST + "  ", NEWEST));
    }

    @Test
    public void everyGateBlocksIndependently() {
        // A false in any single gate is enough; no gate may be skipped.
        assertFalse(shouldNotify(false, false, NEWEST, NEWEST));
        assertFalse(shouldNotify(false, true, "", NEWEST));
        assertFalse(shouldNotify(true, false, "", NEWEST));
        assertFalse(shouldNotify(true, true, NEWEST, NEWEST));
    }
}
