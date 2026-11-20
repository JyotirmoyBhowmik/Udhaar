# Udhaar — Reality Check & Architectural Audit (v27 / v28.0 Spark Mode)

**Document Version**: 28.0.0-AUDIT  
**Audit Date**: 2026-11-11  
**Scope**: Full-stack verification of `app/`, `web/`, `webapp/`, `firestore.rules`, `firestore.indexes.json`, `.github/workflows/`, `README.md`, and `docs/launch-checklist.md`.

---

## 1. Executive Summary

Udhaar v28.0 operates in **Spark Mode (`UDHAAR_TIER = "spark"`)** by default, running 100% on the free Firebase Spark tier without requiring a Google Cloud billing account, Cloud Functions (`functions:api`), Cloud Storage buckets, Cloud KMS, or Secret Manager at runtime.

Key architectural truths verified in code and automated tests:
- **Android Local-First Core**: Records, parties, partial payments, installment schedules, and audit trails are stored locally on the device in a 256-bit **SQLCipher (`net.zetetic:android-database-sqlcipher`)** Room database (`UdhaarDatabase.kt`, schema v13).
- **Cloud Features Default Off (`BuildConfig.CLOUD_FEATURES = false`)**: Unfinished cloud UI surfaces are gated behind `BuildConfig.CLOUD_FEATURES` (default `false` in `app/build.gradle.kts`) so production builds never expose dead-end or unconfigured cloud screens.
- **Firebase Auth & Email Verification Gate**: When cloud account access is used, authentication uses **Firebase Auth** directly (Google Sign-In via Jetpack Credential Manager / Web popup, and Email/Password with mandatory email verification, password reset, and re-authentication). Cloud sync stays strictly disabled until `email_verified == true`.
- **Guest Capability Links without Cloud Functions**: The lender app writes capability-protected snapshots to `/guestLinks/{linkId}` (where `linkId = SHA256(secret)`). The guest web portal (`web/u/guest.js`) signs in anonymously via the locally bundled Firebase JS SDK, reads `/guestLinks/{linkId}`, and creates immutable proposal documents in `/guestLinks/{linkId}/actions/{actionId}` (`confirm`, `dispute`, `claim_1..5`, `date_1..3`). The lender app polls actions via a Firestore collection-group query on `ownerUid` (`GuestActionPollingWorker.kt`).
- **Real Adaptive Layouts & 48 dp Touch Targets**: At widths `>= 600.dp`, `DashboardScreen` and `PeopleDirectoryContent` render side-by-side list-detail panes. `AddEditUdhaarScreen` caps form width at `560.dp` and supports landscape scrolling. All 43 previously undersized touch targets have been upgraded to `>= 48.dp` (`heightIn(min = 48.dp)` / `sizeIn(minWidth = 48.dp, minHeight = 48.dp)`) and verified at `fontScale = 2.0`.
- **In-App Help & Contact + Spark Cloud Data Deletion**: Users have an in-app Help & Contact screen (`HelpAndContactSheet.kt`) with Support (`admin@jyotirmoyb.com`), Grievance Officer (`grievance@jyotirmoyb.com`), Privacy Policy, and Terms of Service links, plus a working **"Delete my cloud data"** button that deletes the user's Firestore documents across all Spark collections and resets local records to `LOCAL_ONLY`.

---

## 2. Launch Checklist Reality Check Matrix (`docs/launch-checklist.md`)

