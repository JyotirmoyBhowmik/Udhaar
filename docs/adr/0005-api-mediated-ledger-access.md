# ADR-0005: API-Mediated Ledger Access & Direct Client Deny Rules

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Security Lead, Backend Lead, Android Lead

---

## 1. Context and Problem Statement

Firebase applications commonly allow direct client access to Cloud Firestore collections using client SDKs (`firebase/firestore`), relying on Firestore Security Rules to enforce authorization.

However, in Udhaar Platform v2:
1. Every write mutation requires field-level envelope encryption using Cloud KMS.
2. Every mutation must generate an immutable audit log entry in `auditLogs/`.
3. Every write must enforce user tier quotas (e.g. maximum 10,000 active records, 50MB attachments) and execute rate limiting.
4. Business logic validation (e.g. integer paise boundaries, valid phone formats, conflict resolution) cannot be completely expressed or verified in declarative Firestore rules without introducing severe rule complexity, execution costs, and security bypass risks.

## 2. Decision Drivers

- **Zero Direct Client Writes to Ledger**: Clients must never directly modify Firestore ledger documents (`users/{uid}/records`, `.../people`, `.../events`, `.../plans`).
- **Enforced Audit Trail**: Every modification must pass through the backend pipeline that records actor identity, IP hash, correlation ID, timestamp, and cryptographic digest.
- **Server-Side Encryption**: Field-level encryption with Cloud KMS must occur before data reaches Firestore storage.
- **Real-Time Reactivity**: Clients still need to know when counterparties confirm records or when data changes on other devices.

## 3. Decision Outcome

Chosen Option: **All Ledger Reads and Writes Mediated via Functions API with Direct Client Deny Rules in Firestore**.

### 1. Firestore Security Rules Policy

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // 1. Ledger collections: Strictly DENIED to all direct client SDK access
    match /users/{userId}/records/{document=**} {
      allow read, write: if false;
    }
    match /users/{userId}/people/{document=**} {
      allow read, write: if false;
    }
    match /users/{userId}/events/{document=**} {
      allow read, write: if false;
    }
    match /users/{userId}/plans/{document=**} {
      allow read, write: if false;
    }
    match /users/{userId}/attachments/{document=**} {
      allow read, write: if false;
    }
    match /users/{userId}/changeLog/{document=**} {
      allow read, write: if false;
    }
    match /auditLogs/{document=**} {
      allow read, write: if false;
    }
    match /phoneIndex/{document=**} {
      allow read, write: if false;
    }
    match /emailIndex/{document=**} {
      allow read, write: if false;
    }
    match /otpChallenges/{document=**} {
      allow read, write: if false;
    }

    // 2. Real-time Sync Signal: Light, non-sensitive document readable by owner
    match /users/{userId}/syncSignal/current {
      allow read: if request.auth != null && request.auth.uid == userId;
      allow write: if false; // Server only
    }

    // 3. Existing shared records & guest collections (backward compatibility)
    match /shared_records/{recordId} {
      allow read: if request.auth != null && (
        resource.data.creatorUid == request.auth.uid ||
        resource.data.counterpartyUid == request.auth.uid
      );
      allow write: if false; // Updates via Cloud Functions API only
    }
  }
}
```

### 2. Real-Time Synchronization Signaling
- When a server mutation completes, the Cloud Function updates a lightweight document: `users/{userId}/syncSignal/current` containing:
  ```json
  {
    "lastSeq": 412,
    "updatedAt": 1762498200000,
    "hint": "RECORD_UPDATED"
  }
  ```
- No PII, names, or amounts are present in `syncSignal`.
- The client listens to this single document via standard Firestore snapshot listener, or receives an FCM Web Push / Background Sync notification.
- Upon receiving the signal, the client triggers `GET /api/v1/sync/pull?cursor={seq}` over the authenticated HTTPS API to pull encrypted, authorized delta updates.

## 4. Consequences

### Positive
- Prevents database tampering, malicious schema injections, and quota circumvention.
- Guarantees 100% of mutations are audited, encrypted, and validated.
- Lowers Firestore read billing costs by eliminating expensive document listeners across large collections.

### Negative / Tradeoffs
- Direct client SDK real-time queries (e.g. `collection().onSnapshot()`) cannot be used for ledger data; clients must rely on `syncSignal` plus `sync/pull` REST endpoints.
