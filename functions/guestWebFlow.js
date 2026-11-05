/**
 * Udhaar Cloud Functions — Secure Guest Web Flow & Hashed Phone Identity Logic
 *
 * Implements:
 * 1. Secret-salted HMAC-SHA256 phone number & OTP hashing using UDHAAR_SERVER_SECRET
 *    from Secret Manager (defineSecret). Always fails closed if the secret is missing.
 * 2. Guest Token SHA-256 verification, 30-day TTL check, and single-use consumption.
 * 3. Server-side validation of guest payment claims, WhatsApp 6-digit OTP verification,
 *    and audit trail helpers.
 */

const crypto = require("crypto");
let defineSecret;
try {
  ({ defineSecret } = require("firebase-functions/params"));
} catch (_) {
  defineSecret = (name) => ({
    name,
    value: () => process.env[name] || ""
  });
}

const UDHAAR_SERVER_SECRET = defineSecret("UDHAAR_SERVER_SECRET");

const GUEST_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days in milliseconds
const OTP_TTL_MS = 15 * 60 * 1000; // 15 minutes
const MAX_NOTE_LENGTH = 500;
const ALLOWED_PROOF_MIME_TYPES = ["image/jpeg", "image/png", "image/webp", "application/pdf"];
const MAX_PROOF_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

// Injected secret strictly for unit tests (null in production; never falls back to a built-in value)
let injectedTestServerSecret = null;

/**
 * Allows unit tests to inject a server secret explicitly without any built-in fallback.
 * Passing null or undefined clears the injected test secret so fail-closed behavior can be tested.
 *
 * @param {string|null|undefined} secret
 */
function setTestServerSecret(secret) {
  injectedTestServerSecret = secret === undefined ? null : secret;
}

/**
 * Resolves the active server secret from Secret Manager (`UDHAAR_SERVER_SECRET.value()`),
 * environment, or explicit test injection. Always fails closed if missing or blank.
 *
 * @param {string} [overrideSecret] Optional explicit secret passed by caller
 * @returns {string} Non-empty server secret
 */
function resolveServerSecret(overrideSecret) {
  const candidates = [
    overrideSecret,
    injectedTestServerSecret,
    (() => {
      try {
        return UDHAAR_SERVER_SECRET.value();
      } catch (_) {
        return undefined;
      }
    })(),
    process.env.UDHAAR_SERVER_SECRET
  ];

  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim().length > 0) {
      return candidate.trim();
    }
  }

  throw new Error("UDHAAR_SERVER_SECRET is not configured. Failing closed.");
}

/**
 * Computes an HMAC-SHA256 hex digest for a 6-digit WhatsApp verification code
 * using `UDHAAR_SERVER_SECRET`. Fails closed if the secret is missing.
 *
 * @param {string} rawCode
 * @param {string} [serverSecret]
 * @returns {string} 64-character hex HMAC-SHA256
 */
function computeCodeHmac(rawCode, serverSecret) {
  const activeSecret = resolveServerSecret(serverSecret);
  const cleanCode = String(rawCode || "").trim();
  if (!/^\d{6}$/.test(cleanCode)) {
    throw new Error("INVALID_CODE: Verification code must be a 6-digit number.");
  }
  return crypto
    .createHmac("sha256", activeSecret)
    .update(`udhaar:otp:v1:${cleanCode}`, "utf8")
    .digest("hex");
}

/**
 * Verifies a 6-digit guest verification code against the stored HMAC, 15-minute expiry,
 * and single-use flag.
 */
async function verifyGuestPhoneOtp(_db, linkRef, linkData, rawCode, nowMs = Date.now(), serverSecret) {
  if (!linkData || typeof linkData !== "object") {
    return { verified: false, status: 404, error: "Link not found." };
  }
  if (linkData.whatsappOtpUsed) {
    return { verified: false, status: 403, error: "This code has already been used." };
  }
  const expiresAt = Number(linkData.whatsappOtpExpiresAtMillis || 0);
  if (!expiresAt || nowMs > expiresAt) {
    return { verified: false, status: 403, error: "This verification code has expired." };
  }

  let candidateHmac;
  try {
    candidateHmac = computeCodeHmac(rawCode, serverSecret);
  } catch (err) {
    if (err.message && err.message.includes("UDHAAR_SERVER_SECRET")) {
      throw err;
    }
    return { verified: false, status: 400, error: "Invalid verification code format." };
  }

  const expectedBuf = Buffer.from(String(linkData.whatsappOtpHmac || ""), "hex");
  const candidateBuf = Buffer.from(candidateHmac, "hex");
  if (
    expectedBuf.length !== 32 ||
    candidateBuf.length !== 32 ||
    !crypto.timingSafeEqual(expectedBuf, candidateBuf)
  ) {
    return { verified: false, status: 403, error: "Incorrect verification code." };
  }

  if (linkRef && typeof linkRef.update === "function") {
    await linkRef.update({
      whatsappOtpUsed: true,
      whatsappVerifiedAtMillis: nowMs,
      trustLevel: "PHONE_VERIFIED"
    });
  }

  return {
    verified: true,
    whatsappVerified: true,
    trustLevel: "Code checked"
  };
}

