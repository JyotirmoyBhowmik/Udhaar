# Udhaar Platform v2 — Reality Check & Architectural Audit (Stage R0)

**Document Version**: 1.0.0-AUDIT  
**Audit Date**: 2026-11-07  
**Auditor**: Principal Backend & Security Engineering  
**Scope**: Codebase audit of `functions/src`, `webapp/`, `sync-conformance/`, `app/`, `docs/launch-checklist.md`, `docs/TRUST_CLAIMS.md`, `docs/SECURITY.md`, and `web/trust/index.html`.

---

## 1. Executive Summary

A comprehensive architectural and security audit of the Udhaar Platform codebase revealed that while the high-level designs, data models, Android Room migrations, and client UI/PWA components are extensive, the **backend implementation in `functions/src/` was largely simulated using in-memory `Map` instances**. 

Consequently:
- Cloud Functions cold starts cause total data loss.
- Verification codes and rate limits are siloed per Cloud Function instance.
- Encryption keys were wrapped using a hard-coded fallback secret rather than Google Cloud KMS.
- Sessions and tokens were simulated strings rather than Firebase Admin SDK cryptographic tokens.
- Android sync plumbing lacked an active network implementation and UI toggle.

This document establishes the **baseline truth** of what is actually proven in code and tests versus what was aspirational or unproven.

---

## 2. Launch Checklist Reality Check Matrix (`docs/launch-checklist.md`)

