package com.screenstream;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {

    @Test
    public void differentShaMeansUpdateAvailable() {
        assertTrue(UpdateChecker.isUpdateAvailable(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    }

    @Test
    public void sameShaMeansUpToDate() {
        assertFalse(UpdateChecker.isUpdateAvailable(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    public void comparisonIsCaseInsensitive() {
        assertFalse(UpdateChecker.isUpdateAvailable(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    public void unknownLocalCommitNeverReportsAnUpdate() {
        assertFalse(UpdateChecker.isUpdateAvailable("unknown", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertFalse(UpdateChecker.isUpdateAvailable("", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertFalse(UpdateChecker.isUpdateAvailable(null, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    }

    @Test
    public void missingRemoteShaNeverReportsAnUpdate() {
        assertFalse(UpdateChecker.isUpdateAvailable("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null));
        assertFalse(UpdateChecker.isUpdateAvailable("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", ""));
    }
}
