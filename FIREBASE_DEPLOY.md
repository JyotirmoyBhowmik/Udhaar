# Udhaar v9.0 — Firebase Hosting, Cloud Functions & App Links Deployment Guide

This guide walks through deploying the static web verification fallback (`web/`), Android Digital Asset Links (`web/.well-known/assetlinks.json`), Firestore security rules (`firestore.rules`), and FCM push notification Cloud Functions (`functions/`).

---

## 1. Get Your Debug & Play App Signing SHA-256 Fingerprints

1. **Debug Keystore Fingerprint** (already included in `web/.well-known/assetlinks.json`):
   ```bash
   keytool -list -v \
     -keystore app/debug.keystore \
     -alias androiddebugkey \
     -storepass android \
     -keypass android
   ```
   - **SHA-1:** `FD:AD:95:C3:89:A1:C2:2A:28:18:C8:58:C9:65:C8:30:C7:57:04:AC`
   - **SHA-256:** `FB:F9:7F:66:8F:61:1E:9B:49:CB:6D:A9:40:17:E8:1C:79:FE:9F:B3:57:B7:2B:15:DB:6D:D2:D9:35:9C:F0:EA`

2. **Google Play App Signing SHA-256 Fingerprint (Production):**
   - Open **Google Play Console -> Udhaar (`com.jyotirmoyb.udhaar`) -> Setup -> App signing**.
   - Under **App signing key certificate**, copy the **SHA-256 certificate fingerprint**.
   - Open `web/.well-known/assetlinks.json` and replace `REPLACE_WITH_YOUR_RELEASE_UPLOAD_OR_PLAY_APP_SIGNING_SHA256_FINGERPRINT` with that SHA-256 fingerprint.
   - Also add both the **SHA-1** and **SHA-256** fingerprints in **Firebase Console -> Project Settings -> Your apps -> Android (`com.jyotirmoyb.udhaar`)**, then download the refreshed `app/google-services.json`.

---

## 2. Configure the Firestore Database ID Parameter for Cloud Functions

`functions/index.js` reads the named Firestore database ID from the `FIRESTORE_DATABASE_ID` environment parameter (`defineString("FIRESTORE_DATABASE_ID", ...)`), defaulting to:

```text
ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf
```

To override it for another environment without changing `functions/index.js`, create `functions/.env`:

```env
FIRESTORE_DATABASE_ID=ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf
```

---

## 3. Install Dependencies & Deploy Hosting, Rules, and Cloud Functions

```bash
# 1. Install Cloud Functions dependencies (Node.js 20)
cd functions && npm install && cd ..

# 2. Authenticate and select your Firebase project
firebase login
firebase use gen-lang-client-0216629575

# 3. Deploy Firestore rules, Hosting (web/), and Cloud Functions (functions/)
firebase deploy --only firestore:rules,hosting,functions
```

---

## 4. Connect Custom Domain (`jyotirmoyb.com`) & Verify App Links

1. In **Firebase Console -> Hosting -> Add custom domain**, connect `jyotirmoyb.com` (or serve `web/.well-known/assetlinks.json` and `web/u/index.html` directly on `https://jyotirmoyb.com`).
2. Verify that Digital Asset Links is publicly reachable with `Content-Type: application/json` and HTTP 200 (no redirects):
   ```bash
   curl -i https://jyotirmoyb.com/.well-known/assetlinks.json
   ```
3. On an Android device with `com.jyotirmoyb.udhaar` installed, trigger and inspect App Links verification:
   ```bash
   adb shell pm verify-app-links --re-verify com.jyotirmoyb.udhaar
   adb shell pm get-app-links com.jyotirmoyb.udhaar
   ```
   Confirm `jyotirmoyb.com: verified` appears under Domain verification state.
