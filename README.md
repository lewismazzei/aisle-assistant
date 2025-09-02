# Aisle Assistant (Android)

A simple Android app to capture nearby Wi‑Fi access point information for items you record, with optional Wi‑Fi RTT distance (802.11mc) when supported. Data can be reviewed in‑app and exported to CSV.


## Prerequisites

- Android Studio (Giraffe or newer recommended)
- Android SDK Platform 34, Build‑Tools 34.x
- JDK 17 (Project is configured for Java/Kotlin 17)
- A physical Android device running Android 9 (API 28) or newer
  - For RTT distance: device must support Wi‑Fi RTT (802.11mc) and be running Android 9+.
  - Emulators generally do not support Wi‑Fi scans or RTT; use a real device.

Optional:
- `adb` on your PATH for CLI install
- `direnv` if you want to use the provided `.envrc` to manage SDK paths


## Clone

- Clone this repo and open it in Android Studio.

```
git clone <your-repo-url>
cd aisle-assistant
```


## Open in Android Studio

1. Open the `aisle-assistant` folder in Android Studio.
2. Let Gradle sync. The project uses:
   - compileSdk 34, targetSdk 34, minSdk 29
   - Kotlin/JVM target 17
3. If prompted, install missing SDK Platform 34 and Build‑Tools via the SDK Manager.


## Build and Run

### From Android Studio
- Connect a physical device with USB debugging enabled.
- Select your device and click Run. The default launch activity is `MainActivity`.

### From CLI
- Ensure `adb` sees your device: `adb devices`
- Build a debug APK: `./gradlew :app:assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`


## Runtime Permissions and Device Settings

The app requests the minimal set of permissions at runtime to perform Wi‑Fi scans and (optionally) Wi‑Fi RTT:

- Android 13+ (API 33+): `NEARBY_WIFI_DEVICES` (declared with `neverForLocation`).
- Android 12 and below: `ACCESS_FINE_LOCATION` (required by the OS to read scan results).

Notes:
- Make sure Wi‑Fi is enabled on the device.
- On Android 12 and below, Location services may need to be ON for Wi‑Fi scan results.
- For RTT distance: the device must support Wi‑Fi RTT and nearby APs must be RTT‑capable. The app ranges up to the first 3 access points per submit to improve stability.


## Using the App

- On launch, the app lists current nearby Wi‑Fi networks (no data is saved yet).
- Enter an item name and tap Submit to record a snapshot of nearby APs for that item.
  - If Wi‑Fi RTT is available, the app will attempt to retrieve distances and save them with the snapshot.
  - If RTT isn’t available or fails, the snapshot is saved without distances.
- Tap History to see recorded items, open an item to view its entries.
- Export CSV from the item detail screen or the history menu. Files are generated into app cache and shared via the Android share sheet.


## Data Storage

- Local SQLite database stored in app data.
- Tables:
  - `items(id, name)`
  - `wifi_entries(id, item_id, ssid, bssid, rssi, frequency, capabilities, distance_mm, distance_stddev_mm, created_at_ms)`
- Schema version: 2
  - v2 adds `distance_mm` and `distance_stddev_mm` to `wifi_entries`.
  - Existing installs migrate in place without data loss.
- To reset data, uninstall the app or clear its storage.


## Troubleshooting

- Build errors about missing SDK 34/Build‑Tools: install via Android Studio > Settings > SDK Manager.
- Gradle/JDK version mismatch: ensure JDK 17 is configured (Project Structure > SDK Location or set `JAVA_HOME`).
- No networks appear:
  - Ensure Wi‑Fi is on and runtime permission was granted.
  - On API < 33, enable device Location services.
- No distance shown:
  - Device or APs may not support Wi‑Fi RTT.
  - OS may report RTT unavailable in your environment.
- Export sharing fails: ensure a compatible app is chosen in the share sheet (e.g., Drive, Files, Email). The app uses a `FileProvider` with authority `${applicationId}.fileprovider`.