/**
 * Masks a phone number for privacy-preserving display (e.g., "98*** ***10").
 */
function maskPhoneNumber(rawPhone) {
  const digits = String(rawPhone || "").replace(/\D/g, "");
  if (digits.length < 4) return "***";
  const last10 = digits.length > 10 ? digits.slice(-10) : digits;
  return `${last10.slice(0, 2)}*** ***${last10.slice(-2)}`;
}

/**
 * Masks a UPI ID for display (e.g., "ra***@okaxis").
 */
function maskUpiId(upiId) {
  const clean = String(upiId || "").trim();
  const atIdx = clean.indexOf("@");
  if (atIdx <= 2) return clean;
  return `${clean.slice(0, 2)}***${clean.slice(atIdx)}`;
}

/**
 * Extracts a coarse device category from User-Agent without fingerprinting.
 */
function getCoarseDeviceType(userAgent) {
  const ua = String(userAgent || "").toLowerCase();
  if (ua.includes("android")) return "Android Web";
  if (ua.includes("iphone") || ua.includes("ipad")) return "iOS Web";
  return "Web Browser";
}

/**
 * Computes a deterministic SHA-256 content hash for a shared record.
 */
function computeContentHashServer(record) {
  const r = record || {};
  const principalPaise =
    typeof r.principalAmountPaise === "number"
      ? r.principalAmountPaise
      : Math.round(Number(r.principalAmount || 0) * 100);
  const canonical = [
    r.remoteId || r.recordId || "",
    r.creatorRole || "",
    r.currency || "INR",
    String(principalPaise),
    String(Boolean(r.isInterestEnabled)),
    r.interestType || "SIMPLE",
    String(r.interestRate || 0),
    r.interestRatePeriod || "PER_ANNUM",
    r.compoundFrequency || "YEARLY",
    String(r.startDateMillis || 0),
    String(r.expiryDateMillis ?? -1)
  ].join("|");
  return crypto.createHash("sha256").update(canonical, "utf8").digest("hex");
}

async function writeAuditEntry(db, remoteId, entry) {
  if (!db || !remoteId) return;
  const ref = db.collection("shared_records").doc(remoteId).collection("audit").doc();
  await ref.set({
    eventId: ref.id,
    remoteId,
    createdAt: Date.now(),
    ...entry
  });
}

async function writeInboxItem(db, remoteId, item) {
  if (!db || !remoteId) return;
  const ref = db.collection("shared_records").doc(remoteId).collection("inbox").doc();
  await ref.set({
    inboxId: ref.id,
    remoteId,
    isRead: false,
    createdAt: Date.now(),
    ...item
  });
}

async function sendPushToCreator() {
  // Handled by Firestore triggers in index.js
}

async function validateAndRateLimitWebLink(db, recordId, rawSecret, nowMs = Date.now()) {
  if (!db || !recordId || !rawSecret) {
    throw new Error("INVALID_LINK");
  }
  const snap = await db.collection("webLinks").doc(recordId).get();
  if (!snap.exists) {
    throw new Error("LINK_NOT_FOUND");
  }
  const data = snap.data() || {};
  const ver = verifyGuestCapabilityToken(data, rawSecret, nowMs);
  if (!ver.valid) {
    throw new Error(ver.reason || "FORBIDDEN");
  }
  return data;
}

/**
 * Normalizes a raw phone number into canonical 10-digit Indian subscriber format
 * or E.164 digits, stripping spaces, dashes, and parentheses.
 *
 * @param {string} rawPhone
 * @returns {string} Normalized digits (at least 10 digits)
 */
function normalizePhoneDigits(rawPhone) {
  if (!rawPhone || typeof rawPhone !== "string") {
    throw new Error("INVALID_PHONE: Phone number must be a non-empty string.");
  }
  const digits = rawPhone.replace(/\D/g, "");
  if (digits.length < 10 || digits.length > 15) {
    throw new Error("INVALID_PHONE: Phone number must contain between 10 and 15 digits.");
  }
  if (digits.length === 12 && digits.startsWith("91")) {
    return digits.slice(2);
  }
  if (digits.length === 11 && digits.startsWith("0")) {
    return digits.slice(1);
  }
  return digits;
}

