package com.screenstream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenStreamServiceAuthTest {

    private final ScreenStreamService service = new ScreenStreamService();

    @Test
    public void noneModeAlwaysAuthorized() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.NONE);
        assertTrue(service.isAuthorized("", Collections.emptyMap()));
        assertTrue(service.isAuthorized(null, Collections.emptyMap()));
    }

    @Test
    public void pinModeRequiresMatchingPin() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.PIN);
        ScreenStreamService.setRequiredPin("123456");

        assertTrue(service.isAuthorized("pin=123456", Collections.emptyMap()));
        assertFalse(service.isAuthorized("pin=000000", Collections.emptyMap()));
        assertFalse(service.isAuthorized("", Collections.emptyMap()));
        assertFalse(service.isAuthorized(null, Collections.emptyMap()));
    }

    @Test
    public void pinModeWithEmptyConfiguredPinDeniesEverything() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.PIN);
        ScreenStreamService.setRequiredPin("");
        assertFalse(service.isAuthorized("pin=", Collections.emptyMap()));
        assertFalse(service.isAuthorized("pin=anything", Collections.emptyMap()));
    }

    @Test
    public void basicModeRequiresMatchingCredentials() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.BASIC);
        ScreenStreamService.setBasicCredentials("viewer", "s3cret");

        Map<String, String> okHeaders = new HashMap<>();
        okHeaders.put("authorization", "Basic " + basicAuthValue("viewer", "s3cret"));
        assertTrue(service.isAuthorized("", okHeaders));

        Map<String, String> wrongHeaders = new HashMap<>();
        wrongHeaders.put("authorization", "Basic " + basicAuthValue("viewer", "wrong"));
        assertFalse(service.isAuthorized("", wrongHeaders));

        assertFalse(service.isAuthorized("", Collections.emptyMap()));
    }

    @Test
    public void basicModeRejectsMalformedAuthorizationHeader() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.BASIC);
        ScreenStreamService.setBasicCredentials("viewer", "s3cret");

        Map<String, String> notBasic = new HashMap<>();
        notBasic.put("authorization", "Bearer abcdef");
        assertFalse(service.isAuthorized("", notBasic));

        Map<String, String> notBase64 = new HashMap<>();
        notBase64.put("authorization", "Basic %%%not-base64%%%");
        assertFalse(service.isAuthorized("", notBase64));

        Map<String, String> noColon = new HashMap<>();
        noColon.put("authorization", "Basic " + android.util.Base64.encodeToString(
            "novalidseparator".getBytes(), android.util.Base64.NO_WRAP));
        assertFalse(service.isAuthorized("", noColon));
    }

    @Test
    public void basicModeWithNoConfiguredCredentialsDeniesEverything() {
        ScreenStreamService.setAuthMode(ScreenStreamService.AuthMode.BASIC);
        ScreenStreamService.setBasicCredentials("", "");

        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Basic " + basicAuthValue("", ""));
        assertFalse(service.isAuthorized("", headers));
    }

    private static String basicAuthValue(String user, String pass) {
        return android.util.Base64.encodeToString(
            (user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
    }
}
