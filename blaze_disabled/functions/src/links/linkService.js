/**
 * Hardened Guest Capability Links & Account Upgrade Service (functions/src/links/linkService.js)
 * Implements Stage S6:
 * - Capability link creation with 32-byte raw secret in URL fragment (#token) and SHA-256 hash in Firestore.
 * - Strict 30-day TTL expiry, rate limiting (20 req/hr per IP), and WhatsApp OTP verification.
 * - 5 failed OTP attempts -> 15-minute lockout.
 * - Anti-replay protection via nonces & idempotency keys.
 * - Account Upgrade Bridge: links guest actions to full authenticated accounts with reciprocal record creation.
 * - Strictly backed by async LinkRepository, LedgerRepository, OtpRepository, and InboxRepository.
 */

const crypto = require("crypto");
const { constantTimeEquals } = require("../crypto/serverCrypto");
const { sendOtpChallenge, verifyFlowOtp } = require("../otp/otpService");
const { getRecord, createRecord, updateRecord } = require("../ledger/recordService");
const { appendLedgerEvent } = require("../ledger/eventService");
const { createInboxNotification } = require("../inbox/inboxService");
const { isFeatureSuspended } = require("../config/killSwitches");
const {
  getLinkRepository,
  getLedgerRepository,
  assertRepoMethod
} = require("../repositories");

const GUEST_LINK_TTL_DAYS = 30;
const GUEST_LINK_TTL_MS = GUEST_LINK_TTL_DAYS * 24 * 60 * 60 * 1000;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000; // 1 hour
const RATE_LIMIT_MAX_REQUESTS = 20;
const MAX_OTP_ATTEMPTS = 5;
const LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutes
const MAX_PROOF_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
const ALLOWED_PROOF_MIMES = ["image/jpeg", "image/png", "image/webp", "application/pdf"];

const REQUIRED_LINK_METHODS = [
  "saveWebLink",
  "getWebLink",
  "updateWebLink",
  "deleteWebLink",
  "listWebLinks",
  "checkAndIncrementIpRateLimit"
];

function getRequiredLinkRepo() {
  const repo = getLinkRepository();
  for (const method of REQUIRED_LINK_METHODS) {
    assertRepoMethod(repo, method, "LinkRepository");
  }
  return repo;
}

function getRequiredLedgerRepo() {
  const repo = getLedgerRepository();
  assertRepoMethod(repo, "getRecord", "LedgerRepository");
  assertRepoMethod(repo, "updateRecord", "LedgerRepository");
  assertRepoMethod(repo, "findLinkedRecord", "LedgerRepository");
  return repo;
}

function hashCapabilityToken(rawToken) {
  return crypto.createHash("sha256").update(String(rawToken || "")).digest("hex");
}

async function checkIpRateLimit(clientIp = "127.0.0.1") {
  const repo = getRequiredLinkRepo();
  return repo.checkAndIncrementIpRateLimit(clientIp, RATE_LIMIT_WINDOW_MS, RATE_LIMIT_MAX_REQUESTS);
}

/**
 * Create a new capability link for a record.
 * Returns the rawToken ONLY once (to be placed in the `#` URL fragment by the sender).
 */
