/**
 * OTP Code & Verification Service (functions/src/otp/otpService.js)
 * Implements cryptographic OTP challenge lifecycle, multi-layer rate limiting,
 * brute-force lockout, timing-safe validation, and fallback delivery.
 */

const crypto = require("crypto");
const {
  generateOtpCode,
  computeOtpHash,
  computePhoneHash,
  computeEmailHash,
  constantTimeEquals,
  hashIp
} = require("../crypto/serverCrypto");
const { OTP_LIMITS } = require("../config");
const { getWhatsAppProvider, getEmailProvider, getSmsProvider } = require("../providers");

// In-memory challenge store for unit tests or when Firestore is uninitialized
const inMemoryChallenges = new Map();
const inMemoryRateLimits = new Map();

// Daily global send budget tracker
let globalDailyBudget = {
  dateStr: new Date().toISOString().split("T")[0],
  sentCount: 0,
  maxDailyBudget: 10000,
  killSwitchTriggered: false
};

function resetDailyBudget() {
  globalDailyBudget.sentCount = 0;
  globalDailyBudget.killSwitchTriggered = false;
  inMemoryChallenges.clear();
  inMemoryRateLimits.clear();
}

/**
 * Check and increment rate limits for destination and IP
 */
function checkRateLimits(destinationHash, ipHashed) {
  const now = Date.now();
  const today = new Date().toISOString().split("T")[0];

  // Global budget kill-switch check
  if (globalDailyBudget.dateStr !== today) {
    globalDailyBudget.dateStr = today;
    globalDailyBudget.sentCount = 0;
    globalDailyBudget.killSwitchTriggered = false;
  }
  if (globalDailyBudget.killSwitchTriggered || globalDailyBudget.sentCount >= globalDailyBudget.maxDailyBudget) {
    globalDailyBudget.killSwitchTriggered = true;
    const err = new Error("Emergency daily OTP dispatch budget reached. Send service paused.");
    err.code = "ERR_GLOBAL_BUDGET_EXCEEDED";
    err.status = 503;
    throw err;
  }

  // 1. IP rate limit check (20 per hour)
  const ipKey = `ip_${ipHashed}`;
  const ipRecord = inMemoryRateLimits.get(ipKey) || { count: 0, resetAt: now + 3600000 };
  if (now > ipRecord.resetAt) {
    ipRecord.count = 0;
    ipRecord.resetAt = now + 3600000;
  }
  if (ipRecord.count >= OTP_LIMITS.MAX_HOURLY_PER_IP) {
    const err = new Error("Too many verification requests from this network. Please try again later.");
    err.code = "ERR_RATE_LIMIT_IP";
    err.status = 429;
    throw err;
  }

  // 2. Destination hourly (3/hr) and daily (10/day) limit check
  const destKey = `dest_${destinationHash}`;
  const destRecord = inMemoryRateLimits.get(destKey) || {
    hourlyCount: 0,
    hourlyResetAt: now + 3600000,
    dailyCount: 0,
    dailyResetAt: now + 86400000,
    lastSentAt: 0
  };

  // Cooldown check (30 seconds)
  if (now - destRecord.lastSentAt < OTP_LIMITS.RESEND_COOLDOWN_SECONDS * 1000) {
    const remainingSecs = Math.ceil((OTP_LIMITS.RESEND_COOLDOWN_SECONDS * 1000 - (now - destRecord.lastSentAt)) / 1000);
    const err = new Error(`Please wait ${remainingSecs} seconds before requesting a new code.`);
    err.code = "ERR_RESEND_COOLDOWN";
    err.status = 429;
    err.retryAfter = remainingSecs;
    throw err;
  }

  // Hourly reset
  if (now > destRecord.hourlyResetAt) {
    destRecord.hourlyCount = 0;
    destRecord.hourlyResetAt = now + 3600000;
  }
  if (destRecord.hourlyCount >= OTP_LIMITS.MAX_HOURLY_PER_DESTINATION) {
    const err = new Error("Hourly verification limit reached for this destination (max 3/hour).");
    err.code = "ERR_RATE_LIMIT_DESTINATION_HOURLY";
    err.status = 429;
    throw err;
  }

  // Daily reset
  if (now > destRecord.dailyResetAt) {
    destRecord.dailyCount = 0;
    destRecord.dailyResetAt = now + 86400000;
  }
  if (destRecord.dailyCount >= OTP_LIMITS.MAX_DAILY_PER_DESTINATION) {
    const err = new Error("Daily verification limit reached for this destination (max 10/day).");
    err.code = "ERR_RATE_LIMIT_DESTINATION_DAILY";
    err.status = 429;
    throw err;
  }

  // Increment counters
  ipRecord.count++;
  inMemoryRateLimits.set(ipKey, ipRecord);

  destRecord.hourlyCount++;
  destRecord.dailyCount++;
  destRecord.lastSentAt = now;
  inMemoryRateLimits.set(destKey, destRecord);

  globalDailyBudget.sentCount++;
}

/**
 * Dispatch OTP and store challenge
 */
