# SafeNight Radar

Offline-first night-safety companion for late-night commutes. Built for the **CityFix** hackathon in 100% Kotlin + Jetpack Compose.

An ambient radar on a dark OpenStreetMap view turns the phones around you into a live crowd map — no API keys, no accounts, no location uploads — and gives one-tap access to a real emergency call.

## Download

- **APK (debug):** [`dist/safenight-radar-debug.apk`](dist/safenight-radar-debug.apk)
- Package `com.cityfix.safenight` · version 1.0 · minSdk 26 · targetSdk 35

Enable "Install unknown apps" for your file manager, then open the APK. Grant Bluetooth, location and Wi-Fi scan permissions on first launch.

## What it does

| Feature | How it works |
| --- | --- |
| **Crowd radar** | `BluetoothLeScanner` reads RSSI of nearby BLE devices and `WifiManager` scan results count visible APs. Counts roll into a salted SHA-256 bucket — no MAC address or device identity is ever stored. |
| **Night map** | OSMDroid with offline tile caching and a `ColorMatrixColorFilter` night palette, so the radar is usable without network and without a paid tile key. |
| **Zone status** | 0–15 m and 15–50 m rings are projected live from the map camera. Signal dots are placed by RSSI distance estimate; the glass status pill reads green **SAFE**, amber **MODERATE** or red **ISOLATED**. |
| **Panic SOS** | Starts a speakerphone call to `112` and fires DTMF (`112,,1`) into the IVR plus a GPS link by `SmsManager`, so the call and the coordinates both go out. |
| **10 s pre-roll audio** | `AudioRecord` keeps a rolling PCM buffer that is written to WAV at trigger time, preserving the ten seconds *before* the call connected — something `MediaRecorder` cannot do. |
| **Hackathon Test Mode** | Injects synthetic radar returns so judges can demo the radar indoors with Bluetooth off. |

## Architecture

MVVM with Coroutines and `StateFlow`. No blocking work on the main thread; sensor callbacks are funnelled through a single scanner coroutine.

```
ui/            MainRadarScreen, RadarViewModel, RadarUiState, components (map, overlay, dock, pill, SOS sheet)
radar/         RadarSensorManager  — BLE + Wi-Fi scanning, hashed device bucketing
location/      FusedLocationProviderClient wrapper
sos/           EmergencySosManager (call + SMS), PanicAudioBuffer (10 s PCM ring buffer)
```

## Build

Requires JDK 17 and Android SDK 35.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Privacy

No analytics, no backend, no account. Scan results are reduced to a rotating salted hash in memory and discarded when the radar stops. Location is used only for the map and the SOS message you trigger yourself.

## Known limitations

- BLE scanning needs the screen on; Android throttles background scans.
- Adjacent-device counts are approximate — RSSI is noisy indoors.
- The debug APK is unsigned for release; use `assembleRelease` with your own keystore to distribute.
