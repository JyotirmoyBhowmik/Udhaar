# Google Play Data Safety Declaration & Form Answers — Udhaar (v24 Spark Mode)

This document provides exact, verified answers for the **Google Play Console Data Safety Form** for Udhaar (package `com.jyotirmoyb.udhaar`), reflecting **Spark Mode** architecture (100% free, sustainable, zero-cost Google infrastructure).

---

## 1. Executive Summary of Declarations

- **Data Collection**: Udhaar collects limited personal information solely for account operation, fraud prevention, and optional peer-to-peer debt tracking.
- **Data Sharing**: **0** third-party ad networks, data brokers, or marketing platforms. Data is shared only with Google Firebase (the hosting infrastructure provider under contract).
- **Data Security**: All data transferred over secure connections (HTTPS/TLS). On Android devices, local database is encrypted with AES-256 via Room and SQLCipher backed by Android Keystore.
- **Data Deletion**: Users can delete their account and all remote records self-service in-app (**Settings &rarr; Delete Account**), or via web request at `https://udhaar.jyotirmoyb.com/privacy.html`.

---

## 2. Google Play Data Safety Form Questionnaire — Question by Question

### Section 1: Data Collection & Sharing Overview

| Question | Answer | Notes |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **Yes** | App collects email, optional contact phone, name, and transaction records when sync is enabled. |
| **Is all user data collected by your app encrypted in transit?** | **Yes** | All network traffic uses HTTPS / TLS 1.3 to Firebase endpoints. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Self-service button in Settings and deletion form on website. |
| **Does your app allow users to create an account?** | **Yes** | Email/Password or Google Sign-In. |
| **Is the account deletion URL publicly accessible?** | **Yes** | `https://udhaar.jyotirmoyb.com/privacy.html#delete-form-card` |

---

### Section 2: Detailed Data Types

#### 1. Personal Info
- **Name**:
  - *Collected*: Yes (User name and counterparty names entered by user).
  - *Shared*: No (Never shared with third parties).
  - *Purpose*: App functionality, Account management.
  - *Optional or required*: Required for record context.
- **Email address**:
  - *Collected*: Yes (Firebase Authentication email).
  - *Shared*: No.
  - *Purpose*: Account management, Fraud prevention & security, Email verification.
  - *Optional or required*: Required for cloud sync accounts.
- **Phone number**:
  - *Collected*: Yes (Optional contact detail entered by user).
  - *Shared*: No.
  - *Purpose*: App functionality (matching contacts and pre-filling WhatsApp intents).
  - *Optional or required*: **Optional**.
  - *Status*: **Always explicitly labelled `phoneStatus: "unverified"` ("Number not verified") in Spark Mode**. Never used for authentication, login, or recovery.
- **Other Personal Info**:
  - *User IDs*: Yes (Firebase Auth UID).
  - *Shared*: No.
  - *Purpose*: Account management, Authorization.

#### 2. Financial Info
- **User payment info / Purchase history**:
  - *Collected*: Yes (Peer-to-peer lending and borrowing records: amounts in whole paise, due dates, payment history, notes).
  - *Shared*: No.
  - *Purpose*: App functionality (Core ledger feature).
  - *Optional or required*: Required for ledger operation.
  - *Credit score*: **No** (Not collected).

#### 3. Photos and Videos
- *Photos*:
  - *Collected*: **No** in cloud sync (Spark Mode has zero cloud photo uploads). On device, local photo receipts may be picked and stored locally in app-private storage.
  - *Shared*: **No**.

#### 4. Messages
- *Emails / SMS*: **No** (Not collected, read, or accessed).

#### 5. Location
- *Approximate or Precise location*: **No** (Not collected).

#### 6. Contacts
- *Contacts*:
  - *Collected*: **No**.
  - *Details*: The Android app requests `READ_CONTACTS` solely as an ephemeral runtime intent helper when the user explicitly taps "Import Contact" to select a single contact name and number. **The contact book is never uploaded in bulk or synchronized to any server**.

#### 7. App Activity & Performance
- *App interactions*: No third-party tracking.
- *Crash logs & Diagnostics*: Standard Play Console vitals only. No third-party crash SDKs in Spark Mode.

#### 8. Device or Other IDs
- *Device IDs*: Play Integrity / App Check tokens (ephemeral tokens to prevent abuse and bot spam). Not used for user profiling or advertising.

---

## 3. Data Not Collected (Explicit Confirmation)

Udhaar does **NOT** collect, access, or transmit any of the following:
1. **Bulk contacts list** (Address book is never scraped, uploaded, or analyzed).
2. **Precise or coarse GPS location**.
3. **SMS messages, call logs, or phone state**.
4. **Microphone or audio recordings**.
5. **Biometric data** (Device fingerprint/face unlock stays entirely inside Android BiometricPrompt hardware security module).
6. **Advertising ID (AAID)** or tracking cookies.

---

## 4. Third-Party Infrastructure Disclosure & Zero Advertising

Udhaar operates **100% on Google infrastructure with zero advertising networks**, data brokers, or commercial marketing SDKs:
- **Firebase Authentication**: User identity and password management.
- **Google Cloud Firestore**: Cloud document storage for accounts with sync enabled.
- **Firebase Hosting**: Delivery of web assets with strict Content Security Policy.
- **Firebase Remote Config**: Kill switches and community quota controls.
- **Google Play Integrity & Firebase App Check**: Device attestation and anti-abuse verification.

---

## 5. Account Deletion & Data Retention Protocol

- **In-App Account Deletion**:
  1. Open Udhaar app &rarr; Settings &rarr; Account Safety.
  2. Tap **"Delete Account & Remote Data"**.
  3. Re-authenticate with account password.
  4. Remote Firestore collection (`/users/{uid}`) is wiped, Firebase Auth account is permanently removed.
  5. Local database on the device can be cleared via **Settings &rarr; Wipe Local Data** or by uninstalling the application.
- **Web Deletion Request**:
  - Anyone (including guest users who confirmed transactions) may request data deletion via `https://udhaar.jyotirmoyb.com/privacy.html#delete-form-card`. Requests are reviewed and acted upon within 48 hours.
