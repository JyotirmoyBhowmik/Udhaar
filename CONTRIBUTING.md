# Contributing to Udhaar

Thank you for your interest in contributing to **Udhaar**! This guide explains how to set up your development environment, run the test suites, and submit pull requests that meet our security, accessibility, and offline-first standards.

---

## 1. Prerequisites

- **JDK**: Java 17 or newer (Android Gradle Plugin compiles with Java 11 source/target compatibility).
- **Android SDK**: API Level 36 (`compileSdk 36`, `targetSdk 36`, `minSdk 24`).
- **Node.js**: Node.js 22+ and `npm` (for building the web assets, bundling the local Firebase SDK, and running Firestore rules & Playwright tests).
- **Firebase CLI** *(optional, for local emulators)*: `npm install -g firebase-tools` (requires Java 17+ for the Firestore emulator).

---

## 2. Repository Structure

| Path | Description |
| :--- | :--- |
| `app/` | Native Android application (Kotlin, Jetpack Compose, Room + SQLCipher v13, WorkManager, Firebase Auth & Firestore). |
| `web/` | Static landing page (`index.html`), 12-screen user guide (`guide/`), privacy policy (`privacy.html`), terms of service (`terms.html`), and guest verification portal (`u/`). |
| `webapp/` | Authenticated Web Ledger (`webapp/src/`), bundled into `web/ledger/` via `scripts/build-webapp.js`. |
| `firestore.rules` | Zero-trust Firestore security rules for Spark Mode (`/users/{uid}/**` and `/guestLinks/{linkId}/**`). |
| `firestore.indexes.json` | Composite and collection-group indexes (including `actions` collection-group index on `ownerUid` + `createdAt`). |
| `functions/` | Automated Node.js test suites (`functions/test/`) and optional Blaze-tier backend modules. |
| `scripts/` | Build, asset budget, CSP verification, and launch-checklist verification scripts. |

---

## 3. Core Engineering & Design Invariants

Every pull request must uphold these project rules:

1. **Offline-First & Spark Mode Default**:
   - The app must work 100% offline without an account.
   - `BuildConfig.UDHAAR_TIER` defaults to `"spark"` and `BuildConfig.CLOUD_FEATURES` defaults to `false` in `app/build.gradle.kts`.
   - Never make `/api/v1` or Cloud Functions calls in Spark Mode.
   - Cloud sync must remain strictly disabled until the user's email address is verified (`isEmailVerified == true`).
2. **Exact Integer Paise Math**:
   - All monetary values in Kotlin and JS must be stored and calculated in integer paise (`Long`, where `₹1.00 = 100L paise`). Never use floating-point types for ledger balances.
3. **Accessibility & Adaptive UI**:
   - Every interactive touch target (`Button`, `OutlinedButton`, `TextButton`, `IconButton`, `FilterChip`, `Checkbox`) must be at least **48 dp × 48 dp** (`Modifier.heightIn(min = 48.dp)` or `Modifier.minTouchTarget()`).
   - Never use fixed `.height(48.dp)` on text buttons if it prevents text from growing at `fontScale = 2.0f`; use `.heightIn(min = 48.dp)`.
   - Maintain adaptive two-pane list-detail layouts at `>= 600.dp`, centered `560.dp` max width on forms, and full scrollability in landscape.
4. **No Hardcoded UI Strings in Compose**:
   - All `Text(...)` composables under `app/src/main/java/com/jyotirmoyb/udhaar/ui/` must reference `stringResource(R.string....)` in `res/values/strings.xml`. `ExampleRobolectricTest` enforces a zero-hardcoded-string-literal check.
   - Keep `MainActivity.kt`, `DashboardScreen.kt`, `PeopleDirectoryScreen.kt`, and `AddEditUdhaarScreen.kt` strictly under **600 lines** each.
5. **Strict Web CSP & Zero Inline Code**:
   - HTML files in `web/` and `webapp/` must contain **zero** inline `<style>` blocks and **zero** inline `<script>` blocks (except `<script type="application/ld+json">`).
   - Do not load third-party scripts from external CDNs; bundle dependencies locally via `scripts/build-webapp.js`.

---

## 4. Building & Running Tests Locally

### Web Build & Asset / CSP Audit
```bash
npm install
npm run build
```

### Firestore Rules & Web Client Tests
```bash
# Run unit & static rules / web client verification:
node --test functions/test/firestore_rules.test.js functions/test/web_client.test.js

# Run against live Firebase Auth & Firestore Emulators:
firebase emulators:exec --only auth,firestore --project demo-udhaar \
  "node --test functions/test/firestore_rules.test.js functions/test/playwright_spark_emulator.test.js"
```

### Android Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Launch Checklist Proof Verification
```bash
node scripts/verify-launch-checklist.js
```

---

## 5. Submitting a Pull Request

1. Create a feature or fix branch from `main` (e.g., `feat/short-description` or `fix/issue-description`).
2. Add or update unit/Robolectric/rules tests covering your changes.
3. Verify that `npm run build`, `node scripts/verify-launch-checklist.js`, `node --test functions/test/firestore_rules.test.js`, and `gradle :app:testDebugUnitTest` pass cleanly.
4. Open a Pull Request describing:
   - What problem is solved and why.
   - Screenshots for any Android Compose or Web UI changes (including tablet `>= 600.dp` or `fontScale = 2.0` if applicable).
   - Confirmation that Spark Mode constraints and 48 dp touch targets are preserved.

---

## 6. Questions & Support

- **General & Developer Support**: [admin@jyotirmoyb.com](mailto:admin@jyotirmoyb.com)
- **Security Reports**: See [SECURITY.md](./SECURITY.md)
