/**
 * Ledger People & Contacts Directory Service (functions/src/ledger/peopleService.js)
 * Implements CRUD on counterparties with field-level envelope encryption and quota checks.
 * Backed by persistent repositories with zero module-level Map storage.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const {
  getOrCreateUserDek,
  encryptPersonFields,
  decryptPersonFields
} = require("../crypto/envelopeCrypto");
const { getLedgerRepository } = require("../repositories");

const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const repo = getLedgerRepository();
    const map = (repo && repo.people) || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryPeople = proxyMap(() => getLedgerRepository().people);

async function createPerson({ uid, personData }) {
  const repo = getLedgerRepository();
  const existingPeople = await repo.listPeople(uid);
  const activeCount = existingPeople.filter(p => !p.deletedAtMs).length;
  if (activeCount >= USER_QUOTAS.MAX_PEOPLE) {
    const err = new Error(`User account quota exceeded: maximum of ${USER_QUOTAS.MAX_PEOPLE} contacts allowed.`);
    err.code = "ERR_QUOTA_EXCEEDED";
    err.status = 403;
    throw err;
  }

  if (!personData || !personData.name || typeof personData.name !== "string" || personData.name.trim().length === 0) {
    const err = new Error("Contact name is required");
    err.code = "ERR_INVALID_NAME";
    err.status = 400;
    throw err;
  }

  const personId = personData.personId || `per_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const now = Date.now();

  const personPayload = {
    personId,
    id: personId,
    name: personData.name.trim(),
    phone: personData.phone ? String(personData.phone).trim() : "",
    email: personData.email ? String(personData.email).trim().toLowerCase() : "",
    notes: personData.notes ? String(personData.notes).trim() : "",
    createdAtMs: now,
    updatedAtMs: now,
    deletedAtMs: null
  };

  const dek = await getOrCreateUserDek(uid);
  const encrypted = encryptPersonFields(personPayload, dek);
  await repo.createPerson(uid, { id: personId, ...encrypted });

  return { ...personPayload };
}

async function getPerson({ uid, personId }) {
  const repo = getLedgerRepository();
  const stored = await repo.getPerson(uid, personId);

  if (!stored || stored.deletedAtMs) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  return decryptPersonFields(stored, dek);
}

async function listPeople({ uid, cursor = null, limit = 50 }) {
  const repo = getLedgerRepository();
  let all = (await repo.listPeople(uid)).filter(p => !p.deletedAtMs);

  all.sort((a, b) => b.createdAtMs - a.createdAtMs);

  let startIndex = 0;
  if (cursor) {
    const foundIdx = all.findIndex(p => (p.personId || p.id) === cursor);
    if (foundIdx !== -1) {
      startIndex = foundIdx + 1;
    }
  }

  const pageSize = Math.min(Math.max(Number(limit) || 50, 1), 100);
  const paged = all.slice(startIndex, startIndex + pageSize);
  const nextCursor = startIndex + pageSize < all.length ? (paged[paged.length - 1].personId || paged[paged.length - 1].id) : null;

  const dek = await getOrCreateUserDek(uid);
  const items = paged.map(p => decryptPersonFields(p, dek));

  return {
    items,
    nextCursor,
    total: all.length
  };
}

async function updatePerson({ uid, personId, updates }) {
  const repo = getLedgerRepository();
  const existing = await repo.getPerson(uid, personId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const dek = await getOrCreateUserDek(uid);
  const decrypted = decryptPersonFields(existing, dek);

  if (updates.name) decrypted.name = String(updates.name).trim();
  if (updates.phone !== undefined) decrypted.phone = String(updates.phone).trim();
  if (updates.email !== undefined) decrypted.email = String(updates.email).trim().toLowerCase();
  if (updates.notes !== undefined) decrypted.notes = String(updates.notes).trim();
  decrypted.updatedAtMs = Date.now();

  const reEncrypted = encryptPersonFields(decrypted, dek);
  await repo.updatePerson(uid, personId, reEncrypted);

  return decrypted;
}

async function deletePerson({ uid, personId }) {
  const repo = getLedgerRepository();
  const existing = await repo.getPerson(uid, personId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  await repo.updatePerson(uid, personId, { deletedAtMs: now, updatedAtMs: now });

  return { success: true, personId, deletedAtMs: now };
}

function resetPeopleStore() {
  const repo = getLedgerRepository();
  if (repo && typeof repo.clear === "function") {
    repo.clear();
  }
}

module.exports = {
  createPerson,
  getPerson,
  listPeople,
  updatePerson,
  deletePerson,
  resetPeopleStore,
  _inMemoryPeople
};