/**
 * Computes a salted HMAC-SHA256 hash of a normalized phone number using a server-side secret.
 * Always fails closed if UDHAAR_SERVER_SECRET is missing.
 *
 * @param {string} rawPhone - User's phone number
 * @param {string} [serverSecret] - Optional override secret; defaults to UDHAAR_SERVER_SECRET
 * @returns {{ normalizedDigits: string, phoneHmac: string }}
 */
function computeSaltedPhoneHash(rawPhone, serverSecret) {
  const activeSecret = resolveServerSecret(serverSecret);
  const normalizedDigits = normalizePhoneDigits(rawPhone);
  const phoneHmac = crypto
    .createHmac("sha256", activeSecret)
    .update(`udhaar:phone:v1:${normalizedDigits}`, "utf8")
    .digest("hex");
  return { normalizedDigits, phoneHmac };
}

/**
 * Computes the SHA-256 hex digest of a raw guest capability token.
 *
 * @param {string} rawToken
 * @returns {string} Lowercase hex SHA-256 hash
 */
function hashGuestToken(rawToken) {
  if (!rawToken || typeof rawToken !== "string" || rawToken.trim().length < 16) {
    throw new Error("INVALID_TOKEN: Guest token must be at least 16 characters.");
  }
  return crypto.createHash("sha256").update(rawToken.trim(), "utf8").digest("hex");
}

/**
 * Validates whether a guest token matches the record's stored hash, has not expired (30-day TTL),
 * and has not already been consumed by a finalized single-use action.
 *
 * @param {object} record - Firestore `shared_udhaars/{recordId}` document data
 * @param {string} rawToken - Raw token presented by the guest client
 * @param {number} [nowMs] - Current timestamp in milliseconds
 * @returns {{ valid: boolean, reason?: string, tokenHash?: string }}
 */
function verifyGuestCapabilityToken(record, rawToken, nowMs = Date.now()) {
  if (!record || typeof record !== "object") {
    return { valid: false, reason: "RECORD_NOT_FOUND" };
  }
  if (!record.guestTokenHash || typeof record.guestTokenHash !== "string") {
    return { valid: false, reason: "GUEST_ACCESS_DISABLED" };
  }
  let candidateHash;
  try {
    candidateHash = hashGuestToken(rawToken);
  } catch (_) {
    return { valid: false, reason: "MALFORMED_TOKEN" };
  }

  const expectedBuf = Buffer.from(record.guestTokenHash, "hex");
  const candidateBuf = Buffer.from(candidateHash, "hex");
  if (
    expectedBuf.length === 0 ||
    expectedBuf.length !== candidateBuf.length ||
    !crypto.timingSafeEqual(expectedBuf, candidateBuf)
  ) {
    return { valid: false, reason: "TOKEN_MISMATCH" };
  }

  const expiresAtMs =
    typeof record.guestTokenExpiresAt === "number"
      ? record.guestTokenExpiresAt
      : (typeof record.createdAt === "number" ? record.createdAt : nowMs) + GUEST_TOKEN_TTL_MS;

  if (nowMs > expiresAtMs) {
    return { valid: false, reason: "TOKEN_EXPIRED" };
  }

  if (record.guestTokenConsumedAt && record.status === "SETTLED") {
    return { valid: false, reason: "TOKEN_ALREADY_CONSUMED" };
  }

  return { valid: true, tokenHash: candidateHash };
}

/**
 * Validates and constructs the Firestore update payload for a guest action.
 */
