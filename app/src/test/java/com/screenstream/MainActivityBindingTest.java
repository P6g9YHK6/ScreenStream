package com.screenstream;

import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityBindingTest {

    @Test
    public void createBindsAllKeyViews() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        assertNotNull(activity.findViewById(R.id.btn_toggle));
        assertNotNull(activity.findViewById(R.id.tv_url));
        assertNotNull(activity.findViewById(R.id.spinner_auth_mode));
        assertNotNull(activity.findViewById(R.id.layout_pin_row));
        assertNotNull(activity.findViewById(R.id.layout_basic_row));
        assertNotNull(activity.findViewById(R.id.tv_pin));
        assertNotNull(activity.findViewById(R.id.edit_basic_user));
        assertNotNull(activity.findViewById(R.id.edit_basic_pass));

        controller.destroy();
    }

    @Test
    public void configurationChangeRebindsViewsWithoutCrashing() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        Configuration newConfig = new Configuration(activity.getResources().getConfiguration());
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);

        assertNotNull(activity.findViewById(R.id.btn_toggle));
        assertNotNull(activity.findViewById(R.id.spinner_auth_mode));
        assertNotNull(activity.findViewById(R.id.tv_pin));

        controller.destroy();
    }

    /**
     * Regression test for the Android 9 report that "Start Streaming" flailed and
     * overlapped content on rotation, with no permission dialog appearing.
     *
     * onConfigurationChanged used to call setContentView() + rebind on every declared
     * config change, even though activity_main.xml has no orientation-specific variant.
     * That full re-inflate could leave a stale, no-longer-listening Button briefly on
     * top of the fresh one, swallowing the tap meant to start the capture-permission
     * flow. Since there's nothing to actually re-inflate, a config change must now
     * leave the existing view tree (and its listeners) alone.
     */
    @Test
    public void configurationChangeDoesNotReinflateTheViewTree() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        android.view.View buttonBefore = activity.findViewById(R.id.btn_toggle);

        Configuration newConfig = new Configuration(activity.getResources().getConfiguration());
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);

        android.view.View buttonAfter = activity.findViewById(R.id.btn_toggle);

        assertSame("the same Button instance (with its original listener) must survive "
            + "a config change instead of being replaced by a freshly inflated one",
            buttonBefore, buttonAfter);

        controller.destroy();
    }
}
