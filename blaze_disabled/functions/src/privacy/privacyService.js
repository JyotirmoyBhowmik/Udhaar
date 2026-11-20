/**
 * DPDP Act 2023 Privacy, Consent Withdrawal & Cryptographic Shredding Service
 * (functions/src/privacy/privacyService.js)
 * Implements Stage S8 Privacy Rights:
 * - Persists privacy and deletion requests in Firestore via PrivacyRepository.
 * - Immediate Cryptographic Shredding of per-user Data Encryption Key (DEK).
 * - 30-day cooling period before hard purge of account shell.
 * - Counterparty record anonymization ("Deleted User").
 * - Granular consent inspection and withdrawal.
 */

const crypto = require("crypto");
const { shredUserDek } = require("../crypto/envelopeCrypto");
const {
  getPrivacyRepository,
  getAuthRepository,
  getSessionRepository,
  getLedgerRepository,
  assertRepoMethod
} = require("../repositories");

const DELETION_COOLING_DAYS = 30;
const DELETION_COOLING_MS = DELETION_COOLING_DAYS * 24 * 60 * 60 * 1000;
const COOLING_PERIOD_MS = DELETION_COOLING_MS;

function getRequiredPrivacyRepo() {
  const repo = getPrivacyRepository();
  assertRepoMethod(repo, "savePrivacyRequest", "PrivacyRepository");
  assertRepoMethod(repo, "getPrivacyRequest", "PrivacyRepository");
  assertRepoMethod(repo, "listPrivacyRequests", "PrivacyRepository");
  assertRepoMethod(repo, "deleteUserData", "PrivacyRepository");
  return repo;
}

function getRequiredAuthRepo() {
  const repo = getAuthRepository();
  assertRepoMethod(repo, "getUser", "AuthRepository");
  assertRepoMethod(repo, "updateUser", "AuthRepository");
  assertRepoMethod(repo, "appendAuditLog", "AuthRepository");
  return repo;
}

/**
 * Execute DPDP Act 2023 Right to Erasure (Account Deletion Request):
 * 1. Immediately destroys the user's Data Encryption Key (DEK) so all encrypted PII is permanently unreadable.
 * 2. Revokes all active push notification tokens/devices.
 * 3. Masks the user's identity on any linked counterparty records as "Deleted User".
 * 4. Persists the deletion request in Firestore and schedules hard purge in 30 days.
 */
async function requestAccountDeletion({ uid, reason = "User requested erasure", clientIp = "127.0.0.1" }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const privacyRepo = getRequiredPrivacyRepo();
  const authRepo = getRequiredAuthRepo();
  const sessionRepo = getSessionRepository();
  const ledgerRepo = getLedgerRepository();

  assertRepoMethod(sessionRepo, "deleteAllUserDevices", "SessionRepository");
  assertRepoMethod(ledgerRepo, "anonymizeCounterpartyRecords", "LedgerRepository");

  const now = Date.now();
  const purgeAtMs = now + DELETION_COOLING_MS;

  // 1. Cryptographic Shredding of user's DEK
  const shredRes = await shredUserDek(uid);
  const keyWasShredded = Boolean(shredRes && (shredRes.existed !== undefined ? shredRes.existed : shredRes));

  // 2. Clear push notification device registrations
  await sessionRepo.deleteAllUserDevices(uid);

  // 3. Anonymize counterparty references in linked records
  await ledgerRepo.anonymizeCounterpartyRecords(uid);

  // 4. Update user account record to PENDING_DELETION
  const existingUser = (await authRepo.getUser(uid)) || { uid, createdAtMs: now };
  await authRepo.updateUser(uid, {
    ...existingUser,
    displayName: "Deleted User",
    status: "PENDING_DELETION",
    deletionRequestedAtMs: now,
    deletionScheduledAtMs: purgeAtMs,
    deletionReason: String(reason || "").trim(),
    cryptoShredded: true,
    cryptoShreddedAtMs: now,
    consents: {
      ...(existingUser.consents || {}),
      whatsappUpdates: false,
      allowWhatsAppReminders: false,
      marketingCommunications: false,
      analytics: false,
      allowAnalytics: false,
      withdrawnAtMs: now
    },
    updatedAtMs: now
  });

  // 5. Persist privacy request in Firestore
  const requestId = `prv_del_${uid}_${now}`;
  await privacyRepo.savePrivacyRequest({
    id: requestId,
    requestId,
    uid,
    type: "ACCOUNT_DELETION",
    status: "PENDING_COOLING_PERIOD",
    reason: String(reason || "").trim(),
    cryptoShredded: true,
    keyExistedBeforeShred: keyWasShredded,
    deletionScheduledAtMs: purgeAtMs,
    clientIp,
    createdAtMs: now
  });

  await authRepo.appendAuditLog({
    uid,
    eventType: "ACCOUNT_DELETION_REQUESTED",
    ipHash: crypto.createHash("sha256").update(clientIp).digest("hex").slice(0, 16),
    metadata: {
      cryptoShredded: true,
      keyWasShredded,
      purgeAtIso: new Date(purgeAtMs).toISOString()
    }
  });

  return {
    uid,
    requestId,
    status: "PENDING_DELETION",
    cryptoShredded: true,
    keyExistedBeforeShred: keyWasShredded,
    deletionRequestedAtMs: now,
    deletionScheduledAtMs: purgeAtMs,
    coolingPeriodDays: DELETION_COOLING_DAYS,
    notice: "Your personal encryption key (DEK) has been permanently destroyed and your account is scheduled for final purge after the 30-day statutory cooling period."
  };
}

