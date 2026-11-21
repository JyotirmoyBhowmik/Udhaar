# Udhaar — Pre-Release Manual QA Test Plan

**Application:** Udhaar (`com.jyotirmoyb.udhaar`)  
**Target Platform:** Android (API 24 to 36) & Web Guest Confirmation Portal (`/u/`)  
**Runtime Architecture:** Firebase Spark (Free Tier, Zero Cloud Costs), Room SQLite + SQLCipher (Offline-First)  
**Execution Scope:** Real Physical Devices (Device 1: Lender, Device 2: Borrower/Guest)  

---

## Document Overview & Quality Gates

This comprehensive test plan defines all manual test scenarios required before tagging and promoting a production release to Google Play. Every test case must be executed against a signed release build (R8 minification enabled) or verified on real physical hardware.

### Testing Legend
- **ID**: Unique test identifier by functional area.
- **Priority**: 
  - `P0` (Blocker: Any failure blocks release promotion)
  - `P1` (Critical: Major regression, release held unless workaround exists)
  - `P2` (High: Minor defect, non-blocking for canary)
- **Status Column**: `Pass` | `Fail` | `Blocked` | `Skipped`

---

## 1. Installation, Onboarding, Secure Storage & App Lock

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-ONB-01** | `P0` | Fresh Clean Installation | Device has no prior Udhaar data | 1. Install release APK via `adb install` or Play Internal Track.<br>2. Launch the app from the application drawer. | App cold-starts under 800ms without ANR or crash. Brand splash screen smoothly transitions to Welcome/Onboarding screen. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-02** | `P1` | 5-Language Onboarding Localization | Fresh install | 1. On language selector screen, switch between: English, Hindi, Bengali, Assamese, Nepali.<br>2. Advance through onboarding carousel slides. | All titles, descriptions, and CTA buttons translate accurately without string truncation, ellipsis clipping, or missing placeholder tokens. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-03** | `P0` | SQLCipher Hardware Key Fail-Closed Protection | Fresh install, Device Keystore active | 1. Complete onboarding.<br>2. Inspect app private files via root/shell (`/data/data/com.jyotirmoyb.udhaar/databases/`).<br>3. Attempt reading raw database with standard sqlite3 CLI: `sqlite3 udhaar.db "SELECT * FROM transactions;"`. | MasterKey generated in Android Keystore. SQLCipher 256-bit AES encryption active. Raw sqlite3 query fails with `file is not a database` or `database disk image is malformed`. Zero plaintext fallback file is created. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-04** | `P0` | Firebase Spark Google Sign-In | Play Services available, Google account on device | 1. Tap "Sign in with Google".<br>2. Choose a valid Google account from system dialog.<br>3. Observe auth completion. | Authenticates successfully via Google ID token. User session persists securely in `EncryptedSharedPreferences`. Account email and display name appear in Settings without network exceptions. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-05** | `P1` | Email / Password Sign-In & Verification | Unauthenticated | 1. Enter email and password.<br>2. Tap Register / Sign In.<br>3. Verify email sent. | Email verification link received. Cloud sync features remain gated until email is verified, honoring Spark data protection invariants. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-06** | `P0` | PBKDF2 PIN Security Setup & Throttling | App logged in | 1. Open Settings > Security > Enable PIN Lock.<br>2. Enter a 4-digit PIN (e.g. `7429`). Confirm PIN.<br>3. Send app to background for > 30 seconds.<br>4. Re-open app.<br>5. Enter wrong PIN 5 times consecutively. | PIN is salted and hashed with PBKDF2 (never stored plaintext). On relaunch, full-screen PIN challenge blocks dashboard access. After 5 incorrect attempts, an exponential lockout delay timer (30s) prevents brute-forcing. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-07** | `P0` | Biometric Authentication Prompt | Device has enrolled fingerprint/face | 1. Enable "Unlock with Fingerprint/Face" in Settings.<br>2. Background the app and reopen.<br>3. Authenticate with registered biometric sensor.<br>4. Cancel biometric and fall back to PIN. | System `BiometricPrompt` displays with cryptographic cipher validation. Successful biometric immediately unlocks app. Cancelling biometric provides seamless fallback to PIN entry. | [ ] Pass<br>[ ] Fail |
| **TC-ONB-08** | `P1` | Keystore Invalidation Recovery Screen | App locked with PIN/Biometrics | 1. While app is installed, open Android Settings > Security and change lock screen type (or clear fingerprints).<br>2. Re-open Udhaar. | App intercepts `KeyPermanentlyInvalidatedException` fail-closed. Displays safe recovery screen ("Secure storage unavailable") offering options to Restore Backup or Reset Data instead of crashing. | [ ] Pass<br>[ ] Fail |

