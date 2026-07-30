package com.screenstream;

import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;

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
}
