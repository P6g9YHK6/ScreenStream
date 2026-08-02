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

/** Checks whether the commit this build was compiled from is behind the repo's main branch on GitHub. */
public class UpdateChecker {

    private static final String API_URL =
        "https://api.github.com/repos/P6g9YHK6/ScreenStream/commits/main";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UpdateChecker");
        t.setDaemon(true);
        return t;
    });

    public interface Callback {
        void onResult(boolean updateAvailable, String latestSha);
        void onError(Exception e);
    }

    public static void checkForUpdate(Callback callback) {
        // Created lazily (not as a static field) so merely loading this class never
        // touches Looper.getMainLooper(), which keeps isUpdateAvailable() testable
        // as a plain JUnit test with no Android framework/Robolectric involved.
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                String latestSha = fetchLatestMainSha();
                boolean updateAvailable = isUpdateAvailable(BuildConfig.GIT_COMMIT, latestSha);
                mainHandler.post(() -> callback.onResult(updateAvailable, latestSha));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    static boolean isUpdateAvailable(String currentSha, String latestSha) {
        if (currentSha == null || currentSha.isEmpty() || "unknown".equalsIgnoreCase(currentSha)) return false;
        if (latestSha == null || latestSha.isEmpty()) return false;
        return !latestSha.equalsIgnoreCase(currentSha);
    }

    private static String fetchLatestMainSha() throws IOException, org.json.JSONException {
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
            return new JSONObject(sb.toString()).getString("sha");
        } finally {
            conn.disconnect();
        }
    }
}