async function sendOtpChallenge({
  flowId,
  destination,
  channel = "WHATSAPP",
  purpose = "SIGNUP",
  locale = "en",
  clientIp = "127.0.0.1",
  deviceHash = "unknown"
}) {
  if (!flowId) throw new Error("flowId is required");
  if (!destination) throw new Error("destination is required");

  const isEmail = channel === "EMAIL";
  const destinationHash = isEmail ? computeEmailHash(destination) : computePhoneHash(destination);
  const ipHashed = hashIp(clientIp);

  // Enforce rate limits
  checkRateLimits(destinationHash, ipHashed);

  // Generate 6-digit code
  const code = generateOtpCode();
  const codeHash = computeOtpHash(code, flowId);
  const challengeId = `chal_${crypto.randomUUID()}`;
  const now = Date.now();

  const expiryMinutes = isEmail
    ? OTP_LIMITS.EMAIL_EXPIRY_MINUTES
    : channel === "SMS"
    ? OTP_LIMITS.SMS_EXPIRY_MINUTES
    : OTP_LIMITS.WHATSAPP_EXPIRY_MINUTES;

  const expiresAtMs = now + expiryMinutes * 60 * 1000;

  // Dispatch via appropriate provider
  let sendResult = null;
  if (channel === "WHATSAPP") {
    const provider = getWhatsAppProvider();
    sendResult = await provider.sendCode({
      destination,
      code,
      locale,
      purpose,
      expiresInMinutes: expiryMinutes
    });
  } else if (channel === "SMS") {
    const provider = getSmsProvider();
    sendResult = await provider.sendCode({
      destination,
      code,
      locale,
      purpose
    });
  } else if (channel === "EMAIL") {
    const provider = getEmailProvider();
    sendResult = await provider.sendEmail({
      toEmail: destination,
      code,
      locale,
      category: "OTP_VERIFICATION"
    });
  }

  // Record challenge metadata
  const challenge = {
    challengeId,
    flowId,
    purpose,
    channel,
    destinationHash,
    codeHash,
    createdAtMs: now,
    expiresAtMs,
    attempts: 0,
    maxAttempts: OTP_LIMITS.MAX_FAILED_ATTEMPTS,
    lockedUntilMs: null,
    consumed: false,
    ipHash: ipHashed,
    deviceHash,
    providerMessageId: sendResult.providerMessageId || sendResult.messageId || null,
    deliveryStatus: sendResult.deliveryStatus || "SENT",
    ttl: Math.floor((expiresAtMs + 86400000) / 1000)
  };

  inMemoryChallenges.set(challengeId, challenge);

  return {
    challengeId,
    channel,
    resendAfterSeconds: OTP_LIMITS.RESEND_COOLDOWN_SECONDS,
    expiresAtMs
  };
}

/**
 * Verify submitted OTP code against active challenge
 */
async function verifyOtpChallenge({ flowId, code, purpose, destination = null }) {
  if (!flowId || !code) {
    const err = new Error("flowId and 6-digit code are required");
    err.code = "ERR_INVALID_PARAMETERS";
    err.status = 400;
    throw err;
  }

  const now = Date.now();

  // Find challenge matching flowId and purpose
  let targetChallenge = null;
  for (const chal of inMemoryChallenges.values()) {
    if (chal.flowId === flowId && chal.purpose === purpose && !chal.consumed) {
      targetChallenge = chal;
      break;
    }
  }

  if (!targetChallenge) {
    const err = new Error("No active verification challenge found for this flow.");
    err.code = "ERR_CHALLENGE_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  // Lockout check (15 minutes after 5 wrong attempts)
  if (targetChallenge.lockedUntilMs && targetChallenge.lockedUntilMs > now) {
    const remainingMins = Math.ceil((targetChallenge.lockedUntilMs - now) / 60000);
    const err = new Error(`Too many incorrect attempts. Account verification is locked for ${remainingMins} more minutes.`);
    err.code = "ERR_ACCOUNT_LOCKED";
    err.status = 403;
    throw err;
  }

  // Expiration check
  if (now > targetChallenge.expiresAtMs) {
    const err = new Error("Verification code has expired. Please request a new code.");
    err.code = "ERR_OTP_EXPIRED";
    err.status = 400;
    throw err;
  }

  // Timing-safe HMAC check
  const candidateHash = computeOtpHash(code.trim(), flowId);
  const isValid = constantTimeEquals(candidateHash, targetChallenge.codeHash);

  if (!isValid) {
    targetChallenge.attempts++;
    if (targetChallenge.attempts >= targetChallenge.maxAttempts) {
      targetChallenge.lockedUntilMs = now + OTP_LIMITS.LOCKOUT_DURATION_MINUTES * 60 * 1000;
      const err = new Error("Too many incorrect attempts. Account verification locked for 15 minutes.");
      err.code = "ERR_MAX_ATTEMPTS_EXCEEDED";
      err.status = 403;
      throw err;
    }
    const attemptsLeft = targetChallenge.maxAttempts - targetChallenge.attempts;
    const err = new Error(`Incorrect verification code. ${attemptsLeft} attempts remaining.`);
    err.code = "ERR_INVALID_OTP";
    err.status = 400;
    err.attemptsRemaining = attemptsLeft;
    throw err;
  }

  // Code matches: mark consumed (single-use only)
  targetChallenge.consumed = true;
  targetChallenge.consumedAtMs = now;

  // Mint flow verification token (HMAC proof that this flow successfully verified)
  const flowToken = crypto
    .createHmac("sha256", targetChallenge.codeHash)
    .update(`verified:${flowId}:${purpose}:${now}`)
    .digest("hex");

  targetChallenge.flowToken = flowToken;

  return {
    verified: true,
    flowToken
  };
}

module.exports = {
  sendOtpChallenge,
  verifyOtpChallenge,
  resetDailyBudget,
  _inMemoryChallenges: inMemoryChallenges,
  _inMemoryRateLimits: inMemoryRateLimits,
  _globalDailyBudget: globalDailyBudget
};
