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
| **Legacy Web Hardcoded UPI Fallback** | `web/u/guest.js` | Lines 2420–2426 | Hardcoded `udhaar@okaxis` fallback disabled; honest checks require lender's valid UPI ID or hide payment options. | Uncomment `actualUpiId = recordData?.lenderUpiId \|\| "udhaar@okaxis"` inside `[LEGACY-DISABLED]` block. |
| **Legacy Web UTR Suffix Slice** | `web/u/guest.js` | Lines 2315–2330 | Capped `utrLast4` slice disabled; full `referenceNumber` (up to 64 chars) is sent per action schema. | Uncomment `utrLast4` slice inside `[LEGACY-DISABLED]` block. |
| **Android Firebase Storage Provider** | `app/src/main/java/com/jyotirmoyb/udhaar/di/FirebaseModule.kt` | Lines 105–118 | Cloud Storage unavailable on Spark plan (returns 402/403). | Uncomment `provideFirebaseStorage` inside `[BLAZE-DISABLED]` markers. |
| **Android Attachment Cloud Storage Upload/Download** | `app/src/main/java/com/jyotirmoyb/udhaar/util/AttachmentManager.kt` | Lines 152–160 | Direct upload/download to Cloud Storage disabled on Spark plan. | Uncomment cloud upload/download inside `[BLAZE-DISABLED]` markers. |
| **Web Firebase Storage Client** | `web/u/firebase-init.js`, `web/u/guest.js` | Lines 47–50, 2465–2472 | Web Cloud Storage access disabled; optional proof stored in Firestore doc `proof/image`. | Uncomment `getStorageInstance` and `uploadProofToFirebaseStorage` in `[BLAZE-DISABLED]` blocks. |

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

---

## 3. Spark Setup Checklist: Enabling App Check Enforcement in Firebase Console

Firebase App Check protects free Spark tier Firestore quota from unauthorized scripts, scrapers, and abusive API calls.

### Step 1: Register Android App in Firebase Console
1. Open the [Firebase Console](https://console.firebase.google.com/) and navigate to your project.
2. In the left navigation, select **App Check** -> **Apps** tab.
3. Locate the Android app (`com.jyotirmoyb.udhaar`).
4. Click **Play Integrity**:
   - Link your Google Play account (or verify package name and SHA-256 certificate fingerprints from `keytool` / Play Console).
   - Set Token TTL to **1 hour** (recommended for mobile).
5. For Local Development / Debug builds:
   - In the overflow menu (three dots) next to the Android app, select **Manage debug tokens**.
   - Launch the debug app on an emulator or physical device. Look for the debug token in logcat:
     `D/DebugAppCheckProvider: Enter this debug token into the Firebase Console: <DEBUG_TOKEN>`
   - Add this debug token in the Firebase Console.

### Step 2: Register Web Portal in Firebase Console
1. Under **App Check** -> **Apps** tab, select the Web app.
2. Click **reCAPTCHA v3** (free tier):
   - Enter your reCAPTCHA v3 Site Secret from the [Google Cloud reCAPTCHA Console](https://cloud.google.com/recaptcha-enterprise).
   - Copy the generated **Site Key** into `web/u/config.js` (`const RECAPTCHA_SITE_KEY = "<YOUR_KEY>";`).
   - Set Token TTL to **1 hour**.

### Step 3: Monitor Traffic Before Enforcement
1. Allow both Android and Web apps to run for 24–48 hours in production or staging.
2. In **App Check** -> **APIs** tab -> **Cloud Firestore**:
   - Inspect the request metrics: **Verified requests** (green), **Unverified requests** (amber), and **Outdated client requests**.
   - Verify that all legitimate app traffic shows as **Verified**.

### Step 4: Enable Enforcement for Cloud Firestore
1. In **App Check** -> **APIs** tab, click **Cloud Firestore**.
2. Click the **Enforce** button.
3. In the confirmation dialog, click **Enforce**.
4. Once enforced, any incoming request to Cloud Firestore lacking a valid Play Integrity or reCAPTCHA v3 App Check token is rejected at the Google edge before consuming daily Spark database read/write quota.
This deploys Firestore rules, Firestore indexes, Cloud Storage rules, Cloud Functions v2, and Firebase Hosting.
