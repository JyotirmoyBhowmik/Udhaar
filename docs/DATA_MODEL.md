# Udhaar Platform v2 — Reconciled Data Model Reference

**Document Version**: 2.0.0-PROD  
**Target Schema Versions**: Android Room Schema v13 | Cloud Firestore v2 Distributed Schema  

---

## 1. Local SQLCipher Room Database (`udhaar_ledger.db`, Schema Version 13)

All on-device tables are encrypted at rest with a 256-bit SQLCipher passphrase wrapped by Android Keystore (`AES256_GCM`). All currency amounts are strictly stored in integer paise (`Long`) to prevent binary floating-point drift.

### Table 1: `udhaar_transactions` (`UdhaarTransaction`)
Stores core ledger entries with Hybrid Logical Clock (HLC) timestamps and synchronization tracking.

| Column | Type | Constraints / Description |
|---|---|---|
| `id` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `partyId` | `INTEGER?` (`Long?`) | Optional link to `parties.partyId`, indexed |
| `transactionType` | `TEXT` (`TransactionType`) | `GIVEN` (Receivable) or `TAKEN` (Payable), indexed |
| `partyName` | `TEXT` | Counterparty display name |
| `phoneNumber` | `TEXT` | Counterparty phone number (E.164) |
| `currency` | `TEXT` (`CurrencyType`) | `INR`, `NPR`, `USD`, `EUR`, `GBP`, `BDT` |
| `principalAmountPaise` | `INTEGER` (`Long`) | Monetary principal in integer paise (e.g., ₹100.50 = `10050L`) |
| `amountPaidPaise` | `INTEGER` (`Long`) | Cumulative repayments in integer paise |
| `paymentMode` | `TEXT` (`PaymentMode`) | `CASH`, `ONLINE`, `UPI`, `PARTY_ACCOUNT` |
| `isInterestEnabled` | `INTEGER` (`Boolean`) | Whether interest calculation is active |
| `interestType` | `TEXT` (`InterestType`) | `SIMPLE` or `COMPOUND` |
| `interestRate` | `REAL` (`Double`) | Numeric interest rate percentage |
| `interestRatePeriod` | `TEXT` (`InterestRatePeriod`) | `PER_MONTH` or `PER_ANNUM` |
| `compoundFrequency` | `TEXT` (`CompoundFrequency`) | `MONTHLY`, `QUARTERLY`, `YEARLY` |
| `startDateMillis` | `INTEGER` (`Long`) | Loan creation/start epoch timestamp |
| `expiryDateMillis` | `INTEGER?` (`Long?`) | Optional due date epoch timestamp, indexed |
| `reminderDateMillis` | `INTEGER?` (`Long?`) | Next scheduled notification timestamp |
| `notes` | `TEXT` | Free-form notes/memo |
| `isSettled` | `INTEGER` (`Boolean`) | `true` if principal and accrued interest are fully settled |
| `settledAtMillis` | `INTEGER?` (`Long?`) | Settlement epoch timestamp |
| `createdAtMillis` | `INTEGER` (`Long`) | Record creation timestamp |
| `verificationHash` | `TEXT` | Canonical SHA-256 digest of record terms |
| `verificationToken` | `TEXT` | Secret token for web sharing link (`#token`) |
| `verificationStatus` | `TEXT` (`VerificationStatus`) | `UNVERIFIED`, `PENDING_REMOTE`, `CONFIRMED`, `DISPUTED`, `EDITED` |
| `disputeReason` | `TEXT` | Counterparty dispute reason when `DISPUTED` |
| `syncId` | `TEXT` | Cross-device client sync UUID (`client_sync_id`), indexed |
| `hlcTimestamp` | `TEXT` | Hybrid Logical Clock timestamp (`<physicalMs>-<counter>-<nodeId>`) |
| `nodeId` | `TEXT` | Originating client node UUID |
| `syncStatus` | `TEXT` (`SyncStatus`) | `LOCAL_ONLY`, `SYNCED`, `CONFLICT`, `DIRTY` |
| `syncRevision` | `INTEGER` (`Long`) | Monotonic local revision counter |
| `isDeleted` | `INTEGER` (`Boolean`) | Soft-delete tombstone flag (`0` active, `1` trashed), indexed |
| `deletedAtMillis` | `INTEGER?` (`Long?`) | Soft-delete timestamp |

---

### Table 2: `partial_payments` (`PartialPayment`)
Stores individual installment repayments with set-union convergence properties.

