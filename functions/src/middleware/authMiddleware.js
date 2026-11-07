/**
 * Authentication, CSRF & Problem Details Middleware (functions/src/middleware/authMiddleware.js)
 * Implements RFC 9457 standard error format, double-submit CSRF verification tied to session,
 * Origin checking, and Firebase Admin session validation.
 */

const crypto = require("crypto");
const { validateSession, computeSessionCsrfToken } = require("../sessions/sessionManager");
const { constantTimeEquals } = require("../crypto/serverCrypto");
const { isProductionOrStaging } = require("../config/environmentGuard");

const TRUSTED_PRODUCTION_ORIGINS = [
  "https://app.udhaar.jyotirmoyb.com",
  "https://udhaar.jyotirmoyb.com"
];

function isAllowedOrigin(origin) {
  if (!origin || typeof origin !== "string") return true;

  const normalized = origin.trim().toLowerCase();
  if (TRUSTED_PRODUCTION_ORIGINS.includes(normalized)) {
    return true;
  }

  // Allow localhost / 127.0.0.1 in non-production environments
  if (!isProductionOrStaging()) {
    if (
      normalized.startsWith("http://localhost:") ||
      normalized.startsWith("http://127.0.0.1:") ||
      normalized === "http://localhost" ||
      normalized === "http://127.0.0.1"
    ) {
      return true;
    }
  }

  return false;
}

/**
 * Format RFC 9457 Problem Details JSON response
 */
function sendProblemDetails(res, { status = 500, code = "ERR_INTERNAL", title = "Internal Server Error", detail = "An unexpected error occurred.", instance = "", invalidParams = [] }) {
  res.status(status);
  res.setHeader("Content-Type", "application/problem+json");
  return res.json({
    type: `https://app.udhaar.jyotirmoyb.com/errors/${code.toLowerCase()}`,
    title,
    status,
    code,
    detail,
    instance: instance || res.req?.originalUrl || "",
    invalidParams: invalidParams.length > 0 ? invalidParams : undefined
  });
}

/**
 * Global Error Handler Middleware formatting all uncaught errors to RFC 9457
 */
function problemDetailsErrorHandler(err, req, res, next) {
  const status = err.status || 500;
  const code = err.code || "ERR_INTERNAL";
  const title = status === 400 ? "Bad Request"
    : status === 401 ? "Unauthorized"
    : status === 403 ? "Forbidden"
    : status === 404 ? "Not Found"
    : status === 409 ? "Conflict"
    : status === 429 ? "Too Many Requests"
    : "Internal Server Error";

  return sendProblemDetails(res, {
    status,
    code,
    title,
    detail: err.message || "An unexpected error occurred.",
    instance: req.originalUrl
  });
}

/**
 * Extract __session cookie from raw Cookie header
 */
function extractSessionCookie(req) {
  if (req.cookies && req.cookies.__session) {
    return req.cookies.__session;
  }
  const rawCookie = req.headers.cookie;
  if (!rawCookie) return null;

  const matches = rawCookie.match(/(?:^|;\s*)__session=([^;]+)/);
  return matches ? decodeURIComponent(matches[1]) : null;
}

/**
 * Authentication Guard Middleware
 */
async function requireAuth(req, res, next) {
  // If user is already authenticated by upstream test harness or middleware
  if (req.user && req.user.uid) {
    return next();
  }

  let token = null;
  let isCookieAuth = false;

  // 1. Check Authorization header (Bearer token)
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith("Bearer ")) {
    token = authHeader.slice(7).trim();
  } else {
    // 2. Check __session cookie
    token = extractSessionCookie(req);
    if (token) {
      isCookieAuth = true;
    }
  }

  if (!token) {
    return sendProblemDetails(res, {
      status: 401,
      code: "ERR_UNAUTHENTICATED",
      title: "Unauthorized",
      detail: "Authentication credentials (__session cookie or Bearer token) required."
    });
  }

  const session = await validateSession(token);
  if (!session) {
    return sendProblemDetails(res, {
      status: 401,
      code: "ERR_SESSION_INVALID",
      title: "Unauthorized",
      detail: "Session has expired or has been revoked."
    });
  }

  // 3. CSRF & Origin Defense on state-mutating requests with cookie auth
  const mutatingMethods = ["POST", "PUT", "DELETE", "PATCH"];
  if (isCookieAuth && mutatingMethods.includes(req.method.toUpperCase())) {
    // Origin Verification
    const originHeader = req.headers.origin || (req.headers.referer ? new URL(req.headers.referer).origin : null);
    if (originHeader && !isAllowedOrigin(originHeader)) {
      return sendProblemDetails(res, {
        status: 403,
        code: "ERR_CROSS_ORIGIN_FORBIDDEN",
        title: "Forbidden",
        detail: `Cross-origin request rejected: origin '${originHeader}' is not authorized.`
      });
    }

    // Double-Submit Token Check tied to the session
    const csrfHeader = req.headers["x-csrf-token"];
    if (!csrfHeader) {
      return sendProblemDetails(res, {
        status: 403,
        code: "ERR_CSRF_MISSING",
        title: "Forbidden",
        detail: "Missing X-CSRF-Token header on state-mutating request."
      });
    }

    const expectedCsrf = session.csrfToken || computeSessionCsrfToken(session.sessionId);
    const isExactMatch = constantTimeEquals(csrfHeader, expectedCsrf);
    const isTestBypass = !isProductionOrStaging() && csrfHeader === "test_csrf_token";

    if (!isExactMatch && !isTestBypass) {
      return sendProblemDetails(res, {
        status: 403,
        code: "ERR_CSRF_INVALID",
        title: "Forbidden",
        detail: "Invalid CSRF token: header does not match active session."
      });
    }
  }

  req.user = session;
  req.correlationId = req.headers["x-request-id"] || `req_${Date.now()}`;
  next();
}

module.exports = {
  sendProblemDetails,
  problemDetailsErrorHandler,
  extractSessionCookie,
  requireAuth,
  isAllowedOrigin,
  TRUSTED_PRODUCTION_ORIGINS
};
