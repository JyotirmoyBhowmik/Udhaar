# Udhaar Operations & Free-Tier Monitoring Guide (Spark Mode)

This document provides operational instructions for monitoring and managing Udhaar running under the **Google Firebase Spark (free) plan**. It covers real-time usage observation in the Firebase Console, the system's fail-safe behavior when daily quotas are reached, non-fatal telemetry diagnostics, and a step-by-step runbook for graduating to the pay-as-you-go **Blaze** plan if organic growth demands it.

---

## 1. Firebase Spark Daily Quotas Overview

The Udhaar architecture runs with **zero cloud infrastructure costs** on the Firebase Spark tier. All cloud sync and guest web links operate strictly within these hard daily limits:

| Resource | Spark Daily Free Limit | Reset Time | System Impact at Limit |
| :--- | :--- | :--- | :--- |
| **Cloud Firestore Document Reads** | **50,000 reads / day** | 00:00 PST (13:30 IST) | Cloud queries reject with `RESOURCE_EXHAUSTED` |
| **Cloud Firestore Document Writes** | **20,000 writes / day** | 00:00 PST (13:30 IST) | Cloud updates reject with `RESOURCE_EXHAUSTED` |
| **Cloud Firestore Document Deletes** | **20,000 deletes / day** | 00:00 PST (13:30 IST) | Deletion jobs queue until next quota cycle |
| **Cloud Firestore Storage** | **1 GiB total stored data** | Continuous limit | Exceeding 1 GiB blocks writes permanently |
| **Firebase Hosting Bandwidth** | **10 GB / month (~360 MB / day)** | Monthly billing cycle | Hosting returns HTTP 509 Bandwidth Exceeded |
| **Firebase Authentication** | **Unlimited (Email/Google/Anon)** | N/A | Free tier covers unlimited verified accounts |
| **Cloud Storage for Firebase** | **0 bytes (Spark Disabled)** | N/A | Photo uploads disabled; receipts rendered locally |
| **Cloud Functions for Firebase** | **0 invocations (Spark Disabled)** | N/A | Code preserved in `/blaze_disabled/functions` |

---

## 2. Watching Quota Usage in Firebase Console

To inspect current daily consumption and avoid unexpected service pauses:

