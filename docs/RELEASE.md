# Udhaar — Production Release & Google Play Deployment Guide

This document outlines the end-to-end production build, signing, versioning, environment separation, and Google Play App Signing enrolment procedures for **Udhaar** (`com.jyotirmoyb.udhaar`).

---

## 1. Release Build Configuration

The release build is configured in [`app/build.gradle.kts`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/build.gradle.kts) and enforces the following production standards:

- **R8 Full Mode**: Enabled in [`gradle.properties`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/gradle.properties) via `android.enableR8.fullMode=true` for aggressive dead-code elimination, bytecode optimization, and class shrinking.
- **Code Minification & Resource Shrinking**:
  - `isMinifyEnabled = true`
  - `isShrinkResources = true`
  - Built-in guard task `verifyReleaseR8` ensures release bundles cannot be packaged if minification or resource shrinking is disabled.
- **Non-Debuggable**: `isDebuggable = false` in the `release` build type.
- **Debug Code Path Elimination**:
  - `BuildConfig.DEBUG` is false in release builds, automatically dropping cleartext `http://` deep-link testing schemes, localhost origins, and debug App Check tokens.
  - Logging via `AppLogger` automatically silences verbose, debug, and info messages in release builds and strips all PII (names, phone numbers, emails, notes, amounts).
- **ProGuard / R8 Keep Rules**: Defined in [`app/proguard-rules.pro`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/proguard-rules.pro):
  - **Room Database**: Retains `@Entity`, `@Dao`, `RoomDatabase`, and `TypeConverter` classes.
  - **Firebase Firestore Models**: Retains JavaBean properties, getters, setters, and annotations (`@PropertyName`, `@DocumentId`, `@ServerTimestamp`, `@Exclude`, `@IgnoreExtraProperties`) for `CloudSharedRecord`, `CloudUserProfile`, `GuestActionV1`, etc.
  - **Serialization**: Retains `@Serializable` classes and companion `$$serializer` instances for `SyncModels`.
  - **Native SQLCipher**: Preserves JNI reflection bridge classes in `net.zetetic.database.**` and `net.zetetic.database.sqlcipher.**`.
  - **WorkManager & Hilt**: Preserves `ListenableWorker` and `@HiltWorker` reflection constructors and dependency injection entry points.
  - **Crash Diagnostics**: Retains `SourceFile`, `LineNumberTable`, and line attributes for Crashlytics stack-trace re-tracing.

---

## 2. Signing & Google Play App Signing Enrolment

### A. Google Play App Signing Enrolment
Google Play App Signing splits signing responsibility into two keys:
1. **App Signing Key**: Generated and securely stored by Google on Google Cloud Key Management Service (KMS). Google uses this key to sign the final distribution APKs delivered to end-user devices.
2. **Upload Key**: Generated locally by the developer. Used exclusively to sign the Android App Bundle (`.aab`) before uploading it to Google Play Console. Google verifies the upload signature, replaces it with the app signing key, and serves the app.

#### Enrolment Steps:
1. Navigate to **Google Play Console** $\rightarrow$ **Release** $\rightarrow$ **Setup** $\rightarrow$ **App integrity**.
2. Under **Play App Signing**, choose *"Use Google-generated key"* (recommended for new apps) or export and upload an existing app signing key via PEPK tool.
3. Register your **Upload Certificate** in Google Play Console.
4. Copy the **SHA-256 fingerprint** of the App Signing key from Play Console and register it in **Firebase Console** $\rightarrow$ **Project Settings** $\rightarrow$ **Your Android Apps** $\rightarrow$ **SHA certificate fingerprints** (required for Google Sign-In and Play Integrity App Check to function in production).

### B. Upload Keystore Generation
To generate a production upload key:
```bash
keytool -genkey -v -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Udhaar Release, OU=Mobile, O=Udhaar, L=Kolkata, ST=West Bengal, C=IN"
```

### C. Keystore Credentials Configuration
Release signing configuration reads credentials dynamically from **environment variables** or [`local.properties`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/local.properties):

| Property / Environment Variable | Description | Example |
| :--- | :--- | :--- |
| `KEYSTORE_PATH` | Absolute or relative path to the `.jks` or `.keystore` file | `/secrets/my-upload-key.jks` |
| `KEYSTORE_PASSWORD` / `STORE_PASSWORD` | Password protecting the keystore file | `SecretPassword123` |
| `KEY_ALIAS` | Alias name of the key inside the keystore | `upload` |
| `KEY_PASSWORD` | Password protecting the specific key alias | `SecretPassword123` |

#### Setting in `local.properties` (Local Development):
```properties
KEYSTORE_PATH=my-upload-key.jks
KEYSTORE_PASSWORD=your_store_password
KEY_ALIAS=upload
KEY_PASSWORD=your_key_password
```

