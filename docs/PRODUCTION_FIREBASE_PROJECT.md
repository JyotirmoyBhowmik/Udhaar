> [!WARNING]
> **OBSOLETE / HISTORICAL REFERENCE (Pre-v32):** Any sections below referencing legacy Cloud Functions, Cloud KMS, Blaze-only server endpoints, or "Platform v2" are **superseded and archived** under `archive/`. For the canonical Udhaar v32.0 Spark Mode architecture, security rules, and data safety declarations, see [`docs/REALITY_CHECK.md`](REALITY_CHECK.md), [`docs/PLAY_DATA_SAFETY.md`](PLAY_DATA_SAFETY.md), and [`README.md`](../README.md).

# Udhaar — API Key Restrictions & Dedicated Production Firebase Project Guide (v29.0 Spark Mode)

This document audits the current Firebase configuration in `app/src/release/google-services.json`, specifies the **exact Google Cloud Console API key restrictions** required before public launch, and provides the step-by-step migration runbook to move off the shared AI Studio project onto a dedicated production Firebase Spark project.

---

## 1. Audit of Current `app/src/release/google-services.json`

Inspecting `app/src/release/google-services.json` in this repository reveals:

| Field | Current Value | Risk / Status |
| :--- | :--- | :--- |
| **`project_id`** | `gen-lang-client-0216629575` | Shared AI Studio / dev project — **must be replaced** with a dedicated production project (e.g., `udhaar-prod`) before wide GA |
| **`project_number`** | `528046585099` | Shared AI Studio project number |
| **`mobilesdk_app_id`** | `1:528046585099:android:468fd8b6debc32fcbade0e` | Android app ID bound to `com.jyotirmoyb.udhaar` |
| **`package_name`** | `com.jyotirmoyb.udhaar` | Registered on Google Play Console — **never change** |
| **OAuth Android SHA-1** | `a693704d0ec3a1da4f2c275569aff90fbd3a40c3` | Single certificate hash registered; verify Play App Signing + Upload key SHA-1s are both registered |
| **OAuth Web Client ID (type 3)** | `528046585099-iklgaumm23ndn59kovmlgvqcomlbb4kq.apps.googleusercontent.com` | Used by Credential Manager Google Sign-In |
| **Android API Key (`current_key`)** | `AIzaSyBnlNuS6il5y53n4fUfICJSd3mtmAS-WiM` | **Live restriction state cannot be verified from the repo** — must be locked down in Google Cloud Console (Section 2 below) |

> [!IMPORTANT]
> **Why live API key restrictions cannot be checked automatically from Git:**
> `google-services.json` contains only the public client API key string — Google Cloud stores application and API restrictions server-side in IAM / Credentials. You **must** verify and apply the restrictions below directly in Google Cloud Console (`https://console.cloud.google.com/apis/credentials?project=<PROJECT_ID>`).

---

## 2. Exact Google Cloud Console API Key Restrictions

Open **Google Cloud Console &rarr; APIs & Services &rarr; Credentials** for your Firebase project (`gen-lang-client-0216629575` today, or `udhaar-prod` after Section 3).

### A. Android Release API Key (`AIzaSyBnlNuS6il5y53n4fUfICJSd3mtmAS-WiM` or new prod Android key)

1. **Set an Application Restriction**: Choose **Android apps**.
2. **Add Package Name & SHA-1 Fingerprints** (add ALL production signing certificates):
   - **Package name**: `com.jyotirmoyb.udhaar`
   - **SHA-1 (Google Play App Signing certificate)**: Copy from Play Console &rarr; *Test and release* &rarr; *Setup* &rarr; *App signing* &rarr; *App signing key certificate* (`SHA-1`).
   - **SHA-1 (Upload key certificate)**: Copy from Play Console &rarr; *App signing* &rarr; *Upload key certificate* (`SHA-1`) or run `keytool -list -v -keystore my-upload-key.jks -alias upload`.
   - *(Remove any debug keystore SHA-1 from the release API key! Keep debug SHA-1s strictly on a separate debug/staging project or separate debug key.)*
3. **Set an API Restriction**: Choose **Restrict key** and allow **ONLY** these Spark-mode Android APIs:
   - `Identity Toolkit API` (`identitytoolkit.googleapis.com`) — Firebase Auth
   - `Token Service API` (`securetoken.googleapis.com`) — Firebase ID token refresh
   - `Cloud Firestore API` (`firestore.googleapis.com`) — Direct Firestore SDK
   - `Firebase Installations API` (`firebaseinstallations.googleapis.com`) — Installations / App Check / FCM
   - `Firebase App Check API` (`firebaseappcheck.googleapis.com`) — Play Integrity attestation
   - `Firebase Cloud Messaging API` (`fcm.googleapis.com`) & `FCM Registration API` (`fcmregistrations.googleapis.com`) — Optional push registration
   - `Firebase Remote Config API` (`firebaseremoteconfig.googleapis.com`) — Spark tier & kill switches
   - *(Optional, only if opt-in telemetry is enabled)* `Firebase Management / Crashlytics / Measurement` APIs (`firebasecrashlytics.googleapis.com`)
4. **Explicitly DO NOT allow on the Android key**:
   - `Generative Language API` / `Vertex AI API` (Gemini) — prevents quota theft on `gen-lang-client-*` projects!
   - `Cloud Functions API` (`cloudfunctions.googleapis.com`) — disabled in Spark Mode
   - `Cloud Storage for Firebase API` — disabled in Spark Mode
   - `Cloud KMS API` / `Secret Manager API` — server-only, never callable from clients
   - `Maps SDK` / `Places API` / any billable Google Cloud API

