package com.screenstream;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks whether this build's version is behind the repo's latest GitHub Release. */
public class UpdateChecker {

    private static final String API_URL =
        "https://api.github.com/repos/P6g9YHK6/ScreenStream/releases/latest";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UpdateChecker");
        t.setDaemon(true);
        return t;
    });

    public interface Callback {
        void onResult(boolean updateAvailable, String latestVersion);
        void onError(Exception e);
    }

    public static void checkForUpdate(Callback callback) {
        // Created lazily (not as a static field) so merely loading this class never
        // touches Looper.getMainLooper(), which keeps isUpdateAvailable() testable
        // as a plain JUnit test with no Android framework/Robolectric involved.
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                String latestVersion = fetchLatestReleaseVersion();
                boolean updateAvailable = isUpdateAvailable(BuildConfig.VERSION_NAME, latestVersion);
                mainHandler.post(() -> callback.onResult(updateAvailable, latestVersion));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    static boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) return false;
        if (latestVersion == null || latestVersion.isEmpty()) return false;
        return !latestVersion.equalsIgnoreCase(currentVersion);
    }

    private static String fetchLatestReleaseVersion() throws IOException, org.json.JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("GitHub API returned HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            String tag = new JSONObject(sb.toString()).getString("tag_name");
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } finally {
            conn.disconnect();
        }
    }
}