#### Setting in CI/CD (GitHub Actions / Cloud Build):
Pass secrets via environment variables in the build step:
```yaml
env:
  KEYSTORE_PATH: ${{ runner.temp }}/upload.jks
  KEYSTORE_PASSWORD: ${{ secrets.PLAY_KEYSTORE_PASSWORD }}
  KEY_ALIAS: ${{ secrets.PLAY_KEY_ALIAS }}
  KEY_PASSWORD: ${{ secrets.PLAY_KEY_PASSWORD }}
```

> [!IMPORTANT]
> **Keystore Security**: Keystore files and passwords must NEVER be committed to Git. [`.gitignore`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/.gitignore) strictly ignores `*.keystore`, `*.jks`, and `local.properties`. If credentials are omitted, the build automatically falls back to `debug.keystore` for local testing.

---

## 3. Versioning Strategy

Udhaar uses semantic versioning alongside incremental build numbers:

- **`versionCode`**: An integer incremented with every release.
  - Derived dynamically in order of preference:
    1. Environment variable `BUILD_NUMBER` (from CI/CD pipeline).
    2. Environment variable `GITHUB_RUN_NUMBER`.
    3. Gradle project property `-PversionCode=30`.
    4. Default fallback: `30`.
- **`versionName`**: Semantic version string `MAJOR.MINOR.PATCH` (e.g., `1.0.0`).
  - Derived from environment variable `VERSION_NAME`, Gradle property `-PversionName=1.0.0`, or defaults to `1.0.0`.
- **In-App Display**:
  - Displayed on the **Settings $\rightarrow$ About** screen (`settings_about_version_text`) in the format:
    `Udhaar records v1.0.0 (Build 30)`.

---

## 4. Environments & Firebase Project Separation

To ensure automated unit tests, Robolectric tests, and local development never touch or corrupt production Firestore collections, the project uses strict environment separation:

| Environment | Purpose | Firebase Project ID | Configuration Source |
| :--- | :--- | :--- | :--- |
| **Production** | Live end-user Google Play releases | `gen-lang-client-0216629575` | [`app/src/release/google-services.json`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/src/release/google-services.json) and [`app/google-services.json`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/google-services.json) |
| **Development** | Debug builds and manual device testing | `udhaar-spark-dev` | [`app/src/debug/google-services.json`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/src/debug/google-services.json) |
| **Automated Tests** | Robolectric & JUnit test suites | `demo-no-project` | `FirestoreEmulatorTestBase` routing to local emulator (`127.0.0.1:8085`) |

The Google Services Gradle plugin automatically selects `app/src/release/google-services.json` when building release bundles and `app/src/debug/google-services.json` when building debug APKs.

---

## 5. Producing the Android App Bundle (AAB)

To compile and package the production release bundle, execute:

### Exact Command:
```bash
./gradlew bundleRelease
```

*On Windows (PowerShell / Command Prompt):*
```powershell
.\gradlew.bat bundleRelease
```

### Output Location:
```text
app/build/outputs/bundle/release/app-release.aab
```

This bundle contains compiled DEX code minified with R8, compressed resources, the compiled ART baseline profile, and localized string configurations for all supported languages (`en`, `hi`, `bn`, `as`, `ne`).

---

## 6. Baseline Profile & Cold Start Performance

1. **Baseline Profile**:
   - Location: [`app/src/main/baseline-prof.txt`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/src/main/baseline-prof.txt).
   - Pre-compiles critical startup execution paths (`UdhaarApplication`, `MainActivity`, `UdhaarViewModel`, `UdhaarDatabase`, `EncryptedStorageManager`, Material3 theme composables, and `GuestActionV1` deserializers).
   - Improves cold-start application launch speed by up to 30% on Android 7.0+ devices via Ahead-Of-Time (AOT) compilation during installation.

2. **Cold Start StrictMode**:
   - Configured in [`UdhaarApplication.kt`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/app/src/main/java/com/jyotirmoyb/udhaar/UdhaarApplication.kt) under `BuildConfig.DEBUG`.
   - Thread policy detects disk reads, writes, and network operations on the main UI thread.
   - All Room database queries, SQLCipher key derivation, and cryptographic payload generation are dispatched strictly off the main thread onto background workers (`Dispatchers.IO`), ensuring smooth startup and zero main-thread blocking.

---

## 7. Release Smoke Test Verification Checklist

Before publishing the `.aab` to Google Play production:

1. [ ] Run automated test suite: `.\gradlew.bat testDebugUnitTest`
2. [ ] Build release bundle: `.\gradlew.bat bundleRelease`
3. [ ] Verify bundle size is within reasonable bounds (< 15 MB) and contains no unstripped debug symbols.
4. [ ] Verify App Check is enforced in Firebase Console for Firestore `(default)` database.
5. [ ] Verify Web guest portal is operational at `https://udhaar.jyotirmoyb.com/u`.
6. [ ] Install release build on physical test device:
   - App opens without crash.
   - Local encrypted database creates cleanly without main-thread ANR.
   - Create a transaction record.
   - Generate and share a guest link.
   - Verify guest link opens in browser and responds with payment claim or confirmation.
   - Accept the pending guest action in Android app and confirm status changes to Settled/Confirmed.