---

## 2. Ledger Management, Exact Paise Engine, Installments & Rounding

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-LED-01** | `P0` | Create Given (Lent) & Taken (Borrowed) Records | On Dashboard | 1. Tap "+" to add new transaction.<br>2. Select "I Gave (Lent)" -> Party: "Ramesh Sharma", Amount: `5000.50`, Due Date: 30 days ahead.<br>3. Save.<br>4. Create second transaction: "I Took (Borrowed)" -> Party: "Sita Devi", Amount: `2500.00`. Save. | Records save immediately to local encrypted Room DB. Principal amount is stored strictly as integer paise (`500050` and `250000`). Dashboard cards reflect "Net Given: ₹5,000.50" and "Net Taken: ₹2,500.00". | [ ] Pass<br>[ ] Fail |
| **TC-LED-02** | `P1` | Edit Core Terms & Audit History | Existing record | 1. Open Ramesh transaction detail.<br>2. Tap Edit.<br>3. Update amount to `6000.50` and change note.<br>4. Save. | Terms update cleanly. An itemized audit log entry records the modification timestamp and previous value. Net balance on dashboard updates to ₹6,000.50. | [ ] Pass<br>[ ] Fail |
| **TC-LED-03** | `P1` | Delete Record (Soft Delete Tombstone) | Existing record | 1. Open transaction detail > tap Menu > Delete Record.<br>2. Confirm deletion dialog. | Record is marked as deleted (tombstone) locally. Record disappears from active dashboard list. Balance recalculates accurately. | [ ] Pass<br>[ ] Fail |
| **TC-LED-04** | `P0` | Record Partial Payments | Active lent record of ₹10,000.00 | 1. Open transaction > tap "Record Payment".<br>2. Enter partial amount `3450.75`, Mode: UPI.<br>3. Save payment.<br>4. Record second partial payment of `6549.25`, Mode: Cash. | After first payment: remaining balance displays `₹6,549.25` (`654925` paise). After second payment: balance reaches `₹0.00` and transaction automatically transitions to "SETTLED". No rounding error of even 1 paisa. | [ ] Pass<br>[ ] Fail |
| **TC-LED-05** | `P0` | Installment Planner Uneven & Rounding Split | New transaction of ₹100.00 | 1. Create transaction of ₹100.00 with Installment Schedule.<br>2. Select 3 monthly installments.<br>3. Inspect generated split. | Installments compute to ₹33.34, ₹33.33, and ₹33.33. Sum of installments equals exactly 10,000 paise (₹100.00). No fractional paisa loss or floating point mismatch. | [ ] Pass<br>[ ] Fail |
| **TC-LED-06** | `P1` | Installment Payment Spillover Allocation | 3-installment plan (₹500 each) | 1. Record an advance payment of ₹750.00. | Payment allocates ₹500.00 to Installment 1 (marking it Fully Paid) and automatically spills remaining ₹250.00 into Installment 2 (Partially Paid). Next due date advances to Installment 2. | [ ] Pass<br>[ ] Fail |
| **TC-LED-07** | `P0` | Large Monetary Scale (Crores / Millions) | New transaction | 1. Add transaction with amount: `₹12,34,56,789.50` (12 Crore 34 Lakh...).<br>2. Record partial payment of `₹2,34,56,789.50`. | 64-bit signed Long integer (`12345678950` paise) handles amount without overflow. Formatted string correctly displays Indian grouping (`₹12,34,56,789.50`). Remaining balance updates to exact `₹10,00,00,000.00`. | [ ] Pass<br>[ ] Fail |
| **TC-LED-08** | `P1` | Decimal Input Boundary & Precision Defense | Add transaction dialog | 1. Attempt typing amount `100.555` (3 decimals).<br>2. Attempt negative amount `-500`.<br>3. Attempt zero amount `0`. | Input filter prevents typing more than 2 decimal digits. Save button remains disabled for zero or negative values. Fails closed with user-friendly validation error. | [ ] Pass<br>[ ] Fail |

