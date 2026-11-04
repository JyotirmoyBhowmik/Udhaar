# Udhaar — Google Play Data Safety & Privacy Declarations (v16.0)

**Application Package:** `com.jyotirmoyb.udhaar`  
**Effective Date:** November 4, 2026  
**Developer / Data Controller:** Jyotirmoy Bhowmik (`admin@jyotirmoyb.com`)  
**Target Release:** Production v16.0 (Target SDK 36, Min SDK 26)

---

## 1. Executive Summary & Privacy Principles

Udhaar is designed from the ground up as an **offline-first, zero-knowledge financial ledger**. All day-to-day transaction records, party profiles, and attachments are stored locally on the user's Android device inside an encrypted SQLCipher database.

Cloud features in Udhaar are **strictly opt-in** and user-initiated:
1. **Encrypted Google Drive Backup (`drive.appdata`):** Client-side encrypted with AES-256-GCM before upload. The developer has zero access to backups or encryption keys.
2. **Counterparty Mutual Verification & Chat:** Facilitated via Google Cloud Firestore and Firebase Cloud Messaging (FCM) with participant-only security rules.
3. **Guest Web Flow (`/u/`):** Allows an unauthenticated counterparty to review, confirm, or dispute a loan via their mobile browser using a zero-cost WhatsApp verification code without third-party tracking or advertising cookies.

---

## 2. Google Play Console — Data Safety Form Responses

When completing the **Play Console &rarr; Policy and programs &rarr; App content &rarr; Data safety** questionnaire, select the following exact responses:

### 2.1 General Assessment
| Question | Selection | Details / Technical Justification |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **Yes** | Financial info, names, phone numbers, and optional photos are processed when the user enables cloud verification or backup. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All network communications (Firebase Firestore, Storage, Cloud Functions, Google Drive) use TLS 1.3 / HTTPS exclusively. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes** | Self-service deletion is provided in-app (**Settings &rarr; Delete My Cloud Data**, **Delete my Drive backups**, **Revoke All Active Web Links**) and via web request at `admin@jyotirmoyb.com`. |

---

### 2.2 Data Categories & Declarations

| Data Category & Type | Collected? | Shared? | Purpose | Retention & Controls |
|---|---|---|---|---|
| **Financial Info &rarr; Other financial info**<br>(Loan amounts, repayment timestamps, currency, interest parameters) | **Yes**<br>*(Optional)* | **Yes**<br>*(Between Counterparties Only)* | • App functionality<br>• Account management | Stored locally in AES-256 SQLCipher DB. Shared with the counterparty via Firestore only when two-party verification is initiated. Never sold or shared with third parties or data brokers. |
| **Financial Info &rarr; User payment info**<br>(UPI Virtual Payment Address / VPA) | **Yes**<br>*(Optional)* | **Yes**<br>*(Between Counterparties Only)* | • App functionality | Masked in transit and UI (`ra*** ***ar@okaxis`). Used strictly to generate standard `upi://pay` intents for direct peer-to-peer settlement. |
| **Personal Info &rarr; Name**<br>(User name and party display name) | **Yes**<br>*(Optional)* | **Yes**<br>*(Between Counterparties Only)* | • App functionality<br>• Account management | Populated from local contacts (with permission) or entered manually. Visible to counterparty to identify who is asking for verification. |
| **Personal Info &rarr; Phone number**<br>(Counterparty mobile number) | **Yes**<br>*(Optional)* | **No**<br>*(Never Shared with Third Parties)* | • App functionality<br>• Fraud prevention & security | Stored as salted SHA-256 hash in cloud records; masked on public web links (`+91 98*** ***10`). Used solely to ensure only the intended counterparty can verify. |
| **Personal Info &rarr; User IDs**<br>(Google Account UID) | **Yes**<br>*(Optional)* | **No** | • Account management | Generated upon Google Sign-In for participant-level Firestore security rules. |
| **Photos and Videos &rarr; Photos**<br>(Receipts, physical bills, UPI payment screenshots) | **Yes**<br>*(Optional)* | **Yes**<br>*(Between Counterparties Only)* | • App functionality | Uploaded to private Firebase Storage bucket with participant-only security rules. Can be deleted at any time by the creator ("Delete guest data") or via automated 30-day retention cleanup. |
| **Messages &rarr; Other in-app messages**<br>(Two-party negotiation and payment notes) | **Yes**<br>*(Optional)* | **Yes**<br>*(Between Counterparties Only)* | • App functionality | Encrypted in transit; stored in Firestore `/chat_threads` with strict participant-only access rules. |
| **Device or other IDs &rarr; Device or other IDs**<br>(Firebase Cloud Messaging / FCM token) | **Yes**<br>*(Optional)* | **No** | • App functionality<br>• Push notifications | Used exclusively to route real-time transaction alerts. Automatically purged when unregistered or when user deletes cloud data. |

---

## 3. Cryptographic Storage & Encryption at Rest

