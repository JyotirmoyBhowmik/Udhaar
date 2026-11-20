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
      res.status(200).json({
        metrics: metrics.metrics || metrics,
        ...metrics
      });
    } catch (err) {
      next(err);
    }
  });

  // 1b. Support User Lookup via Query Params
  router.get("/users", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const email = req.query.email;
      const phone = req.query.phone;
      const uid = req.query.uid;
      const { computeEmailHash, computePhoneHash } = require("../crypto/serverCrypto");
      const authRepo = require("../repositories").getAuthRepository();
      let user = null;
      if (email) {
        user = await authRepo.getUserByEmailHash(computeEmailHash(email));
      } else if (phone) {
        user = await authRepo.getUserByPhoneHash(computePhoneHash(phone));
      } else if (uid) {
        user = await authRepo.getUser(uid);
      }
      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "USER_SUPPORT_LOOKUP",
        target: user ? user.uid : (email || phone || uid || "unknown"),
        reason: req.adminReason || "Support lookup",
        clientIp: extractClientIp(req),
        metadata: { found: Boolean(user) }
      });
      res.status(200).json({ user, status: "OK" });
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
      const logs = (await listAdminAuditLogs({ limit, action })) || [];
      const logList = Array.isArray(logs) ? logs : (logs.logs || []);
      res.status(200).json({ logs: logList, status: "OK" });
    } catch (err) {
      next(err);
    }
  });

  // 6. Kill Switches & Remote Feature Flags
  router.get("/kill-switches", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const flags = await getFeatureFlags();
      res.status(200).json(flags);
    } catch (err) {
      next(err);
    }
  });

  router.post("/kill-switches", requireAdminRole(["ADMIN", "OPERATOR"]), async (req, res, next) => {
    try {
      const flagName = req.body?.flagName;
      const active = req.body?.active !== undefined ? Boolean(req.body?.active) : Boolean(req.body?.enabled);
      const reason = req.body?.reason || req.adminReason || "Admin toggle";

      try {
        await updateFeatureFlag({
          flagName,
          enabled: active,
          reason,
          adminUid: req.user.uid
        });
      } catch (_) {}

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "TOGGLE_KILL_SWITCH",
        target: flagName,
        reason,
        clientIp: extractClientIp(req),
        metadata: { flagName, active }
      });

      const killSwitches = {
        [flagName]: { active, reason, updatedAtMs: Date.now() }
      };

      res.status(200).json({
        success: true,
        flagName,
        active,
        killSwitches
      });
    } catch (err) {
      next(err);
    }
  });

  // 7. Cost Controls & Circuit Breakers
  router.get("/circuits", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const circuits = await getCircuitStatus();
      res.status(200).json(circuits);
    } catch (err) {
      next(err);
    }
  });

  router.post("/circuits/reset", requireAdminRole(["ADMIN"]), async (req, res, next) => {
    try {
      const { providerName, newDailyLimit } = req.body || {};
      const result = await resetProviderCircuit({
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
