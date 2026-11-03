# Google Drive AppData Backup Setup Guide — Udhaar (v13)

This guide documents the complete step-by-step procedure to enable and verify Google Drive AppData backups (`https://www.googleapis.com/auth/drive.appdata`) for **Udhaar** (`com.jyotirmoyb.udhaar`).

---

## 1. Prerequisites & Architectural Overview

Udhaar uses the **Google Drive AppData Folder** (`appDataFolder`).
- **Zero Developer Access:** Backup files are encrypted client-side with **AES-256-GCM** (PBKDF2-HMAC-SHA256, 120,000 iterations) and stored in the user's private Google Drive application storage space.
- **Hidden from General Drive UI:** The user cannot accidentally delete or modify the backup files from their root Google Drive folder; they can manage or delete them from Google Drive Web Settings > Manage Apps > Udhaar or via the in-app **"Delete my Drive backups"** button.
- **Standalone Google Sign-In & Authorization:** Authorization is handled directly via Google Play Services `Identity.getAuthorizationClient(context).authorize(authorizationRequest)`. **`google-services.json` is not required for Google Drive backup.**

---

## 2. Reading Your App Signing Fingerprints from the App

Udhaar v13 features an integrated certificate reader in **Settings > Backup**:
1. Open Udhaar on your Android device.
2. Tap the **Settings (Gear)** icon in the top right.
3. Scroll to **Backup & Sync**.
4. Under **App Signing Credentials**, locate:
   - **Package Name:** `com.jyotirmoyb.udhaar` (with Copy button)
   - **SHA-1 Certificate Fingerprint:** (with Copy button)
   - **SHA-256 Certificate Fingerprint:** (with Copy button)
5. Tap the Copy button next to the **SHA-1** fingerprint to paste it directly into Google Cloud Console or Firebase.

> **Tip:** You can also run the **"Run connection test"** button at any time. If authorization fails, it diagnoses the root cause and tells you whether the SHA-1 is missing, whether the Drive API is disabled, or whether your Gmail account needs to be added as a test user.

---

## 3. Google Cloud Console Setup

Project: `gen-lang-client-0216629575` (or your corresponding Google Cloud project).

### Step 3.1: Enable the Google Drive API
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Select your project: **`gen-lang-client-0216629575`**.
3. Navigate to **APIs & Services > Library**.
4. Search for **Google Drive API**.
5. Click **Google Drive API** and click **Enable**.
   *(If this is disabled, the connection test will report `SERVICE_DISABLED` or HTTP 403 `accessNotConfigured`).*

---

### Step 3.2: Configure the OAuth Consent Screen
1. Navigate to **APIs & Services > OAuth consent screen**.
2. Select **User Type**: **External** and click **Create**.
3. **App Information**:
   - **App name:** `Udhaar`
   - **User support email:** `admin@jyotirmoyb.com` (or your developer email)
   - **App logo:** (Optional, upload the Udhaar logo)
4. **App Domain**:
   - **Application home page:** `https://udhaar.jyotirmoyb.com/`
   - **Application privacy policy link:** `https://udhaar.jyotirmoyb.com/privacy.html`
   - **Application terms of service link:** `https://udhaar.jyotirmoyb.com/`
   - **Authorized domains:** `jyotirmoyb.com`
5. **Developer contact information**:
   - Email: `admin@jyotirmoyb.com`
6. Click **Save and Continue**.

---

### Step 3.3: Add the `drive.appdata` Scope
1. On the **Scopes** page, click **Add or Remove Scopes**.
2. Filter or search for `drive.appdata` or enter manually:
   - `https://www.googleapis.com/auth/drive.appdata` (See, edit, create, and delete only the specific Google Drive data that you use with this app)
3. Check the checkbox next to `.../auth/drive.appdata`.
4. Click **Update** and then **Save and Continue**.

---

### Step 3.4: Add Test Users (Required while in "Testing" mode)
1. On the **Test users** page, click **+ Add Users**.
2. Enter the Gmail account(s) you use on your physical test devices or emulator (e.g. `yourname@gmail.com`).
3. Click **Add** and **Save and Continue**.
   *(Note: If a user tries to sign in while the app is in Testing mode and their account is not in this list, Google will block sign-in with `access_denied` or "Access blocked: App has not completed the Google verification process".)*
4. When ready for all public users, click **Publish App** on the OAuth consent screen to move from Testing to Production.

---

### Step 3.5: Create Android OAuth Client IDs for Every Signing Key