---

## 3. Google Drive Encrypted Backup, Second-Device Restore & Fault Tolerance

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-BAK-01** | `P0` | Google Drive AES-256-GCM Encrypted Backup | Signed into Google Drive | 1. Go to Settings > Backup & Restore > "Back up to Google Drive".<br>2. Enter custom 24-char recovery passphrase.<br>3. Execute backup. | Backup file encrypts locally with AES-256-GCM before upload. File uploads to Google Drive hidden `appDataFolder`. Timestamp and encrypted payload size display in Settings. | [ ] Pass<br>[ ] Fail |
| **TC-BAK-02** | `P0` | Cross-Device Restore to Second Phone | Second device with clean install | 1. Install Udhaar on Device 2.<br>2. Sign in with same Google account.<br>3. Tap "Restore from Google Drive".<br>4. Enter correct 24-char recovery passphrase. | Remote backup is detected and downloaded. Decryption succeeds. All parties, records, paise balances, partial payments, and installment schedules match Device 1 with 100% fidelity. | [ ] Pass<br>[ ] Fail |
| **TC-BAK-03** | `P0` | Corrupted Backup File Defense (Data Survival) | Device with active local records | 1. Seed device with 5 active records.<br>2. Provide a corrupted/truncated `.udhaar` backup file.<br>3. Attempt restore. | Restore fails with typed `CorruptedBackupException`. App displays safe error notice. **Existing live records on device remain 100% intact and untouched** (atomic transaction rollback). | [ ] Pass<br>[ ] Fail |
| **TC-BAK-04** | `P0` | Wrong Decryption Passphrase Rejection | Device with backup available | 1. Initiate backup restore.<br>2. Enter an incorrect recovery passphrase. | GCM tag verification fails. Throws `InvalidPassphraseException`. App shows "Incorrect recovery key" error. Zero corrupt or unreadable data written to local database. | [ ] Pass<br>[ ] Fail |
| **TC-BAK-05** | `P1` | Zip-Slip & Path Traversal Injection Defense | Device ready for file restore | 1. Attempt importing a crafted `.udhaar` backup archive containing directory traversal filenames (e.g. `../../lib/payload.so`). | Backup unpacker inspects canonical paths. Detects traversal attempt, rejects entry immediately, logs non-fatal `ZIP_SLIP_ATTEMPT` event with zero PII, and terminates restore. | [ ] Pass<br>[ ] Fail |

---

## 4. Guest Confirmation Flow Across 2 Devices (Web Capability Portal)

