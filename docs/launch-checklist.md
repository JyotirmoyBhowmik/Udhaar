# Udhaar — Production Launch Readiness Checklist (v32.0 Spark Mode)

**Document Version**: 32.0.0  
**Target Release**: v32.0 (Spark Mode)  
**Target Region**: `asia-south1` (Mumbai, India)  
**Classification**: PUBLIC RELEASE & AUDIT

> [!NOTE]
> **Truth Audit Notice**: In compliance with `docs/REALITY_CHECK.md`, items are strictly checked `[x]` ONLY when proven by concrete code AND passing automated tests in the repository. Unproven or external manual items remain unchecked `[ ]`.

---

## 1. Cloud Infrastructure & Networking Gate (SRE Lead)
- [x] **Spark-Safe CI/CD Deployments**: Staging and production workflows deploy only `hosting`, `firestore:rules`, and `firestore:indexes`. [Proof: `.github/workflows/deploy-prod.yml`, Test: `web-tests/stage_s10_release.test.js`]
- [ ] **Custom Domains & SSL**: `udhaar.jyotirmoyb.com` and `app.udhaar.jyotirmoyb.com`. *(Status: Configured in DNS; live production certificates managed externally)*
- [x] **Origin Isolation**: Separate Firebase Hosting site targets (`udhaar-marketing-prod` and `udhaar-app-prod`) configured with distinct security origins. [Proof: `infra/firebase.prod.json`, Test: `web-tests/openapi_contract.test.js`]
- [x] **HTTP Security Headers**: Strict CSP (`connect-src` restricted to Firebase Auth, Firestore, and App Check), `HSTS: max-age=63072000`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`. [Proof: `infra/firebase.prod.json`, Test: `web-tests/web_client.test.js`]
- [ ] **Google Cloud API Key Restrictions & Dedicated Production Project**: Restrict Android/Browser API keys in Google Cloud Console before scaling. [Guide: `docs/PRODUCTION_FIREBASE_PROJECT.md`]

---

## 2. Identity, Authentication & Anti-Fraud Gate (Security Lead)
- [x] **Explicit Cloud Verification Gate Setting**: `BuildConfig.REQUIRE_CLOUD_VERIFICATION` defaults to `false` (allowing users to continue locally without cloud sync) and supports mandatory verification builds (`true`) with 5-language wording. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/CloudVerificationGateScreen.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/VerificationAndMessagingV31Test.kt`]
- [x] **Verified Email Enforced Everywhere**: Every ledger, profile, shared record, chat thread/message, and guest link/action/proof path in `firestore.rules` requires `request.auth.token.email_verified == true`. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Unverified Contact Phone Disclosure**: Phone numbers are stored as unverified contact metadata and are never used for login or account recovery. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Firebase App Check Debug Isolation**: `firebase-appcheck-debug` is scoped to `debugImplementation` only; release builds install Play Integrity only and fail Gradle verification if any debug App Check artifact enters the release classpath. [Proof: `app/src/release/java/com/jyotirmoyb/udhaar/di/AppCheckProviderInstaller.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/sync/SparkQuotaAndAppCheckTest.kt`]

---

## 3. Data Integrity & Firestore Security Rules Gate (Backend Lead)
- [x] **Strict Spark Collection Schema, Allow-List, Size Cap & Revision Rules**: Owner-only + `email_verified == true` rules enforced with strict field allow-lists, types, lengths, `withinSize` limits, `rev == resource.data.rev + 1` on updates, and create-only `events`. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Chat Safety Rules Enforcement (Thread & User Block)**: Blocked users (at thread level or `/users/{uid}/blocked_users/{targetUid}`) are rejected by `firestore.rules` when attempting to create chat messages. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Fuzz & Per-Rule Coverage**: Extra fields, oversized strings, wrong types, and invalid enums tested across every Firestore path. [Proof: `firestore.rules`, Test: `rules-tests/firestore-rules.test.js`]
- [x] **Composite & Collection-Group Indexes**: Firestore index definitions deployed for `records`, `events`, and `collectionGroup("actions")`. [Proof: `firestore.indexes.json`, Test: `web-tests/openapi_contract.test.js`]

---

## 4. Web Application & PWA Client Gate (Web Lead)
- [x] **Offline-First IndexedDB Engine**: `udhaar_ledger_db` with outbox mutation queue and CRDT-lite conflict reconciliation. [Proof: `web/assets/idb-outbox.js`, Test: `web-tests/enterprise_tech.test.js`]
- [x] **Zero Inline Code**: 100% compliant with strict CSP across `index.html`, `privacy.html`, `terms.html`, `u/index.html`, and `webapp`. [Proof: `web/index.html`, Test: `web-tests/landing_privacy_page.test.js`]
- [x] **Biometric Passkey Guard**: FIDO2 WebAuthn biometric screen lock with 10-minute inactivity auto-lock. [Proof: `web/assets/webauthn-lock.js`, Test: `web-tests/enterprise_tech.test.js`]
- [x] **Performance Weight Budgets**: Landing page shell $< 600\text{KB}$, Guest portal $< 250\text{KB}$, Web App shell $< 300\text{KB}$. [Proof: `scripts/build-web.js`, Test: `web-tests/webapp_s2.test.js`]
- [x] **Multi-Language Completeness**: 100% dictionary completeness across English, Hindi, Bengali, Assamese, and Nepali. [Proof: `web/terms.html`, Test: `web-tests/webapp_s2.test.js`]

