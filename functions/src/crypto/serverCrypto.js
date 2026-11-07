/**
 * Server Cryptographic Primitives (functions/src/crypto/serverCrypto.js)
 * Implements HMAC blind indexing, secure random OTP generation,
 * and timing-safe comparisons backed by Google Cloud Secret Manager.
 * Zero hard-coded fallback secrets.
 */

const crypto = require("crypto");
const { getSecret } = require("../config/secrets");

function getServerSecret() {
  return getSecret("UDHAAR_SERVER_SECRET");
}

function getPhonePepper() {
  return getSecret("UDHAAR_PHONE_PEPPER");
}

function getEmailPepper() {
  return getSecret("UDHAAR_EMAIL_PEPPER");
}

/**
 * Generate a cryptographically secure 6-digit numeric OTP
 */
function generateOtpCode() {
  return crypto.randomInt(100000, 1000000).toString();
}

/**
 * Compute blind index HMAC for phone numbers (E.164)
 * @param {string} phoneE164
 * @param {string} [secret] - Optional secret override (e.g. for key rotation testing)
 */
function computePhoneHash(phoneE164, secret = getPhonePepper()) {
  if (!phoneE164) return "";
  const cleanPhone = phoneE164.trim();
  return crypto.createHmac("sha256", secret).update(`phone:${cleanPhone}`).digest("hex");
}

/**
 * Compute blind index HMAC for normalized emails
 * @param {string} normalizedEmail
 * @param {string} [secret] - Optional secret override (e.g. for key rotation testing)
 */
function computeEmailHash(normalizedEmail, secret = getEmailPepper()) {
  if (!normalizedEmail) return "";
  const cleanEmail = normalizedEmail.trim().toLowerCase();
  return crypto.createHmac("sha256", secret).update(`email:${cleanEmail}`).digest("hex");
}

/**
 * Compute HMAC digest of an OTP bound to a specific flow ID
 * @param {string} code
 * @param {string} flowId
 * @param {string} [secret] - Optional secret override
 */
function computeOtpHash(code, flowId, secret = getServerSecret()) {
  return crypto.createHmac("sha256", secret).update(`otp:${flowId}:${code}`).digest("hex");
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
 * @param {string} ip
 * @param {string} [secret] - Optional secret override
 */
function hashIp(ip, secret = getServerSecret()) {
  const cleanIp = (ip || "127.0.0.1").trim();
  return crypto.createHmac("sha256", secret).update(`ip:${cleanIp}`).digest("hex").slice(0, 32);
}

module.exports = {
  getServerSecret,
  getPhonePepper,
  getEmailPepper,
  generateOtpCode,
  computePhoneHash,
  computeEmailHash,
  computeOtpHash,
  constantTimeEquals,
  hashIp
};