Android OAuth authentication strictly verifies both the **Package Name** (`com.jyotirmoyb.udhaar`) and the **SHA-1 fingerprint** of the signing key. Because the signing key changes across development, build machines, and Google Play, you should create an OAuth client for **each** key:

1. Navigate to **APIs & Services > Credentials**.
2. Click **+ Create Credentials > OAuth client ID**.
3. Select **Application type:** **Android**.
4. Set **Name:** `Udhaar Android Debug Key`.
5. Enter **Package name:** `com.jyotirmoyb.udhaar`.
6. Enter **SHA-1 certificate fingerprint:**
   - Copy the SHA-1 from **Settings > Backup > App Signing Credentials** on your debug build, or generate it via:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
7. Click **Create**.

#### Additional OAuth Clients Needed for Production & Play Store:
- **Upload Keystore:** If building signed release APKs/AABs locally, extract SHA-1 from your release keystore and add an OAuth client named `Udhaar Android Upload Key`.
- **Google Play App Signing (CRITICAL):**
  1. Open [Google Play Console](https://play.google.com/console).
  2. Select **Udhaar (`com.jyotirmoyb.udhaar`)**.
  3. Navigate to **Setup > App Integrity > App signing key certificate**.
  4. Copy the **SHA-1 certificate fingerprint**.
  5. In Google Cloud Console, click **+ Create Credentials > OAuth client ID > Android**.
  6. Name: `Udhaar Google Play App Signing`.
  7. Package name: `com.jyotirmoyb.udhaar`.
  8. SHA-1: Paste the Play Store app signing SHA-1.
  9. Click **Create**.

---

## 4. Firebase Project Settings Sync (Optional, for Verification & Chat)

While Google Drive backup does not depend on Firebase, counterparty verification and mutual ledger chat do:
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select project **`gen-lang-client-0216629575`**.
3. Click the **Gear icon > Project settings**.
4. Under **Your apps > com.jyotirmoyb.udhaar**:
5. Click **Add fingerprint**.
6. Paste the **SHA-1** and **SHA-256** fingerprints obtained from **Settings > Backup > App Signing Credentials**.
7. Download the updated `google-services.json` and place it in the `app/` folder (or deploy via Firebase CLI).

---

## 5. Verifying the Connection in the App

1. Open Udhaar &rarr; **Settings** &rarr; **Backup & Sync**.
2. Under **Google Drive Cloud Backup**, tap **Sign in with Google Drive** (or **Reconnect**).
3. Choose your Google account from the system dialog and accept the permissions.
4. Tap **Run connection test**:
   - Step 1: Checks Google Play Services availability.
   - Step 2: Requests authorization token for `drive.appdata` scope.
   - Step 3: Tests Drive REST API by querying `appDataFolder`.
5. If successful: You will see a green **"Connection Test Passed"** banner with details: `Token acquired, Drive API queried successfully`.
6. If an error occurs, the test displays a clear plain-language fix:
   - **Code 10 / 12500:** *"Package name or SHA-1 not registered. Add the SHA-1 shown above to an Android OAuth client for com.jyotirmoyb.udhaar."*
   - **access_denied:** *"Add your Gmail as a test user, or publish the consent screen."*
   - **HTTP 403 accessNotConfigured:** *"Enable the Google Drive API in the Google Cloud project."*
   - **HTTP 401:** *"Reconnect Google Drive."*
   - **No Network:** *"Unable to connect to Google servers. Please check your internet connection and try again."*
   - Tap **"Show technical details"** to view the underlying HTTP code and raw message.

---

## 6. How Backups and Deletions Work

- **Manual Backup:** Tap **Back up now** &rarr; Enter your passphrase (at least 4 characters) &rarr; Tap **Encrypt & Upload**.
- **Automated Backup:** WorkManager triggers periodic encrypted backups based on your selected frequency (Daily, Weekly, Monthly) and Wi-Fi preference.
- **Restore:** Tap **Restore** on any backup in the list &rarr; Enter passphrase &rarr; Preview transactions &rarr; Confirm Restore.
- **Delete Single Backup:** Tap **Delete** next to any remote backup.
- **Delete All Drive Backups:** Tap **Delete my Drive backups** &rarr; Confirm **Delete Permanently**.
- **Web Deletion:** Users can also delete all hidden app data at any time via [drive.google.com](https://drive.google.com) &rarr; Settings &rarr; Manage Apps &rarr; Udhaar &rarr; Delete hidden app data.
