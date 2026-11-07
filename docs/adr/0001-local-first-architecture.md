# ADR-0001: Local-First Architecture Across Android and Web

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Backend Lead, Web Lead, Android Lead, Security Lead

---

## 1. Context and Problem Statement

Udhaar started as an offline-first Android application managing personal credit and debt (bahi khata) using SQLCipher and Room. Users in India and Nepal operate in variable network conditions (subways, rural markets, high-congestion 4G/3G towers) and demand instantaneous UI interactions without loading spinners or network roundtrips.

With Udhaar Platform v2 expanding to include a comprehensive Progressive Web Application (`app.udhaar.jyotirmoyb.com`), we must define whether the web and mobile apps act as thin API clients or autonomous local-first replicas.

## 2. Decision Drivers

- **Zero-Latency Interactions**: Recording a payment, adding a transaction, or browsing past records must never block on HTTP round-trips.
- **Offline Autonomy**: Full read and write capabilities must be preserved even when disconnected for days.
- **Data Survivability**: Data written while offline must never be lost due to tab closure, battery exhaustion, or intermittent connectivity.
- **Consistency**: Both clients (Android Room and Web IndexedDB) must converge deterministically to identical states when synchronizing.

## 3. Considered Options

1. **Thin Client Architecture**: Web app fetches from REST/GraphQL APIs on demand; Android app uses Room only as a read-through cache.
2. **Local-First Everywhere (Selected)**: The Android SQLite/SQLCipher database and the browser IndexedDB database are each complete, fully functional local replicas. The server acts as a synchronization hub, validation engine, notification relay, and counterparty sharing coordinator.

## 4. Decision Outcome

Chosen Option: **Local-First Everywhere**.

### Technical Details

1. **Client Storage**:
   - **Android**: Room v12/v13 over SQLCipher (encrypted database file).
   - **Web App**: IndexedDB (`udhaar_ledger_db`) managed via Dexie/native IDB wrappers with an application-level AES-GCM-256 session key.
2. **Write Flow**:
   - All user mutations (Create Record, Edit, Settle, Partial Payment, Installment) write immediately to the local replica database.
   - Concurrently, a mutation entry is appended to a local `sync_outbox` table/store with a UUIDv7 `syncId`, client revision counter, and Hybrid Logical Clock (`hlc`).
   - The UI updates optimistically in $<16\text{ms}$.
3. **Synchronization Hub**:
   - A background sync worker (WorkManager on Android; Service Worker Background Sync & fetch on Web) batches queued mutations and transmits them to `POST /api/v1/sync/push`.
   - The server validates authorization, evaluates quotas, runs field-level envelope encryption, updates Firestore collections, and assigns a monotonically increasing server sequence number (`seq`).
   - Clients pull down server changes via `GET /api/v1/sync/pull?cursor={seq}`.
4. **Conflict Convergence**:
   - Payments and audit log entries are append-only and merge by mathematical set union.
   - Non-critical metadata uses Last-Writer-Wins governed by Hybrid Logical Clocks (`hlc`).
   - Financial terms (principal amount, interest rate, due date, installment plan) trigger a explicit `CONFLICT` state if modified concurrently or if the record was previously verified by the counterparty, surfacing a side-by-side reconciliation choice to the user rather than silently overwriting.

## 5. Consequences

### Positive
- Instant UI responsiveness regardless of connectivity.
- The web app works completely offline as an installable PWA.
- Server downtime does not prevent users from accessing their financial ledgers or adding records.
- Network usage is reduced to delta synchronization payloads.

### Negative / Tradeoffs
- Complexity of handling conflict resolution, schema migrations, and outbox state machines across two distinct platforms (Kotlin and TypeScript).
- Requires a shared conformance test suite (`sync-conformance/`) to prove both engines converge identically under arbitrary interleaving.
