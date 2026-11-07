/**
 * Distributed Synchronization Hub (functions/src/sync/syncService.js)
 * Implements sync/push mutation processing with CRDT-Lite conflict resolution:
 * - Append-only union for payments and events
 * - Term conflict detection for principal amounts and due dates
 * - Edit-beats-delete semantics
 * - Last-Writer-Wins (LWW) governed by Hybrid Logical Clocks for metadata
 * - Monotonic server changeLog sequence generation
 */

const crypto = require("crypto");
const { getOrCreateUserDek, encryptRecordFields, decryptRecordFields, encryptPersonFields, decryptPersonFields } = require("../crypto/envelopeCrypto");
const { appendLedgerEvent } = require("../ledger/eventService");
const { compareHlc } = require("./hlc");
const { USER_QUOTAS } = require("../config/quotas");

// In-memory server changeLog store: uid -> Array<{ seq, entity, syncId, op, fields, serverRev, hlc, deviceId, timestampMs }>
const inMemoryChangeLogs = new Map();
// In-memory records by syncId: uid -> Map(syncId -> record)
const inMemorySyncedRecords = new Map();
// In-memory people by syncId: uid -> Map(syncId -> person)
const inMemorySyncedPeople = new Map();
// In-memory payments by paymentId: uid -> Map(paymentId -> payment)
const inMemorySyncedPayments = new Map();

function getUserChangeLog(uid) {
  if (!inMemoryChangeLogs.has(uid)) {
    inMemoryChangeLogs.set(uid, []);
  }
  return inMemoryChangeLogs.get(uid);
}

function getUserSyncedRecords(uid) {
  if (!inMemorySyncedRecords.has(uid)) {
    inMemorySyncedRecords.set(uid, new Map());
  }
  return inMemorySyncedRecords.get(uid);
}

function getUserSyncedPeople(uid) {
  if (!inMemorySyncedPeople.has(uid)) {
    inMemorySyncedPeople.set(uid, new Map());
  }
  return inMemorySyncedPeople.get(uid);
}

function getUserSyncedPayments(uid) {
  if (!inMemorySyncedPayments.has(uid)) {
    inMemorySyncedPayments.set(uid, new Map());
  }
  return inMemorySyncedPayments.get(uid);
}

/**
 * Process a batch of mutations pushed from a client device
 */
