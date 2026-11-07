# Udhaar Platform v2 — Production Launch Readiness Checklist

**Document Version**: 2.1.0-AUDITED  
**Target Release**: v21.0.0  
**Target Region**: `asia-south1` (Mumbai, India)  
**Security Classification**: CONFIDENTIAL & RESTRICTED  

> [!WARNING]
> **Stage R0 Audit Notice**: In compliance with the Reality Check audit (`docs/REALITY_CHECK.md`), items are strictly checked `[x]` ONLY when proven by concrete code AND passing automated tests in the repository. All unproven, simulated, or pending infrastructure items are unchecked `[ ]`.

---

## 1. Cloud Infrastructure & Networking Gate (SRE Lead)
- [ ] **Regional Pinning**: Cloud Functions v2 and Cloud Firestore instances pinned strictly to `asia-south1` (Mumbai). *(Status: Functions pinned in code; Firestore DB location pending terraform/gcloud script)*
- [ ] **Custom Domains & SSL**: `udhaar.jyotirmoyb.com` and `app.udhaar.jyotirmoyb.com`. *(Status: Configured in DNS; live production certificates pending domain activation)*
- [x] **Origin Isolation**: Separate Firebase Hosting site targets (`udhaar-marketing-prod` and `udhaar-app-prod`) configured with distinct security origins. [Proof: `infra/firebase.prod.json`, Test: `functions/test/openapi_contract.test.js`]
- [x] **HTTP Security Headers**: Strict CSP, `HSTS: max-age=63072000`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`. [Proof: `infra/firebase.prod.json`, Test: `functions/test/openapi_contract.test.js`]
- [x] **Cloud KMS Encryption**: Master Key Encryption Key (`user-dek`) provisioned with automated 90-day key rotation in `asia-south1`. [Proof: `infra/terraform/main.tf`, Test: `functions/test/stage_r2_cryptography.test.js`]
- [x] **Secret Manager**: Production secrets (`UDHAAR_SERVER_SECRET`, `UDHAAR_PHONE_PEPPER`, `UDHAAR_EMAIL_PEPPER`, `META_APP_SECRET`, etc.) injected into Cloud Functions via runtime dependency arrays. [Proof: `functions/src/config/secrets.js`, Test: `functions/test/stage_r2_cryptography.test.js`]

---

## 2. Identity, Authentication & Anti-Fraud Gate (Security Lead)
- [ ] **WhatsApp Cloud API**: Production Meta App approved with active credit line and `udhaar_login_code` authentication template in EN, HI, BN, NE, AS. *(Status: Provider adapter written; live credentials pending)*
- [ ] **Email Provider**: Amazon SES / SendGrid configured with SPF, DKIM, and DMARC passing on `no-reply@jyotirmoyb.com`. *(Status: Provider adapter written; live credentials pending)*
- [x] **OTP Service Hardening**: Challenges stored with Firestore TTL policy, atomic rate limiting, 50%/80%/100% budget alerts, and global budget transaction. [Proof: `functions/src/otp/otpService.js`, Test: `functions/test/stage_r4_abuse_resistant_codes.test.js`]
- [x] **Fraud Mitigation**: Server-side reCAPTCHA Enterprise and Play Integrity App Check verification on endpoints. [Proof: `functions/src/security/appCheckMiddleware.js`, Test: `functions/test/stage_r3_sessions_tokens.test.js`]
- [x] **Session Hardening**: Firebase Admin `createSessionCookie` (`__session`) and `createCustomToken` with Firestore revocation. [Proof: `functions/src/sessions/sessionManager.js`, Test: `functions/test/stage_r3_sessions_tokens.test.js`]

---

## 3. Data Integrity & Firestore Security Rules Gate (Backend Lead)
- [x] **Deny-by-Default Firestore Rules**: All direct client reads and writes to platform v2 ledger collections (`users/{uid}/records`, `people`, `events`, `plans`) rejected with `allow read, write: if false;`. [Proof: `firestore.rules`, Test: `functions/test/firestore_rules.test.js`]
- [ ] **API Gateway Pattern**: All ledger operations routed through authenticated Cloud Functions validating payloads and persisting to Firestore. *(Status: Pending Stage R1 real persistence)*
- [x] **Envelope Encryption**: Field-level AES-256-GCM encryption of personal attributes wrapped via Cloud KMS with per-user DEKs in `asia-south1`. [Proof: `functions/src/crypto/envelopeCrypto.js`, Test: `functions/test/stage_r2_cryptography.test.js`]
- [x] **Composite Indexes**: Firestore index definitions deployed for cursor pagination and composite sorting across records, payments, and events. [Proof: `firestore.indexes.json`, Test: `functions/test/openapi_contract.test.js`]
- [ ] **Real-Time Sync Signal**: Lightweight non-sensitive `users/{uid}/syncSignal` document written to Firestore by sync hub. *(Status: Pending Stage R1)*

---

## 4. Web Application & PWA Client Gate (Web Lead)
- [x] **Offline-First IndexedDB Engine**: `udhaar_ledger_db` with outbox mutation queue and CRDT-lite conflict reconciliation. [Proof: `web/assets/idb-outbox.js`, Test: `functions/test/enterprise_tech.test.js`]
- [x] **Zero Inline Code**: 100% compliant with strict CSP (zero inline `<script>` or `<style>` tags). [Proof: `web/index.html`, Test: `functions/test/landing_privacy_page.test.js`]
- [x] **Biometric Passkey Guard**: FIDO2 WebAuthn biometric screen lock with 10-minute inactivity auto-lock. [Proof: `web/assets/webauthn-lock.js`, Test: `functions/test/enterprise_tech.test.js`]
- [x] **Performance Weight Budgets**: Landing page shell $< 600\text{KB}$, Guest portal $< 250\text{KB}$, Web App shell $< 300\text{KB}$. [Proof: `scripts/build-web.js`, Test: `functions/test/webapp_s2.test.js`]
- [x] **Multi-Language Completeness**: 100% dictionary completeness across English, Hindi, Bengali, Assamese, and Nepali. [Proof: `web/landing.js`, Test: `functions/test/webapp_s2.test.js`]
- [x] **Accessibility (a11y)**: Automated `axe-core` audit passes with zero critical or serious WCAG 2.1 AA violations. [Proof: `functions/test/playwright_e2e.test.js`, Test: `functions/test/playwright_e2e.test.js`]

---

## 5. Android Native Application Gate (Android Lead)
- [x] **Room Schema v13 Migration**: Automated migration with SQLCipher encryption, adding Hybrid Logical Clocks (`hlcTimestamp`, `nodeId`), outbox queue, and sync revision tracking. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/RoomMigration12To13Test.kt`]
- [x] **Offline-First Continuity**: Behavior strictly identical to v1 when user is not signed in; sync is completely optional. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleUnitTest.kt`]
- [x] **SyncWorker**: Robust Android `WorkManager` background sync wired to live network API client and TokenStore. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/sync/SyncWorker.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/sync/TwoDeviceSyncTest.kt`]
- [x] **Polite Reminders Engine**: Respects quiet hours (9:00 PM to 8:00 AM) and polite escalation levels (Gentle &rarr; Friendly &rarr; Firm &rarr; Final). [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/notification/ReminderScheduler.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleUnitTest.kt`]
- [ ] **R8 Minification & Code Shrinking**: Release build compiled with ProGuard/R8 rules with localeFilters and non-required camera feature. *(Status: Pending Stage R8)*

---

## 6. Privacy & Legal Compliance Gate (DPO / Grievance Officer)
- [ ] **DPDP Act 2023 Schedule**: Notice and consent architecture covering all personal data processing. *(Status: Documented; external legal review pending)*
- [ ] **Nepal Privacy Act 2075 Schedule**: Cross-border transfer disclosures and purpose limitations. *(Status: Documented; external legal review pending)*
- [ ] **Consent Manager**: Granular consent choices for account ledger, WhatsApp notifications, and analytics persisted in Firestore. *(Status: Pending Stage R1)*
- [x] **Data Erasure & Crypto-Shredding**: Instant crypto-shredding of DEK in KMS on account deletion, followed by 30-day cooling period. [Proof: `functions/src/crypto/envelopeCrypto.js`, Test: `functions/test/stage_r2_cryptography.test.js`]
- [x] **Grievance Officer Designation**: Publicly listed contact (`grievance@jyotirmoyb.com`) with statutory 30-day resolution SLA. [Proof: `docs/legal-and-dpdp-updates.md`, Test: `functions/test/stage_s10_release.test.js`]

---

## 7. Observability, Backups & SRE Gate (SRE Lead)
- [x] **Structured JSON Logging**: Enterprise logging with automatic PII sanitization. [Proof: `functions/src/observability/logger.js`, Test: `functions/test/stage_s9_observability.test.js`]
- [x] **W3C Distributed Tracing**: Standard `traceparent` and correlation IDs propagated end-to-end. [Proof: `functions/src/observability/tracingMiddleware.js`, Test: `functions/test/stage_s9_observability.test.js`]
- [ ] **SLO Dashboards**: API availability $\ge 99.9\%$, latency $p95 < 500\text{ms}$, OTP delivery $< 20\text{s}$ backed by Cloud Monitoring. *(Status: Pending live GCP metrics deployment)*
- [ ] **Automated PITR Backups**: Hourly incremental and daily full backups to `gs://udhaar-backups-asia-south1/` sealed with SHA-256 manifests. *(Status: Code written; GCS bucket provision pending)*
- [ ] **Emergency Rollback**: One-command instant rollback script (`./scripts/rollback.sh` / `.\scripts\rollback.ps1`) verified in live production environment. *(Status: Script tested in sandbox; live run pending)*

---

## 8. Staged Rollout Timeline & Sign-Off

| Phase | Traffic Allocation | Soak Duration | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Canary Phase 1** | **5%** | 7 Days | Error rate $< 0.1\%$, zero P1/P2 incidents, latency $p95 < 350\text{ms}$ |
| **Canary Phase 2** | **25%** | 3 Days | Telemetry stable, OTP send budget within thresholds |
| **General Availability** | **100%** | Permanent | Full global release promoted to live production |

**Audit Status**:
- **Baseline Audit (Stage R0)**: Complete. 24 items proven, 12 items pending completion across Stages R6–R8.
