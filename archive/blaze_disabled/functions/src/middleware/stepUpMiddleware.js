/**
 * Step-Up Authentication Middleware & Service (functions/src/middleware/stepUpMiddleware.js)
 * 
 * Enforces high-assurance re-authentication (fresh email OTP or biometric passkey completed
 * within the last 10 minutes) for sensitive operations:
 * - Data exports (CSV, statement, full portable JSON archive)
 * - Account deletion (Right to Erasure & cryptographic shredding)
 * - Revoking other active sessions or devices
 * - Updating phone number or email address
 */

const crypto = require("crypto");
const { sendProblemDetails } = require("./authMiddleware");
const { getSecret } = require("../config/secrets");
const { getSessionRepository } = require("../repositories");

const STEP_UP_WINDOW_MS = 10 * 60 * 1000; // 10 minutes (600,000 ms)

/**
 * Mint a signed step-up token for 10-minute validity
 * @param {string} uid
 * @returns {string} Step-up token
 */
function generateStepUpToken(uid) {
  const now = Date.now();
  const expiresAt = now + STEP_UP_WINDOW_MS;
  const payload = `${uid}:${now}:${expiresAt}`;
  const secret = getSecret("UDHAAR_SERVER_SECRET");
  const signature = crypto.createHmac("sha256", secret).update(`step_up:${payload}`).digest("hex");
  return Buffer.from(`${payload}:${signature}`).toString("base64url");
}

/**
 * Verify a step-up token
 * @param {string} token
 * @param {string} expectedUid
 * @returns {boolean}
 */
function verifyStepUpToken(token, expectedUid) {
  if (!token || typeof token !== "string") return false;

  try {
    const decoded = Buffer.from(token, "base64url").toString("utf8");
    const parts = decoded.split(":");
    if (parts.length !== 4) return false;

    const [uid, timestampStr, expiresAtStr, signature] = parts;
    if (uid !== expectedUid) return false;

    const expiresAt = Number(expiresAtStr);
    if (Date.now() > expiresAt) return false;

    const payload = `${uid}:${timestampStr}:${expiresAtStr}`;
    const secret = getSecret("UDHAAR_SERVER_SECRET");
    const expectedSig = crypto.createHmac("sha256", secret).update(`step_up:${payload}`).digest("hex");

    return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expectedSig));
  } catch (_) {
    return false;
  }
}

/**
 * Middleware enforcing that the requesting session has completed authentication
 * or step-up verification within the last 10 minutes.
 */
async function requireStepUpAuth(req, res, next) {
  if (!req.user || !req.user.uid) {
    return sendProblemDetails(res, {
      status: 401,
      code: "ERR_UNAUTHENTICATED",
      title: "Unauthorized",
      detail: "Authentication required prior to step-up verification."
    });
  }

  const now = Date.now();
  const lastStepUp = req.user.lastStepUpAtMs || req.user.createdAtMs || 0;
  const isFresh = (now - lastStepUp) <= STEP_UP_WINDOW_MS;

  if (isFresh) {
    return next();
  }

  // Check for X-Step-Up-Token header
  const stepUpHeader = req.headers["x-step-up-token"];
  if (stepUpHeader && verifyStepUpToken(stepUpHeader, req.user.uid)) {
    return next();
  }

  return sendProblemDetails(res, {
    status: 403,
    code: "ERR_STEP_UP_REQUIRED",
    title: "Forbidden",
    detail: "Step-up authentication required: this sensitive operation requires fresh verification completed within the last 10 minutes."
  });
}

module.exports = {
  requireStepUpAuth,
  generateStepUpToken,
  verifyStepUpToken,
  STEP_UP_WINDOW_MS
};
