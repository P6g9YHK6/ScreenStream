package com.screenstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG          = "ScreenStream";
    private static final int    DEFAULT_PORT = 8080;

    private static final SecureRandom secureRandom = new SecureRandom();

    private TextView tvStatus, tvUrl, tvUrlLabel, tvUrlHint, tvQuality, tvFpsLabel, tvPin, tvUpdateStatus;
    private Button   btnToggle, btnFps5, btnFps10, btnFps15, btnFps24, btnFps30, btnFps60;
    private Button   btnPinRegen, btnBasicRegen, btnCheckUpdates;
    private SeekBar      seekBarQuality;
    private SwitchCompat switchAudio;
    private SwitchCompat switchAutoRestart;
    private SwitchCompat switchHttps;
    private Spinner  spinnerSampleRate, spinnerChannels, spinnerEncoding, spinnerAuthMode;
    private EditText editPort, editBasicUser, editBasicPass;
    private View     layoutPinRow, layoutBasicRow;

    private Settings settings;

    private boolean isStreaming        = false;
    private boolean autoRestart        = false;
    private int     selectedFps        = 24;
    private boolean audioEnabled       = true;
    private boolean httpsEnabled       = false;
    private int     selectedSampleRate = 44100;
    private int     selectedChannels   = 2;
    private int     selectedEncoding   = AudioFormat.ENCODING_PCM_16BIT;
    private int     selectedPort       = DEFAULT_PORT;
    private String  currentUrl         = "";

    private ScreenStreamService.AuthMode authMode  = ScreenStreamService.AuthMode.NONE;
    private String currentPin;
    private String basicUser;
    private String basicPass;

    private final BroadcastReceiver stoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScreenStreamService.ACTION_STOPPED.equals(intent.getAction())) {
                isStreaming = false;
                updateStatusStopped();
                if (autoRestart) {
                    requestCapturePermission();
                }
            }
        }
    };

    private final ActivityResultLauncher<Intent> projectionLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                startStreamingService(result.getResultCode(), result.getData());
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        });

    // RECORD_AUDIO is a dangerous permission and is required for AudioPlaybackCaptureConfiguration
    // even though it captures device playback, not the microphone. Without this, AudioRecord
    // construction throws SecurityException and audio silently never works.
    private final ActivityResultLauncher<String> audioPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!granted) {
                Toast.makeText(this, "Audio permission denied, streaming without audio", Toast.LENGTH_SHORT).show();
            }
            launchCapturePermission();
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        settings = new Settings(this);

        bindViewsAndListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        ErrorReporter.get().init(this);
        ErrorReporter.get().addListener(error -> {
            if (error.level == ErrorReporter.Level.ERROR || error.level == ErrorReporter.Level.FATAL) {
                runOnUiThread(() -> showErrorDialog(error));
            }
        });

        updateStatusStopped();
    }

    private void bindViewsAndListeners() {
        tvStatus          = findViewById(R.id.tv_status);
        tvUrl             = findViewById(R.id.tv_url);
        tvUrlLabel        = findViewById(R.id.tv_url_label);
        tvUrlHint         = findViewById(R.id.tv_url_hint);
        tvQuality         = findViewById(R.id.tv_quality);
        tvFpsLabel        = findViewById(R.id.tv_fps_label);
        btnToggle         = findViewById(R.id.btn_toggle);
        seekBarQuality    = findViewById(R.id.seekbar_quality);
        switchAudio       = findViewById(R.id.switch_audio);
        switchAutoRestart = findViewById(R.id.switch_auto_restart);
        spinnerSampleRate = findViewById(R.id.spinner_sample_rate);
        spinnerChannels   = findViewById(R.id.spinner_channels);
        spinnerEncoding   = findViewById(R.id.spinner_encoding);
        editPort          = findViewById(R.id.edit_port);
        btnFps5           = findViewById(R.id.btn_fps_5);
        btnFps10          = findViewById(R.id.btn_fps_10);
        btnFps15          = findViewById(R.id.btn_fps_15);
        btnFps24          = findViewById(R.id.btn_fps_24);
        btnFps30          = findViewById(R.id.btn_fps_30);
        btnFps60          = findViewById(R.id.btn_fps_60);
        spinnerAuthMode   = findViewById(R.id.spinner_auth_mode);
        layoutPinRow      = findViewById(R.id.layout_pin_row);
        layoutBasicRow    = findViewById(R.id.layout_basic_row);
        tvPin             = findViewById(R.id.tv_pin);
        btnPinRegen       = findViewById(R.id.btn_pin_regen);
        btnBasicRegen     = findViewById(R.id.btn_basic_regen);
        editBasicUser     = findViewById(R.id.edit_basic_user);
        editBasicPass     = findViewById(R.id.edit_basic_pass);
        switchHttps        = findViewById(R.id.switch_https);
        btnCheckUpdates    = findViewById(R.id.btn_check_updates);
        tvUpdateStatus     = findViewById(R.id.tv_update_status);

        setupQuality();
        setupFps();
        setupAudio();
        setupSpinners();
        setupPort();
        setupAutoRestart();
        setupAuth();
        setupHttps();
        setupUpdateCheck();

        btnToggle.setOnClickListener(v -> { if (isStreaming) stopStreaming(); else requestCapturePermission(); });
        tvUrl.setOnClickListener(v -> shareUrl());
        tvUrl.setOnLongClickListener(v -> { openInBrowser(); return true; });
    }

    private void setupQuality() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            seekBarQuality.setMin(10);
        }
        seekBarQuality.setMax(100);
        int savedQuality = settings.getInt(Settings.KEY_QUALITY, 70);
        seekBarQuality.setProgress(savedQuality);
        tvQuality.setText(savedQuality + "%");
        seekBarQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                int clamped = Math.max(10, p);
                tvQuality.setText(clamped + "%");
                ScreenStreamService.setJpegQuality(clamped);
                settings.putInt(Settings.KEY_QUALITY, clamped);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupFps() {
        View.OnClickListener click = v -> {
            if      (v == btnFps5)  setFps(5);
            else if (v == btnFps10) setFps(10);
            else if (v == btnFps15) setFps(15);
            else if (v == btnFps24) setFps(24);
            else if (v == btnFps30) setFps(30);
            else if (v == btnFps60) setFps(60);
        };
        for (Button b : new Button[]{btnFps5, btnFps10, btnFps15, btnFps24, btnFps30, btnFps60})
            b.setOnClickListener(click);
        setFps(settings.getInt(Settings.KEY_FPS, 24));
    }

    private void setFps(int fps) {
        selectedFps = fps;
        ScreenStreamService.setTargetFps(fps);
        settings.putInt(Settings.KEY_FPS, fps);
        tvFpsLabel.setText("FPS: " + fps);
        int active   = getColor(R.color.colorPrimary);
        int inactive = getColor(R.color.btnInactive);
        btnFps5.setBackgroundColor(fps == 5   ? active : inactive);
        btnFps10.setBackgroundColor(fps == 10 ? active : inactive);
        btnFps15.setBackgroundColor(fps == 15 ? active : inactive);
        btnFps24.setBackgroundColor(fps == 24 ? active : inactive);
        btnFps30.setBackgroundColor(fps == 30 ? active : inactive);
        btnFps60.setBackgroundColor(fps == 60 ? active : inactive);
    }

    private void setupAudio() {
        audioEnabled = settings.getBoolean(Settings.KEY_AUDIO_ENABLED, true);
        switchAudio.setChecked(audioEnabled);
        ScreenStreamService.setAudioEnabled(audioEnabled);
        switchAudio.setOnCheckedChangeListener((btn, checked) -> {
            audioEnabled = checked;
            ScreenStreamService.setAudioEnabled(checked);
            settings.putBoolean(Settings.KEY_AUDIO_ENABLED, checked);
        });
    }

    private void setupHttps() {
        httpsEnabled = settings.getBoolean(Settings.KEY_HTTPS_ENABLED, false);
        switchHttps.setChecked(httpsEnabled);
        ScreenStreamService.setHttpsEnabled(httpsEnabled);
        switchHttps.setOnCheckedChangeListener((btn, checked) -> {
            httpsEnabled = checked;
            ScreenStreamService.setHttpsEnabled(checked);
            settings.putBoolean(Settings.KEY_HTTPS_ENABLED, checked);
        });
    }

    private void setupSpinners() {

        String[] srLabels = {"16 kHz (low bandwidth)", "22 kHz (FM quality)",
                             "44.1 kHz (CD quality)",  "48 kHz (studio)"};
        int[]    srValues = {16000, 22050, 44100, 48000};

        ArrayAdapter<String> srAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, srLabels);
        srAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSampleRate.setAdapter(srAdapter);
        selectedSampleRate = settings.getInt(Settings.KEY_AUDIO_SAMPLE_RATE, 44100);
        spinnerSampleRate.setSelection(indexOf(srValues, selectedSampleRate, 2));
        ScreenStreamService.setAudioSampleRate(selectedSampleRate);
        spinnerSampleRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedSampleRate = srValues[pos];
                ScreenStreamService.setAudioSampleRate(selectedSampleRate);
                settings.putInt(Settings.KEY_AUDIO_SAMPLE_RATE, selectedSampleRate);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] chLabels = {"Mono", "Stereo"};
        int[]    chValues = {1, 2};

        ArrayAdapter<String> chAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, chLabels);
        chAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChannels.setAdapter(chAdapter);
        selectedChannels = settings.getInt(Settings.KEY_AUDIO_CHANNELS, 2);
        spinnerChannels.setSelection(indexOf(chValues, selectedChannels, 1));
        ScreenStreamService.setAudioChannels(selectedChannels);
        spinnerChannels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedChannels = chValues[pos];
                ScreenStreamService.setAudioChannels(selectedChannels);
                settings.putInt(Settings.KEY_AUDIO_CHANNELS, selectedChannels);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] encLabels = {"PCM 16-bit (standard)", "PCM 8-bit (small)", "PCM Float (high quality)"};
        int[]    encValues = {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT
        };

        ArrayAdapter<String> encAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, encLabels);
        encAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEncoding.setAdapter(encAdapter);
        selectedEncoding = settings.getInt(Settings.KEY_AUDIO_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        spinnerEncoding.setSelection(indexOf(encValues, selectedEncoding, 0));
        ScreenStreamService.setAudioEncoding(selectedEncoding);
        spinnerEncoding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedEncoding = encValues[pos];
                ScreenStreamService.setAudioEncoding(selectedEncoding);
                settings.putInt(Settings.KEY_AUDIO_ENCODING, selectedEncoding);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private static int indexOf(int[] values, int target, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) return i;
        }
        return fallback;
    }

    private void setupPort() {
        selectedPort = settings.getInt(Settings.KEY_PORT, DEFAULT_PORT);
        editPort.setText(String.valueOf(selectedPort));
        editPort.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validateAndApplyPort();
        });
        editPort.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validateAndApplyPort();
                editPort.clearFocus();
            }
            return false;
        });
    }

    private void setupAutoRestart() {
        autoRestart = settings.getBoolean(Settings.KEY_AUTO_RESTART, false);
        switchAutoRestart.setChecked(autoRestart);
        switchAutoRestart.setOnCheckedChangeListener((btn, checked) -> {
            autoRestart = checked;
            settings.putBoolean(Settings.KEY_AUTO_RESTART, checked);
        });
    }

    private void setupAuth() {
        authMode = parseAuthModeName(settings.getString(Settings.KEY_AUTH_MODE, ScreenStreamService.AuthMode.NONE.name()));

        currentPin = settings.getString(Settings.KEY_PIN, null);
        if (currentPin == null) {
            currentPin = generatePin();
            settings.putString(Settings.KEY_PIN, currentPin);
        }
        basicUser = settings.getString(Settings.KEY_BASIC_USER, "viewer");
        basicPass = settings.getString(Settings.KEY_BASIC_PASS, null);
        if (basicPass == null) {
            basicPass = generatePassword();
            settings.putString(Settings.KEY_BASIC_PASS, basicPass);
        }

        String[] labels = {"None", "PIN", "Basic Auth"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAuthMode.setAdapter(adapter);
        spinnerAuthMode.setSelection(authModeToIndex(authMode));
        tvPin.setText(currentPin);
        editBasicUser.setText(basicUser);
        editBasicPass.setText(basicPass);
        updateAuthRowsVisibility();

        spinnerAuthMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                authMode = indexToAuthMode(pos);
                settings.putString(Settings.KEY_AUTH_MODE, authMode.name());
                updateAuthRowsVisibility();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnPinRegen.setOnClickListener(v -> {
            currentPin = generatePin();
            settings.putString(Settings.KEY_PIN, currentPin);
            tvPin.setText(currentPin);
        });
        btnBasicRegen.setOnClickListener(v -> {
            basicPass = generatePassword();
            settings.putString(Settings.KEY_BASIC_PASS, basicPass);
            editBasicPass.setText(basicPass);
        });
        editBasicUser.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                basicUser = editBasicUser.getText().toString();
                settings.putString(Settings.KEY_BASIC_USER, basicUser);
            }
        });
        editBasicPass.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                basicPass = editBasicPass.getText().toString();
                settings.putString(Settings.KEY_BASIC_PASS, basicPass);
            }
        });
    }

    private static ScreenStreamService.AuthMode parseAuthModeName(String name) {
        try {
            return ScreenStreamService.AuthMode.valueOf(name);
        } catch (Exception e) {
            return ScreenStreamService.AuthMode.NONE;
        }
    }

    private void updateAuthRowsVisibility() {
        layoutPinRow.setVisibility(authMode == ScreenStreamService.AuthMode.PIN ? View.VISIBLE : View.GONE);
        layoutBasicRow.setVisibility(authMode == ScreenStreamService.AuthMode.BASIC ? View.VISIBLE : View.GONE);
    }

    private static int authModeToIndex(ScreenStreamService.AuthMode mode) {
        switch (mode) {
            case PIN:   return 1;
            case BASIC: return 2;
            default:    return 0;
        }
    }

    private static ScreenStreamService.AuthMode indexToAuthMode(int pos) {
        switch (pos) {
            case 1:  return ScreenStreamService.AuthMode.PIN;
            case 2:  return ScreenStreamService.AuthMode.BASIC;
            default: return ScreenStreamService.AuthMode.NONE;
        }
    }

    private void setupUpdateCheck() {
        tvUpdateStatus.setText("Version " + BuildConfig.VERSION_NAME);
        btnCheckUpdates.setOnClickListener(v -> runUpdateCheck());
    }

    private void runUpdateCheck() {
        tvUpdateStatus.setText("Checking…");
        UpdateChecker.checkForUpdate(new UpdateChecker.Callback() {
            @Override public void onResult(boolean updateAvailable, String latestVersion) {
                if (isFinishing() || isDestroyed()) return;
                if (updateAvailable) {
                    tvUpdateStatus.setText("Update available (" + latestVersion + ")");
                    showUpdateAvailableDialog(latestVersion);
                } else {
                    tvUpdateStatus.setText("Up to date (" + BuildConfig.VERSION_NAME + ")");
                    Toast.makeText(MainActivity.this, "You're on the latest version", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setText("Update check failed");
                Toast.makeText(MainActivity.this, "Couldn't check for updates", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpdateAvailableDialog(String latestVersion) {
        new AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Version " + latestVersion + " is available on GitHub.\nThis build is "
                + BuildConfig.VERSION_NAME + ".")
            .setPositiveButton("View on GitHub", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/P6g9YHK6/ScreenStream/releases"))))
            .setNegativeButton("Later", null)
            .show();
    }

    private String generatePin() {
        return String.format(Locale.US, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String generatePassword() {
        byte[] raw = new byte[9];
        secureRandom.nextBytes(raw);
        return android.util.Base64.encodeToString(raw,
            android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }

    private void validateAndApplyPort() {
        String text = editPort.getText().toString().trim();
        try {
            int port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show();
                editPort.setText(String.valueOf(selectedPort));
            } else {
                selectedPort = port;
                settings.putInt(Settings.KEY_PORT, selectedPort);
            }
        } catch (NumberFormatException e) {
            editPort.setText(String.valueOf(selectedPort));
        }
    }

    private void shareUrl() {
        if (currentUrl.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Watch my screen live: " + currentUrl);
        share.putExtra(Intent.EXTRA_SUBJECT, "Live screen stream");
        startActivity(Intent.createChooser(share, "Share stream URL"));
    }

    private void openInBrowser() {
        if (currentUrl.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)));
        } catch (Exception e) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Stream URL", currentUrl));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestCapturePermission() {
        validateAndApplyPort();
        if (audioEnabled && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
        } else {
            launchCapturePermission();
        }
    }

    private void launchCapturePermission() {
        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projectionLauncher.launch(mpm.createScreenCaptureIntent());
    }

    private void startStreamingService(int resultCode, Intent data) {
        Intent svc = new Intent(this, ScreenStreamService.class);
        svc.setAction(ScreenStreamService.ACTION_START);
        svc.putExtra(ScreenStreamService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(ScreenStreamService.EXTRA_DATA, data);
        svc.putExtra(ScreenStreamService.EXTRA_PORT, selectedPort);
        svc.putExtra(ScreenStreamService.EXTRA_QUALITY, seekBarQuality.getProgress());
        svc.putExtra(ScreenStreamService.EXTRA_FPS, selectedFps);
        svc.putExtra(ScreenStreamService.EXTRA_AUDIO, audioEnabled);
        svc.putExtra(ScreenStreamService.EXTRA_HTTPS, httpsEnabled);
        svc.putExtra(ScreenStreamService.EXTRA_AUDIO_SR, selectedSampleRate);
        svc.putExtra(ScreenStreamService.EXTRA_AUDIO_CH, selectedChannels);
        svc.putExtra(ScreenStreamService.EXTRA_AUDIO_ENC, selectedEncoding);
        svc.putExtra(ScreenStreamService.EXTRA_AUTH_MODE, authMode.name());
        svc.putExtra(ScreenStreamService.EXTRA_PIN, currentPin);
        svc.putExtra(ScreenStreamService.EXTRA_BASIC_USER, basicUser);
        svc.putExtra(ScreenStreamService.EXTRA_BASIC_PASS, basicPass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        isStreaming = true;
        updateStatusRunning();
    }

    private void stopStreaming() {
        Intent svc = new Intent(this, ScreenStreamService.class);
        svc.setAction(ScreenStreamService.ACTION_STOP);
        startService(svc);
        isStreaming = false;
        updateStatusStopped();
    }

    private void updateStatusRunning() {
        tvStatus.setText("● LIVE");
        tvStatus.setTextColor(getColor(R.color.green));
        btnToggle.setText("Stop Streaming");
        btnToggle.setBackgroundColor(getColor(R.color.red));
        currentUrl = (httpsEnabled ? "https://" : "http://") + getLocalIpAddress() + ":" + selectedPort;
        if (authMode == ScreenStreamService.AuthMode.PIN) {
            currentUrl += "/?pin=" + currentPin;
        }
        tvUrl.setText(currentUrl);
        tvUrl.setVisibility(View.VISIBLE);
        tvUrlLabel.setVisibility(View.VISIBLE);
        tvUrlHint.setVisibility(View.VISIBLE);
        setOptionsEnabled(false);
    }

    private void updateStatusStopped() {
        tvStatus.setText("○ STOPPED");
        tvStatus.setTextColor(getColor(R.color.gray));
        btnToggle.setText("Start Streaming");
        btnToggle.setBackgroundColor(getColor(R.color.colorPrimary));
        tvUrl.setVisibility(View.GONE);
        tvUrlLabel.setVisibility(View.GONE);
        tvUrlHint.setVisibility(View.GONE);
        currentUrl = "";
        setOptionsEnabled(true);
    }

    private void setOptionsEnabled(boolean on) {
        for (Button b : new Button[]{btnFps5, btnFps10, btnFps15, btnFps24, btnFps30, btnFps60})
            b.setEnabled(on);
        switchAudio.setEnabled(on);
        switchHttps.setEnabled(on);
        spinnerSampleRate.setEnabled(on);
        spinnerChannels.setEnabled(on);
        spinnerEncoding.setEnabled(on);
        editPort.setEnabled(on);
        switchAutoRestart.setEnabled(on);
        spinnerAuthMode.setEnabled(on);
        btnPinRegen.setEnabled(on);
        btnBasicRegen.setEnabled(on);
        editBasicUser.setEnabled(on);
        editBasicPass.setEnabled(on);
    }

    private String getLocalIpAddress() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    android.net.Network net = cm.getActiveNetwork();
                    if (net != null) {
                        android.net.LinkProperties lp = cm.getLinkProperties(net);
                        if (lp != null) {
                            for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                                java.net.InetAddress addr = la.getAddress();
                                if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                                    String host = addr.getHostAddress();
                                    if (host != null) return host;
                                }
                            }
                        }
                    }
                }
            } else {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    int ip = wm.getConnectionInfo().getIpAddress();
                    if (ip != 0) {
                        byte[] b = new byte[]{
                            (byte)(ip & 0xff), (byte)(ip >> 8 & 0xff),
                            (byte)(ip >> 16 & 0xff), (byte)(ip >> 24 & 0xff)};
                        return java.net.InetAddress.getByAddress(b).getHostAddress();
                    }
                }
            }
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    String host = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && host != null && !host.contains(":"))
                        return host;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "IP error", e);
        }
        return "0.0.0.0";
    }

    private void showErrorDialog(ErrorReporter.AppError error) {
        if (isFinishing() || isDestroyed()) return;
        String body = error.message + (error.detail != null && !error.detail.isEmpty()
            ? "\n\n" + error.detail.substring(0, Math.min(400, error.detail.length()))
            : "");
        String copyText = "[" + error.source.label + " / " + error.level.name() + "]\n" + body;
        new AlertDialog.Builder(this)
            .setTitle(error.source.label + " — " + error.level.name())
            .setMessage(body)
            .setPositiveButton("Dismiss", null)
            .setNegativeButton("Copy", (d, w) -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("ScreenStream error", copyText));
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Stop Stream", (d, w) -> { if (isStreaming) stopStreaming(); })
            .show();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // activity_main.xml has no orientation-specific variant, so there's nothing to
        // re-inflate here. Re-inflating on every rotation used to cause a visibly
        // double-rendered frame on lower-end devices, and could swallow the tap that was
        // meant to reach the "Start Streaming" button.
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ScreenStreamService.ACTION_STOPPED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stoppedReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stoppedReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stoppedReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isStreaming) stopStreaming();
    }
}
