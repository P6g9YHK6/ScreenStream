package com.screenstream;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {

    @Test
    public void differentVersionMeansUpdateAvailable() {
        assertTrue(UpdateChecker.isUpdateAvailable("1.0", "1.1"));
    }

    @Test
    public void sameVersionMeansUpToDate() {
        assertFalse(UpdateChecker.isUpdateAvailable("1.0", "1.0"));
    }

    @Test
    public void comparisonIsCaseInsensitive() {
        assertFalse(UpdateChecker.isUpdateAvailable("1.0-RC", "1.0-rc"));
    }

    @Test
    public void emptyCurrentVersionNeverReportsAnUpdate() {
        assertFalse(UpdateChecker.isUpdateAvailable("", "1.1"));
        assertFalse(UpdateChecker.isUpdateAvailable(null, "1.1"));
    }

    @Test
    public void missingLatestVersionNeverReportsAnUpdate() {
        assertFalse(UpdateChecker.isUpdateAvailable("1.0", null));
        assertFalse(UpdateChecker.isUpdateAvailable("1.0", ""));
    }
}
