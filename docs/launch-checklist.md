# Udhaar Platform v2 — Production Launch Readiness Checklist

**Document Version**: 2.0.0-PROD  
**Target Release**: v21.0.0  
**Target Region**: `asia-south1` (Mumbai, India)  
**Security Classification**: CONFIDENTIAL & RESTRICTED  

This checklist acts as the formal launch gate for Udhaar Platform v2. Every item must be verified and signed off by the respective engineering leads before promoting production traffic past the 5% canary threshold.

---

## 1. Cloud Infrastructure & Networking Gate (SRE Lead)
- [x] **Regional Pinning**: Cloud Functions v2 and Cloud Firestore instances pinned strictly to `asia-south1` (Mumbai) for data sovereignty compliance.
- [x] **Custom Domains & SSL**:
  - `udhaar.jyotirmoyb.com` (Marketing, Guest Flow, App Links) &rarr; Active, SSL valid.
  - `app.udhaar.jyotirmoyb.com` (PWA Web Ledger) &rarr; Active, SSL valid.
- [x] **Origin Isolation**: Separate Firebase Hosting site targets (`udhaar-marketing-prod` and `udhaar-app-prod`) configured with distinct security origins.
- [x] **HTTP Security Headers**: Strict CSP, `HSTS: max-age=63072000`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`.
- [x] **Cloud KMS Encryption**: Master Key Encryption Key (`udhaar-master-kek`) provisioned with automated 90-day key rotation in `asia-south1`.
- [x] **Secret Manager**: Production secrets (`WHATSAPP_CLOUD_API_TOKEN`, `EMAIL_PROVIDER_CONFIG`, `UDHAAR_SERVER_SECRET`) injected into Cloud Functions via runtime dependency arrays.

---

## 2. Identity, Authentication & Anti-Fraud Gate (Security Lead)
- [x] **WhatsApp Cloud API**: Production Meta App approved with active credit line and `udhaar_login_code` authentication template in EN, HI, BN, NE, AS.
- [x] **Email Provider**: Amazon SES / SendGrid configured with SPF, DKIM, and DMARC passing on `no-reply@jyotirmoyb.com`.
- [x] **OTP Service Hardening**:
  - Codes generated with `crypto.randomInt` (zero predictable pseudorandomness).
  - Only HMAC-SHA256 stored in `otpChallenges/` collection with TTL policy.
  - Constant-time verification using `crypto.timingSafeEqual`.
  - Rate limits enforced: 30s resend, 3/hr & 10/day per destination, 20/hr per IP, 5-attempt lockout.
- [x] **Fraud Mitigation**: reCAPTCHA Enterprise on web, Play Integrity App Check on Android, disposable email domain blocklist, and dial code restrictions (`+91`, `+977`).
- [x] **Session Hardening**:
  - Web: `__session` HttpOnly, Secure, SameSite=Strict cookie with 7-day sliding and 30-day absolute expiration.
  - Android: Custom token exchanged for Firebase ID token with secure encrypted token refresh storage.

---

## 3. Data Integrity & Firestore Security Rules Gate (Backend Lead)
- [x] **Deny-by-Default Firestore Rules**: All direct client reads and writes to platform v2 ledger collections (`users/{uid}/records`, `people`, `events`, `plans`) rejected with `allow read, write: if false;`.
- [x] **API Gateway Pattern**: All ledger operations routed through authenticated Cloud Functions validating payloads, enforcing quotas, and generating audit entries.
- [x] **Envelope Encryption**: Field-level AES-256-GCM encryption of personal attributes (`partyName`, `notes`, `phone`, `email`, `upiId`).
- [x] **Composite Indexes**: Firestore index definitions deployed for cursor pagination and composite sorting across records, payments, and events.
- [x] **Real-Time Sync Signal**: Lightweight non-sensitive `users/{uid}/syncSignal` document deployed for client real-time synchronization wakeups.

---

## 4. Web Application & PWA Client Gate (Web Lead)
- [x] **Offline-First IndexedDB Engine**: `udhaar_ledger_db` with outbox mutation queue and CRDT-lite conflict reconciliation.
- [x] **Zero Inline Code**: 100% compliant with strict CSP (zero inline `<script>` or `<style>` tags).
- [x] **Biometric Passkey Guard**: FIDO2 WebAuthn biometric screen lock with 10-minute inactivity auto-lock.
- [x] **Performance Weight Budgets**: Landing page shell $< 600\text{KB}$, Guest portal $< 250\text{KB}$, Web App shell $< 300\text{KB}$.
- [x] **Multi-Language Completeness**: 100% dictionary completeness across English, Hindi, Bengali, Assamese, and Nepali.
- [x] **Accessibility (a11y)**: Automated `axe-core` audit passes with zero critical or serious WCAG 2.1 AA violations.

---

## 5. Android Native Application Gate (Android Lead)
- [x] **Room Schema v13 Migration**: Automated migration with SQLCipher encryption, adding Hybrid Logical Clocks (`hlcTimestamp`, `nodeId`), outbox queue, and sync revision tracking.
- [x] **Offline-First Continuity**: Behavior strictly identical to v1 when user is not signed in; sync is completely optional.
- [x] **SyncWorker**: Robust Android `WorkManager` background sync with exponential backoff and network constraints.
- [x] **Polite Reminders Engine**: Respects quiet hours (9:00 PM to 8:00 AM) and polite escalation levels (Gentle &rarr; Friendly &rarr; Firm &rarr; Final).
- [x] **R8 Minification & Code Shrinking**: Release build compiled with ProGuard/R8 rules; APK size minimized.

---

## 6. Privacy & Legal Compliance Gate (DPO / Grievance Officer)
- [x] **DPDP Act 2023 Schedule**: Notice and consent architecture covering all personal data processing.
- [x] **Nepal Privacy Act 2075 Schedule**: Cross-border transfer disclosures and purpose limitations.
- [x] **Consent Manager**: Granular consent choices for account ledger, WhatsApp notifications, and analytics.
- [x] **Data Erasure & Crypto-Shredding**: Instant crypto-shredding of DEK on account deletion, followed by 30-day cooling period.
- [x] **Grievance Officer Designation**: Publicly listed contact (`grievance@jyotirmoyb.com`) with statutory 30-day resolution SLA.

---

## 7. Observability, Backups & SRE Gate (SRE Lead)
- [x] **Structured JSON Logging**: Enterprise logging with automatic PII sanitization.
- [x] **W3C Distributed Tracing**: Standard `traceparent` and correlation IDs propagated end-to-end.
- [x] **SLO Dashboards**: API availability $\ge 99.9\%$, latency $p95 < 500\text{ms}$, OTP delivery $< 20\text{s}$.
- [x] **Automated PITR Backups**: Hourly incremental and daily full backups to `gs://udhaar-backups-asia-south1/` sealed with SHA-256 manifests.
- [x] **Emergency Rollback**: One-command instant rollback script (`./scripts/rollback.sh` / `.\scripts\rollback.ps1`) tested and verified with RTO $< 60\text{ seconds}$.

---

## 8. Staged Rollout Timeline & Sign-Off

| Phase | Traffic Allocation | Soak Duration | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Canary Phase 1** | **5%** | 7 Days | Error rate $< 0.1\%$, zero P1/P2 incidents, latency $p95 < 350\text{ms}$ |
| **Canary Phase 2** | **25%** | 3 Days | Telemetry stable, OTP send budget within thresholds |
| **General Availability** | **100%** | Permanent | Full global release promoted to live production |

**Sign-Off Approvals**:
- **Principal Engineer**: Approved
- **Security Lead**: Approved
- **SRE & Operations Lead**: Approved
- **Data Protection Officer**: Approved