| Column | Type | Constraints / Description |
|---|---|---|
| `id` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `transactionId` | `INTEGER` (`Long`) | `FOREIGN KEY -> udhaar_transactions(id) ON DELETE CASCADE`, indexed |
| `amountPaise` | `INTEGER` (`Long`) | Repayment amount in integer paise |
| `paidAtMillis` | `INTEGER` (`Long`) | Timestamp repayment occurred |
| `paymentMode` | `TEXT` | `CASH`, `ONLINE`, `UPI`, `PARTY_ACCOUNT` |
| `note` | `TEXT` | Optional repayment memo |
| `syncId` | `TEXT` | Unique repayment sync UUID, indexed |
| `hlcTimestamp` | `TEXT` | Hybrid Logical Clock timestamp |
| `nodeId` | `TEXT` | Originating device node UUID |
| `syncStatus` | `TEXT` (`SyncStatus`) | `LOCAL_ONLY`, `SYNCED`, `CONFLICT`, `DIRTY` |
| `isDeleted` | `INTEGER` (`Boolean`) | Soft-delete flag |

---

### Table 3: `outbox_mutations` (`OutboxMutation`)
Queue of offline changes waiting to push to Cloud Functions sync API.

| Column | Type | Constraints / Description |
|---|---|---|
| `mutationId` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `entityType` | `TEXT` | `TRANSACTION`, `PAYMENT`, `PARTY` |
| `syncId` | `TEXT` | Entity sync identifier, indexed |
| `operation` | `TEXT` | `INSERT`, `UPDATE`, `DELETE` |
| `payloadJson` | `TEXT` | JSON serialized payload |
| `hlcTimestamp` | `TEXT` | HLC timestamp of mutation |
| `retryCount` | `INTEGER` (`Int`) | Retry backoff counter |
| `createdAtMillis` | `INTEGER` (`Long`) | Enqueue timestamp |

---

### Table 4: `parties` (`PartyEntity`)
People Directory entries.

| Column | Type | Constraints / Description |
|---|---|---|
| `partyId` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `name` | `TEXT` | Contact display name |
| `phoneNumber` | `TEXT` | Normalized E.164 phone number, indexed |
| `upiId` | `TEXT` | Counterparty VPA (`user@upi`) |
| `email` | `TEXT` | Counterparty email |
| `tagOrNotes` | `TEXT` | Relationship category |
| `preferredCurrency` | `TEXT` | Default currency code |
| `createdAtMillis` | `INTEGER` (`Long`) | Creation epoch ms |
| `syncId` | `TEXT` | Unique sync identifier |
| `syncStatus` | `TEXT` | `LOCAL_ONLY`, `SYNCED`, `CONFLICT`, `DIRTY` |

---

## 2. Cloud Firestore Collections (Platform v2 Architecture)

All client reads and writes to Cloud Firestore are blocked by default (`allow read, write: if false;` in `firestore.rules`). Data is managed exclusively by Cloud Functions v2 using envelope encryption.

### 1. `users/{uid}/records/{recordId}`
Primary ledger records partition per user.

| Field Name | Type | Description |
|---|---|---|
| `recordId` | `string` | Unique record document ID |
| `syncId` | `string` | Client synchronization UUID (`client_sync_id`) |
| `hlcTimestamp` | `string` | Hybrid Logical Clock timestamp (`<physicalMs>-<counter>-<nodeId>`) |
| `nodeId` | `string` | Originating client node UUID |
| `seq` | `number` (`int`) | Monotonic per-user sequence number allocated atomically by transaction |
| `encryptedPayload` | `string` (base64) | AES-256-GCM ciphertext protecting `partyName`, `notes`, `contactPhone`, `upiId`, `reminderNote` |
| `iv` | `string` (base64) | 12-byte initialization vector |
| `tag` | `string` (base64) | 16-byte authentication tag |
| `keyVersion` | `number` (`int`) | Version of the user DEK used for encryption |
| `recordType` | `string` | `GIVEN` or `TAKEN` (clear attribute for indexing) |
| `currency` | `string` | Currency ISO code (`INR`, `NPR`, etc.) |
| `principalAmountPaise` | `number` (`int64`) | Principal in integer paise |
| `amountPaidPaise` | `number` (`int64`) | Cumulative repayments in integer paise |
| `isSettled` | `boolean` | Settlement status flag |
| `isDeleted` | `boolean` | Tombstone deletion flag |
| `dueDateMillis` | `number` (`int64`) | Due date epoch timestamp |
| `updatedAtMillis` | `number` (`int64`) | Last mutation timestamp |
| `syncStatus` | `string` | `SYNCED` or `CONFLICT` |

---

### 2. `users/{uid}/keys/current`
User envelope encryption Data Encryption Key (DEK).

