/**
 * Admin Role-Based Access Control (RBAC) & Audit Guard Middleware
 * (functions/src/middleware/adminMiddleware.js)
 * Implements Stage S8 Admin Governance:
 * - Role hierarchy: ADMIN > OPERATOR > AUDITOR.
 * - Enforces mandatory non-empty audit reason on all state mutations.
 * - Protects against privilege escalation and unauthorized governance calls.
 */

const { sendProblemDetails } = require("./authMiddleware");

const adminRoles = Object.create(null);

function setAdminRole(uid, role) {
  const validRoles = ["ADMIN", "OPERATOR", "AUDITOR"];
  const r = String(role || "").toUpperCase();
  if (!validRoles.includes(r)) {
    throw new Error(`Invalid admin role: '${role}'. Allowed: ADMIN, OPERATOR, AUDITOR.`);
  }
  adminRoles[uid] = r;
}

function removeAdminRole(uid) {
  delete adminRoles[uid];
}

function clearAdminRoles() {
  for (const k of Object.keys(adminRoles)) {
    delete adminRoles[k];
  }
}

const _inMemoryAdminRoles = new Proxy(adminRoles, {
  get(target, prop) {
    if (prop === "get") return (k) => target[k];
    if (prop === "set") return (k, v) => { target[k] = v; return target; };
    if (prop === "has") return (k) => Object.prototype.hasOwnProperty.call(target, k);
    if (prop === "delete") return (k) => { delete target[k]; return true; };
    if (prop === "clear") return () => clearAdminRoles();
    if (prop === "size") return Object.keys(target).length;
    return target[prop];
  }
});

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

    // 2. IP Allowlist Enforcement
    const allowlistEnv = process.env.ADMIN_IP_ALLOWLIST;
    const clientIp = (
      (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
      req.ip ||
      req.socket?.remoteAddress ||
      "127.0.0.1"
    );

    if (allowlistEnv) {
      const allowedIps = allowlistEnv.split(",").map(ip => ip.trim()).filter(Boolean);
      const isAllowed = allowedIps.includes(clientIp) || allowedIps.includes("*");
      if (!isAllowed) {
        return sendProblemDetails(res, {
          status: 403,
          code: "ERR_ADMIN_IP_NOT_ALLOWED",
          title: "Forbidden",
          detail: "Access denied. Client IP is not in admin allowlist."
        });
      }
    }

    // 3. Multi-Factor Authentication (MFA) Enforcement
    const claims = req.user.claims || {};
    const hasMfa = claims.mfa_enforced === true ||
      claims.sign_in_second_factor != null ||
      (claims.firebase && claims.firebase.sign_in_second_factor != null);

    const isProductionOrStaging = ["production", "staging"].includes(process.env.NODE_ENV) ||
      ["production", "staging"].includes(process.env.UDHAAR_ENV);

    if ((isProductionOrStaging && !hasMfa) || claims.mfa_enforced === false) {
      return sendProblemDetails(res, {
        status: 403,
        code: "ERR_ADMIN_MFA_REQUIRED",
        title: "Forbidden",
        detail: "Multi-Factor Authentication (MFA) is strictly required for administrative console access."
      });
    }

    // 4. Resolve admin role
    const userRole = adminRoles[req.user.uid] || req.user.role || (req.user.claims && req.user.claims.adminRole);

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
  _inMemoryAdminRoles
};
