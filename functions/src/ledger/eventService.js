/**
 * Ledger Event & Merkle Audit Trail Service (functions/src/ledger/eventService.js)
 * Implements append-only event logging, cryptographic Merkle hash chaining,
 * and user sync signaling.
 * Backed by persistent LedgerRepository and SyncRepository with zero Proxy facades or module-level Map storage.
 */

const crypto = require("crypto");
const { getLedgerRepository, getSyncRepository, assertRepoMethod } = require("../repositories");

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
  const ledgerRepo = getLedgerRepository();
  const syncRepo = getSyncRepository();

  assertRepoMethod(ledgerRepo, "listEvents", "LedgerRepository");
  assertRepoMethod(ledgerRepo, "addEvent", "LedgerRepository");
  assertRepoMethod(syncRepo, "getSyncSignal", "SyncRepository");
  assertRepoMethod(syncRepo, "touchSyncSignal", "SyncRepository");

  let prevHash = getGenesisHash(uid);
  const existingEventsList = await ledgerRepo.listEvents(uid);
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
    id: eventId,
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

  await ledgerRepo.addEvent(uid, eventRecord);

  const currentSignal = (await syncRepo.getSyncSignal(uid)) || { seq: 0 };
  const nextSeq = (currentSignal.seq || 0) + 1;
  await syncRepo.touchSyncSignal(uid, nextSeq);

  return eventRecord;
}

/**
 * List events for a specific record or all user events
 */
async function listEvents({ uid, recordId = null }) {
  const ledgerRepo = getLedgerRepository();
  assertRepoMethod(ledgerRepo, "listEvents", "LedgerRepository");
  return ledgerRepo.listEvents(uid, recordId);
}

/**
 * Verify Merkle chain integrity from genesis to latest block
 */
async function verifyAuditChain(uid) {
  const ledgerRepo = getLedgerRepository();
  assertRepoMethod(ledgerRepo, "listEvents", "LedgerRepository");
  const all = await ledgerRepo.listEvents(uid);

  if (!all || all.length === 0) {
    return { valid: true, blockCount: 0 };
  }

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

async function getSyncSignal(uid) {
  const syncRepo = getSyncRepository();
  assertRepoMethod(syncRepo, "getSyncSignal", "SyncRepository");
  return (await syncRepo.getSyncSignal(uid)) || { seq: 0, updatedAtMs: 0 };
}

async function resetEventStore() {
  const ledgerRepo = getLedgerRepository();
  const syncRepo = getSyncRepository();
  assertRepoMethod(ledgerRepo, "clear", "LedgerRepository");
  assertRepoMethod(syncRepo, "clear", "SyncRepository");
  await Promise.all([ledgerRepo.clear(), syncRepo.clear()]);
}

module.exports = {
  appendLedgerEvent,
  listEvents,
  verifyAuditChain,
  getSyncSignal,
  resetEventStore
};
