const crypto = require("node:crypto");

let FieldValue;
try {
  FieldValue = require("firebase-admin/firestore").FieldValue;
} catch (_) {
  FieldValue = { serverTimestamp: () => ({ toMillis: () => Date.now() }) };
}

let getAuth;
try {
  getAuth = require("firebase-admin/auth").getAuth;
} catch (_) {
  getAuth = () => null;
}

let getStorage;
try {
  getStorage = require("firebase-admin/storage").getStorage;
} catch (_) {
  getStorage = () => null;
}

let getMessaging;
try {
  getMessaging = require("firebase-admin/messaging").getMessaging;
} catch (_) {
  getMessaging = () => null;
}

const ANDROID_CHANNEL_ID = "udhaar_chat_messages_channel";

/**
 * Constant-time comparison of a guest secret against the stored SHA-256 hex hash.
 */
function safeSecretEqual(secret, storedHashHex) {
  if (!secret || typeof secret !== "string" || !storedHashHex || typeof storedHashHex !== "string") {
    return false;
  }
  const secretHash = crypto.createHash("sha256").update(secret.trim()).digest("hex");
  if (secretHash.length !== storedHashHex.length) {
    return false;
  }
  return crypto.timingSafeEqual(
    Buffer.from(secretHash, "hex"),
    Buffer.from(storedHashHex, "hex")
  );
}

/**
 * Masks a 10-digit subscriber phone number as "98*** ***10".
 */
function maskPhoneNumber(raw) {
  if (!raw || typeof raw !== "string") return "—";
  const digitsOnly = raw.replace(/\D+/g, "");
  if (!digitsOnly) return "—";
  const core = digitsOnly.length > 10 ? digitsOnly.slice(-10) : digitsOnly;
  if (core.length <= 4) {
    return core.charAt(0) + "***";
  }
  const firstTwo = core.slice(0, 2);
  const lastTwo = core.slice(-2);
  const prefix = digitsOnly.length > 10 ? "+" + digitsOnly.slice(0, digitsOnly.length - 10) + " " : "";
  return prefix + firstTwo + "*** ***" + lastTwo;
}

/**
 * Masks a UPI ID (VPA) as "ba*** ***an@okaxis".
 */
function maskUpiId(raw) {
  if (!raw || typeof raw !== "string") return "—";
  const trimmed = raw.trim();
  if (!trimmed) return "—";
  const atIndex = trimmed.indexOf("@");
  const localPart = atIndex >= 0 ? trimmed.slice(0, atIndex) : trimmed;
  const domainSuffix = atIndex >= 0 ? trimmed.slice(atIndex) : "";
  if (localPart.length <= 4) {
    return localPart.charAt(0) + "***" + domainSuffix;
  }
  const firstTwo = localPart.slice(0, 2);
  const lastTwo = localPart.slice(-2);
  return firstTwo + "*** ***" + lastTwo + domainSuffix;
}

/**
 * Extracts coarse device type from User-Agent without storing IP or raw fingerprint.
 */
function getCoarseDeviceType(userAgent) {
  if (!userAgent || typeof userAgent !== "string") return "Unknown";
  if (/tablet|ipad/i.test(userAgent)) return "Tablet";
  if (/mobile|android|iphone/i.test(userAgent)) return "Mobile";
  return "Desktop";
}

/**
 * Computes deterministic SHA-256 content hash matching Android VerificationCryptoHelper.
 */