### Step 2.1: Open Cloud Firestore Usage Dashboard
1. Navigate to the [Firebase Console](https://console.firebase.google.com/).
2. Select your Udhaar project (e.g., `udhaar-prod` or `demo-udhaar-spark`).
3. In the left navigation menu, expand **Build** and click **Firestore Database**.
4. Click on the **Usage** tab at the top of the page.
5. Review the three main graphs:
   - **Document Reads**: Shows read operations over the last 24 hours. Keep under 50,000.
   - **Document Writes**: Shows write operations over the last 24 hours. Keep under 20,000.
   - **Document Deletes**: Shows delete operations over the last 24 hours. Keep under 20,000.

### Step 2.2: Check Storage Consumption
1. In the same **Firestore Database** section, inspect the **Usage > Storage size** graph.
2. Verify total stored data is well below 1.0 GiB (typically 10–50 MB for tens of thousands of lightweight ledger documents).
3. Weekly pruning of expired links by `CleanExpiredGuestLinksWorker` ensures tombstone and expired link actions do not accumulate.

### Step 2.3: Check Firebase Hosting Bandwidth
1. In the left navigation menu, select **Build > Hosting**.
2. Click the **Usage** tab.
3. Review **Data transferred** across the current 30-day window against the 10 GB quota.

---

## 3. Behavior at Quota Exhaustion (`RESOURCE_EXHAUSTED`)

### 3.1 Zero Data Loss Guarantee
Udhaar is engineered with a **local-first, fail-closed offline architecture**:
- **Android**: Room with SQLCipher is the primary, single source of truth. All ledger entries, installments, contacts, and repayments commit synchronously to local encrypted storage.
- **Web**: IndexedDB maintains local state.
- **Outbox Queue**: Cloud synchronization mutations are held in the local `sync_outbox` table. When Firestore returns `RESOURCE_EXHAUSTED` (HTTP 429), the outbox worker pauses with exponential backoff and automatically resumes sync when the daily quota resets at midnight PST.

### 3.2 User Experience & Plain-Language Communications
When the daily limit is reached:
1. No raw technical errors or stack traces are ever shown to users.
2. The Android client and Web guest portal display a polite, clear notice:
   > *"Sync is resting until tomorrow to keep Udhaar free for everyone. All your records are safely saved on your device."*
3. The app remains 100% functional offline: creating, updating, viewing, calculating interest, generating receipts, and browsing historical transactions.

### 3.3 Zero-PII Crashlytics Non-Fatal Diagnostics
When `RESOURCE_EXHAUSTED` occurs, `AppLogger` records a non-fatal event in Firebase Crashlytics:
- **Error ID**: `RESOURCE_EXHAUSTED` or `FIRESTORE_RESOURCE_EXHAUSTED`
- **Metadata**: Sanitized link ID or operation type (e.g., `linkId: "guest-..."`).
- **Zero PII**: No phone numbers, borrower names, monetary amounts, UPI IDs, notes, or credentials are ever recorded.
- **Install ID**: Anonymized `anon-<uuid>` token; no user account emails or device identifiers.

Other tracked non-fatal Crashlytics events:
- `SQLCIPHER_INIT_FAILED`: Secure database initialization issue (device Keystore failure).
- `RESTORE_FAILED`: Google Drive encrypted backup restore error or corruption.
- `GUEST_LINK_PUBLISH_FAILED`: Transient network or App Check validation failure on link creation.

---

## 4. Runbook: Upgrading from Spark to Blaze Plan

If community adoption grows beyond 50,000 daily reads or if photo attachments / push notifications require cloud backends, follow these steps to transition cleanly from Spark to Blaze:

### Step 4.1: Associate Billing Account
1. Open [Firebase Console](https://console.firebase.google.com/) > Project Settings > **Usage and billing**.
2. Click **Modify plan** and select **Blaze (Pay as you go)**.
3. Attach a valid Google Cloud Billing account with budget alerts set to \$5 or \$10/month.

### Step 4.2: Enable Blaze Features in Gradle
1. In `gradle.properties` (or CI build environment variables), toggle:
   ```properties
   udhaar.tier=blaze
   udhaar.cloudFeatures=true
   udhaar.blaze.enabled=true
   ```
2. In `app/build.gradle.kts`, `BLAZE_FEATURES_ENABLED` will compile as `true`, enabling Cloud Storage proof viewer and background Cloud Functions sync.

### Step 4.3: Restore Blaze-Disabled Components
1. **Cloud Functions**:
   Move preserved files from `blaze_disabled/functions/` back to root `functions/`:
   ```bash
   git mv blaze_disabled/functions functions
   ```
2. **Cloud Storage Rules**:
   Move preserved rules from `blaze_disabled/storage.rules` back to root:
   ```bash
   git mv blaze_disabled/storage.rules storage.rules
   ```
3. **Legacy Tests**:
   Move `blaze_disabled/tests/firestore.legacy.test.js` back to `functions/test/`.

### Step 4.4: Deploy Cloud Functions and Storage Rules
Run full deployment with the Firebase CLI:
```bash
firebase deploy --only firestore:rules,firestore:indexes,storage,functions,hosting
```

---

## 5. Security & Emergency Incident Response

- **Abuse Mitigation**: If an attacker attempts to exhaust quotas via the web guest portal, App Check enforces valid reCAPTCHA tokens. If needed, toggle `guest_links_enabled = false` in Firebase Remote Config to temporarily suspend link issuance without affecting local ledger usage.
- **Account Deletion**: In-app **Settings > Delete my account and data** processes batched deletes client-side within Firestore Spark write quotas.
