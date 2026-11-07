# ADR-0004: Server-Readable Data with Field-Level Envelope Encryption Using Cloud KMS

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Security Lead, Backend Lead, Compliance Officer

---

## 1. Context and Problem Statement

Udhaar manages personal credit and debt records between two counterparties. The records contain both sensitive personal identifying information (PII) such as phone numbers, emails, counterparty names, transaction notes, and item descriptions, as well as operational data like principal amounts (in paise), interest rates, due dates, and verification timestamps.

A key architectural question for Platform v2 is whether the cloud ledger should be:
1. **Fully End-to-End Encrypted (E2EE)**: The server stores only opaque blobs that only client devices can decrypt.
2. **Plaintext Storage**: All fields stored as standard Firestore documents with database-level encryption at rest (default Google-managed keys).
3. **Field-Level Envelope Encryption (Selected)**: The server has controlled, audited access to decrypt specific fields via Cloud KMS, while sensitive PII fields are encrypted at the application layer with per-user data encryption keys (DEK) wrapped by a Key Encryption Key (KEK) in Cloud KMS. Operational numbers (amounts in paise, due dates, status) remain server-readable.

## 2. Decision Drivers

- **Functional Requirements**:
  - The server must generate unauthenticated guest verification links where counterparties view the exact amount due, repayment terms, and return dates.
  - The server must execute scheduled reminders, compute late notices, and send automated WhatsApp utility notifications.
  - The server must aggregate analytics, enforce tier quotas, and perform verification auditing without exposing raw PII.
- **Why Full E2EE is a Documented Non-Goal for v2**:
  - In a full E2EE ledger, the server cannot read amounts or due dates. If a lender sends a guest link to a borrower who does not have the app installed, either the lender's device must remain online to act as a cryptographic proxy, or the encryption key must be shared via the link (which makes the link an unauthenticated bearer token, defeating the server's ability to enforce rate limits, phone OTP verification, and dispute resolution).
  - Furthermore, server-side notification templates (WhatsApp utility templates approved by Meta) require the server to supply variable values (amount and date) directly in the API payload to Meta. If the server cannot read the amount, it cannot send WhatsApp reminders on behalf of the user.
- **Cost of Full E2EE in Future Versions**:
  - Implementing full zero-knowledge E2EE in future iterations would require multi-party threshold cryptography or asymmetric client-side key exchanges (e.g. Signal Protocol double-ratchet for every counterparty relationship). This would increase client complexity tenfold, require complex key backup ceremonies, and prevent server-driven WhatsApp reminders.

## 3. Decision Outcome

Chosen Option: **Server-Readable Data with Field-Level Envelope Encryption (Cloud KMS)**.

### 1. Envelope Encryption Scheme

```
                     ┌───────────────────────────┐
                     │ Google Cloud KMS          │
                     │ Key Ring: udhaar-keyring  │
                     │ KEK: user-dek-wrapper     │
                     │ (Rotated every 90 days)   │
                     └─────────────┬─────────────┘
                                   │ Wrap / Unwrap
                                   ▼
┌─────────────────────────────────────────────────────────────┐
│ Cloud Functions Server (asia-south1)                        │
│                                                             │
│ Per-User Data Encryption Key (DEK):                         │
│ - Generated via crypto.randomBytes(32) (AES-256-GCM)        │
│ - Wrapped by KMS KEK and stored in users/{uid}/keys/current │
│                                                             │
│ Encryption:                                                 │
│ Ciphertext = AES-256-GCM(plaintext, DEK, IV, AAD={uid, rec})│
└─────────────────────────────────────────────────────────────┘
```

### 2. Field Classification Matrix

| Field Category | Classification | Storage Format | Encryption Mechanism |
| :--- | :--- | :--- | :--- |
| **Party Names** | Confidential PII | Encrypted String | AES-256-GCM (DEK) |
| **Phone Numbers** | Confidential PII | Encrypted String + Blind Index | AES-256-GCM (DEK) + HMAC-SHA256 (Pepper) |
| **Email Addresses** | Confidential PII | Encrypted String + Blind Index | AES-256-GCM (DEK) + HMAC-SHA256 (Pepper) |
| **UPI Virtual Payment Addresses**| Financial PII | Encrypted String | AES-256-GCM (DEK) |
| **Transaction Notes & Items** | Confidential | Encrypted String / JSON | AES-256-GCM (DEK) |
| **Principal Amount (paise)** | Operational | Integer (Long) | Plaintext in Firestore (Server-Readable) |
| **Due Dates & Timestamps** | Operational | Integer (Epoch ms) | Plaintext in Firestore (Server-Readable) |
| **Status, Rev, SyncId, HLC** | Operational | String / Integer | Plaintext in Firestore (Server-Readable) |

### 3. Key Rotation & Lifecycle
- The Cloud KMS KEK is configured with automatic rotation every 90 days.
- User DEKs are re-wrapped under the newest KEK version upon access.
- On user account deletion, the user's DEK is cryptographically purged, rendering all past backups and replicas permanently unrecoverable (cryptographic erasure).

## 4. Consequences

### Positive
- Server can send WhatsApp templates with exact amounts and due dates.
- Guest web portal can verify amounts without requiring client-side key exchange.
- PII is protected against database dump compromises and rogue snapshot inspections.
- Satisfies DPDP Act 2023 technical safeguards for financial data.

### Negative / Tradeoffs
- Cloud KMS API calls introduce slight latency (~10-25ms) and nominal per-request costs, mitigated by caching unwrapped DEKs in the secure, short-lived Cloud Functions instance memory (max 5 minutes TTL).
