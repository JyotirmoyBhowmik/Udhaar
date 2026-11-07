/**
 * Server Cryptographic Primitives & Input Normalization (functions/src/crypto/serverCrypto.js)
 * Implements HMAC blind indexing, secure random OTP generation, phone/email validation,
 * and timing-safe comparisons backed by Google Cloud Secret Manager.
 * Zero hard-coded fallback secrets.
 */

const crypto = require("crypto");
const { getSecret } = require("../config/secrets");
const { DISPOSABLE_EMAIL_DOMAINS } = require("../config");

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

const generateSixDigitOtp = generateOtpCode;

/**
 * Validate and normalize phone numbers for supported regions (+91 India, +977 Nepal)
 */
function validateAndNormalizePhone(rawPhone) {
  if (!rawPhone || typeof rawPhone !== "string") {
    const err = new Error("Valid phone number is required.");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  const stripped = rawPhone.replace(/[\s\-()]/g, "");
  if (!stripped || stripped.length < 10) {
    const err = new Error("Invalid phone number format.");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  let e164 = stripped;
  if (/^[6-9][0-9]{9}$/.test(stripped)) {
    e164 = `+91${stripped}`;
  } else if (/^91[6-9][0-9]{9}$/.test(stripped)) {
    e164 = `+${stripped}`;
  } else if (/^9779[678][0-9]{8}$/.test(stripped)) {
    e164 = `+${stripped}`;
  }

  if (!e164.startsWith("+")) {
    const err = new Error("Invalid phone number format.");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  if (e164.startsWith("+91")) {
    const subscriber = e164.slice(3);
    if (!/^[6-9][0-9]{9}$/.test(subscriber)) {
      const err = new Error("Invalid Indian mobile number.");
      err.code = "ERR_INVALID_PHONE";
      err.status = 400;
      throw err;
    }
    return {
      e164,
      country: "IN",
      countryCallingCode: "+91"
    };
  }

  if (e164.startsWith("+977")) {
    const subscriber = e164.slice(4);
    if (!/^9[678][0-9]{8}$/.test(subscriber)) {
      const err = new Error("Invalid Nepali mobile number.");
      err.code = "ERR_INVALID_PHONE";
      err.status = 400;
      throw err;
    }
    return {
      e164,
      country: "NP",
      countryCallingCode: "+977"
    };
  }

  if (/^\+[1-9][0-9]{6,14}$/.test(e164)) {
    const err = new Error("Only India (+91) and Nepal (+977) mobile numbers are currently supported.");
    err.code = "ERR_UNSUPPORTED_COUNTRY";
    err.status = 400;
    throw err;
  }

  const err = new Error("Invalid phone number format.");
  err.code = "ERR_INVALID_PHONE";
  err.status = 400;
  throw err;
}

function normalizePhoneE164(rawPhone) {
  return validateAndNormalizePhone(rawPhone).e164;
}

/**
 * Validate and normalize email address, blocking disposable domains
 */
function validateAndNormalizeEmail(rawEmail) {
  if (!rawEmail || typeof rawEmail !== "string") {
    const err = new Error("Valid email address is required.");
    err.code = "ERR_INVALID_EMAIL";
    err.status = 400;
    throw err;
  }

  const normalized = rawEmail.trim().toLowerCase();
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(normalized)) {
    const err = new Error("Invalid email address format.");
    err.code = "ERR_INVALID_EMAIL";
    err.status = 400;
    throw err;
  }

  const domain = normalized.split("@")[1];
  const blockedDomains = new Set([
    ...(Array.isArray(DISPOSABLE_EMAIL_DOMAINS) ? DISPOSABLE_EMAIL_DOMAINS : []),
    "mailinator.com",
    "tempmail.com",
    "10minutemail.com",
    "guerrillamail.com",
    "yopmail.com",
    "trashmail.com"
  ]);

  if (blockedDomains.has(domain)) {
    const err = new Error("Disposable or temporary email addresses are not permitted.");
    err.code = "ERR_DISPOSABLE_EMAIL";
    err.status = 400;
    throw err;
  }

  return normalized;
}

function normalizeEmail(rawEmail) {
  return validateAndNormalizeEmail(rawEmail);
}

/**
 * Compute blind index HMAC for phone numbers (E.164)
 */
function computePhoneHash(phoneE164, secret = getPhonePepper()) {
  if (!phoneE164) return "";
  const cleanPhone = phoneE164.trim();
  return crypto.createHmac("sha256", secret).update(`phone:${cleanPhone}`).digest("hex");
}

/**
 * Compute blind index HMAC for normalized emails
 */
function computeEmailHash(normalizedEmail, secret = getEmailPepper()) {
  if (!normalizedEmail) return "";
  const cleanEmail = normalizedEmail.trim().toLowerCase();
  return crypto.createHmac("sha256", secret).update(`email:${cleanEmail}`).digest("hex");
}

/**
 * Compute HMAC digest of an OTP bound to a specific flow ID
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
  generateSixDigitOtp,
  validateAndNormalizePhone,
  normalizePhoneE164,
  validateAndNormalizeEmail,
  normalizeEmail,
  computePhoneHash,
  computeEmailHash,
  computeOtpHash,
  constantTimeEquals,
  hashIp
};
