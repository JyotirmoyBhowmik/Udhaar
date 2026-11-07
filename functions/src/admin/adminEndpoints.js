/**
 * Admin Governance REST API Endpoints (functions/src/admin/adminEndpoints.js)
 * Implements Stage S8 Admin Console:
 * - Telemetry, masked identity lookups, link revocations, kill-switch overrides.
 * - Enforces requireAuth and requireAdminRole with mandatory audit justification reasons.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const { requireAdminRole } = require("../middleware/adminMiddleware");
const {
  getSystemOverviewMetrics,
  lookupUserMasked,
  setAccountLockState,
  revokeGuestLinkAdmin,
  listAdminAuditLogs,
  logAdminAction
} = require("./adminService");
const { getFeatureFlags, updateFeatureFlag } = require("../config/killSwitches");
const { getCircuitStatus, resetProviderCircuit } = require("../config/circuitBreakers");

function extractClientIp(req) {
  return (
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    "127.0.0.1"
  );
}

function createAdminRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. System Health & Overview (Admin, Operator, Auditor)
  router.get("/overview", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const metrics = await getSystemOverviewMetrics();
      res.status(200).json(metrics);
    } catch (err) {
      next(err);
    }
  });

  // 2. Masked User Lookup (Admin, Operator, Auditor)
  router.post("/users/lookup", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const { queryHash } = req.body || {};
      const clientIp = extractClientIp(req);
      const result = await lookupUserMasked({
        queryHash,
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        reason: req.adminReason || "Routine verification query",
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 3. Lock / Unlock Account (Admin, Operator)
  router.post("/users/:targetUid/lock", requireAdminRole(["ADMIN", "OPERATOR"]), async (req, res, next) => {
    try {
      const { targetUid } = req.params;
      const { locked } = req.body || {};
      const clientIp = extractClientIp(req);

      const result = await setAccountLockState({
        targetUid,
        locked,
        reason: req.adminReason,
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. Revoke Capability Guest Link (Admin, Operator)
  router.post("/links/:recordId/revoke", requireAdminRole(["ADMIN", "OPERATOR"]), async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const clientIp = extractClientIp(req);

      const result = await revokeGuestLinkAdmin({
        recordId,
        reason: req.adminReason,
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 5. Query Immutable Admin Audit Logs (Admin, Operator, Auditor)
  router.get("/audit-logs", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const { limit, action } = req.query;
      const logs = await listAdminAuditLogs({ limit, action });
      res.status(200).json(logs);
    } catch (err) {
      next(err);
    }
  });

  // 6. Kill Switches & Remote Feature Flags
  router.get("/kill-switches", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const flags = getFeatureFlags();
      res.status(200).json(flags);
    } catch (err) {
      next(err);
    }
  });

  router.post("/kill-switches", requireAdminRole(["ADMIN", "OPERATOR"]), async (req, res, next) => {
    try {
      const { flagName, enabled } = req.body || {};
      const result = updateFeatureFlag({
        flagName,
        enabled,
        reason: req.adminReason,
        adminUid: req.user.uid
      });

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "UPDATE_KILL_SWITCH",
        target: flagName,
        reason: req.adminReason,
        clientIp: extractClientIp(req),
        metadata: { flagName, enabled }
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 7. Cost Controls & Circuit Breakers
  router.get("/circuits", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const circuits = getCircuitStatus();
      res.status(200).json(circuits);
    } catch (err) {
      next(err);
    }
  });

  router.post("/circuits/reset", requireAdminRole(["ADMIN"]), async (req, res, next) => {
    try {
      const { providerName, newDailyLimit } = req.body || {};
      const result = resetProviderCircuit({
        providerName,
        newDailyLimit,
        reason: req.adminReason,
        adminUid: req.user.uid
      });

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "RESET_CIRCUIT_BREAKER",
        target: providerName,
        reason: req.adminReason,
        clientIp: extractClientIp(req),
        metadata: { providerName, newDailyLimit }
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 8. Security Guarantee: Non-Bypassable Lost-Number Cooling Period
  router.post("/users/:targetUid/cooling-period/bypass", requireAdminRole(["ADMIN"]), async (req, res) => {
    return res.status(403).json({
      type: "https://udhaar.app/errors/cooling-period-non-bypassable",
      title: "Forbidden",
      status: 403,
      code: "ERR_ADMIN_COOLING_PERIOD_NON_BYPASSABLE",
      detail: "Admins cannot bypass the statutory 30-day security cooling period for lost numbers under any circumstances."
    });
  });

  // 9. Security Guarantee: Ledger Content Reading is Strictly Denied
  router.get("/users/:targetUid/records", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res) => {
    return res.status(403).json({
      type: "https://udhaar.app/errors/ledger-access-denied",
      title: "Forbidden",
      status: 403,
      code: "ERR_ADMIN_LEDGER_ACCESS_DENIED",
      detail: "User ledger contents are protected by end-to-end envelope encryption and are strictly inaccessible to administrative personnel."
    });
  });

  return router;
}

module.exports = {
  createAdminRouter
};
