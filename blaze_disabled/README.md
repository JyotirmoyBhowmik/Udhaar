# Blaze-Disabled Inventory & Upgrade Runbook

This directory (`blaze_disabled/`) contains all code, configurations, rules, and scripts preserved from the Firebase Blaze (paid) plan tier.

Under Udhaar's **Code Preservation Rules**, code requiring billing or paid services is never deleted. When the project operates on the **Firebase Spark (free) tier**, all active execution paths run strictly on Spark-compatible components (Firestore `(default)` database, Firebase Auth, Firebase Hosting, App Check, Remote Config, and FCM client features).

---

## 1. Inventory of Preserved Artifacts

| Item | Original Path | Preserved Path / Location | Reason | Restore Steps |
|---|---|---|---|---|
| **Cloud Functions Source Tree** | `functions/` | `blaze_disabled/functions/` | Cloud Functions v2 deployment requires billing account (Blaze plan). | Run `git mv blaze_disabled/functions functions`. |
| **Cloud Storage Security Rules** | `storage.rules` | `blaze_disabled/storage.rules` | Cloud Storage bucket activation and access requires Blaze plan. | Run `git mv blaze_disabled/storage.rules storage.rules`. |
| **Previous Firestore Rules (Pre-ActionType)** | `firestore.rules` | `blaze_disabled/firestore.rules.previous` | Previous rules version prior to Spark actionType free rate-limit and Google verification tightening. | Diff or restore if legacy action schemas are needed. |
| **Blaze Firebase Configuration** | `firebase.json` | `blaze_disabled/firebase.blaze.json` | Active `firebase.json` must only declare `"firestore"` and `"hosting"` for Spark deploy. | Replace `firebase.json` with `blaze_disabled/firebase.blaze.json`. |
| **Android Firebase Functions Provider** | `app/src/main/java/com/jyotirmoyb/udhaar/di/FirebaseModule.kt` | Lines 68–78 | `FirebaseFunctions` instance initialization disabled on Spark. | Set `udhaar.blaze.enabled=true` in `gradle.properties` or toggle `BLAZE_FEATURES_ENABLED = true`. |
| **Named Firestore Database ID** | `app/src/main/java/com/jyotirmoyb/udhaar/di/FirebaseModule.kt` | Lines 57–62 | Spark free tier covers only one database `(default)`; named DB resolution disabled. | Uncomment `FirebaseFirestore.getInstance(app, context.getString(R.string.firestore_database_id))`. |
| **Firestore Database ID String Resource** | `app/src/main/res/values/firebase_applet_config.xml` | Line 3 | Named DB ID retained but unused on Spark. | Keep resource or update with Blaze database ID. |
| **Android API Client (`/api/v1/**`)** | `app/src/main/java/com/jyotirmoyb/udhaar/sync/ApiClient.kt` | Lines 173–278 | HTTP calls to Cloud Functions endpoints fail closed with `FeatureUnavailableOnFreePlan`. | Set `udhaar.blaze.enabled=true` in `gradle.properties` or pass `blazeEnabled = true`. |
| **Android Functions Gradle Dependency** | `app/build.gradle.kts` | Line 230 | Dependency retained but marked unused in Spark mode. | Dependency remains active for Blaze builds. |
| **Web Feature Flag** | `web/u/config.js` | Line 8 (`BLAZE_FEATURES_ENABLED = false`) | Gates client web tier behavior. | Set `const BLAZE_FEATURES_ENABLED = true;` in `web/u/config.js`. |
| **Web Ledger Cloud API Call** | `web/ledger/ledger.js` | Lines 1821–1862 | Direct `fetch("/api/...")` commented out with `[BLAZE-DISABLED]`. | Uncomment the `fetch` block inside `[BLAZE-DISABLED START]` and `[BLAZE-DISABLED END]`. |
| **Android CI Functions Tests** | `.github/workflows/android-ci.yml` | Lines 35–42 | CI cannot run functions tests without `functions/` root. | Uncomment lines 35–42. |
| **Platform CI Functions & Emulator Tests** | `.github/workflows/ci.yml` | Lines 31, 61–67, 81–99, 126–145, 161–166 | Functions dependencies, tests, and audit commented out with `[BLAZE-DISABLED]`. | Uncomment the commented blocks in `.github/workflows/ci.yml`. |
| **Deploy Production Conformance Gate** | `.github/workflows/deploy-prod.yml` | Lines 38–41 | Functions unit test step commented out with `[BLAZE-DISABLED]`. | Uncomment `npm --prefix functions test`. |
| **Deploy Staging Smoke Check** | `.github/workflows/deploy-staging.yml` | Lines 66–69 | Functions E2E smoke test commented out with `[BLAZE-DISABLED]`. | Uncomment `node functions/test/playwright_e2e.test.js`. |
| **Guest Link whatsappOtp Firestore Field** | `app/src/main/java/com/jyotirmoyb/udhaar/data/cloud/GuestWebCloudRepository.kt` | Lines 140–147 | An OTP stored in a readable document provides no security on Spark; capability URL `#k=<secret>` is used instead. | Uncomment `whatsappOtp` write inside `[BLAZE-DISABLED]` markers. |

---

## 2. Step-by-Step Blaze Upgrade Runbook

When upgrading the Firebase project from **Spark (free)** to **Blaze (pay-as-you-go)**:

### Step 1: Restore File Paths
Execute git moves to restore directories to root:
```bash
git mv blaze_disabled/functions functions
git mv blaze_disabled/storage.rules storage.rules
cp blaze_disabled/firebase.blaze.json firebase.json
```

### Step 2: Enable Android Blaze Feature Flag
In `gradle.properties` (or via command line flag `-Pudhaar.blaze.enabled=true`):
```properties
udhaar.tier=blaze
udhaar.blaze.enabled=true
```
This enables:
- `FirebaseFunctions.getInstance(app, "asia-south1")` in `FirebaseModule.kt`
- HTTP endpoints in `ApiClient.kt`
- Server-side synchronization transport (`HttpSyncCloudTransport`) in `SyncWorker` and `RepositoryModule`

### Step 3: Enable Web Blaze Feature Flag
1. In `web/u/config.js`, set:
   ```javascript
   const BLAZE_FEATURES_ENABLED = true;
   ```
2. In `web/site-config.js`, set:
   ```javascript
   UDHAAR_TIER: "blaze"
   ```
3. In `web/ledger/ledger.js`, uncomment the `apiCall` fetch block between `// [BLAZE-DISABLED START]` and `// [BLAZE-DISABLED END]`.

### Step 4: Restore CI / CD Workflow Steps
Uncomment the blocks marked `# [BLAZE-DISABLED START]` in:
- `.github/workflows/android-ci.yml`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-prod.yml`
- `.github/workflows/deploy-staging.yml`

### Step 5: Deploy Full Platform to Blaze
```bash
firebase deploy --project <blaze-project-id>
```
This deploys Firestore rules, Firestore indexes, Cloud Storage rules, Cloud Functions v2, and Firebase Hosting.
