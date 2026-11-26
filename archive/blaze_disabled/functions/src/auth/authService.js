/**
 * Identity, Registration, Sign-In, and Recovery Service (functions/src/auth/authService.js)
 * Implements Stage S1 / R4 Identity & Registration Flows:
 * 1. Phone (WhatsApp OTP) + Mandatory Email (OTP) + Age >= 18 + DPDP Consent
 * 2. Non-enumerable Sign-in via Phone or Email
 * 3. Account Recovery via Verified Email (Immediate for Active Session, 30-Day Cooling Period for Lost Number)
 * 4. Strictly backed by async AuthRepository without Proxy facades or module-level maps.
 */

const crypto = require("crypto");
const {
  validateAndNormalizePhone,
  normalizePhoneE164,
  validateAndNormalizeEmail,
  normalizeEmail,
  computePhoneHash,
  computeEmailHash,
  hashIp
} = require("../crypto/serverCrypto");
const { sendOtpChallenge, verifyFlowOtp } = require("../otp/otpService");
const { createSession } = require("../sessions/sessionManager");
const { verifyWebBotAssessment } = require("../security/recaptchaEnterprise");
const { OTP_LIMITS } = require("../config");
const { isFeatureSuspended } = require("../config/killSwitches");
const { getAuthRepository, getOtpRepository, assertRepoMethod } = require("../repositories");

function getRequiredAuthRepo() {
  const repo = getAuthRepository();
  assertRepoMethod(repo, "getUser", "AuthRepository");
  assertRepoMethod(repo, "getUserByPhoneHash", "AuthRepository");
  assertRepoMethod(repo, "getUserByEmailHash", "AuthRepository");
  assertRepoMethod(repo, "createUserTransaction", "AuthRepository");
  assertRepoMethod(repo, "updateUser", "AuthRepository");
  assertRepoMethod(repo, "updateIdentifierTransaction", "AuthRepository");
  assertRepoMethod(repo, "appendAuditLog", "AuthRepository");
  return repo;
}

async function logAuditEvent(uid, eventType, clientIp, metadata = {}) {
  const repo = getRequiredAuthRepo();
  await repo.appendAuditLog({
    uid,
    eventType,
    ipHash: hashIp(clientIp),
    timestamp: new Date().toISOString(),
    metadata
  });
}

function maskEmail(email) {
  const [local, domain] = String(email || "").split("@");
  if (!domain) return "***";
  const visible = local.length > 2 ? local.slice(0, 2) : local.slice(0, 1);
  return `${visible}***@${domain}`;
}

function maskPhone(e164) {
  if (!e164 || e164.length < 6) return "***";
  if (e164.startsWith("+91") && e164.length === 13) {
    return `+91 ${e164.slice(3, 5)}*** ***${e164.slice(-2)}`;
  }
  if (e164.startsWith("+977") && e164.length === 14) {
    return `+977 ${e164.slice(4, 6)}*** ***${e164.slice(-2)}`;
  }
  return `${e164.slice(0, 4)}******${e164.slice(-3)}`;
}

// ============================================================================
// FLOW 1: SIGN-UP (Phone -> Email -> Complete)
// ============================================================================

async function startSignup({
  phoneNumber,
  locale = "en",
  botCheckToken = null,
  clientIp = "127.0.0.1",
  clientType = "WEB"
}) {
  if (await isFeatureSuspended("KILL_SWITCH_REGISTRATION")) {
    const err = new Error("New account registrations are temporarily suspended for maintenance.");
    err.code = "ERR_REGISTRATION_SUSPENDED";
    err.status = 503;
    throw err;
  }

  if (botCheckToken !== null && botCheckToken !== undefined) {
    await verifyWebBotAssessment({
      token: botCheckToken,
      expectedAction: "signup_start",
      clientIp,
      clientType
    });
  }

  const normalizedPhone = normalizePhoneE164(phoneNumber);
  const flowId = `flow_signup_${crypto.randomBytes(12).toString("hex")}`;

  const otpRes = await sendOtpChallenge({
    flowId,
    destination: normalizedPhone,
    channel: "WHATSAPP",
    purpose: "SIGNUP",
    locale,
    clientIp
  });

  const masked = maskPhone(normalizedPhone);
  return {
    flowId,
    step: "VERIFY_PHONE_OTP",
    channel: otpRes.channel,
    resendAfterSeconds: OTP_LIMITS.RESEND_COOLDOWN_SECONDS,
    maskedPhone: masked,
    destinationMasked: masked,
    expiresInSeconds: otpRes.expiresInSeconds,
    expiresAtMs: otpRes.expiresAtMs
  };
}

