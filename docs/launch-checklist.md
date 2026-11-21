# Udhaar — Production Launch Readiness Checklist (v29.0 Spark Mode)

**Document Version**: 29.0.0  
**Target Release**: v29.0 (Spark Mode)  
**Target Region**: `asia-south1` (Mumbai, India)  
**Classification**: PUBLIC RELEASE & PORTFOLIO AUDIT  

> [!NOTE]
> **Spark Mode & Truth Audit Notice**: In compliance with `docs/REALITY_CHECK.md`, items are strictly checked `[x]` ONLY when proven by concrete code AND passing automated tests in the repository. Unproven or external manual items are unchecked `[ ]`.

---

## 1. Cloud Infrastructure & Networking Gate (SRE Lead)
- [x] **Spark-Safe CI/CD Deployments**: Staging and production workflows deploy only `hosting`, `firestore:rules`, and `firestore:indexes` (no paid Cloud Functions or Storage rules required in Spark Mode). [Proof: `.github/workflows/deploy-prod.yml`, Test: `web-tests/stage_s10_release.test.js`]
- [ ] **Custom Domains & SSL**: `udhaar.jyotirmoyb.com` and `app.udhaar.jyotirmoyb.com`. *(Status: Configured in DNS; live production certificates managed externally)*
- [x] **Origin Isolation**: Separate Firebase Hosting site targets (`udhaar-marketing-prod` and `udhaar-app-prod`) configured with distinct security origins. [Proof: `infra/firebase.prod.json`, Test: `web-tests/openapi_contract.test.js`]
- [x] **HTTP Security Headers**: Strict CSP (`connect-src` restricted to Firebase Auth, Firestore, and App Check), `HSTS: max-age=63072000`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`. [Proof: `infra/firebase.prod.json`, Test: `web-tests/web_client.test.js`]
- [ ] **Google Cloud API Key Restrictions & Dedicated Production Project**: Restrict Android/Browser API keys in Google Cloud Console and migrate to a dedicated production Spark project before scaling. [Guide: `docs/PRODUCTION_FIREBASE_PROJECT.md`]
- [ ] **Optional Blaze Tier Cloud KMS & Secret Manager**: Archived in `blaze_disabled/functions/` for optional Blaze deployments; inactive in default Spark Mode.

---

## 2. Identity, Authentication & Anti-Fraud Gate (Security Lead)
- [x] **Firebase Auth in Spark Mode**: Android and Web authenticate directly with Firebase Auth (Google Sign-In via Credential Manager / popup, Email & Password with verification email, password reset, and re-authentication) with zero `/api/v1` dependencies. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]
- [x] **Email Verification Gate**: Cloud sync stays strictly disabled (`isEnabled = false`) until the user's email address is verified (`request.auth.token.email_verified == true` or Google Sign-In). [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/sync/SyncWorker.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]
- [x] **Unverified Contact Phone Disclosure**: Phone numbers are stored as unverified contact metadata (`Contact only — not verified`) and are never used for login or account recovery in Spark Mode. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Firebase App Check Debug Isolation**: `firebase-appcheck-debug` is scoped to `debugImplementation` only; release builds install Play Integrity only and fail Gradle verification if any debug App Check artifact enters the release classpath. [Proof: `app/src/release/java/com/jyotirmoyb/udhaar/di/AppCheckProviderInstaller.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/sync/SparkQuotaAndAppCheckTest.kt`]

---

## 3. Data Integrity & Firestore Security Rules Gate (Backend Lead)
- [x] **Strict Spark Collection Schema, Allow-List, Size Cap & Revision Rules**: Owner-only + `email_verified == true` rules enforced with strict field allow-lists, types, lengths, real `withinSize` limits, `rev == resource.data.rev + 1` on updates, and create-only schema-checked `events`. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Guest Capability Links & Fixed Action Slots**: `/guestLinks/{linkId}` enforces 32–64 hex capability IDs, active status, expiry timestamp, and fixed 10-slot action IDs (`confirm`, `dispute`, `claim_1..5`, `date_1..3`) with zero updates/deletes. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Composite & Collection-Group Indexes**: Firestore index definitions deployed for `records`, `events`, and `collectionGroup("actions")` (`ownerUid` + `createdAt`). [Proof: `firestore.indexes.json`, Test: `web-tests/openapi_contract.test.js`]
- [x] **Lender Approval Invariant**: Guest actions are polled via `GuestActionPollingWorker` collection-group query on `ownerUid` and staged as pending proposals that never change the ledger until accepted in-app. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/sync/GuestActionPollingWorker.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]

---

## 4. Web Application & PWA Client Gate (Web Lead)
- [x] **Offline-First IndexedDB Engine**: `udhaar_ledger_db` with outbox mutation queue and CRDT-lite conflict reconciliation. [Proof: `web/assets/idb-outbox.js`, Test: `web-tests/enterprise_tech.test.js`]
- [x] **Zero Inline Code**: 100% compliant with strict CSP (zero inline `<script>` or `<style>` tags across `index.html`, `privacy.html`, `terms.html`, `u/index.html`, and `webapp`). [Proof: `web/index.html`, Test: `web-tests/landing_privacy_page.test.js`]
- [x] **Biometric Passkey Guard**: FIDO2 WebAuthn biometric screen lock with 10-minute inactivity auto-lock. [Proof: `web/assets/webauthn-lock.js`, Test: `web-tests/enterprise_tech.test.js`]
- [x] **Performance Weight Budgets**: Landing page shell $< 600\text{KB}$, Guest portal $< 250\text{KB}$, Web App shell $< 300\text{KB}$. [Proof: `scripts/build-web.js`, Test: `web-tests/webapp_s2.test.js`]
- [x] **Multi-Language Completeness**: 100% dictionary completeness across English, Hindi, Bengali, Assamese, and Nepali on Landing, Privacy, Terms, Guest Portal, and Web App. [Proof: `web/terms.html`, Test: `web-tests/webapp_s2.test.js`]
- [x] **Accessibility (a11y) & Multi-Browser E2E**: Automated `axe-core` audit and Playwright suite re-enabled in CI for Spark static pages and emulators. [Proof: `web-tests/playwright_e2e.test.js`, Test: `web-tests/playwright_e2e.test.js`]

---

## 5. Android Native Application Gate (Android Lead)
- [x] **Room Schema v13 Migration**: Automated migration with SQLCipher 256-bit encryption, adding Hybrid Logical Clocks (`hlcTimestamp`, `nodeId`), outbox queue, and sync revision tracking. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDatabase.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/RoomMigration12To13Test.kt`]
- [x] **Explicit Release Versioning & Monotonic Guard**: `version.properties` + CI run-number base (`100 + GITHUB_RUN_NUMBER`) guarantees monotonic `versionCode >= 28` and fails release builds if missing/invalid. [Proof: `version.properties`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Offline-First & Spark Cloud Flag**: `BuildConfig.CLOUD_FEATURES` defaults to `true` (Spark free-tier features enabled, `BLAZE_FEATURES_ENABLED = false`) while offline ledger features continue working 100% locally without sign-in. [Proof: `app/build.gradle.kts`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]
- [x] **Adaptive Layouts & 48 dp Touch Targets at Font Scale 2.0**: List-detail two-pane layout at `>= 600.dp`, centered `560.dp` form width cap, landscape support, and all interactive touch targets $\ge 48\text{dp}$ verified at `fontScale = 2.0`. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/DashboardScreen.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt`]
- [x] **Detekt & Clean Icon Namespace**: `androidx` package-squatting icon workaround removed in favor of `com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons`, `AccountAndWebAccessSection.kt` split under 600 lines, and Detekt enforced in CI. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/icons/UdhaarIcons.kt`, Test: `web-tests/stage_s10_release.test.js`]
- [x] **Polite Reminders Engine**: Respects quiet hours (9:00 PM to 8:00 AM) and polite escalation levels (Gentle &rarr; Friendly &rarr; Firm &rarr; Final). [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/notification/ReminderScheduler.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleUnitTest.kt`]
- [x] **R8 Minification & Code Shrinking**: Release build compiled with ProGuard/R8 rules, `localeFilters` restricted to 5 languages, and optional camera hardware feature. [Proof: `app/build.gradle.kts`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]

---

## 6. Privacy & Legal Compliance Gate (DPO / Grievance Officer)
- [ ] **External Counsel Sign-Off on DPDP Act 2023 & Nepal Privacy Act 2075**: Statutory schedules documented in repository; external legal review optional prior to commercial expansion.
- [x] **Terms of Service & Privacy Policy in 5 Languages**: Publicly accessible `web/privacy.html` and `web/terms.html` in English, Hindi, Bengali, Assamese, and Nepali linked from Landing, Privacy, and In-App Settings/Help. [Proof: `web/terms.html`, Test: `web-tests/landing_privacy_page.test.js`]
- [x] **In-App Cloud Data Deletion (Spark Mode)**: Working "Delete my cloud data" action deletes user documents across Firestore Spark collections (`records`, `people`, `events`, `plans`, `attachments`, `meta`, `users/{uid}`, and owned `guestLinks`) and resets local records to `LOCAL_ONLY`. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]
- [x] **In-App Help, Support & Grievance Contact**: Dedicated in-app Help & Contact screen and public documentation listing Support (`admin@jyotirmoyb.com`) and Grievance Officer (`grievance@jyotirmoyb.com`) with 30-day resolution SLA. [Proof: `docs/legal-and-dpdp-updates.md`, Test: `web-tests/stage_s10_release.test.js`]

---

## 7. Observability, Backups & SRE Gate (SRE Lead)
- [x] **Zero-PII Client Logging**: `SafeLogger` redacts phone numbers, UPI IDs, amounts, emails, and tokens before logging, and suppresses debug logs in release builds. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/util/SafeLogger.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt`]
- [x] **Opt-In Telemetry Gate**: Crashlytics and Analytics stay strictly disabled until the user explicitly opts in via Settings. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/util/TelemetryManager.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Encrypted Google Drive & Local `.udhaar` Backups**: AES-256-GCM authenticated encryption with PBKDF2 (600,000 iterations), recovery key support, and in-app "Test my backup" round-trip verification. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/backup/DriveBackupCrypto.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Disaster Recovery & Operational Runbooks**: Documented procedures for rollback, incident response, quota management, and key rotation. [Proof: `docs/runbooks/disaster-recovery.md`, Test: `web-tests/stage_s10_release.test.js`]

