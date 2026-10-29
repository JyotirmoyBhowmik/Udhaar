# Udhaar Phase 3 — Two-Party Verification Security Specification

## 1. Data Invariants & Collection Architecture

1. **`/user_profiles/{userId}` (`UserProfile`)**:
   - Stores a user's opt-in cloud verification & payment profile (`userId`, `displayName`, `phoneNumber`, `upiId`, `photoUri`, `createdAt`, `updatedAt`).
   - **PII Isolation Invariant**: Contains phone number and UPI ID. Strictly isolated to the owner (`request.auth.uid == userId`). Blanket signed-in reads are forbidden.

2. **`/shared_records/{remoteId}` (`SharedVerificationRecord`)**:
   - Stores minimal two-party Udhaar verification state: `remoteId`, `creatorUid`, `counterpartyUid`, `participantUids` (bounded list of 1–2 UIDs), `lenderName`, `borrowerName`, `creatorRole` (`GIVEN`/`TAKEN`), `currency` (`INR`/`NPR`), `principalAmount`, `amountPaid`, `paymentMode`, `isInterestEnabled`, `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`, `startDateMillis`, `expiryDateMillis`, `contentHash` (64-char SHA-256 hex), `verificationStatus` (`PENDING`, `CONFIRMED`, `DISPUTED`, `EDITED_AFTER_CONFIRM`), `verifiedAtMillis`, `disputeReason`, `encryptedNotes` (AES-GCM E2EE ciphertext), `attachmentRefs` (bounded list of <= 5 E2EE strings), `isSettled`, `createdAt`, `updatedAt`.
   - **Two-Party Access Invariant**: Only `creatorUid` and `counterpartyUid` (`request.auth.uid in resource.data.participantUids`) can `get` or `list` the record.
   - **Immutability Invariant**: `remoteId`, `creatorUid`, and `createdAt` can never be mutated after creation.

3. **`/shared_records/{remoteId}/partial_payments/{paymentId}` (`SharedPartialPayment`)**:
   - Stores partial repayment records and counterparty acknowledgements (`paymentId`, `remoteId`, `recordedByUid`, `recordedByName`, `participantUids`, `amount`, `paymentMode`, `paidAtMillis`, `encryptedNote`, `isAcknowledged`, `acknowledgedByUid`, `acknowledgedAtMillis`, `createdAt`, `updatedAt`).
   - **Two-Party Access Invariant**: Only participants (`request.auth.uid in resource.data.participantUids`) can read, create, or acknowledge partial payments.

4. **`/shared_records/{remoteId}/audit_trail/{eventId}` (`SharedAuditEvent`)**:
   - Immutable append-only audit timeline (`CREATED`, `SHARED`, `VIEWED`, `CONFIRMED`, `DISPUTED`, `EDITED`, `PAYMENT_RECORDED`, `PAYMENT_ACKNOWLEDGED`, `SETTLED`).
   - **Append-Only Invariant**: `allow update: if false;`. No audit event can ever be modified after creation.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Read on `/shared_records`**: `{ "auth": null, "op": "get", "path": "/shared_records/rec_1" }` -> Rejected.
2. **Third-Party Read on `/shared_records` (Mallory not in `participantUids`)**: `{ "auth": "mallory", "op": "get", "path": "/shared_records/rec_1" }` -> Rejected.
3. **Unfiltered List Query on `/shared_records`**: `{ "auth": "alice", "op": "list", "query": "collection('shared_records')" }` -> Rejected (rules are not filters).
4. **Cross-User Profile PII Read on `/user_profiles/{bobUid}`**: `{ "auth": "alice", "op": "get", "path": "/user_profiles/bob_456" }` -> Rejected.
5. **Identity Spoofing on Record Creation (`creatorUid != request.auth.uid`)**: `{ "creatorUid": "bob_456", "participantUids": ["bob_456"] }` by `alice_123` -> Rejected.
6. **Shadow Field Injection (`isAdmin: true` ghost key)**: Adding undeclared keys to `/shared_records/{remoteId}` -> Rejected by `.keys().hasOnly(...)`.
7. **Mutating Immutable `creatorUid` or `createdAt` on Update**: Attempting to overwrite `creatorUid` during update -> Rejected.
8. **Negative Principal Amount (`principalAmount: -500`)**: -> Rejected by `isValidSharedRecord`.
9. **Invalid Content Hash Length (`contentHash: "short"`)**: -> Rejected by `contentHash.size() == 64`.
10. **Oversized Encrypted Notes DoS (`encryptedNotes` > 2048 chars)**: -> Rejected by `.size() <= 2048`.
11. **Mutating an Audit Trail Entry (`update` on `/audit_trail/{eventId}`)**: -> Rejected (`allow update: if false`).
12. **Hijacking an Already-Claimed Counterparty Slot**: Third party attempting to overwrite a non-empty `counterpartyUid` -> Rejected.
