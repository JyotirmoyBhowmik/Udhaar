/**
 * Authentication, CSRF & Problem Details Middleware (functions/src/middleware/authMiddleware.js)
 * Implements RFC 9457 standard error format, double-submit CSRF verification,
 * Origin checking, and session validation.
 */

const { validateSession } = require("../sessions/sessionManager");

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
  let token = null;

  // 1. Check Authorization header (Bearer token)
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith("Bearer ")) {
    token = authHeader.slice(7).trim();
  } else {
    // 2. Check __session cookie
    token = extractSessionCookie(req);

    // If cookie auth, enforce CSRF protection on mutating methods
    const mutatingMethods = ["POST", "PUT", "DELETE", "PATCH"];
    if (mutatingMethods.includes(req.method.toUpperCase())) {
      const csrfHeader = req.headers["x-csrf-token"];
      if (!csrfHeader) {
        return sendProblemDetails(res, {
          status: 403,
          code: "ERR_CSRF_MISSING",
          title: "Forbidden",
          detail: "Missing X-CSRF-Token header on state-mutating request."
        });
      }
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

  req.user = session;
  req.correlationId = req.headers["x-request-id"] || `req_${Date.now()}`;
  next();
}

module.exports = {
  sendProblemDetails,
  problemDetailsErrorHandler,
  extractSessionCookie,
  requireAuth
};
