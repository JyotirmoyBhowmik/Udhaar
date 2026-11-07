/**
 * Ledger Records Service (functions/src/ledger/recordService.js)
 * Implements CRUD on ledger entries with field-level envelope encryption,
 * quota enforcement, optimistic concurrency control (ETag/rev), and audit events.
 * Backed by persistent repositories with zero module-level Map storage.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const {
  getOrCreateUserDek,
  encryptRecordFields,
  decryptRecordFields
} = require("../crypto/envelopeCrypto");
const { appendLedgerEvent } = require("./eventService");
const { getLedgerRepository } = require("../repositories");

/**
 * Backward-compatible proxy object for legacy tests inspecting in-memory records map
 */
const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const map = getMapFn() || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryRecords = proxyMap(() => getLedgerRepository().records);

function resetRecordsStore() {
  const repo = getLedgerRepository();
  if (typeof repo.clear === "function") {
    repo.clear();
  }
}

/**
 * Validate incoming record payload
 */
function validateRecordInput(data) {
  if (!data || typeof data !== "object") {
    const err = new Error("Record payload is required");
    err.code = "ERR_INVALID_INPUT";
    err.status = 400;
    throw err;
  }

  // Amount must be positive integer paise
  const paise = Number(data.principalAmountPaise);
  if (!Number.isInteger(paise) || paise <= 0) {
    const err = new Error("Principal amount must be a positive integer in paise (e.g. 500000 for ₹5,000.00)");
    err.code = "ERR_INVALID_AMOUNT";
    err.status = 400;
    throw err;
  }

  // Role must be LENDER or BORROWER
  const role = String(data.counterpartyRole || "").toUpperCase();
  if (role !== "LENDER" && role !== "BORROWER") {
    const err = new Error("Counterparty role must be either 'LENDER' or 'BORROWER'");
    err.code = "ERR_INVALID_ROLE";
    err.status = 400;
    throw err;
  }

  // Counterparty name required
  if (!data.partyName || typeof data.partyName !== "string" || data.partyName.trim().length === 0) {
    const err = new Error("Counterparty name is required");
    err.code = "ERR_INVALID_PARTY_NAME";
    err.status = 400;
    throw err;
  }

  return {
    principalAmountPaise: paise,
    counterpartyRole: role,
    partyName: data.partyName.trim(),
    partyPhone: data.partyPhone ? String(data.partyPhone).trim() : "",
    dueDateMs: Number(data.dueDateMs) || 0,
    note: data.note ? String(data.note).trim() : "",
    items: Array.isArray(data.items) ? data.items : [],
    upiId: data.upiId ? String(data.upiId).trim() : "",
    partyId: data.partyId || `pty_${Date.now()}_${crypto.randomBytes(3).toString("hex")}`
  };
}

/**
 * Create a new record
 */
