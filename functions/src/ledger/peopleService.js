/**
 * Ledger People & Contacts Directory Service (functions/src/ledger/peopleService.js)
 * Implements CRUD on counterparties with field-level envelope encryption and quota checks.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const {
  getOrCreateUserDek,
  encryptPersonFields,
  decryptPersonFields
} = require("../crypto/envelopeCrypto");

// In-memory people store: uid -> Map(personId -> person)
const inMemoryPeople = new Map();

function getUserPeopleMap(uid) {
  if (!inMemoryPeople.has(uid)) {
    inMemoryPeople.set(uid, new Map());
  }
  return inMemoryPeople.get(uid);
}

async function createPerson({ uid, personData }) {
  const peopleMap = getUserPeopleMap(uid);

  // Quota check
  const activeCount = Array.from(peopleMap.values()).filter(p => !p.deletedAtMs).length;
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
  peopleMap.set(personId, encrypted);

  return { ...personPayload };
}

async function getPerson({ uid, personId }) {
  const peopleMap = getUserPeopleMap(uid);
  const stored = peopleMap.get(personId);

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
  const peopleMap = getUserPeopleMap(uid);
  let all = Array.from(peopleMap.values()).filter(p => !p.deletedAtMs);

  all.sort((a, b) => b.createdAtMs - a.createdAtMs);

  let startIndex = 0;
  if (cursor) {
    const foundIdx = all.findIndex(p => p.personId === cursor);
    if (foundIdx !== -1) {
      startIndex = foundIdx + 1;
    }
  }

  const pageSize = Math.min(Math.max(Number(limit) || 50, 1), 100);
  const paged = all.slice(startIndex, startIndex + pageSize);
  const nextCursor = startIndex + pageSize < all.length ? paged[paged.length - 1].personId : null;

  const dek = await getOrCreateUserDek(uid);
  const items = paged.map(p => decryptPersonFields(p, dek));

  return {
    items,
    nextCursor,
    total: all.length
  };
}

async function updatePerson({ uid, personId, updates }) {
  const peopleMap = getUserPeopleMap(uid);
  const existing = peopleMap.get(personId);

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
  peopleMap.set(personId, reEncrypted);

  return decrypted;
}

async function deletePerson({ uid, personId }) {
  const peopleMap = getUserPeopleMap(uid);
  const existing = peopleMap.get(personId);

  if (!existing || existing.deletedAtMs) {
    const err = new Error(`Person ${personId} not found`);
    err.code = "ERR_PERSON_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  existing.deletedAtMs = now;
  existing.updatedAtMs = now;

  return { success: true, personId, deletedAtMs: now };
}

function resetPeopleStore() {
  inMemoryPeople.clear();
}

module.exports = {
  createPerson,
  getPerson,
  listPeople,
  updatePerson,
  deletePerson,
  resetPeopleStore,
  _inMemoryPeople: inMemoryPeople
};
