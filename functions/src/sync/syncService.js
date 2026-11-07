/**
 * CRDT-Lite Sync Engine & Conflict Reconciliation Service (functions/src/sync/syncService.js)
 * Implements:
 * - POST /api/v1/sync/push (idempotent change processing, LWW vs FINANCIAL_CONFLICT, set union payments, Edit-Beats-Delete)
 * - GET /api/v1/sync/pull (cursor-based sequence feed)
 * - Monotonic sequence number generation and syncSignal updates inside Firestore transactions.
 * - Strictly backed by async SyncRepository methods with zero Proxy facades or no-op fallbacks.
 */

const { createHlc, incrementHlc, updateHlc, compareHlc, packHlc } = require("./hlc");
const { getSyncRepository, assertRepoMethod } = require("../repositories");

const FINANCIAL_FIELDS = [
  "principalAmountPaise",
  "counterpartyRole",
  "interestType",
  "interestRateBps",
  "interestPeriod",
  "compoundFrequency",
  "startDateMs"
];

const NON_FINANCIAL_FIELDS = [
  "partyName",
  "partyPhone",
  "note",
  "dueDateMs",
  "reminderDateMs",
  "remindersDisabled",
  "items",
  "upiId",
  "status"
];

const REQUIRED_SYNC_METHODS = [
  "runTransaction",
  "allocateSequenceNumber",
  "appendChangeLog",
  "pullChangeLog",
  "touchSyncSignal",
  "getSyncSignal",
  "getSyncedRecord",
  "saveSyncedRecord",
  "listSyncedRecords",
  "getSyncedPerson",
  "saveSyncedPerson",
  "getSyncedPayment",
  "saveSyncedPayment"
];

function getRequiredSyncRepo() {
  const repo = getSyncRepository();
  for (const method of REQUIRED_SYNC_METHODS) {
    assertRepoMethod(repo, method, "SyncRepository");
  }
  return repo;
}

/**
 * Push batch of outbox changes from a client device.
 * Every mutation executes inside a repository transaction using only async repository methods.
 */