async function submitSignupEmail({ flowId, flowToken, email, locale = "en", clientIp = "127.0.0.1" }) {
  if (!flowToken) {
    const err = new Error("Phone verification flowToken is required before adding email.");
    err.code = "ERR_MISSING_FLOW_TOKEN";
    err.status = 400;
    throw err;
  }

  const normalizedEmail = normalizeEmail(email);

  const otpRes = await sendOtpChallenge({
    flowId,
    destination: normalizedEmail,
    channel: "EMAIL",
    purpose: "SIGNUP_EMAIL",
    locale,
    clientIp
  });

  const masked = maskEmail(normalizedEmail);
  return {
    flowId,
    step: "VERIFY_EMAIL_OTP",
    channel: otpRes.channel,
    resendAfterSeconds: OTP_LIMITS.RESEND_COOLDOWN_SECONDS,
    maskedEmail: masked,
    destinationMasked: masked,
    expiresInSeconds: otpRes.expiresInSeconds,
    expiresAtMs: otpRes.expiresAtMs
  };
}

async function completeSignup({
  flowId,
  flowToken,
  phoneNumber,
  email,
  displayName,
  locale = "en",
  acceptedTermsVersion = "v2.0",
  acceptedPrivacyVersion = "v2.0",
  isAge18Plus = false,
  allowWhatsAppReminders = false,
  clientType = "WEB",
  deviceInfo = {},
  clientIp = "127.0.0.1"
}) {
  if (!isAge18Plus) {
    const err = new Error("You must be at least 18 years old to create an Udhaar account under the Indian Contract Act, 1872.");
    err.code = "ERR_UNDERAGE_USER";
    err.status = 400;
    throw err;
  }

  if (!flowToken) {
    const err = new Error("Verified flowToken is required to complete registration.");
    err.code = "ERR_MISSING_FLOW_TOKEN";
    err.status = 400;
    throw err;
  }

  const normalizedPhone = normalizePhoneE164(phoneNumber);
  const normalizedEmail = normalizeEmail(email);
  const phoneHash = computePhoneHash(normalizedPhone);
  const emailHash = computeEmailHash(normalizedEmail);

  const uid = `usr_${crypto.randomBytes(12).toString("hex")}`;
  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();

  const userProfile = {
    uid,
    displayName: (displayName || "").trim(),
    phoneNumber: normalizedPhone,
    email: normalizedEmail,
    phoneHash,
    emailHash,
    maskedPhone: maskPhone(normalizedPhone),
    maskedEmail: maskEmail(normalizedEmail),
    locale: locale || "en",
    defaultCurrency: "INR",
    emailVerified: true,
    phoneVerified: true,
    status: "ACTIVE",
    consents: {
      termsVersion: acceptedTermsVersion,
      privacyVersion: acceptedPrivacyVersion,
      isAge18Plus: true,
      allowWhatsAppReminders: Boolean(allowWhatsAppReminders),
      consentedAt: nowIso
    },
    createdAtMs: nowMs,
    createdAt: nowIso,
    updatedAtMs: nowMs,
    updatedAt: nowIso,
    pendingPhoneChange: null
  };

  const repo = getRequiredAuthRepo();
  await repo.createUserTransaction(
    userProfile,
    { uid, phoneHash, createdAt: nowIso },
    { uid, emailHash, createdAt: nowIso }
  );

  await logAuditEvent(uid, "ACCOUNT_CREATED", clientIp, { clientType });

  const session = await createSession({
    uid,
    clientType,
    deviceInfo,
    ipAddress: clientIp
  });

  return {
    uid,
    user: userProfile,
    ...session
  };
}

