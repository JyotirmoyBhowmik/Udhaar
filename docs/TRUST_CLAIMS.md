# Udhaar Trust Center Claims & Technical Verification Proofs

Every public statement published on the Udhaar Trust Center (`/trust/`) and Landing Page is mathematically and technically verified against the open codebase. Below is the comprehensive proof mapping:

---

### Claim 1: "Your records stay locked on your phone"
* **Plain Language Statement**: All borrower and lender names, loan amounts, notes, payment history, and photo receipts are encrypted and stored locally in your phone's memory. We do not maintain a central database of your private credit records.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`: Encrypted SQLite via SQLCipher (`SupportOpenHelperFactory`).
  - `app/src/main/java/com/jyotirmoyb/udhaar/security/EncryptedStorageManager.kt`: Master database passphrase is generated inside Android Keystore hardware security module (`AndroidKeyStore`) and never transmitted.
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/DashboardScreen.kt`: Reads directly from on-device Room DAO queries.

---

### Claim 2: "Your backup password never leaves your phone"
* **Plain Language Statement**: When you create a backup, your phone encrypts your records with your personal backup password before uploading them to your personal Google Drive folder. We cannot read, decrypt, or reset this backup if you lose your password.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/backup/DriveBackupCrypto.kt`:
    - PBKDF2 with 600,000 iterations (`PBKDF2WithHmacSHA256`) and 16-byte random salt.
    - AES-256-GCM authenticated cipher with 12-byte IV and 128-bit authentication tag.
    - Plaintext password and derived encryption keys are wiped from memory immediately after encryption.
  - `docs/DATA_SAFETY_AND_PRIVACY.md`: Scoped to Google Drive `appDataFolder` (`drive.appdata`), isolated from general Drive files.

---

### Claim 3: "What the web page stores for a guest"
* **Plain Language Statement**: When you share a private web link, our server stores only the specific record you shared, a 15-minute verification code hash, and the phone number hash. It does not store your phone book, address, or other loans.
* **Technical Proof**:
  - `functions/guestWebFlow.js`:
    - Counterparty phone numbers are stored strictly as SHA-256 salted hashes (`computeSaltedPhoneHash`).
    - The link access secret exists only in the URL fragment (`#token`) on the guest browser and is never stored in Firestore database documents or sent in HTTP headers.
  - `firestore.rules`:
    - `/webLinks/{recordId}` documents are locked (`allow read, write: if false`) and accessible only via trusted Cloud Functions Admin SDK.

---

### Claim 4: "How long data is kept"
* **Plain Language Statement**: Shared web links expire automatically after 7 to 30 days (as chosen by the lender). WhatsApp verification codes expire after 15 minutes. Uploaded receipt photos on the server are automatically purged after 90 days.
* **Technical Proof**:
  - `functions/guestWebFlow.js`: Line 140 enforces 15-minute TTL on WhatsApp OTP codes (`codeCreatedAt + 15 * 60 * 1000`).
  - `functions/guestWebFlow.js`: Enforces `Date.now() > record.expiresAtMillis` reject checks.
  - `storage.lifecycle.json`: Cloud Storage rule `action: { type: "Delete" }, condition: { age: 90 }`.

---

### Claim 5: "How to delete your data"
* **Plain Language Statement**: You can delete any record inside the app at any time, which permanently erases it from your phone's database. If you uninstall the app, all local records are permanently wiped. Shared web links can be revoked instantly from the app or deleted using the online form on `/privacy.html`.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/RecordDetailSheet.kt`: One-tap delete with cascade removal of associated installments, attachments, and audit events.
  - `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/WebShareSheet.kt`: Instant "Revoke link" button invokes `guestWebRepository.revokeGuestWebLink(recordId)`.
  - `web/privacy.html`: Includes interactive deletion form routing to `deleteGuestRecord` endpoint.

---

### Claim 6: "What Udhaar does NOT do"
* **Plain Language Statement**:
  1. Udhaar does not move, transfer, or hold money. All payments happen directly through your UPI apps (GPay, PhonePe, Paytm, BHIM) or cash.
  2. Udhaar is not a bank, non-banking financial company (NBFC), or money lender.
  3. Records and web confirmation links are for personal mutual clarity and friendly record-keeping; they do not constitute legal contracts or credit bureau reports.
* **Technical Proof**:
  - `app/src/main/java/com/jyotirmoyb/udhaar/util/UpiQrGenerator.kt`: Generates standard `upi://pay?pa=...&am=...` deep links passed directly to external third-party UPI apps installed on the device.
  - Codebase contains zero financial transaction execution APIs, zero payment gateway SDKs, and zero payment processing gateways.
