/**
 * Ledger Records Service (functions/src/ledger/recordService.js)
 * Implements CRUD, quota enforcement, optimistic concurrency (ETag / serverRev),
 * cursor pagination, and Cloud KMS envelope encryption.
 * Backed by persistent LedgerRepository with zero Proxy facades or module-level Map storage.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const { getOrCreateUserDek, encryptRecordFields, decryptRecordFields } = require("../crypto/envelopeCrypto");
const { appendLedgerEvent } = require("./eventService");
const { getLedgerRepository, assertRepoMethod } = require("../repositories");

const VALID_ROLES = ["LENDER", "BORROWER"];
const VALID_STATUSES = ["PENDING", "CONFIRMED", "SETTLED", "DISPUTED"];

function getRequiredLedgerRepo() {
  const repo = getLedgerRepository();
  assertRepoMethod(repo, "createRecord", "LedgerRepository");
  assertRepoMethod(repo, "getRecord", "LedgerRepository");
  assertRepoMethod(repo, "updateRecord", "LedgerRepository");
  assertRepoMethod(repo, "listRecords", "LedgerRepository");
  return repo;
}

/**
 * Validate integer paise and record payload
 */
function validateRecordInput(data, isUpdate = false) {
  if (!data || typeof data !== "object") {
    const err = new Error("Record data is required");
    err.code = "ERR_INVALID_DATA";
    err.status = 400;
    throw err;
  }

  const rawPaise = data.principalAmountPaise !== undefined ? data.principalAmountPaise : data.amountPaise;
  if (!isUpdate || rawPaise !== undefined) {
    const paise = Number(rawPaise);
    if (!Number.isInteger(paise) || paise <= 0) {
      const err = new Error("principalAmountPaise must be a positive integer in paise (e.g., 50000 for ₹500.00)");
      err.code = "ERR_INVALID_AMOUNT";
      err.status = 400;
      throw err;
    }
  }

  const partyName = data.partyName || data.counterpartyName;
  if (!isUpdate || partyName !== undefined) {
    if (!partyName || typeof partyName !== "string" || partyName.trim().length === 0) {
      const err = new Error("Counterparty name (partyName) is required");
      err.code = "ERR_INVALID_PARTY_NAME";
      err.status = 400;
      throw err;
    }
    if (partyName.length > USER_QUOTAS.MAX_NAME_LENGTH) {
      const err = new Error(`Counterparty name cannot exceed ${USER_QUOTAS.MAX_NAME_LENGTH} characters`);
      err.code = "ERR_PARTY_NAME_TOO_LONG";
      err.status = 400;
      throw err;
    }
  }

  const role = data.counterpartyRole || data.role;
  if (!isUpdate || role !== undefined) {
    const resolvedRole = role || "BORROWER";
    if (!VALID_ROLES.includes(resolvedRole)) {
      const err = new Error("counterpartyRole must be LENDER or BORROWER");
      err.code = "ERR_INVALID_ROLE";
      err.status = 400;
      throw err;
    }
  }

  if (data.note && typeof data.note === "string" && data.note.length > USER_QUOTAS.MAX_NOTE_LENGTH) {
    const err = new Error(`Note cannot exceed ${USER_QUOTAS.MAX_NOTE_LENGTH} characters`);
    err.code = "ERR_NOTE_TOO_LONG";
    err.status = 400;
    throw err;
  }

  if (data.items && Array.isArray(data.items) && data.items.length > USER_QUOTAS.MAX_ITEMS_PER_RECORD) {
    const err = new Error(`Cannot exceed ${USER_QUOTAS.MAX_ITEMS_PER_RECORD} line items per record`);
    err.code = "ERR_TOO_MANY_ITEMS";
    err.status = 400;
    throw err;
  }
}

/**
 * Create a new ledger record
 */