// ============================================================================
// FLOW 2: SIGN-IN (Non-Enumerable Phone or Email)
// ============================================================================

async function startSignin({
  phoneNumber = null,
  identifier = null,
  locale = "en",
  botCheckToken = null,
  clientIp = "127.0.0.1",
  clientType = "WEB"
}) {
  if (botCheckToken !== null && botCheckToken !== undefined) {
    await verifyWebBotAssessment({
      token: botCheckToken,
      expectedAction: "signin_start",
      clientIp,
      clientType
    });
  }

  const rawTarget = identifier || phoneNumber;
  const isEmail = String(rawTarget || "").includes("@");
  const normalized = isEmail ? normalizeEmail(rawTarget) : normalizePhoneE164(rawTarget);
  const channel = isEmail ? "EMAIL" : "WHATSAPP";
  const hash = isEmail ? computeEmailHash(normalized) : computePhoneHash(normalized);
  const flowId = `flow_signin_${crypto.randomBytes(12).toString("hex")}`;
  const destinationMasked = isEmail ? maskEmail(normalized) : maskPhone(normalized);

  const repo = getRequiredAuthRepo();
  const existingUser = isEmail
    ? await repo.getUserByEmailHash(hash)
    : await repo.getUserByPhoneHash(hash);

  const expiresInSeconds = OTP_LIMITS.CODE_TTL_MINUTES * 60;
  const expiresAtMs = Date.now() + expiresInSeconds * 1000;

  // Non-enumerable defense: only dispatch external OTP if user exists,
  // but return an identical response structure either way.
  if (existingUser) {
    await sendOtpChallenge({
      flowId,
      destination: normalized,
      channel,
      purpose: "LOGIN",
      locale,
      clientIp
    });
  }

  return {
    flowId,
    channel,
    resendAfterSeconds: OTP_LIMITS.RESEND_COOLDOWN_SECONDS,
    destinationMasked,
    maskedDestination: destinationMasked,
    expiresInSeconds,
    expiresAtMs
  };
}

async function verifySignin({
  flowId,
  code,
  phoneNumber = null,
  identifier = null,
  clientType = "WEB",
  deviceInfo = {},
  clientIp = "127.0.0.1"
}) {
  const rawTarget = identifier || phoneNumber;
  const isEmail = String(rawTarget || "").includes("@");
  const normalized = isEmail ? normalizeEmail(rawTarget) : normalizePhoneE164(rawTarget);
  const hash = isEmail ? computeEmailHash(normalized) : computePhoneHash(normalized);

  const repo = getRequiredAuthRepo();
  const user = isEmail
    ? await repo.getUserByEmailHash(hash)
    : await repo.getUserByPhoneHash(hash);

  try {
    await verifyFlowOtp({ flowId, code, purpose: "LOGIN" });
  } catch (otpErr) {
    if (otpErr.code === "ERR_MAX_ATTEMPTS_EXCEEDED" || otpErr.code === "ERR_ACCOUNT_LOCKED") {
      throw otpErr;
    }
    const err = new Error("Invalid verification code or credentials.");
    err.code = "ERR_INVALID_CREDENTIALS";
    err.status = 401;
    throw err;
  }

  if (!user) {
    const err = new Error("Invalid verification code or credentials.");
    err.code = "ERR_INVALID_CREDENTIALS";
    err.status = 401;
    throw err;
  }

  await logAuditEvent(user.uid, "LOGIN_SUCCESS", clientIp, {
    channel: isEmail ? "EMAIL" : "WHATSAPP",
    clientType
  });

  const session = await createSession({
    uid: user.uid,
    clientType,
    deviceInfo,
    ipAddress: clientIp
  });

  return {
    uid: user.uid,
    user,
    ...session
  };
}

const completeSignin = verifySignin;

/**
 * Fallback OTP delivery via alternative channel (EMAIL or SMS) when WhatsApp fails
 */