| Field Name | Type | Description |
|---|---|---|
| `wrappedKey` | `string` (base64) | 256-bit AES DEK wrapped by Google Cloud KMS (`asia-south1`) |
| `keyVersion` | `number` (`int`) | Monotonic key rotation version |
| `kmsKeyName` | `string` | Cloud KMS resource name used to wrap the DEK |
| `createdAtMillis` | `number` (`int64`) | Initial key generation timestamp |
| `rotatedAtMillis` | `number` (`int64`) | Last key re-wrap/rotation timestamp |

---

### 3. `users/{uid}/syncSignal/current`
Lightweight non-sensitive synchronization notification document.

| Field Name | Type | Description |
|---|---|---|
| `seq` | `number` (`int`) | Latest sequence number allocated across all user records |
| `updatedAtMillis` | `number` (`int64`) | Timestamp signal was updated |

---

### 4. `phoneIndex/{phoneHmac}` & `emailIndex/{emailHmac}`
Unique atomic indexes for user discovery and account uniqueness.

| Field Name | Type | Description |
|---|---|---|
| Document ID | `string` | HMAC-SHA256 of normalized phone/email keyed with server pepper |
| `uid` | `string` | Associated user UID |
| `createdAtMillis` | `number` (`int64`) | Registration timestamp |

---

### 5. `otpChallenges/{challengeId}`
Ephemeral verification challenges for sign-in, sign-up, and step-up auth.

| Field Name | Type | Description |
|---|---|---|
| `flowId` | `string` | Flow identifier |
| `flowType` | `string` | `SIGNIN`, `SIGNUP_PHONE`, `SIGNUP_EMAIL`, `STEP_UP` |
| `destinationHash` | `string` | Peppered HMAC of target phone/email |
| `codeHash` | `string` | HMAC-SHA256 of 6-digit verification code |
| `attempts` | `number` (`int`) | Failed verification attempts (lockout at 5) |
| `maxAttempts` | `number` (`int`) | Maximum attempts allowed (5) |
| `createdAtMillis` | `number` (`int64`) | Challenge creation timestamp |
| `expiresAtMillis` | `number` (`int64`) | Expiration epoch ms (10-minute TTL policy) |
| `resendCooldownUntil` | `number` (`int64`) | Cooldown epoch ms (30 seconds) |

---

### 6. `rateLimits/{targetId}`
Distributed rate-limiting buckets with TTL eviction.

| Field Name | Type | Description |
|---|---|---|
| Document ID | `string` | Target identifier (`ip:<hash>` or `dest:<hash>`) |
| `count` | `number` (`int`) | Request count within window |
| `windowStartMillis` | `number` (`int64`) | Window start timestamp |
| `expiresAtMillis` | `number` (`int64`) | Expiration timestamp (10-minute TTL) |

---

### 7. `globalCounters/otpBudget`
Centralized daily OTP messaging quota tracking.

| Field Name | Type | Description |
|---|---|---|
| `date` | `string` | Current date in `YYYY-MM-DD` |
| `count` | `number` (`int`) | Cumulative codes sent today |
| `dailyLimit` | `number` (`int`) | Maximum daily quota (e.g. 500) |
| `alert50Sent` | `boolean` | 50% budget threshold alert flag |
| `alert80Sent` | `boolean` | 80% budget threshold alert flag |
| `alert100Sent` | `boolean` | 100% budget threshold alert flag |
| `emergencyKillSwitch` | `boolean` | Emergency kill switch flag |

---

### 8. `idempotencyKeys/{key}`
Request deduplication table for state-mutating operations.

| Field Name | Type | Description |
|---|---|---|
| Document ID | `string` | Client-provided idempotency key UUID |
| `statusCode` | `number` (`int`) | Cached HTTP status code |
| `responseBody` | `map` | Cached JSON response payload |
| `createdAtMillis` | `number` (`int64`) | Request creation timestamp |
| `expiresAtMillis` | `number` (`int64`) | TTL eviction timestamp (24 hours) |

---

### 9. `sessions/{sessionId}`
Active user sessions and connected devices for device management and revocation.

| Field Name | Type | Description |
|---|---|---|
| `sessionId` | `string` | Unique session UUID |
| `uid` | `string` | Associated user UID |
| `deviceId` | `string` | Client hardware/browser device UUID |
| `userAgent` | `string` | Client user-agent string |
| `ipHash` | `string` | Salted hash of client IP address |
| `createdAtMillis` | `number` (`int64`) | Session creation timestamp |
| `expiresAtMillis` | `number` (`int64`) | Session expiration timestamp (14 days) |
| `revoked` | `boolean` | Revocation status flag |
