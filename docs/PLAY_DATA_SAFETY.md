# Google Play Data Safety Declaration — Udhaar (v32.0)

**Package Name:** `com.jyotirmoyb.udhaar`  
**Version:** `32.0` (`versionCode = 32`)  
**Canonical Source:** This file is the single source of truth for Google Play Data Safety declarations (duplicate `docs/play-data-safety.md` removed).  
**Application Architecture:** Offline-first personal ledger & Khata with hardware-backed local encryption (SQLCipher AES-256 + Android Keystore) and optional Firebase Spark (free tier) cloud verification & messaging.  
**Cloud Verification Gate Policy (`BuildConfig.REQUIRE_CLOUD_VERIFICATION`):** Defaults to `false`. On launch, Udhaar presents optional Cloud Verification (Google Sign-In or verified Email/Password) with an explicit **Continue locally without cloud sync** option so users can run the app 100% locally with zero account or cloud transmission. When a build sets `-Pudhaar.requireCloudVerification=true`, verified cloud sign-in is required before entering the ledger.  
**Non-Lending Declaration:** Udhaar is strictly a personal bookkeeping and ledger utility. Udhaar does NOT lend money, issue credit, facilitate loans, or process financial payments.

---

## 1. Executive Summary & Google Play Invariants

| Attribute | App Status | Architectural Proof / Code Location |
| :--- | :--- | :--- |
| **Data Shared with 3rd Parties** | **NO (0%)** | Zero advertising SDKs (`com.google.android.gms.permission.AD_ID` removed in `AndroidManifest.xml`). Zero data broker integrations. |
| **Data Encrypted in Transit** | **YES (100%)** | Enforced by `network_security_config.xml` (`cleartextTrafficPermitted="false"`), TLS 1.3 HTTPS to Firebase Auth & Firestore. |
| **Account & Data Deletion Mechanism** | **YES** | Automated in-app deletion (`AccountAndWebAccessSection.kt`) wiping local SQLCipher DB, Keystore keys, Google Drive backups, and Cloud Firestore documents; plus public web deletion URL (`https://udhaar.jyotirmoyb.com/delete-account`). |
| **Verified Email Cloud Access** | **YES (100%)** | `firestore.rules` enforces `request.auth.token.email_verified == true` (`isVerifiedSignedIn()`) on every user ledger, shared record, chat, and guest path. |
| **Target SDK Level** | **API 36** | `app/build.gradle.kts` (`targetSdk = 36`, `compileSdk = 36`). |

---

## 2. Google Play Data Safety Questionnaire Mappings

### 2.1 Personal Information

#### A. Name (User Display Name & Party Names)
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: App functionality (identifying Khata counterparties and labeling shared receipts).
- **Optional or Required?**: **Required** to create a person/party in the local Khata; optional on cloud sync when using local-only mode.
- **Encrypted in Transit?**: **Yes** (TLS 1.3 to Firestore; local SQLCipher AES-256 at rest).
- **Deletable?**: **Yes** (edit/delete individual parties or wipe everything via **Settings → Account and web access → Delete my account and data**).
- **Code Citations**: `UdhaarEntities.kt`, `InputValidator.kt`, `AccountAndWebAccessSection.kt`.

#### B. Phone Number
- **Collected?**: **Yes** (only if manually entered or picked by user)
- **Shared?**: **No**
- **Purpose**: App functionality (contact attribution and user-initiated WhatsApp/SMS reminder deep links). Stored strictly as unverified contact metadata; never used for SMS OTP login.
- **Optional or Required?**: **Optional**.
- **Encrypted in Transit?**: **Yes** (TLS 1.3; local SQLCipher AES-256).
- **Deletable?**: **Yes**.
- **Code Citations**: `UdhaarEntities.kt`, `InputValidator.kt`, `CommunicationSheets.kt`.

