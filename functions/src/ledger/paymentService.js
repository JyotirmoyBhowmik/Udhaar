/**
 * Ledger Payments Service (functions/src/ledger/paymentService.js)
 * Implements partial payments, balance reduction, settlement transitions, and audit events.
 */

const crypto = require("crypto");
const { getRecord, _inMemoryRecords } = require("./recordService");
const { getOrCreateUserDek, encryptRecordFields, decryptRecordFields } = require("../crypto/envelopeCrypto");
const { appendLedgerEvent, listEvents } = require("./eventService");

/**
 * Record a partial or full repayment against a record
 */
async function recordPayment({ uid, recordId, amountPaise, paymentMode = "UPI", note = "", idempotencyKey = "" }) {
  const paise = Number(amountPaise);
  if (!Number.isInteger(paise) || paise <= 0) {
    const err = new Error("Payment amount must be a positive integer in paise");
    err.code = "ERR_INVALID_PAYMENT_AMOUNT";
    err.status = 400;
    throw err;
  }

  // Get current record
  const record = await getRecord({ uid, recordId });

  if (record.status === "SETTLED") {
    const err = new Error("Cannot add payment to an already settled record");
    err.code = "ERR_RECORD_ALREADY_SETTLED";
    err.status = 400;
    throw err;
  }

  if (paise > record.remainingAmountPaise) {
    const err = new Error(`Payment amount (₹${(paise / 100).toFixed(2)}) exceeds remaining balance (₹${(record.remainingAmountPaise / 100).toFixed(2)})`);
    err.code = "ERR_PAYMENT_EXCEEDS_BALANCE";
    err.status = 400;
    throw err;
  }

  const newRemaining = record.remainingAmountPaise - paise;
  const newStatus = newRemaining === 0 ? "SETTLED" : record.status;
  const now = Date.now();

  record.remainingAmountPaise = newRemaining;
  record.status = newStatus;
  record.serverRev += 1;
  record.updatedAtMs = now;

  // Re-encrypt record
  const dek = await getOrCreateUserDek(uid);
  const reEncrypted = encryptRecordFields(record, dek);
  const userRecordsMap = _inMemoryRecords.get(uid);
  if (userRecordsMap) {
    userRecordsMap.set(recordId, reEncrypted);
  }

  // Append payment event
  const event = await appendLedgerEvent({
    uid,
    recordId,
    eventType: "PARTIAL_PAYMENT",
    amountPaise: paise,
    paymentMode,
    note: note.trim(),
    metadata: {
      remainingAmountPaise: newRemaining,
      newStatus,
      idempotencyKey
    }
  });

  return {
    paymentId: event.eventId,
    recordId,
    amountPaise: paise,
    remainingAmountPaise: newRemaining,
    status: newStatus,
    timestampMs: now,
    merkleHash: event.merkleHash
  };
}

/**
 * List payment history for a record
 */
async function listPayments({ uid, recordId }) {
  const events = await listEvents({ uid, recordId });
  return events.filter(e => e.eventType === "PARTIAL_PAYMENT");
}

module.exports = {
  recordPayment,
  listPayments
};
