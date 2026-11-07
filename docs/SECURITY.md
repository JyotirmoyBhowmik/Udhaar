# Udhaar Platform v2 — Production Security Architecture & Technical Specifications

**Document Version**: 2.0.0-PROD  
**Target Region**: `asia-south1` (Mumbai, India)  
**Security Classification**: CONFIDENTIAL & RESTRICTED  

---

## 1. On-Device Security & Local Storage (Android)

1. **Zero-Plaintext Local Database**:
   - `udhaar_ledger.db` is encrypted at rest using **SQLCipher (`net.zetetic:android-database-sqlcipher:4.5.4`)** with a randomly generated 256-bit key stored in `EncryptedSharedPreferences` backed by the Android Keystore hardware security module (`MasterKey.KeyScheme.AES256_GCM`).
   - Implemented in: `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`, `app/src/main/java/com/jyotirmoyb/udhaar/security/EncryptedStorageManager.kt`.
2. **PIN Protection & Key Derivation**:
   - User security PIN is never stored in plaintext. It is hashed with **PBKDF2-HMAC-SHA256** (60,000 iterations + 16-byte random salt) and verified in constant time (`MessageDigest.isEqual`).
   - Implemented in: `app/src/main/java/com/jyotirmoyb/udhaar/security/PinSecurityManager.kt`.
3. **Zero-PII Structured Logging**:
   - All client logging routes through `SafeLogger`, which automatically scrubs phone numbers, UPI IDs (`*@*`), currency amounts, verification tokens, emails, and PINs, and suppresses debug output in release builds (`!BuildConfig.DEBUG`).
   - Implemented in: `app/src/main/java/com/jyotirmoyb/udhaar/util/SafeLogger.kt`.
4. **Encrypted Google Drive Backups**:
   - Client-side authenticated encryption prior to upload to Google Drive `appDataFolder` (`drive.appdata` scope) using **AES-256-GCM** (PBKDF2 with 600,000 iterations, 16-byte random salt, 12-byte IV, 128-bit authentication tag). Derived keys and plaintext passwords are wiped from memory immediately.
   - Implemented in: `app/src/main/java/com/jyotirmoyb/udhaar/backup/DriveBackupCrypto.kt`.

---

## 2. Cloud Envelope Encryption & Key Management

Udhaar implements a two-tier Envelope Encryption architecture protecting all personal credit records in Cloud Firestore.

```
+------------------------------------------------------------------------+
| Google Cloud KMS (asia-south1)                                         |
| Key: projects/<id>/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek |
+------------------------------------------------------------------------+
                               | (wrap / unwrap via KMS API)
                               v
+------------------------------------------------------------------------+
| Per-User Data Encryption Key (DEK)                                     |
| 256-bit random AES key per user                                        |
| Stored wrapped in Firestore: users/{uid}/keys/current                  |
| Cached in memory with short TTL (5 minutes)                            |
+------------------------------------------------------------------------+
                               | (AES-256-GCM)
                               v
+------------------------------------------------------------------------+
| Field-Level Encrypted Ledger Attributes                                |
| Ciphertext + IV (12B) + Tag (16B)                                      |
| AAD bound to recordId:fieldName                                        |
+------------------------------------------------------------------------+
```

### Specifications:
1. **Master Key Encryption Key (KEK)**:
   - **KMS Location**: `asia-south1` (Mumbai).
   - **Resource Name**: `projects/<PROJECT_ID>/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek`.
   - **Algorithm**: `GOOGLE_SYMMETRIC_ENCRYPTION` (AES-256-GCM).
   - **Rotation**: Automated 90-day rotation schedule (`7776000s`).
   - **Infrastructure**: Provisioned via Terraform in `infra/terraform/main.tf` and gcloud scripts in `infra/scripts/setup-kms.sh`.
2. **Per-User Data Encryption Key (DEK)**:
   - **Generation**: Random 256-bit cryptographically secure key generated via `crypto.randomBytes(32)` during first write.
   - **Wrapping**: Wrapped by Cloud KMS in `asia-south1` before storage in Firestore document `users/{uid}/keys/current`.
   - **Key Versioning & Lazy Re-Wrapping**: Key document stores `keyVersion`, `kmsKeyName`, `createdAtMillis`, and `rotatedAtMillis`. When KEK rotates, DEKs are re-wrapped lazily on next write.
   - **Memory Caching**: Cached in an isolated in-memory LRU with a 5-minute TTL (`DEK_CACHE_TTL_MS = 300_000`).
   - **Crypto-Shredding**: On account deletion, `cryptoShredUserDek(uid)` permanently deletes the wrapped DEK from Firestore and purges memory cache, rendering all historic ciphertexts irreversibly unreadable.
