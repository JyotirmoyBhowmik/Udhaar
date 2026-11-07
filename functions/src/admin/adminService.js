/**
 * Admin Governance, Abuse Mitigation & Immutable Audit Log Service
 * (functions/src/admin/adminService.js)
 * Implements Stage S8 Admin Capabilities:
 * - Append-only admin audit logs persisted in Firestore via AdminRepository.
 * - Masked user lookup by SHA-256 phone/email hash (zero plaintext PII or decrypted ledger notes).
 * - Account lock/unlock & capability link revocation with mandatory audit justification.
 * - System health & operational telemetry overview.
 */

const crypto = require("crypto");
const { getFeatureFlags } = require("../config/killSwitches");
const { getCircuitStatus } = require("../config/circuitBreakers");
const {
  getAdminRepository,
  getAuthRepository,
  getSessionRepository,
  getLinkRepository,
  assertRepoMethod
} = require("../repositories");

function getRequiredAdminRepo() {
  const repo = getAdminRepository();
  assertRepoMethod(repo, "appendAuditLog", "AdminRepository");
  assertRepoMethod(repo, "listAuditLogs", "AdminRepository");
  return repo;
}

function getRequiredAuthRepo() {
  const repo = getAuthRepository();
  assertRepoMethod(repo, "getUser", "AuthRepository");
  assertRepoMethod(repo, "updateUser", "AuthRepository");
  assertRepoMethod(repo, "listUsers", "AuthRepository");
  assertRepoMethod(repo, "findUserByQueryHash", "AuthRepository");
  return repo;
}

/**
 * Append an immutable entry to the Admin Audit Log in Firestore
 */
async function logAdminAction({
  adminUid,
  adminRole = "ADMIN",
  action,
  target,
  reason,
  clientIp = "127.0.0.1",
  metadata = {}
}) {
  if (!adminUid || !action) {
    throw new Error("adminUid and action are required for admin audit log");
  }
  if (!reason || typeof reason !== "string" || reason.trim().length < 5) {
    const err = new Error("Mandatory audit justification reason (min 5 chars) is required.");
    err.code = "ERR_ADMIN_REASON_REQUIRED";
    err.status = 400;
    throw err;
  }

  const adminRepo = getRequiredAdminRepo();
  const now = Date.now();
  const logId = `adm_${now}_${crypto.randomBytes(4).toString("hex")}`;
  const entry = {
    logId,
    id: logId,
    adminUid,
    adminRole,
    action,
    target: target || "system",
    reason: reason.trim(),
    clientIp,
    metadata: metadata ? JSON.parse(JSON.stringify(metadata)) : {},
    timestampMs: now
  };

  return adminRepo.appendAuditLog(entry);
}

/**
 * List immutable admin audit logs (newest first)
 */
async function listAdminAuditLogs({ limit = 100, action = null } = {}) {
  const adminRepo = getRequiredAdminRepo();
  return adminRepo.listAuditLogs({ limit, action });
}

/**
 * System overview & operational metrics for Admin Console
 */
async function getSystemOverviewMetrics() {
  const authRepo = getRequiredAuthRepo();
  const sessionRepo = getSessionRepository();
  const linkRepo = getLinkRepository();
  const adminRepo = getRequiredAdminRepo();

  assertRepoMethod(sessionRepo, "countActiveDevices", "SessionRepository");
  assertRepoMethod(linkRepo, "countActiveWebLinks", "LinkRepository");

  const users = await authRepo.listUsers();
  let activeUsers = 0;
  let lockedUsers = 0;
  let pendingDeletionUsers = 0;

  for (const user of users) {
    if (user.status === "LOCKED" || user.isLocked) lockedUsers++;
    else if (user.status === "PENDING_DELETION") pendingDeletionUsers++;
    else activeUsers++;
  }

  const [
    totalRegisteredDevices,
    activeCapabilityLinks,
    killSwitches,
    circuitBreakers,
    auditLogs
  ] = await Promise.all([
    sessionRepo.countActiveDevices(),
    linkRepo.countActiveWebLinks(),
    getFeatureFlags(),
    getCircuitStatus(),
    adminRepo.listAuditLogs({ limit: 500 })
  ]);

  return {
    timestampMs: Date.now(),
    users: {
      total: users.length,
      active: activeUsers,
      locked: lockedUsers,
      pendingDeletion: pendingDeletionUsers
    },
    devices: {
      totalRegistered: totalRegisteredDevices
    },
    guestLinks: {
      activeCount: activeCapabilityLinks
    },
    killSwitches,
    circuitBreakers,
    costControls: circuitBreakers,
    auditLogCount: auditLogs.length
  };
}

/**
 * Masked User Lookup by SHA-256 phoneHash, emailHash, or exact UID.
 * Strictly returns masked metadata ONLY — NEVER returns decrypted ledger notes or party names.
 */
