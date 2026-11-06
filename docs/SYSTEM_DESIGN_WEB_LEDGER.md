# Udhaar Web Platform & Full-System Architecture Design

This document details the production architecture, cloud ledger synchronization, new user onboarding with email OTP, WhatsApp-based capability verification, and root-cause resolution for Firebase Hosting warnings and amount confirmation anomalies.

---

## 1. Firebase Deploy Warnings: Root-Cause & Resolution

### The Error
```
⚠ hosting[gen-lang-client-0216629575]: Unable to find a valid endpoint for function getGuestRecord, but still including it in the config
⚠ hosting[gen-lang-client-0216629575]: Unable to find a valid endpoint for function guestConfirm, but still including it in the config
...
```

### Why It Happened
1. **Isolated Hosting Deployment**: Running `firebase deploy --only hosting` deploys only static files in `web/`. However, `firebase.json` defines rewrites mapping `/api/getGuestRecord`, `/api/guestConfirm`, etc., to Cloud Functions located in region `asia-south1`. Firebase Hosting verifies whether those functions exist in that project and region. Because Cloud Functions were not deployed to `gen-lang-client-0216629575`, Hosting issued warnings that endpoints were missing.
2. **Default Region Mismatch in Cloud Functions v2**: In Cloud Functions v2, omitting `region: "asia-south1"` or `setGlobalOptions({ region: "asia-south1" })` causes functions to deploy to `us-central1` by default. When Hosting looks for functions in `asia-south1`, it cannot find them.
3. **Firestore Named Database Disconnect**: `functions/guestWebFlowEndpoints.js` contained 18 calls to `admin.firestore()`. In Firebase projects utilizing a named Firestore database (`ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf`), `admin.firestore()` targets `(default)` where no Udhaar records reside.

### The Fix
1. Configured `setGlobalOptions({ region: "asia-south1", maxInstances: 10 })` in `functions/index.js` and explicit region on all HTTP handlers.
2. Implemented `getFirestoreDb()` in `functions/guestWebFlowEndpoints.js` targeting `ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf`.
3. Standardized the deployment command:
   ```bash
   firebase deploy --only functions,hosting,firestore:rules --project gen-lang-client-0216629575
   ```

---

## 2. Cloud Ledger Persistence & Synchronization Architecture

### Dual-Layer Storage Topology

```
┌──────────────────────────────────────────────┐
│             Android Device (Kotlin)          │
│  - 100% Offline-First Room SQLite Database   │
│  - Local calculations (Interest, Balances)   │
│  - Offline capability token generator        │
└──────────────────────┬───────────────────────┘
                       │ Bi-directional Cloud Sync
                       ▼
┌──────────────────────────────────────────────┐
│           Cloud Firestore (Named DB)         │
│  1. /users/{uid}/ledger/{recordId}           │
│     -> Isolated private ledger copy          │
│  2. /shared_records/{recordId}               │
│     -> Two-party shared ledger entity        │
│  3. /shared_records/{recordId}/audit_trail/  │
│     -> Tamper-evident SHA-256 audit log      │
│  4. /user_profiles/{uid}                     │
│     -> Verified phone & email metadata       │
└──────────────────────▲───────────────────────┘
                       │ HTTPS REST API (/api/*)
                       ▼
┌──────────────────────────────────────────────┐
│          Web Ledger Portal (/ledger/)        │
│  - ServiceWorker cache (Shell only)          │
│  - localStorage persistence for offline use  │
│  - Cloud-synchronized when authenticated     │
└──────────────────────────────────────────────┘
```

1. **On Android**:
   - The app stores all records in Room DB (`app/src/main/java/com/jyotirmoyb/udhaar/data/local/`).
   - The app operates fully without network access.
   - When online, records synchronize with Firestore.
2. **In Cloud Firestore**:
   - `/users/{uid}/ledger/{recordId}`: Stores user-scoped transaction entries.
   - `/shared_records/{recordId}`: Stores two-party shared transaction status, readable/writable only by participants.
   - `/webLinks/{recordId}`: Stores capability token hashes and single-use WhatsApp OTP HMACs.
3. **On the Web**:
   - Web users authenticate via email OTP.
   - The web app persists records in `localStorage` for instantaneous offline access.
   - Background synchronization syncs pending records via `/api/getUserLedger` and `/api/syncWebRecord`.

