# Udhaar Platform v2: Data Classification & Schema Specification

- **Version**: 2.0.0
- **Scope**: Firestore Collections, Cloud Storage, SQLite/Room Schemas, and IndexedDB

---

## 1. Data Classification Matrix

| Classification Level | Definition | Handling Rules |
| :--- | :--- | :--- |
| **Confidential PII** | Direct personally identifiable information (Phone, Email, Name, Identity). | **Field-level envelope encrypted** via Cloud KMS (AES-256-GCM). Blind-indexed via HMAC-SHA256 for exact match lookups. Never logged in plaintext. |
| **Financial PII** | Sensitive financial metadata (UPI Virtual Payment Addresses, personal payment notes). | **Field-level envelope encrypted** via Cloud KMS. Never logged or exposed in unauthenticated API responses. |
| **Operational Financial** | Numerical ledger fields required for calculations and notifications (Principal amount in paise, interest rate, due dates, payment amounts). | **Plaintext in Firestore**. Server-readable to enable guest links, WhatsApp template variable population, and settlement math. Access controlled strictly by API authentication. |
| **System & Sync Metadata** | Synchronization identifiers, revision counters, logical timestamps, status enums. | **Plaintext in Firestore**. Non-sensitive; used for replication, conflict resolution, and routing. |

---

## 2. Comprehensive Field Classification by Collection

### 2.1 Collection: `users/{uid}`
* **Document ID**: Random UUIDv4 (`usr_...`). Never derived from phone number.
* **Size Limit**: $\le 10\text{ KB}$

| Field | Classification | Encryption Method | Searchable Via | Description |
| :--- | :--- | :--- | :--- | :--- |
| `uid` | System Metadata | None | Direct Path | Unique user identifier |
| `phoneEncrypted` | Confidential PII | Envelope AES-256-GCM | Blind Index | User mobile number (E.164) |
| `phoneHash` | Confidential PII | HMAC-SHA256(pepper) | `phoneIndex/` | Blind lookup index |
| `emailEncrypted` | Confidential PII | Envelope AES-256-GCM | Blind Index | User email address |
| `emailHash` | Confidential PII | HMAC-SHA256(pepper) | `emailIndex/` | Blind lookup index |
| `displayNameEncrypted` | Confidential PII | Envelope AES-256-GCM | None | User chosen display name |
| `defaultCurrency` | Operational | None | None | Currency code (`INR` or `NPR`) |
| `locale` | Operational | None | None | UI locale (`en`, `hi`, `bn`, `as`, `ne`) |
| `createdAtMs` | Operational | None | Query Order | Epoch timestamp of account creation |
| `updatedAtMs` | Operational | None | None | Epoch timestamp of last profile update |
| `consents` | Compliance | None | None | Recorded DPDP consent versions and timestamps |
| `status` | System Metadata | None | None | `ACTIVE`, `SUSPENDED`, `DELETED_PENDING` |

---

### 2.2 Collection: `users/{uid}/records/{recordId}`
* **Document ID**: UUIDv7 (`rec_...`).
* **Size Limit**: $\le 30\text{ KB}$

