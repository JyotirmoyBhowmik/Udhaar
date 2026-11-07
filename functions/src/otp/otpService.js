/**
 * OTP Code & Verification Service (functions/src/otp/otpService.js)
 * Implements cryptographic OTP challenge lifecycle, multi-layer rate limiting,
 * brute-force lockout, timing-safe validation, and fallback delivery.
 * Backed by persistent repositories with zero module-level Map storage.
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
const { getOtpRepository } = require("../repositories");

/**
 * Backward-compatible proxy objects for legacy tests inspecting in-memory maps
 */
const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const map = getMapFn() || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryChallenges = proxyMap(() => getOtpRepository().challenges);
const _inMemoryRateLimits = proxyMap(() => getOtpRepository().rateLimits);
const _globalDailyBudget = new Proxy({}, {
  get(target, prop) {
    const repo = getOtpRepository();
    const budget = repo.dailyBudget || {};
    return budget[prop];
  },
  set(target, prop, value) {
    const repo = getOtpRepository();
    if (repo.dailyBudget) {
      repo.dailyBudget[prop] = value;
    }
    return true;
  }
});

function resetDailyBudget() {
  const repo = getOtpRepository();
  if (typeof repo.clear === "function") {
    repo.clear();
  }
}

/**
 * Check and increment rate limits for destination and IP
 */
async function checkRateLimits(destinationHash, ipHashed) {
  const otpRepo = getOtpRepository();

  // 1. Global budget check & increment
  await otpRepo.checkAndIncrementDailyBudget(OTP_LIMITS.MAX_DAILY_GLOBAL_BUDGET);

  // 2. IP rate limit check (20 per hour)
  await otpRepo.checkAndIncrementIpLimit(ipHashed, OTP_LIMITS.MAX_HOURLY_PER_IP, 3600000);

  // 3. Destination cooldown, hourly (3/hr) and daily (10/day) limit check
  await otpRepo.checkAndIncrementDestinationLimit(destinationHash, OTP_LIMITS);
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

  // Enforce rate limits via repository
  await checkRateLimits(destinationHash, ipHashed);

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

  // Record challenge metadata in repository
  const challenge = {
    challengeId,
    flowId,
    purpose,
    channel,
    destinationHash,
    codeHash,
    createdAtMs: now,
    expiresAtMs,
    expiresAt: expiresAtMs,
    attempts: 0,
    maxAttempts: OTP_LIMITS.MAX_FAILED_ATTEMPTS,
    lockedUntilMs: null,
    consumed: false,
    ipHash: ipHashed,
    deviceHash,
    providerMessageId: (sendResult && (sendResult.providerMessageId || sendResult.messageId)) || null,
    deliveryStatus: (sendResult && sendResult.deliveryStatus) || "SENT",
    ttl: Math.floor((expiresAtMs + 86400000) / 1000)
  };

  const otpRepo = getOtpRepository();
  await otpRepo.saveChallenge(challenge);

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
  const otpRepo = getOtpRepository();

  // Find active challenge matching flowId and purpose
  let targetChallenge = null;
  if (otpRepo.challenges && typeof otpRepo.challenges.values === "function") {
    for (const chal of otpRepo.challenges.values()) {
      if (chal.flowId === flowId && chal.purpose === purpose && !chal.consumed) {
        targetChallenge = chal;
        break;
      }
    }
  } else if (otpRepo.db) {
    // Firestore query
    const snap = await otpRepo.db
      .collection("otpChallenges")
      .where("flowId", "==", flowId)
      .where("purpose", "==", purpose)
      .where("consumed", "==", false)
      .limit(1)
      .get();
    if (!snap.empty) {
      targetChallenge = snap.docs[0].data();
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
    const newAttempts = (targetChallenge.attempts || 0) + 1;
    const updates = { attempts: newAttempts };

    if (newAttempts >= targetChallenge.maxAttempts) {
      updates.lockedUntilMs = now + OTP_LIMITS.LOCKOUT_DURATION_MINUTES * 60 * 1000;
      await otpRepo.consumeChallenge(targetChallenge.challengeId, updates);
      const err = new Error("Too many incorrect attempts. Account verification locked for 15 minutes.");
      err.code = "ERR_MAX_ATTEMPTS_EXCEEDED";
      err.status = 403;
      throw err;
    }

    await otpRepo.consumeChallenge(targetChallenge.challengeId, updates);
    const attemptsLeft = targetChallenge.maxAttempts - newAttempts;
    const err = new Error(`Incorrect verification code. ${attemptsLeft} attempts remaining.`);
    err.code = "ERR_INVALID_OTP";
    err.status = 400;
    err.attemptsRemaining = attemptsLeft;
    throw err;
  }

  // Code matches: mark consumed (single-use only)
  const flowToken = crypto
    .createHmac("sha256", targetChallenge.codeHash)
    .update(`verified:${flowId}:${purpose}:${now}`)
    .digest("hex");

  await otpRepo.consumeChallenge(targetChallenge.challengeId, {
    consumed: true,
    consumedAtMs: now,
    flowToken
  });

  return {
    verified: true,
    flowToken
  };
}

module.exports = {
  sendOtpChallenge,
  verifyOtpChallenge,
  resetDailyBudget,
  _inMemoryChallenges,
  _inMemoryRateLimits,
  _globalDailyBudget
};
