# Udhaar Platform v2: STRIDE Threat Model & Security Architecture

- **Version**: 2.0.0
- **Classification**: Production Security Specification
- **Scope**: Udhaar Android, Web App (`app.udhaar.jyotirmoyb.com`), Marketing & Guest Portal (`udhaar.jyotirmoyb.com`), Cloud Functions v2, Firestore, Cloud Storage, and External Providers.

---

## 1. System Decomposition & Trust Boundaries

```
[ Unauthenticated User / Attacker ]
               │
      (Internet / TLS 1.3)
               ▼
┌──────────────────────────────────────────────┐ [Trust Boundary 1: CDN / Hosting Edge]
│ Firebase Hosting CDN                         │
│ - Strict CSP, HSTS, CoOP, CoRP, no-store     │
│ - Origin Separation: udhaar. / app.udhaar.   │
└──────────────────────┬───────────────────────┘
                       │ Rewrites /api/**
                       ▼
┌──────────────────────────────────────────────┐ [Trust Boundary 2: API Gateway / Functions]
│ Cloud Functions v2 (Node 20, asia-south1)     │
│ - App Check & Play Integrity                 │
│ - reCAPTCHA Enterprise                       │
│ - Zod Schema Validation                      │
│ - Rate Limiting & Quota Enforcement          │
│ - Session Verification & CSRF Checks         │
└───────┬──────────────┬──────────────┬────────┘
        │              │              │
        ▼              ▼              ▼
┌──────────────┐┌──────────────┐┌──────────────┐ [Trust Boundary 3: Storage & KMS]
│ Firestore    ││ Cloud Storage││ Cloud KMS    │
│ (Rules: Deny)││ (Signed URLs)││ (DEK Wrap)   │
└──────────────┘└──────────────┘└──────────────┘
```

---

## 2. STRIDE Threat Analysis by Component

### 2.1 Authentication & Identity System

| Threat Category | Threat Description | Severity | Mitigation Architecture | Verification / Test Case |
| :--- | :--- | :---: | :--- | :--- |
| **Spoofing** | Attacker intercepts or brute-forces 6-digit WhatsApp or email OTP codes to hijack an account. | **Critical** | 1. Codes are cryptographically generated via `crypto.randomInt`.<br>2. Only HMAC-SHA256 of code is stored in `otpChallenges/`.<br>3. Max 5 failed attempts before 15-minute lock.<br>4. 5-minute expiry for WhatsApp/SMS, 10-minute for email.<br>5. Constant-time comparison using `crypto.timingSafeEqual`. | `otp_bruteforce_lockout_test`<br>Assert lockout occurs after 5th incorrect attempt. |
| **Tampering** | Attacker reuses an OTP from a previous verification flow or cross-uses a phone code for email. | **High** | Challenge documents bind code strictly to a single `flowId`, `purpose` (`SIGNUP`, `SIGNIN`), and destination HMAC. Marked `consumed: true` upon first use within an atomic Firestore transaction. | `otp_cross_flow_replay_test`<br>Assert code rejected when submitted with different `flowId` or purpose. |
| **Repudiation** | User denies performing high-risk actions (account deletion, data export, phone change). | **High** | Mandatory Step-Up Authentication: requires a fresh email OTP or WebAuthn passkey assertion within 10 minutes. Action is written to immutable `auditLogs/` with timestamp, actor UID, and client IP hash. | `step_up_auth_enforcement_test`<br>Assert sensitive endpoint returns 403 without recent step-up token. |
| **Information Disclosure** | Attacker enumerates registered phone numbers or email addresses by observing sign-in response differences. | **High** | Constant-time response: `POST /auth/signin/start` returns identical HTTP 200 payload and uniform timing regardless of whether the phone number exists in `phoneIndex/`. No user existence leakage. | `signin_timing_enumeration_test`<br>Assert response body and timing variance $<50\text{ms}$ across existing and non-existent users. |
| **Denial of Service** | Botnet floods OTP endpoints to exhaust WhatsApp budget or lock out legitimate users (SMS/WhatsApp bombing). | **High** | 1. Global daily send budget with hard kill switch.<br>2. Per-phone rate limits: 1 per 30s, 3 per hour, 10 per day.<br>3. Per-IP rate limits: 20 per hour.<br>4. Web enforcement: reCAPTCHA Enterprise token required on every start.<br>5. App enforcement: Play Integrity via Firebase App Check. | `otp_rate_limit_saturation_test`<br>Assert 429 Too Many Requests after 3 requests in 1 hour. |
| **Elevation of Privilege** | Session hijacking via stolen session cookies or cross-site request forgery. | **Critical** | 1. `__session` cookie configured with `HttpOnly`, `Secure`, `SameSite=Strict`.<br>2. Double-submit `X-CSRF-Token` header check on state-mutating requests.<br>3. `Origin` and `Sec-Fetch-Site` header validation. | `csrf_origin_tamper_test`<br>Assert request rejected if `X-CSRF-Token` or `Origin` header is missing/mismatched. |

