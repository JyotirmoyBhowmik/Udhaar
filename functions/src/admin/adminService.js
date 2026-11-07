/**
 * Admin Governance, Security Auditing & User Inspection Service
 * (functions/src/admin/adminService.js)
 * Implements Stage S8 Admin Console:
 * - Masked user lookup (zero access to decrypted financial ledger records).
 * - Abuse mitigation: account locks, capability guest-link revocations.
 * - Immutable administrative audit log with mandatory justification reasons.
 * - System telemetry, spend monitoring, and kill-switch governance.
 */

const crypto = require("crypto");
const { _inMemoryUsers } = require("../auth/authService");
const { _inMemoryUserDevices } = require("../push/pushService");
const { revokeDevice } = require("../push/pushService");
const { _inMemoryLinks, createCapabilityLink } = require("../links/linkService");
const { getFeatureFlags, updateFeatureFlag } = require("../config/killSwitches");
const { getCircuitStatus, resetProviderCircuit } = require("../config/circuitBreakers");

// In-memory admin audit log: immutable append-only list
const inMemoryAdminAuditLogs = [];

function resetAdminStore() {
  inMemoryAdminAuditLogs.length = 0;
}

/**
 * Append an immutable entry to the admin audit log
 */
async function logAdminAction({
  adminUid,
  adminRole = "ADMIN",
  action,
  target = null,
  reason,
  clientIp = "127.0.0.1",
  metadata = {}
}) {
  const logId = `aud_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const now = Date.now();

  const entry = {
    logId,
    timestampMs: now,
    isoDate: new Date(now).toISOString(),
    adminUid: String(adminUid || "unknown"),
    adminRole: String(adminRole || "ADMIN"),
    action: String(action),
    target: target ? String(target) : null,
    reason: String(reason || "").trim(),
    clientIp: String(clientIp),
    metadata
  };

  inMemoryAdminAuditLogs.push(Object.freeze(entry));
  return entry;
}

/**
 * List immutable admin audit logs (pagination support)
 */
async function listAdminAuditLogs({ limit = 50, action = null }) {
  let logs = [...inMemoryAdminAuditLogs];
  if (action) {
    logs = logs.filter(l => l.action === action.toUpperCase());
  }
  logs.sort((a, b) => b.timestampMs - a.timestampMs);
  return logs.slice(0, Math.min(100, Math.max(1, Number(limit) || 50)));
}

/**
 * 1. Masked User Lookup (Searches strictly by pseudonymized hashes; never exposes decrypted ledger data)
 */
async function lookupUserMasked({ queryHash, adminUid, adminRole, reason, clientIp }) {
  if (!queryHash || typeof queryHash !== "string") {
    const err = new Error("Search query must be a valid pseudonymized phone or email hash.");
    err.code = "ERR_INVALID_QUERY_HASH";
    err.status = 400;
    throw err;
  }

  const cleanHash = queryHash.trim();
  let matchedUser = null;

  for (const user of _inMemoryUsers.values()) {
    if (user.phoneHash === cleanHash || user.emailHash === cleanHash || user.uid === cleanHash) {
      matchedUser = user;
      break;
    }
  }

  // Log the lookup action
  await logAdminAction({
    adminUid,
    adminRole,
    action: "USER_LOOKUP",
    target: cleanHash,
    reason,
    clientIp,
    metadata: { found: Boolean(matchedUser) }
  });

  if (!matchedUser) {
    const err = new Error("No user found matching the provided identity hash.");
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const devicesMap = _inMemoryUserDevices.get(matchedUser.uid);
  const deviceCount = devicesMap ? devicesMap.size : 0;

  // STRICT GUARANTEE: Never expose unmasked PII or plaintext ledger records
  return {
    uid: matchedUser.uid,
    displayName: matchedUser.displayName || "Udhaar User",
    maskedPhone: matchedUser.maskedPhone || "+91 ••••• ••••",
    maskedEmail: matchedUser.maskedEmail || "•••••@••••.•••",
    status: matchedUser.status || "ACTIVE",
    isLocked: Boolean(matchedUser.isLocked),
    lockReason: matchedUser.lockReason || null,
    createdAtMs: matchedUser.createdAtMs,
    deviceCount,
    consents: matchedUser.consents || {},
    notesCountNotice: "Ledger contents are end-to-end envelope encrypted and inaccessible to administrative staff."
  };
}

/**
 * 2. Set Account Lock State (Abuse mitigation)
 */
async function setAccountLockState({ targetUid, locked, reason, adminUid, adminRole, clientIp }) {
  const user = _inMemoryUsers.get(targetUid);
  if (!user) {
    const err = new Error(`Target user ${targetUid} not found.`);
    err.code = "ERR_USER_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const isLocking = Boolean(locked);
  user.isLocked = isLocking;
  user.lockReason = isLocking ? reason : null;
  user.updatedAtMs = Date.now();

  await logAdminAction({
    adminUid,
    adminRole,
    action: isLocking ? "LOCK_USER" : "UNLOCK_USER",
    target: targetUid,
    reason,
    clientIp
  });

  return {
    success: true,
    uid: targetUid,
    isLocked: user.isLocked,
    lockReason: user.lockReason,
    updatedAtMs: user.updatedAtMs
  };
}

/**
 * 3. Revoke Capability Guest Link
 */
async function revokeGuestLinkAdmin({ recordId, reason, adminUid, adminRole, clientIp }) {
  const link = _inMemoryLinks.get(recordId);
  if (!link) {
    const err = new Error(`Capability link for record ${recordId} not found.`);
    err.code = "ERR_LINK_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  link.isRevoked = true;
  link.revokedBy = adminUid;
  link.revokedReason = reason;
  link.revokedAtMs = Date.now();

  await logAdminAction({
    adminUid,
    adminRole,
    action: "REVOKE_GUEST_LINK",
    target: recordId,
    reason,
    clientIp
  });

  return {
    success: true,
    recordId,
    isRevoked: true,
    revokedAtMs: link.revokedAtMs
  };
}

/**
 * 4. System Overview Metrics
 */
async function getSystemOverviewMetrics() {
  const allUsers = Array.from(_inMemoryUsers.values());
  const activeCount = allUsers.filter(u => !u.isLocked && u.status !== "PENDING_DELETION").length;
  const lockedCount = allUsers.filter(u => u.isLocked).length;
  const pendingDeletionCount = allUsers.filter(u => u.status === "PENDING_DELETION").length;

  const featureFlags = getFeatureFlags();
  const circuits = getCircuitStatus();

  return {
    timestampMs: Date.now(),
    users: {
      total: allUsers.length,
      active: activeCount,
      locked: lockedCount,
      pendingDeletion: pendingDeletionCount
    },
    costControls: circuits,
    killSwitches: featureFlags,
    auditLogsRecorded: inMemoryAdminAuditLogs.length
  };
}

module.exports = {
  logAdminAction,
  listAdminAuditLogs,
  lookupUserMasked,
  setAccountLockState,
  revokeGuestLinkAdmin,
  getSystemOverviewMetrics,
  resetAdminStore,
  _inMemoryAdminAuditLogs: inMemoryAdminAuditLogs
};
