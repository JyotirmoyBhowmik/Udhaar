/**
 * Ledger People (Contacts Directory) Service (functions/src/ledger/peopleService.js)
 * Implements CRUD, quota enforcement (5,000 max), and Cloud KMS field-level encryption.
 * Backed by persistent LedgerRepository with zero Proxy facades or module-level Map storage.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const { getOrCreateUserDek, encryptPersonFields, decryptPersonFields } = require("../crypto/envelopeCrypto");
const { getLedgerRepository, assertRepoMethod } = require("../repositories");

function getRequiredLedgerRepo() {
  const repo = getLedgerRepository();
  assertRepoMethod(repo, "createPerson", "LedgerRepository");
  assertRepoMethod(repo, "getPerson", "LedgerRepository");
  assertRepoMethod(repo, "updatePerson", "LedgerRepository");
  assertRepoMethod(repo, "listPeople", "LedgerRepository");
  return repo;
}

/**
 * Create a new counterparty contact in user's directory
 */
async function createPerson(uidOrParams, personDataArg) {
  let uid;
  let personData;
  if (typeof uidOrParams === "object" && uidOrParams !== null && (uidOrParams.personData || uidOrParams.name)) {
    uid = uidOrParams.uid;
    personData = uidOrParams.personData || uidOrParams;
  } else {
    uid = uidOrParams;
    personData = personDataArg || {};
  }

  if (!personData || !personData.name || typeof personData.name !== "string" || !personData.name.trim()) {
    const err = new Error("Contact name is required");
    err.code = "ERR_INVALID_PERSON_NAME";
    err.status = 400;
    throw err;
  }

  const repo = getRequiredLedgerRepo();
  const activeCount = (await repo.listPeople(uid)).length;

  if (activeCount >= USER_QUOTAS.MAX_PEOPLE) {
    const err = new Error(`Account people quota (${USER_QUOTAS.MAX_PEOPLE}) exceeded.`);
    err.code = "ERR_PEOPLE_QUOTA_EXCEEDED";
    err.status = 403;
    throw err;
  }

  const now = Date.now();
  const personId = personData.personId || `per_${now}_${crypto.randomBytes(4).toString("hex")}`;

  const plainPerson = {
    personId,
    id: personId,
    uid,
    name: personData.name.trim(),
    phone: (personData.phone || "").trim(),
    email: (personData.email || "").trim(),
    upiId: (personData.upiId || "").trim(),
    notes: (personData.notes || "").trim(),
    isBlocked: Boolean(personData.isBlocked),
    isMuted: Boolean(personData.isMuted),
    createdAtMs: now,
    updatedAtMs: now,
    deletedAtMs: null
  };

  const dek = await getOrCreateUserDek(uid);
  const encrypted = encryptPersonFields(plainPerson, dek);
  await repo.createPerson(uid, encrypted);

  return plainPerson;
}

/**
 * Get a single decrypted person by ID
 */
async function getPerson({ uid, personId }) {
  const repo = getRequiredLedgerRepo();
  const encrypted = await repo.getPerson(uid, personId);

  if (!encrypted || encrypted.deletedAtMs || encrypted.deleted) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  return decryptPersonFields(encrypted, dek);
}

/**
 * List all active people for a user
 */
async function listPeople({ uid, limit = USER_QUOTAS.DEFAULT_PAGE_SIZE, cursor = null }) {
  const pageSize = Math.min(Math.max(1, Number(limit) || 25), USER_QUOTAS.MAX_PAGE_SIZE);
  const repo = getRequiredLedgerRepo();
  const dek = await getOrCreateUserDek(uid);

  const rawList = await repo.listPeople(uid);
  const decrypted = [];
  for (const raw of rawList) {
    if (raw.deletedAtMs || raw.deleted) continue;
    decrypted.push(decryptPersonFields(raw, dek));
  }

  decrypted.sort((a, b) => a.name.localeCompare(b.name));

  let startIndex = 0;
  if (cursor) {
    const idx = decrypted.findIndex(p => p.personId === cursor);
    if (idx !== -1) startIndex = idx + 1;
  }

  const items = decrypted.slice(startIndex, startIndex + pageSize);
  const nextCursor = startIndex + pageSize < decrypted.length ? items[items.length - 1].personId : null;

  return {
    items,
    nextCursor,
    total: decrypted.length
  };
}

/**
 * Update a person contact
 */
async function updatePerson({ uid, personId, updates }) {
  const current = await getPerson({ uid, personId });
  const now = Date.now();

  const updatedPlain = {
    ...current,
    name: updates.name !== undefined ? updates.name.trim() : current.name,
    phone: updates.phone !== undefined ? updates.phone.trim() : current.phone,
    email: updates.email !== undefined ? updates.email.trim() : current.email,
    upiId: updates.upiId !== undefined ? updates.upiId.trim() : current.upiId,
    notes: updates.notes !== undefined ? updates.notes.trim() : current.notes,
    isBlocked: updates.isBlocked !== undefined ? Boolean(updates.isBlocked) : current.isBlocked,
    isMuted: updates.isMuted !== undefined ? Boolean(updates.isMuted) : current.isMuted,
    updatedAtMs: now
  };

  const dek = await getOrCreateUserDek(uid);
  const encrypted = encryptPersonFields(updatedPlain, dek);
  const repo = getRequiredLedgerRepo();
  await repo.updatePerson(uid, personId, encrypted);

  return updatedPlain;
}

/**
 * Soft-delete a person contact
 */
async function deletePerson({ uid, personId }) {
  const repo = getRequiredLedgerRepo();
  const existing = await repo.getPerson(uid, personId);

  if (!existing || existing.deletedAtMs || existing.deleted) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  await repo.updatePerson(uid, personId, {
    deletedAtMs: Date.now(),
    deleted: true,
    updatedAtMs: Date.now()
  });

  return { success: true, personId };
}

async function resetPeopleStore() {
  const repo = getLedgerRepository();
  assertRepoMethod(repo, "clear", "LedgerRepository");
  await repo.clear();
}

module.exports = {
  createPerson,
  getPerson,
  listPeople,
  updatePerson,
  deletePerson,
  resetPeopleStore
};
