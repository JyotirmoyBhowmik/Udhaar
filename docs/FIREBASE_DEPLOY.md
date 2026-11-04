# Firebase Deployment Runbook — Udhaar Production (v17.0)

This document provides complete, production-grade deployment instructions for the **Udhaar** backend infrastructure: Cloud Firestore Security Rules, Cloud Functions (v2), Firebase Hosting, and Security Headers.

---

## 1. Project Topology & Architecture

| Component | Identifier / Detail |
|---|---|
| **Google Cloud Project** | `gen-lang-client-0216629575` |
| **Named Firestore Database** | `ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf` |
| **Cloud Functions Region** | `asia-south1` (Mumbai, India) |
| **Cloud Functions Runtime** | Node.js 20 (Gen 2 Cloud Run backed) |
| **Production Domain** | `https://udhaar.jyotirmoyb.com` |
| **Guest Verification Portal** | `https://udhaar.jyotirmoyb.com/u/` |
| **Digital Asset Links** | `https://udhaar.jyotirmoyb.com/.well-known/assetlinks.json` |

---

## 2. Security Headers & CSP Specifications

All web resources deployed via Firebase Hosting enforce enterprise-grade security headers configured in `firebase.json`.

### 2.1 Security Headers on `/u/**` (Guest Portal)
- **`Strict-Transport-Security`**: `max-age=31536000; includeSubDomains`
  - Enforces HTTPS for a minimum of 1 year and protects against SSL stripping attacks.
- **`Content-Security-Policy`**:
  ```text
  default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob: https:; connect-src 'self' https://*.cloudfunctions.net https://asia-south1-*.cloudfunctions.net https://*.firebaseio.com; frame-ancestors 'none';
  ```
  > [!IMPORTANT]
  > Notice that `'unsafe-inline'` and `'unsafe-eval'` are **strictly forbidden**. All scripts are external (`guest.js`, `qrcode.min.js`), and styles are external (`guest.css`).
- **`X-Frame-Options`**: `DENY`
  - Prevents clickjacking and embedding inside iframe containers.
- **`X-Content-Type-Options`**: `nosniff`
  - Blocks MIME-type sniffing.
- **`Referrer-Policy`**: `no-referrer`
  - Guarantees that sensitive URL fragment tokens (`#<secret>`) are never leaked in referrer headers to external sites.
- **`Permissions-Policy`**: `geolocation=(), microphone=(), camera=()`
  - Restricts browser device hardware access.
- **`Cache-Control`**: `no-store, no-cache, must-revalidate, proxy-revalidate`
  - Ensures transaction amounts and sensitive party details are never stored in intermediate CDN or browser caches.

---

## 3. Pre-Deployment Verification Checklist

Before deploying any changes to production, execute the automated validation suite locally:

```bash
# 1. Validate complete localization (5 regional languages: EN, HI, BN, AS, NE)
# Fails if any missing keys, untranslated strings, banned jargon, or ALL CAPS exist:
npm run check:i18n --prefix functions

# 2. Run Cloud Functions unit tests, security rules tests & Playwright E2E suite:
npm test --prefix functions

# 3. Run Android unit & Robolectric test suite:
.\gradlew testDebugUnitTest
```

All 3 commands must complete with **0 errors and 0 failures**.

---

## 4. Step-by-Step Deployment Procedure

### Step 4.1: Firebase CLI Sign-in & Project Selection
```bash
# Authenticate with your Firebase administrator Google account
firebase login

# Set the active Firebase project
firebase use gen-lang-client-0216629575

# Verify the active project
firebase projects:list
```

### Step 4.2: Deploy Firestore Security Rules
Deploy `firestore.rules` to secure the named database:
```bash
firebase deploy --only firestore:rules
```
*Key Protections Verified by Tests:*
1. Client apps are strictly forbidden from reading or writing `/webLinks/{recordId}` directly.
2. Creators cannot self-confirm their own transactions (`rule_err_creator_cannot_confirm`).
3. Audit logs (`/shared_records/{id}/audit/{eventId}`) are immutable and append-only.
4. Guest payment claims and return date proposals cannot be directly created by untrusted clients.

