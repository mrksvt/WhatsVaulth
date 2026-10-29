package com.mrksvt.waen.update;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ApkCleanupPolicyTest {

    @Test
    public void apkIsDeletedOnlyAfterASuccessfulInstall() {
        assertTrue(ApkCleanupPolicy.shouldDeleteAfterInstall(true));
        assertFalse(ApkCleanupPolicy.shouldDeleteAfterInstall(false));
    }

    @Test
    public void nothingIsKeptWithoutPendingWork() {
        assertNull(ApkCleanupPolicy.keepFileKey(false, "ABCDEF12"));
    }

    @Test
    public void pendingWorkKeepsTheReleaseFile() {
        assertEquals("ABCDEF12", ApkCleanupPolicy.keepFileKey(true, "ABCDEF12"));
    }

    @Test
    public void emptyReleaseKeyKeepsNothing() {
        assertNull(ApkCleanupPolicy.keepFileKey(true, ""));
        assertNull(ApkCleanupPolicy.keepFileKey(true, null));
    }

    @Test
    public void cleanupRemovesForeignFilesAndKeepsTheOwnedOne() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "waenvault-cleanup-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File owned = new File(dir, ApkDownloader.apkFileName("KEEP"));
            File ownedPartial = new File(dir, ApkDownloader.partialFileName("KEEP"));
            File foreign = new File(dir, ApkDownloader.apkFileName("OTHER"));
            File foreignPartial = new File(dir, ApkDownloader.partialFileName("OTHER"));
            File unrelated = new File(dir, "notes.txt");
            for (File file : new File[]{owned, ownedPartial, foreign, foreignPartial, unrelated}) {
                assertTrue(file.createNewFile());
            }

            int removed = ApkDownloader.cleanupStale(dir, "KEEP");
            assertEquals(2, removed);
            assertTrue(owned.exists());
            assertTrue(ownedPartial.exists());
            assertFalse(foreign.exists());
            assertFalse(foreignPartial.exists());
            assertTrue(unrelated.exists());
        } finally {
            for (File file : dir.listFiles()) {
                file.delete();
            }
            dir.delete();
        }
    }

    @Test
    public void cleanupWithoutAKeptKeyRemovesEveryApk() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "waenvault-cleanup-all-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            File first = new File(dir, ApkDownloader.apkFileName("ONE"));
            File second = new File(dir, ApkDownloader.partialFileName("TWO"));
            assertTrue(first.createNewFile());
            assertTrue(second.createNewFile());

            assertEquals(2, ApkDownloader.cleanupStale(dir, null));
            assertFalse(first.exists());
            assertFalse(second.exists());
        } finally {
            for (File file : dir.listFiles()) {
                file.delete();
            }
            dir.delete();
        }
    }
}