function computeContentHashServer(record, plan = null, installments = []) {
  const normalizedLender = (record.lenderName || "").trim().toLowerCase();
  const normalizedBorrower = (record.borrowerName || "").trim().toLowerCase();
  const formattedAmount = Number(record.principalAmount || 0).toFixed(2);
  const normalizedCurrency = (record.currency || "INR").trim().toUpperCase();
  const normalizedExpiry = record.expiryDateMillis != null && record.expiryDateMillis > 0 ? record.expiryDateMillis : 0;
  const interestSegment = record.isInterestEnabled
    ? `1|${record.interestType || "SIMPLE"}|${Number(record.interestRate || 0).toFixed(4)}|${record.interestRatePeriod || "PER_MONTH"}|${record.compoundFrequency || "MONTHLY"}`
    : "0|NONE|0.0000|NONE|NONE";

  let canonicalString = `v1|lender=${normalizedLender}|borrower=${normalizedBorrower}|amount=${formattedAmount}|currency=${normalizedCurrency}|start=${record.startDateMillis}|expiry=${normalizedExpiry}|interest=${interestSegment}`;

  if (plan && Array.isArray(installments) && installments.length > 0) {
    canonicalString += `|plan=${plan.installmentCount};${plan.frequency};${plan.interestMode};${plan.totalAmountDue}`;
    const sortedInst = [...installments].sort((a, b) => a.sequence - b.sequence);
    canonicalString += `|inst=${sortedInst.map((i) => `${i.sequence}:${i.dueDate}:${i.amountDue}`).join(";")}`;
  }

  return crypto.createHash("sha256").update(canonicalString, "utf8").digest("hex");
}

/**
 * Writes an immutable audit entry to `/shared_records/{remoteId}/audit/{eventId}`.
 */
