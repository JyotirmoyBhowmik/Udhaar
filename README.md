# Udhaar v13.0 — Monochrome Offline-First Loan & Ledger Android Application

**Udhaar** (`com.jyotirmoyb.udhaar`, v13.0 / versionCode 13, Room Schema v7) is a strictly monochrome (`#000000` / `#FFFFFF`) offline-first Android application built with Kotlin, Jetpack Compose, SQLCipher-encrypted Room SQLite, and optional zero-trust Firebase Cloud Verification & Encrypted Google Drive Backup for tracking personal and party-based loans (**Given / Lent** and **Taken / Borrowed**) across **Indian Rupees (`INR ₹`)**, **Nepalese Rupees (`NPR रू`)**, **US Dollars (`USD $`)**, **Euros (`EUR €`)**, **British Pounds (`GBP £`)**, and **Bangladeshi Taka (`BDT ৳`)**.

---

## 1. Key Features (v13.0)

- **Hardware-Backed On-Device Security:**
  - Startup **Biometric Security Gate** (`BiometricPrompt` Fingerprint / Face Unlock + PBKDF2-HMAC-SHA256 salted PIN with exponential lockout backoff).
  - **SQLCipher 256-bit AES Room Database** (`udhaar_ledger.db`, Schema v7) + `EncryptedSharedPreferences` backed by Android Keystore `MasterKey`.
  - Optional `FLAG_SECURE` window protection against screenshots and task-switcher previews.
- **Multi-Currency Ledger & Dynamic Interest Engine:**
  - Split summary cards for **Receivable (Given)**, **Payable (Taken)**, and **Net Position** with instant currency tabs (`INR`, `NPR`, `USD`, `EUR`, `GBP`, `BDT`).
  - Simple & Compound Interest (`Monthly`, `Quarterly`, `Yearly` compounding; `% per month` or `% per annum`), partial repayments, and 1-tap settlement.
  - Immutable **SHA-256 Hash-Chained Audit Trail** (`audit_trail` table) for every record creation, edit, partial payment, and settlement.
- **People Directory & Per-Party Khata:**
  - Import contacts via Android's single-contact picker, bulk address-book picker (`READ_CONTACTS` with explicit rationale dialog), or `.vcf` vCard file import.
  - Per-party statements (Thermal PNG & vector PDF via `PdfStatementGenerator`), custom WhatsApp reminder templates, and two-way offline ZXing UPI QR codes (`UpiQrGenerator`).
- **Mutual Cryptographic Verification & Party Chat (`https://udhaar.jyotirmoyb.com/u/*`):**
  - Generate signed SHA-256 verification links (`https://udhaar.jyotirmoyb.com/u/<token>`) and payment profile links (`https://udhaar.jyotirmoyb.com/u/p/<token>`).
  - Real-time Firestore mutual confirmation (`UNVERIFIED` -> `PENDING_REMOTE` -> `CONFIRMED` / `DISPUTED`) and per-party chat threads with FCM push notifications.
- **Encrypted Google Drive AppData Backup (`DriveBackupWorker`):**
  - Client-side `AES-256-GCM` (PBKDF2 120,000 iterations) encrypted snapshots uploaded to Google Drive `appDataFolder` (`drive.appdata` scope).

---

## 2. Documentation Index (`/docs/`)

Detailed technical and compliance documentation is organized under `/docs/`:
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Modular MVVM architecture diagram, DI container (`UdhaarAppContainer`), domain ViewModels (`LedgerViewModel`, `VerificationViewModel`, `ChatViewModel`, `BackupViewModel`, `SettingsViewModel`), and domain repositories.
- [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) — Complete schema reference for all 7 Room SQLite tables (Schema v7) and 3 Cloud Firestore collections.
- [`docs/SECURITY.md`](docs/SECURITY.md) — Zero-PII logging (`SafeLogger`), Google Cloud API Key restriction guide (`firebase-applet-config.json`), Play Console permission declarations (`SCHEDULE_EXACT_ALARM` removal, `READ_CONTACTS`, `CAMERA`), and complete Google Play Data Safety questionnaire answers.
- [`FIREBASE_DEPLOY.md`](FIREBASE_DEPLOY.md) — Step-by-step deployment guide for Firebase Hosting (`udhaar.jyotirmoyb.com`), Firestore security rules, Cloud Functions, and Android App Links verification (`adb shell pm get-app-links com.jyotirmoyb.udhaar`).

---

## 3. Setup, Build & Test Instructions

### Prerequisites
- Android SDK 36, JDK 11+
- Gradle (invoked directly via `gradle` in the workspace root)

### Build Commands
```bash
# Compile Debug APK
gradle assembleDebug

# Compile Release APK / AAB
gradle assembleRelease
```

### Run Unit & Robolectric Tests
```bash
# Run JVM Unit Tests (InterestCalculator, VerificationCryptoHelper, SafeLogger, UdhaarResult)
gradle :app:testDebugUnitTest --tests com.jyotirmoyb.udhaar.ExampleUnitTest

# Run full unit & Robolectric suite
gradle :app:testDebugUnitTest

# Run Firestore Security Rules Emulator Tests
node --test firestore.test.js
```

---

## 4. Privacy Policy & Account/Cloud Data Deletion

- **Live Privacy Policy URL:** [`https://udhaar.jyotirmoyb.com/privacy.html`](https://udhaar.jyotirmoyb.com/privacy.html) (source in `web/privacy.html`, also accessible in-app via **Settings -> About -> Privacy Policy**).
- **In-App Cloud Data Deletion:** Users can permanently delete all cloud-synced records, chat threads/messages, user profile documents, and revoke their FCM push token at any time via **Settings -> Cloud Verification & Account -> Delete My Cloud Data**.

---

## 5. Admin & Developer Contact

- **Administrator & Lead Developer:** `Jyotirmoy Bhowmik`
- **Support Email:** `admin@jyotirmoyb.com`
- **Production Domain:** `https://udhaar.jyotirmoyb.com`
