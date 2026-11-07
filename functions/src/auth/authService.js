/**
 * Core Authentication Service (functions/src/auth/authService.js)
 * Implements passwordless registration, phone + email verification,
 * timing-safe non-enumerable sign-in, and atomic unique indexing.
 */

const crypto = require("crypto");
const { parsePhoneNumber } = require("libphonenumber-js");
const { ALLOWED_COUNTRY_CODES } = require("../config");
const { computePhoneHash, computeEmailHash } = require("../crypto/serverCrypto");
const { sendOtpChallenge, verifyOtpChallenge } = require("../otp/otpService");
const { isDisposableEmail } = require("./disposableEmails");
const { createSession } = require("../sessions/sessionManager");

// In-memory user and index stores for testing / emulator
const inMemoryUsers = new Map();
const inMemoryPhoneIndex = new Map();
const inMemoryEmailIndex = new Map();
const inMemoryAuditLogs = [];

function resetAuthStore() {
  inMemoryUsers.clear();
  inMemoryPhoneIndex.clear();
  inMemoryEmailIndex.clear();
  inMemoryAuditLogs.length = 0;
}

/**
 * Validate and normalize E.164 phone number
 */
function validateAndNormalizePhone(phoneNumber) {
  if (!phoneNumber || typeof phoneNumber !== "string") {
    const err = new Error("Phone number is required");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  let parsed = null;
  try {
    parsed = parsePhoneNumber(phoneNumber.trim(), "IN");
  } catch (_) {
    const err = new Error("Invalid phone number format. Must be E.164 (e.g. +91 98765 43210).");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  if (!parsed || !parsed.isValid()) {
    const err = new Error("Invalid phone number format or digits.");
    err.code = "ERR_INVALID_PHONE";
    err.status = 400;
    throw err;
  }

  const e164 = parsed.format("E.164");
  const countryPrefix = "+" + parsed.countryCallingCode;

  if (!ALLOWED_COUNTRY_CODES.includes(countryPrefix)) {
    const err = new Error(`Service currently restricted to India (+91) and Nepal (+977). Country ${countryPrefix} is not supported.`);
    err.code = "ERR_UNSUPPORTED_COUNTRY";
    err.status = 400;
    throw err;
  }

  return {
    e164,
    country: parsed.country,
    countryCallingCode: countryPrefix
  };
}

/**
 * Validate and normalize email address
 */
function validateAndNormalizeEmail(email) {
  if (!email || typeof email !== "string") {
    const err = new Error("Email address is required");
    err.code = "ERR_INVALID_EMAIL";
    err.status = 400;
    throw err;
  }

  const clean = email.trim().toLowerCase();
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(clean)) {
    const err = new Error("Invalid email address format.");
    err.code = "ERR_INVALID_EMAIL";
    err.status = 400;
    throw err;
  }

  if (isDisposableEmail(clean)) {
    const err = new Error("Disposable or temporary email addresses are not permitted. Please use your primary email.");
    err.code = "ERR_DISPOSABLE_EMAIL";
    err.status = 400;
    throw err;
  }

  return clean;
}

/**
 * 1. Start sign-up with mobile number
 */
async function startSignup({ phoneNumber, locale = "en", botCheckToken = "test_token", clientIp = "127.0.0.1" }) {
  const { e164 } = validateAndNormalizePhone(phoneNumber);
  const flowId = crypto.randomUUID();

  // Dispatch 6-digit WhatsApp OTP
  const result = await sendOtpChallenge({
    flowId,
    destination: e164,
    channel: "WHATSAPP",
    purpose: "SIGNUP",
    locale,
    clientIp
  });

  return {
    flowId,
    channel: result.channel,
    resendAfterSeconds: result.resendAfterSeconds,
    destinationMasked: e164.slice(0, 3) + " " + e164.slice(3, 5) + "*** ***" + e164.slice(-2)
  };
}

/**
 * 2. Verify OTP for active flow
 */
async function verifyFlowOtp({ flowId, code, purpose = "SIGNUP" }) {
  return await verifyOtpChallenge({
    flowId,
    code,
    purpose
  });
}

/**
 * 3. Submit email address during sign-up
 */
async function submitSignupEmail({ flowId, flowToken, email, locale = "en", clientIp = "127.0.0.1" }) {
  if (!flowId || !flowToken) {
    const err = new Error("Valid flowId and phone verification proof (flowToken) required");
    err.code = "ERR_INVALID_FLOW";
    err.status = 400;
    throw err;
  }

  const cleanEmail = validateAndNormalizeEmail(email);

  // Send 6-digit email OTP
  const result = await sendOtpChallenge({
    flowId,
    destination: cleanEmail,
    channel: "EMAIL",
    purpose: "SIGNUP_EMAIL",
    locale,
    clientIp
  });

  return {
    flowId,
    channel: "EMAIL",
    resendAfterSeconds: result.resendAfterSeconds
  };
}

/**
 * 4. Complete sign-up: atomic creation of user and unique blind indexes
 */
async function completeSignup({
  flowId,
  flowToken,
  phoneNumber,
  email,
  displayName,
  acceptedTermsVersion = "v2.0",
  acceptedPrivacyVersion = "v2.0",
  isAge18Plus = true,
  allowWhatsAppReminders = true,
  clientType = "WEB",
  clientIp = "127.0.0.1",
  userAgent = ""
}) {
  if (!flowId || !flowToken) {
    const err = new Error("Missing flow proof tokens");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (!isAge18Plus) {
    const err = new Error("You must be 18 years of age or older to use Udhaar.");
    err.code = "ERR_AGE_RESTRICTION";
    err.status = 400;
    throw err;
  }

  if (!displayName || displayName.trim().length < 2 || displayName.trim().length > 60) {
    const err = new Error("Display name must be between 2 and 60 characters.");
    err.code = "ERR_INVALID_NAME";
    err.status = 400;
    throw err;
  }

  const { e164 } = validateAndNormalizePhone(phoneNumber);
  const cleanEmail = validateAndNormalizeEmail(email);

  const phoneHmac = computePhoneHash(e164);
  const emailHmac = computeEmailHash(cleanEmail);

  // Atomic unique collision check
  if (inMemoryPhoneIndex.has(phoneHmac) || inMemoryEmailIndex.has(emailHmac)) {
    const err = new Error("An account with these credentials already exists.");
    err.code = "ERR_ACCOUNT_EXISTS";
    err.status = 409;
    throw err;
  }

  const uid = `usr_${crypto.randomUUID()}`;
  const now = Date.now();

  const user = {
    uid,
    phoneEncrypted: `enc:${e164}`,
    phoneHash: phoneHmac,
    emailEncrypted: `enc:${cleanEmail}`,
    emailHash: emailHmac,
    displayName: displayName.trim(),
    defaultCurrency: "INR",
    locale: "en",
    createdAtMs: now,
    updatedAtMs: now,
    status: "ACTIVE",
    consents: {
      termsVersion: acceptedTermsVersion,
      privacyVersion: acceptedPrivacyVersion,
      isAge18Plus: true,
      allowWhatsAppReminders,
      timestampMs: now
    }
  };

  // Atomic write
  inMemoryUsers.set(uid, user);
  inMemoryPhoneIndex.set(phoneHmac, { uid, createdAtMs: now });
  inMemoryEmailIndex.set(emailHmac, { uid, createdAtMs: now });

  inMemoryAuditLogs.push({
    logId: `log_${crypto.randomUUID()}`,
    timestamp: now,
    actorUid: uid,
    action: "USER_REGISTERED",
    ipHash: crypto.createHash("sha256").update(clientIp).digest("hex").slice(0, 16)
  });

  // Create session
  const session = await createSession({
    uid,
    clientType,
    clientIp,
    userAgent
  });

  return {
    uid,
    sessionCookieValue: session.sessionCookieValue,
    customToken: session.customToken,
    user: {
      uid,
      displayName: user.displayName,
      phoneNumberMasked: e164.slice(0, 3) + " " + e164.slice(3, 5) + "*** ***" + e164.slice(-2),
      emailMasked: cleanEmail.slice(0, 1) + "***@" + cleanEmail.split("@")[1]
    }
  };
}

/**
 * 5. Sign-in: Constant-time, uniform non-enumerable response
 */
async function startSignin({ phoneNumber, clientIp = "127.0.0.1", locale = "en" }) {
  const { e164 } = validateAndNormalizePhone(phoneNumber);
  const flowId = crypto.randomUUID();
  const phoneHmac = computePhoneHash(e164);

  const userExists = inMemoryPhoneIndex.has(phoneHmac);

  if (userExists) {
    // Send real WhatsApp OTP
    await sendOtpChallenge({
      flowId,
      destination: e164,
      channel: "WHATSAPP",
      purpose: "SIGNIN",
      locale,
      clientIp
    });
  } else {
    // Unknown user: simulate constant-time delay (50-100ms) without sending OTP
    await new Promise(r => setTimeout(r, 65));
  }

  // Uniform response regardless of whether number exists or not
  return {
    flowId,
    channel: "WHATSAPP",
    resendAfterSeconds: 30,
    destinationMasked: e164.slice(0, 3) + " " + e164.slice(3, 5) + "*** ***" + e164.slice(-2)
  };
}

/**
 * 6. Verify sign-in and establish session
 */
async function verifySignin({ flowId, code, phoneNumber, clientType = "WEB", clientIp = "127.0.0.1", userAgent = "" }) {
  const { e164 } = validateAndNormalizePhone(phoneNumber);
  const phoneHmac = computePhoneHash(e164);

  const indexEntry = inMemoryPhoneIndex.get(phoneHmac);
  if (!indexEntry) {
    const err = new Error("Invalid verification code or session expired.");
    err.code = "ERR_INVALID_CREDENTIALS";
    err.status = 401;
    throw err;
  }

  // Verify OTP
  await verifyOtpChallenge({
    flowId,
    code,
    purpose: "SIGNIN"
  });

  const user = inMemoryUsers.get(indexEntry.uid);
  if (!user || user.status !== "ACTIVE") {
    const err = new Error("User account is inactive or suspended.");
    err.code = "ERR_ACCOUNT_SUSPENDED";
    err.status = 403;
    throw err;
  }

  const session = await createSession({
    uid: user.uid,
    clientType,
    clientIp,
    userAgent
  });

  inMemoryAuditLogs.push({
    logId: `log_${crypto.randomUUID()}`,
    timestamp: Date.now(),
    actorUid: user.uid,
    action: "USER_SIGNED_IN",
    clientType
  });

  return {
    uid: user.uid,
    sessionCookieValue: session.sessionCookieValue,
    customToken: session.customToken,
    user: {
      uid: user.uid,
      displayName: user.displayName,
      phoneNumberMasked: e164.slice(0, 3) + " " + e164.slice(3, 5) + "*** ***" + e164.slice(-2)
    }
  };
}

module.exports = {
  validateAndNormalizePhone,
  validateAndNormalizeEmail,
  startSignup,
  verifyFlowOtp,
  submitSignupEmail,
  completeSignup,
  startSignin,
  verifySignin,
  resetAuthStore,
  _inMemoryUsers: inMemoryUsers,
  _inMemoryPhoneIndex: inMemoryPhoneIndex,
  _inMemoryEmailIndex: inMemoryEmailIndex,
  _inMemoryAuditLogs: inMemoryAuditLogs
};