#### C. Email Address
- **Collected?**: **Yes** (Only when the user chooses to sign in for optional Cloud Verification, two-party chat, or Google Drive backup; not collected in local-only mode when `BuildConfig.REQUIRE_CLOUD_VERIFICATION = false`).
- **Shared?**: **No**.
- **Purpose**: Account management, verified-email Firestore access control (`email_verified == true`), and two-party verification.
- **Optional or Required?**: **Optional** in default builds (`BuildConfig.REQUIRE_CLOUD_VERIFICATION = false`); **Required** only in enterprise/managed builds with `REQUIRE_CLOUD_VERIFICATION = true`.
- **Encrypted in Transit?**: **Yes** (Firebase Authentication TLS 1.3).
- **Deletable?**: **Yes** (in-app deletion triggers `user.delete()` and purges all Firestore user collections).
- **Code Citations**: `CloudVerificationGateScreen.kt`, `AccountAndWebAccessSection.kt`, `TokenStore.kt`.

---

### 2.2 Financial Information

#### A. User Transaction & Repayment Records
- **Collected?**: **Yes**
- **Shared?**: **No** (shared only with the explicitly invited counterparty if the user opts into a two-party shared record or guest link).
- **Purpose**: Core App functionality (personal bookkeeping, exact paise balance calculations, installment plans, and partial payment logs).
- **Optional or Required?**: **Required** for recording entries.
- **Encrypted in Transit?**: **Yes** (TLS 1.3; local SQLCipher AES-256 at rest).
- **Deletable?**: **Yes**.
- **Code Citations**: `UdhaarEntities.kt`, `LedgerRepository.kt`, `GuestActionV1.kt`.

#### B. Other Financial Info (UPI IDs, Payment Modes, Reference Numbers)
- **Collected?**: **Yes** (when optionally entered by user)
- **Shared?**: **No**
- **Purpose**: Opening external third-party UPI apps (`upi://pay`) for peer-to-peer settlement. Udhaar never processes payments or stores banking credentials/PINs.
- **Optional or Required?**: **Optional**.
- **Encrypted in Transit?**: **Yes**.
- **Deletable?**: **Yes**.

---

### 2.3 Messages & Chat Safety Metadata

#### In-App Two-Party Chat Messages, Reports, Blocks & Mutes
- **Collected?**: **Yes** (only when user signs in with a verified email and participates in two-party record or direct chat).
- **Shared?**: **No** (accessible strictly to the two verified participants via `firestore.rules`).
- **Purpose**: App functionality (clarifying balances, requesting due-date extensions) and user safety (thread/user mute, block enforcement in `firestore.rules`, and abuse report reasons stored locally in `user_chat_safety` and in Firestore `/users/{uid}/blocked_users/{targetUid}`).
- **Optional or Required?**: **Optional** (unavailable in local-only mode).
- **Encrypted in Transit?**: **Yes** (TLS 1.3).
- **Deletable?**: **Yes** (senders can delete own messages; account deletion wipes user safety & block records).
- **Code Citations**: `CommunicationSheets.kt`, `ChatSafetyDialogs.kt`, `ChatRepository.kt`, `firestore.rules`.

---

### 2.4 Photos and Videos

#### Receipt Photos & Payment Proofs
- **Collected?**: **Yes** (only when explicitly captured or selected via Android Photo Picker `PickVisualMedia`).
- **Shared?**: **No**.
- **Purpose**: App functionality (attaching bill receipts or payment proofs).
- **Optional or Required?**: **Optional**.
- **Encrypted in Transit?**: **Yes** (EXIF-stripped WebP `< 680 KB` for guest proofs; app-private encrypted storage locally).
- **Deletable?**: **Yes**.

---

### 2.5 App Information, Diagnostics & Identifiers

- **Crash Logs & Diagnostics**: Collected only when user opts in via Settings (`TelemetryManager.kt`). All logs pass through `SafeLogger` / `AppLogger` PII scrubbing.
- **App Check / Play Integrity**: Release builds use `PlayIntegrityAppCheckProviderFactory` (`app/src/release/.../AppCheckProviderInstaller.kt`) for API fraud prevention.
- **Installation ID**: Random anonymous UUID (`TokenStore.kt`) used strictly for session/sync isolation; wiped on uninstall or data reset.

---

## 3. Account Deletion & Support Contacts

- **Public Web Deletion URL**: `https://udhaar.jyotirmoyb.com/delete-account`
- **Privacy Policy**: `https://udhaar.jyotirmoyb.com/privacy.html`
- **Support Contact**: `admin@jyotirmoyb.com`
- **Grievance Officer (DPDP Act 2023 & IT Rules)**: Jyotirmoy Bhowmik — `grievance@jyotirmoyb.com`
