/**
 * Core OTP Challenge, Rate Limiting, Brute-Force Lockout & Budget Service (functions/src/otp/otpService.js)
 * Implements Stage S1 / R4:
 * - 6-digit CSPRNG OTP generation
 * - 10-minute TTL
 * - 30-second resend cooldown
 * - Max 3 sends/hour, 10 sends/day per destination
 * - Max 20 requests/hour per IP
 * - Max 5 verify attempts per challenge -> 15-minute lockout
 * - Global daily send budget with 50% / 80% / 100% kill-switch
 * - Strictly backed by async OtpRepository without Proxy facades or module-level maps.
 */

const { OTP_LIMITS } = require("../config");
const { isFeatureSuspended } = require("../config/killSwitches");
const { checkProviderCircuit, recordProviderSpend } = require("../config/circuitBreakers");
const {
  computePhoneHash,
  computeEmailHash,
  computeOtpHash,
  generateSixDigitOtp,
  hashIp
} = require("../crypto/serverCrypto");
const { getWhatsAppProvider, getEmailProvider, getSmsProvider } = require("../providers");
const { getOtpRepository, assertRepoMethod } = require("../repositories");

function getRequiredOtpRepo() {
  const repo = getOtpRepository();
  assertRepoMethod(repo, "saveChallenge", "OtpRepository");
  assertRepoMethod(repo, "getChallengeByFlowId", "OtpRepository");
  assertRepoMethod(repo, "verifyAndConsumeChallengeTransaction", "OtpRepository");
  assertRepoMethod(repo, "checkAndIncrementIpLimit", "OtpRepository");
  assertRepoMethod(repo, "checkAndIncrementDestinationLimit", "OtpRepository");
  assertRepoMethod(repo, "checkAndIncrementDailyBudget", "OtpRepository");
  return repo;
}

function resetDailyBudget(customMaxBudget = OTP_LIMITS.GLOBAL_DAILY_SEND_BUDGET) {
  const repo = getOtpRepository();
  assertRepoMethod(repo, "clear", "OtpRepository");
  const clearPromise = repo.clear();
  if (typeof repo.setMaxDailyBudgetOverride === "function" && customMaxBudget !== OTP_LIMITS.GLOBAL_DAILY_SEND_BUDGET) {
    if (clearPromise && typeof clearPromise.then === "function") {
      return clearPromise.then(() => repo.setMaxDailyBudgetOverride(customMaxBudget));
    }
    repo.setMaxDailyBudgetOverride(customMaxBudget);
  }
  return clearPromise;
}

async function getDailyBudgetStatus() {
  const repo = getOtpRepository();
  assertRepoMethod(repo, "getDailyBudgetStatus", "OtpRepository");
  return repo.getDailyBudgetStatus(OTP_LIMITS.GLOBAL_DAILY_SEND_BUDGET);
}

/**
 * Check and increment multi-layer rate limits (IP + Destination + Global Budget)
 * Atomically persisted via OtpRepository transactions
 */
async function enforceSendRateLimits(destinationHash, ipHashed) {
  const repo = getRequiredOtpRepo();

  await repo.checkAndIncrementDailyBudget(OTP_LIMITS.GLOBAL_DAILY_SEND_BUDGET);
  await repo.checkAndIncrementIpLimit(ipHashed, OTP_LIMITS.MAX_HOURLY_PER_IP, 3600000);
  await repo.checkAndIncrementDestinationLimit(destinationHash, OTP_LIMITS);
}

/**
 * Create and dispatch a 6-digit OTP challenge for a specific flowId and purpose
 */
