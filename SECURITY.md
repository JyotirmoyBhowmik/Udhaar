# Security Policy

## Supported Versions

Only the latest public release of Udhaar is actively supported with security updates.

| Version | Supported | Mode |
| :--- | :--- | :--- |
| **28.0 (`versionCode = 28`)** | Yes | Spark Mode (`UDHAAR_TIER = "spark"`, `CLOUD_FEATURES = false`) |
| `< 28.0` | No | Upgrade to 28.0+ |

---

## How to Report a Security Problem

If you discover a security vulnerability, privacy issue, or Firestore rules bypass in Udhaar, please report it privately so we can fix it before public disclosure.

- **Primary Security Contact**: [admin@jyotirmoyb.com](mailto:admin@jyotirmoyb.com)
- **Grievance & Data Protection Officer**: [grievance@jyotirmoyb.com](mailto:grievance@jyotirmoyb.com)
- **Subject Line**: `[SECURITY] Udhaar Vulnerability Report — <brief summary>`

Please do **not** open a public GitHub issue for unpatched security vulnerabilities.

### What to Include in Your Report

1. **Affected Component**: Android app (`com.jyotirmoyb.udhaar`), Firestore Security Rules (`firestore.rules`), Web Ledger (`webapp/`), Guest Portal (`web/u/`), or Marketing/Static site (`web/`).
2. **Reproduction Steps**: Clear step-by-step instructions, proof-of-concept code, or rule evaluation test case demonstrating the issue.
3. **Impact Assessment**: What data or capability an attacker could access or modify.

---

## Response Timeline

- **Initial Acknowledgement**: Within **48 hours** of receiving your report.
- **Triage & Severity Confirmation**: Within **5 business days**.
- **Fix & Release**:
  - Critical / High severity (e.g., Firestore rules bypass, cryptographic flaw): Within **7–14 days**.
  - Medium / Low severity: Within **30 days** (aligned with our statutory 30-day Grievance Officer SLA).

---

## Scope & Security Architecture Summary

### In Scope
- **Local Encryption (`app/`)**: SQLCipher 256-bit database encryption (`EncryptedStorageManager.kt`), PBKDF2 PIN hashing (`PinSecurityManager.kt`), and AES-256-GCM `.udhaar` Drive backups (`DriveBackupCrypto.kt`).
- **Firestore Security Rules (`firestore.rules`)**:
  - Owner-only + verified-email isolation on `/users/{uid}` and its subcollections (`records`, `people`, `events`, `plans`, `attachments`, `meta/syncSignal`).
  - Capability-hash isolation on `/guestLinks/{linkId}` and fixed 10-slot action creation caps on `/guestLinks/{linkId}/actions/{actionId}` (`confirm`, `dispute`, `claim_1..5`, `date_1..3`).
  - Append-only immutability on `events` and `actions` (no client updates or deletes).
- **Content Security Policy (`firebase.json`, `infra/firebase.*.json`)**:
  - Strict zero-inline-script and zero-inline-style enforcement across all HTML pages.
  - `connect-src` restricted exclusively to Firebase Authentication, Cloud Firestore, and Firebase App Check endpoints.

### Out of Scope
- Denial-of-service attacks relying solely on exhausting free Firebase Spark tier daily quotas.
- Social engineering, phishing, or physical access to an unlocked device where the user has not enabled the optional PIN/Biometric lock.
- Unverified contact phone numbers entered by users (`phoneStatus: "unverified"`), which are never used for authentication or account recovery in Spark Mode.

---

## Safe Harbor

Good-faith security research conducted in compliance with this policy—without accessing or modifying other users' real data, and without disrupting service availability—is authorized. We will not pursue legal action against researchers who report vulnerabilities responsibly under this policy.