> **Setup:** Device 1 (Lender Android App) & Device 2 (Borrower/Guest Mobile Browser).

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-GST-01** | `P0` | Level A Private Link Creation & Guest Read | Device 1 & Device 2 online | 1. On Device 1, open a ₹5,000 transaction > tap "Share Confirmation Link".<br>2. Choose "Standard Private Link (Anonymous)".<br>3. Share URL (`/u/p/{linkId}`) to Device 2.<br>4. On Device 2, open link in Chrome/Safari mobile browser. | Device 2 initializes anonymous Firebase Auth and App Check (reCAPTCHA v3/Enterprise). Renders read-only loan summary card showing payable amount ₹5,000.00 and action buttons. | [ ] Pass<br>[ ] Fail |
| **TC-GST-02** | `P0` | Level B Google-Verified Link: Wrong Account Rejection | Link created with borrower email `borrower@gmail.com` | 1. On Device 1, generate link specifying required email `borrower@gmail.com`.<br>2. On Device 2, open link and sign in with `wrong_user@gmail.com`. | Security rules verify `sha256(request.auth.token.email) == resource.data.borrowerEmailHash`. Query is **DENIED** (`Permission denied`). Web portal displays "Access Denied: Please sign in with the invited email address". | [ ] Pass<br>[ ] Fail |
| **TC-GST-03** | `P0` | Level B Google-Verified Link: Right Account Access | Same link as TC-GST-02 | 1. On Device 2, switch Google account and sign in with `borrower@gmail.com`. | Rules check passes. Document loads successfully. Full transaction terms and action proposal buttons display. | [ ] Pass<br>[ ] Fail |
| **TC-GST-04** | `P0` | Guest `CONFIRM` Proposal Submission & Acceptance | Link open on Device 2 | 1. On Device 2, guest taps "Confirm Terms".<br>2. Submit confirmation.<br>3. On Device 1, open Udhaar app > open Transaction Updates. | Device 2 writes action payload `{actionType: "CONFIRM", status: "PENDING"}` to `/guestLinks/{id}/actions/CONFIRM`. Device 1 displays pending confirmation card. Device 1 taps "Accept". Record status updates to "CONFIRMED". | [ ] Pass<br>[ ] Fail |
| **TC-GST-05** | `P1` | Guest `DISPUTE` Proposal Submission | Active link on Device 2 | 1. On Device 2, guest taps "Dispute Terms".<br>2. Enter reason: "Principal amount was ₹4000, not ₹5000".<br>3. Submit dispute. | Action doc `/actions/DISPUTE` created with status `PENDING`. Device 1 receives notification badge and displays borrower's dispute reason. | [ ] Pass<br>[ ] Fail |
| **TC-GST-06** | `P1` | Guest `PROPOSE_DATE` Due Date Adjustment | Active link on Device 2 | 1. On Device 2, tap "Propose New Date".<br>2. Select date 15 days later + add reason.<br>3. Submit.<br>4. On Device 1, review and tap "Accept Date". | Date action created. Device 1 accepts proposal; transaction due date updates locally and in cloud. | [ ] Pass<br>[ ] Fail |
| **TC-GST-07** | `P0` | Guest `CLAIM_PAYMENT` (UPI Mode) | Active link on Device 2 | 1. Tap "Claim Payment Made".<br>2. Enter Amount: `₹2,500.00`, Mode: `UPI`, UTR: `UPI987654321012`, Note: "Paid via GPay".<br>3. Submit claim. | Document `/actions/CLAIM_PAYMENT` created with `amountPaise: 250000`. Rules enforce exact paise and valid UTR string. Device 1 shows pending payment claim. | [ ] Pass<br>[ ] Fail |
| **TC-GST-08** | `P1` | Guest `CLAIM_PAYMENT` (Cash & Bank Transfer Modes) | Active link on Device 2 | 1. Test claim with Mode: `CASH` (no UTR required).<br>2. Test claim with Mode: `BANK_TRANSFER` (IMPS reference). | Validated cleanly against `ALLOWED_PAYMENT_MODES`. Both modes save successfully with status `PENDING`. | [ ] Pass<br>[ ] Fail |
| **TC-GST-09** | `P0` | Duplicate Pending Action Denial (Free Rate Limiting) | Pending `CLAIM_PAYMENT` exists | 1. While previous `CLAIM_PAYMENT` is PENDING on Device 2, attempt submitting a second `CLAIM_PAYMENT`. | Firestore rules prevent overwriting an existing pending action: write is rejected with `Permission denied`. Client informs user that a previous claim is already awaiting lender review. | [ ] Pass<br>[ ] Fail |
| **TC-GST-10** | `P0` | Lender Rejection of Payment Claim | Pending claim on Device 1 | 1. On Device 1, open pending claim.<br>2. Tap "Reject Claim" > Enter reason: "UTR not found in bank statement". | Action status transitions to `REJECTED`. Local transaction balance is unchanged. Device 2 shows rejection notice when page is refreshed. | [ ] Pass<br>[ ] Fail |
| **TC-GST-11** | `P0` | Revoked Link Access Prevention | Active link shared | 1. On Device 1, tap "Revoke Link" on transaction.<br>2. On Device 2, refresh or open the link. | Device 1 updates link `status = "REVOKED"`. Device 2 receives `PERMISSION_DENIED` on read or friendly banner: "This link has been deactivated by the sender." Zero financial data exposed. | [ ] Pass<br>[ ] Fail |
| **TC-GST-12** | `P0` | Expired Link Access Prevention | Expired link | 1. Generate link with 1-hour expiration.<br>2. Test opening link after expiration timestamp passes. | Rules verify `request.time < resource.data.expiresAtMs`. Query fails closed with `Permission denied`. Web page displays "This link has expired." | [ ] Pass<br>[ ] Fail |
| **TC-GST-13** | `P1` | Missing Lender UPI Graceful UI Handling | Lender has NO UPI ID in Settings | 1. Clear Lender UPI ID in Settings.<br>2. Generate guest link and open on Device 2.<br>3. Observe UPI section in guest portal. | QR code container is hidden. Clean notice displays: "Lender has not added a UPI ID." App and web portal do NOT crash or show broken placeholder images. | [ ] Pass<br>[ ] Fail |