async function sendOtpChallenge({
  flowId,
  destination,
  channel = "WHATSAPP",
  purpose = "LOGIN",
  locale = "en",
  clientIp = "127.0.0.1"
}) {
  if (await isFeatureSuspended("KILL_SWITCH_OTP_DISPATCH")) {
    const err = new Error("OTP dispatch is temporarily suspended by administrator.");
    err.code = "ERR_DISPATCH_SUSPENDED";
    err.status = 503;
    throw err;
  }

  if (channel === "WHATSAPP" && (await isFeatureSuspended("KILL_SWITCH_WHATSAPP_OTP"))) {
    const err = new Error("WhatsApp OTP dispatch is temporarily suspended by administrator.");
    err.code = "ERR_WHATSAPP_OTP_SUSPENDED";
    err.status = 503;
    throw err;
  }

  const circuitCheck = await checkProviderCircuit(channel);
  if (!circuitCheck.allowed) {
    const err = new Error(`Service temporarily unavailable due to cost circuit breaker (${circuitCheck.reason}).`);
    err.code = "ERR_CIRCUIT_BREAKER_OPEN";
    err.status = 503;
    throw err;
  }

  if (!flowId || typeof flowId !== "string") {
    const err = new Error("flowId is required for OTP challenge.");
    err.code = "ERR_INVALID_FLOW_ID";
    err.status = 400;
    throw err;
  }

  const isEmail = channel === "EMAIL" || String(destination).includes("@");
  const isSms = channel === "SMS";
  const destinationHash = isEmail ? computeEmailHash(destination) : computePhoneHash(destination);
  const ipHashed = hashIp(clientIp);
  const repo = getRequiredOtpRepo();

  const existing = await repo.getChallengeByFlowId(flowId, purpose);
  if (existing && existing.lockedUntilMs && existing.lockedUntilMs > Date.now()) {
    const remainingMins = Math.ceil((existing.lockedUntilMs - Date.now()) / 60000);
    const err = new Error(`Too many failed attempts. Please try again in ${remainingMins} minutes.`);
    err.code = "ERR_ACCOUNT_LOCKED";
    err.status = 403;
    throw err;
  }

  await enforceSendRateLimits(destinationHash, ipHashed);
  await recordProviderSpend(channel, 1);

  const code = generateSixDigitOtp();
  const codeHash = computeOtpHash(code, flowId);
  const now = Date.now();
  const expiresAtMs = now + OTP_LIMITS.CODE_TTL_MINUTES * 60 * 1000;

  let providerRes;
  if (isEmail) {
    const emailProvider = getEmailProvider();
    const { subject, textContent, htmlContent } = emailProvider.buildContent
      ? emailProvider.buildContent(code, locale)
      : {
          subject: `${code} is your Udhaar verification code`,
          textContent: `Your Udhaar verification code is ${code}`,
          htmlContent: `<p>Your Udhaar verification code is <strong>${code}</strong></p>`
        };

    providerRes = await emailProvider.sendEmail({
      toEmail: destination,
      subject,
      textContent,
      htmlContent,
      code,
      category: "OTP"
    });
  } else if (isSms && typeof getSmsProvider === "function") {
    const smsProvider = getSmsProvider();
    providerRes = await smsProvider.sendCode({
      destination,
      code,
      locale,
      purpose
    });
  } else {
    const whatsAppProvider = getWhatsAppProvider();
    providerRes = await whatsAppProvider.sendCode({
      destination,
      code,
      locale,
      purpose
    });
  }

  const challengeId = `chal_${flowId}_${purpose}`;
  const challengeRecord = {
    challengeId,
    flowId,
    destinationHash,
    channel: isEmail ? "EMAIL" : (isSms ? "SMS" : "WHATSAPP"),
    purpose,
    codeHash,
    attempts: 0,
    maxAttempts: OTP_LIMITS.MAX_VERIFY_ATTEMPTS,
    createdAtMs: now,
    expiresAtMs,
    expiresAt: new Date(expiresAtMs).toISOString(),
    lockedUntilMs: null,
    consumed: false,
    providerMessageId: providerRes.providerMessageId || providerRes.messageId,
    deliveryStatus: "SENT"
  };

  await repo.saveChallenge(challengeRecord);

  return {
    challengeId,
    flowId,
    channel: challengeRecord.channel,
    expiresAtMs,
    expiresInSeconds: OTP_LIMITS.CODE_TTL_MINUTES * 60
  };
}

/**
 * Verify a 6-digit OTP code strictly scoped to a flowId and purpose
 * Executed atomically inside a repository transaction
 */
async function verifyFlowOtp({ flowId, code, purpose = null, destination = null }) {
  if (!flowId || !code || typeof code !== "string") {
    const err = new Error("flowId and 6-digit code are required.");
    err.code = "ERR_INVALID_OTP_INPUT";
    err.status = 400;
    throw err;
  }

  const repo = getRequiredOtpRepo();
  return repo.verifyAndConsumeChallengeTransaction({
    flowId,
    code,
    purpose,
    destination
  });
}

const verifyOtpChallenge = verifyFlowOtp;

module.exports = {
  sendOtpChallenge,
  verifyFlowOtp,
  verifyOtpChallenge,
  resetDailyBudget,
  getDailyBudgetStatus
};
