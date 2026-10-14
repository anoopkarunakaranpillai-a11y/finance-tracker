# Building, signing and releasing

## Build outputs (automatic)

Every push to `main` that changes `android/` runs the **Android app** GitHub workflow. It does the following:

1. It builds `app-debug.apk`, `app-release.apk` (or `app-release-unsigned.apk`) and `app-release.aab`.
2. It runs all unit and UI tests and Android lint.
3. It boots an Android 14 emulator (Pixel 6) and runs the on-device tests. It then measures cold-start time three times.
4. It publishes the results to the `android-ci` branch and attaches the APK and AAB to the **android-latest** pre-release.

**To install on your phone:**

1. Open the repository's Releases page and find **android-latest**.
2. Download `app-debug.apk` and open it.
3. Allow "Install unknown apps" for your browser if Android asks.

The debug build installs next to the release build, as **com.anoop.financetracker.debug**.

## Building locally (Android Studio)

1. Install Android Studio (Ladybug or newer) with JDK 17.
2. Choose **File → Open** and select the `android/` folder. Gradle downloads everything on first sync.
3. Run the **app** configuration on a phone or emulator.

From a terminal:

```bash
cd android
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit + Robolectric UI tests
./gradlew connectedDebugAndroidTest  # on a connected device/emulator
./gradlew bundleRelease            # app/build/outputs/bundle/release/app-release.aab
```

## Release signing (one-time setup, done by you)

No keys are stored in the repository. Google Play needs an **upload key**:

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upload.jks > upload.jks.b64      # macOS: base64 -i upload.jks -o upload.jks.b64
```

In GitHub, go to **Settings → Secrets and variables → Actions → New repository secret** and add these four secrets:

| Secret | Value |
|---|---|
| `FT_KEYSTORE_BASE64` | contents of `upload.jks.b64` |
| `FT_KEYSTORE_PASSWORD` | keystore password |
| `FT_KEY_ALIAS` | `upload` |
| `FT_KEY_PASSWORD` | key password |

The next build then produces a signed `app-release.apk` and `app-release.aab`.

Keep `upload.jks` and its passwords somewhere safe outside the repo. With Play App Signing, Google holds the final app signing key, and the upload key can be reset through Play support if it is lost.

To sign locally instead, export `FT_KEYSTORE_PATH`, `FT_KEYSTORE_PASSWORD`, `FT_KEY_ALIAS` and `FT_KEY_PASSWORD` before running `./gradlew bundleRelease`.

## Google Play readiness checklist

| Item | Status |
|---|---|
| Android App Bundle | ✅ built by CI (`app-release.aab`) |
| Target SDK | ✅ 35 (Android 15). Lint notes that 36 is now available; update before Play's next yearly deadline. |
| Minimum SDK | 26 (Android 8.0) |
| Version code | ✅ CI uses the build number, so each upload is higher |
| Code shrinking | ✅ R8 minify and resource shrinking in release |
| Permissions | INTERNET and ACCESS_NETWORK_STATE (Study corner only), POST_NOTIFICATIONS (asked only when a reminder is switched on), USE_BIOMETRIC. No storage, location, contacts or SMS. |
| Cloud backup | ✅ disabled (`allowBackup=false` plus data-extraction rules); in-app encrypted backup instead |
| Cleartext traffic | ✅ blocked (network security config) |
| Signing | ⏳ needs your upload key (see above) |
| Store listing | ⏳ needs app name, short and full description, screenshots, a 512 px icon and a feature graphic |
| Privacy policy URL | ⏳ required by Play. A short page stating that finance data stays on the device and nothing is collected is enough; it can be hosted on the existing GitHub Pages site. |
| Data safety form | Suggested answers: no data collected or shared; data encrypted in transit (HTTPS) and at rest; users can delete data by uninstalling. |
| Content rating | Complete the questionnaire. The app contains a children's learning section, so answer the target-audience questions accordingly. If children are a target audience, Play's Families policy applies to the study pages. |
| Account and app access | No login. Mention the optional PIN lock in the reviewer notes. |