---

## 5. Offline-First Resilience & Sync Conformance

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-OFF-01** | `P0` | Full Offline Local Ledger Mutation | Put Device 1 into Airplane Mode | 1. Enable Airplane Mode.<br>2. Create 2 new transactions.<br>3. Record 1 partial payment.<br>4. Generate PDF receipt.<br>5. Restart the app while still offline. | App performs all actions smoothly with 0ms network latency. Encrypted Room SQLite commits all records. PDF renders locally. Re-opening app offline shows all entries intact. | [ ] Pass<br>[ ] Fail |
| **TC-OFF-02** | `P0` | Offline Action Acceptance & Reconnection Convergence | Device 1 offline, pending guest claim cached | 1. While offline, open previously cached pending payment claim.<br>2. Tap "Accept".<br>3. Verify local balance updates.<br>4. Disable Airplane Mode and reconnect to Wi-Fi. | Local outbox enqueues mutation. Upon reconnection, sync worker drains outbox to Firestore. Balance converges without duplicate payment insertion or double-counting. | [ ] Pass<br>[ ] Fail |
| **TC-OFF-03** | `P1` | Edit-Beats-Delete Conflict Resolution | 2 devices modifying same record offline | 1. Device A deletes record while offline.<br>2. Device B edits record note while offline.<br>3. Reconnect both devices. | Sync engine executes Edit-Beats-Delete rule: the record is resurrected with active status and updated fields. Data is never silently lost. | [ ] Pass<br>[ ] Fail |

---

