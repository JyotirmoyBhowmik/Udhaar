# Udhaar v32.0 (Spark Mode) — Offline-First Personal Ledger & Khata

[![Udhaar CI/CD](https://github.com/JyotirmoyBhowmik/Udhaar/actions/workflows/ci.yml/badge.svg)](https://github.com/JyotirmoyBhowmik/Udhaar/actions/workflows/ci.yml)
[![Firebase Spark](https://img.shields.io/badge/Firebase_Plan-Spark_(Free)-039be5?logo=firebase)](docs/OPERATIONS.md)
[![Security: SQLCipher](https://img.shields.io/badge/Security-Fail--Closed_SQLCipher-2e7d32)](docs/SECURITY.md)
[![Google Play](https://img.shields.io/badge/Google_Play-Data_Safety-34a853?logo=googleplay)](docs/PLAY_DATA_SAFETY.md)

**Udhaar** (`com.jyotirmoyb.udhaar`, `versionName = "32.0"`, `versionCode = 32` via `version.properties`, Room Schema `v15`) is an offline-first personal ledger and Khata app for Android and the Web. It tracks **Given / Owed** and **Taken / Borrowed** records in **Indian Rupees (`INR ₹`)** and **Nepalese Rupees (`NPR रू`)** across **5 languages** (`English`, `Hindi`, `Bengali`, `Assamese`, and `Nepali`) and **8 visual themes**.

Udhaar runs 100% on the **Firebase Spark (Free) Tier** (`UDHAAR_TIER = "spark"`, `BLAZE_FEATURES_ENABLED = false`) using only Firebase Authentication, Cloud Firestore Security Rules & Indexes, and Firebase Hosting. Legacy prototypes have been consolidated under `archive/`.

---

## 1. Key Capabilities in v32.0

- **Explicit Cloud Verification Gate Setting (`BuildConfig.REQUIRE_CLOUD_VERIFICATION`):**
  - Defaults to `false` (`-Pudhaar.requireCloudVerification=false`), allowing users to either sign in with a verified Google/Email account for two-party cloud verification and in-app chat, or tap **Continue locally without cloud sync** to use the encrypted on-device Khata with zero account requirement.
  - Setting `-Pudhaar.requireCloudVerification=true` enforces mandatory cloud verification before entering the ledger, with matching localized copy in all 5 languages (`en`, `hi`, `bn`, `as`, `ne`).
- **Two-Party Chat Safety (User & Thread Report, Block, Mute):**
  - Users can **Mute**, **Block**, or **Report** both individual threads and counterparties (`user_chat_safety` in Room + `/users/{uid}/blocked_users/{targetUid}` and `/chat_threads/{threadId}` in Firestore).
  - `firestore.rules` strictly rejects message creation (`allow create`) whenever the sender is blocked at either the thread level or the recipient's user block list.
- **Verified Email Enforcement Across Every Cloud Path:**
  - `firestore.rules` enforces `request.auth != null && request.auth.token.email_verified == true` (`isVerifiedSignedIn()`) across every user ledger, shared record, chat thread/message, and guest link/action/proof path, verified by automated emulator rules tests and fuzz tests (`rules-tests/firestore-rules.test.js`).
- **Performance & Native Symbols:**
  - Stable keys on every `LazyColumn` / `LazyRow`.
  - History and search backed by **Paging 3** (`UdhaarDao.getSettledHistoryPaged`, `searchTransactionsPaged`, `searchPartiesPaged`) with narrow projection queries (`TransactionHistoryProjection`, `PartySearchProjection`) instead of `SELECT *`.
  - Dedicated `baselineprofile` module (`BaselineProfileGenerator` + `StartupBenchmarks`) and release native symbols (`ndk { debugSymbolLevel = "SYMBOL_TABLE" }`).
- **Accessibility & Localization:**
  - Audited `contentDescription` labels, semantic `Role` annotations on custom clickables, `>= 48 dp` touch targets, and automated Robolectric TalkBack + `fontScale = 2.0f` UI tests across Home, Record, People, and Chat screens.
  - 100% localized across English, Hindi, Bengali, Assamese, and Nepali with a CI test that fails on missing, blank, or untranslated keys.
- **Modular Codebase & Strict CI:**
  - `VerificationSheets`, `CommunicationSheets`, and `EncryptedStorageManager` split into focused files under 600 lines; all app logging routed through zero-PII `SafeLogger`.
  - GitHub Actions CI (`ci.yml`) runs Detekt (failing on any warning, `--max-issues 0`), Android Lint, JVM/Robolectric unit tests, Gradle Managed Device (`pixel6Api34`) running `ReleaseSmokeInstrumentedTest`, Firestore Rules Emulator tests, and Secret Scan, with `.github/dependabot.yml` keeping Gradle, npm, and GitHub Actions dependencies up to date.

---

## 2. Architecture Overview

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                        ANDROID APP (com.jyotirmoyb.udhaar)                   │
│                     versionCode = 32 | versionName = "32.0"                  │
│   REQUIRE_CLOUD_VERIFICATION = false (default) | CLOUD_FEATURES = true       │
│                                                                              │
│  ┌──────────────────────────────┐      ┌──────────────────────────────────┐  │
│  │ Jetpack Compose UI (M3)      │      │ Hardware-Backed Local Security   │  │
│  │ • Paging 3 Projections       │      │ • BiometricPrompt + PBKDF2 PIN   │  │
│  │ • >= 600 dp Two-Pane Layout  │      │ • SQLCipher 256-bit Room DB v15  │  │
│  │ • >= 48 dp Touch Targets     │      │ • EncryptedSharedPreferences     │  │
│  └──────────────┬───────────────┘      └─────────────────┬────────────────┘  │
│                 │                                        │                   │
│                 ▼                                        ▼                   │
│  ┌──────────────────────────────┐      ┌──────────────────────────────────┐  │
│  │ Exact Paise Engine (Long)    │      │ AES-256-GCM Backup Engine        │  │
│  │ • 100 paise = ₹1.00 / रू1.00 │      │ • Local .udhaar / JSON / PDF     │  │
│  │ • Simple/Compound Interest   │      │ • Google Drive appDataFolder     │  │
│  │ • Installments & Group Split │      │ • 24-char Recovery Key           │  │
│  └──────────────┬───────────────┘      └──────────────────────────────────┘  │
└─────────────────┼────────────────────────────────────────────────────────────┘
                  │  (Optional unless REQUIRE_CLOUD_VERIFICATION = true;
                  │   requires Firebase Auth email_verified == true)
                  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     FIREBASE SPARK (FREE TIER) BACKEND                       │
│     Deployed via: firebase deploy --only hosting,firestore:rules,indexes     │
│                                                                              │
│  ┌─────────────────────────┐  ┌───────────────────────────────────────────┐  │
│  │ Firebase Authentication │  │ Cloud Firestore (firestore.rules)         │  │
│  │ • Google Sign-In        │  │ • /users/{uid}/records|people|plans|      │  │
│  │ • Email/Password +      │  │   events|attachments|blocked_users        │  │
│  │   Verified Email Only   │  │ • /shared_records/{remoteId}/...          │  │
│  │                         │  │ • /chat_threads/{threadId}/messages/...   │  │
│  │                         │  │ • /guestLinks/{linkId}/actions/...        │  │
│  └─────────────────────────┘  └───────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Build, Test & Deploy Commands

### Build
```bash
# 1. Build Web Landing, Legal Pages, Guest Portal (/u/), and WebApp (/ledger/)
npm install --ignore-scripts
npm run build

# 2. Debug APK (local-first default: REQUIRE_CLOUD_VERIFICATION = false)
gradle :app:assembleDebug

# 3. Debug APK with mandatory cloud verification enabled
gradle :app:assembleDebug -Pudhaar.requireCloudVerification=true

# 4. Signed Release App Bundle (AAB) with R8, Resource Shrinking & Native Symbol Table
gradle :app:verifyReleaseR8 :app:bundleRelease
```

### Verification & Tests
```bash
# 1. Detekt static analysis (fails on any warning)
gradle :app:detekt

# 2. Android JVM & Robolectric tests (including TalkBack/fontScale 2.0 & 5-language i18n checks)
gradle :app:testDebugUnitTest

# 3. Gradle Managed Device instrumented smoke test
gradle :app:pixel6Api34DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jyotirmoyb.udhaar.ReleaseSmokeInstrumentedTest

# 4. Firestore Security Rules + Chat Safety + Fuzz Tests (Emulator)
npx firebase emulators:exec --project demo-udhaar-spark --only auth,firestore \
  "npm --prefix rules-tests test"

# 5. Launch Checklist & Web Tests
npm test
```

### Deploy (Firebase Spark)
```bash
npm run build
npx firebase deploy --only hosting,firestore:rules,firestore:indexes --project <YOUR_FIREBASE_PROJECT_ID>
```

---

## 4. Single-Source Documentation Index

| Topic | Canonical Document |
| :--- | :--- |
| **Architectural Truth Audit (v32.0)** | [`docs/REALITY_CHECK.md`](docs/REALITY_CHECK.md) |
| **Google Play Data Safety Declaration** | [`docs/PLAY_DATA_SAFETY.md`](docs/PLAY_DATA_SAFETY.md) |
| **Google Play Store Listing Copy** | [`docs/STORE_LISTING.md`](docs/STORE_LISTING.md) |
| **Launch Readiness Checklist** | [`docs/launch-checklist.md`](docs/launch-checklist.md) |
| **Operations & Spark Monitoring** | [`docs/OPERATIONS.md`](docs/OPERATIONS.md) |
| **Security & Vulnerability Policy** | [`docs/SECURITY.md`](docs/SECURITY.md) |
| **Privacy Policy (5 Languages)** | [`https://udhaar.jyotirmoyb.com/privacy.html`](https://udhaar.jyotirmoyb.com/privacy.html) |
| **Terms of Service (5 Languages)** | [`https://udhaar.jyotirmoyb.com/terms.html`](https://udhaar.jyotirmoyb.com/terms.html) |
| **Support & Grievance Contact** | Support: `admin@jyotirmoyb.com` · Grievance Officer: `grievance@jyotirmoyb.com` |