### Step 4.3: Deploy Cloud Functions (v2)
Install production dependencies and deploy all functions to `asia-south1`:
```bash
# Navigate to functions directory and install dependencies
cd functions
npm install --omit=dev

# Return to root directory
cd ..

# Set the named database environment parameter if not already set
firebase functions:params:set FIRESTORE_DATABASE_ID="ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf"

# Deploy Cloud Functions
firebase deploy --only functions
```

*Endpoints Deployed:*
- `createGuestWebLink` (Callable: Creator generates link + secret hash)
- `revokeGuestWebLink` (Callable: Creator cancels active link)
- `deleteGuestData` (Callable: Creator purges proof attachments)
- `getGuestRecord` (HTTPS POST: Guest fetches masked record details)
- `verifyGuestPhoneOtp` (HTTPS POST: Validates 6-digit WhatsApp code)
- `guestConfirm` (HTTPS POST: Finalizes confirmation and writes audit log)
- `guestDispute` (HTTPS POST: Submits dispute reason code)
- `guestPaymentClaim` (HTTPS POST: Submits proof of payment)
- `guestProposeDate` (HTTPS POST: Proposes new repayment date)
- `guestSetUpi` (HTTPS POST: Guest lender sets payout UPI ID)
- `cleanupExpiredGuestDataScheduled` (PubSub Scheduler: Runs daily at 00:00 IST)

### Step 4.4: Deploy Firebase Hosting
Deploy static HTML, CSS, JavaScript, WebP images, and rewrites:
```bash
firebase deploy --only hosting
```
*Files Deployed:*
- `web/index.html` (Modern, plain-language landing page)
- `web/landing.css`, `web/landing.js` (Multi-lingual landing client)
- `web/privacy.html` (Plain-language privacy policy with data deletion form)
- `web/u/index.html`, `web/u/guest.css`, `web/u/guest.js` (Guest verification flow)
- `web/assets/` (WebP images, `qrcode.min.js`, icons)
- `web/sitemap.xml`, `web/robots.txt`, `web/404.html`
- `web/.well-known/assetlinks.json` (Android App Links association)

### Step 4.5: Full Synchronized Deployment
To deploy all components at once:
```bash
firebase deploy --only functions,hosting,firestore:rules
```

---

## 5. Post-Deployment Verification & Testing

### 5.1 Verification Checklist
1. **Security Headers Verification:**
   ```bash
   curl -I https://udhaar.jyotirmoyb.com/u/
   ```
   Confirm presence of:
   - `strict-transport-security: max-age=31536000; includeSubDomains`
   - `content-security-policy` without `'unsafe-inline'`
   - `x-frame-options: DENY`
   - `x-content-type-options: nosniff`
   - `referrer-policy: no-referrer`

2. **Digital Asset Links Verification:**
   ```bash
   curl -s https://udhaar.jyotirmoyb.com/.well-known/assetlinks.json
   ```
   Verify JSON matches the SHA-256 certificate fingerprint of the production release key.

3. **Cloud Functions Health Check:**
   Open the Google Cloud Console / Firebase Console &rarr; Functions &rarr; Logs. Verify zero runtime startup crashes or missing secret parameters.

4. **Interactive Guest Flow Test:**
   Navigate to `https://udhaar.jyotirmoyb.com/u/?demo=true` in Google Chrome and verify:
   - Screen 1 displays amount in rupees (`₹15,000`).
   - Language selector switches seamlessly across English, Hindi, Bengali, Assamese, and Nepali.
   - Confirmation leads to Screen 2 and Screen 3.
   - UPI QR code renders cleanly.

---

## 6. Disaster Recovery & Rollback

### Rollback Hosting
```bash
firebase hosting:rollback
```

### Rollback Cloud Functions
If a regression occurs in a Cloud Function:
1. Revert git commit: `git revert HEAD`
2. Run tests: `npm test --prefix functions`
3. Redeploy: `firebase deploy --only functions`