/**
 * Cancel pending account deletion during the 30-day cooling period
 */
async function cancelAccountDeletion({ uid, clientIp = "127.0.0.1" }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const privacyRepo = getRequiredPrivacyRepo();
  const authRepo = getRequiredAuthRepo();
  const user = await authRepo.getUser(uid);

  if (!user || user.status !== "PENDING_DELETION") {
    const err = new Error("Account is not currently pending deletion");
    err.code = "ERR_NOT_PENDING_DELETION";
    err.status = 400;
    throw err;
  }

  const now = Date.now();
  const updatedUser = await authRepo.updateUser(uid, {
    status: "ACTIVE",
    deletionScheduledAtMs: null,
    deletionCancelledAtMs: now,
    updatedAtMs: now
  });

  const requestId = `prv_cancel_${uid}_${now}`;
  await privacyRepo.savePrivacyRequest({
    id: requestId,
    requestId,
    uid,
    type: "CANCEL_DELETION",
    status: "COMPLETED",
    clientIp,
    createdAtMs: now
  });

  await authRepo.appendAuditLog({
    uid,
    eventType: "ACCOUNT_DELETION_CANCELLED",
    ipHash: crypto.createHash("sha256").update(clientIp).digest("hex").slice(0, 16),
    metadata: { cancelledAtMs: now }
  });

  return {
    uid,
    status: updatedUser.status,
    cancelledAtMs: now
  };
}

/**
 * Get user's DPDP Act 2023 consent preferences
 */
async function getUserConsents({ uid }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const authRepo = getRequiredAuthRepo();
  const user = (await authRepo.getUser(uid)) || { uid, consents: {} };
  const defaults = {
    termsAccepted: true,
    privacyNoticeAccepted: true,
    age18Confirmed: true,
    whatsappUpdates: true,
    allowWhatsAppReminders: true,
    emailNotifications: true,
    allowEmailReminders: true,
    optionalAnalytics: false,
    allowAnalytics: false
  };

  const merged = {
    ...defaults,
    ...(user.consents || {})
  };

  return {
    uid,
    ...merged,
    consents: merged,
    updatedAtMs: user.updatedAtMs || user.createdAtMs || Date.now()
  };
}

/**
 * Update or withdraw optional DPDP consents (WhatsApp reminders, analytics, email)
 */
async function updateUserConsents({ uid, consentsUpdates = {}, clientIp = "127.0.0.1" }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (consentsUpdates.revokeCoreTerms === true || consentsUpdates.termsAccepted === false) {
    const err = new Error("Withdrawing core Terms or Privacy Policy consent requires account deletion under DPDP Act 2023.");
    err.code = "ERR_MANDATORY_CONSENT_WITHDRAWAL";
    err.status = 400;
    throw err;
  }

  const privacyRepo = getRequiredPrivacyRepo();
  const authRepo = getRequiredAuthRepo();
  const existingUser = (await authRepo.getUser(uid)) || { uid, status: "ACTIVE", consents: {} };
  const currentConsents = existingUser.consents || {};
  const now = Date.now();

  const whatsappEnabled =
    consentsUpdates.allowWhatsAppReminders !== undefined
      ? Boolean(consentsUpdates.allowWhatsAppReminders)
      : (consentsUpdates.whatsappUpdates !== undefined
        ? Boolean(consentsUpdates.whatsappUpdates)
        : currentConsents.whatsappUpdates !== false);

  const emailEnabled =
    consentsUpdates.allowEmailReminders !== undefined
      ? Boolean(consentsUpdates.allowEmailReminders)
      : (consentsUpdates.emailNotifications !== undefined
        ? Boolean(consentsUpdates.emailNotifications)
        : currentConsents.emailNotifications !== false);

  const analyticsEnabled =
    consentsUpdates.allowAnalytics !== undefined
      ? Boolean(consentsUpdates.allowAnalytics)
      : (consentsUpdates.optionalAnalytics !== undefined
        ? Boolean(consentsUpdates.optionalAnalytics)
        : Boolean(currentConsents.optionalAnalytics));

  const updatedConsents = {
    ...currentConsents,
    termsAccepted: true,
    privacyNoticeAccepted: true,
    age18Confirmed: true,
    whatsappUpdates: whatsappEnabled,
    allowWhatsAppReminders: whatsappEnabled,
    emailNotifications: emailEnabled,
    allowEmailReminders: emailEnabled,
    optionalAnalytics: analyticsEnabled,
    allowAnalytics: analyticsEnabled,
    updatedAtMs: now
  };

  await authRepo.updateUser(uid, {
    ...existingUser,
    consents: updatedConsents,
    updatedAtMs: now
  });

  const requestId = `prv_consent_${uid}_${now}`;
  await privacyRepo.savePrivacyRequest({
    id: requestId,
    requestId,
    uid,
    type: "CONSENT_UPDATE",
    status: "COMPLETED",
    consents: updatedConsents,
    clientIp,
    createdAtMs: now
  });

  await authRepo.appendAuditLog({
    uid,
    eventType: "DPDP_CONSENT_UPDATED",
    ipHash: crypto.createHash("sha256").update(clientIp).digest("hex").slice(0, 16),
    metadata: updatedConsents
  });

  return {
    uid,
    ...updatedConsents,
    consents: updatedConsents,
    updatedAtMs: now
  };
}

