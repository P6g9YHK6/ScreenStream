# Screen Stream

> **Experimental** — core functionality works but the project is under active development. Expect rough edges.

Stream your Android screen to any browser on your local Wi-Fi network. No accounts, no cloud, no cables. Open the app, tap Start, and share the URL anyone on the same network can watch immediately in Chrome, Firefox, Safari, or any modern browser.

<img width="2076" height="2152" alt="Screenshot" src="https://github.com/user-attachments/assets/5012f099-c2f5-4e8c-b504-1d371f19165c" />

---

## Status

This project is functional for its primary use cases but should be considered experimental:

- Live MJPEG video streaming works reliably on most devices
- Audio streaming works on Android 10+ but may have latency depending on network conditions
- Screen rotation and foldable display adaptation is implemented but may show brief freezes during the transition
- No authentication — anyone on the network can view the stream while it is running

---

## Features

- **Live video** — MJPEG stream viewable in any browser, no plugins required
- **Audio streaming** — captures device audio playback with configurable sample rate (16 / 22 / 44.1 / 48 kHz), channels (mono / stereo), and encoding (PCM 8-bit / 16-bit / 32-bit float)
- **Adjustable frame rate** — 5, 10, 15, 24, 30, or 60 fps
- **JPEG quality slider** — trade image sharpness for bandwidth
- **Configurable port** — change the HTTP port from the default 8080
- **Rotation and fold aware** — the viewer page adapts when you rotate or unfold the phone
- **Auto-reconnect** — both video and audio streams reconnect automatically if interrupted
- **No internet required** — everything stays on your local network

---

## Why a browser instead of Miracast or Chromecast

Miracast and Chromecast are the standard ways to mirror an Android screen to a display, but both come with a long list of failure modes:

- **Miracast** requires Wi-Fi Direct, which many routers, corporate networks, and hotel networks block or simply don't support. Some Android manufacturers ship broken or incomplete Miracast implementations. Compatibility between sender and receiver hardware is inconsistent, and the connection setup frequently fails silently.
- **Chromecast** requires a Google account, the Google Home app, and both devices to be on the same network segment. It does not work on networks that isolate clients from each other (common in offices, hotels, schools, and mobile hotspots). It also requires the display to have a Chromecast device attached.
- Both protocols have **codec negotiation** that can fail depending on the device, driver, or firmware version. When they fail, there is usually no useful error message.

ScreenStream sidesteps all of this. It runs a plain HTTP server on your phone and serves MJPEG video over a standard browser request. Any device with a browser and a network connection can view the stream: a laptop, a desktop, a smart TV with a browser, another phone. There is no pairing, no app install on the viewer side, no codec negotiation, no protocol handshake that can silently fail. If the browser can load a webpage, it can display the stream.

This makes it particularly useful in environments where casting protocols are unreliable: presentations from a phone to a projector connected to a laptop, streaming to a car screen over a mobile hotspot, or demonstrating an app on a network that blocks Wi-Fi Direct.

### Projector / presentation
Connect a laptop to the projector, open the stream URL in a browser. Present from your phone wirelessly — no HDMI dongle, no screen mirroring app on the laptop.

### Mobile game on a TV
Stream a game with audio to a smart TV browser or a laptop connected to the TV. Works with any game that plays audio through the system.

### Demo or support
Show someone exactly what is on your screen over a local network. Useful for tech support, app demonstrations, or walking someone through a process on the phone.

### Home dashboard
Leave the stream open on a wall-mounted screen or tablet to display a home automation dashboard, live camera feed, or any app running on the phone.

---

## Requirements

- Android 6.0 or newer (API 23+)
- Wi-Fi connection shared with the viewing device
- Audio capture requires Android 10 or newer (API 29+)

---

## Setup

```bash
git clone https://github.com/P6g9YHK6/AndroidScreenStream.git
cd AndroidScreenStream
```

## Build

### Prerequisites

- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK with API 34

### From Android Studio

1. Open the `ScreenStream` folder
2. Let Gradle sync complete
3. Connect a device with USB debugging enabled
4. Run → Run 'app'

### From the command line

```bash
cd ScreenStream
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

Every push builds both a debug and unsigned release APK, available as workflow artifacts for 30 days. Pushing a tag matching `v*` builds a signed release APK and attaches it to a GitHub Release automatically.

---

## Usage

1. Open **ScreenStream** on your Android device
2. Configure frame rate, quality, audio settings, and port as needed
3. Tap **Start Streaming** and accept the screen capture prompt
4. The app shows the stream URL — for example `http://192.168.1.42:8080`
5. Open that URL in any browser on the same Wi-Fi network

---

## Permissions

| Permission | Purpose |
| --- | --- |
| `FOREGROUND_SERVICE` / `MEDIA_PROJECTION` | Required to capture the screen while the app runs in the background |
| `INTERNET` | Serves the HTTP stream to other devices on the network |
| `RECORD_AUDIO` | Audio capture (Android 10+, optional) |
| `ACCESS_WIFI_STATE` | Reads the local IP address to display the stream URL |
| `POST_NOTIFICATIONS` | Error notifications on Android 13+ |

---

## Known limitations

- No encryption or authentication — the stream is accessible to anyone on the network
- Audio streaming is PCM over raw HTTP; some browsers may not support auto-play without a user gesture
- Maximum capture resolution is capped at 1280px on the long edge for performance
- Not tested on all Android versions or device configurations