3. **Field-Level Encryption**:
   - **Encrypted Attributes**: `partyName`, `notes`, `contactPhone`, `upiId`, `reminderNote`.
   - **Clear Attributes (Indexing & Sync)**: `recordType`, `currency`, `principalAmountPaise`, `amountPaidPaise`, `isSettled`, `isDeleted`, `dueDateMillis`, `updatedAtMillis`, `syncStatus`, `hlcTimestamp`, `nodeId`, `seq`.
   - **Cipher**: AES-256-GCM (`aes-256-gcm`).
   - **Additional Authenticated Data (AAD)**: Explicitly bound to `${recordId}:${fieldName}` to prevent field-swapping and ciphertext relocation attacks.
4. **Implementation Files**:
   - `functions/src/crypto/envelopeCrypto.js`: `getOrGenerateUserDek()`, `encryptField()`, `decryptField()`, `encryptRecord()`, `decryptRecord()`, `cryptoShredUserDek()`.
   - `functions/src/crypto/kmsProvider.js`: `wrapKey()`, `unwrapKey()`, `getKmsKeyName()`.
   - Tests: `functions/test/stage_r2_cryptography.test.js`.

---

## 3. Secret Manager Bindings & Runtime Invariants

1. **Secret Manager Declarations**:
   - All production secrets are defined using Cloud Functions v2 `defineSecret` and injected into the execution runtime environment:
     - `UDHAAR_SERVER_SECRET`: Master server secret for HMAC signing and session generation.
     - `UDHAAR_PHONE_PEPPER`: Secret pepper for phone number index hashes (`phoneIndex`).
     - `UDHAAR_EMAIL_PEPPER`: Secret pepper for email index hashes (`emailIndex`).
     - `META_APP_SECRET`: Meta App Secret for WhatsApp Cloud API webhook signature verification (`X-Hub-Signature-256`).
     - `META_ACCESS_TOKEN`: System User access token for WhatsApp Cloud API messaging.
     - `SMS_DLT_API_KEY`: Enterprise DLT SMS gateway authentication key.
     - `RESEND_API_KEY`: Production transactional email API key.
     - `RECAPTCHA_ENTERPRISE_API_KEY`: Google Cloud reCAPTCHA Enterprise assessment API key.
     - `ADMIN_SECRET_KEY`: Internal administrative authentication key.
2. **Least-Privilege Trigger Binding**:
   - Secrets are bound exclusively to the function instances that require them in `functions/index.js` (e.g., `whatsappWebhook` receives only `META_APP_SECRET`; `authApi` receives `UDHAAR_SERVER_SECRET`, `UDHAAR_PHONE_PEPPER`, and `RECAPTCHA_ENTERPRISE_API_KEY`).
3. **Production Fail-Closed Invariant**:
   - Under `NODE_ENV === "production"` or `"staging"`, `environmentGuard.js` verifies that zero fallback secrets, zero in-memory storage maps, and zero mock providers are configured. Any missing secret immediately halts function startup with a fatal error.
4. **Implementation Files**:
   - `functions/src/config/secrets.js`: `getSecret()`, `getAllSecrets()`.
   - `functions/src/config/environmentGuard.js`: `assertProductionSecurity()`.
   - `functions/index.js`: Cloud Functions v2 definitions with explicit secret dependencies.

---

## 4. Abuse Resistance, Rate Limits & Budget Controls

1. **Distributed Atomic Rate Limiting**:
   - Rate limit buckets are stored in Firestore under `rateLimits/{targetId}` with atomic transactions (`otpRepository.js`).
   - **Per-Destination Limit**: Maximum 3 code requests per 10 minutes per phone/email.
   - **Per-IP Limit**: Maximum 10 code requests per 10 minutes per client IP.
   - **Resend Cooldown**: Mandatory 30-second wait between code sends on the same flow.
   - **Lockout**: 5 consecutive incorrect code submissions locks the challenge permanently (`ERR_TOO_MANY_ATTEMPTS`).
2. **Global Daily Send Budget & Emergency Kill Switch**:
   - All WhatsApp/SMS sends increment a centralized daily counter in Firestore: `globalCounters/otpBudget`.
   - **Threshold Alerts**: Automated alerts fire at 50%, 80%, and 100% of the daily send budget.
   - **Emergency Kill Switch**: When budget reaches 100% or `emergencyKillSwitch === true`, all further code generation requests fail closed (`ERR_BUDGET_EXCEEDED`).
3. **Constant-Time Verification**:
   - OTP challenge hashes use HMAC-SHA256 (`crypto.createHmac("sha256", pepper)`).
   - Code verification compares submitted hashes using `crypto.timingSafeEqual` to eliminate timing side-channel attacks.
   - Sign-in and sign-up responses return identical payloads and uniform latency for registered and unregistered numbers to prevent enumeration.
4. **Implementation Files**:
   - `functions/src/otp/otpService.js`: `requestOtp()`, `verifyOtp()`, `computeOtpHash()`.
   - `functions/src/repositories/firestore/otpRepository.js`: `createChallenge()`, `verifyChallenge()`, `checkRateLimit()`, `checkGlobalBudget()`.
   - Tests: `functions/test/stage_r4_abuse_resistant_codes.test.js`.

---

## 5. Bot Mitigation: App Check & reCAPTCHA Enterprise

