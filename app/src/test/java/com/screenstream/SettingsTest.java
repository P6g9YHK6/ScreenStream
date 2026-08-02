package com.screenstream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Settings must survive across process restarts, so persistence is tested via a fresh instance. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsTest {

    @Test
    public void intSurvivesANewInstance() {
        new Settings(RuntimeEnvironment.getApplication()).putInt("port", 9090);
        Settings reopened = new Settings(RuntimeEnvironment.getApplication());
        assertEquals(9090, reopened.getInt("port", -1));
    }

    @Test
    public void booleanSurvivesANewInstance() {
        new Settings(RuntimeEnvironment.getApplication()).putBoolean("auto_restart", true);
        Settings reopened = new Settings(RuntimeEnvironment.getApplication());
        assertTrue(reopened.getBoolean("auto_restart", false));
    }

    @Test
    public void stringSurvivesANewInstance() {
        new Settings(RuntimeEnvironment.getApplication()).putString("basic_user", "alice");
        Settings reopened = new Settings(RuntimeEnvironment.getApplication());
        assertEquals("alice", reopened.getString("basic_user", null));
    }

    @Test
    public void missingKeyFallsBackToDefault() {
        Settings settings = new Settings(RuntimeEnvironment.getApplication());
        assertEquals(42, settings.getInt("never_written", 42));
        assertFalse(settings.getBoolean("never_written_bool", false));
        assertEquals("fallback", settings.getString("never_written_str", "fallback"));
    }

}
