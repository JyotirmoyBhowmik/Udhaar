# Udhaar Trust Center Claims & Technical Verification Proofs

Every public statement published on the Udhaar Trust Center (`/trust/`), Landing Page, and Documentation is mathematically and technically verified against the open codebase. Below is the comprehensive proof mapping:

---

### Claim 1: "Your records stay locked on your phone by default"
* **Plain Language Statement**: By default, Udhaar operates 100% offline-first. All borrower and lender names, amounts, notes, payment history, and photo receipts are encrypted with SQLCipher on your device. We do not maintain a central database of your private credit records. If you choose to turn on the optional Cloud Sync & Web Access feature, your data is protected by hardware Cloud KMS envelope encryption (AES-256-GCM) where even platform administrators cannot read your ledger contents, and sync can be turned off or deleted at any time.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`: Encrypted SQLite via SQLCipher (`SupportOpenHelperFactory`) under Room Schema v13.
  - `app/src/main/java/com/jyotirmoyb/udhaar/security/EncryptedStorageManager.kt`: Master database passphrase is generated inside Android Keystore hardware security module (`AndroidKeyStore`) and never transmitted.
  - `app/src/main/java/com/jyotirmoyb/udhaar/sync/TwoDeviceSyncTest.kt`: Proves feature flag `cloud_sync_enabled` is off by default with zero network calls made.
  - `functions/src/crypto/envelopeCrypto.js`: Envelope encryption with per-user 256-bit DEK wrapped in Google Cloud KMS (`asia-south1`).
  - `functions/src/admin/adminEndpoints.js`: Proves administrators are strictly denied access to ledger contents (403 `ERR_ADMIN_LEDGER_ACCESS_DENIED`).
  - **Tests**: `app/src/test/java/com/jyotirmoyb/udhaar/sync/TwoDeviceSyncTest.kt`, `functions/test/stage_r6_webapp_admin.test.js`, `functions/test/stage_r2_cryptography.test.js`.

---

### Claim 2: "Your backup password never leaves your phone"
* **Plain Language Statement**: When you create a backup, your phone encrypts your records with your personal backup password before uploading them to your personal Google Drive folder. We cannot read, decrypt, or reset this backup if you lose your password.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/backup/DriveBackupCrypto.kt`:
    - PBKDF2 with 600,000 iterations (`PBKDF2WithHmacSHA256`) and 16-byte random salt.
    - AES-256-GCM authenticated cipher with 12-byte IV and 128-bit authentication tag.
    - Plaintext password and derived encryption keys are wiped from memory immediately after encryption.
  - `docs/DATA_SAFETY_AND_PRIVACY.md`: Scoped to Google Drive `appDataFolder` (`drive.appdata`), isolated from general Drive files.
  - **Tests**: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt`.

---

### Claim 3: "What the web page stores for a guest"
* **Plain Language Statement**: When you share a private web confirmation link, our server stores only the specific record you shared, a 15-minute verification code hash, and the phone number hash. It does not store your phone book, address, or other loans.
* **Technical Proof**:
  - `functions/guestWebFlow.js`:
    - Counterparty phone numbers are stored strictly as SHA-256 salted hashes (`computeSaltedPhoneHash`) with a server secret pepper.
    - The link access secret exists only in the URL fragment (`#token`) on the guest browser and is never stored in Firestore database documents or sent in HTTP headers.
  - `firestore.rules`:
    - `/webLinks/{recordId}` documents are locked (`allow read, write: if false`) and accessible only via trusted Cloud Functions Admin SDK.
  - **Tests**: `functions/test/enterprise_tech.test.js`, `functions/test/stage_r2_cryptography.test.js`.

---

### Claim 4: "How long data is kept"
* **Plain Language Statement**: Shared web links expire automatically after 7 to 30 days (as chosen by the lender). WhatsApp verification codes expire after 15 minutes. Uploaded receipt photos on the server are automatically purged after 90 days. Deleted accounts undergo an irreversible crypto-shredding of encryption keys followed by a 30-day statutory cooling period.
* **Technical Proof**:
  - `functions/guestWebFlow.js`: Line 140 enforces 15-minute TTL on WhatsApp OTP codes (`codeCreatedAt + 15 * 60 * 1000`).
  - `functions/guestWebFlow.js`: Enforces `Date.now() > record.expiresAtMillis` reject checks.
  - `storage.lifecycle.json`: Cloud Storage rule `action: { type: "Delete" }, condition: { age: 90 }`.
  - `functions/src/privacy/privacyService.js`: Account deletion permanently crypto-shreds user DEK and enforces non-bypassable 30-day cooling period.
  - **Tests**: `functions/test/stage_r2_cryptography.test.js`, `functions/test/stage_r4_abuse_resistant_codes.test.js`.

---

### Claim 5: "How to delete your data"
* **Plain Language Statement**: You can delete any record inside the app at any time, which permanently erases it from your phone's database. Deleting the app wipes all local records. When signed into the web app, signing out completely wipes the encrypted browser cache. Shared web links can be revoked instantly from the app or deleted online at `/privacy.html`. Full account deletion permanently shreds your encryption keys so ciphertexts cannot ever be decrypted.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/RecordDetailSheet.kt`: One-tap delete with cascade removal of associated installments, attachments, and audit events.
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/WebShareSheet.kt`: Instant "Revoke link" button invokes `guestWebRepository.revokeGuestWebLink(recordId)`.
  - `webapp/src/api/authClient.js`: Sign-out triggers complete `localStorage`, `sessionStorage`, and `ledgerDb.clearAll()` IndexedDB cache wipe.
  - `functions/src/crypto/envelopeCrypto.js`: `cryptoShredUserDek` irreversibly purges wrapped DEK from Firestore and in-memory cache.
  - `web/privacy.html`: Includes interactive deletion form routing to `deleteGuestRecord` endpoint.
  - **Tests**: `functions/test/stage_r6_webapp_admin.test.js`, `functions/test/stage_r2_cryptography.test.js`.

---

### Claim 6: "What Udhaar does NOT do"
* **Plain Language Statement**:
  1. Udhaar does not move, transfer, or hold money. All payments happen directly through your UPI apps (GPay, PhonePe, Paytm, BHIM) or cash.
  2. Udhaar is not a bank, non-banking financial company (NBFC), or money lender.
  3. Records and web confirmation links are for personal mutual clarity and friendly record-keeping; they do not constitute legal contracts or credit bureau reports.
  4. Platform administrators cannot read your ledger contents, cannot bypass the cooling period, and cannot access user ciphertexts.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/UpiQrGenerator.kt`: Generates standard `upi://pay?pa=...&am=...` deep links passed directly to external third-party UPI apps installed on the device.
  - `functions/src/admin/adminEndpoints.js`: Strict 403 denial on ledger content reads (`ERR_ADMIN_LEDGER_ACCESS_DENIED`) and non-bypassable cooling period (`ERR_ADMIN_COOLING_PERIOD_NON_BYPASSABLE`).
  - Codebase contains zero financial transaction execution APIs, zero payment gateway SDKs, and zero payment processing gateways.
  - **Tests**: `functions/test/stage_r6_webapp_admin.test.js`, `app/src/test/java/com/jyotirmoyb/udhaar/ExampleUnitTest.kt`.
