# Google Play Data Safety Declaration Helper — Udhaar

**Package Name:** `com.jyotirmoyb.udhaar`  
**Application Architecture:** Offline-first personal ledger & Khata with hardware-backed encryption (SQLCipher AES-256 + Android Keystore) and Firebase Spark (free tier) zero-trust synchronization.  
**Non-Lending Declaration:** Udhaar is strictly a personal bookkeeping and ledger application. Udhaar does NOT lend money, issue credit, facilitate loans, or process financial payments.

---

## 1. Executive Summary & Google Play Invariants

| Attribute | App Status | Architectural Proof / Code Location |
| :--- | :--- | :--- |
| **Data Shared with 3rd Parties** | **NO (0%)** | Zero advertising SDKs (`com.google.android.gms.permission.AD_ID` explicitly removed in `AndroidManifest.xml`). Zero data broker integrations. |
| **Data Encrypted in Transit** | **YES (100%)** | Enforced by `network_security_config.xml` (`cleartextTrafficPermitted="false"`), TLS 1.3 HTTPS Google Cloud endpoints. |
| **Account & Data Deletion Mechanism** | **YES** | Automated in-app deletion (`btn_delete_account_and_data` in `AccountAndWebAccessSection.kt`), wiping local SQLite database, Keystore encryption keys, Google Drive backups, and Cloud Firestore documents; plus web deletion request URL (`https://udhaar.jyotirmoyb.com/delete-account`). |
| **Target SDK Level** | **API 36** | `app/build.gradle.kts` (`targetSdk = 36`, `compileSdk = 36`). |

---

## 2. Google Play Data Safety Questionnaire Mappings

### 2.1 Personal Information