---

### 2.2 Synchronization Engine (Local-First Sync)

| Threat Category | Threat Description | Severity | Mitigation Architecture | Verification / Test Case |
| :--- | :--- | :---: | :--- | :--- |
| **Spoofing** | Compromised client impersonates another device ID or injects forged server sequence numbers. | **High** | Server validates `deviceId` against `users/{uid}/devices/{deviceId}` and issues all `serverRev` and `seq` values authoritatively. Clients cannot declare server sequence numbers. | `sync_unregistered_device_test`<br>Assert reject if `deviceId` is not registered. |
| **Tampering** | Attacker tampers with monetary fields (principal amount, interest) during offline sync reconciliation. | **Critical** | Monetary terms are protected by conflict detection: if client and server both modified terms or if the record was previously counterparty-verified, silent overwriting is forbidden. Record enters `CONFLICT` state for explicit user reconciliation. | `sync_monetary_conflict_test`<br>Assert conflict flag raised when monetary terms diverge. |
| **Repudiation** | Counterparty denies receiving or recording a partial repayment during synchronization. | **Medium** | Partial payments are append-only events merged via mathematical union by UUIDv7 and timestamp. Entries cannot be deleted via delta sync. | `sync_payment_append_only_test`<br>Assert payments cannot be deleted through sync delta updates. |
| **Information Disclosure** | Data packet sniffing during delta push/pull. | **High** | Strict TLS 1.3 in transit + Field-level envelope encryption (Cloud KMS) for all PII fields (party names, notes, phone, email, UPI ID). | `network_eavesdrop_pii_check`<br>Assert Firestore payload contains encrypted ciphertext for PII. |
| **Denial of Service** | Malicious client pushes 100,000 synthetic sync mutations to exhaust Firestore storage. | **High** | Max 500 changes per push request; 500KB payload size cap; user quota limits (maximum 10,000 active records per account). | `sync_quota_exceeded_test`<br>Assert 413 / 400 when change count or payload exceeds quota. |
| **Elevation of Privilege** | User attempts to sync records belonging to another `userId` (IDOR). | **Critical** | Cloud Functions enforce `context.auth.uid == targetUid` on all sync endpoints. Firestore rules deny direct client reads/writes. | `sync_cross_tenant_idor_test`<br>Assert 403 Forbidden when requesting another user's sync log. |

---

### 2.3 Guest Links & Unauthenticated Verification (`/u/**`)

