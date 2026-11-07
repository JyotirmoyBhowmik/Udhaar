# ADR 0008: Spark Mode — Zero-Cost Architecture on Free Firebase Tier

- **Status**: Accepted
- **Date**: 2026-11-08
- **Context**: Udhaar Platform (v24) operates on Google Cloud / Firebase. The owner has no Meta Business Account and no paid billing account, requiring a 100% free, sustainable, working system running exclusively on the Firebase Spark Plan.

---

## 1. Context and Problem Statement

Platform v2 incorporates Cloud Functions (v2), Google Cloud KMS envelope encryption, Secret Manager, reCAPTCHA Enterprise, Cloud Storage for receipt photos, Meta WhatsApp Cloud API OTP, and Brevo transactional email.

None of those components can run on the free Firebase Spark plan:
1. **Cloud Functions**: Deployment requires an active Cloud Billing account (Blaze plan) due to Cloud Build container registry requirements.
2. **Cloud Storage**: Free tier limits and upload pipelines require project billing to guard bucket quotas.
3. **Google Cloud KMS & Secret Manager**: Paid GCP services requiring active billing.
4. **Meta WhatsApp Cloud API**: Requires an approved Meta Business Manager account, payment method, and per-conversation fees for utility templates.
5. **Paid Email / SMS Gateways**: Third-party APIs with recurring usage fees.

The project requires an honest, secure, functional tier that deploys with `firebase deploy --only hosting,firestore:rules,firestore:indexes` on a project with zero billing (no billing-required services in Spark Mode), while keeping the Blaze Platform v2 code intact behind a toggle for future scaling.

---

## 2. Decision: Dual-Tier Architecture (`UDHAAR_TIER`)

We introduce an operational tier switch:
```text
UDHAAR_TIER = "spark" | "blaze" (Default: "spark")
```

Defined across:
- `web/site-config.js` (`SITE_CONFIG.UDHAAR_TIER`)
- Android `BuildConfig.UDHAAR_TIER`
- Firebase Remote Config (`UDHAAR_TIER`)
- Cloud Functions runtime guard (`enforceTierGuard()`)

### In Spark Mode:
- **Client Direct to Firestore**: Clients communicate directly with Cloud Firestore guarded by comprehensive security rules (`firestore.rules`). Zero `/api/v1/**` server calls are made.
- **Firebase Auth (Free)**: User registration and login use Firebase Authentication directly:
  - Email + Password (min 10 characters, client strength meter, common password reject).
  - Google Sign-In (Credential Manager on Android, GoogleAuthProvider on Web).
  - Native Firebase Email Verification (free).
  - Anonymous sign-in exclusively for guest capability links (`/u/r/<recordId>#<secret>`).
- **Offline-First Room & IndexedDB**: The phone's Room + SQLCipher database remains the single source of truth. Sync is optional and off by default.
- **Client-Side Conflict Resolution**: Reuses existing `SyncConflictPolicy` and Hybrid Logical Clocks (HLC) locally in the client.

---

## 3. What Spark Mode Cannot Do (Explicit Limitations)

Honesty is a core tenet of Udhaar. In Spark Mode, where free infrastructure means a weaker guarantee, the app and documentation state so explicitly:

| Capability | Blaze Platform v2 Guarantee | Spark Mode Reality (Honest Guarantee) |
| :--- | :--- | :--- |
| **Phone Verification** | Proved via Meta WhatsApp OTP / DLT SMS | **Unverified contact detail only**. Cannot prove phone ownership for free. Stored as `phoneStatus: "unverified"`, labeled "Number not verified". Never used for login or recovery. |
| **Push Notifications & Emails** | Server FCM push & Brevo transactional emails | **Client-polled local notifications**. No server push. WorkManager periodically checks for updates while app is active. Alerts can be delayed. |
| **Scheduled Reminders** | Cloud Scheduler + PubSub cron triggers | **Device-local alarms only** via Android `AlarmManager` / `WorkManager`. |
| **Payment Proof Photos** | Encrypted upload to Google Cloud Storage | **Text references only**. Photo uploads to cloud storage are disabled. |
| **Rate Limiting & Abuse** | Server Redis / Firestore atomic budget counters | **Firebase Auth native throttling**, client disposable-domain blocklist, and strict Firestore security rules per-user document caps. |
| **Data Encryption at Rest** | Cloud KMS Envelope Encryption (asia-south1) | **Standard Google Firestore encryption-at-rest**, plus optional client-side AES-256-GCM / PBKDF2 passphrase encryption. |
| **Daily Project Quotas** | Paid scaling | **Spark limits: ~50,000 reads / ~20,000 writes per day**. Monitored via client write budgets (500/day/user) and graceful UI backoff. |

---

## 4. Migration Path: Transitioning from Spark to Blaze

When the project owner upgrades the Firebase project to the Blaze plan:
1. **Enable Cloud Billing**: Link a Google Cloud Billing account in Firebase Console.
2. **Switch Tier Config**:
   - Set `UDHAAR_TIER = "blaze"` in `web/site-config.js`, Android build property `udhaar.tier=blaze`, and Remote Config.
3. **Provision Secrets & KMS**:
   - Run `bash infra/scripts/setup-kms-and-secrets.sh <PROJECT_ID>` to provision Cloud KMS `user-dek` and Secret Manager secrets (`UDHAAR_SERVER_SECRET`, `WHATSAPP_API_TOKEN`, etc.).
4. **Deploy Backend**:
   - Run `firebase deploy --only functions,storage:rules`.
5. **Client Adoption**:
   - Web App and Android apps detect `UDHAAR_TIER = "blaze"`, activate WhatsApp OTP verification, enable Cloud Storage photo uploads, and connect to the Cloud Functions sync gateway.
