# Udhaar — Reality Check & Architectural Audit (v32.0 Spark Mode)

**Document Version:** `32.0.0-AUDIT`  
**Version Audited:** `versionName = "32.0"`, `versionCode = 32` (`app/build.gradle.kts`, `version.properties`, `package.json`)  
**Scope:** End-to-end audit of `app/`, `baselineprofile/`, `firestore.rules`, `rules-tests/`, `web/`, `webapp/`, `.github/`, `docs/`, and `archive/`.

---

## 1. Executive Summary

Udhaar v32.0 runs strictly on **local-first encrypted SQLCipher Room storage (v15)** plus the **Firebase Spark (Free) Tier** (Authentication, Firestore Security Rules & Indexes, and Firebase Hosting). All obsolete staging reports, duplicate Data Safety files, and legacy pre-Spark directories (`functions/`, `blaze_disabled/`, `admin/`) have been removed or consolidated under `archive/`.

---

## 2. v31 → v32 Fix Verification Matrix

| # | Requirement | Proving Code / Config | Proving Automated Test | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Cloud Verification Gate (`BuildConfig.REQUIRE_CLOUD_VERIFICATION`, default `false`)** with 5-language wording & updated docs | `app/build.gradle.kts`, `CloudVerificationGateScreen.kt`, `UdhaarScreenRouter.kt`, `UdhaarViewModel.kt`, `docs/PLAY_DATA_SAFETY.md`, `docs/STORE_LISTING.md`, `README.md` | `VerificationAndMessagingV31Test.kt`, `ExampleUnitTest.kt` | **Verified** |
| **2** | **Chat Safety (Report, Block & Mute for User and Thread)** stored locally + in Firestore & enforced in `firestore.rules` | `UdhaarEntities.kt` (`UserChatSafetyEntity`), `ChatRepository.kt`, `VerificationCloudRepository.kt`, `ChatSafetyDialogs.kt`, `CommunicationSheets.kt`, `firestore.rules` (`isUserBlockedBy`, `parentThreadAllowsMessage`) | `rules-tests/firestore-rules.test.js` (`A8`, `C8`), `VerificationAndMessagingV31Test.kt` | **Verified** |
| **3** | **Docs Cleanup & Single Source of Truth** (`docs/STAGING_REPORT.md` & duplicate `docs/play-data-safety.md` deleted; `functions/`, `blaze_disabled/`, `admin/` archived under `archive/`) | `archive/README.md`, `docs/REALITY_CHECK.md`, `docs/PLAY_DATA_SAFETY.md`, `docs/STORE_LISTING.md`, `docs/launch-checklist.md` | `scripts/verify-launch-checklist.js`, `web-tests/stage_s10_release.test.js` | **Verified** |
| **4** | **Firestore Rules: Verified Email Everywhere + Fuzz Tests + Per-Rule Coverage** | `firestore.rules` (`isVerifiedSignedIn()` across `/users/**`, `/guestLinks/**`, `/user_profiles/**`, `/shared_records/**`, `/chat_threads/**`) | `rules-tests/firestore-rules.test.js` (`A1–A8`, `B1–B6`, `C1–C8`, `D1` fuzz matrix) | **Verified** |
| **5** | **Performance: LazyColumn Keys, Paging 3 Projections, Baseline Profile, Startup Benchmark, Native Symbols** | `UdhaarDao.kt`, `UdhaarProjections.kt`, `LedgerRepository.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`, `baselineprofile/src/main/java/.../BaselineProfileGenerator.kt`, `StartupBenchmarks.kt`, `app/build.gradle.kts` (`ndk { debugSymbolLevel = "SYMBOL_TABLE" }`) | `ColdStartPerformanceAndStrictModeTest.kt`, `LedgerRepositoryTest.kt` | **Verified** |
| **6** | **Accessibility: Audited `contentDescription`, Clickable `Role`s, TalkBack & `fontScale = 2.0f` UI Tests** | `DashboardScreen.kt`, `AddEditUdhaarScreen.kt`, `PeopleDirectoryScreen.kt`, `CommunicationSheets.kt`, `DriveBackupPassphraseDialog.kt`, `DriveRestorePassphraseDialog.kt` | `AccessibilityTalkBackFontScaleTest.kt`, `ExampleRobolectricTest.kt` | **Verified** |
| **7** | **Translations (5 Languages: `en`, `hi`, `bn`, `as`, `ne`) + CI Untranslated/Missing Key Test** | `app/src/main/res/values*/strings.xml`, `MainActivity.kt`, `AddEditPartyAndAmountSection.kt`, `AddEditTermsAndInterestSection.kt` | `ExampleUnitTest.kt` (`plainLanguageStyleGuide_andNoBannedWordsOrAllCapsRemain`) | **Verified** |
| **8** | **Modular Files `< 600` Lines + Zero Raw `Log` Calls** (`VerificationSheets`, `CommunicationSheets`, `EncryptedStorageManager` split; `SafeLogger` used everywhere) | `VerificationSheets.kt` (546), `VerificationIncomingDialogs.kt` (478), `PaymentProfileShareSheets.kt` (523), `CommunicationSheets.kt` (576), `ChatMessageComponents.kt` (544), `ChatSafetyDialogs.kt` (559), `EncryptedStorageManager.kt` (408), `SqlCipherMigrationEngine.kt` (442), `KeystoreCryptoAndFileHelper.kt` (188) | `AppLoggerAndSecurityHardeningTest.kt` | **Verified** |
| **9** | **CI Hardening: Gradle Managed Device (`ReleaseSmokeInstrumentedTest`), Dependabot, Fail on Detekt Warnings** | `.github/workflows/ci.yml` (`managed-device-smoke-test`), `.github/dependabot.yml`, `app/build.gradle.kts` (`--max-issues 0`), `config/detekt/detekt.yml` (`maxIssues: 0`) | `web-tests/stage_s10_release.test.js`, `gradle :app:detekt` | **Verified** |
| **10** | **Version Bump to `32.0`** | `app/build.gradle.kts` (`versionCode = 32`, `versionName = "32.0"`), `version.properties`, `package.json` | `ReleaseSmokeTest.kt` | **Verified** |

---

## 3. Security & Privacy Invariants

1. **Local Storage Encryption:** 256-bit SQLCipher Room database (`UdhaarDatabase` v15) with fail-closed Android Keystore key wrapping (`EncryptedStorageManager.kt`).
2. **Zero-Knowledge Backups:** PBKDF2-HMAC-SHA256 (600,000 iterations) + AES-256-GCM (`DriveBackupCrypto.kt`) for local `.udhaar` and Google Drive `appDataFolder` backups.
3. **Zero Unverified Cloud Access:** Every Firestore match rule in `firestore.rules` requires `request.auth != null && request.auth.token.email_verified == true`.
4. **Zero-PII Logging:** All application log statements use `SafeLogger`, which scrubs phone numbers, emails, UPI IDs, amounts, and secrets before forwarding to `AppLogger`.
5. **No Server-Side Cloud Functions or Cloud KMS in Production:** Production deployments exclusively deploy `hosting`, `firestore:rules`, and `firestore:indexes` on Firebase Spark.