#### A. Name (User's Name & Counterparty Party Names)
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: App functionality (identifying ledger counterparties and personalization on shared receipts).
- **Optional or Required?**: **Required** for creating a contact/party in the local ledger; optional nickname on guest web verification links.
- **Encrypted in Transit?**: **Yes** (HTTPS/TLS 1.3 to Firestore; fragment secret `#k=` on web links never touches HTTP headers).
- **Deletable?**: **Yes**. Users can edit/delete any party in the app, or tap **Settings → Account and web access → "Delete my account and data"** to permanently wipe all party names.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarEntities.kt` (lines 40–48: `partyName`, `lenderName`, `borrowerName`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/InputValidator.kt` (lines 14–22: `validateName` enforces 1..100 characters, sanitizes whitespace)
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt` (lines 280–350: batched Firestore cleanup)

#### B. Phone Number
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: App functionality (attributing transactions to contacts; sending local WhatsApp reminder deep links). Phone numbers are stored strictly as unverified local contact attributes and are **never** used for SMS OTP authentication on the Spark tier.
- **Optional or Required?**: **Optional**. Transactions can be recorded with just a party name without a phone number.
- **Encrypted in Transit?**: **Yes** (HTTPS/TLS 1.3 to Firestore; encrypted with AES-256 in local SQLCipher DB).
- **Deletable?**: **Yes**. Deleting the party or using in-app account deletion wipes all numbers.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarEntities.kt` (line 44: `phoneNumber`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/InputValidator.kt` (lines 25–48: `normalizePhoneNumber` format validation)
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/CommunicationSheets.kt` (lines 120–180: WhatsApp `https://wa.me/` client-side deep links)

#### C. Email Address
- **Collected?**: **Yes** (Only when user explicitly signs in to Firebase Spark Sync or Google Drive Backup).
- **Shared?**: **No**.
- **Purpose**: Account management & cloud synchronization access control.
- **Optional or Required?**: **Optional**. The app functions 100% offline without signing in or providing an email address.
- **Encrypted in Transit?**: **Yes** (Firebase Authentication TLS 1.3).
- **Deletable?**: **Yes**. In-app account deletion triggers `user.delete()` in Firebase Authentication and purges all user records.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt` (lines 80–126: `SparkAuthSession`, `FirebaseSparkAuthController`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/security/TokenStore.kt` (lines 15–30: Encrypted session storage)

---

### 2.2 Financial Information

#### A. User Payment & Transaction Records
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: Core App functionality (ledger bookkeeping, balance calculations, installment scheduling, payment acknowledgement).
- **Optional or Required?**: **Required** to record an entry in the ledger.
- **Encrypted in Transit?**: **Yes** (Encrypted in transit over HTTPS; encrypted at rest via SQLCipher AES-256).
- **Deletable?**: **Yes**. Individual records can be edited or deleted; complete deletion wipes the SQLite database and all cloud copies.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarEntities.kt` (lines 15–35: `UdhaarTransaction.principalAmountPaise`, `UdhaarPayment.amountPaise`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/GuestActionV1.kt` (lines 40–80: `GuestActionV1.ClaimPayment`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/repository/LedgerRepository.kt` (lines 60–120: atomic Room ledger balance mutations)

#### B. Other Financial Info (UPI IDs, Payment Modes, UTR Numbers)
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: Facilitating direct peer-to-peer settlement via user-initiated external UPI app deep links (`upi://pay`). Udhaar never handles banking credentials, card numbers, or PINs.
- **Optional or Required?**: **Optional**. Users can settle in cash or offline without UPI.
- **Encrypted in Transit?**: **Yes** (HTTPS/TLS 1.3).
- **Deletable?**: **Yes**. In-app account deletion purges all stored UPI IDs and references.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarEntities.kt` (lines 50–55: `PaymentMode` enum: `CASH`, `UPI`, `BANK_TRANSFER`)
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/ProfileUpiSection.kt` (lines 50–90: user UPI ID configuration)
  - `web/u/guest.js` (lines 2570–2610: opens external UPI application; zero payment processing)

---

### 2.3 Photos and Videos

#### Payment Proofs & Receipt Photos
- **Collected?**: **Yes** (Only when user or counterparty explicitly takes a photo or selects an image).
- **Shared?**: **No**.
- **Purpose**: App functionality (attaching proof of payment or bill receipts to a transaction).
- **Optional or Required?**: **Optional**.
- **Encrypted in Transit?**: **Yes**. Web guest proposals re-encode images to WebP ($&lt; 680\text{ KB}$), strip EXIF metadata, and write directly to Firestore `proof/image` over HTTPS. On Android, images are stored in encrypted app-internal storage.
- **Deletable?**: **Yes**. When the lender accepts or saves the proof to the local ledger, the Firestore document is deleted to free quota. "Delete my account and data" purges all attachment files.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/AttachmentManager.kt` (lines 40–90: local encrypted file storage)
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/PaymentProofDialog.kt` (lines 80–140: viewing and deleting proof documents)
  - `web/u/guest.js` (lines 1390–1430: canvas EXIF stripping, WebP compression, Firestore `proof/image` upload)

---

### 2.4 App Information and Performance

#### A. Crash Logs
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: Analytics and app stability diagnostics via Google Firebase Crashlytics.
- **Optional or Required?**: **Required** for diagnosing unexpected runtime failures.
- **Encrypted in Transit?**: **Yes** (Firebase Crashlytics TLS 1.3).
- **Deletable?**: **Yes** (Automatically expires per Firebase 90-day retention policy).
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/AppLogger.kt` (lines 45–95: automated zero-PII regex scrubbing of phone, email, amount, and secrets before Crashlytics dispatch)
  - `app/src/main/java/com/jyotirmoyb/udhaar/UdhaarApplication.kt` (lines 25–35: Crashlytics user ID set to anonymous UUID install ID, never personal information)

#### B. Diagnostics & App Check
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: Fraud prevention, security, and API integrity via Google Play Integrity and Firebase App Check.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/di/FirebaseModule.kt` (lines 40–58: `PlayIntegrityAppCheckProviderFactory`)

---

### 2.5 Device or Other Identifiers

#### Installation ID
- **Collected?**: **Yes**
- **Shared?**: **No**
- **Purpose**: App functionality & session isolation (identifying active local install to prevent sync collision).
- **Optional or Required?**: **Required**.
- **Encrypted in Transit?**: **Yes** (HTTPS).
- **Deletable?**: **Yes**. Resetting app data or uninstalling wipes the installation ID.
- **Code Citations**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/security/EncryptedTokenStore.kt` (lines 40–60: `getDeviceId()`)

---

## 3. Account Deletion URL Verification

- **Public Web Deletion URL**: `https://udhaar.jyotirmoyb.com/delete-account`
- **Hosting Source**: `web/delete-account.html`
- **Routing Configuration**: `firebase.json` rewrites `/delete-account` &rarr; `/delete-account.html`
- **Compliance Status**: Satisfies Google Play Account Deletion Policy requirement for in-app deletion path and web request mechanism with zero post-deletion data retention.