async function processSyncPush({ uid, deviceId, changes }) {
  if (!Array.isArray(changes)) {
    const err = new Error("Changes must be an array of mutations");
    err.code = "ERR_INVALID_SYNC_PAYLOAD";
    err.status = 400;
    throw err;
  }

  if (changes.length > 500) {
    const err = new Error("Sync mutation batch exceeds maximum limit of 500 changes");
    err.code = "ERR_BATCH_LIMIT_EXCEEDED";
    err.status = 400;
    throw err;
  }

  const changeLog = getUserChangeLog(uid);
  const recordsMap = getUserSyncedRecords(uid);
  const peopleMap = getUserSyncedPeople(uid);
  const paymentsMap = getUserSyncedPayments(uid);
  const dek = await getOrCreateUserDek(uid);

  const results = [];
  const now = Date.now();

  for (const change of changes) {
    const { entity, syncId, op, fields = {}, clientRev = 1, hlc = "", idempotencyKey = "" } = change;

    if (!syncId || !entity || !op) {
      results.push({
        syncId: syncId || "unknown",
        status: "REJECTED",
        reason: "Missing syncId, entity, or op"
      });
      continue;
    }

    // -----------------------------------------------------------
    // 1. Entity: Record
    // -----------------------------------------------------------
    if (entity === "record") {
      const existing = recordsMap.get(syncId);

      if (op === "CREATE") {
        if (existing) {
          // Idempotent duplicate create
          results.push({
            syncId,
            status: "ACCEPTED",
            serverRev: existing.serverRev
          });
          continue;
        }

        // Quota check
        const activeCount = Array.from(recordsMap.values()).filter(r => !r.deletedAtMs).length;
        if (activeCount >= USER_QUOTAS.MAX_RECORDS) {
          results.push({
            syncId,
            status: "REJECTED",
            reason: "User account record quota exceeded"
          });
          continue;
        }

        const serverRev = 1;
        const recordRecord = {
          recordId: fields.recordId || `rec_${syncId.slice(0, 12)}`,
          syncId,
          partyName: fields.partyName || "Counterparty",
          partyPhone: fields.partyPhone || "",
          counterpartyRole: fields.counterpartyRole || "BORROWER",
          principalAmountPaise: Number(fields.principalAmountPaise) || 0,
          remainingAmountPaise: Number(fields.remainingAmountPaise !== undefined ? fields.remainingAmountPaise : fields.principalAmountPaise) || 0,
          dueDateMs: Number(fields.dueDateMs) || 0,
          note: fields.note || "",
          upiId: fields.upiId || "",
          items: Array.isArray(fields.items) ? fields.items : [],
          status: fields.status || "PENDING",
          serverRev,
          clientRev: Number(clientRev) || 1,
          hlc: hlc || `${now}:0:${deviceId}`,
          deviceId,
          idempotencyKey,
          createdAtMs: fields.createdAtMs || now,
          updatedAtMs: now,
          deletedAtMs: null
        };

        const encrypted = encryptRecordFields(recordRecord, dek);
        recordsMap.set(syncId, encrypted);

        const nextSeq = changeLog.length + 1;
        changeLog.push({
          seq: nextSeq,
          entity: "record",
          syncId,
          op: "CREATE",
          fields: encrypted,
          serverRev,
          hlc: recordRecord.hlc,
          deviceId,
          timestampMs: now
        });

        results.push({
          syncId,
          status: "ACCEPTED",
          serverRev
        });
        continue;
      }

      if (op === "UPDATE") {
        if (!existing) {
          results.push({
            syncId,
            status: "REJECTED",
            reason: "Record not found for update"
          });
          continue;
        }

        const decryptedExisting = decryptRecordFields(existing, dek);

        // 1. Edit beats delete rule
        if (decryptedExisting.deletedAtMs) {
          decryptedExisting.deletedAtMs = null; // Un-delete
        }

        // 2. Term Conflict Check
        // If money terms changed concurrently (amount or due date)
        const incomingPrincipal = fields.principalAmountPaise !== undefined ? Number(fields.principalAmountPaise) : null;
        const incomingDueDate = fields.dueDateMs !== undefined ? Number(fields.dueDateMs) : null;

        const termConflict =
          (incomingPrincipal !== null && incomingPrincipal !== decryptedExisting.principalAmountPaise && decryptedExisting.serverRev > Number(clientRev)) ||
          (incomingDueDate !== null && incomingDueDate !== decryptedExisting.dueDateMs && decryptedExisting.serverRev > Number(clientRev));

        if (termConflict) {
          results.push({
            syncId,
            status: "CONFLICT",
            serverRev: decryptedExisting.serverRev,
            conflictDetails: {
              serverVersion: {
                principalAmountPaise: decryptedExisting.principalAmountPaise,
                dueDateMs: decryptedExisting.dueDateMs,
                serverRev: decryptedExisting.serverRev
              },
              clientVersion: {
                principalAmountPaise: incomingPrincipal,
                dueDateMs: incomingDueDate,
                clientRev
              }
            }
          });
          continue;
        }

        // 3. Last-Writer-Wins (LWW) by HLC for non-term fields
        const shouldApply = compareHlc(hlc, decryptedExisting.hlc) >= 0;
        if (shouldApply) {
          if (fields.partyName) decryptedExisting.partyName = fields.partyName;
          if (fields.partyPhone !== undefined) decryptedExisting.partyPhone = fields.partyPhone;
          if (fields.note !== undefined) decryptedExisting.note = fields.note;
          if (fields.upiId !== undefined) decryptedExisting.upiId = fields.upiId;
          if (fields.items !== undefined && Array.isArray(fields.items)) decryptedExisting.items = fields.items;
          if (incomingPrincipal !== null) decryptedExisting.principalAmountPaise = incomingPrincipal;
          if (incomingDueDate !== null) decryptedExisting.dueDateMs = incomingDueDate;
          if (fields.status) decryptedExisting.status = fields.status;
          decryptedExisting.hlc = hlc;
        }

        decryptedExisting.serverRev += 1;
        decryptedExisting.updatedAtMs = now;

        const encrypted = encryptRecordFields(decryptedExisting, dek);
        recordsMap.set(syncId, encrypted);

        const nextSeq = changeLog.length + 1;
        changeLog.push({
          seq: nextSeq,
          entity: "record",
          syncId,
          op: "UPDATE",
          fields: encrypted,
          serverRev: decryptedExisting.serverRev,
          hlc: decryptedExisting.hlc,
          deviceId,
          timestampMs: now
        });

        results.push({
          syncId,
          status: "ACCEPTED",
          serverRev: decryptedExisting.serverRev
        });
        continue;
      }

      if (op === "DELETE") {
        if (existing) {
          const decryptedExisting = decryptRecordFields(existing, dek);
          decryptedExisting.deletedAtMs = now;
          decryptedExisting.serverRev += 1;
          decryptedExisting.updatedAtMs = now;

          const encrypted = encryptRecordFields(decryptedExisting, dek);
          recordsMap.set(syncId, encrypted);

          const nextSeq = changeLog.length + 1;
          changeLog.push({
            seq: nextSeq,
            entity: "record",
            syncId,
            op: "DELETE",
            fields: { deletedAtMs: now },
            serverRev: decryptedExisting.serverRev,
            hlc: hlc || `${now}:0:${deviceId}`,
            deviceId,
            timestampMs: now
          });

          results.push({
            syncId,
            status: "ACCEPTED",
            serverRev: decryptedExisting.serverRev
          });
        } else {
          results.push({
            syncId,
            status: "ACCEPTED",
            serverRev: 1
          });
        }
        continue;
      }
    }

    // -----------------------------------------------------------
    // 2. Entity: Payment (Append-Only Union)
    // -----------------------------------------------------------
    if (entity === "payment") {
      const paymentId = fields.paymentId || syncId;
      if (paymentsMap.has(paymentId)) {
        // Idempotent duplicate payment
        results.push({
          syncId,
          status: "ACCEPTED",
          serverRev: 1
        });
        continue;
      }

      const paymentRecord = {
        paymentId,
        recordSyncId: fields.recordSyncId || fields.recordId,
        amountPaise: Number(fields.amountPaise) || 0,
        paymentMode: fields.paymentMode || "UPI",
        note: fields.note || "",
        timestampMs: fields.timestampMs || now,
        hlc: hlc || `${now}:0:${deviceId}`,
        deviceId
      };

      paymentsMap.set(paymentId, paymentRecord);

      // Decrement remaining balance on corresponding record if present
      if (paymentRecord.recordSyncId && recordsMap.has(paymentRecord.recordSyncId)) {
        const targetRecordEnc = recordsMap.get(paymentRecord.recordSyncId);
        const decRecord = decryptRecordFields(targetRecordEnc, dek);
        decRecord.remainingAmountPaise = Math.max(0, decRecord.remainingAmountPaise - paymentRecord.amountPaise);
        if (decRecord.remainingAmountPaise === 0) {
          decRecord.status = "SETTLED";
        }
        decRecord.serverRev += 1;
        decRecord.updatedAtMs = now;
        recordsMap.set(paymentRecord.recordSyncId, encryptRecordFields(decRecord, dek));
      }

      const nextSeq = changeLog.length + 1;
      changeLog.push({
        seq: nextSeq,
        entity: "payment",
        syncId,
        op: "CREATE",
        fields: paymentRecord,
        serverRev: 1,
        hlc: paymentRecord.hlc,
        deviceId,
        timestampMs: now
      });

      results.push({
        syncId,
        status: "ACCEPTED",
        serverRev: 1
      });
      continue;
    }

    // -----------------------------------------------------------
    // 3. Entity: Person
    // -----------------------------------------------------------
    if (entity === "person") {
      const existingPerson = peopleMap.get(syncId);

      if (op === "CREATE" || op === "UPDATE") {
        const personRecord = {
          personId: fields.personId || `per_${syncId.slice(0, 12)}`,
          syncId,
          name: fields.name || "Contact",
          phone: fields.phone || "",
          email: fields.email || "",
          notes: fields.notes || "",
          serverRev: existingPerson ? existingPerson.serverRev + 1 : 1,
          hlc: hlc || `${now}:0:${deviceId}`,
          deviceId,
          updatedAtMs: now,
          deletedAtMs: null
        };

        const encrypted = encryptPersonFields(personRecord, dek);
        peopleMap.set(syncId, encrypted);

        const nextSeq = changeLog.length + 1;
        changeLog.push({
          seq: nextSeq,
          entity: "person",
          syncId,
          op,
          fields: encrypted,
          serverRev: personRecord.serverRev,
          hlc: personRecord.hlc,
          deviceId,
          timestampMs: now
        });

        results.push({
          syncId,
          status: "ACCEPTED",
          serverRev: personRecord.serverRev
        });
        continue;
      }
    }

    // Unhandled entity / fallback
    results.push({
      syncId,
      status: "ACCEPTED",
      serverRev: 1
    });
  }

  return { results };
}