| Category | Feature / Claim | Proving File | Proving Test | Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Infra** | Spark-Safe CI/CD Deployments (`hosting`, `firestore:rules`, `firestore:indexes` only) | `.github/workflows/deploy-prod.yml`, `.github/workflows/deploy-staging.yml` | `functions/test/web_client.test.js` | **Proven** | `functions:api` and `storage` rules removed from Spark deploy workflows. |
| **1. Infra** | Dual Origin Isolation & Strict CSP | `infra/firebase.prod.json`, `firebase.json` | `functions/test/web_client.test.js` | **Proven** | `connect-src` restricted to Firebase Auth, Firestore, and App Check endpoints; zero inline code. |
| **1. Infra** | Optional Blaze Enterprise Modules (`functions/`) | `functions/src/crypto/envelopeCrypto.js`, `functions/src/config/secrets.js` | `functions/test/stage_r2_cryptography.test.js` | **Optional (Blaze Only)** | Retained in repository for optional Blaze deployments; not deployed or required in Spark Mode. |
| **2. Auth** | Firebase Auth (Google Credential Manager + Email/Password + Reset + Re-auth) | `app/src/main/java/com/jyotirmoyb/udhaar/ui/sheets/settings/AccountAndWebAccessSection.kt` | `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt` | **Proven** | Zero `/api/v1` calls in Spark Mode; Cloud Sync switch disabled until email is verified. |
| **2. Auth** | Unverified Contact Phone Numbers | `firestore.rules`, `AccountAndWebAccessSection.kt` | `functions/test/firestore_rules.test.js` | **Proven** | Phone numbers are optional contact metadata (`phoneStatus: "unverified"`), never used for login or recovery. |
| **3. Data** | Spark Collection Security Rules (`users/{uid}`, `records`, `people`, `events`, `plans`, `attachments`, `meta/syncSignal`) | `firestore.rules` | `functions/test/firestore_rules.test.js` | **Proven** | Tested for stranger, other user, unverified email, owner verified, and no updates on `events`. |
| **3. Data** | Guest Links & Fixed 10-Slot Action Rules (`guestLinks/{linkId}/actions/{actionId}`) | `firestore.rules`, `web/u/guest.js` | `functions/test/firestore_rules.test.js` | **Proven** | Tested for expired link, revoked link, action ID caps (`confirm`, `dispute`, `claim_1..5`, `date_1..3`), and no updates/deletes. |
| **3. Data** | Collection-Group Polling on `ownerUid` | `app/src/main/java/com/jyotirmoyb/udhaar/sync/GuestActionPollingWorker.kt` | `functions/test/firestore_rules.test.js` | **Proven** | Composite index in `firestore.indexes.json` + collection-group rule verified. |
| **4. Web** | Locally Bundled Firebase JS SDK (No CDN, No `window.__udhaarFirebaseAuth`) | `webapp/src/api/firebaseSdk.js`, `scripts/build-webapp.js` | `functions/test/web_client.test.js` | **Proven** | Official `firebase` npm package bundled into `webapp/dist/vendor/` and `web/u/vendor/`. |
| **4. Web** | 5-Language Terms of Service & Privacy Policy (`en`, `hi`, `bn`, `as`, `ne`) | `web/terms.html`, `web/privacy.html` | `functions/test/landing_privacy_page.test.js` | **Proven** | Linked from landing page, privacy page, and Android app with zero inline styles/scripts. |
| **5. Android** | Real Adaptive Layouts (`>= 600.dp` List-Detail, `560.dp` Form Width Cap, Landscape) | `DashboardScreen.kt`, `PeopleDirectoryScreen.kt`, `AddEditUdhaarScreen.kt` | `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt` | **Proven** | Verified with Robolectric Compose tests on `w800dp` tablet and `w640dp-h360dp` landscape qualifiers. |
| **5. Android** | 48 dp Minimum Touch Targets at `fontScale = 2.0` | `app/src/main/java/com/jyotirmoyb/udhaar/ui/**` | `app/src/test/java/com/jyotirmoyb/udhaar/ExampleRobolectricTest.kt` | **Proven** | All 43 sub-48dp controls updated to `heightIn(min = 48.dp)` / `size(48.dp)` and tested at `fontScale = 2.0f`. |
| **6. Privacy** | In-App Help & Contact + Spark Mode "Delete My Cloud Data" | `HelpAndContactSheet.kt`, `AccountAndWebAccessSection.kt` | `app/src/test/java/com/jyotirmoyb/udhaar/ui/sheets/AccountAndWebAccessSectionTest.kt` | **Proven** | Deletes user's Firestore documents across all Spark collections and resets local records to `LOCAL_ONLY`. |

---

## 3. Trust Center Claims Reality Check (`docs/TRUST_CLAIMS.md`)

1. **Claim 1: "Your records stay locked on your phone"**:
   - **Status**: **PROVEN**. Verified in `UdhaarDatabase.kt` and `EncryptedStorageManager.kt`. The Android app operates 100% locally with 256-bit SQLCipher encryption and Android Keystore key wrapping.
2. **Claim 2: "Your backup password never leaves your phone"**:
   - **Status**: **PROVEN**. Verified in `DriveBackupCrypto.kt` (PBKDF2-HMAC-SHA256 with 600,000 iterations + AES-256-GCM authenticated encryption).
3. **Claim 3: "What the web page stores for a guest"**:
   - **Status**: **PROVEN**. Verified in `GuestWebCloudRepository.kt`, `web/u/guest.js`, and `firestore.rules` (capability secret stays in URL `#fragment`, `linkId = SHA256(secret)`, and guest actions are immutable proposals in fixed action slots).
4. **Claim 4: "How long data is kept"**:
   - **Status**: **PROVEN**. Guest links enforce `expiresAt` in `firestore.rules` (`request.time < link.expiresAt`) and can be revoked (`status == "revoked"`) at any time from the lender app.
5. **Claim 5: "How to delete your data"**:
   - **Status**: **PROVEN**. Users can delete individual records or wipe local data anytime, use the in-app **"Delete my cloud data"** button in Settings to erase all synced Firestore documents and guest links in Spark Mode, or submit a web deletion request via `privacy.html`.
6. **Claim 6: "What Udhaar does NOT do"**:
   - **Status**: **PROVEN**. The codebase contains zero payment execution APIs, zero third-party ad SDKs, zero background contact book uploads, and zero phone-number authentication in Spark Mode.