async function createRecord(uidOrParams, recordDataArg) {
  let uid;
  let recordData;

  if (typeof uidOrParams === "object" && uidOrParams !== null && (uidOrParams.recordData || uidOrParams.userId)) {
    uid = uidOrParams.uid || uidOrParams.userId;
    recordData = uidOrParams.recordData || uidOrParams;
  } else {
    uid = uidOrParams;
    recordData = recordDataArg || {};
  }

  validateRecordInput(recordData, false);

  const repo = getRequiredLedgerRepo();
  const recordCount = typeof repo.countRecords === "function"
    ? await repo.countRecords(uid)
    : (await repo.listRecords(uid)).length;

  if (recordCount >= USER_QUOTAS.MAX_RECORDS) {
    const err = new Error(`Account record quota (${USER_QUOTAS.MAX_RECORDS}) exceeded.`);
    err.code = "ERR_QUOTA_EXCEEDED";
    err.status = 403;
    throw err;
  }

  const now = Date.now();
  const recordId = recordData.recordId || `rec_${now}_${crypto.randomBytes(4).toString("hex")}`;
  const rawPaise = recordData.principalAmountPaise !== undefined ? recordData.principalAmountPaise : recordData.amountPaise;
  const principalPaise = Number(rawPaise);
  const partyName = (recordData.partyName || recordData.counterpartyName || "Counterparty").trim();
  const counterpartyRole = recordData.counterpartyRole || recordData.role || "BORROWER";

  const plainRecord = {
    recordId,
    id: recordId,
    uid,
    personId: recordData.personId || null,
    counterpartyRole,
    partyName,
    partyPhone: (recordData.partyPhone || "").trim(),
    principalAmountPaise: principalPaise,
    remainingAmountPaise: principalPaise,
    currency: "INR",
    status: recordData.status && VALID_STATUSES.includes(recordData.status) ? recordData.status : "PENDING",
    interestType: recordData.interestType || "NONE",
    interestRateBps: Number(recordData.interestRateBps) || 0,
    interestPeriod: recordData.interestPeriod || "MONTHLY",
    compoundFrequency: recordData.compoundFrequency || "NONE",
    startDateMs: Number(recordData.startDateMs) || now,
    dueDateMs: recordData.dueDateMs ? Number(recordData.dueDateMs) : null,
    reminderDateMs: recordData.reminderDateMs ? Number(recordData.reminderDateMs) : null,
    remindersDisabled: Boolean(recordData.remindersDisabled),
    note: (recordData.note || "").trim(),
    items: Array.isArray(recordData.items) ? recordData.items : [],
    upiId: (recordData.upiId || "").trim(),
    linkedCreatorUid: recordData.linkedCreatorUid || null,
    remoteRecordId: recordData.remoteRecordId || null,
    serverRev: 1,
    clientRev: Number(recordData.clientRev) || 1,
    createdAtMs: now,
    updatedAtMs: now,
    deletedAtMs: null
  };

  // Envelope Encrypt PII fields before storing in repository
  const dek = await getOrCreateUserDek(uid);
  const encryptedToStore = encryptRecordFields(plainRecord, dek);
  await repo.createRecord(uid, encryptedToStore);

  // Append Genesis Event to Audit Chain
  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "GENESIS",
    amountPaise: principalPaise,
    note: "Record created",
    metadata: { counterpartyRole: plainRecord.counterpartyRole }
  });

  return plainRecord;
}

/**
 * Get a single decrypted record by ID
 */
async function getRecord({ uid, recordId }) {
  const repo = getRequiredLedgerRepo();
  const encrypted = await repo.getRecord(uid, recordId);

  if (!encrypted || encrypted.deletedAtMs || encrypted.deleted) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  return decryptRecordFields(encrypted, dek);
}

/**
 * List decrypted records with cursor pagination and filtering
 */
async function listRecords({ uid, limit = 50, cursor = null, status = null, role = null }) {
  const maxLimit = (USER_QUOTAS && USER_QUOTAS.MAX_PAGE_SIZE) || 500;
  const pageSize = Math.min(Math.max(1, Number(limit) || 25), maxLimit);
  const repo = getRequiredLedgerRepo();
  const dek = await getOrCreateUserDek(uid);

  const rawList = await repo.listRecords(uid);
  let decryptedList = [];
  for (const raw of rawList) {
    if (raw.deletedAtMs || raw.deleted) continue;
    if (status && raw.status !== status) continue;
    if (role && raw.counterpartyRole !== role) continue;
    decryptedList.push(decryptRecordFields(raw, dek));
  }

  // Sort by updatedAtMs descending, then recordId
  decryptedList.sort((a, b) => b.updatedAtMs - a.updatedAtMs || a.recordId.localeCompare(b.recordId));

  let startIndex = 0;
  if (cursor) {
    const idx = decryptedList.findIndex(r => r.recordId === cursor);
    if (idx !== -1) startIndex = idx + 1;
  }

  const items = decryptedList.slice(startIndex, startIndex + pageSize);
  const nextCursor = startIndex + pageSize < decryptedList.length ? items[items.length - 1].recordId : null;

  return {
    items,
    nextCursor,
    totalCount: decryptedList.length
  };
}

/**
 * Update an existing record with ETag / expectedRev concurrency check
 */