## 6. Daily Quota Exhaustion (`RESOURCE_EXHAUSTED` / 429) Fault Tolerance

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-QTA-01** | `P0` | Android Client `RESOURCE_EXHAUSTED` Interception | Device online | 1. Simulate Firestore quota exhaustion (inject `FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED` via debug hook or emulator limit).<br>2. Attempt syncing records. | App does NOT crash. Intercepts exception and displays plain-language notice: *"Sync is resting until tomorrow to keep Udhaar free for everyone. All your records are safely saved on your device."* | [ ] Pass<br>[ ] Fail |
| **TC-QTA-02** | `P0` | Local Functionality During Quota Rest Period | Quota exhausted state | 1. While Firestore rejects requests with 429 / `RESOURCE_EXHAUSTED`, add new records and view existing parties on Android device. | Local SQLite database operates at full speed. All user actions save successfully to local disk. Outbox queues updates for midnight PST quota reset. | [ ] Pass<br>[ ] Fail |
| **TC-QTA-03** | `P1` | Zero-PII Crashlytics Non-Fatal Logging | Quota exhausted state | 1. Trigger `RESOURCE_EXHAUSTED`.<br>2. Inspect logcat and Firebase Crashlytics dashboard. | Logcat shows scrubbed `[REDACTED]` message. Crashlytics records non-fatal event `RESOURCE_EXHAUSTED` containing only anonymized error code and install ID. Zero names, phones, or amounts logged. | [ ] Pass<br>[ ] Fail |

---

## 7. Permissions, Device Form Factors & Android OS Compatibility

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-ENV-01** | `P1` | Contacts Permission Denial Graceful Fallback | Fresh install | 1. When adding a party, tap "Pick from Contacts".<br>2. When system prompt appears, tap "Don't allow" (Deny).<br>3. Tap "Pick from Contacts" again. | App does not crash. Displays helpful rationale banner explaining contacts permission is optional. Allows typing party name and phone number manually. | [ ] Pass<br>[ ] Fail |
| **TC-ENV-02** | `P1` | Android 13+ Notification Permission Denial | Android 13+ device | 1. Launch app on Android 13+. Deny `POST_NOTIFICATIONS`.<br>2. Create due-date reminder. | Core functionality works completely. In-app badge reminds user notifications are muted, with shortcut to Android Settings. | [ ] Pass<br>[ ] Fail |
| **TC-ENV-03** | `P0` | OS Matrix: Android 10, 13, 14, 15+ Compatibility | Target devices available | 1. Execute basic smoke test (install, add record, app lock) on Android 10 (API 29), Android 13 (API 33), and Android 15/16 (compileSdk 36). | Zero API compatibility crashes. Biometrics, SQLCipher, notifications, and edge-to-edge window insets render properly on all supported Android versions. | [ ] Pass<br>[ ] Fail |
| **TC-ENV-04** | `P1` | Compact Phone Screen (360dp Width) | Device or emulator set to 360x640dp | 1. Open "Add Record" modal.<br>2. Open Settings screen. | Form width respects `<= 560dp` constraint. Touch targets maintain `>= 48dp`. All buttons, input fields, and bottom action bars remain completely visible and scrollable. | [ ] Pass<br>[ ] Fail |
| **TC-ENV-05** | `P1` | Dark Mode & Theme Contrast | System theme set to Dark | 1. Switch Android system theme to Dark.<br>2. Navigate Dashboard, Transaction Details, and UPI Sheet in Emerald Slate and Monochrome themes. | High-contrast palette active. No unreadable black-on-black or white-on-white text. WCAG AAA contrast ratio satisfied on core financial figures. | [ ] Pass<br>[ ] Fail |
| **TC-ENV-06** | `P1` | Accessibility Font Scaling (1.3x to 2.0x) | Android Settings > Font Size set to Largest (2.0x) | 1. Set display font scale to 2.0x.<br>2. Navigate Dashboard, Add Form, and Settings. | Text wraps cleanly without truncation. Modal BottomSheets automatically expand and scroll to keep "Save" and "Cancel" buttons reachable. | [ ] Pass<br>[ ] Fail |

---

