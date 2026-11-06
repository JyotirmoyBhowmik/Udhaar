# Udhaar v22.0 — Offline-First Personal Loan & Khata Android Application

**Udhaar** (`com.jyotirmoyb.udhaar`, `v22.0` / `versionCode = 22`, Room Schema `v12`) is an offline-first Android application built with Kotlin, Jetpack Compose, Hilt, SQLCipher-encrypted Room SQLite, and optional zero-trust Firebase Cloud Verification & Encrypted Google Drive Backup for tracking personal and party-based records (**Given / Owed** and **Taken / Borrowed**) across **Indian Rupees (`INR ₹`)** and **Nepalese Rupees (`NPR रू`)** in **5 languages** (`English`, `Hindi`, `Bengali`, `Assamese`, and `Nepali`) and **8 curated visual themes** (including *Emerald Slate*, *Warm Paper Khata*, *Pure Monochrome*, *Forest Ink*, *Royal Indigo*, *Terracotta Clay*, *Midnight Ledger*, and *Obsidian Gold*).

---

## 1. Key Features (v22.0)

- **Exact Paise Money Engine (`Long` Minor Units Only):**
  - Every financial record (`UdhaarTransaction`, `PartialPayment`, `InstallmentPlan`, `Installment`), DAO query, repository, interest calculator (`InterestCalculator`), installment schedule (`InstallmentCalculator`), group split calculator (`GroupSplitCalculator`), and backup/export pipeline (`LedgerBackupManager`) uses **`Long` paise** (`100 paise = ₹1.00 / रू1.00`) as the sole source of truth with `BigDecimal` + `RoundingMode.HALF_UP` arithmetic.
  - Safe Room migrations (`MIGRATION_10_11` and `MIGRATION_11_12`) convert legacy `Double` amounts to `Long` paise, rebuild tables without floating-point columns, and verify row counts and paise sums before committing.
- **Default-On R8 Shrinking, Narrow ProGuard Rules & `verifyReleaseR8` Guard:**
  - `isMinifyEnabled = true` and `isShrinkResources = true` are **enabled by default** on the `release` build type in `app/build.gradle.kts` (opting out is only permitted via `-Pudhaar.r8=false` for local non-bundle debug iterations).
  - The `verifyReleaseR8` Gradle task is wired as a mandatory dependency of `bundleRelease` / `packageReleaseBundle` and fails the build immediately if R8 minification or resource shrinking is disabled.
  - Narrow `app/proguard-rules.pro` keeps only Room `@Entity` / `@Dao` / `RoomDatabase` / `Migration` / `@TypeConverter` classes, Android manifest entry points, AssistedInject `ListenableWorker` constructors, and required SQLCipher JNI / Tink AEAD reflection targets while preserving `SourceFile,LineNumberTable` for Play Console & Crashlytics stack trace deobfuscation.
- **Hardware-Backed On-Device Security:**
  - Startup **Biometric Security Gate** (`BiometricPrompt` Fingerprint / Face Unlock + PBKDF2-HMAC-SHA256 salted PIN with exponential lockout backoff).
  - **SQLCipher 256-bit AES Room Database** (`udhaar_ledger.db`, Schema `v12`) + `EncryptedSharedPreferences` backed by Android Keystore `MasterKey`, with a self-healing schema verification and pre-migration emergency JSON export safeguard.
  - Optional `FLAG_SECURE` window protection and 1-tap **Privacy Masking (`₹ •••••`)** across cards, statements, and thermal receipts.
- **v19 Compact Dashboard & Adaptive Multi-Pane Layout:**
  - **Dashboard layout (`v19` placement):** Top header bar & pill tabs (`Active`, `People`, `History`) $\rightarrow$ global search bar & filter chips (`All`, `Owed`, `Borrowed`, `Overdue`, `Settled`) $\rightarrow$ compact `Total Receivable` / `Total Payable` / `Net Position` summary cards $\rightarrow$ dismissible `NeedsAttentionStrip` $\rightarrow$ `Records (N)` header with `+ Group Split` & sort/date-range selector $\rightarrow$ compact 2-row collapsible transaction cards.
  - **Adaptive Responsive UI:** Automatically switches to a **side-by-side list + detail two-pane layout** at widths $\ge 600\text{ dp}$, constrains Add/Edit forms to a centered $\le 560\text{ dp}$ container on tablets/foldables, and supports landscape mode.
  - **Accessibility & Touch Targets:** All interactive clickables enforce a $\ge 48\text{ dp}$ touch target (`minimumInteractiveComponentSize` / `AppIconButton` / `heightIn(min = 48.dp)`) with no fixed-height text buttons so labels scale cleanly up to `fontScale = 2.0`.