async function updateRecord({ uid, recordId, updates, expectedRev = null }) {
  validateRecordInput(updates, true);

  const repo = getRequiredLedgerRepo();
  const existingEncrypted = await repo.getRecord(uid, recordId);

  if (!existingEncrypted || existingEncrypted.deletedAtMs || existingEncrypted.deleted) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  // Optimistic Concurrency Check (If-Match / expectedRev)
  if (expectedRev !== null && expectedRev !== undefined) {
    if (Number(expectedRev) !== existingEncrypted.serverRev) {
      const err = new Error(`Revision conflict: expected serverRev ${expectedRev}, but current is ${existingEncrypted.serverRev}`);
      err.code = "ERR_CONFLICT";
      err.status = 409;
      err.currentRev = existingEncrypted.serverRev;
      throw err;
    }
  }

  const dek = await getOrCreateUserDek(uid);
  const currentPlain = decryptRecordFields(existingEncrypted, dek);
  const now = Date.now();

  const updatedPlain = {
    ...currentPlain,
    partyName: updates.partyName !== undefined ? updates.partyName.trim() : currentPlain.partyName,
    partyPhone: updates.partyPhone !== undefined ? updates.partyPhone.trim() : currentPlain.partyPhone,
    counterpartyRole: updates.counterpartyRole !== undefined ? updates.counterpartyRole : currentPlain.counterpartyRole,
    principalAmountPaise: updates.principalAmountPaise !== undefined ? Number(updates.principalAmountPaise) : currentPlain.principalAmountPaise,
    remainingAmountPaise: updates.remainingAmountPaise !== undefined ? Number(updates.remainingAmountPaise) : currentPlain.remainingAmountPaise,
    status: updates.status !== undefined ? updates.status : currentPlain.status,
    dueDateMs: updates.dueDateMs !== undefined ? updates.dueDateMs : currentPlain.dueDateMs,
    reminderDateMs: updates.reminderDateMs !== undefined ? updates.reminderDateMs : currentPlain.reminderDateMs,
    remindersDisabled: updates.remindersDisabled !== undefined ? Boolean(updates.remindersDisabled) : currentPlain.remindersDisabled,
    snoozedUntilMs: updates.snoozedUntilMs !== undefined ? updates.snoozedUntilMs : currentPlain.snoozedUntilMs,
    pendingPaymentClaim: updates.pendingPaymentClaim !== undefined ? updates.pendingPaymentClaim : currentPlain.pendingPaymentClaim,
    pendingDateProposal: updates.pendingDateProposal !== undefined ? updates.pendingDateProposal : currentPlain.pendingDateProposal,
    note: updates.note !== undefined ? updates.note.trim() : currentPlain.note,
    items: updates.items !== undefined ? updates.items : currentPlain.items,
    upiId: updates.upiId !== undefined ? updates.upiId.trim() : currentPlain.upiId,
    serverRev: currentPlain.serverRev + 1,
    updatedAtMs: now
  };

  const reEncrypted = encryptRecordFields(updatedPlain, dek);
  await repo.updateRecord(uid, recordId, reEncrypted);

  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "TERMS_EDIT",
    amountPaise: updatedPlain.principalAmountPaise,
    note: "Record updated",
    metadata: { serverRev: updatedPlain.serverRev }
  });

  return updatedPlain;
}

/**
 * Mark a record as settled in full
 */
async function settleRecord({ uid, recordId, paymentMode = "CASH", note = "Settled in full" }) {
  const current = await getRecord({ uid, recordId });
  const settledAmount = current.remainingAmountPaise;

  const updated = await updateRecord({
    uid,
    recordId,
    updates: {
      remainingAmountPaise: 0,
      status: "SETTLED"
    }
  });

  await appendLedgerEvent({
    uid,
    recordId,
    eventType: "SETTLEMENT",
    amountPaise: settledAmount,
    paymentMode,
    note
  });

  return updated;
}

/**
 * Soft-delete a record (tombstone for sync convergence)
 */
async function deleteRecord({ uid, recordId }) {
  const repo = getRequiredLedgerRepo();
  const existing = await repo.getRecord(uid, recordId);

  if (!existing || existing.deletedAtMs || existing.deleted) {
    const err = new Error(`Record ${recordId} not found`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  await repo.updateRecord(uid, recordId, {
    deletedAtMs: Date.now(),
    deleted: true,
    serverRev: existing.serverRev + 1,
    updatedAtMs: Date.now()
  });

  return { success: true, recordId, deletedAtMs: Date.now() };
}

async function resetRecordsStore() {
  const repo = getLedgerRepository();
  assertRepoMethod(repo, "clear", "LedgerRepository");
  await repo.clear();
}

module.exports = {
  createRecord,
  getRecord,
  listRecords,
  updateRecord,
  settleRecord,
  deleteRecord,
  resetRecordsStore
};
