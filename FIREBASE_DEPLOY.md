# Udhaar v21.0 — Firebase Hosting, Cloud Functions & App Links Deployment Guide

This guide walks through deploying the static web platform (`web/index.html`, `web/u/index.html`, `web/privacy.html`, `web/calculator.js`, `web/site-config.js`), Android Digital Asset Links (`web/.well-known/assetlinks.json`), Firestore security rules (`firestore.rules`), and Cloud Functions (v2) (`functions/`).

---

## 1. Single Source of Truth for Static Hosting (`web/`)

All Firebase Hosting assets live exclusively in `/web/`:
- `web/index.html` — Emerald product landing page with interactive 4-step guest flow demo, integer-paise repayment calculator, animated stepper, and language selector (`https://udhaar.jyotirmoyb.com/`)
- `web/site-config.js` — Single source of truth for Google Play Store public availability (`PLAY_STORE_LIVE: false`), routing visitors to early access / waitlist testing until public launch
- `web/calculator.js` — Client-side integer-paise repayment calculator with remainder absorption on the final installment, mirroring the Android app
- `web/u/index.html` — Guest verification, dispute, and payment claim flow (`https://udhaar.jyotirmoyb.com/u/<token>`)
- `web/privacy.html` — Plain-language Privacy Policy, retention schedule, and instant data deletion request form (`https://udhaar.jyotirmoyb.com/privacy.html`)
- `web/.well-known/assetlinks.json` — Android Digital Asset Links (`delegate_permission/common.handle_all_urls`) for package `com.jyotirmoyb.udhaar`
- `web/design-tokens.css` — Unified CSS design system tokens (Emerald `#064e3b`, `#10b981`, neutral dark/light themes, typography)
- `web/manifest.json` & `web/sw.js` — Web App Manifest and offline static shell service worker

---

## 2. Pre-Deployment Automated Verification Checklist

Before deploying any changes to production, execute the automated validation pipeline:

```bash
# 1. Validate complete localization (5 regional languages: EN, HI, BN, AS, NE):
npm run check:i18n

# 2. Build production assets, check bundle budgets, and verify zero inline CSP:
npm run build

# 3. Optimize images into responsive WebP & AVIF variants:
npm run optimize:images

# 4. Run real-browser Playwright E2E suite across Chromium, Firefox & WebKit:
npm run test:e2e

# 5. Run full Cloud Functions unit tests and security rules tests:
npm test

# 6. Run Android unit & Robolectric test suite:
.\gradlew.bat testDebugUnitTest
```

All commands must complete with **0 errors and 0 failures**.

---

## 3. Verify Debug & Play App Signing SHA-256 Fingerprints

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

## 4. Deploy Hosting, Firestore Rules & Cloud Functions

### Deploy Web Hosting Only
```bash
firebase deploy --only hosting
```

### Full Combined Production Deployment
```bash
firebase deploy --only firestore:rules,hosting,functions
```

---

## 5. Custom Domain (`udhaar.jyotirmoyb.com`) & App Links Verification

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
