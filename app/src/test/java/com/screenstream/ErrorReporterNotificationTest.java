package com.screenstream;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests: the error notification used to silently cut the stack trace
 * to 200 characters (500 for the "Copy" action), which is exactly why a pasted
 * crash report could end mid-line with no "Caused by" section. The notification
 * and its Copy action must always carry the whole detail/stack trace.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ErrorReporterNotificationTest {

    private static String stackTrace(int frames) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frames; i++) {
            sb.append("\tat com.screenstream.SomeReallyLongClassName.someMethod")
                .append(i).append("(SomeReallyLongClassName.java:").append(100 + i).append(")\n");
        }
        return sb.toString();
    }

    private static String longStackTrace() {
        return stackTrace(60);
    }

    @Test
    public void notificationBigTextContainsTheEntireDetailUntruncated() {
        Context ctx = RuntimeEnvironment.getApplication();
        ErrorReporter.get().init(ctx);

        // Sized well past the old 200-char notification / 500-char copy caps, but
        // comfortably under the ~1KB ceiling the notification system itself imposes
        // on a single CharSequence extra (a platform limit we can't and shouldn't
        // work around at the app level - the "Copy" action exists for that).
        String detail = stackTrace(9);
        assertTrue("fixture must exceed the old 200-char notification cap", detail.length() > 500);
        assertTrue("fixture must stay under the platform's CharSequence extra limit", detail.length() < 900);

        ErrorReporter.get().report(ErrorReporter.Level.FATAL, ErrorReporter.Source.SYSTEM, "boom", detail);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);
        Notification notification = shadowNm.getNotification(99);

        CharSequence bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        assertTrue("notification body should contain the full stack trace",
            bigText.toString().contains(detail));
        assertFalse("notification body must not be silently truncated with '...'",
            bigText.toString().trim().endsWith("..."));
    }

    @Test
    public void copyActionCarriesTheEntireDetailUntruncated() {
        Context ctx = RuntimeEnvironment.getApplication();
        ErrorReporter.get().init(ctx);

        String detail = longStackTrace();

        ErrorReporter.get().report(ErrorReporter.Level.ERROR, ErrorReporter.Source.SYSTEM, "boom", detail);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = Shadows.shadowOf(nm).getNotification(99);

        Notification.Action copyAction = notification.actions[0];
        Intent savedIntent = Shadows.shadowOf(copyAction.actionIntent).getSavedIntent();
        String copyText = savedIntent.getStringExtra("error_text");

        assertTrue("copy text should contain the full stack trace", copyText.contains(detail));
        assertFalse(copyText.trim().endsWith("..."));
    }

    @Test
    public void throwableStackTracesAreNeverTruncatedRegardlessOfLength() {
        Throwable deep = null;
        for (int i = 0; i < 40; i++) {
            deep = new RuntimeException("frame " + i, deep);
        }

        java.io.StringWriter sw = new java.io.StringWriter();
        deep.printStackTrace(new java.io.PrintWriter(sw));
        String expected = sw.toString();

        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.FATAL, ErrorReporter.Source.SYSTEM, "deep chain", expected);

        assertTrue(err.detail.length() > 800);
        assertFalse(err.detail.endsWith("..."));
    }
}
