/**
 * DPDP Act 2023 Privacy Tools & Account Deletion Engine
 * (functions/src/privacy/privacyService.js)
 * Implements Stage S8 Privacy Compliance:
 * - Section 11 Right to Erasure with immediate Cryptographic DEK Shredding.
 * - 30-day cooling period / grace window before hard database purge.
 * - Reciprocal accounting integrity: counterparty records display "Deleted User".
 * - Consent withdrawal & management.
 */

const crypto = require("crypto");
const { _inMemoryUsers } = require("../auth/authService");
const { shredUserDek } = require("../crypto/envelopeCrypto");
const { resetDevicesStore, _inMemoryUserDevices } = require("../push/pushService");
const { _inMemoryRecords } = require("../ledger/recordService");
const { appendLedgerEvent } = require("../ledger/eventService");

const COOLING_PERIOD_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

/**
 * 1. Request Account Deletion with Immediate Cryptographic Key Shredding
 */
async function requestAccountDeletion({ uid, reason = "User requested erasure", clientIp = "127.0.0.1" }) {
  const user = _inMemoryUsers.get(uid);
  if (!user) {
    const err = new Error("User account not found.");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  const scheduledPurgeMs = now + COOLING_PERIOD_MS;

  // 1. Immediate Cryptographic Shredding of Data Encryption Key (DEK)
  // All historical ciphertexts encrypted with this key become mathematically irreversible junk.
  const shredResult = shredUserDek(uid);

  // 2. Mark account pending deletion with 30-day cooling window
  user.status = "PENDING_DELETION";
  user.deletionRequestedAtMs = now;
  user.deletionScheduledAtMs = scheduledPurgeMs;
  user.deletionReason = reason;
  user.updatedAtMs = now;

  // 3. Clear registered push devices
  const userDevices = _inMemoryUserDevices.get(uid);
  if (userDevices) {
    userDevices.clear();
  }

  // 4. Anonymize shared records for counterparties
  // Maintains accounting integrity for counterparties while preserving user's right to erasure
  for (const recordsMap of _inMemoryRecords.values()) {
    for (const rec of recordsMap.values()) {
      if (rec.linkedCreatorUid === uid) {
        rec.partyName = "Deleted User";
        rec.partyPhone = "+910000000000";
        rec.note = "Counterparty account has been deleted.";
      }
    }
  }

  return {
    success: true,
    uid,
    status: "PENDING_DELETION",
    cryptoShredded: shredResult.success,
    coolingPeriodDays: 30,
    deletionScheduledDate: new Date(scheduledPurgeMs).toISOString(),
    message: "Your account is scheduled for deletion. Your personal cryptographic key has been shredded immediately."
  };
}

/**
 * 2. Cancel Account Deletion (Within 30-day cooling window)
 */
async function cancelAccountDeletion({ uid, clientIp = "127.0.0.1" }) {
  const user = _inMemoryUsers.get(uid);
  if (!user) {
    const err = new Error("User account not found.");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (user.status !== "PENDING_DELETION") {
    return {
      success: true,
      alreadyActive: true,
      status: user.status
    };
  }

  const now = Date.now();
  if (user.deletionScheduledAtMs && now > user.deletionScheduledAtMs) {
    const err = new Error("The 30-day cooling period has expired. Account cannot be restored.");
    err.code = "ERR_COOLING_PERIOD_EXPIRED";
    err.status = 410;
    throw err;
  }

  user.status = "ACTIVE";
  user.deletionRequestedAtMs = null;
  user.deletionScheduledAtMs = null;
  user.deletionReason = null;
  user.updatedAtMs = now;

  return {
    success: true,
    uid,
    status: "ACTIVE",
    message: "Account deletion cancelled. Account restored to active status."
  };
}

/**
 * 3. Get User Consents
 */
async function getUserConsents({ uid }) {
  const user = _inMemoryUsers.get(uid);
  if (!user) {
    const err = new Error("User not found.");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  return {
    uid,
    consents: user.consents || {
      termsAccepted: true,
      whatsappUpdates: true,
      optionalAnalytics: false
    },
    updatedAtMs: user.updatedAtMs || user.createdAtMs
  };
}

/**
 * 4. Update / Withdraw Consents
 */
async function updateUserConsents({ uid, consentsUpdates, clientIp = "127.0.0.1" }) {
  const user = _inMemoryUsers.get(uid);
  if (!user) {
    const err = new Error("User not found.");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  const currentConsents = user.consents || {};
  const updated = {
    ...currentConsents,
    ...consentsUpdates,
    updatedAtMs: now
  };

  user.consents = updated;
  user.updatedAtMs = now;

  return {
    success: true,
    uid,
    consents: updated,
    updatedAtMs: now
  };
}

module.exports = {
  requestAccountDeletion,
  cancelAccountDeletion,
  getUserConsents,
  updateUserConsents,
  COOLING_PERIOD_MS
};
