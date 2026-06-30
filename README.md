# Smap Fingerprint Reader

Android app that lets [FieldTask](https://github.com/smap-consulting/fieldTask5) (and any other
app) capture a fingerprint from a **Mantra MFS500** USB scanner and receive the captured image
back. FieldTask launches this app via an intent, the user places a finger on the reader, and a
PNG of the fingerprint is returned to the caller.

It is packaged as a separate app rather than a library so the Mantra vendor SDK (with its USB
driver and native code) is isolated from the host app.

- Package / application id: `au.smap.smapfingerprintreader`
- Min SDK 24, target SDK 31, compile SDK 33
- License: Apache 2.0

## How it works

```
FieldTask  --(Intent au.smap.fingerprintreader.SCAN)-->  ScanActivity
   ^                                                          |
   |                                                  selects a Scanner
   |                                                  (MFS500 / Demo)
   |                                                          |
   |                                                  capture fingerprint
   |                                                  via Mantra MorfinAuth SDK
   |                                                          |
   +--(RESULT_OK, ClipData raw URI "fpr.png")---------  save PNG, return URI
```

1. `ScanActivity` is exported and handles the action `au.smap.fingerprintreader.SCAN`.
2. It reads the request parameters (see below) and the user's selected scanner (defaults to
   MFS500), built through `ScannerFactory`.
3. The selected `Scanner` connects to the device, captures the fingerprint, and produces a
   `Bitmap`.
4. The bitmap is written as a PNG to internal storage (`files/scan_images/`) and shared back
   through a `FileProvider`.
5. The activity finishes with `RESULT_OK` and an `Intent` whose `ClipData` holds the image URI,
   with `FLAG_GRANT_READ_URI_PERMISSION` set so the caller can read it.

### Source map

| Path | Role |
|------|------|
| `ScanActivity.java` | Entry point for the SCAN intent; drives the capture UI and returns the result |
| `MainActivity.java` | Launcher activity (mostly for standalone testing) |
| `application/FingerprintReader.java` | `Application` singleton holding shared state, parameters and logging |
| `model/ScannerViewModel.java` | `LiveData` for scanner state (`disconnected/connected/scanning/error`) and the captured image URI |
| `scanners/Scanner.java` | Abstract scanner interface (`connect/startCapture/isConnected/destroy`) |
| `scanners/ScannerFactory.java` | Returns a `Scanner` for the chosen name |
| `scanners/MFS500Scanner.java` | Mantra MFS500 implementation (default) using the MorfinAuth SDK |
| `scanners/MFS100Scanner.java` | Partial MFS100 implementation |
| `scanners/DemoScanner.java` | Returns a bundled `assets/sample.png` — for testing without hardware |
| `utilities/FileUtilities.java` | Saves the bitmap as PNG and returns a `FileProvider` URI |
| `fragments/SettingsFragment.java` + `res/xml/preferences.xml` | Lets the user choose the scanner |

## Calling it from FieldTask (the integration contract)

Launch `ScanActivity` for a result:

```java
Intent intent = new Intent("au.smap.fingerprintreader.SCAN");
intent.putExtra("type", "image");     // optional, defaults to "image"
intent.putExtra("quality", "60");     // optional, min capture quality 0-100, default 60
intent.putExtra("timeout", "10000");  // optional, capture timeout in ms, default 10000
startActivityForResult(intent, REQUEST_FINGERPRINT);
```

**Request extras** (all optional, all passed as strings):

| Extra | Default | Meaning |
|-------|---------|---------|
| `type` | `image` | Result type. Currently a fingerprint **image** is returned. |
| `quality` | `60` | Minimum acceptable capture quality. |
| `timeout` | `10000` | Capture timeout in milliseconds. |

**Result:** on success the activity sets `RESULT_OK` and returns an `Intent` with:

- `ClipData` containing a single raw URI labelled `fpr.png`, pointing at the captured PNG.
- `FLAG_GRANT_READ_URI_PERMISSION` so the caller can read the file.

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_FINGERPRINT && resultCode == RESULT_OK && data != null) {
        Uri uri = data.getClipData().getItemAt(0).getUri();
        // read the PNG via getContentResolver().openInputStream(uri)
    }
}
```

If the device cannot be reached or capture fails, the activity stays open and shows an on-screen
error log rather than returning a result.

## Building the APK

Requires Android Studio (or the command-line SDK) with an Android SDK that includes API 33.

```sh
# Point the build at your SDK (or set it in Android Studio)
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Debug build
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release build (unsigned unless you add signing config)
./gradlew assembleRelease

# Install onto a connected device
./gradlew installDebug
```

The Mantra SDK is vendored in `app/libs/` (`MorfinAuth.aar`, `mantra.mfs100.jar`) so no extra
download is needed to build.

## Installing and using with FieldTask

1. Build and install **both** apps on the same Android device.
2. Plug the MFS500 reader into the device's USB port (a USB-OTG adapter is usually required).
   Grant the USB permission prompt the first time.
3. In FieldTask, run a form that requests a fingerprint — it launches this app via the SCAN
   intent.
4. Place a finger on the reader. The captured fingerprint image is returned to FieldTask.

To try the capture flow **without hardware**, open this app directly and switch the scanner to
**Demo** in settings; it returns a bundled sample image.

## Forking / adding another scanner

1. Subclass `scanners/Scanner.java`.
2. In `connect`/`startCapture`, post state changes to `app.model.getScannerState()` and, when
   an image is ready, post its URI to `app.model.getImage()` (use `FileUtilities.getUri(...)` to
   turn a `Bitmap` into a shareable URI).
3. Register the scanner name in `scanners/ScannerFactory.java` and add it to the
   `scanners` string-array in `res/values/arrays.xml` so it appears in settings.

The default scanner is set in `res/xml/preferences.xml` (`defaultValue="MFS500"`) and as the
fallback in `ScannerFactory`.

## Notes

- The captured image is written to app-internal storage (`files/scan_images/`) and shared via
  the `FileProvider` authority `au.com.smap.FingerprintReader.fileprovider`.
- The MFS500 path produces a BMP from the SDK which is re-encoded as PNG before being returned.
- MFS100 support is only partially implemented; MFS500 is the supported device.
