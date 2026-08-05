package com.screenstream;

import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Regression test: RECORD_AUDIO is declared in the manifest but was never requested at
 * runtime, so AudioRecord construction threw SecurityException on every device and audio
 * silently never worked. Starting a capture with the audio switch on must now request
 * RECORD_AUDIO first when it isn't already granted.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityAudioPermissionTest {

    @Test
    public void startingStreamWithAudioEnabledAndUngrantedPermissionRequestsRecordAudio() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().get();

        Button toggle = activity.findViewById(R.id.btn_toggle);
        toggle.performClick();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        ShadowActivity.PermissionsRequest request = shadowActivity.getLastRequestedPermission();

        assertNotNull("starting a stream with audio enabled must ask for RECORD_AUDIO "
            + "since AudioRecord construction otherwise throws SecurityException", request);
        assertArrayEquals(new String[]{android.Manifest.permission.RECORD_AUDIO},
            request.requestedPermissions);

        controller.destroy();
    }

    @Test
    public void startingStreamWithAudioDisabledDoesNotRequestRecordAudio() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().get();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        // onCreate already requested POST_NOTIFICATIONS on this SDK; that's the baseline
        // "last request" we expect to see untouched if RECORD_AUDIO is correctly skipped.
        ShadowActivity.PermissionsRequest baseline = shadowActivity.getLastRequestedPermission();

        androidx.appcompat.widget.SwitchCompat audioSwitch = activity.findViewById(R.id.switch_audio);
        audioSwitch.setChecked(false);

        Button toggle = activity.findViewById(R.id.btn_toggle);
        toggle.performClick();

        assertSame("audio is off, so no new permission request (RECORD_AUDIO) should fire",
            baseline, shadowActivity.getLastRequestedPermission());

        controller.destroy();
    }
}