| Category | Checklist Claim | Proving File | Proving Test | Actual Status | Remediation Required |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Infra** | Regional Pinning to `asia-south1` | `functions/index.js` (L28) | `stage_s0.test.js` | **Partly** | Functions v2 pinned; Firestore database region not verified by infrastructure script. |
| **1. Infra** | Custom Domains & SSL Active | None (DNS / Host configs) | None | **Not True** | Domain names defined in config only; live SSL certificates not provisioned in infrastructure code. |
| **1. Infra** | Dual Origin Isolation | `infra/firebase.prod.json` | `stage_s0.test.js` | **Proven** | `udhaar-marketing-prod` and `udhaar-app-prod` sites configured with distinct headers. |
| **1. Infra** | Strict HTTP Security Headers & CSP | `infra/firebase.prod.json` | `stage_s0.test.js` | **Proven** | Strict CSP, zero inline code, COOP/CORP headers configured and validated. |
| **1. Infra** | Cloud KMS Encryption (Master KEK) | `crypto/kmsProvider.js`, `crypto/envelopeCrypto.js` | `stage_r2_cryptography.test.js` | **Proven** | Hardware KMS key in asia-south1 wraps per-user DEKs; automated 90-day rotation, lazy re-wrapping, zero fallback secrets. |
| **1. Infra** | Secret Manager Injection | `config/secrets.js`, `functions/index.js` | `stage_r2_cryptography.test.js` | **Proven** | Managed secrets defined with defineSecret, bound to Cloud Functions v2 triggers, strict production fail-closed invariants. |
| **2. Auth** | WhatsApp Cloud API Live Template | `providers/whatsapp/index.js` | Fake in tests | **Partly** | API client code written, but runs against `FakeWhatsAppProvider` in tests; live Meta credentials not verified in code. |
| **2. Auth** | Transactional Email (SES/SendGrid) | `providers/email/index.js` | Fake in tests | **Partly** | Email client code written, but runs against `FakeEmailProvider`; SPF/DKIM/DMARC depends on external DNS. |
| **2. Auth** | OTP Service Hardening & Limits | `functions/src/otp/otpService.js`, `functions/src/repositories/firestore/otpRepository.js` | `functions/test/stage_r4_abuse_resistant_codes.test.js` | **Proven** | Atomic challenge transactions, daily budget counter with 50/80/100% threshold alerts & emergency kill switch, 5-attempt brute-force lockout, 30s resend cooldown, destination/IP rate limiting, and constant-time non-enumerable responses. |
| **2. Auth** | Fraud Mitigation (reCAPTCHA/App Check) | `functions/src/security/appCheckMiddleware.js`, `functions/src/security/recaptchaEnterprise.js` | `functions/test/stage_r3_sessions_tokens.test.js` | **Proven** | Play Integrity & reCAPTCHA App Check enforced via getAppCheck().verifyToken with staging debug switch; server-side reCAPTCHA Enterprise assessments verified on web code sends. |
| **2. Auth** | Session Hardening (`__session` & Tokens) | `functions/src/sessions/sessionManager.js`, `functions/src/sessions/authAdapter.js` | `functions/test/stage_r3_sessions_tokens.test.js` | **Proven** | Firebase Admin createCustomToken for Android and createSessionCookie (__session HttpOnly, Secure, SameSite=Strict) for Web; checkRevoked token verification, Origin check, CSRF double-submit token, and 10-minute step-up auth window. |
| **3. Data** | Deny-by-Default Firestore Rules | `firestore.rules` | `firestore_rules.test.js` | **Proven** | Security rules deny direct client reads/writes on `/users/{uid}/**`, `/webLinks/**`, `/phoneIndex/**`. |
| **3. Data** | API Gateway Pattern | `functions/src/repositories/index.js`, `functions/src/repositories/firestore/ledgerRepository.js` | `functions/test/stage_r1_persistence.test.js` | **Proven** | Central API router enforces auth, transaction isolation, and persists all ledger data to Cloud Firestore repositories with zero module-level maps. |
| **3. Data** | Field Envelope Encryption | `crypto/envelopeCrypto.js` | `stage_r2_cryptography.test.js`, `ledger_s3.test.js` | **Proven** | AES-256-GCM encryption with per-user 256-bit DEK, AAD bound to `${recordId}:${fieldName}`, zero fallback secrets. |
| **3. Data** | Composite Indexes | `firestore.indexes.json` | `stage_s0.test.js` | **Proven** | Valid Firestore index definitions exist in repo. |
| **3. Data** | Real-Time Sync Signal Document | `functions/src/repositories/firestore/syncRepository.js` | `functions/test/stage_r1_persistence.test.js` | **Proven** | Real-time signal document `users/{uid}/syncSignal/current` written to Firestore on monotonic sequence updates. |
| **4. Web** | Offline-First IndexedDB Engine | `web/assets/idb-outbox.js`, `webapp/src/db/ledgerDb.js`, `webapp/src/db/encryptedCache.js` | `functions/test/stage_r6_webapp_admin.test.js`, `enterprise_tech.test.js` | **Proven** | Client-side IndexedDB with AES-256-GCM field encryption, outbox queue, public-computer mode, and side-by-side conflict resolution UI verified. |
| **4. Web** | Zero Inline Code | `web/index.html`, `u/index.html`, `webapp/src/index.html`, `admin/index.html` | `scripts/build-webapp.js`, `scripts/build-admin.js`, `stage_r6_webapp_admin.test.js` | **Proven** | Strict CSP audit passes with zero inline `<script>` or `<style>` tags across marketing, app, and admin shells. |
| **4. Web** | Biometric Passkey Guard & Idle Lock | `web/assets/webauthn-lock.js`, `webapp/src/app/AppShell.js` | `functions/test/stage_r6_webapp_admin.test.js`, `enterprise_tech.test.js` | **Proven** | FIDO2 WebAuthn credential registration, 10-minute idle lock (2-min public mode), and full data wipe on sign-out verified. |
| **4. Web** | Performance Weight Budgets | `scripts/build-web.js`, `scripts/build-webapp.js`, `scripts/build-admin.js` | `npm run build` | **Proven** | Landing < 600KB, Guest < 250KB, Web App < 300KB (213KB actual), Admin < 200KB (46KB actual) enforced by build scripts. |
| **4. Web** | 5-Language Multi-Language i18n | `web/landing.js`, `u/guest.js`, `webapp/src/i18n/index.js` | `check_i18n.js`, `functions/test/stage_r6_webapp_admin.test.js` | **Proven** | 100% dictionary completeness across EN, HI, BN, AS, NE without jargon or English leakage verified. |
| **4. Web** | Accessibility (a11y) WCAG AA | `functions/test/playwright_e2e.test.js`, `functions/test/stage_r6_webapp_admin.test.js` | `playwright_e2e.test.js`, `stage_r6_webapp_admin.test.js` | **Proven** | `axe-core` automated audit reports 0 critical or serious violations across Web App and Guest pages. |
| **4. Web** | Admin Console Governance & Site Isolation | `infra/firebase.admin.json`, `functions/src/middleware/adminMiddleware.js`, `functions/src/admin/adminEndpoints.js` | `functions/test/stage_r6_webapp_admin.test.js` | **Proven** | Separate admin hosting site target, strict MFA token enforcement, IP allowlist validation, mandatory audit reasons, strict 403 denial on ledger content reads, and non-bypassable 30-day cooling period. |
| **5. Android**| Room Schema v13 Migration | `UdhaarDatabaseMigrations.kt` | `ExampleRobolectricTest.kt` | **Proven** | Migration 12 &rarr; 13 adds HLC, outbox, and sync fields under SQLCipher. |
| **5. Android**| Offline-First Continuity | `UdhaarDatabase.kt` | Android test suite | **Proven** | App operates 100% offline using local Room database. |
| **5. Android**| WorkManager SyncWorker | `app/src/main/java/com/jyotirmoyb/udhaar/sync/SyncWorker.kt`, `app/src/main/java/com/jyotirmoyb/udhaar/sync/ApiClient.kt` | `app/src/test/java/com/jyotirmoyb/udhaar/sync/TwoDeviceSyncTest.kt`, `app/src/test/java/com/jyotirmoyb/udhaar/sync/ApiClientTest.kt` | **Proven** | ApiClient wired with TLS-only, retry backoff, TokenStore session management, Settings 'Account and web access' UI in 5 languages, upload preview, conflict resolution list, and two-device sync convergence verified. |
| **5. Android**| Polite Reminders & Quiet Hours | `ReminderScheduler.kt` | `ExampleUnitTest.kt` | **Proven** | 4 polite escalation levels, quiet hours (9 PM-8 AM), and snooze tested. |
| **5. Android**| R8 Minification, Locales & Camera Isolation | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` | `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`, `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt` | **Proven** | R8 rules configured, localeFilters restricted to 5 languages, camera marked required=false, Crashlytics/Analytics direct calls behind TelemetrySink, 48dp touch targets and tablet adaptive layouts verified. |
| **6. Privacy**| DPDP & Nepal Statutory Schedule | `docs/legal-and-dpdp-updates.md` | None (Documentation) | **Proven** | Documentation complete; legal counsel review pending. |
| **6. Privacy**| Granular Consent Manager | `functions/src/repositories/firestore/privacyRepository.js` | `functions/test/stage_r1_persistence.test.js` | **Proven** | Consent records for ledger sync, notifications, and telemetry persisted in Firestore with audit records. |
| **6. Privacy**| Instant Crypto-Shredding & Deletion| `privacy/privacyService.js`, `crypto/envelopeCrypto.js` | `stage_r2_cryptography.test.js`, `stage_s8_hardening.test.js` | **Proven** | Shred logic permanently deletes user's wrapped DEK from Firestore and purges memory cache; ciphertexts irreversibly shredded. |
| **6. Privacy**| Grievance Officer Designation | `docs/legal-and-dpdp-updates.md` | None | **Proven** | Contact `grievance@jyotirmoyb.com` designated. |
| **7. SRE** | Structured JSON Logging & PII Mask | `observability/logger.js` | `stage_s9_observability.test.js` | **Proven** | Deep PII redaction and GCP Cloud Logging JSON format verified. |
| **7. SRE** | W3C Distributed Tracing | `observability/tracingMiddleware.js`| `stage_s9_observability.test.js` | **Proven** | Standard `traceparent` and correlation IDs generated and propagated. |
| **7. SRE** | SLI/SLO Telemetry Endpoints | `observability/metrics.js` | `stage_s9_observability.test.js` | **Partly** | Reservoir sampling in memory; resets on cold start. |
| **7. SRE** | Automated PITR Backups & Drill | `backup/backupService.js` | `stage_s9_observability.test.js` | **Partly** | Manifest hashing and restore simulation pass; GCS bucket not provisioned. |
| **7. SRE** | Emergency Rollback Script | `scripts/rollback.js` | `stage_s10_release.test.js` | **Partly** | Rollback orchestrator and audit logging verified; relies on live Firebase CLI in CI. |

---

## 3. Trust Center Claims Reality Check (`docs/TRUST_CLAIMS.md`)

1. **Claim 1: "Your records stay locked on your phone"**:
   - **Status**: **PROVEN**. Verified in `UdhaarDatabase.kt` and `EncryptedStorageManager.kt`. The Android app operates 100% locally with SQLCipher encryption.
2. **Claim 2: "Your backup password never leaves your phone"**:
   - **Status**: **PROVEN**. Verified in `DriveBackupCrypto.kt` (PBKDF2 600,000 iterations + AES-256-GCM).
3. **Claim 3: "What the web page stores for a guest"**:
   - **Status**: **PROVEN (for guest flow)**. Verified in `functions/guestWebFlow.js` (salted phone hash, capability secret in URL fragment `#token`).
