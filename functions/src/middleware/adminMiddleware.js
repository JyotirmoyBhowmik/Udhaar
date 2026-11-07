/**
 * Admin Role-Based Access Control (RBAC) & Audit Guard Middleware
 * (functions/src/middleware/adminMiddleware.js)
 * Implements Stage S8 Admin Governance:
 * - Role hierarchy: ADMIN > OPERATOR > AUDITOR.
 * - Enforces mandatory non-empty audit reason on all state mutations.
 * - Protects against privilege escalation and unauthorized governance calls.
 */

const { sendProblemDetails } = require("./authMiddleware");

// In-memory admin roles mapping for emulator/test environment: uid -> role
const inMemoryAdminRoles = new Map();

function setAdminRole(uid, role) {
  const validRoles = ["ADMIN", "OPERATOR", "AUDITOR"];
  const r = String(role || "").toUpperCase();
  if (!validRoles.includes(r)) {
    throw new Error(`Invalid admin role: '${role}'. Allowed: ADMIN, OPERATOR, AUDITOR.`);
  }
  inMemoryAdminRoles.set(uid, r);
}

function removeAdminRole(uid) {
  inMemoryAdminRoles.delete(uid);
}

function clearAdminRoles() {
  inMemoryAdminRoles.clear();
}

/**
 * Higher-order middleware factory requiring specific admin role(s)
 */
function requireAdminRole(allowedRoles = ["ADMIN", "OPERATOR", "AUDITOR"]) {
  const normalizedAllowed = Array.isArray(allowedRoles)
    ? allowedRoles.map(r => r.toUpperCase())
    : [allowedRoles.toUpperCase()];

  return async (req, res, next) => {
    // 1. Ensure user is authenticated
    if (!req.user || !req.user.uid) {
      return sendProblemDetails(res, {
        status: 401,
        code: "ERR_UNAUTHORIZED",
        title: "Unauthorized",
        detail: "Admin credentials required."
      });
    }

    // 2. Resolve admin role
    const userRole = inMemoryAdminRoles.get(req.user.uid) || req.user.role || (req.user.claims && req.user.claims.adminRole);

    if (!userRole || !normalizedAllowed.includes(String(userRole).toUpperCase())) {
      return sendProblemDetails(res, {
        status: 403,
        code: "ERR_FORBIDDEN",
        title: "Forbidden",
        detail: `Access restricted. Required role: [${normalizedAllowed.join(", ")}]. Current role: '${userRole || "NONE"}'.`
      });
    }

    req.adminRole = String(userRole).toUpperCase();

    // 3. For state-mutating requests, enforce mandatory justification reason
    const mutatingMethods = ["POST", "PUT", "DELETE", "PATCH"];
    if (mutatingMethods.includes(req.method.toUpperCase())) {
      const reason = req.headers["x-admin-reason"] || (req.body && req.body.reason);
      if (!reason || typeof reason !== "string" || reason.trim().length < 5) {
        return sendProblemDetails(res, {
          status: 400,
          code: "ERR_ADMIN_REASON_REQUIRED",
          title: "Bad Request",
          detail: "Admin state modifications require a non-empty audit reason (at least 5 characters) via X-Admin-Reason header or body.reason."
        });
      }
      req.adminReason = reason.trim();
    }

    next();
  };
}

module.exports = {
  requireAdminRole,
  setAdminRole,
  removeAdminRole,
  clearAdminRoles,
  _inMemoryAdminRoles: inMemoryAdminRoles
};
