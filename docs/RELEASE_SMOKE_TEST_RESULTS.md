# Udhaar Release Smoke Test Results Template

> **Purpose**: Structured sign-off record for every Play Store release candidate (`versionCode >= 28`, Spark Mode).  
> Fill in every row during pre-release verification and attach logs/screenshots before promoting a build from Internal Testing to Production.

---

## 1. Release Metadata

| Field | Value |
| :--- | :--- |
| **App Version** | `29.0` (`versionCode`: `29` or CI build `100 + GITHUB_RUN_NUMBER`) |
| **Application ID** | `com.jyotirmoyb.udhaar` |
| **Git Commit SHA** | `<fill-in-40-char-sha>` |
| **Build Artifact** | `app-release.aab` / `app-release.apk` |
| **Build Flags** | `CLOUD_FEATURES=false` (default), `R8=enabled`, `AppCheck=PlayIntegrity` |
| **Firebase Project** | `<demo-udhaar-spark / udhaar-prod>` (Spark Free Tier) |
| **Tester Name** | `<fill-in>` |
| **Date (UTC)** | `<YYYY-MM-DD>` |

---

## 2. Device & Emulator Matrix

| Environment | Device / AVD | Android API | ABI | Result |
| :--- | :--- | :---: | :---: | :---: |
| Physical Device 1 (Primary) | `<e.g., Pixel 7a>` | API 34+ | `arm64-v8a` | `[ ] PASS  [ ] FAIL` |
| Physical Device 2 (Low-RAM / Older API) | `<e.g., Redmi / Samsung>` | API 26–30 | `armeabi-v7a` / `arm64-v8a` | `[ ] PASS  [ ] FAIL` |
| Managed / Local Emulator | `Pixel 6 API 34` | API 34 | `x86_64` / `arm64-v8a` | `[ ] PASS  [ ] FAIL` |
| Large Screen / Tablet (600dp+) | `Pixel Tablet API 34` | API 34 | `x86_64` | `[ ] PASS  [ ] FAIL` |

---

## 3. Automated Pre-Flight Gates (CI / Local)

| Gate | Command | Expected | Actual |
| :--- | :--- | :--- | :--- |
| Release Versioning Guard | `gradle :app:verifyReleaseVersioning` | `versionCode >= 28`, matches `version.properties` | `[ ] PASS  [ ] FAIL` |
| No Debug App Check in Release | `gradle :app:verifyReleaseNoDebugAppCheck` | Zero `firebase-appcheck-debug` on release classpath | `[ ] PASS  [ ] FAIL` |
| Release R8 Minification | `gradle :app:verifyReleaseR8` | `mapping.txt` generated, zero missing classes | `[ ] PASS  [ ] FAIL` |
| Detekt Static Analysis | `gradle :app:detekt` | 0 new issues | `[ ] PASS  [ ] FAIL` |
| JVM & Robolectric Unit Suite | `gradle :app:testDebugUnitTest` | All unit & accessibility (`fontScale=2.0`) tests pass | `[ ] PASS  [ ] FAIL` |
| Firestore Rules Emulator Suite | `firebase emulators:exec --project demo-udhaar-spark --only firestore "npm --prefix rules-tests test"` | All 13 suites pass | `[ ] PASS  [ ] FAIL` |
| Web Build & Test Suite | `npm run build && npm run test:web && npm run test` | All suites & checklist checks pass | `[ ] PASS  [ ] FAIL` |
| Instrumented Smoke Suite | `gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jyotirmoyb.udhaar.ReleaseSmokeInstrumentedTest` | 6/6 smoke tests pass (with Firebase emulators running) | `[ ] PASS  [ ] FAIL` |

---

## 4. Core Release Smoke Test Journeys

### 4.1 Cold Start & Security Defaults
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke01_coldStartLaunchesMainActivityWithSecureDefaults`
- **Manual Check**:
  - [ ] Fresh install launches under 2 seconds without ANR or crash.
  - [ ] Onboarding / Dashboard renders cleanly in light mode, dark mode, and `fontScale = 2.0`.
  - [ ] SQLCipher 256-bit database file is encrypted on disk (non-plaintext SQLite header).
  - [ ] Telemetry / Crashlytics opt-in defaults to **OFF** on fresh install.
  - [ ] `CLOUD_FEATURES=false` hides unfinished cloud-only toggles cleanly.
- **Result**: `[ ] PASS  [ ] FAIL`
- **Notes / Cold-Start Time (ms)**: `<fill-in>`

### 4.2 Add a Ledger Record (Exact Paise Math)
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke02_addRecordPersistsExactPaiseInEncryptedLedger`
- **Manual Check**:
  - [ ] Create a new **Given** record (`₹12,500.50`) and record a partial payment (`₹2,500.25`).
  - [ ] Remaining balance displays `₹10,000.25` with zero floating-point rounding drift.
  - [ ] Force-stop and relaunch app; record and partial payment persist identically.
