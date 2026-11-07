/**
 * Ledger Event & Merkle Audit Trail Service (functions/src/ledger/eventService.js)
 * Implements append-only event logging, cryptographic Merkle hash chaining,
 * and user sync signaling.
 */

const crypto = require("crypto");

// In-memory events store for testing/emulator: uid -> Map(eventId -> event)
const inMemoryEvents = new Map();
// In-memory sync signals: uid -> { seq, updatedAtMs }
const inMemorySyncSignals = new Map();

function getGenesisHash(uid) {
  return crypto.createHash("sha256").update(`genesis:${uid}:udhaar_v2`).digest("hex");
}

function calculateBlockHash(prevHash, eventType, timestampMs, payload) {
  const payloadStr = typeof payload === "string" ? payload : JSON.stringify(payload);
  return crypto
    .createHash("sha256")
    .update(`${prevHash}:${eventType}:${timestampMs}:${payloadStr}`)
    .digest("hex");
}

/**
 * Append an immutable event to the user's ledger event log
 */
async function appendLedgerEvent({ uid, recordId, eventType, amountPaise = 0, paymentMode = null, note = "", metadata = {} }) {
  const now = Date.now();
  const eventId = `evt_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;

  if (!inMemoryEvents.has(uid)) {
    inMemoryEvents.set(uid, new Map());
  }
  const userEvents = inMemoryEvents.get(uid);

  // Find previous block hash
  let prevHash = getGenesisHash(uid);
  const existingEventsList = Array.from(userEvents.values());
  if (existingEventsList.length > 0) {
    prevHash = existingEventsList[existingEventsList.length - 1].merkleHash;
  }

  const payload = {
    recordId,
    amountPaise,
    paymentMode,
    note,
    metadata
  };

  const merkleHash = calculateBlockHash(prevHash, eventType, now, payload);

  const eventRecord = {
    eventId,
    recordId,
    eventType,
    amountPaise,
    paymentMode,
    note,
    metadata,
    timestampMs: now,
    merklePrevHash: prevHash,
    merkleHash
  };

  userEvents.set(eventId, eventRecord);

  // Increment sync signal sequence
  const currentSignal = inMemorySyncSignals.get(uid) || { seq: 0, updatedAtMs: now };
  currentSignal.seq += 1;
  currentSignal.updatedAtMs = now;
  inMemorySyncSignals.set(uid, currentSignal);

  return eventRecord;
}

/**
 * List events for a specific record or all user events
 */
async function listEvents({ uid, recordId = null }) {
  if (!inMemoryEvents.has(uid)) {
    return [];
  }
  const all = Array.from(inMemoryEvents.get(uid).values());
  if (recordId) {
    return all.filter(e => e.recordId === recordId);
  }
  return all;
}

/**
 * Verify Merkle chain integrity from genesis to latest block
 */
async function verifyAuditChain(uid) {
  if (!inMemoryEvents.has(uid)) {
    return { valid: true, blockCount: 0 };
  }

  const all = Array.from(inMemoryEvents.get(uid).values());
  let expectedPrev = getGenesisHash(uid);

  for (let i = 0; i < all.length; i++) {
    const block = all[i];
    if (block.merklePrevHash !== expectedPrev) {
      return { valid: false, brokenIndex: i, reason: "PREV_HASH_MISMATCH" };
    }

    const payload = {
      recordId: block.recordId,
      amountPaise: block.amountPaise,
      paymentMode: block.paymentMode,
      note: block.note,
      metadata: block.metadata
    };

    const calculated = calculateBlockHash(block.merklePrevHash, block.eventType, block.timestampMs, payload);
    if (calculated !== block.merkleHash) {
      return { valid: false, brokenIndex: i, reason: "CONTENT_DIGEST_CORRUPTED" };
    }

    expectedPrev = block.merkleHash;
  }

  return { valid: true, blockCount: all.length };
}

function getSyncSignal(uid) {
  return inMemorySyncSignals.get(uid) || { seq: 0, updatedAtMs: 0 };
}

function resetEventStore() {
  inMemoryEvents.clear();
  inMemorySyncSignals.clear();
}

module.exports = {
  appendLedgerEvent,
  listEvents,
  verifyAuditChain,
  getSyncSignal,
  resetEventStore,
  _inMemoryEvents: inMemoryEvents
};