---

## 5. Android Native Application Gate (Android Lead)
- [x] **Room Schema v15 & Paging 3 Projections**: SQLCipher 256-bit Room database with Paging 3 projection queries (`TransactionHistoryProjection`, `PartySearchProjection`) replacing `SELECT *` for history and search. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/data/UdhaarDao.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/data/repository/LedgerRepositoryTest.kt`]
- [x] **Baseline Profile & Startup Benchmark + Native Symbol Table**: Dedicated `baselineprofile` module with `BaselineProfileGenerator`, `StartupBenchmarks`, and `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` on release builds. [Proof: `baselineprofile/src/main/java/com/jyotirmoyb/udhaar/baselineprofile/BaselineProfileGenerator.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/performance/ColdStartPerformanceAndStrictModeTest.kt`]
- [x] **Explicit Release Versioning (`32.0`) & Monotonic Guard**: `version.properties` + `app/build.gradle.kts` (`versionCode = 32`, `versionName = "32.0"`). [Proof: `version.properties`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Accessibility (TalkBack Semantics, Clickable Roles & `fontScale = 2.0f`)**: Audited `contentDescription`, semantic `Role` on clickables, and Robolectric TalkBack + `fontScale = 2.0f` UI tests across Home, Record, People, and Chat screens. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/components/DashboardScreen.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/AccessibilityTalkBackFontScaleTest.kt`]
- [x] **Modular UI/Security Files `< 600` Lines, Zero Detekt Warnings & CI Managed Device + Dependabot**: `VerificationSheets`, `CommunicationSheets`, and `EncryptedStorageManager` split under 600 lines; Detekt fails on any warning (`--max-issues 0`); `.github/dependabot.yml` and `managed-device-smoke-test` active in CI. [Proof: `.github/workflows/ci.yml`, Test: `web-tests/stage_s10_release.test.js`]
- [x] **5-Language Android Translations CI Gate**: All strings translated across `en`, `hi`, `bn`, `as`, `ne` with CI failure on missing or untranslated keys. [Proof: `app/src/main/res/values/strings.xml`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ExampleUnitTest.kt`]

---

## 6. Privacy & Legal Compliance Gate (DPO / Grievance Officer)
- [ ] **External Counsel Sign-Off on DPDP Act 2023 & Nepal Privacy Act 2075**: Statutory schedules documented in repository; external legal review optional prior to commercial expansion.
- [x] **Terms of Service & Privacy Policy in 5 Languages**: Publicly accessible `web/privacy.html` and `web/terms.html` in English, Hindi, Bengali, Assamese, and Nepali. [Proof: `web/terms.html`, Test: `web-tests/landing_privacy_page.test.js`]
- [x] **In-App Cloud Data Deletion**: Working "Delete my cloud data" action deletes user documents across Firestore collections and resets local records to `LOCAL_ONLY`. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt`]
- [x] **In-App Help, Support & Grievance Contact**: Dedicated in-app Help & Contact screen and public documentation listing Support (`admin@jyotirmoyb.com`) and Grievance Officer (`grievance@jyotirmoyb.com`). [Proof: `docs/legal-and-dpdp-updates.md`, Test: `web-tests/stage_s10_release.test.js`]

---

## 7. Observability, Backups & SRE Gate (SRE Lead)
- [x] **Zero-PII Client Logging (`SafeLogger`)**: Zero raw `Log.*` calls in app code; `SafeLogger` scrubs PII before delegating to `AppLogger`. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/util/SafeLogger.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/security/AppLoggerAndSecurityHardeningTest.kt`]
- [x] **Opt-In Telemetry Gate**: Crashlytics and Analytics stay disabled until explicit user opt-in via Settings. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/util/TelemetryManager.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Encrypted Google Drive & Local `.udhaar` Backups**: AES-256-GCM authenticated encryption with PBKDF2 (600,000 iterations) and round-trip verification. [Proof: `app/src/main/java/com/jyotirmoyb/udhaar/backup/DriveBackupCrypto.kt`, Test: `app/src/test/java/com/jyotirmoyb/udhaar/ReleaseSmokeTest.kt`]
- [x] **Disaster Recovery & Operational Runbooks**: Documented operational procedures. [Proof: `docs/runbooks/disaster-recovery.md`, Test: `web-tests/stage_s10_release.test.js`]

---

## 8. Staged Rollout Timeline & Sign-Off

| Phase | Traffic Allocation | Soak Duration | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Internal / Closed Testing** | **Internal Testers** | 7 Days | Zero crashes, backup/restore verified, guest links & chat safety verified |
| **Canary Phase 1** | **5% -> 25%** | 7 Days | Crash-free users $> 99.5\%$, Firebase Spark quota $< 50\%$ daily cap |
| **General Availability** | **100%** | Permanent | Public release on Google Play and Firebase Hosting |