### 3.1 Local Android Device Storage
1. **SQLCipher Database:** Local file `udhaar_ledger.db` is encrypted with 256-bit AES using SQLCipher.
2. **Android Keystore Integration:** The 256-bit database passphrase is generated securely using `SecureRandom` and stored in `EncryptedSharedPreferences` backed by `MasterKey.KeyScheme.AES256_GCM` in the Android Keystore.
3. **Application PIN Protection:** User PINs are hashed using **PBKDF2-HMAC-SHA256** with 60,000 iterations and a cryptographically unique 16-byte salt. PIN comparisons use constant-time `MessageDigest.isEqual`.

### 3.2 Cloud Firestore & Storage Encryption
1. **Server-Side Encryption:** All Cloud Firestore collections (`shared_records`, `webLinks`, `chat_threads`, `user_profiles`) and Cloud Storage buckets are encrypted at rest with Google-managed AES-256 keys.
2. **Audit Trail Immutability:** Audit trail documents (`/shared_records/{id}/audit/{eventId}`) are append-only. Firestore rules prohibit clients from modifying or deleting past audit logs.

### 3.3 Google Drive Backup Encryption
1. **Client-Side Encryption:** Backups uploaded to Google Drive `appDataFolder` (`drive.appdata` scope) are encrypted client-side using `AES/GCM/NoPadding` with a 256-bit key derived via `PBKDF2WithHmacSHA256` (120,000 iterations, 16-byte salt).
2. **Zero-Knowledge Architecture:** Google Drive acts solely as an encrypted blob store. Neither Google nor the app developer can read the backup contents without the user's secret passphrase.

---

## 4. Guest Web Flow & Zero-Cost Privacy Guarantees

### 4.1 Zero Carrier SMS & Zero Meta Cloud Charges
- Udhaar eliminates carrier SMS surcharges and Firebase Blaze SMS costs by generating a **6-digit WhatsApp OTP** alongside an embedded URL fragment secret (`https://udhaar.jyotirmoyb.com/u/r/RECORD_ID#SECRET`).
- The plain OTP and secret are passed directly into a pre-filled WhatsApp message draft on the creator's phone.
- Only the SHA-256 hash of the OTP and secret are sent to Firebase Cloud Functions.
- Brute-force attacks are thwarted through constant-time verification (`crypto.timingSafeEqual`) and strict lockout after 5 failed attempts per hour.

### 4.2 Web Tracking & Cookie Policy
- **Zero Third-Party Trackers:** Udhaar's web portal (`/u/index.html`) contains no Google Analytics, no Facebook SDK, no advertising cookies, and no tracking pixels.
- **Client-Side State Storage:** Browser storage (`localStorage` and `sessionStorage`) is used exclusively on the guest's device to maintain chosen UI language, theme, and verified session state.

### 4.3 Automated Retention & Purge Rules
- **Web Link Lifespan:** Links are configured by the creator to expire automatically after **7, 30, or 90 days**.
- **Immediate Revocation:** Creators can revoke any active link on-demand or use **Settings &rarr; Revoke All Active Web Links** to invalidate all links immediately.
- **Scheduled Retention Cleanup:** A daily Cloud Function (`cleanupExpiredGuestDataScheduled`) purges past-due web links (`expiresAtMillis < now`) and deletes orphaned payment claim proof images older than 30 days or marked resolved from Cloud Storage.

---

## 5. User Rights & Data Deletion Mechanisms

Udhaar provides multiple self-service methods to inspect and delete data:

1. **Delete All Cloud Data (Instant):**
   - In-App: **Settings &rarr; Cloud Verification & Account &rarr; Delete My Cloud Data**.
   - Cloud Functions purge `/user_profiles/{uid}`, authored `/shared_records`, and associated `/chat_threads`, and unregisters the FCM token.
2. **Delete Google Drive Backups:**
   - In-App: **Settings &rarr; Backup & Sync &rarr; Delete my Drive backups**.
   - Web: Users can delete Udhaar's hidden app data directly via [Google Drive Web](https://drive.google.com) &rarr; Settings &rarr; Manage Apps &rarr; Udhaar &rarr; Delete hidden app data.
3. **Manual Cloud Deletion Request:**
   - Users may send an email from their registered Google account to `admin@jyotirmoyb.com`. Cloud data associated with their email or phone number will be deleted within 48 hours.

---

## 6. Regulatory & App Store Compliance Checklist

- [x] **Google Play Target SDK:** Complies with Target SDK 36 (Android 16).
- [x] **No Exact Alarm Abuses:** `SCHEDULE_EXACT_ALARM` removed; uses `AlarmManager.setAndAllowWhileIdle` for standard reminders.
- [x] **Runtime Permission Disclosures:** Prominent in-app disclosures precede `READ_CONTACTS`, `CAMERA`, and `POST_NOTIFICATIONS`.
- [x] **No Third-Party Ad Networks:** Zero advertising SDKs present in APK.
- [x] **Safe Logging (`SafeLogger`):** Strips all PII (names, phone numbers, VPAs, amounts, tokens) from logs and disables debug logs in release builds.