| Field | Classification | Encryption Method | Description |
| :--- | :--- | :--- | :--- |
| `recordId` | System Metadata | None | Primary record ID |
| `syncId` | System Metadata | None | UUIDv7 distributed sync identifier |
| `partyId` | System Metadata | None | Associated counterparty ID |
| `partyNameEncrypted` | Confidential PII | Envelope AES-256-GCM | Counterparty name |
| `partyPhoneEncrypted`| Confidential PII | Envelope AES-256-GCM | Counterparty mobile number |
| `counterpartyRole` | Operational | None | `LENDER` (I borrowed) or `BORROWER` (I lent) |
| `principalAmountPaise`| Operational Financial| None (Integer Long) | Initial principal amount in integer paise |
| `remainingAmountPaise`| Operational Financial| None (Integer Long) | Outstanding balance in integer paise |
| `dueDateMs` | Operational Financial| None (Epoch ms) | Promised return date |
| `noteEncrypted` | Confidential | Envelope AES-256-GCM | Transaction description |
| `itemsEncrypted` | Confidential | Envelope AES-256-GCM | Itemized breakdown array |
| `upiIdEncrypted` | Financial PII | Envelope AES-256-GCM | Payee UPI VPA |
| `status` | Operational | None | `PENDING`, `CONFIRMED`, `DISPUTED`, `SETTLED` |
| `clientRevision` | System Metadata | None | Monotonically increasing revision counter |
| `hlc` | System Metadata | None | Hybrid Logical Clock string (`timestamp:counter:device`) |
| `serverRev` | System Metadata | None | Authoritative server sequence counter |
| `deletedAtMs` | System Metadata | None | Soft-delete tombstone timestamp (or null) |

---

### 2.3 Collection: `users/{uid}/events/{eventId}` (Append-Only)
* **Document ID**: UUIDv7 (`evt_...`).
* **Size Limit**: $\le 10\text{ KB}$
* **Rule**: Append-only. Modifications and hard deletions strictly forbidden.

| Field | Classification | Encryption Method | Description |
| :--- | :--- | :--- | :--- |
| `eventId` | System Metadata | None | Event ID |
| `recordId` | System Metadata | None | Target record ID |
| `eventType` | System Metadata | None | `PARTIAL_PAYMENT`, `STATUS_CHANGE`, `TERMS_EDIT` |
| `amountPaise` | Operational Financial| None | Payment amount in paise (if applicable) |
| `paymentMode` | Operational | None | `UPI`, `CASH`, `BANK_TRANSFER` |
| `noteEncrypted` | Confidential | Envelope AES-256-GCM | Payment note or reason |
| `timestampMs` | Operational | None | Execution epoch ms |
| `merklePrevHash` | System Metadata | None | Hash of prior event in block chain |
| `merkleHash` | System Metadata | None | SHA-256 digest of this block |

---

### 2.4 Blind Index Collections

#### `phoneIndex/{phoneHmac}`
* **Document ID**: `HMAC-SHA256(E.164_phone, secret_phone_pepper)`.
* **Fields**: `{ uid: "usr_...", createdAtMs: 1762498200000 }`.
* **Access**: Functions Admin SDK only. Direct client read/write denied.

#### `emailIndex/{emailHmac}`
* **Document ID**: `HMAC-SHA256(normalized_email, secret_email_pepper)`.
* **Fields**: `{ uid: "usr_...", createdAtMs: 1762498200000 }`.
* **Access**: Functions Admin SDK only. Direct client read/write denied.

---

### 2.5 Ephemeral Collections

#### `otpChallenges/{challengeId}`
* **Document ID**: Random UUIDv4.
* **Fields**:
  - `purpose`: `SIGNUP` | `SIGNIN` | `CHANGE_PHONE` | `CHANGE_EMAIL` | `GUEST_CONFIRM`
  - `channel`: `WHATSAPP` | `SMS` | `EMAIL`
  - `destinationHash`: HMAC-SHA256 of phone or email
  - `codeHash`: HMAC-SHA256 of the 6-digit code
  - `flowIdHash`: HMAC-SHA256 of client flowId
  - `attempts`: Integer (0 to 5)
  - `maxAttempts`: 5
  - `resendCount`: Integer
  - `createdAtMs`: Timestamp
  - `expiresAtMs`: Timestamp
  - `consumed`: Boolean
  - `ttl`: Firestore TTL field (auto-purged 24 hours after expiration)

#### `auditLogs/{logId}`
* **Document ID**: KSUID / UUIDv7.
* **Fields**: `{ timestamp, actorUid, action, ipHash, userAgent, correlationId, details, prevHash, hash }`.
* **Retention**: 400 days in Firestore, archived to Coldline Storage bucket.