async function lookupUserMasked({ queryHash, adminUid, adminRole, reason, clientIp }) {
  const cleanHash = String(queryHash || "").trim();
  if (!cleanHash || cleanHash.length < 4) {
    const err = new Error("Valid queryHash or UID is required for lookup");
    err.code = "ERR_INVALID_LOOKUP_QUERY";
    err.status = 400;
    throw err;
  }

  const authRepo = getRequiredAuthRepo();
  const matchedUser = await authRepo.findUserByQueryHash(cleanHash);

  await logAdminAction({
    adminUid,
    adminRole,
    action: "USER_LOOKUP_MASKED",
    target: matchedUser ? matchedUser.uid : `hash:${cleanHash.slice(0, 12)}`,
    reason: reason || "Support inquiry masked lookup",
    clientIp,
    metadata: { found: Boolean(matchedUser) }
  });

  if (!matchedUser) {
    const err = new Error("No user found matching the provided identifier hash");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const sessionRepo = getSessionRepository();
  assertRepoMethod(sessionRepo, "listUserDevices", "SessionRepository");
  const devices = await sessionRepo.listUserDevices(matchedUser.uid);

  return {
    uid: matchedUser.uid,
    maskedPhone: matchedUser.maskedPhone || (matchedUser.phoneHash ? `sha256:${matchedUser.phoneHash.slice(0, 10)}...` : "••••"),
    maskedEmail: matchedUser.maskedEmail || (matchedUser.emailHash ? `sha256:${matchedUser.emailHash.slice(0, 10)}...` : "••••"),
    status: matchedUser.status || "ACTIVE",
    isLocked: Boolean(matchedUser.isLocked || matchedUser.status === "LOCKED"),
    lockReason: matchedUser.lockReason || null,
    createdAtMs: matchedUser.createdAtMs || null,
    deletionScheduledAtMs: matchedUser.deletionScheduledAtMs || null,
    deviceCount: devices.length,
    consents: matchedUser.consents || {},
    notesCountNotice: "Ledger contents are end-to-end envelope encrypted and inaccessible to administrators."
  };
}

/**
 * Lock or Unlock a user account for abuse mitigation
 */
async function setAccountLockState({
  targetUid,
  locked,
  reason,
  adminUid,
  adminRole,
  clientIp
}) {
  if (!targetUid) {
    const err = new Error("targetUid is required");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  const authRepo = getRequiredAuthRepo();
  const user = await authRepo.getUser(targetUid);
  if (!user) {
    const err = new Error("Target user account not found");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const isLocked = Boolean(locked);
  const now = Date.now();
  const updatedUser = await authRepo.updateUser(targetUid, {
    isLocked,
    status: isLocked ? "LOCKED" : "ACTIVE",
    lockReason: isLocked ? reason : null,
    lockedAtMs: isLocked ? now : null,
    updatedAtMs: now
  });

  const auditEntry = await logAdminAction({
    adminUid,
    adminRole,
    action: isLocked ? "LOCK_USER_ACCOUNT" : "UNLOCK_USER_ACCOUNT",
    target: targetUid,
    reason,
    clientIp,
    metadata: { previousStatus: user.status, newStatus: updatedUser.status }
  });

  return {
    uid: targetUid,
    isLocked: updatedUser.isLocked,
    status: updatedUser.status,
    lockReason: updatedUser.lockReason,
    auditLogId: auditEntry.logId
  };
}

/**
 * Revoke a guest capability link (Abuse / Harassment report mitigation)
 */
async function revokeGuestLinkAdmin({
  recordId,
  reason,
  adminUid,
  adminRole,
  clientIp
}) {
  if (!recordId) {
    const err = new Error("recordId is required");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  const linkRepo = getLinkRepository();
  assertRepoMethod(linkRepo, "getWebLink", "LinkRepository");
  assertRepoMethod(linkRepo, "updateWebLink", "LinkRepository");

  const linkMeta = await linkRepo.getWebLink(recordId);
  if (!linkMeta) {
    const err = new Error("Capability link not found");
    err.code = "ERR_LINK_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  await linkRepo.updateWebLink(recordId, {
    isRevoked: true,
    revokedAtMs: now,
    revokedByAdminUid: adminUid,
    revocationReason: reason
  });

  const auditEntry = await logAdminAction({
    adminUid,
    adminRole,
    action: "REVOKE_CAPABILITY_LINK",
    target: recordId,
    reason,
    clientIp,
    metadata: { creatorUid: linkMeta.creatorUid }
  });

  return {
    recordId,
    isRevoked: true,
    revokedAtMs: now,
    auditLogId: auditEntry.logId
  };
}

async function resetAdminAuditStore() {
  const adminRepo = getAdminRepository();
  assertRepoMethod(adminRepo, "clear", "AdminRepository");
  await adminRepo.clear();
}

const resetAdminStore = resetAdminAuditStore;
const clearAuditLogs = resetAdminAuditStore;

module.exports = {
  logAdminAction,
  listAdminAuditLogs,
  getSystemOverviewMetrics,
  lookupUserMasked,
  setAccountLockState,
  revokeGuestLinkAdmin,
  resetAdminAuditStore,
  resetAdminStore,
  clearAuditLogs
};
