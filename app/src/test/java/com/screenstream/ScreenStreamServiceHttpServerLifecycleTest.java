package com.screenstream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the bug reported on the F-Droid MR: enabling HTTPS could still
 * serve plain HTTP under an https:// URL.
 *
 * stopCapture() used to leave the listening ServerSocket open. startCapture() only
 * (re)builds the listener "if (serverSocket == null || serverSocket.isClosed())", so
 * starting without HTTPS, stopping, then flipping the HTTPS switch on and starting again
 * reused the old plain-HTTP socket instead of building a new TLS one. A browser hitting
 * that URL over TLS then got ERR_CONNECTION_CLOSED.
 *
 * stopCapture() now closes and clears the socket (and interrupts the server thread) so
 * every start rebuilds the listener fresh against the current setting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenStreamServiceHttpServerLifecycleTest {

    @Test
    public void stopCaptureClosesAndClearsTheListeningSocket() throws Exception {
        ScreenStreamService service = new ScreenStreamService();

        ServerSocket socket = new ServerSocket(0);
        Thread thread = new Thread(() -> { });

        setField(service, "serverSocket", socket);
        setField(service, "serverThread", thread);

        invokeStopCapture(service);

        assertTrue("the previously open listener must be closed so a stale socket "
            + "can't be reused under a different protocol", socket.isClosed());
        assertNull("serverSocket must be cleared so the next start rebuilds it",
            getField(service, "serverSocket"));
        assertNull("serverThread must be cleared", getField(service, "serverThread"));
    }

    @Test
    public void stopCaptureWithNoServerRunningDoesNotThrow() throws Exception {
        ScreenStreamService service = new ScreenStreamService();
        invokeStopCapture(service);
        assertNull(getField(service, "serverSocket"));
    }

    private static void invokeStopCapture(ScreenStreamService service) throws Exception {
        Method m = ScreenStreamService.class.getDeclaredMethod("stopCapture");
        m.setAccessible(true);
        m.invoke(service);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ScreenStreamService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = ScreenStreamService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