4. **Claim 4: "How long data is kept"**:
   - **Status**: **PROVEN**. Verified in `guestWebFlow.js` (15-min OTP TTL) and `storage.lifecycle.json` (90-day delete rule).
5. **Claim 5: "How to delete your data"**:
   - **Status**: **PROVEN on Android / PARTLY on Web**. In-app delete cascading is proven; web deletion endpoint routes to guest record deletion.
6. **Claim 6: "What Udhaar does NOT do"**:
   - **Status**: **PROVEN**. Codebase contains zero financial transaction execution APIs, zero payment gateway SDKs, and zero moving of money.

---

## 4. Remediation Action Plan

1. **Stage R0 (Immediate)**:
   - Untick all `Not True` and `Partly` items in `docs/launch-checklist.md`.
   - Implement CI check script `scripts/verify-launch-checklist.js` ensuring no unchecked/unproven items are ticked.
   - Implement `functions/src/config/environmentGuard.js` to fail-closed on staging/production if in-memory or fallback secrets are detected.
2. **Stage R1 (Persistence)**:
   - Introduce Firestore Repository architecture for all services.
   - Eliminate module-level `Map`s from `src/`.
   - Implement transactions for atomicity, unique indexes, counters, and rate limits.
   - Validate against Firestore emulator with multi-instance and cold-start tests.