### B. Separate Web API Key (for Firebase Hosting & `/u/` Guest Portal)

Create or edit the **Browser / Web key** (never reuse the Android-restricted key for Web):

1. **Application Restriction**: Choose **Websites (HTTP referrers)**.
2. **Allowed Referrers** (add only your real production origins):
   - `https://udhaar.jyotirmoyb.com/*`
   - `https://app.udhaar.jyotirmoyb.com/*`
   - `https://<your-prod-project-id>.web.app/*`
   - `https://<your-prod-project-id>.firebaseapp.com/*`
3. **API Restriction**: Choose **Restrict key** and allow **ONLY**:
   - `Identity Toolkit API` (`identitytoolkit.googleapis.com`)
   - `Token Service API` (`securetoken.googleapis.com`)
   - `Cloud Firestore API` (`firestore.googleapis.com`)
   - `Firebase Installations API` (`firebaseinstallations.googleapis.com`)
   - `Firebase App Check API` (`firebaseappcheck.googleapis.com`)
   - `Firebase Remote Config API` (`firebaseremoteconfig.googleapis.com`)

### C. Verification Command (Local Check with `gcloud` — Optional)

```bash
gcloud services api-keys list --project=<PROJECT_ID> --format="table(name,displayName,restrictions)"
```
Confirm `restrictions.androidKeyRestrictions.allowedApplications` contains only `com.jyotirmoyb.udhaar` and `restrictions.apiTargets` contains no Generative Language / Vertex / Functions APIs.

---

## 3. Switching to a Dedicated Production Firebase Project (`udhaar-prod`)

Moving off `gen-lang-client-0216629575` ensures zero blast radius from AI Studio prototyping, clean Spark quota isolation, and strict production IAM boundaries.

### Step 1: Provision the Dedicated Spark Project
1. Open [Firebase Console](https://console.firebase.google.com/) &rarr; **Add project** &rarr; Create `udhaar-prod` (stay on **Spark Free Plan**, do **not** link a billing account).
2. Enable **Authentication** providers:
   - Email/Password (enable verification email template)
   - Google (configure support email)
   - Anonymous (used strictly for `/u/` capability links under App Check)
3. Create **Cloud Firestore** database `(default)` in **`asia-south1` (Mumbai)** in production mode.
4. Register the Android app with exact package name **`com.jyotirmoyb.udhaar`** and add **both** your Google Play App Signing SHA-1 and Upload Key SHA-1 (plus SHA-256 fingerprints).
5. Register the Web app (`udhaar-web-prod`) and enable Firebase Hosting.
6. Register **App Check**:
   - Android: **Play Integrity** (link Google Play Console project)
   - Web: **reCAPTCHA v3** (generate a dedicated production site key & secret key)
   - Enforce App Check on **Cloud Firestore** and **Authentication** after a 7-day monitoring soak.

### Step 2: Apply API Key Restrictions Immediately
Follow **Section 2** above on the newly created `udhaar-prod` Android key and Web key before committing or deploying config files.

### Step 3: Replace Project Config Files in the Repository
1. **Android Release Config**:
   - Download the new `google-services.json` from `udhaar-prod` and overwrite **`app/src/release/google-services.json`**.
   - Keep **`app/src/debug/google-services.json`** pointing to your separate dev/staging Firebase project (`udhaar-dev` or `gen-lang-client-0216629575`) so debug builds never touch production data.
   - Verify the `client_type: 3` Web OAuth Client ID in `app/src/release/google-services.json` matches what Google Sign-In (`CredentialManager`) expects (it is auto-wired into `default_web_client_id` by the Google Services Gradle plugin).
2. **Firebase CLI Target (`.firebaserc`)**:
   ```json
   {
     "projects": {
       "default": "udhaar-prod",
       "staging": "gen-lang-client-0216629575"
     }
   }
   ```
3. **Web Portal & Web App Config**:
   - Update the Firebase web SDK config object in `web/u/firebase-init.js` and `webapp/src/api/firebaseSdk.js` with `udhaar-prod`'s web `apiKey`, `authDomain`, `projectId`, `messagingSenderId`, and `appId`.
   - Update `RECAPTCHA_SITE_KEY` in `web/u/config.js` with your production reCAPTCHA v3 site key.
   - Confirm `web/site-config.js` keeps `UDHAAR_TIER: "spark"`.
4. **Hosting Targets (`infra/firebase.prod.json`)**:
   - Bind hosting targets with `firebase target:apply hosting udhaar-marketing-prod <site-id>` and `firebase target:apply hosting udhaar-app-prod <app-site-id>`.

### Step 4: Deploy Spark-Safe Rules, Indexes & Hosting
```bash
# 1. Verify rules tests pass locally first
firebase emulators:exec --project demo-udhaar-spark --only firestore "npm --prefix rules-tests test"

# 2. Switch CLI to dedicated production project
firebase use udhaar-prod

# 3. Deploy ONLY Spark-free resources (never functions or storage)
firebase deploy --only firestore:rules,firestore:indexes,hosting
```

### Step 5: Update GitHub Actions CI/CD Secrets
In **GitHub &rarr; Settings &rarr; Secrets and variables &rarr; Actions**, update:
- `FIREBASE_SERVICE_ACCOUNT_UDHAAR_PROD`: JSON key for a least-privilege deployer service account in `udhaar-prod` (roles: `Firebase Hosting Admin`, `Cloud Datastore Index Admin`, `Firebase Rules Admin` — no Owner role).
- `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`: Your Play Console upload keystore.
- Verify release build gates pass:
  ```bash
  gradle :app:verifyReleaseVersioning :app:verifyReleaseNoDebugAppCheck :app:verifyReleaseR8 :app:bundleRelease
  ```
