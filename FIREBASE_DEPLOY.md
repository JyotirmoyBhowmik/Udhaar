# Udhaar v13.0 — Firebase Hosting, Cloud Functions & App Links Deployment Guide

This guide walks through deploying the static web verification site and privacy policy (`web/index.html`, `web/u/index.html`, `web/privacy.html`), Android Digital Asset Links (`web/.well-known/assetlinks.json`), Firestore security rules (`firestore.rules`), and FCM push notification Cloud Functions (`functions/`).

---

## 1. Single Source of Truth for Static Hosting (`web/`)

All Firebase Hosting assets live exclusively in `/web/`:
- `web/index.html` — Monochrome product landing page (`https://udhaar.jyotirmoyb.com/`)
- `web/u/index.html` — Read-only verification & dispute fallback (`https://udhaar.jyotirmoyb.com/u/<token>` and `/u/p/<token>`)
- `web/privacy.html` — Official Privacy Policy & Cloud Data Deletion instructions (`https://udhaar.jyotirmoyb.com/privacy.html`)
- `web/.well-known/assetlinks.json` — Android Digital Asset Links (`delegate_permission/common.handle_all_urls` and `delegate_permission/common.get_login_creds`) for package `com.jyotirmoyb.udhaar`

---

## 2. Verify Debug & Play App Signing SHA-256 Fingerprints

1. **Debug Keystore Fingerprint:**
   ```bash
   keytool -list -v \
     -keystore app/debug.keystore \
     -alias androiddebugkey \
     -storepass android \
     -keypass android
   ```
2. **Configured SHA-256 Fingerprints in `web/.well-known/assetlinks.json`:**
   - `FB:F9:7F:66:8F:61:1E:9B:49:CB:6D:A9:40:17:E8:1C:79:FE:9F:B3:57:B7:2B:15:DB:6D:D2:D9:35:9C:F0:EA`
   - `6E:C6:02:03:6D:C8:8B:11:A6:E8:6B:19:63:15:28:04:8A:BB:80:15:E8:F2:36:D9:B2:E0:AB:49:55:41:09:33`
   - `D5:83:63:59:01:59:15:B8:F0:A7:A4:4F:C9:F7:3E:DF:3E:8D:92:70:83:6D:78:A7:03:B0:CC:D1:CD:7C:41:E1`
   - `23:BB:AC:CE:CD:64:6D:70:B3:3D:AB:E4:18:C2:5C:DF:1F:9B:72:F1:F9:C7:CA:AC:41:0C:4F:88:EE:0E:62:F5`

---

## 3. Deploy Hosting, Firestore Rules & Cloud Functions

### Option A: Firebase CLI
```bash
# 1. Install Cloud Functions dependencies (Node.js 20)
cd functions && npm install && cd ..

# 2. Authenticate and select the Firebase project
firebase login
firebase use gen-lang-client-0216629575

# 3. Deploy Firestore rules, Hosting (web/), and Cloud Functions (functions/)
firebase deploy --only firestore:rules,hosting,functions
```

### Option B: Direct Firebase Hosting REST API (CI / Cloud Sandbox)
When the environment service account has `roles/firebasehosting.admin` on `gen-lang-client-0216629575`, deploy `/web` directly against `https://firebasehosting.googleapis.com/v1beta1/sites/gen-lang-client-0216629575/versions`.

---

## 4. Custom Domain (`udhaar.jyotirmoyb.com`) & App Links Verification

1. **Firebase Console -> Hosting -> Add custom domain:**
   - Enter `udhaar.jyotirmoyb.com` (leave "Redirect domain name" unchecked unless redirecting `www.udhaar.jyotirmoyb.com`).
2. **Cloudflare DNS (`jyotirmoyb.com`):**
   - Set **Proxy status = DNS only (Grey Cloud)** on both the verification `TXT` record and the `CNAME` (`udhaar` -> `gen-lang-client-0216629575.web.app`) or `A` record so Firebase can provision SSL and Google Play can verify `.well-known/assetlinks.json` without proxy redirects.
3. **Verify HTTP 200 & JSON Headers:**
   ```bash
   curl -I https://udhaar.jyotirmoyb.com/.well-known/assetlinks.json
   curl -I https://udhaar.jyotirmoyb.com/privacy.html
   ```
4. **Verify Android App Links on Device via ADB:**
   ```bash
   adb shell pm verify-app-links --re-verify com.jyotirmoyb.udhaar
   adb shell pm get-app-links com.jyotirmoyb.udhaar
   ```
   Confirm the output shows:
   ```text
   com.jyotirmoyb.udhaar:
     ID: ...
     Signatures: [...]
     Domain verification state:
       udhaar.jyotirmoyb.com: verified
   ```