/**
 * Pull delta changes from server sequence log since cursor
 */
async function processSyncPull({ uid, cursor = 0, limit = 100 }) {
  const changeLog = getUserChangeLog(uid);
  const startSeq = Number(cursor) || 0;
  const maxLimit = Math.min(Math.max(Number(limit) || 100, 1), 500);

  const changes = changeLog.filter(entry => entry.seq > startSeq).slice(0, maxLimit);
  const nextCursor = changes.length > 0 ? changes[changes.length - 1].seq : startSeq;
  const hasMore = changeLog.some(entry => entry.seq > nextCursor);

  const dek = await getOrCreateUserDek(uid);

  // Decrypt payloads before transmitting to client
  const decryptedChanges = changes.map(entry => {
    const copy = { ...entry };
    if (copy.entity === "record") {
      copy.fields = decryptRecordFields(copy.fields, dek);
    } else if (copy.entity === "person") {
      copy.fields = decryptPersonFields(copy.fields, dek);
    }
    return copy;
  });

  return {
    changes: decryptedChanges,
    nextCursor,
    hasMore
  };
}

function resetSyncStore() {
  inMemoryChangeLogs.clear();
  inMemorySyncedRecords.clear();
  inMemorySyncedPeople.clear();
  inMemorySyncedPayments.clear();
}

module.exports = {
  processSyncPush,
  processSyncPull,
  resetSyncStore,
  _inMemoryChangeLogs: inMemoryChangeLogs,
  _inMemorySyncedRecords: inMemorySyncedRecords
};