| Threat Category | Threat Description | Severity | Mitigation Architecture | Verification / Test Case |
| :--- | :--- | :---: | :--- | :--- |
| **Spoofing** | Attacker guesses 6-digit WhatsApp guest confirmation code. | **High** | Max 5 verification attempts per guest link before the code is permanently revoked; 15-minute expiration; rate limiting on guest verify endpoint. | `guest_code_max_attempts_test`<br>Assert token revoked after 5 incorrect entries. |
| **Tampering** | Guest alters transaction terms (e.g. changes amount from ₹5,000 to ₹500) during confirmation. | **Critical** | Guest link endpoints only allow predefined status transitions (`CONFIRM`, `DISPUTE`, `CLAIM_PAYMENT`, `PROPOSE_DATE`). Guest cannot modify principal amount or currency. | `guest_immutable_amount_test`<br>Assert amount field is rejected if present in guest action payload. |
| **Information Disclosure** | Search engine crawlers index private guest links exposing debt records. | **High** | 1. Secret capability token resides exclusively in URL fragment (`#token=...`), which is never sent over HTTP or stored in server logs.<br>2. Headers: `X-Robots-Tag: noindex, nofollow`, `Referrer-Policy: no-referrer`. | `guest_noindex_header_test`<br>Assert `X-Robots-Tag` present on all `/u/**` responses. |

---

### 2.4 Attachments & Payment Proof Uploads

| Threat Category | Threat Description | Severity | Mitigation Architecture | Verification / Test Case |
| :--- | :--- | :---: | :--- | :--- |
| **Tampering / RCE** | Attacker uploads malicious executable disguised as JPG receipt. | **Critical** | 1. Direct uploads forbidden by Storage rules (`allow create: if false`).<br>2. Signed URLs require exact `Content-Type` and enforce `x-goog-content-length-range: 1, 5242880` (5MB).<br>3. Client-side canvas re-encoding strips EXIF and converts to `image/webp`.<br>4. Download headers enforce `Content-Disposition: attachment` for PDFs to prevent in-browser HTML execution. | `attachment_mime_content_test`<br>Assert signed URL rejects mismatching MIME type or size $>5\text{MB}$. |

---

### 2.5 Admin Console & Operational Access

| Threat Category | Threat Description | Severity | Mitigation Architecture | Verification / Test Case |
| :--- | :--- | :---: | :--- | :--- |
| **Elevation of Privilege** | Compromised staff account accesses user ledgers. | **Critical** | 1. Separate Firebase Hosting origin with IP allowlisting.<br>2. Role-based claims (`admin: true`) verified with hardware MFA.<br>3. Zero Ledger Read Policy: Admin APIs have no endpoints to read transaction notes or ledger balances.<br>4. Admin lookups display masked data only (e.g. `+91 98*** **210`).<br>5. Every admin action requires a mandatory reason and logs to immutable `auditLogs/`. | `admin_ledger_access_denied_test`<br>Assert admin role cannot read `users/{uid}/records`. |

---

## 3. Browser Storage Vulnerabilities & Inherent Limits

In compliance with production security standards, the operational limitations and threat boundaries of web browser storage must be explicitly recognized:

1. **Lack of Hardware Key Isolation**: Unlike Android devices equipped with hardware Keystores (StrongBox / TEE), browser storage (IndexedDB, `localStorage`) is protected by browser sandbox boundaries and file system permissions of the operating system user.
2. **Malicious Extensions & Physical Terminal Access**: Browser extensions with `<all_urls>` permissions or unauthorized physical access to an unlocked browser can inspect IndexedDB contents.
3. **Mitigations Implemented in Udhaar Web App**:
   - **In-Memory CryptoKey**: At sign-in, an AES-GCM session key is generated in memory (`window.crypto.subtle`) with `extractable: false`. Cached IndexedDB data is encrypted using this ephemeral key.
   - **Inactivity Screen Lock**: Automatically activates after 10 minutes of user inactivity, clearing sensitive DOM views and requiring biometric passkey or WhatsApp OTP re-verification.
   - **Public Computer Mode**: When toggled at sign-in, IndexedDB replication is completely disabled; all data resides strictly in volatile memory and is wiped on tab closure.
   - **Full Sign-Out Wipe**: Initiating sign-out executes `indexedDB.deleteDatabase("udhaar_ledger_db")` and clears all caches.