async function writeAuditEntry(db, remoteId, {
  actorType,
  eventType,
  maskedPhone = "",
  hashBefore = "",
  hashAfter = "",
  summaryText,
  deviceType = "Unknown",
  now = Date.now(),
}) {
  const auditRef = db.collection("shared_records").doc(remoteId).collection("audit").doc();
  await auditRef.set({
    eventId: auditRef.id,
    remoteId,
    actorType, // 'creator', 'app_counterparty', 'guest_phone_verified', 'guest_link_only'
    eventType,
    maskedPhone,
    hashBefore,
    hashAfter,
    summaryText,
    deviceType,
    timeMillis: now,
    createdAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Writes an in-app inbox item to `/shared_records/{remoteId}/inbox/{inboxId}`.
 */
async function writeInboxItem(db, remoteId, {
  type,
  title,
  body,
  claimId = null,
  proposalId = null,
  amount = null,
  dateMillis = null,
  metadata = {},
  now = Date.now(),
}) {
  const inboxRef = db.collection("shared_records").doc(remoteId).collection("inbox").doc();
  await inboxRef.set({
    inboxId: inboxRef.id,
    remoteId,
    type,
    title,
    body,
    claimId,
    proposalId,
    amount,
    dateMillis,
    metadata,
    isRead: false,
    readAtMillis: -1,
    timeMillis: now,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Sends a high-priority FCM push notification to the record creator.
 * Respects lock screen privacy: never exposes amounts or names.
 */
async function sendPushToCreator(db, creatorUid, remoteId, subType) {
  if (!creatorUid) return;
  const profileRef = db.collection("user_profiles").doc(creatorUid);
  const profileSnap = await profileRef.get();
  if (!profileSnap.exists) return;

  const fcmToken = (profileSnap.data().fcmToken || "").trim();
  if (!fcmToken) return;

  try {
    await getMessaging().send({
      token: fcmToken,
      notification: {
        title: "Udhaar Update",
        body: "Update on one of your records",
      },
      data: {
        type: "GUEST_ACTION",
        subType,
        remoteId,
        title: "Udhaar Update",
        body: "Update on one of your records",
      },
      android: {
        priority: "high",
        notification: {
          channelId: ANDROID_CHANNEL_ID,
          clickAction: "android.intent.action.MAIN",
        },
      },
    });
  } catch (err) {
    const code = err && err.code ? String(err.code) : "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      await profileRef.update({
        fcmToken: "",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  }
}

/**
 * Validates web link existence, expiration, revocation, allowed action, and rate limits.
 */
async function validateAndRateLimitWebLink(db, recordId, secret, requiredAction, now = Date.now()) {
  const linkRef = db.collection("webLinks").doc(recordId);
  const linkSnap = await linkRef.get();
  if (!linkSnap.exists) {
    return { valid: false, status: 404, error: "Record link is invalid or does not exist." };
  }

  const link = linkSnap.data();
  if (!safeSecretEqual(secret, link.secretHash)) {
    return { valid: false, status: 403, error: "Invalid link security credentials." };
  }

  if (link.revoked) {
    return { valid: false, status: 403, error: "This link has been revoked by the sender." };
  }

  if (link.expiresAtMillis && now > link.expiresAtMillis) {
    return { valid: false, status: 410, error: "This verification link has expired." };
  }

  const allowedActions = Array.isArray(link.allowedActions) ? link.allowedActions : [];
  if (requiredAction && !allowedActions.includes(requiredAction)) {
    return { valid: false, status: 403, error: `Action '${requiredAction}' is not permitted on this link.` };
  }

  // Rate Limiting
  const counters = link.attemptCounters || {};
  const todayStr = new Date(now).toISOString().slice(0, 10);
  let actionCount = counters.actionCountToday || 0;
  if (counters.lastActionDay !== todayStr) {
    actionCount = 0;
  }

  if (actionCount >= 20) {
    return {
      valid: false,
      status: 429,
      retryAfter: 86400,
      error: "Daily action limit reached (max 20 per day). Please try again tomorrow.",
    };
  }

  const oneHourAgo = now - 3600000;
  if (counters.wrongOtpCount >= 5 && counters.lastOtpAttemptMillis > oneHourAgo) {
    const remainingSecs = Math.max(1, Math.ceil((counters.lastOtpAttemptMillis + 3600000 - now) / 1000));
    return {
      valid: false,
      status: 429,
      retryAfter: remainingSecs,
      error: "Too many failed attempts. Please wait 1 hour before trying again.",
    };
  }

  // Increment action counter
  await linkRef.update({
    "attemptCounters.actionCountToday": actionCount + 1,
    "attemptCounters.lastActionDay": todayStr,
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { valid: true, linkRef, link };
}

/**
 * Handles guest OTP verification when trustLevelRequired is PHONE_OTP or WHATSAPP_OTP.
 * Supports zero-cost WhatsApp OTP (6-digit numeric verification code) or Firebase Auth phone token.
 */
async function verifyGuestPhoneOtp(db, linkRef, link, guestTokenOrWhatsappOtp, now = Date.now()) {
  if (link.trustLevelRequired === "LINK_ONLY") {
    return { verified: true, maskedPhone: "" };
  }

  if (!guestTokenOrWhatsappOtp || typeof guestTokenOrWhatsappOtp !== "string") {
    return { verified: false, status: 401, error: "WhatsApp verification code is required for this action." };
  }

  const tokenStr = guestTokenOrWhatsappOtp.trim();

  // 1. Zero-Cost WhatsApp Verification Code (6-digit numeric OTP)
  if (link.whatsappOtpHash && /^\d{6}$/.test(tokenStr)) {
    const isMatch = safeSecretEqual(tokenStr, link.whatsappOtpHash);
    if (!isMatch) {
      const counters = link.attemptCounters || {};
      const newWrongCount = (counters.wrongOtpCount || 0) + 1;
      await linkRef.update({
        "attemptCounters.wrongOtpCount": newWrongCount,
        "attemptCounters.lastOtpAttemptMillis": now,
        updatedAt: FieldValue.serverTimestamp(),
      });
      return {
        verified: false,
        status: 403,
        error: "Incorrect WhatsApp verification code. Please check the 6-digit code sent on WhatsApp.",
      };
    }

    // Reset wrong OTP count on success
    await linkRef.update({
      "attemptCounters.wrongOtpCount": 0,
      whatsappVerified: true,
      updatedAt: FieldValue.serverTimestamp(),
    });

    return {
      verified: true,
      maskedPhone: link.counterpartyPhoneMasked || "WhatsApp Verified",
      whatsappVerified: true,
    };
  }

  // 2. Firebase Auth Phone Token Fallback
  let decoded;
  try {
    decoded = await getAuth().verifyIdToken(tokenStr);
  } catch (e) {
    return { verified: false, status: 401, error: "Invalid WhatsApp verification code or token." };
  }

  const rawPhone = (decoded.phone_number || "").trim();
  if (!rawPhone) {
    return { verified: false, status: 401, error: "Authenticated account does not have a verified phone number." };
  }

  // Verify phone matches stored phoneHash if provided
  if (link.phoneHash) {
    const salt = link.phoneSalt || "";
    const computedPhoneHash = crypto.createHash("sha256").update(rawPhone + salt).digest("hex");
    const isMatch = computedPhoneHash.length === link.phoneHash.length &&
      crypto.timingSafeEqual(Buffer.from(computedPhoneHash, "hex"), Buffer.from(link.phoneHash, "hex"));

    if (!isMatch) {
      const counters = link.attemptCounters || {};
      const newWrongCount = (counters.wrongOtpCount || 0) + 1;
      await linkRef.update({
        "attemptCounters.wrongOtpCount": newWrongCount,
        "attemptCounters.lastOtpAttemptMillis": now,
        updatedAt: FieldValue.serverTimestamp(),
      });
      return {
        verified: false,
        status: 403,
        error: "Verified phone number does not match the intended counterparty for this record.",
      };
    }
  } else {
    const newSalt = crypto.randomBytes(16).toString("hex");
    const newHash = crypto.createHash("sha256").update(rawPhone + newSalt).digest("hex");
    await linkRef.update({
      phoneSalt: newSalt,
      phoneHash: newHash,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  return { verified: true, maskedPhone: maskPhoneNumber(rawPhone), rawPhone };
}

/**
 * Purges or marks expired web links and cleans up orphaned guest claim attachments.
 * - Finds webLinks where expiresAtMillis < now and status !== "EXPIRED", or revoked == true and older than 7 days.
 * - For records with completed or expired claims, deletes orphaned proof files from Cloud Storage.
 */
async function purgeExpiredGuestData(db, storageBucket, now = Date.now()) {
  let expiredLinksCount = 0;
  let cleanedProofsCount = 0;

  if (!db) return { expiredLinksCount, cleanedProofsCount };

  // 1. Expire past-due active web links
  try {
    const linksSnap = await db.collection("webLinks").get();
    for (const doc of linksSnap.docs) {
      const data = doc.data();
      const isPastExpiry = typeof data.expiresAtMillis === "number" && data.expiresAtMillis < now;
      const isRevokedOld = data.revoked === true && ((data.revokedAtMillis || data.createdAtMillis || 0) < (now - 7 * 86400000));
      if ((isPastExpiry || isRevokedOld) && data.status !== "EXPIRED") {
        await doc.ref.update({
          status: "EXPIRED",
          expiredAtMillis: now,
          updatedAt: FieldValue.serverTimestamp(),
        });
        expiredLinksCount++;
      }
    }
  } catch (err) {
    console.error("Error purging expired webLinks:", err);
  }

  // 2. Query payment claims older than 30 days or marked CONFIRMED/REJECTED with storage proofs
  try {
    const recordsSnap = await db.collection("shared_records").get();
    for (const recordDoc of recordsSnap.docs) {
      const claimsSnap = await recordDoc.ref.collection("payment_claims").get();
      for (const claimDoc of claimsSnap.docs) {
        const claim = claimDoc.data();
        const isOld = (claim.claimedAtMillis || 0) < (now - 30 * 86400000);
        const isResolved = ["CONFIRMED", "REJECTED"].includes(claim.status);
        if ((isOld || isResolved) && claim.proofStoragePath) {
          if (storageBucket && typeof storageBucket.file === "function") {
            try {
              await storageBucket.file(claim.proofStoragePath).delete();
              cleanedProofsCount++;
            } catch (e) {
              // File might already be deleted or non-existent
            }
          }
          await claimDoc.ref.update({
            proofStoragePath: null,
            proofCleanedAt: FieldValue.serverTimestamp(),
          });
        }
      }
    }
  } catch (err) {
    console.error("Error purging payment claim proof storage:", err);
  }

  return { expiredLinksCount, cleanedProofsCount };
}

module.exports = {
  safeSecretEqual,
  maskPhoneNumber,
  maskUpiId,
  getCoarseDeviceType,
  computeContentHashServer,
  writeAuditEntry,
  writeInboxItem,
  sendPushToCreator,
  validateAndRateLimitWebLink,
  verifyGuestPhoneOtp,
  purgeExpiredGuestData,
};
