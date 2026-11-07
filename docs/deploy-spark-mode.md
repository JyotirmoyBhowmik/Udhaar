# Udhaar Spark Mode — Deployment & Firebase Console Guide

This guide describes how to deploy and operate Udhaar in **Spark Mode** (100% free, zero billing account required, zero-cost Google infrastructure).

---

## 1. Overview & Golden Rules

In Spark Mode:
- **No Cloud Functions** are deployed (Cloud Functions requires Blaze billing).
- **No Cloud Storage buckets** are deployed.
- **No Secret Manager, KMS, or paid WhatsApp/SMS APIs** are invoked.
- All client sync and capability links run **serverless** directly between the client apps (Android Room + Web IndexedDB) and Firebase Auth, Cloud Firestore, Firebase Hosting, Remote Config, and App Check.

### The Single Deployment Command
```bash
firebase deploy --only hosting,firestore:rules,firestore:indexes
```
> [!IMPORTANT]
> Never run `firebase deploy` without `--only hosting,firestore:rules,firestore:indexes` on a Spark plan project. Attempting to deploy Cloud Functions on Spark will fail with `HTTP 400: Billing account not configured`.

---

## 2. Firebase Console Configuration (One-Time Setup)

### Step 1: Create Free Firebase Project
1. Open [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** &rarr; Name it `udhaar-app` (or your choice).
3. Disable Google Analytics (optional, keeps setup simpler and privacy footprint minimal).
4. Verify the project is on the **Spark Plan (Free $0/month)**. Do **NOT** attach a credit card or billing account.

---

### Step 2: Configure Firebase Authentication
1. In the Firebase Console left menu, navigate to **Build** &rarr; **Authentication** &rarr; Click **Get Started**.
2. Under the **Sign-in method** tab:
   - **Email/Password**:
     - Enable **Email/Password**.
     - Leave "Email link (passwordless sign-in)" disabled initially (or enable once custom Hosting domain App Links are configured).
     - Click **Save**.
   - **Google**:
     - Enable **Google**.
     - Set your Project support email.
     - Click **Save**.
   - **Anonymous**:
     - Enable **Anonymous** (used strictly by guests opening `/u/` capability links with App Check).
     - Click **Save**.
3. Under **Settings** &rarr; **Templates**:
   - Customize the **Email address verification** template with sender name "Udhaar Ledger".

---

### Step 3: Create Cloud Firestore Database
1. Navigate to **Build** &rarr; **Firestore Database** &rarr; Click **Create database**.
2. **Database ID**: Leave as `(default)`.
3. **Location**: Select **`asia-south1` (Mumbai)** (or closest region to your user base).
4. **Security Rules**: Choose **Start in production mode** (our deploy command will overwrite with `firestore.rules`).
5. Click **Create**.

---

### Step 4: Import Remote Config Parameters
1. Navigate to **Build** &rarr; **Remote Config**.
2. Click **Create configuration** (or the three dots &rarr; **Publish from JSON**).
3. Copy the contents of [`docs/remote-config-template.json`](../docs/remote-config-template.json):
   ```json
   {
     "parameters": {
       "UDHAAR_TIER": {
         "defaultValue": { "value": "spark" },
         "description": "Operating tier: 'spark' (100% free serverless) or 'blaze' (cloud functions & enterprise APIs)"
       },
       "signup_enabled": {
         "defaultValue": { "value": "true" },
         "description": "Emergency kill switch to pause new user registrations if quotas approach limits"
       },
       "guest_links_enabled": {
         "defaultValue": { "value": "true" },
         "description": "Kill switch to pause creation of new /u/ capability links"
       },
       "max_client_daily_writes": {
         "defaultValue": { "value": "500" },
         "description": "Maximum allowed write mutations per client per day"
       }
     }
   }
   ```
4. Click **Publish changes**.

---

### Step 5: Configure App Check (Free Attestation)
1. Navigate to **Build** &rarr; **App Check** &rarr; **Apps** tab.
2. **Android App (`com.jyotirmoyb.udhaar`)**:
   - Click **Register** &rarr; Select **Play Integrity**.
   - Specify Token TTL: `1 hour`.
   - Link Play Console project if published.
3. **Web App**:
   - Obtain free reCAPTCHA v3 key from [Google reCAPTCHA Console](https://www.google.com/recaptcha/admin).
   - In App Check &rarr; Web app &rarr; Select **reCAPTCHA v3**.
   - Enter secret key &rarr; Token TTL: `1 hour`.
4. Keep enforcement in **Monitoring (Unenforced)** for the first 7 days, then switch to **Enforce** in the **APIs** tab.

---

## 3. CLI Deployment Steps

### Prerequisites
- Node.js 20+
- Firebase CLI (`npm install -g firebase-tools`)

### Commands
```bash
# 1. Login to Firebase CLI
firebase login

# 2. Select your Firebase Spark project
firebase use <your-project-id>

# 3. Validate your site-config.js points to Spark mode
# Ensure web/site-config.js has:
# UDHAAR_TIER: "spark"

# 4. Deploy rules, indexes, and hosting only
firebase deploy --only hosting,firestore:rules,firestore:indexes
```

### Expected Output:
```
✔  Deploy complete!

Project Console: https://console.firebase.google.com/project/<project-id>/overview
Hosting URL: https://<project-id>.web.app
```

---

## 4. Upgrading to Blaze Tier Later (Migration Path)

If traffic expands and the business acquires a payment method and Meta Business verification, upgrade seamlessly:

1. **In Firebase Console**: Click **Upgrade** on the bottom left and select the **Blaze (Pay-as-you-go)** plan.
2. **Enable Services**:
   - Google Cloud Secret Manager & Cloud KMS.
   - Cloud Functions for Firebase.
   - Cloud Storage bucket.
3. **Update Configuration**:
   - In `web/site-config.js`: Set `UDHAAR_TIER: "blaze"`.
   - In Remote Config: Set `UDHAAR_TIER: "blaze"`.
   - In `app/build.gradle.kts`: Pass `-Pudhaar.tier=blaze`.
4. **Deploy Full Platform**:
   ```bash
   firebase deploy
   ```
5. All existing Spark Mode records and offline Room databases will continue syncing seamlessly without data migration or re-registration.