---

## 3. Amount Showing 0 & Confirmation Anomaly: Root Cause & 4-Tier Solution

### Root Cause
1. When Cloud Functions were not deployed, requests to `/api/getGuestRecord` failed (404/503).
2. The error handler fell back to parsing query parameters (`?amt=`).
3. Older capability links lacked URL query parameters (`https://domain/u/r/<id>#<secret>`), defaulting `qsAmt` to 0.
4. The fallback rendered "Shared on WhatsApp", and clicking "Yes, this is correct" confirmed the record with ₹0.

### 4-Tier Solution
1. **Android Link Generation (`GuestWebCloudRepository.kt`)**:
   - Appends explicit parameters to the base URL before the `#` fragment:
     `https://udhaar.jyotirmoyb.com/u/r/<id>?amt=5000&from=Ravi&to=Amit&cur=INR#<token>`
   - Guarantees immediate fallback amount access even if fragments are stripped or functions are offline.
2. **Token Fragment Parsing (`web/u/guest.js`)**:
   - Prioritizes `params.embeddedRecord` decoded from the self-contained `#v2|...` fragment for immediate render.
3. **Client-Side Manual Amount Safeguard**:
   - If an amount is missing or $\le 0$, the UI displays an explicit amount input box (`#manual-amount-container`) and disables confirmation until a valid positive amount is entered.
4. **Server-Side Enforcement (`functions/guestWebFlowEndpoints.js`)**:
   - `guestConfirm` validates `finalPaise > 0`. If a record has 0 paise, it accepts `confirmedAmountPaise` from the payload; if both are 0, it rejects with `400 AMOUNT_REQUIRED`.

---

## 4. New User Onboarding & Email Verification Architecture

```
User enters Mobile + Email
           │
           ▼
POST /api/sendEmailOtp
   ├── Validates email format & mobile number (>= 10 digits)
   ├── Enforces sliding-window rate limit (max 3 per 15 min)
   ├── Generates cryptographically secure 6-digit OTP
   ├── Computes HMAC-SHA256 with UDHAAR_SERVER_SECRET
   └── Saves to email_verifications/{emailHash} (10-min TTL)
           │
           ▼
User enters 6-Digit Email OTP
           │
           ▼
POST /api/verifyEmailOtp
   ├── Loads verification doc, verifies TTL and attempt count
   ├── Validates OTP via crypto.timingSafeEqual
   ├── Deletes verification document
   ├── Creates/updates user_profiles/{uid} (emailVerified: true)
   ├── Issues 30-day session token stored in web_sessions/{sessionHash}
   └── Returns sessionToken & user profile with "✓ Verified" status
```

---

## 5. WhatsApp-Based 6-Digit Verification Code Flow

1. When a transaction is recorded (via Android or Web):
   - Server or client generates a random 6-digit OTP (e.g. `482910`).
   - Generates capability secret token and stores HMAC in `webLinks/{recordId}`.
2. Deep Link Generation:
   - Formats WhatsApp message:
     ```
     Hi Ravi, I've recorded ₹5,000 (lent you) on Udhaar.
     Please review and confirm our record here: https://.../u/r/tx_123#<token>

     Your 6-digit WhatsApp verification code is: 482910
     ```
   - Opens `https://wa.me/<phone>?text=...` directly on Android or Web.
3. Guest Verification:
   - Guest opens link, enters the 6-digit WhatsApp code.
   - Code is verified via HMAC against Firestore.
   - Record status transitions to `CONFIRMED`.

---

## 6. Web Ledger Portal Specifications

| Feature | Specification |
|---|---|
| **Location** | `/ledger/` (`web/ledger/index.html`, `ledger.css`, `ledger.js`) |
| **Offline Support** | ServiceWorker static caching + `localStorage` persistence |
| **KPIs** | Total Lent (₹), Total Borrowed (₹), Net Balance, Pending Verification |
| **Languages** | 5 Languages (English, Hindi, Bengali, Assamese, Nepali) |
| **Design Tokens** | Emerald color palette matching Android app (`/design-tokens.css`) |
| **Security** | Strict CSP (Zero inline scripts, zero inline styles) |
| **Accessibility** | WCAG 2.1 AA compliant, 4.5:1 contrast, keyboard nav, focus trap |