async function processSyncPush({ uid, deviceId = "server", changes }) {
  if (!uid) {
    const err = new Error("User ID is required for sync push");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (!Array.isArray(changes)) {
    const err = new Error("changes must be an array");
    err.code = "ERR_INVALID_SYNC_PAYLOAD";
    err.status = 400;
    throw err;
  }

  const repo = getRequiredSyncRepo();
  const now = Date.now();
  let serverHlc = createHlc("server", now);

  const applied = [];
  const conflicts = [];
  const results = [];

  await repo.runTransaction(async (tx) => {
    for (const change of changes) {
      const {
        entity = "record",
        syncId,
        op,
        fields = {},
        fieldHlcs = {},
        baseRev,
        clientRev,
        hlc: clientHlcStr,
        idempotencyKey
      } = change;

      if (!syncId || !op) {
        continue;
      }

      if (clientHlcStr) {
        serverHlc = updateHlc(serverHlc, clientHlcStr, Date.now());
      } else {
        serverHlc = incrementHlc(serverHlc, Date.now());
      }
      const changeHlcStr = packHlc(serverHlc);
      const effectiveHlcStr = clientHlcStr || fields.hlc || changeHlcStr;
      const effectiveBaseRev =
        baseRev !== undefined
          ? Number(baseRev)
          : clientRev !== undefined
          ? Number(clientRev)
          : fields.clientRevision !== undefined
          ? Number(fields.clientRevision)
          : 1;

      // =========================================================================
      // 1. ENTITY: PAYMENT (Idempotent Set Union by paymentId)
      // =========================================================================
      if (entity === "payment" || op === "APPLY_PARTIAL_PAYMENT" || op === "INSERT_PAYMENT") {
        const paymentId = fields.paymentId || syncId;
        const recordSyncId = fields.recordSyncId || fields.recordId;
        const amountPaise = Number(fields.amountPaise) || 0;

        // Execute all reads first for Firestore transaction compliance
        const existingPayment = await repo.getSyncedPayment(uid, paymentId, tx);
        const targetRecord = recordSyncId ? await repo.getSyncedRecord(uid, recordSyncId, tx) : null;

        if (existingPayment) {
          const itemResult = {
            syncId: paymentId,
            entity: "payment",
            op: "APPLY_PARTIAL_PAYMENT",
            status: "ACCEPTED",
            recordSyncId,
            serverRev: targetRecord ? targetRecord.serverRev : 1,
            seq: existingPayment.seq || 1,
            idempotentDuplicate: true
          };
          applied.push(itemResult);
          results.push(itemResult);
          continue;
        }

        const seq = await repo.allocateSequenceNumber(uid, tx);

        const paymentObj = {
          paymentId,
          syncId: paymentId,
          recordSyncId,
          amountPaise,
          paymentMode: fields.paymentMode || "UPI",
          note: fields.note || "",
          paidAtMs: fields.paidAtMs || fields.timestampMs || now,
          timestampMs: fields.timestampMs || fields.paidAtMs || now,
          hlc: effectiveHlcStr,
          deviceId,
          seq
        };

        await repo.saveSyncedPayment(uid, paymentId, paymentObj, tx);

        let updatedTargetRecord = targetRecord ? cloneDeep(targetRecord) : null;
        if (updatedTargetRecord) {
          updatedTargetRecord.paymentIds = Array.isArray(updatedTargetRecord.paymentIds)
            ? [...updatedTargetRecord.paymentIds]
            : [];
          updatedTargetRecord.payments = Array.isArray(updatedTargetRecord.payments)
            ? [...updatedTargetRecord.payments]
            : [];

          if (!updatedTargetRecord.paymentIds.includes(paymentId)) {
            updatedTargetRecord.paymentIds.push(paymentId);
            updatedTargetRecord.payments.push(paymentObj);
          }

          const totalPaid = updatedTargetRecord.payments.reduce((acc, p) => acc + (Number(p.amountPaise) || 0), 0);
          updatedTargetRecord.paidAmountPaise = totalPaid;
          updatedTargetRecord.remainingAmountPaise = Math.max(
            0,
            Number(updatedTargetRecord.principalAmountPaise) - totalPaid
          );

          if (updatedTargetRecord.remainingAmountPaise === 0 && updatedTargetRecord.principalAmountPaise > 0) {
            updatedTargetRecord.status = "SETTLED";
          }
          updatedTargetRecord.serverRev = (Number(updatedTargetRecord.serverRev) || 1) + 1;
          updatedTargetRecord.hlc = effectiveHlcStr;
          updatedTargetRecord.updatedAtMs = now;

          await repo.saveSyncedRecord(uid, recordSyncId, updatedTargetRecord, tx);
        }

        await repo.appendChangeLog(uid, seq, {
          entity: "payment",
          syncId: paymentId,
          recordSyncId,
          op: "APPLY_PARTIAL_PAYMENT",
          serverRev: updatedTargetRecord ? updatedTargetRecord.serverRev : 1,
          hlc: paymentObj.hlc,
          deviceId,
          fields: cloneDeep(paymentObj),
          payment: cloneDeep(paymentObj),
          record: updatedTargetRecord ? cloneDeep(updatedTargetRecord) : null
        }, tx);
        await repo.touchSyncSignal(uid, seq, tx);

        const itemResult = {
          syncId: paymentId,
          entity: "payment",
          op: "APPLY_PARTIAL_PAYMENT",
          status: "ACCEPTED",
          recordSyncId,
          serverRev: updatedTargetRecord ? updatedTargetRecord.serverRev : 1,
          seq
        };
        applied.push(itemResult);
        results.push(itemResult);
        continue;
      }

      // =========================================================================
      // 2. ENTITY: PERSON (LWW per field)
      // =========================================================================
      if (entity === "person") {
        const existingPerson = await repo.getSyncedPerson(uid, syncId, tx);
        const seq = await repo.allocateSequenceNumber(uid, tx);

        const mergedPerson = existingPerson
          ? {
              ...existingPerson,
              ...fields,
              serverRev: (existingPerson.serverRev || 1) + 1,
              hlc: effectiveHlcStr,
              updatedAtMs: now
            }
          : {
              syncId,
              personId: syncId,
              uid,
              ...fields,
              serverRev: 1,
              hlc: effectiveHlcStr,
              createdAtMs: now,
              updatedAtMs: now
            };

        await repo.saveSyncedPerson(uid, syncId, mergedPerson, tx);
        await repo.appendChangeLog(uid, seq, {
          entity: "person",
          syncId,
          op: existingPerson ? "UPDATE" : "CREATE",
          serverRev: mergedPerson.serverRev,
          hlc: mergedPerson.hlc,
          deviceId,
          fields: cloneDeep(mergedPerson),
          person: cloneDeep(mergedPerson)
        }, tx);
        await repo.touchSyncSignal(uid, seq, tx);

        const itemResult = {
          syncId,
          entity: "person",
          op: existingPerson ? "UPDATE" : "CREATE",
          status: "ACCEPTED",
          serverRev: mergedPerson.serverRev,
          seq
        };
        applied.push(itemResult);
        results.push(itemResult);
        continue;
      }

      // =========================================================================
      // 3. ENTITY: RECORD
      // =========================================================================
      if (entity === "record") {
        const existing = await repo.getSyncedRecord(uid, syncId, tx);

        if (op === "CREATE" || !existing) {
          if (existing && idempotencyKey && existing.lastIdempotencyKey === idempotencyKey) {
            const itemResult = {
              syncId,
              entity: "record",
              op: "CREATE",
              status: "ACCEPTED",
              serverRev: existing.serverRev,
              seq: existing.lastSeq || 1,
              idempotentDuplicate: true
            };
            applied.push(itemResult);
            results.push(itemResult);
            continue;
          }

          if (existing && op === "CREATE") {
            const reconciled = reconcileRecordUpdate({
              existing,
              incomingFields: fields,
              incomingFieldHlcs: fieldHlcs,
              baseRev: 0,
              clientRev: effectiveBaseRev,
              changeHlcStr: effectiveHlcStr,
              deviceId,
              idempotencyKey
            });

            const seq = await repo.allocateSequenceNumber(uid, tx);
            reconciled.record.lastSeq = seq;
            await repo.saveSyncedRecord(uid, syncId, reconciled.record, tx);
            await repo.appendChangeLog(uid, seq, {
              entity: "record",
              syncId,
              op: reconciled.hasFinancialConflict ? "CONFLICT" : "UPDATE",
              serverRev: reconciled.record.serverRev,
              hlc: reconciled.record.hlc,
              deviceId,
              fields: cloneDeep(reconciled.record),
              record: cloneDeep(reconciled.record)
            }, tx);
            await repo.touchSyncSignal(uid, seq, tx);

            if (reconciled.hasFinancialConflict) {
              const conflictDetails = {
                conflictFields: reconciled.conflictFields,
                serverVersion: cloneDeep(existing),
                clientVersion: { ...cloneDeep(existing), ...cloneDeep(fields) },
                serverRecord: cloneDeep(reconciled.record)
              };
              const conflictItem = {
                syncId,
                entity: "record",
                op: "CONFLICT",
                status: "CONFLICT",
                serverRev: reconciled.record.serverRev,
                seq,
                conflictFields: reconciled.conflictFields,
                serverRecord: cloneDeep(reconciled.record),
                conflictDetails
              };
              conflicts.push(conflictItem);
              results.push(conflictItem);
            } else {
              const itemResult = {
                syncId,
                entity: "record",
                op: "UPDATE",
                status: "ACCEPTED",
                serverRev: reconciled.record.serverRev,
                seq
              };
              applied.push(itemResult);
              results.push(itemResult);
            }
            continue;
          }

          const initialFieldHlcs = {};
          for (const k of Object.keys(fields)) {
            initialFieldHlcs[k] = fieldHlcs[k] || effectiveHlcStr;
          }

          const principalPaise = Number(fields.principalAmountPaise) || 0;
          const paidPaise = Number(fields.paidAmountPaise) || 0;
          const seq = await repo.allocateSequenceNumber(uid, tx);

          const newRecord = {
            ...fields,
            syncId,
            recordId: fields.recordId || syncId,
            uid,
            principalAmountPaise: principalPaise,
            paidAmountPaise: paidPaise,
            remainingAmountPaise:
              fields.remainingAmountPaise !== undefined
                ? Number(fields.remainingAmountPaise)
                : Math.max(0, principalPaise - paidPaise),
            partyName: fields.partyName || fields.personName || "",
            personName: fields.personName || fields.partyName || "",
            partyPhone: fields.partyPhone || fields.phone || "",
            counterpartyRole: fields.counterpartyRole || "BORROWER",
            note: fields.note || "",
            status: fields.status || "PENDING",
            interestType: fields.interestType || "NONE",
            interestRateBps: Number(fields.interestRateBps) || 0,
            interestPeriod: fields.interestPeriod || "MONTHLY",
            compoundFrequency: fields.compoundFrequency || "NONE",
            startDateMs: fields.startDateMs || now,
            dueDateMs: fields.dueDateMs || null,
            reminderDateMs: fields.reminderDateMs || null,
            remindersDisabled: Boolean(fields.remindersDisabled),
            items: Array.isArray(fields.items) ? fields.items : [],
            upiId: fields.upiId || "",
            paymentIds: Array.isArray(fields.paymentIds) ? [...fields.paymentIds] : [],
            payments: Array.isArray(fields.payments) ? [...fields.payments] : [],
            serverRev: 1,
            clientRev: effectiveBaseRev,
            hlc: effectiveHlcStr,
            fieldHlcs: initialFieldHlcs,
            syncStatus: "SYNCED",
            conflictData: null,
            deletedAtMs: null,
            createdAtMs: fields.createdAtMs || now,
            updatedAtMs: now,
            lastIdempotencyKey: idempotencyKey || null,
            lastSeq: seq
          };

          await repo.saveSyncedRecord(uid, syncId, newRecord, tx);
          await repo.appendChangeLog(uid, seq, {
            entity: "record",
            syncId,
            op: "CREATE",
            serverRev: 1,
            hlc: newRecord.hlc,
            deviceId,
            fields: cloneDeep(newRecord),
            record: cloneDeep(newRecord)
          }, tx);
          await repo.touchSyncSignal(uid, seq, tx);

          const itemResult = {
            syncId,
            entity: "record",
            op: "CREATE",
            status: "ACCEPTED",
            serverRev: 1,
            seq
          };
          applied.push(itemResult);
          results.push(itemResult);
          continue;
        }

        if (op === "UPDATE" || op === "UPDATE_TERMS") {
          if (idempotencyKey && existing.lastIdempotencyKey === idempotencyKey) {
            const itemResult = {
              syncId,
              entity: "record",
              op: "UPDATE",
              status: "ACCEPTED",
              serverRev: existing.serverRev,
              seq: existing.lastSeq || 1,
              idempotentDuplicate: true
            };
            applied.push(itemResult);
            results.push(itemResult);
            continue;
          }

          const reconciled = reconcileRecordUpdate({
            existing,
            incomingFields: fields,
            incomingFieldHlcs: fieldHlcs,
            baseRev: effectiveBaseRev,
            clientRev: Number(clientRev) || effectiveBaseRev,
            changeHlcStr: effectiveHlcStr,
            deviceId,
            idempotencyKey
          });

          const seq = await repo.allocateSequenceNumber(uid, tx);
          reconciled.record.lastSeq = seq;
          await repo.saveSyncedRecord(uid, syncId, reconciled.record, tx);
          await repo.appendChangeLog(uid, seq, {
            entity: "record",
            syncId,
            op: reconciled.hasFinancialConflict ? "CONFLICT" : "UPDATE",
            serverRev: reconciled.record.serverRev,
            hlc: reconciled.record.hlc,
            deviceId,
            fields: cloneDeep(reconciled.record),
            record: cloneDeep(reconciled.record)
          }, tx);
          await repo.touchSyncSignal(uid, seq, tx);

          if (reconciled.hasFinancialConflict) {
            const conflictDetails = {
              conflictFields: reconciled.conflictFields,
              serverVersion: cloneDeep(existing),
              clientVersion: { ...cloneDeep(existing), ...cloneDeep(fields) },
              serverRecord: cloneDeep(reconciled.record)
            };
            const conflictItem = {
              syncId,
              entity: "record",
              op: "CONFLICT",
              status: "CONFLICT",
              serverRev: reconciled.record.serverRev,
              seq,
              conflictFields: reconciled.conflictFields,
              serverRecord: cloneDeep(reconciled.record),
              conflictDetails
            };
            conflicts.push(conflictItem);
            results.push(conflictItem);
          } else {
            const itemResult = {
              syncId,
              entity: "record",
              op: "UPDATE",
              status: "ACCEPTED",
              serverRev: reconciled.record.serverRev,
              seq
            };
            applied.push(itemResult);
            results.push(itemResult);
          }
          continue;
        }

        if (op === "DELETE") {
          const isConcurrentDelete = effectiveBaseRev < Number(existing.serverRev);
          // Edit-Beats-Delete: if another device already edited the record since baseRev, the edit prevails
          const shouldStayAlive = isConcurrentDelete && !existing.deletedAtMs;
          const seq = await repo.allocateSequenceNumber(uid, tx);

          const updated = {
            ...existing,
            deletedAtMs: shouldStayAlive ? null : (fields.deletedAtMs || now),
            status: shouldStayAlive ? (existing.status === "DELETED" ? "PENDING" : existing.status) : "DELETED",
            serverRev: existing.serverRev + 1,
            hlc: compareHlc(effectiveHlcStr, existing.hlc) >= 0 ? effectiveHlcStr : existing.hlc,
            updatedAtMs: now,
            lastIdempotencyKey: idempotencyKey || existing.lastIdempotencyKey,
            lastSeq: seq
          };

          await repo.saveSyncedRecord(uid, syncId, updated, tx);
          await repo.appendChangeLog(uid, seq, {
            entity: "record",
            syncId,
            op: shouldStayAlive ? "UPDATE" : "DELETE",
            serverRev: updated.serverRev,
            hlc: updated.hlc,
            deviceId,
            fields: cloneDeep(updated),
            record: cloneDeep(updated)
          }, tx);
          await repo.touchSyncSignal(uid, seq, tx);

          const itemResult = {
            syncId,
            entity: "record",
            op: "DELETE",
            status: "ACCEPTED",
            serverRev: updated.serverRev,
            seq
          };
          applied.push(itemResult);
          results.push(itemResult);
          continue;
        }

        if (op === "RESOLVE_CONFLICT") {
          const resolution = fields.resolution || "KEEP_MINE";
          let resolvedRecord = { ...existing };

          if (resolution === "KEEP_MINE" && existing.conflictData) {
            const clientVersion = existing.conflictData.clientProposed || {};
            for (const f of FINANCIAL_FIELDS) {
              if (clientVersion[f] !== undefined) {
                resolvedRecord[f] = clientVersion[f];
                resolvedRecord.fieldHlcs = resolvedRecord.fieldHlcs || {};
                resolvedRecord.fieldHlcs[f] = effectiveHlcStr;
              }
            }
          } else if (fields.resolvedFields) {
            for (const [k, v] of Object.entries(fields.resolvedFields)) {
              resolvedRecord[k] = v;
              resolvedRecord.fieldHlcs = resolvedRecord.fieldHlcs || {};
              resolvedRecord.fieldHlcs[k] = effectiveHlcStr;
            }
          }

          const totalPaid = (resolvedRecord.payments || []).reduce((acc, p) => acc + (Number(p.amountPaise) || 0), 0);
          resolvedRecord.paidAmountPaise = totalPaid;
          resolvedRecord.remainingAmountPaise = Math.max(0, Number(resolvedRecord.principalAmountPaise) - totalPaid);
          if (resolvedRecord.remainingAmountPaise === 0 && resolvedRecord.principalAmountPaise > 0) {
            resolvedRecord.status = "SETTLED";
          } else if (resolvedRecord.status === "SETTLED" && resolvedRecord.remainingAmountPaise > 0) {
            resolvedRecord.status = "PENDING";
          }

          const seq = await repo.allocateSequenceNumber(uid, tx);
          resolvedRecord.syncStatus = "SYNCED";
          resolvedRecord.conflictData = null;
          resolvedRecord.serverRev = existing.serverRev + 1;
          resolvedRecord.hlc = effectiveHlcStr;
          resolvedRecord.updatedAtMs = now;
          resolvedRecord.lastSeq = seq;

          await repo.saveSyncedRecord(uid, syncId, resolvedRecord, tx);
          await repo.appendChangeLog(uid, seq, {
            entity: "record",
            syncId,
            op: "RESOLVE_CONFLICT",
            serverRev: resolvedRecord.serverRev,
            hlc: resolvedRecord.hlc,
            deviceId,
            fields: cloneDeep(resolvedRecord),
            record: cloneDeep(resolvedRecord)
          }, tx);
          await repo.touchSyncSignal(uid, seq, tx);

          const itemResult = {
            syncId,
            entity: "record",
            op: "RESOLVE_CONFLICT",
            status: "ACCEPTED",
            serverRev: resolvedRecord.serverRev,
            seq
          };
          applied.push(itemResult);
          results.push(itemResult);
        }
      }
    }
  });

  const finalSignal = await repo.getSyncSignal(uid);

  return {
    success: true,
    serverHlc: packHlc(serverHlc),
    latestSeq: finalSignal?.seq || 0,
    results,
    applied,
    conflicts
  };
}

/**
 * Reconcile an incoming record update against existing server state
 * using CRDT-Lite rules:
 * - Edit-Beats-Delete: Any edit resurrects a soft-deleted record.
 * - Non-financial fields: Last-Write-Wins (LWW) by HLC
 * - Financial fields: If concurrent (baseRev < existing.serverRev) and value differs, flag FINANCIAL_CONFLICT!
 */
function reconcileRecordUpdate({
  existing,
  incomingFields,
  incomingFieldHlcs = {},
  baseRev = 0,
  clientRev = 1,
  changeHlcStr,
  deviceId,
  idempotencyKey
}) {
  const updated = cloneDeep(existing);
  updated.fieldHlcs = updated.fieldHlcs || {};

  // Edit-Beats-Delete: resurrect if previously deleted
  if (updated.deletedAtMs) {
    updated.deletedAtMs = null;
    if (updated.status === "DELETED") {
      updated.status = "PENDING";
    }
  }

  const isConcurrentEdit = Number(baseRev) < Number(existing.serverRev);
  const conflictFields = [];
  const clientProposed = {};
  const serverValues = {};

  for (const [field, incomingVal] of Object.entries(incomingFields)) {
    if (
      [
        "syncId",
        "recordId",
        "uid",
        "serverRev",
        "clientRev",
        "clientRevision",
        "hlc",
        "syncStatus",
        "conflictData",
        "payments",
        "paymentIds",
        "idempotencyKey"
      ].includes(field)
    ) {
      continue;
    }

    const currentVal = updated[field];
    const isSameValue = JSON.stringify(currentVal) === JSON.stringify(incomingVal);
    if (isSameValue) {
      continue;
    }

    const incomingHlc = incomingFieldHlcs[field] || incomingFields.hlc || changeHlcStr;
    const existingFieldHlc = updated.fieldHlcs[field] || updated.hlc;

    if (FINANCIAL_FIELDS.includes(field)) {
      if (isConcurrentEdit) {
        conflictFields.push(field);
        clientProposed[field] = incomingVal;
        serverValues[field] = currentVal;
      } else {
        updated[field] = incomingVal;
        updated.fieldHlcs[field] = incomingHlc;
      }
    } else {
      if (!isConcurrentEdit || compareHlc(incomingHlc, existingFieldHlc) >= 0) {
        updated[field] = incomingVal;
        updated.fieldHlcs[field] = incomingHlc;
      }
    }
  }

  const totalPaid = (updated.payments || []).reduce((acc, p) => acc + (Number(p.amountPaise) || 0), 0);
  updated.paidAmountPaise = totalPaid;
  updated.remainingAmountPaise = Math.max(0, Number(updated.principalAmountPaise) - totalPaid);

  const hasFinancialConflict = conflictFields.length > 0;
  if (hasFinancialConflict) {
    const existingConflictFields = updated.conflictData?.conflictFields || [];
    const mergedConflictFields = Array.from(new Set([...existingConflictFields, ...conflictFields]));

    updated.syncStatus = "FINANCIAL_CONFLICT";
    updated.conflictData = {
      conflictFields: mergedConflictFields,
      serverValues: { ...(updated.conflictData?.serverValues || {}), ...serverValues },
      clientProposed: { ...(updated.conflictData?.clientProposed || {}), ...clientProposed },
      detectedAtMs: Date.now(),
      clientDeviceId: deviceId,
      baseRev,
      serverRev: existing.serverRev
    };
  }

  updated.serverRev = existing.serverRev + 1;
  updated.clientRev = Number(clientRev) || updated.clientRev;
  if (compareHlc(changeHlcStr, updated.hlc) > 0) {
    updated.hlc = changeHlcStr;
  }
  updated.updatedAtMs = Date.now();
  if (idempotencyKey) {
    updated.lastIdempotencyKey = idempotencyKey;
  }

  return {
    record: updated,
    hasFinancialConflict,
    conflictFields
  };
}

/**
 * Pull ordered changes from the user's changeLog since cursor sequence number
 */
async function processSyncPull({ uid, cursor = 0, limit = 100 }) {
  if (!uid) {
    const err = new Error("User ID is required for sync pull");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const repo = getRequiredSyncRepo();
  const sinceSeq = Number(cursor) || 0;
  const maxLimit = Math.min(Math.max(1, Number(limit) || 100), 500);

  const page = await repo.pullChangeLog(uid, sinceSeq, maxLimit + 1);
  const hasMore = page.length > maxLimit;
  const sliced = hasMore ? page.slice(0, maxLimit) : page;
  const sig = await repo.getSyncSignal(uid);
  const nextCursor = sliced.length > 0 ? sliced[sliced.length - 1].seq : sinceSeq;

  return {
    changes: cloneDeep(sliced),
    nextCursor,
    latestSeq: sig?.seq || nextCursor,
    hasMore,
    serverHlc: packHlc(createHlc("server", Date.now()))
  };
}

async function getUserRecordState(uid, syncId) {
  const repo = getRequiredSyncRepo();
  const rec = await repo.getSyncedRecord(uid, syncId);
  return rec ? cloneDeep(rec) : null;
}

async function resetSyncStore() {
  const repo = getSyncRepository();
  assertRepoMethod(repo, "clear", "SyncRepository");
  await repo.clear();
}

function cloneDeep(obj) {
  if (obj === null || obj === undefined) return obj;
  return JSON.parse(JSON.stringify(obj));
}

module.exports = {
  FINANCIAL_FIELDS,
  NON_FINANCIAL_FIELDS,
  processSyncPush,
  processSyncPull,
  getUserRecordState,
  resetSyncStore
};