## 8. Account & Complete Data Deletion (Play Store Compliance)

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-DEL-01** | `P0` | In-App "Delete My Account & Data" Workflow | User has active account, local records, Drive backup, and Firestore docs | 1. Navigate to Settings > Account > "Delete my account and data".<br>2. Read warning dialog explaining permanent deletion.<br>3. Type "DELETE" or confirm biometric auth.<br>4. Confirm deletion. | Progress dialog displays deletion stages: client-side batch deletion of Firestore `/users/{uid}/**`, revocation/cleanup of guest links, purging of Google Drive backup, wiping Room DB & Keystore keys, and deletion of Firebase Auth account. | [ ] Pass<br>[ ] Fail |
| **TC-DEL-02** | `P0` | Post-Deletion State Verification | TC-DEL-01 executed | 1. Inspect app state post-deletion.<br>2. Re-launch app.<br>3. Check Google Drive and Firebase Console. | App returns to pristine clean welcome screen. Local SQLite database is wiped clean. Google Drive backup folder is empty. Firebase Auth user is deleted. | [ ] Pass<br>[ ] Fail |
| **TC-DEL-03** | `P1` | Public Web Deletion Request Page | Web browser | 1. Navigate to `https://udhaar.jyotirmoyb.com/delete-account` (`/delete-account.html`). | Public page loads cleanly without authentication. Provides step-by-step in-app deletion instructions, support contact email, and data retention policy matching Play Console requirements. | [ ] Pass<br>[ ] Fail |

---

## 9. Upgrade Test: Version Migration & Data Retention

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-UPG-01** | `P0` | Room Schema Migration (v11/v12 to Current) | Device with previous release installed | 1. Install previous release APK.<br>2. Create 10 parties, 15 transactions, 5 partial payments, and customized Settings.<br>3. Note total outstanding balances.<br>4. Install the new release build over it: `adb install -r app-release.apk`.<br>5. Launch the upgraded app. | App launches without `IllegalStateException` or migration failure. Room migrations execute seamlessly. 100% of historical transactions, paise sums, partial payments, and PIN locks are preserved exactly. | [ ] Pass<br>[ ] Fail |

---

## 10. Production Minified (R8 Full Mode) Build Smoke Test

| ID | Priority | Test Title & Scope | Preconditions | Detailed Step-by-Step Instructions | Expected Result | Pass / Fail |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-REL-01** | `P0` | Release AAB R8 Obfuscation & Mapping Verification | Built via `./gradlew :app:bundleRelease` | 1. Build release AAB: `./gradlew :app:bundleRelease`.<br>2. Inspect `app/build/outputs/mapping/release/mapping.txt`.<br>3. Unzip AAB and verify embedded `proguard.map`. | `mapping.txt` is populated and non-empty. `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` is bundled inside the AAB. Total uncompressed DEX size is shrunk `< 12 MB`. | [ ] Pass<br>[ ] Fail |
| **TC-REL-02** | `P0` | Release Build Model Mapping & Reflection | Signed release build installed on real phone | 1. Launch signed release build.<br>2. Perform end-to-end sync, Google Sign-in, and PDF generation. | Room entities, Firestore data classes (`CloudSharedRecord`, `GuestActionV1`), kotlinx.serialization companions, and SQLCipher JNI bridges work without `ClassNotFoundException` or `NoSuchMethodError`. | [ ] Pass<br>[ ] Fail |
| **TC-REL-03** | `P0` | App Check Play Integrity Provider in Release | Signed release build installed | 1. Generate a guest confirmation link.<br>2. Verify App Check token exchange in release mode. | Play Integrity provider is active. Zero debug App Check provider tokens are compiled or emitted in release mode. | [ ] Pass<br>[ ] Fail |
| **TC-REL-04** | `P1` | About Screen Plan Mode & Version Display | Signed release build | 1. Open Settings > About / Diagnostics screen.<br>2. Inspect version and plan indicators. | Screen displays: "Version: 1.0.0 (versionCode 30)", "Plan mode: Spark (free)", and "BLAZE_FEATURES_ENABLED: false". | [ ] Pass<br>[ ] Fail |