- **Result**: `[ ] PASS  [ ] FAIL`
- **Notes**: `<fill-in>`

### 4.3 Database Upgrade from v12 to v13
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke03_upgradeFromV12DatabasePreservesAllLedgerData`
- **Manual Check**:
  - [ ] Upgrade over an existing v12 Room database containing parties, transactions, partial payments, and installment plans.
  - [ ] `MIGRATION_12_13` completes with zero crashes or data loss.
  - [ ] Existing rows receive non-empty `syncId`, `syncStatus = LOCAL_ONLY`, `clientRev = 1`, `serverRev = 0`, and `sync_outbox` table exists.
- **Result**: `[ ] PASS  [ ] FAIL`
- **Notes**: `<fill-in>`

### 4.4 Encrypted Backup & Restore Round-Trip
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke04_encryptedBackupAndRestoreRoundTrip`
- **Manual Check**:
  - [ ] Export encrypted `.udhaar` backup with custom passphrase (`AES-256-GCM` + `PBKDF2 600k`).
  - [ ] Wrong passphrase fails cleanly with user-friendly error message.
  - [ ] Correct passphrase restores parties, transactions, and partial payments accurately via **Test my backup** / **Restore**.
- **Result**: `[ ] PASS  [ ] FAIL`
- **Notes**: `<fill-in>`

### 4.5 Home-Screen Widget
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke05_homeScreenWidgetUpdatesWithoutError`
- **Manual Check**:
  - [ ] Add Udhaar home-screen widget on launcher.
  - [ ] Add or settle a record in-app and verify widget updates totals without crashing or displaying stale values.
  - [ ] Tapping widget opens Udhaar cleanly.
- **Result**: `[ ] PASS  [ ] FAIL`
- **Notes**: `<fill-in>`

### 4.6 Two-Device Sync on Firebase Emulators (Spark Mode)
- **Automated Test**: `ReleaseSmokeInstrumentedTest.smoke06_twoDeviceSyncConvergesOnFirebaseEmulators`
- **Emulator Setup Command**:
  ```bash
  firebase emulators:start --project demo-udhaar-spark --only auth,firestore
  ```
- **Manual / Emulator Check**:
  - [ ] Unverified email account is rejected by `firestore.rules` (`PERMISSION_DENIED`).
  - [ ] Verified account signs in on Device A, creates record (`rev = 1`), drains outbox to Firestore emulator.
  - [ ] Device B pulls delta from Firestore emulator and converges with identical `syncId`, `principalAmountPaise`, and `SyncStatus.SYNCED`.
  - [ ] Device A updates record (`rev = 2`, strictly monotonic `rev == resource.data.rev + 1`), Device B pulls and updates cleanly.
  - [ ] Settings → **Delete my cloud data** clears Firestore Spark collections (`records`, `people`, `events`, `plans`, `attachments`, `meta`, `users/{uid}`, owned `guestLinks`) and resets local records to `LOCAL_ONLY`.
- **Result**: `[ ] PASS  [ ] FAIL  [ ] SKIPPED (explain why)`
- **Notes**: `<fill-in>`

---

## 5. Adaptive Layout, Legal & Release Hardening Checks

- [ ] **600dp+ Tablet / Foldable**: Two-pane List-Detail layout renders cleanly; forms stay centered at `<= 560.dp`.
- [ ] **Touch Targets & Font Scale 2.0**: No clipped buttons or interactive targets `< 48.dp`.
- [ ] **Help & Contact Screen**: Links to Terms of Service (`/terms.html`), Privacy Policy (`/privacy`), Support (`admin@jyotirmoyb.com`), and Grievance Officer (`grievance@jyotirmoyb.com`) work.
- [ ] **Play Integrity / API Key Restrictions**: Release API key in Google Cloud Console is restricted to `com.jyotirmoyb.udhaar` + Play App Signing SHA-1 and required Firebase APIs only (see `docs/PRODUCTION_FIREBASE_PROJECT.md`).

---

## 6. Issues Found & Triage

| # | Severity (`P0`/`P1`/`P2`) | Summary | Owner | Status |
| :---: | :---: | :--- | :--- | :---: |
| 1 | `<none>` | `<none>` | `<n/a>` | `<n/a>` |

---

## 7. Final Go / No-Go Sign-Off

- **Decision**: `[ ] GO FOR PRODUCTION   [ ] HOLD (BLOCKERS OPEN)`
- **Release Engineer**: `<name>` — `<date>`
- **QA Reviewer**: `<name>` — `<date>`