- **Installments, Group Splits, Statements & Reminders:**
  - Flexible installment schedules (`Weekly`, `Monthly`, `Custom`) with per-installment progress tracking and localized reminder templates in 5 languages.
  - Equal, custom-amount, and percentage **Group Splits** with penny-exact remainder allocation.
  - Per-party and full-ledger **Vector PDF Statements** (`PdfStatementGenerator`), **Thermal PNG Receipts** (`ThermalReceiptGenerator`), and two-way offline **ZXing UPI QR Cards** (`UpiQrGenerator`).
- **Mutual Cloud Verification, Guest Web Flow & Secret Manager:**
  - Counterparty verification links (`https://udhaar.jyotirmoyb.com/u/<token>`) and payment profile links (`https://udhaar.jyotirmoyb.com/u/p/<token>`).
  - Cloud Functions (`functions/guestWebFlow.js`, `functions/guestWebFlowEndpoints.js`) read `UDHAAR_SERVER_SECRET` exclusively via Firebase Secret Manager (`defineSecret("UDHAAR_SERVER_SECRET")`), failing closed if missing.
  - Zero-client-upload `storage.rules` (`allow create: if false`); proof uploads use server-issued V4 signed URLs enforcing MIME allow-listing (`image/jpeg`, `image/png`, `image/webp`, `application/pdf`), `x-goog-content-length-range: 0,5242880` (5 MB max), and a 90-day GCS lifecycle deletion policy (`storage.lifecycle.json`).
- **Encrypted Google Drive Backup, Recovery Key & Opt-In Telemetry:**
  - Client-side `AES-256-GCM` (PBKDF2 120,000 iterations) encrypted snapshots in Google Drive `appDataFolder`, plus a 24-character **Backup Recovery Key** (`UDHR-XXXX-...`) and an in-app **"Test my backup"** round-trip verification button.
  - **Crashlytics & Screen Analytics (`TelemetryManager`):** Strictly **OFF by default** until the user explicitly opts in under **Settings $\rightarrow$ Privacy & Lock**, with automatic PII scrubbing (`SafeLogger.redact`).

---

## 2. Codebase Structure & Modularity

All primary UI screen entry points are modularized to stay well under 600 lines:
- `app/src/main/java/com/jyotirmoyb/udhaar/MainActivity.kt` & `MainActivityDialogsAndHelpers.kt`
- `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/`:
  - `DashboardScreen.kt`, `DashboardHeaderAndSummary.kt`, `DashboardSearchAndFilter.kt`, `DashboardTransactionCard.kt`, `DashboardExpandedCardContent.kt`, `DashboardDetailPane.kt`
  - `PeopleDirectoryScreen.kt`, `PartyDirectoryCard.kt`, `PartyDetailScreen.kt`, `PartyDetailSummarySection.kt`
  - `AddEditUdhaarScreen.kt`, `AddEditPartyAndAmountSection.kt`, `AddEditTermsAndInterestSection.kt`, `AddEditAttachmentsAndDialogs.kt`
- `app/schemas/com.jyotirmoyb.udhaar.data.UdhaarDatabase/` — Exported Room schemas `3.json` through `12.json`.
- `.github/workflows/android-ci.yml` — GitHub Actions CI running:
  1. `android-quality-and-release`: Unit tests, Robolectric smoke tests, Android Lint, `ktlint`, and R8 `assembleRelease`.
  2. `bundle-release`: Decodes `RELEASE_KEYSTORE_BASE64`, runs `verifyReleaseR8` and signed `bundleRelease` with `STORE_PASSWORD` and `KEY_PASSWORD`, prints the AAB size and DEX size breakdown, and uploads `app-release.aab` and `app/build/outputs/mapping/release/mapping.txt` as build artifacts.
  3. `emulator-smoke-test`: Runs the 6-part release smoke test suite on the `pixel6Api34` Gradle Managed Device emulator (`pixel6Api34DebugAndroidTest`) and Robolectric JVM runner (`ReleaseSmokeTest`).
  4. `functions-and-rules-tests`: Runs Cloud Functions & Storage signed URL tests.

---

## 3. Documentation Index (`/docs/`)

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Modular MVVM architecture, Hilt DI modules, domain ViewModels, and repositories.
- [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) — Complete schema reference for Room SQLite tables (`v12`) and Cloud Firestore collections.
- [`docs/SECURITY.md`](docs/SECURITY.md) — Zero-PII logging (`SafeLogger`), Secret Manager configuration, Play Console permission declarations, and Google Play Data Safety documentation.
- [`docs/RELEASE_SMOKE_TEST_CHECKLIST.md`](docs/RELEASE_SMOKE_TEST_CHECKLIST.md) — Pre-release verification checklist, exact `bundleRelease` + `verifyReleaseR8` commands, and Play Console R8 / DEX verification steps.
- [`FIREBASE_DEPLOY.md`](FIREBASE_DEPLOY.md) — Deployment guide for Firebase Hosting, Firestore & Storage rules, Cloud Functions secrets (`firebase functions:secrets:set UDHAAR_SERVER_SECRET`), and GCS 90-day lifecycle rules.