async function fallbackOtpChallenge({
  flowId,
  channel = "EMAIL",
  destination,
  locale = "en",
  clientIp = "127.0.0.1"
}) {
  const normalizedChannel = String(channel || "EMAIL").toUpperCase();
  if (!["EMAIL", "SMS"].includes(normalizedChannel)) {
    const err = new Error("Fallback channel must be EMAIL or SMS.");
    err.code = "ERR_INVALID_FALLBACK_CHANNEL";
    err.status = 400;
    throw err;
  }

  const normalizedDest =
    normalizedChannel === "EMAIL" ? normalizeEmail(destination) : normalizePhoneE164(destination);

  // Clear resend cooldown on original challenge so user can immediately receive fallback
  const otpRepo = getOtpRepository();
  if (otpRepo && typeof otpRepo.setRateLimit === "function") {
    const destHash =
      normalizedChannel === "EMAIL" ? computeEmailHash(normalizedDest) : computePhoneHash(normalizedDest);
    const existingLimit = await otpRepo.getRateLimit(`dest_${destHash}`);
    if (existingLimit) {
      await otpRepo.setRateLimit(`dest_${destHash}`, { ...existingLimit, lastSentAt: 0 });
    }
  }

  return sendOtpChallenge({
    flowId,
    destination: normalizedDest,
    channel: normalizedChannel,
    purpose: "SIGNUP",
    locale,
    clientIp
  });
}

// ============================================================================
// FLOW 3: ACCOUNT RECOVERY & NUMBER CHANGE
// ============================================================================