1. **Firebase App Check Enforcement**:
   - Validated on every public API endpoint via `getAppCheck().verifyToken(appCheckToken)`.
   - **Android Client**: Verified against Google Play Integrity API.
   - **Web Client**: Verified against Google Cloud reCAPTCHA Enterprise.
   - Staging environments support authorized debug tokens; production strictly rejects requests with missing or invalid tokens (`401 ERR_APP_CHECK_INVALID`).
   - Implemented in: `functions/src/security/appCheckMiddleware.js`.
2. **reCAPTCHA Enterprise Risk Assessment**:
   - Every verification code generation request from the web triggers a server-to-server assessment via Google Cloud reCAPTCHA Enterprise API (`projects/<id>/assessments`).
   - **Score Threshold**: Requests with risk scores below `0.70` or suspicious risk reasons are rejected immediately (`403 ERR_BOT_DETECTED`).
   - Implemented in: `functions/src/security/recaptchaEnterprise.js`.
3. **CSRF & Origin Verification**:
   - Double-submit CSRF token tied cryptographically to the session.
   - Strict `Origin` and `Sec-Fetch-Site` validation rejecting cross-origin state-mutating requests (`POST`, `PUT`, `DELETE`).
   - Implemented in: `functions/src/middleware/authMiddleware.js`.

---

## 6. Client Sessions & Web App Cache Hardening

1. **Firebase Admin Session Cookies**:
   - Web authentication uses `admin.auth().createSessionCookie(idToken, { expiresIn: 14 * 24 * 60 * 60 * 1000 })`.
   - Cookie attributes: `__session`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`.
   - Tokens verified server-side using `verifySessionCookie(cookie, true)` with `checkRevoked: true`.
   - Native Android clients use `admin.auth().createCustomToken(uid)`.
   - Implemented in: `functions/src/sessions/sessionManager.js`.
2. **Step-Up Authentication**:
   - Critical operations (account deletion, data export, phone/email change, revoking other devices) strictly enforce server-side step-up authentication. A fresh OTP or passkey verification must have succeeded within the last 10 minutes (`stepUpUntilMillis > Date.now()`).
3. **Encrypted Client Cache (Web)**:
   - Web application stores records locally in IndexedDB (`udhaar_web_db`) with AES-256-GCM authenticated encryption using keys derived via WebCrypto PBKDF2.
   - AAD bound to `udhaar:idb:${syncId}` protecting `partyName`, `notes`, `contactPhone`, `upiId`, and `reminderNote`.
   - Implemented in: `webapp/src/db/encryptedCache.js`, `webapp/src/db/ledgerDb.js`.
4. **Idle Lock & Public-Computer Mode**:
   - Inactivity tracker automatically engages a secure screen lock after 10 minutes (standard) or 2 minutes (public-computer mode).
   - Sign-out triggers a complete wipe of `localStorage`, `sessionStorage`, and IndexedDB partitions.
   - Implemented in: `webapp/src/app/AppShell.js`, `webapp/src/api/authClient.js`.

---

## 7. Administrative Security Governance & Lost-Number Cooling Period

1. **Site & Origin Isolation**:
   - Admin Console is deployed to an independent Firebase Hosting site target: `admin.udhaar.jyotirmoyb.com` (`infra/firebase.admin.json`).
   - Isolated security origin with strict Content-Security-Policy (zero inline scripts or styles).
2. **Admin RBAC, MFA & IP Allowlist**:
   - Admin routes require specific custom claims: `SUPER_ADMIN`, `COMPLIANCE_OFFICER`, `AUDITOR`, `SUPPORT_AGENT`.
   - Mandatory Multi-Factor Authentication: verified via `token.mfa_enforced === true`.
   - Mandatory IP Allowlist: incoming requests checked against `ADMIN_IP_ALLOWLIST` environment configuration (returns 403 `ERR_ADMIN_IP_NOT_ALLOWED`).
   - Mandatory Audit Reason: all state-mutating admin actions must supply `X-Admin-Reason` header logged to tamper-evident audit records.
   - Implemented in: `functions/src/middleware/adminMiddleware.js`.
3. **Zero-Access to Ledger Contents**:
   - Platform administrators are technically forbidden from reading user ledger entries. Any administrative API call attempting to inspect records returns 403 `ERR_ADMIN_LEDGER_ACCESS_DENIED`.
   - Implemented in: `functions/src/admin/adminEndpoints.js`.
4. **Non-Bypassable Lost-Number Cooling Period**:
   - When a user requests phone recovery or lost-number reassignment, an automated 30-day statutory cooling period is enforced (`coolingUntilMillis`).
   - System administrators are strictly prohibited from bypassing cooling periods. Administrative override attempts return 403 `ERR_ADMIN_COOLING_PERIOD_NON_BYPASSABLE`.
   - Implemented in: `functions/src/admin/adminEndpoints.js`, `functions/src/repositories/firestore/adminRepository.js`.
   - Tests: `functions/test/stage_r6_webapp_admin.test.js`.
