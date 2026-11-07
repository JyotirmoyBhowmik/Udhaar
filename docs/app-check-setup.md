# Udhaar Spark Mode — App Check Configuration Guide (Zero Cost)

## 1. Overview
Firebase App Check protects Udhaar's Cloud Firestore database from abuse, scraper bots, and unauthorized API calls by attesting that incoming requests originate from the authentic Udhaar Android app and official web app.

In Spark Mode, App Check operates **100% free** using Google's free-tier attestation providers:
- **Android App**: **Google Play Integrity API** (Free tier: 10,000 verifications / day).
- **Web App**: **Google reCAPTCHA v3** (Free tier: 1,000,000 assessments / month).

---

## 2. Step-by-Step Firebase Console Setup

### A. Android App Registration (Play Integrity)
1. Navigate to [Firebase Console](https://console.firebase.google.com/) > Select your Udhaar project.
2. In the left navigation, open **Build** > **App Check** > **Apps** tab.
3. Locate the Android app (`com.jyotirmoyb.udhaar`) and click **Register**.
4. Select **Play Integrity** as the attestation provider.
5. In **Google Play Console** > **Release** > **App Integrity**:
   - Link your Google Cloud / Firebase project to your Play Console application.
6. In Firebase Console, specify **Token Time to Live (TTL)**: `1 hour` (recommended default).
7. Click **Save**.

### B. Web App Registration (reCAPTCHA v3)
1. Open the [Google reCAPTCHA Admin Console](https://www.google.com/recaptcha/admin).
2. Create a new reCAPTCHA v3 key:
   - Label: `Udhaar Web (Spark Mode)`
   - reCAPTCHA type: `Score based (v3)`
   - Domains: Add `udhaar.jyotirmoyb.com`, `jyotirmoyb.com`, and `localhost` (for development).
3. Copy the generated **Site Key** and **Secret Key**.
4. In [Firebase Console](https://console.firebase.google.com/) > **App Check** > **Apps** tab:
   - Locate the Web app.
   - Select **reCAPTCHA v3**.
   - Paste the **reCAPTCHA Secret Key**.
   - Set Token TTL: `1 hour`.
5. Click **Save**.

---

## 3. Web Client Activation Code

In `webapp/src/api/appCheck.js`:
```javascript
import { initializeAppCheck, ReCaptchaV3Provider } from "firebase/app-check";

export function initAppCheck(firebaseApp, siteKey) {
  if (typeof window === "undefined") return null;
  
  return initializeAppCheck(firebaseApp, {
    provider: new ReCaptchaV3Provider(siteKey),
    isTokenAutoRefreshEnabled: true
  });
}
```

---

## 4. Enforcement Strategy

1. **Phase 1: Monitoring (First 7 Days)**
   - Keep enforcement set to **Unenforced**.
   - Check the App Check metrics graph in Firebase Console to verify that legitimate requests receive valid attestation tokens (verdict > 99%).
2. **Phase 2: Enforce**
   - In App Check > **APIs** tab > **Cloud Firestore** > Click **Enforce**.
   - All incoming direct Firestore requests from non-attested clients will be rejected at the Google edge before consuming read/write quota.

---

## 5. Local Development & Testing (Debug Provider)

During local development and CI testing, real Play Integrity and reCAPTCHA assessments are unavailable. Use the **Firebase App Check Debug Provider**:
- **Web App**: Set `self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;` before initializing App Check, or pass a debug token registered in Firebase Console > App Check > Apps > Manage debug tokens.
- **Android App**: Use `DebugAppCheckProviderFactory.getInstance()` in `debug` build variants. Register the generated UUID debug token in Firebase Console.