async function createRecord({ uid, recordData, clientRevision = 1, hlc = "", idempotencyKey = "" }) {
  const ledgerRepo = getLedgerRepository();

  // 1. Quota check
  const activeRecords = await ledgerRepo.listRecords(uid);
  const activeCount = activeRecords.filter(r => !r.deletedAtMs).length;
  if (activeCount >= USER_QUOTAS.MAX_RECORDS) {
    const err = new Error(`User account quota exceeded: maximum of ${USER_QUOTAS.MAX_RECORDS} records allowed.`);
    err.code = "ERR_QUOTA_EXCEEDED";
    err.status = 403;
    throw err;
  }

  // 2. Input validation
  const validated = validateRecordInput(recordData);

  const recordId = `rec_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const syncId = recordData.syncId || crypto.randomUUID();
  const now = Date.now();

  const recordPayload = {
    recordId,
    id: recordId,
    syncId,
    partyId: validated.partyId,
    partyName: validated.partyName,
    partyPhone: validated.partyPhone,
    counterpartyRole: validated.counterpartyRole,
    principalAmountPaise: validated.principalAmountPaise,
    remainingAmountPaise: validated.principalAmountPaise,
    dueDateMs: validated.dueDateMs,
    note: validated.note,
    items: validated.items,
    upiId: validated.upiId,
    status: "PENDING",
    serverRev: 1,
    clientRevision: Number(clientRevision) || 1,
    hlc: hlc || `${now}:0:server`,
    idempotencyKey,
    linkedRemoteRecordId: recordData.linkedRemoteRecordId || null,
    linkedCreatorUid: recordData.linkedCreatorUid || null,
    createdAtMs: now,
    updatedAtMs: now,
    deletedAtMs: null
  };

  // 3. Encrypt sensitive fields with user's DEK
  const dek = await getOrCreateUserDek(uid);
  const encryptedToStore = encryptRecordFields(recordPayload, dek);

  // 4. Store in database
  await ledgerRepo.createRecord(uid, { id: recordId, ...encryptedToStore });

  // 5. Append genesis event
  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "GENESIS",
    amountPaise: validated.principalAmountPaise,
    note: "Record created",
    metadata: { role: validated.counterpartyRole }
  });

  // Return clean decrypted DTO to client
  return { ...recordPayload };
}

/**
 * Retrieve a single record by ID
 */
async function getRecord({ uid, recordId }) {
  const ledgerRepo = getLedgerRepository();
  const stored = await ledgerRepo.getRecord(uid, recordId);

  if (!stored || stored.deletedAtMs) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  return decryptRecordFields(stored, dek);
}

/**
 * List records with filtering and cursor pagination
 */
async function listRecords({ uid, status = null, role = null, cursor = null, limit = 50 }) {
  const ledgerRepo = getLedgerRepository();
  let all = await ledgerRepo.listRecords(uid, {
    status: status ? status.toUpperCase() : null,
    counterpartyRole: role ? role.toUpperCase() : null
  });
  all = all.filter(r => !r.deletedAtMs);

  let startIndex = 0;
  if (cursor) {
    const foundIdx = all.findIndex(r => (r.recordId || r.id) === cursor);
    if (foundIdx !== -1) {
      startIndex = foundIdx + 1;
    }
  }

  const pageSize = Math.min(Math.max(Number(limit) || 50, 1), 100);
  const paged = all.slice(startIndex, startIndex + pageSize);
  const nextCursor = startIndex + pageSize < all.length ? (paged[paged.length - 1].recordId || paged[paged.length - 1].id) : null;

  const dek = await getOrCreateUserDek(uid);
  const items = paged.map(r => decryptRecordFields(r, dek));

  return {
    items,
    nextCursor,
    total: all.length
  };
}

/**
 * Update an existing record with concurrency control
 */
async function updateRecord({ uid, recordId, updates, expectedRev = null, hlc = "" }) {
  const ledgerRepo = getLedgerRepository();
  const existing = await ledgerRepo.getRecord(uid, recordId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  // Concurrency check
  if (expectedRev !== null && expectedRev !== undefined && existing.serverRev !== Number(expectedRev)) {
    const err = new Error(`Record update conflict: current revision is ${existing.serverRev}, but expected ${expectedRev}`);
    err.code = "ERR_CONFLICT";
    err.status = 409;
    err.currentRev = existing.serverRev;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  const decrypted = decryptRecordFields(existing, dek);
  const now = Date.now();

  if (updates.partyName) decrypted.partyName = String(updates.partyName).trim();
  if (updates.partyPhone !== undefined) decrypted.partyPhone = String(updates.partyPhone).trim();
  if (updates.dueDateMs !== undefined) decrypted.dueDateMs = Number(updates.dueDateMs);
  if (updates.note !== undefined) decrypted.note = String(updates.note).trim();
  if (updates.upiId !== undefined) decrypted.upiId = String(updates.upiId).trim();
  if (updates.items !== undefined && Array.isArray(updates.items)) decrypted.items = updates.items;

  decrypted.serverRev += 1;
  decrypted.updatedAtMs = now;
  if (hlc) decrypted.hlc = hlc;

  // Re-encrypt and save
  const reEncrypted = encryptRecordFields(decrypted, dek);
  await ledgerRepo.updateRecord(uid, recordId, reEncrypted);

  // Append audit event
  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "TERMS_EDIT",
    note: "Record terms updated",
    metadata: { serverRev: decrypted.serverRev }
  });

  return decrypted;
}

/**
 * Settle a record (marks remaining paise = 0, status = SETTLED)
 */
async function settleRecord({ uid, recordId, note = "" }) {
  const ledgerRepo = getLedgerRepository();
  const existing = await ledgerRepo.getRecord(uid, recordId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  const decrypted = decryptRecordFields(existing, dek);
  const settledAmount = decrypted.remainingAmountPaise;

  decrypted.remainingAmountPaise = 0;
  decrypted.status = "SETTLED";
  decrypted.serverRev += 1;
  decrypted.updatedAtMs = Date.now();

  const reEncrypted = encryptRecordFields(decrypted, dek);
  await ledgerRepo.updateRecord(uid, recordId, reEncrypted);

  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "STATUS_CHANGE",
    amountPaise: settledAmount,
    note: note || "Record settled in full",
    metadata: { newStatus: "SETTLED" }
  });

  return decrypted;
}

/**
 * Soft delete a record
 */
async function deleteRecord({ uid, recordId }) {
  const ledgerRepo = getLedgerRepository();
  const existing = await ledgerRepo.getRecord(uid, recordId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  const updates = {
    deletedAtMs: now,
    serverRev: (existing.serverRev || 1) + 1,
    updatedAtMs: now
  };
  await ledgerRepo.updateRecord(uid, recordId, updates);

  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "STATUS_CHANGE",
    note: "Record deleted",
    metadata: { deletedAtMs: now }
  });

  return { success: true, recordId, deletedAtMs: now };
}

module.exports = {
  createRecord,
  getRecord,
  listRecords,
  updateRecord,
  settleRecord,
  deleteRecord,
  validateRecordInput,
  resetRecordsStore,
  _inMemoryRecords
};