async function createCapabilityLink({ uid, recordId, expiresInDays = GUEST_LINK_TTL_DAYS }) {
  if (!uid || !recordId) {
    const err = new Error("User ID and Record ID are required");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  const record = await getRecord({ uid, recordId });
  if (!record) {
    const err = new Error("Record not found");
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const rawToken = crypto.randomBytes(24).toString("base64url");
  const capabilityHash = hashCapabilityToken(rawToken);
  const now = Date.now();
  const ttlMs = Math.min(Math.max(1, Number(expiresInDays) || GUEST_LINK_TTL_DAYS), 90) * 86400000;
  const expiresAtMs = now + ttlMs;

  const linkMeta = {
    recordId,
    creatorUid: uid,
    capabilityHash,
    createdAtMs: now,
    expiresAtMs,
    isRevoked: false,
    failedOtpAttempts: 0,
    lockedUntilMs: 0,
    otpVerified: false,
    isPhoneVerified: false,
    otpVerifiedAtMs: null,
    activeFlowId: null,
    usedNonces: [],
    idempotencyResults: {},
    upgradedToUid: null
  };

  const linkRepo = getRequiredLinkRepo();
  await linkRepo.saveWebLink(recordId, linkMeta);

  return {
    recordId,
    rawToken,
    capabilityHash,
    expiresAtMs,
    guestUrl: `https://udhaar.jyotirmoyb.com/u/${recordId}#${rawToken}`
  };
}

/**
 * Validate capability token, TTL expiry, and lockout state for a guest link
 */
async function resolveAndVerifyLink(recordId, rawToken) {
  if (await isFeatureSuspended("KILL_SWITCH_GUEST_LINKS")) {
    const err = new Error("Guest verification links are temporarily suspended by system administrator.");
    err.code = "ERR_GUEST_PORTAL_SUSPENDED";
    err.status = 503;
    throw err;
  }

  if (!recordId || !rawToken || typeof rawToken !== "string" || rawToken.length < 16) {
    const err = new Error("Invalid or missing capability link key");
    err.code = "ERR_INVALID_CAPABILITY_TOKEN";
    err.status = 403;
    throw err;
  }

  const linkRepo = getRequiredLinkRepo();
  const linkMeta = await linkRepo.getWebLink(recordId);
  if (!linkMeta) {
    const err = new Error("Verification link not found or has expired");
    err.code = "ERR_LINK_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (linkMeta.isRevoked) {
    const err = new Error("This verification link has been revoked by the sender");
    err.code = "ERR_LINK_REVOKED";
    err.status = 403;
    throw err;
  }

  const now = Date.now();
  if (now > linkMeta.expiresAtMs) {
    const err = new Error("This verification link has expired (30-day limit)");
    err.code = "ERR_LINK_EXPIRED";
    err.status = 403;
    throw err;
  }

  const candidateHash = hashCapabilityToken(rawToken);
  if (!constantTimeEquals(candidateHash, linkMeta.capabilityHash)) {
    const err = new Error("Invalid capability link key");
    err.code = "ERR_INVALID_CAPABILITY_TOKEN";
    err.status = 403;
    throw err;
  }

  if (linkMeta.lockedUntilMs && now < linkMeta.lockedUntilMs) {
    const err = new Error("Too many failed verification attempts. Link is locked for 15 minutes.");
    err.code = "ERR_ACCOUNT_LOCKED";
    err.status = 429;
    err.retryAfterSeconds = Math.ceil((linkMeta.lockedUntilMs - now) / 1000);
    throw err;
  }

  return linkMeta;
}

/**
 * Retrieve masked/decrypted guest view of the record
 */
async function getGuestRecordView({ recordId, rawToken, clientIp = "127.0.0.1" }) {
  await checkIpRateLimit(clientIp);
  const linkMeta = await resolveAndVerifyLink(recordId, rawToken);
  const record = await getRecord({ uid: linkMeta.creatorUid, recordId });

  const phone = record.partyPhone || "";
  const maskedPhone = phone.length >= 4 ? `•••• ••${phone.slice(-4)}` : "••••";

  const directionForRecipient = record.counterpartyRole === "BORROWER" ? "YOU_OWE" : "OWES_YOU";

  return {
    recordId: record.recordId,
    creatorUid: linkMeta.creatorUid,
    lenderName: record.counterpartyRole === "BORROWER" ? (record.creatorName || "Lender") : record.partyName,
    borrowerName: record.counterpartyRole === "BORROWER" ? record.partyName : (record.creatorName || "Borrower"),
    partyName: record.partyName,
    maskedRecipientPhone: maskedPhone,
    principalAmountPaise: record.principalAmountPaise,
    remainingAmountPaise: record.remainingAmountPaise,
    amount: record.principalAmountPaise / 100,
    remainingAmount: record.remainingAmountPaise / 100,
    currency: "INR",
    directionForRecipient,
    counterpartyRole: record.counterpartyRole,
    status: record.status,
    verificationStatus: record.verificationStatus || "PENDING",
    startDateMs: record.startDateMs,
    dueDateMs: record.dueDateMs,
    note: record.note || "",
    items: record.items || [],
    upiId: record.upiId || "",
    contentHash: record.contentHash || hashCapabilityToken(`${record.recordId}:${record.principalAmountPaise}`).slice(0, 32),
    requirePhoneOtp: Boolean(phone && !linkMeta.otpVerified),
    otpVerified: Boolean(linkMeta.otpVerified),
    isPhoneVerified: Boolean(linkMeta.isPhoneVerified || linkMeta.otpVerified),
    expiresAtMs: linkMeta.expiresAtMs,
    pendingPaymentClaim: record.pendingPaymentClaim || null,
    pendingDateProposal: record.pendingDateProposal || null
  };
}

/**
 * Request a 6-digit WhatsApp OTP challenge for the guest recipient
 */
async function requestGuestOtp({ recordId, rawToken, clientIp = "127.0.0.1", locale = "en" }) {
  await checkIpRateLimit(clientIp);
  const linkMeta = await resolveAndVerifyLink(recordId, rawToken);
  const record = await getRecord({ uid: linkMeta.creatorUid, recordId });

  const destinationPhone = record.partyPhone;
  if (!destinationPhone) {
    const err = new Error("No recipient phone number associated with this record");
    err.code = "ERR_NO_RECIPIENT_PHONE";
    err.status = 400;
    throw err;
  }

  const flowId = `guest_otp_${recordId}_${Date.now()}`;
  const otpResult = await sendOtpChallenge({
    flowId,
    destination: destinationPhone,
    channel: "WHATSAPP",
    purpose: "GUEST_VERIFY",
    locale,
    clientIp
  });

  const linkRepo = getRequiredLinkRepo();
  await linkRepo.updateWebLink(recordId, { activeFlowId: flowId });

  return {
    success: true,
    flowId,
    channel: "WHATSAPP",
    expiresAtMs: otpResult.expiresAtMs,
    maskedPhone: destinationPhone.slice(-4)
  };
}

/**
 * Verify the 6-digit WhatsApp OTP submitted by the guest.
 * Enforces 5-attempt brute-force lockout (15 minutes).
 */
async function verifyGuestOtp({ recordId, rawToken, code, flowId, clientIp = "127.0.0.1" }) {
  await checkIpRateLimit(clientIp);
  const linkMeta = await resolveAndVerifyLink(recordId, rawToken);
  const linkRepo = getRequiredLinkRepo();

  const cleanCode = String(code || "").trim();
  if (!/^\d{6}$/.test(cleanCode)) {
    const err = new Error("Verification code must be 6 digits");
    err.code = "ERR_INVALID_OTP_FORMAT";
    err.status = 400;
    throw err;
  }

  const targetFlowId = flowId || linkMeta.activeFlowId;
  if (!targetFlowId) {
    const err = new Error("No active OTP challenge found. Please request a code first.");
    err.code = "ERR_CHALLENGE_NOT_FOUND";
    err.status = 400;
    throw err;
  }

  try {
    await verifyFlowOtp({
      flowId: targetFlowId,
      code: cleanCode,
      purpose: "GUEST_VERIFY"
    });

    const now = Date.now();
    await linkRepo.updateWebLink(recordId, {
      failedOtpAttempts: 0,
      lockedUntilMs: 0,
      otpVerified: true,
      isPhoneVerified: true,
      otpVerifiedAtMs: now
    });

    return {
      verified: true,
      isPhoneVerified: true,
      trustLevel: "Code checked",
      verifiedAtMs: now
    };
  } catch (otpErr) {
    const newFailedAttempts = (linkMeta.failedOtpAttempts || 0) + 1;
    const updates = { failedOtpAttempts: newFailedAttempts };

    if (newFailedAttempts >= MAX_OTP_ATTEMPTS || otpErr.code === "ERR_ACCOUNT_LOCKED" || otpErr.code === "ERR_MAX_ATTEMPTS_EXCEEDED") {
      updates.lockedUntilMs = Date.now() + LOCKOUT_DURATION_MS;
      await linkRepo.updateWebLink(recordId, updates);
      const lockErr = new Error("Maximum 5 OTP attempts exceeded. Link is locked for 15 minutes.");
      lockErr.code = "ERR_ACCOUNT_LOCKED";
      lockErr.status = 429;
      lockErr.retryAfterSeconds = 15 * 60;
      throw lockErr;
    }

    await linkRepo.updateWebLink(recordId, updates);
    const remaining = MAX_OTP_ATTEMPTS - newFailedAttempts;
    const invalidErr = new Error(`Incorrect verification code. ${remaining} attempt(s) remaining.`);
    invalidErr.code = "ERR_INVALID_CODE";
    invalidErr.status = 403;
    invalidErr.remainingAttempts = remaining;
    throw invalidErr;
  }
}

/**
 * Submit a guest action (CONFIRM / CONFIRM_RECORD, DISPUTE / DISPUTE_RECORD, PROPOSE_DATE, PAYMENT_CLAIM)
 * with anti-replay nonces and idempotency keys.
 */
async function submitGuestAction({
  recordId,
  rawToken,
  action,
  payload = {},
  nonce = null,
  idempotencyKey = null,
  clientIp = "127.0.0.1"
}) {
  await checkIpRateLimit(clientIp);
  const linkMeta = await resolveAndVerifyLink(recordId, rawToken);
  const linkRepo = getRequiredLinkRepo();
  const ledgerRepo = getRequiredLedgerRepo();

  const usedNonces = Array.isArray(linkMeta.usedNonces) ? [...linkMeta.usedNonces] : [];
  const idempotencyResults = { ...(linkMeta.idempotencyResults || {}) };

  const dedupeKey = idempotencyKey || nonce;
  if (dedupeKey && idempotencyResults[dedupeKey]) {
    return {
      ...idempotencyResults[dedupeKey],
      idempotentReplay: true
    };
  }

  if (nonce && !usedNonces.includes(nonce)) {
    usedNonces.push(nonce);
  }

  const record = await getRecord({ uid: linkMeta.creatorUid, recordId });
  const now = Date.now();
  let result = {};
  const recordUpdates = {};

  const normalizedAction = String(action || "").toUpperCase();

  switch (normalizedAction) {
    case "CONFIRM":
    case "CONFIRM_RECORD": {
      recordUpdates.status = "CONFIRMED";
      recordUpdates.verificationStatus = "VERIFIED";
      recordUpdates.verifiedAtMs = now;
      recordUpdates.trustLevel = linkMeta.otpVerified ? "Code checked" : "Private link";
      recordUpdates.updatedAtMs = now;

      await appendLedgerEvent({
        uid: linkMeta.creatorUid,
        recordId,
        eventType: "GUEST_CONFIRM",
        amountPaise: record.principalAmountPaise,
        note: "Confirmed by recipient via web link",
        metadata: { trustLevel: recordUpdates.trustLevel, otpVerified: linkMeta.otpVerified }
      });

      await createInboxNotification({
        uid: linkMeta.creatorUid,
        recordId,
        type: "RECORD_CONFIRMED",
        title: `Record confirmed by ${record.partyName}`,
        body: `Verified via ${recordUpdates.trustLevel}`,
        amountPaise: record.principalAmountPaise,
        partyName: record.partyName,
        requiresAction: false
      });

      result = {
        success: true,
        action: normalizedAction,
        status: "CONFIRMED",
        verificationStatus: "VERIFIED",
        trustLevel: recordUpdates.trustLevel,
        timestampMs: now
      };
      break;
    }

    case "DISPUTE":
    case "DISPUTE_RECORD": {
      const reason = String(payload.reason || "").trim();
      if (!reason) {
        const err = new Error("Dispute reason is required");
        err.code = "ERR_MISSING_REASON";
        err.status = 400;
        throw err;
      }

      recordUpdates.status = "DISPUTED";
      recordUpdates.verificationStatus = "DISPUTED";
      recordUpdates.disputeReason = reason;
      recordUpdates.disputedAtMs = now;
      recordUpdates.updatedAtMs = now;

      await appendLedgerEvent({
        uid: linkMeta.creatorUid,
        recordId,
        eventType: "GUEST_DISPUTE",
        amountPaise: record.principalAmountPaise,
        note: reason,
        metadata: { reason }
      });

      await createInboxNotification({
        uid: linkMeta.creatorUid,
        recordId,
        type: "RECORD_DISPUTED",
        title: `Issue reported by ${record.partyName}`,
        body: reason,
        amountPaise: record.principalAmountPaise,
        partyName: record.partyName,
        requiresAction: true,
        metadata: { reason }
      });

      result = {
        success: true,
        action: normalizedAction,
        status: "DISPUTED",
        verificationStatus: "DISPUTED",
        reason,
        timestampMs: now
      };
      break;
    }

    case "PROPOSE_DATE": {
      const proposedDateMs = Number(payload.proposedDateMs || payload.proposedDate);
      if (!Number.isFinite(proposedDateMs) || proposedDateMs <= 0) {
        const err = new Error("Valid proposed return date is required");
        err.code = "ERR_INVALID_DATE";
        err.status = 400;
        throw err;
      }

      const noteStr = String(payload.note || "").trim();
      const proposal = {
        proposedDateMs,
        note: noteStr,
        proposedAtMs: now,
        status: "PENDING_APPROVAL"
      };
      recordUpdates.pendingDateProposal = proposal;
      recordUpdates.updatedAtMs = now;

      await appendLedgerEvent({
        uid: linkMeta.creatorUid,
        recordId,
        eventType: "GUEST_PROPOSE_DATE",
        amountPaise: 0,
        note: proposal.note,
        metadata: proposal
      });

      await createInboxNotification({
        uid: linkMeta.creatorUid,
        recordId,
        type: "DATE_PROPOSAL",
        title: `New return date proposed by ${record.partyName}`,
        body: proposal.note || "Requested due date extension",
        amountPaise: record.remainingAmountPaise,
        partyName: record.partyName,
        requiresAction: true,
        metadata: proposal
      });

      result = {
        success: true,
        action: "PROPOSE_DATE",
        proposedDateMs,
        note: noteStr,
        proposal,
        timestampMs: now
      };
      break;
    }

    case "PAYMENT_CLAIM": {
      const amountPaise = payload.amountPaise !== undefined
        ? Number(payload.amountPaise)
        : Math.round(Number(payload.amount || 0) * 100);

      if (!Number.isInteger(amountPaise) || amountPaise <= 0) {
        const err = new Error("Payment claim amount must be a positive integer in paise");
        err.code = "ERR_INVALID_PAYMENT_CLAIM";
        err.status = 400;
        throw err;
      }

      if (amountPaise > record.remainingAmountPaise) {
        const err = new Error("Claimed payment exceeds remaining balance");
        err.code = "ERR_CLAIM_EXCEEDS_BALANCE";
        err.status = 400;
        throw err;
      }

      const claimId = `clm_${now}_${crypto.randomBytes(4).toString("hex")}`;
      const claim = {
        claimId,
        amountPaise,
        paymentMode: payload.paymentMode || payload.mode || "UPI",
        utrNumber: String(payload.utrNumber || "").trim(),
        note: String(payload.note || "").trim(),
        proofObjectPath: payload.proof?.objectPath || payload.proof?.storagePath || null,
        claimedAtMs: now,
        status: "PENDING_LENDER_APPROVAL"
      };

      recordUpdates.pendingPaymentClaim = claim;
      recordUpdates.updatedAtMs = now;

      await appendLedgerEvent({
        uid: linkMeta.creatorUid,
        recordId,
        eventType: "GUEST_PAYMENT_CLAIM",
        amountPaise,
        paymentMode: claim.paymentMode,
        note: claim.note,
        metadata: claim
      });

      await createInboxNotification({
        uid: linkMeta.creatorUid,
        recordId,
        type: "PAYMENT_CLAIM",
        title: `Payment claim of ₹${(amountPaise / 100).toFixed(2)} from ${record.partyName}`,
        body: claim.utrNumber ? `UTR: ${claim.utrNumber}` : (claim.note || `Via ${claim.paymentMode}`),
        amountPaise,
        partyName: record.partyName,
        requiresAction: true,
        metadata: claim
      });

      result = {
        success: true,
        action: "PAYMENT_CLAIM",
        amountPaise,
        status: "PENDING_LENDER_APPROVAL",
        claim,
        timestampMs: now
      };
      break;
    }

    default: {
      const err = new Error(`Unsupported guest action: ${action}`);
      err.code = "ERR_INVALID_ACTION";
      err.status = 400;
      throw err;
    }
  }

  await ledgerRepo.updateRecord(linkMeta.creatorUid, recordId, recordUpdates);

  if (dedupeKey) {
    idempotencyResults[dedupeKey] = result;
  }
  await linkRepo.updateWebLink(recordId, {
    usedNonces,
    idempotencyResults
  });

  return result;
}

/**
 * Generate a signed GCS upload URL for payment proof attachments.
 * Enforces strict 5MB limit and image/PDF MIME types.
 */
async function generateProofUploadUrl({
  recordId,
  rawToken,
  contentType = "image/jpeg",
  sizeBytes = 0,
  ext = "jpg",
  clientIp = "127.0.0.1"
}) {
  await checkIpRateLimit(clientIp);
  await resolveAndVerifyLink(recordId, rawToken);

  const mime = String(contentType || "").toLowerCase();
  if (!ALLOWED_PROOF_MIMES.includes(mime)) {
    const err = new Error("Unsupported file type. Only JPEG, PNG, WEBP, and PDF are allowed.");
    err.code = "ERR_INVALID_PROOF_TYPE";
    err.status = 400;
    throw err;
  }

  const bytes = Number(sizeBytes);
  if (!Number.isFinite(bytes) || bytes <= 0 || bytes > MAX_PROOF_SIZE_BYTES) {
    const err = new Error("Attachment size must be between 1 byte and 5 MB.");
    err.code = "ERR_FILE_TOO_LARGE";
    err.status = 400;
    throw err;
  }

  const safeExt = String(ext || "jpg").replace(/[^a-zA-Z0-9]/g, "").slice(0, 5) || "jpg";
  const fileId = `${Date.now()}_${crypto.randomBytes(6).toString("hex")}`;
  const objectPath = `guest_proofs/${recordId}/${fileId}.${safeExt}`;
  const expiresAtMs = Date.now() + 15 * 60 * 1000;

  const signature = crypto
    .createHmac("sha256", "udhaar_gcs_signer_v2")
    .update(`${objectPath}:${mime}:${bytes}:${expiresAtMs}`)
    .digest("hex");

  return {
    uploadUrl: `https://storage.googleapis.com/udhaar-proofs-bucket/${objectPath}?X-Goog-Expires=900&X-Goog-Signature=${signature}`,
    objectPath,
    storagePath: objectPath,
    contentType: mime,
    maxSizeBytes: MAX_PROOF_SIZE_BYTES,
    expiresAtMs,
    requiredHeaders: {
      "Content-Type": mime,
      "x-goog-content-length-range": `0,${MAX_PROOF_SIZE_BYTES}`
    }
  };
}

/**
 * Account Upgrade Bridge:
 * Links a verified guest record into an authenticated user's ledger without re-typing data.
 * Creates the reciprocal record in the new user's account and links both parties.
 */
async function upgradeGuestToAccount({
  recordId,
  rawToken,
  targetUid,
  clientIp = "127.0.0.1"
}) {
  await checkIpRateLimit(clientIp);
  const linkMeta = await resolveAndVerifyLink(recordId, rawToken);
  const linkRepo = getRequiredLinkRepo();
  const ledgerRepo = getRequiredLedgerRepo();

  if (!targetUid) {
    const err = new Error("Authenticated targetUid is required to upgrade guest record");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (targetUid === linkMeta.creatorUid) {
    const err = new Error("Cannot upgrade your own created record into the same account");
    err.code = "ERR_SELF_UPGRADE";
    err.status = 400;
    throw err;
  }

  if (linkMeta.upgradedToUid === targetUid) {
    const existingRaw = await ledgerRepo.findLinkedRecord(targetUid, recordId);
    if (existingRaw) {
      const decryptedExisting = await getRecord({ uid: targetUid, recordId: existingRaw.recordId });
      return {
        success: true,
        upgraded: true,
        alreadyUpgraded: true,
        targetUid,
        record: decryptedExisting,
        reciprocalRecordId: decryptedExisting.recordId,
        originalRecordId: recordId
      };
    }
  }

  const sourceRecord = await getRecord({ uid: linkMeta.creatorUid, recordId });
  const reciprocalRole = sourceRecord.counterpartyRole === "BORROWER" ? "LENDER" : "BORROWER";

  const reciprocalRecord = await createRecord({
    uid: targetUid,
    recordData: {
      partyName: sourceRecord.creatorName || "Verified Udhaar Partner",
      partyPhone: sourceRecord.creatorPhone || "+919000000000",
      counterpartyRole: reciprocalRole,
      principalAmountPaise: sourceRecord.principalAmountPaise,
      dueDateMs: sourceRecord.dueDateMs,
      startDateMs: sourceRecord.startDateMs,
      note: sourceRecord.note || `Linked from shared Udhaar record ${recordId}`,
      items: sourceRecord.items || [],
      upiId: sourceRecord.upiId || ""
    }
  });

  await ledgerRepo.updateRecord(targetUid, reciprocalRecord.recordId, {
    remoteRecordId: recordId,
    linkedRemoteRecordId: recordId,
    linkedCreatorUid: linkMeta.creatorUid,
    status: sourceRecord.status === "CONFIRMED" || sourceRecord.verificationStatus === "VERIFIED" ? "CONFIRMED" : sourceRecord.status,
    verificationStatus: "VERIFIED",
    remainingAmountPaise: sourceRecord.remainingAmountPaise
  });

  await ledgerRepo.updateRecord(linkMeta.creatorUid, recordId, {
    counterpartyUid: targetUid,
    linkedReciprocalRecordId: reciprocalRecord.recordId,
    verificationStatus: "VERIFIED"
  });

  await linkRepo.updateWebLink(recordId, { upgradedToUid: targetUid });

  await appendLedgerEvent({
    uid: linkMeta.creatorUid,
    recordId,
    eventType: "GUEST_ACCOUNT_UPGRADED",
    amountPaise: sourceRecord.principalAmountPaise,
    note: "Recipient claimed record into full Udhaar account",
    metadata: { counterpartyUid: targetUid, reciprocalRecordId: reciprocalRecord.recordId }
  });

  const finalReciprocal = await getRecord({ uid: targetUid, recordId: reciprocalRecord.recordId });

  return {
    success: true,
    upgraded: true,
    alreadyUpgraded: false,
    targetUid,
    record: finalReciprocal,
    originalRecordId: recordId,
    reciprocalRecordId: finalReciprocal.recordId,
    reciprocalRole,
    principalAmountPaise: sourceRecord.principalAmountPaise,
    remainingAmountPaise: sourceRecord.remainingAmountPaise
  };
}

async function resetLinksStore() {
  const repo = getLinkRepository();
  assertRepoMethod(repo, "clear", "LinkRepository");
  await repo.clear();
}

module.exports = {
  GUEST_LINK_TTL_DAYS,
  GUEST_LINK_TTL_MS,
  RATE_LIMIT_MAX_REQUESTS,
  MAX_OTP_ATTEMPTS,
  LOCKOUT_DURATION_MS,
  MAX_PROOF_SIZE_BYTES,
  hashCapabilityToken,
  createCapabilityLink,
  resolveAndVerifyLink,
  getGuestRecordView,
  requestGuestOtp,
  verifyGuestOtp,
  submitGuestAction,
  generateProofUploadUrl,
  upgradeGuestToAccount,
  resetLinksStore
};