async function recordConsent(uid, options = {}) {
  const authRepo = getRequiredAuthRepo();
  const user = (await authRepo.getUser(uid)) || { uid };
  const consents = {
    ...(user.consents || {}),
    accepted: true,
    privacyPolicyVersion: options.privacyPolicyVersion || "2026.05",
    marketingOptIn: Boolean(options.marketingOptIn),
    updatedAtMs: Date.now()
  };
  await authRepo.updateUser(uid, { consents });
  return consents;
}

async function getConsentStatus(uid) {
  const authRepo = getRequiredAuthRepo();
  const user = await authRepo.getUser(uid);
  const consents = user?.consents || {};
  return {
    accepted: consents.accepted !== false,
    privacyPolicyVersion: consents.privacyPolicyVersion || "2026.05",
    ...consents
  };
}

async function exportUserData(uid) {
  const ledgerRepo = getLedgerRepository();
  const authRepo = getRequiredAuthRepo();
  const user = await authRepo.getUser(uid);
  const people = (await ledgerRepo.listPeople(uid)) || [];
  const rawRecords = (await ledgerRepo.listRecords(uid)) || [];
  const records = rawRecords.map(r => ({
    ...r,
    amountPaise: r.amountPaise !== undefined ? r.amountPaise : r.principalAmountPaise
  }));
  const events = (ledgerRepo.listEvents ? await ledgerRepo.listEvents(uid) : []) || [];

  return {
    schemaVersion: "udhaar-dpdp-export-v1",
    uid,
    exportedAtMs: Date.now(),
    user: user || { uid },
    ledger: {
      people,
      records,
      events
    }
  };
}

async function deleteUserData(uid, { reason = "USER_REQUESTED_DPDP" } = {}) {
  const ledgerRepo = getLedgerRepository();
  const authRepo = getRequiredAuthRepo();
  
  const records = (await ledgerRepo.listRecords(uid)) || [];
  const people = (await ledgerRepo.listPeople(uid)) || [];

  let recordsDeleted = 0;
  for (const r of records) {
    if (ledgerRepo.deleteRecordPermanently) {
      await ledgerRepo.deleteRecordPermanently(uid, r.recordId || r.id);
    } else {
      await ledgerRepo.deleteRecord(uid, r.recordId || r.id);
    }
    recordsDeleted++;
  }

  let peopleDeleted = 0;
  for (const p of people) {
    if (ledgerRepo.deletePersonPermanently) {
      await ledgerRepo.deletePersonPermanently(uid, p.personId || p.id);
    } else {
      await ledgerRepo.deletePerson(uid, p.personId || p.id);
    }
    peopleDeleted++;
  }

  if (ledgerRepo.records && ledgerRepo.records.has(uid)) {
    ledgerRepo.records.get(uid).clear();
  }
  if (ledgerRepo.people && ledgerRepo.people.has(uid)) {
    ledgerRepo.people.get(uid).clear();
  }

  return {
    deleted: true,
    uid,
    reason,
    counts: {
      recordsDeleted,
      peopleDeleted
    }
  };
}

module.exports = {
  DELETION_COOLING_DAYS,
  DELETION_COOLING_MS,
  COOLING_PERIOD_MS,
  requestAccountDeletion,
  cancelAccountDeletion,
  getUserConsents,
  updateUserConsents,
  recordConsent,
  getConsentStatus,
  exportUserData,
  deleteUserData
};
