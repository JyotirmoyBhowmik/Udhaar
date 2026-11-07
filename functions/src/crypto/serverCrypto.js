/**
 * Server Cryptographic Primitives (functions/src/crypto/serverCrypto.js)
 * Implements HMAC blind indexing, secure random OTP generation,
 * and timing-safe comparisons.
 */

const crypto = require("crypto");

const FALLBACK_TEST_SECRET = "udhaar_fallback_test_secret_32_bytes_long!!";

function getServerSecret() {
  return process.env.UDHAAR_SERVER_SECRET || FALLBACK_TEST_SECRET;
}

/**
 * Generate a cryptographically secure 6-digit numeric OTP
 */
function generateOtpCode() {
  return crypto.randomInt(100000, 1000000).toString();
}

/**
 * Compute blind index HMAC for phone numbers (E.164)
 */
function computePhoneHash(phoneE164) {
  if (!phoneE164) return "";
  const cleanPhone = phoneE164.trim();
  return crypto.createHmac("sha256", getServerSecret()).update(`phone:${cleanPhone}`).digest("hex");
}

/**
 * Compute blind index HMAC for normalized emails
 */
function computeEmailHash(normalizedEmail) {
  if (!normalizedEmail) return "";
  const cleanEmail = normalizedEmail.trim().toLowerCase();
  return crypto.createHmac("sha256", getServerSecret()).update(`email:${cleanEmail}`).digest("hex");
}

/**
 * Compute HMAC digest of an OTP bound to a specific flow ID
 */
function computeOtpHash(code, flowId) {
  return crypto.createHmac("sha256", getServerSecret()).update(`otp:${flowId}:${code}`).digest("hex");
}

/**
 * Constant-time string equality check to prevent timing attacks
 */
function constantTimeEquals(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Hash IP address for rate-limiting bucket privacy
 */
function hashIp(ip) {
  const cleanIp = (ip || "127.0.0.1").trim();
  return crypto.createHmac("sha256", getServerSecret()).update(`ip:${cleanIp}`).digest("hex").slice(0, 32);
}

module.exports = {
  getServerSecret,
  generateOtpCode,
  computePhoneHash,
  computeEmailHash,
  computeOtpHash,
  constantTimeEquals,
  hashIp
};