---

## 4. Setup, Build, Release Bundle & Play Console R8 Verification

### Prerequisites
- Android SDK 36, JDK 11+
- Gradle (invoked directly via `gradle` in the workspace root)

### Exact Build & Release Bundle Commands
```bash
# 1. Compile Debug APK
gradle assembleDebug

# 2. Build Signed Release App Bundle (AAB) with R8 & Resource Shrinking (ON by default)
#    bundleRelease automatically executes verifyReleaseR8 first and fails if R8 is disabled.
gradle :app:verifyReleaseR8 :app:bundleRelease

# 3. Build Signed Release APK with R8 & Resource Shrinking (ON by default)
gradle :app:assembleRelease

# 4. Opt-out of R8 ONLY for fast local non-bundle debug runs (blocked on bundleRelease):
gradle :app:assembleRelease -Pudhaar.r8=false
```

### How to Confirm Shrinking & R8 Metadata in Google Play Console
1. **Verify locally before uploading (`app/build/outputs/bundle/release/app-release.aab`):**
   - Check that `app/build/outputs/mapping/release/mapping.txt` exists and is non-empty.
   - Run `unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "proguard\.map|classes.*\.dex"` to verify that `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` is embedded inside the AAB and that the `base/dex/classes*.dex` total is shrunk well below the previous 47 MB unminified DEX size.
2. **Confirm in Google Play Console after uploading the AAB:**
   - Navigate to **Google Play Console $\rightarrow$ Udhaar (`com.jyotirmoyb.udhaar`) $\rightarrow$ Test and release $\rightarrow$ App bundle explorer** and select version **`22 (22.0)`**.
   - **Details tab:** Verify that no *"unobfuscated code / missing deobfuscation file"* warning is shown.
   - **Downloads tab $\rightarrow$ Assets and files:** Confirm **ReTrace mapping file (`mapping.txt`)** appears as **Available / Uploaded** (bundled automatically from `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`; the standalone `app/build/outputs/mapping/release/mapping.txt` is also archived in CI artifacts).
   - **Downloads tab $\rightarrow$ Download size:** Confirm the compressed download size and DEX footprint reflect R8 code and resource shrinking.

### Run Unit, Release Smoke, Managed Emulator & Node Tests
```bash
# Run Release Smoke Test Suite (cold start, add record, restore backup, open guest link, widget update, v11->v12 migration, R8 verification)
gradle :app:testDebugUnitTest --tests com.jyotirmoyb.udhaar.ReleaseSmokeTest

# Run Release Smoke Test Suite on Gradle Managed Device Emulator (Pixel 6 API 34)
gradle :app:pixel6Api34DebugAndroidTest

# Run JVM Unit Tests (paise property tests, InterestCalculator, GroupSplit, Reminders, Style Guide)
gradle :app:testDebugUnitTest --tests com.jyotirmoyb.udhaar.ExampleUnitTest

# Run Full Robolectric Suite (Room migrations 1->12, SQLCipher encryption, fontScale 2.0 touch targets, adaptive 600dp layout, telemetry & backup round-trip)
gradle :app:testDebugUnitTest --tests com.jyotirmoyb.udhaar.ExampleRobolectricTest

# Run Cloud Functions & Storage Signed URL Tests
node --test functions/guestWebFlow.test.js
```

---

## 5. Privacy Policy & Account/Cloud Data Deletion

- **Live Privacy Policy URL:** [`https://udhaar.jyotirmoyb.com/privacy.html`](https://udhaar.jyotirmoyb.com/privacy.html) (source in `web/privacy.html`, also accessible in-app via **Settings $\rightarrow$ About $\rightarrow$ Privacy Policy**).
- **In-App Cloud Data Deletion:** Users can permanently delete all cloud-synced records, chat threads/messages, user profile documents, and revoke their FCM push token at any time via **Settings $\rightarrow$ Cloud Verification & Account $\rightarrow$ Delete My Cloud Data**.

---

## 6. Admin & Developer Contact

- **Administrator & Lead Developer:** `Jyotirmoy Bhowmik`
- **Support Email:** `admin@jyotirmoyb.com`
- **Production Domain:** `https://udhaar.jyotirmoyb.com`
