# Udhaar v13.0 — Security Architecture, API Key Restrictions & Google Play Policy Compliance

## 1. On-Device & Cloud Security Overview

1. **Zero-Plaintext Local Storage:**
   - **Database:** `udhaar_ledger.db` is encrypted at rest using **SQLCipher (`net.zetetic:android-database-sqlcipher:4.5.4`)** with a randomly generated 32-byte (256-bit) key stored in `EncryptedSharedPreferences` backed by Android Keystore (`MasterKey.KeyScheme.AES256_GCM`).
   - **PIN Storage:** The user's security PIN is never stored in plaintext. It is hashed with **PBKDF2-HMAC-SHA256** (60,000 iterations + 16-byte random salt) and verified in constant time (`MessageDigest.isEqual`).
2. **Zero-PII Logging (`SafeLogger`):**
   - All log statements route through `SafeLogger`, which automatically scrubs phone numbers, UPI IDs (`*@*`), currency amounts, verification tokens, email addresses, and PINs before logging, and suppresses debug/warn output in release builds (`!BuildConfig.DEBUG`).
3. **Encrypted Google Drive Backup:**
   - Backups uploaded to Google Drive `appDataFolder` (`drive.appdata` scope) are encrypted client-side prior to upload using `AES/GCM/NoPadding` (256-bit key derived via `PBKDF2WithHmacSHA256`, 120,000 iterations, 16-byte random salt, 12-byte IV).

---

## 2. Securing the Public Firebase Client API Key (`firebase-applet-config.json` & `google-services.json`)

Firebase client API keys identify the Firebase project (`gen-lang-client-0216629575`) to Google servers and are not secret credentials, but **MUST** be restricted in Google Cloud Console so they cannot be abused outside your app or domain:

### Step-by-Step Restriction in Google Cloud Console
1. Open **[Google Cloud Console -> APIs & Services -> Credentials](https://console.cloud.google.com/apis/credentials?project=gen-lang-client-0216629575)**.
2. **Restrict the Android Client API Key:**
   - Under **Application restrictions**, select **Android apps**.
   - Click **Add an item** and add package name `com.jyotirmoyb.udhaar` for each certificate SHA-1 fingerprint:
     - Debug SHA-1: `FD:AD:95:C3:89:A1:C2:2A:28:18:C8:58:C9:65:C8:30:C7:57:04:AC`
     - Play App Signing SHA-1: *(from Play Console -> Setup -> App signing)*
   - Under **API restrictions**, select **Restrict key** and enable **ONLY**:
     - `Cloud Firestore API`
     - `Firebase Installations API`
     - `Firebase Cloud Messaging API` / `FCM Registration API`
     - `Identity Toolkit API`
     - `Token Service API`
3. **Restrict the Web Verification Fallback Key (used by `web/u/index.html`):**
   - Under **Application restrictions**, select **Websites (HTTP referrers)** and allow only:
     - `https://udhaar.jyotirmoyb.com/*`
     - `https://gen-lang-client-0216629575.web.app/*`
     - `https://gen-lang-client-0216629575.firebaseapp.com/*`
   - Under **API restrictions**, enable **ONLY** `Cloud Firestore API`, `Identity Toolkit API`, and `Token Service API`.

---

## 3. Android Permissions & Google Play Console Declarations

### A. `SCHEDULE_EXACT_ALARM` Removed (Policy Compliant)
- Google Play restricts `SCHEDULE_EXACT_ALARM` exclusively to alarm clock and calendar apps.
- **Resolution in v13.0:** `SCHEDULE_EXACT_ALARM` has been completely removed from `AndroidManifest.xml`. `ReminderScheduler` schedules repayment reminders using `AlarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ...)`, which works reliably across Android 8–16 without requiring special exact-alarm privileges or a Play Console declaration.

### B. `READ_CONTACTS` Prominent In-App Disclosure
- **When requested:** Only when the user explicitly taps **"Browse & Import Multiple Contacts"** in the People Directory or **"Import from Contacts"** in Settings.
- **Zero-permission alternatives provided:** Users can also pick a single contact via the system Contact Picker (`Intent.ACTION_PICK`) or import a `.vcf` vCard file without granting `READ_CONTACTS`.
- **In-app disclosure shown before the runtime permission prompt (`ContactsPermissionRationaleDialog`):**
  > *"Udhaar uses your contacts only on your device to help you quickly select a party's name and phone number when recording a loan or credit entry. Your address book is never uploaded to our servers unless you explicitly share a verification link with that contact."*

### C. `CAMERA` Prominent In-App Disclosure
- **When requested:** Only when the user taps **"Take Photo"** to attach a physical bill, receipt, or UPI payment screenshot to a transaction or partial repayment.
- **Zero-permission alternative provided:** Users can attach existing gallery photos via the Android Photo Picker (`PickVisualMedia`) or PDFs via Storage Access Framework (`OpenDocument`) without granting `CAMERA`.

---

## 4. Google Play Console — Data Safety Form Answers

Use the exact table below when completing **Play Console -> App content -> Data safety**:

| Question / Data Category | Selection | Details |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **Yes** | Only when the user signs in to optional Cloud Verification / Encrypted Drive Backup |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All Firestore, Auth, FCM, and Drive API traffic uses TLS 1.3 / HTTPS |
| **Do you provide a way for users to request that their data is deleted?** | **Yes** | In-app button: **Settings -> Cloud Verification & Account -> Delete My Cloud Data** (`deleteAllMyCloudData` deletes `/user_profiles/{uid}`, `/shared_records`, `/chat_threads`, and revokes the FCM token) + web instructions at `https://udhaar.jyotirmoyb.com/privacy.html#delete-data` |
| **Personal Info -> Name, Email address, Phone number, User IDs** | **Collected (Optional)** | Used for Account Management & App Functionality (counterparty verification & chat). **Not shared with third parties for advertising.** |
| **Financial Info -> Other financial info (Loan/ledger records & UPI VPA)** | **Collected (Optional)** | Stored locally in SQLCipher Room DB; synced only if the user enables Encrypted Google Drive Backup or shares a mutual verification record with a counterparty. |
| **Messages -> Other in-app messages** | **Collected (Optional)** | Per-party verification chat messages between consenting ledger counterparties. |
| **Photos and videos / Files and docs** | **Stored Locally / User’s Google Drive AppData** | Receipt attachments stay on-device and in the user's own encrypted Google Drive `appDataFolder`. |
| **Contacts** | **Processed Ephemerally On-Device** | Selected contacts are saved locally in the on-device People Directory; the address book is never bulk-uploaded. |
| **Device or other IDs (FCM Registration Token)** | **Collected (Optional)** | Used strictly to deliver push notifications for mutual verification updates and party chat messages. Deleted upon "Delete My Cloud Data". |