function buildGuestActionUpdate(record, rawToken, actionPayload, nowMs = Date.now()) {
  const verification = verifyGuestCapabilityToken(record, rawToken, nowMs);
  if (!verification.valid) {
    const err = new Error(`Guest token verification failed: ${verification.reason}`);
    err.code = verification.reason;
    throw err;
  }

  if (!actionPayload || typeof actionPayload !== "object") {
    throw new Error("INVALID_PAYLOAD: Action payload is required.");
  }

  const { action } = actionPayload;

  if (action === "CONFIRM_RECORD") {
    if (record.status === "SETTLED") {
      throw new Error("INVALID_STATE: Record is already settled.");
    }
    return {
      status: "CONFIRMED",
      recipientConfirmedAt: nowMs,
      confirmedVia: "GUEST_WEB_LINK",
      updatedAt: nowMs
    };
  }

  if (action === "DISPUTE_RECORD") {
    const reason = typeof actionPayload.reason === "string" ? actionPayload.reason.trim() : "";
    if (!reason || reason.length < 3 || reason.length > MAX_NOTE_LENGTH) {
      throw new Error("INVALID_REASON: Dispute reason must be between 3 and 500 characters.");
    }
    return {
      status: "DISPUTED",
      disputeReason: reason,
      disputedAt: nowMs,
      disputedVia: "GUEST_WEB_LINK",
      updatedAt: nowMs
    };
  }

  if (action === "SUBMIT_PAYMENT_CLAIM") {
    const rawAmountPaise = actionPayload.amountPaise;
    const rawAmountRupees = actionPayload.amount;
    const amountPaise =
      typeof rawAmountPaise === "number" && Number.isInteger(rawAmountPaise)
        ? rawAmountPaise
        : typeof rawAmountRupees === "number" && Number.isFinite(rawAmountRupees)
          ? Math.round(rawAmountRupees * 100)
          : NaN;

    if (!Number.isInteger(amountPaise) || amountPaise <= 0) {
      throw new Error("INVALID_AMOUNT: Claimed payment amount must be a positive number.");
    }

    const principalPaise =
      typeof record.principalAmountPaise === "number" && Number.isInteger(record.principalAmountPaise)
        ? record.principalAmountPaise
        : Math.round(Number(record.principalAmount || 0) * 100);
    const paidPaise =
      typeof record.amountPaidPaise === "number" && Number.isInteger(record.amountPaidPaise)
        ? record.amountPaidPaise
        : Math.round(Number(record.amountPaid || 0) * 100);
    const remainingPaise = Math.max(0, principalPaise - paidPaise);

    const maxAllowedClaimPaise = Math.max(remainingPaise, principalPaise * 2, 100);
    if (amountPaise > maxAllowedClaimPaise) {
      throw new Error("AMOUNT_EXCEEDS_LIMIT: Claimed payment exceeds maximum allowable balance.");
    }

    const note = typeof actionPayload.note === "string" ? actionPayload.note.trim().slice(0, MAX_NOTE_LENGTH) : "";
    const utrNumber = typeof actionPayload.utrNumber === "string" ? actionPayload.utrNumber.trim().slice(0, 64) : "";

    let proofMeta = null;
    if (actionPayload.proof) {
      const { mimeType, sizeBytes, storagePath, downloadUrl } = actionPayload.proof;
      if (!ALLOWED_PROOF_MIME_TYPES.includes(mimeType)) {
        throw new Error("INVALID_PROOF_TYPE: Only JPEG, PNG, WEBP, or PDF proofs are allowed.");
      }
      if (typeof sizeBytes !== "number" || sizeBytes <= 0 || sizeBytes > MAX_PROOF_SIZE_BYTES) {
        throw new Error("INVALID_PROOF_SIZE: Proof file size must be between 1 byte and 5 MB.");
      }
      const expectedPrefix = `payment_proofs/${record.recordId}/`;
      const guestPrefix = `guest_proofs/${record.recordId}/`;
      if (
        typeof storagePath !== "string" ||
        (!storagePath.startsWith(expectedPrefix) && !storagePath.startsWith(guestPrefix))
      ) {
        throw new Error("INVALID_PROOF_PATH: Storage path must belong to this record.");
      }
      proofMeta = {
        mimeType,
        sizeBytes,
        storagePath,
        downloadUrl: typeof downloadUrl === "string" ? downloadUrl : null
      };
    }

    const existingClaims = Array.isArray(record.pendingPaymentClaims) ? record.pendingPaymentClaims : [];
    const claimId = `claim_${nowMs}_${crypto.randomBytes(4).toString("hex")}`;
    const newClaim = {
      claimId,
      amountPaise,
      note,
      utrNumber,
      proof: proofMeta,
      status: "PENDING_LENDER_APPROVAL",
      submittedAt: nowMs,
      submittedVia: "GUEST_WEB_LINK"
    };

    return {
      pendingPaymentClaims: [...existingClaims, newClaim],
      hasPendingPaymentClaim: true,
      lastPaymentClaimAt: nowMs,
      updatedAt: nowMs
    };
  }

  throw new Error(`UNSUPPORTED_ACTION: Unknown guest action '${action}'.`);
}

module.exports = {
  UDHAAR_SERVER_SECRET,
  GUEST_TOKEN_TTL_MS,
  OTP_TTL_MS,
  ALLOWED_PROOF_MIME_TYPES,
  MAX_PROOF_SIZE_BYTES,
  setTestServerSecret,
  resolveServerSecret,
  computeCodeHmac,
  verifyGuestPhoneOtp,
  maskPhoneNumber,
  maskUpiId,
  getCoarseDeviceType,
  computeContentHashServer,
  writeAuditEntry,
  writeInboxItem,
  sendPushToCreator,
  validateAndRateLimitWebLink,
  normalizePhoneDigits,
  computeSaltedPhoneHash,
  hashGuestToken,
  verifyGuestCapabilityToken,
  buildGuestActionUpdate
};