---

## 8. Real-Device Manual & Instrumented Smoke Verification Gate
- [ ] **Instrumented Release Smoke Suite Executed on Device/Emulator**: Run `ReleaseSmokeInstrumentedTest` and record results in `docs/RELEASE_SMOKE_TEST_RESULTS.md`.
- [ ] **Physical Camera & Optical Scanning**: Verify receipt capture and QR scanning on budget handsets where autofocus is limited (`uses-feature required="false"`).
- [ ] **Biometric Prompt Hardware Verification**: Test biometric screen unlock on Android 13, 14, and 15 physical devices with fingerprint sensors, face unlock, and device PIN fallback.
- [ ] **Physical Touch Target Ergonomics**: Validate $\ge 48\text{dp}$ touch targets and `fontScale = 2.0` layout on physical 5.5" phones and 10" tablets.
- [ ] **Airplane Mode Reconnection Drill**: Verify offline ledger edits and Firestore Spark sync convergence after toggling Airplane Mode on physical devices.

---

## 9. Staged Rollout Timeline & Sign-Off

| Phase | Traffic Allocation | Soak Duration | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Internal / Closed Testing** | **Internal Testers** | 7 Days | Zero crashes, backup/restore verified, guest links verified |
| **Canary Phase 1** | **5% -> 25%** | 7 Days | Crash-free users $> 99.5\%$, Firebase Spark quota $< 50\%$ daily cap |
| **General Availability** | **100%** | Permanent | Public release on Google Play and Firebase Hosting |