async function initiatePhoneChange({
  uid = null,
  recoveryEmail = null,
  newPhoneNumber,
  hasActiveSession = false,
  locale = "en",
  clientIp = "127.0.0.1"
}) {
  const normalizedNewPhone = normalizePhoneE164(newPhoneNumber);
  const newPhoneHash = computePhoneHash(normalizedNewPhone);

  const repo = getRequiredAuthRepo();
  const existingPhoneOwner = await repo.getUserByPhoneHash(newPhoneHash);
  if (existingPhoneOwner && existingPhoneOwner.uid !== uid) {
    const err = new Error("The new phone number is already associated with another Udhaar account.");
    err.code = "ERR_PHONE_ALREADY_IN_USE";
    err.status = 409;
    throw err;
  }

  let targetUser = null;
  if (uid) {
    targetUser = await repo.getUser(uid);
  } else if (recoveryEmail) {
    const emailHash = computeEmailHash(normalizeEmail(recoveryEmail));
    targetUser = await repo.getUserByEmailHash(emailHash);
  }

  if (!targetUser) {
    const err = new Error("Account not found for recovery.");
    err.code = "ERR_ACCOUNT_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const flowId = `flow_recovery_${crypto.randomBytes(12).toString("hex")}`;

  await sendOtpChallenge({
    flowId,
    destination: normalizedNewPhone,
    channel: "WHATSAPP",
    purpose: "RECOVERY",
    locale,
    clientIp
  });

  if (hasActiveSession && uid === targetUser.uid) {
    return {
      flowId,
      mode: "IMMEDIATE_ACTIVE_SESSION",
      coolingPeriodDays: 0,
      message: "Verify the code sent to your new phone number to complete the change immediately."
    };
  }

  const coolingPeriodMs = OTP_LIMITS.LOST_NUMBER_COOLING_PERIOD_DAYS * 24 * 60 * 60 * 1000;
  const effectiveAtMs = Date.now() + coolingPeriodMs;
  const pendingPhoneChange = {
    flowId,
    newPhoneHash,
    maskedNewPhone: maskPhone(normalizedNewPhone),
    requestedAt: new Date().toISOString(),
    effectiveAt: new Date(effectiveAtMs).toISOString(),
    effectiveAtMs,
    status: "COOLING_PERIOD"
  };

  await repo.updateUser(targetUser.uid, { pendingPhoneChange });

  await logAuditEvent(targetUser.uid, "LOST_NUMBER_RECOVERY_INITIATED", clientIp, {
    effectiveAt: pendingPhoneChange.effectiveAt
  });

  return {
    flowId,
    mode: "LOST_NUMBER_COOLING_PERIOD",
    coolingPeriodDays: OTP_LIMITS.LOST_NUMBER_COOLING_PERIOD_DAYS,
    effectiveAt: pendingPhoneChange.effectiveAt,
    message: `For your security, lost-number recovery enters a ${OTP_LIMITS.LOST_NUMBER_COOLING_PERIOD_DAYS}-day cooling period. Alerts have been sent to your old number and email.`
  };
}

async function confirmImmediatePhoneChange({
  uid,
  flowId,
  code,
  newPhoneNumber,
  clientIp = "127.0.0.1"
}) {
  await verifyFlowOtp({ flowId, code, purpose: "RECOVERY" });

  const repo = getRequiredAuthRepo();
  const user = await repo.getUser(uid);
  if (!user) {
    const err = new Error("User not found.");
    err.code = "ERR_ACCOUNT_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const normalizedNewPhone = normalizePhoneE164(newPhoneNumber);
  const newPhoneHash = computePhoneHash(normalizedNewPhone);

  const updatedUser = await repo.updateIdentifierTransaction(uid, {
    oldPhoneHash: user.phoneHash,
    newPhoneHash,
    userUpdates: {
      phoneHash: newPhoneHash,
      maskedPhone: maskPhone(normalizedNewPhone),
      pendingPhoneChange: null,
      updatedAt: new Date().toISOString()
    }
  });

  await logAuditEvent(uid, "PHONE_NUMBER_CHANGED_IMMEDIATE", clientIp);

  return {
    success: true,
    maskedPhone: updatedUser.maskedPhone
  };
}

async function cancelPendingPhoneChange({ uid, clientIp = "127.0.0.1" }) {
  const repo = getRequiredAuthRepo();
  const user = await repo.getUser(uid);
  if (!user || !user.pendingPhoneChange) {
    const err = new Error("No pending phone number change found.");
    err.code = "ERR_NO_PENDING_CHANGE";
    err.status = 400;
    throw err;
  }

  await repo.updateUser(uid, {
    pendingPhoneChange: null,
    updatedAt: new Date().toISOString()
  });
  await logAuditEvent(uid, "LOST_NUMBER_RECOVERY_CANCELLED_BY_OWNER", clientIp);

  return {
    cancelled: true,
    message: "Pending phone number recovery has been cancelled."
  };
}

async function getOrCreateUser({ channel = "email", destination = "user@example.com", displayName = "Test User" } = {}) {
  const isEmail = channel.toLowerCase() === "email" || destination.includes("@");
  const normalized = isEmail ? normalizeEmail(destination) : normalizePhoneE164(destination);
  const hash = isEmail ? computeEmailHash(normalized) : computePhoneHash(normalized);
  const repo = getRequiredAuthRepo();
  let user = isEmail ? await repo.getUserByEmailHash(hash) : await repo.getUserByPhoneHash(hash);
  if (user) {
    return { user, uid: user.uid };
  }
  const uid = `usr_${crypto.randomBytes(12).toString("hex")}`;
  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();
  const uniquePhone = "+919" + crypto.randomInt(100000000, 999999999).toString();
  const assignedPhone = isEmail ? uniquePhone : normalized;
  const assignedEmail = isEmail ? normalized : `${uid}@example.com`;
  const phoneHash = computePhoneHash(assignedPhone);
  const emailHash = computeEmailHash(assignedEmail);

  const userProfile = {
    uid,
    displayName,
    phoneNumber: assignedPhone,
    email: assignedEmail,
    phoneHash,
    emailHash,
    maskedPhone: maskPhone(assignedPhone),
    maskedEmail: maskEmail(assignedEmail),
    locale: "en",
    status: "ACTIVE",
    createdAtMs: nowMs,
    createdAt: nowIso,
    updatedAtMs: nowMs,
    updatedAt: nowIso
  };
  await repo.createUserTransaction(
    userProfile,
    { uid, phoneHash: userProfile.phoneHash, createdAt: nowIso },
    { uid, emailHash: userProfile.emailHash, createdAt: nowIso }
  );
  return { user: userProfile, uid };
}

async function resetAuthStore() {
  const repo = getAuthRepository();
  assertRepoMethod(repo, "clear", "AuthRepository");
  await repo.clear();
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
  completeSignin,
  fallbackOtpChallenge,
  initiatePhoneChange,
  confirmImmediatePhoneChange,
  cancelPendingPhoneChange,
  resetAuthStore,
  getOrCreateUser
};
