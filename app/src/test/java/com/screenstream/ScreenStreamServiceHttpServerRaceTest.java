package com.screenstream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the crash reported live:
 *
 * "Uncaught exception on thread [HttpServer]: Attempt to invoke virtual method
 * 'boolean java.net.ServerSocket.isClosed()' on a null object reference"
 *
 * stopCapture() runs on a different thread than the HttpServer accept loop and
 * closes-then-nulls the serverSocket field. The accept loop used to read that same
 * field twice (once for accept(), once for isClosed() in the catch block), so a
 * stopCapture() landing between those two reads could null the field out from under
 * it. The fix binds the loop to a single local ServerSocket reference captured once,
 * so a concurrent stopCapture() can only ever close it, never null it, out from
 * under the loop.
 *
 * Note: a separate fix added a catch-all in startHttpServer() that would also catch
 * this NullPointerException and log it instead of crashing the thread, so checking
 * only for an uncaught exception isn't enough to tell the race actually happened.
 * This also asserts nothing gets logged as an unexpected failure, which is what a
 * silently-caught NPE from this race would produce.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenStreamServiceHttpServerRaceTest {

    @Test
    public void stopCaptureDuringAcceptLoopDoesNotCrashTheServerThread() throws Exception {
        ScreenStreamService.setHttpsEnabled(false);
        ScreenStreamService service = new ScreenStreamService();

        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught.set(e));

        int errorsBefore = ErrorReporter.get().getRecentErrors().size();

        try {
            invoke(service, "startHttpServer", new Class<?>[]{int.class}, 0);

            // Wait for the HttpServer thread to actually bind and start listening.
            long deadline = System.currentTimeMillis() + 2000;
            while (getField(service, "serverSocket") == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            // Give it a moment to reach the blocking accept() call.
            Thread.sleep(100);

            // Exactly what a concurrent stopCapture() does: interrupt, close, null out.
            invoke(service, "stopCapture", new Class<?>[]{});

            // Give the accept loop time to unblock, hit the catch block, and either
            // crash (bug) or break out cleanly (fixed).
            Thread.sleep(300);

            assertNull("the HttpServer thread must not crash when stopCapture() "
                + "concurrently closes the listening socket", uncaught.get());

            List<ErrorReporter.AppError> newErrors =
                ErrorReporter.get().getRecentErrors().subList(errorsBefore,
                    ErrorReporter.get().getRecentErrors().size());
            for (ErrorReporter.AppError err : newErrors) {
                boolean isTheRaceNpe = err.message != null
                    && err.message.contains("Unexpected HTTP server failure")
                    && err.detail != null && err.detail.contains("NullPointerException");
                assertTrue("a concurrent stop must not surface a NullPointerException "
                    + "from the accept loop, got: " + err.message + " / " + err.detail,
                    !isTheRaceNpe);
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    private static void invoke(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = ScreenStreamService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        m.invoke(target, args);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = ScreenStreamService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
