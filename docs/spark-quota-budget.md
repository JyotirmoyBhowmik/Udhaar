# Udhaar Spark Mode — Quota Budget & Capacity Model

## 1. Executive Summary
Udhaar Spark Mode runs 100% serverless on the Google Firebase Spark (free) plan with zero billing account required. To ensure stability and avoid service degradation, the platform operates under Google's documented Firebase Spark Plan quotas:
- **Cloud Firestore Document Reads**: **50,000 reads / day**
- **Cloud Firestore Document Writes**: **20,000 writes / day**
- **Cloud Firestore Document Deletes**: **20,000 deletes / day**
- **Cloud Firestore Storage**: **1 GiB total stored data**
- **Firebase Hosting Bandwidth**: **10 GB / month (360 MB / day)**
- **Firebase Auth Phone SMS**: Strictly disabled / unverified; Email/Password & Google Sign-In are **unlimited and free**.

---

## 2. Daily Usage Modeling (Per Active User)

An active user (DAU) typically performs the following actions on any given day:
1. **App Launch & Catch-Up Sync**: Reads changed records since last local sync timestamp. Due to Firestore offline caching and IndexedDB / SQLite local-first storage, clean cache hits produce **0 network reads**. Only modified documents since `lastSyncedAtMs` are transferred.
   - *Estimated Reads*: 2 to 10 document reads per session.
2. **Recording Transactions / Payments**:
   - Creating a transaction: 1 write to `records/{recordId}` + 1 write to `events/{eventId}` = 2 writes.
   - Adding or editing a contact: 1 write to `people/{personId}`.
   - Updating an existing transaction / partial payment: 1 write to `records/{recordId}` + 1 write to `events/{eventId}` = 2 writes.
   - Average transactions per active lender/borrower per day: 1 to 4 transactions.
   - *Estimated Writes*: 3 to 10 document writes per day.
3. **Guest Capability Links (/u/)**:
   - Creating a guest link: 1 write to `guestLinks/{linkId}`.
   - Guest opens link: 1 read of `guestLinks/{linkId}`.
   - Guest submits proposal (confirm, claim, dispute, or date): 1 write to `guestLinks/{linkId}/actions/{actionId}`.
   - Lender checks proposals (collection-group query): 1 to 3 reads per check.
   - *Estimated Writes*: 2 writes per link shared.
   - *Estimated Reads*: 2 to 4 reads per link shared.

---

## 3. Scale Capacity Projections

| Metric | 10 DAU | 100 DAU | 1,000 DAU | Spark Plan Daily Limit |
| :--- | :--- | :--- | :--- | :--- |
| **Daily Document Reads** | ~100 – 200 | ~1,000 – 3,000 | ~15,000 – 35,000 | **50,000** |
| **% of Daily Read Quota** | **< 0.5%** | **2% – 6%** | **30% – 70%** | 100% |
| **Daily Document Writes** | ~50 – 100 | ~500 – 1,200 | ~5,000 – 12,000 | **20,000** |
| **% of Daily Write Quota**| **< 0.5%** | **2.5% – 6%** | **25% – 60%** | 100% |
| **Firestore Storage Growth**| ~100 KB / day | ~1 MB / day | ~10 MB / day | **1,024 MB (1 GiB)** |
| **Days to 1 GiB Storage** | > 10,000 days | > 1,000 days | ~100 days (requires tombstone pruning) | N/A |
| **Hosting Transfer** | ~15 MB / day | ~150 MB / day | ~1.5 GB / day (needs CDN caching) | **360 MB / day** |

---

## 4. Quota Protection Invariants & Guardrails

To strictly prevent runaway consumption and quota exhaustion:

1. **Client-Side Write Rate Throttling**:
   - Enforce a client-side write limiter capping total mutations to **500 writes / day per user**.
   - Configured via Firebase Remote Config: `max_client_daily_writes = 500`.

2. **Offline-First Cache Invariant**:
   - SQLite (SQLCipher) on Android and IndexedDB on Web remain the primary source of truth.
   - Web app enables Firestore persistence (`enableIndexedDbPersistence()`).
   - Never perform unbounded `collection.get()` queries on app startup. All sync operations pass a `where("updatedAtMs", ">", lastSyncedAtMs)` cursor filter.

3. **Fixed Action IDs on Guest Capability Subcollections**:
   - Fixed IDs prevent guests or malicious bots from flooding Firestore with infinite subdocuments:
     - `confirm` (single document)
     - `dispute` (single document)
     - `claim_1` through `claim_5` (maximum 5 payment claims per link)
     - `date_1` through `date_3` (maximum 3 date proposals per link)
   - Enforced by `firestore.rules`.

4. **Zero Cloud Storage Uploads**:
   - Photo uploads to Google Cloud Storage are disabled in Spark Mode. Attachments are referenced as local URIs or cryptographic hashes, completely eliminating Cloud Storage bandwidth and GB-hour costs.

5. **Quota Exhaustion Runbook (Graceful Degradation)**:
   - When Firestore returns `RESOURCE_EXHAUSTED` (HTTP 429 / code `resource-exhausted`):
     - The client catches the error and surfaces a friendly notification:
       *"Cloud sync is resting until tomorrow due to free tier daily quota limits. All your records are safely saved on your device and will sync automatically when the quota resets at 12:00 AM PST."*
     - The application continues to function in full offline mode (creating, updating, and viewing records in Room SQLite / IndexedDB) without data loss.
