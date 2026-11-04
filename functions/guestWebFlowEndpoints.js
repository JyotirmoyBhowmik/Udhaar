const crypto = require("node:crypto");
const { onRequest, onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineString } = require("firebase-functions/params");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const {
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
} = require("./guestWebFlow");

const DATABASE_ID = defineString("FIRESTORE_DATABASE_ID", {
  default: "ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf",
  description: "Named Cloud Firestore database ID for Udhaar",
});

const CORS_ORIGINS = [
  "https://udhaar.jyotirmoyb.com",
  "http://localhost:5000",
  "http://127.0.0.1:5000",
  "http://localhost:8080",
  "http://127.0.0.1:8080",
];

/**
 * 1. Callable: Creator generates a web verification link.
 * Stores only SHA-256 hash of secret in webLinks/{recordId}.
 */
exports.createGuestWebLink = onCall({ region: "asia-south1" }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated to create a web link.");
  }
  const {
    recordId,
    secretHash,
    counterpartyRole,
    phoneSalt,
    phoneHash,
    expiresInDays,
    trustLevelRequired,
    allowedActions,
    showPayQr,
    showNote,
  } = request.data || {};

  if (!recordId || typeof recordId !== "string" || !secretHash || typeof secretHash !== "string") {
    throw new HttpsError("invalid-argument", "Missing required link parameters (recordId, secretHash).");
  }

  const db = getFirestore(DATABASE_ID.value());
  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    throw new HttpsError("not-found", "Shared record not found.");
  }

  const record = recordSnap.data();
  if (record.creatorUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Only the record creator can generate a web verification link.");
  }

  const days = [7, 30, 90].includes(Number(expiresInDays)) ? Number(expiresInDays) : 30;
  const now = Date.now();
  const expiresAtMillis = now + days * 86400000;
  const trustLevel = ["LINK_ONLY", "PHONE_OTP"].includes(trustLevelRequired) ? trustLevelRequired : "PHONE_OTP";
  const validActions = Array.isArray(allowedActions) && allowedActions.length > 0
    ? allowedActions.filter((a) =>
        ["view", "confirm", "dispute", "pay_claim", "propose_date", "set_upi", "show_pay_qr"].includes(a)
      )
    : ["view", "confirm", "dispute", "pay_claim", "propose_date", "show_pay_qr"];

  const whatsappOtp = Math.floor(100000 + Math.random() * 900000).toString();
  const whatsappOtpHash = crypto.createHash("sha256").update(whatsappOtp).digest("hex");

  const linkData = {
    recordId,
    creatorUid: request.auth.uid,
    counterpartyRole: counterpartyRole === "LENDER" ? "LENDER" : "BORROWER",
    secretHash: secretHash.trim(),
    whatsappOtpHash,
    phoneSalt: (phoneSalt || "").trim(),
    phoneHash: (phoneHash || "").trim(),
    createdAt: FieldValue.serverTimestamp(),
    createdAtMillis: now,
    expiresAtMillis,
    revoked: false,
    allowedActions: validActions,
    trustLevelRequired: trustLevel,
    showPayQr: !!showPayQr,
    showNote: !!showNote,
    attemptCounters: {
      wrongOtpCount: 0,
      lastOtpAttemptMillis: 0,
      actionCountToday: 0,
      lastActionDay: new Date(now).toISOString().slice(0, 10),
    },
  };

  await db.collection("webLinks").doc(recordId).set(linkData);
  await recordRef.update({
    webLinkStatus: "ACTIVE",
    trustLevel: trustLevel === "PHONE_OTP" ? "PHONE_VERIFIED" : "LINK_ONLY",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: "creator",
    eventType: "LINK_CREATED",
    summaryText: `Creator generated web link with ${trustLevel} trust level (expires in ${days} days)`,
    now,
  });

  return {
    success: true,
    recordId,
    whatsappOtp,
    expiresAtMillis,
    trustLevelRequired: trustLevel,
  };
});

/**
 * 2. Callable: Creator revokes web verification link immediately (kill switch).
 */
exports.revokeGuestWebLink = onCall({ region: "asia-south1" }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }
  const { recordId } = request.data || {};
  if (!recordId) throw new HttpsError("invalid-argument", "Missing recordId.");

  const db = getFirestore(DATABASE_ID.value());
  const linkRef = db.collection("webLinks").doc(recordId);
  const linkSnap = await linkRef.get();
  if (!linkSnap.exists) throw new HttpsError("not-found", "Link does not exist.");

  const link = linkSnap.data();
  if (link.creatorUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Only the record creator can revoke this link.");
  }

  await linkRef.update({
    revoked: true,
    updatedAt: FieldValue.serverTimestamp(),
  });

  await db.collection("shared_records").doc(recordId).update({
    webLinkStatus: "REVOKED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: "creator",
    eventType: "LINK_REVOKED",
    summaryText: "Creator revoked web verification link",
  });

  return { success: true };
});

/**
 * 3. HTTPS POST: Guest opens web link and fetches safe public view.
 */
exports.getGuestRecord = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret } = req.body || {};
  if (!recordId || !secret) {
    res.status(400).json({ error: "Missing recordId or secret token." });
    return;
  }

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "view");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link } = validation;
  const recordSnap = await db.collection("shared_records").doc(recordId).get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }

  const record = recordSnap.data();
  let payeeUpiId = "";
  let payeeName = record.lenderName || "Lender";
  if (link.showPayQr && record.creatorUid) {
    const creatorProfSnap = await db.collection("user_profiles").doc(record.creatorUid).get();
    if (creatorProfSnap.exists) {
      payeeUpiId = (creatorProfSnap.data().upiId || "").trim();
      payeeName = (creatorProfSnap.data().displayName || payeeName).trim();
    }
  }

  let planSummary = null;
  const plansSnap = await db
    .collection("shared_records")
    .doc(recordId)
    .collection("installment_plans")
    .where("status", "==", "ACTIVE")
    .limit(1)
    .get();

  if (!plansSnap.empty) {
    const pDoc = plansSnap.docs[0];
    const p = pDoc.data();
    const instSnap = await pDoc.ref.collection("installments").get();
    const installments = instSnap.docs.map((d) => d.data()).sort((a, b) => a.sequence - b.sequence);
    const paidCount = installments.filter((i) => i.status === "PAID" || i.amountPaid >= i.amountDue).length;
    const nextDue = installments.find((i) => i.status !== "PAID" && i.amountPaid < i.amountDue);
    planSummary = {
      planId: p.planId,
      installmentCount: p.installmentCount,
      frequency: p.frequency,
      totalAmountDue: p.totalAmountDue,
      paidCount,
      nextDueDate: nextDue ? nextDue.dueDate : null,
      nextDueAmount: nextDue ? nextDue.amountDue - nextDue.amountPaid : 0,
    };
  }

  const isGiven = record.creatorRole === "GIVEN";
  const creatorName = isGiven ? record.lenderName : record.borrowerName;
  const counterpartyName = isGiven ? record.borrowerName : record.lenderName;

  await writeAuditEntry(db, recordId, {
    actorType: "guest_link_only",
    eventType: "VIEWED",
    summaryText: "Guest opened verification page",
    deviceType: getCoarseDeviceType(req.headers["user-agent"]),
  });

  res.status(200).json({
    recordId,
    creatorName,
    counterpartyName,
    counterpartyRole: link.counterpartyRole,
    creatorRole: record.creatorRole,
    principalAmount: record.principalAmount,
    amountPaid: record.amountPaid || 0,
    currency: record.currency || "INR",
    startDateMillis: record.startDateMillis,
    expiryDateMillis: record.expiryDateMillis,
    interestRate: record.interestRate || 0,
    interestType: record.interestType || "SIMPLE",
    isInterestEnabled: !!record.isInterestEnabled,
    verificationStatus: record.verificationStatus,
    trustLevelRequired: link.trustLevelRequired,
    allowedActions: link.allowedActions || [],
    showPayQr: !!link.showPayQr,
    payeeUpiId: link.showPayQr ? payeeUpiId : "",
    payeeName,
    counterpartyPhoneMasked: maskPhoneNumber(link.phoneHash ? "" : record.counterpartyPhone || ""),
    creatorPhoneMasked: maskPhoneNumber(record.creatorPhone || ""),
    creatorPhoneClean: (record.creatorPhone || "").replace(/\D+/g, ""),
    hasWhatsappOtp: !!link.whatsappOtpHash,
    installmentPlan: planSummary,
    allowNote: !!link.showNote,
  });
});

/**
 * 4. HTTPS POST: Guest confirms record terms.
 */
exports.guestConfirm = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret, guestIdToken, whatsappOtp } = req.body || {};
  if (!recordId || !secret) {
    res.status(400).json({ error: "Missing required parameters." });
    return;
  }

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "confirm");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link, linkRef } = validation;
  const otpResult = await verifyGuestPhoneOtp(db, linkRef, link, whatsappOtp || guestIdToken);
  if (!otpResult.verified) {
    res.status(otpResult.status).json({ error: otpResult.error });
    return;
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }

  const record = recordSnap.data();
  if (record.verificationStatus === "CONFIRMED") {
    res.status(400).json({ error: "This record has already been confirmed." });
    return;
  }

  const now = Date.now();
  const newHash = computeContentHashServer(record);
  const trustLevel = link.trustLevelRequired === "PHONE_OTP" ? "PHONE_VERIFIED" : "LINK_ONLY";

  await recordRef.update({
    verificationStatus: "CONFIRMED",
    verifiedAtMillis: now,
    trustLevel,
    verifiedPhoneMasked: otpResult.maskedPhone || "",
    contentHash: newHash,
    lastGuestAction: "CONFIRMED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: link.trustLevelRequired === "PHONE_OTP" ? "guest_phone_verified" : "guest_link_only",
    eventType: "CONFIRMED",
    maskedPhone: otpResult.maskedPhone,
    hashBefore: record.contentHash,
    hashAfter: newHash,
    summaryText: "Guest confirmed record terms on web",
    deviceType: getCoarseDeviceType(req.headers["user-agent"]),
    now,
  });

  await writeInboxItem(db, recordId, {
    type: "CONFIRMED",
    title: "Record Confirmed",
    body: `Counterparty confirmed the record via web (${trustLevel})`,
    now,
  });

  await sendPushToCreator(db, record.creatorUid, recordId, "CONFIRMED");

  res.status(200).json({
    success: true,
    verificationStatus: "CONFIRMED",
    trustLevel,
  });
});

/**
 * 5. HTTPS POST: Guest disputes record terms.
 */
exports.guestDispute = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret, guestIdToken, reasonCode, reasonText } = req.body || {};
  if (!recordId || !secret || !reasonCode) {
    res.status(400).json({ error: "Missing required parameters." });
    return;
  }

  const validReasons = ["WRONG_AMOUNT", "WRONG_DATE", "NEVER_TOOK_IT", "OTHER"];
  if (!validReasons.includes(reasonCode)) {
    res.status(400).json({ error: "Invalid dispute reason code." });
    return;
  }

  const sanitizedText = (reasonText || "").replace(/[\x00-\x1F\x7F]/g, "").slice(0, 300);

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "dispute");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link, linkRef } = validation;
  const otpResult = await verifyGuestPhoneOtp(db, linkRef, link, guestIdToken);
  if (!otpResult.verified) {
    res.status(otpResult.status).json({ error: otpResult.error });
    return;
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }

  const record = recordSnap.data();
  const fullDispute = sanitizedText ? `${reasonCode}: ${sanitizedText}` : reasonCode;
  const now = Date.now();

  await recordRef.update({
    verificationStatus: "DISPUTED",
    disputeReason: fullDispute,
    lastGuestAction: "DISPUTED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: link.trustLevelRequired === "PHONE_OTP" ? "guest_phone_verified" : "guest_link_only",
    eventType: "DISPUTED",
    maskedPhone: otpResult.maskedPhone,
    hashBefore: record.contentHash,
    hashAfter: record.contentHash,
    summaryText: `Guest disputed record: ${fullDispute}`,
    deviceType: getCoarseDeviceType(req.headers["user-agent"]),
    now,
  });

  await writeInboxItem(db, recordId, {
    type: "DISPUTED",
    title: "Record Disputed",
    body: `Counterparty disputed record: ${fullDispute}`,
    metadata: { reasonCode, reasonText: sanitizedText },
    now,
  });

  await sendPushToCreator(db, record.creatorUid, recordId, "DISPUTED");

  res.status(200).json({
    success: true,
    verificationStatus: "DISPUTED",
  });
});

/**
 * 6. HTTPS POST: Guest reports having made a repayment.
 */
exports.guestPaymentClaim = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret, guestIdToken, amount, dateMillis, mode, reference, proofFileName, proofMimeType } =
    req.body || {};
  const parsedAmount = Number(amount);
  if (!recordId || !secret || !parsedAmount || parsedAmount <= 0) {
    res.status(400).json({ error: "Invalid payment claim details." });
    return;
  }

  const validModes = ["UPI", "CASH", "BANK"];
  const paymentMode = validModes.includes(mode) ? mode : "UPI";

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "pay_claim");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link, linkRef } = validation;
  const otpResult = await verifyGuestPhoneOtp(db, linkRef, link, guestIdToken);
  if (!otpResult.verified) {
    res.status(otpResult.status).json({ error: otpResult.error });
    return;
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }
  const record = recordSnap.data();

  const claimId = "claim_" + crypto.randomBytes(8).toString("hex");
  let proofStoragePath = "";
  let signedUploadUrl = null;

  if (proofFileName && proofMimeType) {
    const allowedMimes = ["image/jpeg", "image/png", "image/webp", "application/pdf"];
    if (!allowedMimes.includes(proofMimeType)) {
      res.status(400).json({ error: "Invalid proof file type. Allowed: JPEG, PNG, WEBP, PDF." });
      return;
    }
    const safeName = proofFileName.replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 100);
    proofStoragePath = `guest_proofs/${recordId}/${claimId}_${safeName}`;
    try {
      const bucket = getStorage().bucket();
      const file = bucket.file(proofStoragePath);
      const [url] = await file.getSignedUrl({
        version: "v4",
        action: "write",
        expires: Date.now() + 15 * 60 * 1000,
        contentType: proofMimeType,
      });
      signedUploadUrl = url;
    } catch (err) {
      console.warn("Storage signed URL generation unavailable, proceeding without upload URL:", err.message);
    }
  }

  const now = Date.now();
  const cleanRef = (reference || "").replace(/[\x00-\x1F\x7F]/g, "").slice(0, 200);

  const claimDoc = {
    claimId,
    recordId,
    amount: parsedAmount,
    dateMillis: Number(dateMillis) || now,
    mode: paymentMode,
    reference: cleanRef,
    proofStoragePath,
    status: "PENDING_LENDER_CONFIRM",
    claimedByPhoneMasked: otpResult.maskedPhone,
    trustLevel: link.trustLevelRequired === "PHONE_OTP" ? "PHONE_VERIFIED" : "LINK_ONLY",
    claimedAtMillis: now,
    createdAt: FieldValue.serverTimestamp(),
  };

  await recordRef.collection("payment_claims").doc(claimId).set(claimDoc);
  await recordRef.update({
    pendingPaymentClaim: true,
    lastGuestAction: "PAYMENT_CLAIM",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: link.trustLevelRequired === "PHONE_OTP" ? "guest_phone_verified" : "guest_link_only",
    eventType: "PAYMENT_RECORDED",
    maskedPhone: otpResult.maskedPhone,
    summaryText: `Guest reported repayment of ${record.currency || "₹"}${parsedAmount} via ${paymentMode}`,
    now,
  });

  await writeInboxItem(db, recordId, {
    type: "PAYMENT_CLAIM",
    title: "Repayment Claimed",
    body: `Counterparty reported payment of ${record.currency || "₹"}${parsedAmount} (${paymentMode})`,
    claimId,
    amount: parsedAmount,
    dateMillis: Number(dateMillis) || now,
    metadata: { mode: paymentMode, reference: cleanRef, proofStoragePath },
    now,
  });

  await sendPushToCreator(db, record.creatorUid, recordId, "PAYMENT_CLAIM");

  res.status(200).json({
    success: true,
    claimId,
    uploadUrl: signedUploadUrl,
  });
});

/**
 * 7. HTTPS POST: Guest proposes new return date.
 */
exports.guestProposeDate = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret, guestIdToken, proposedDueDateMillis, message } = req.body || {};
  const parsedDate = Number(proposedDueDateMillis);
  const now = Date.now();
  if (!recordId || !secret || !parsedDate || parsedDate <= now || parsedDate > now + 365 * 86400000) {
    res.status(400).json({ error: "Invalid proposed due date. Must be between tomorrow and 365 days." });
    return;
  }

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "propose_date");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link, linkRef } = validation;
  const otpResult = await verifyGuestPhoneOtp(db, linkRef, link, guestIdToken);
  if (!otpResult.verified) {
    res.status(otpResult.status).json({ error: otpResult.error });
    return;
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }
  const record = recordSnap.data();

  const proposalId = "prop_" + crypto.randomBytes(8).toString("hex");
  const cleanMsg = (message || "").replace(/[\x00-\x1F\x7F]/g, "").slice(0, 300);

  const proposalDoc = {
    proposalId,
    recordId,
    proposedDueDateMillis: parsedDate,
    message: cleanMsg,
    status: "PENDING_LENDER_DECISION",
    proposedByPhoneMasked: otpResult.maskedPhone,
    proposedAtMillis: now,
    createdAt: FieldValue.serverTimestamp(),
  };

  await recordRef.collection("date_proposals").doc(proposalId).set(proposalDoc);
  await recordRef.update({
    pendingDateProposal: true,
    lastGuestAction: "DATE_PROPOSED",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: link.trustLevelRequired === "PHONE_OTP" ? "guest_phone_verified" : "guest_link_only",
    eventType: "EXTENSION_REQUESTED",
    maskedPhone: otpResult.maskedPhone,
    summaryText: `Guest requested due date extension to ${new Date(parsedDate).toISOString().slice(0, 10)}`,
    now,
  });

  await writeInboxItem(db, recordId, {
    type: "DATE_PROPOSAL",
    title: "Extension Proposed",
    body: `Counterparty proposed new return date: ${new Date(parsedDate).toISOString().slice(0, 10)}`,
    proposalId,
    dateMillis: parsedDate,
    metadata: { message: cleanMsg },
    now,
  });

  await sendPushToCreator(db, record.creatorUid, recordId, "DATE_PROPOSED");

  res.status(200).json({
    success: true,
    proposalId,
  });
});

/**
 * 8. HTTPS POST: Guest lender sets repayment UPI ID.
 */
exports.guestSetUpi = onRequest({ region: "asia-south1", cors: CORS_ORIGINS }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed. Use POST." });
    return;
  }
  const { recordId, secret, guestIdToken, upiId } = req.body || {};
  const cleanUpi = (upiId || "").trim();
  const upiRegex = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/;
  if (!recordId || !secret || !upiRegex.test(cleanUpi)) {
    res.status(400).json({ error: "Invalid UPI ID format." });
    return;
  }

  const db = getFirestore(DATABASE_ID.value());
  const validation = await validateAndRateLimitWebLink(db, recordId, secret, "set_upi");
  if (!validation.valid) {
    if (validation.retryAfter) res.set("Retry-After", String(validation.retryAfter));
    res.status(validation.status).json({ error: validation.error });
    return;
  }

  const { link, linkRef } = validation;
  if (link.counterpartyRole !== "LENDER") {
    res.status(403).json({ error: "Only a guest lender can configure repayment UPI ID." });
    return;
  }

  const otpResult = await verifyGuestPhoneOtp(db, linkRef, link, guestIdToken);
  if (!otpResult.verified) {
    res.status(otpResult.status).json({ error: otpResult.error });
    return;
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) {
    res.status(404).json({ error: "Record not found." });
    return;
  }
  const record = recordSnap.data();

  await recordRef.update({
    guestUpiId: cleanUpi,
    lastGuestAction: "SET_UPI",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: link.trustLevelRequired === "PHONE_OTP" ? "guest_phone_verified" : "guest_link_only",
    eventType: "EDITED",
    maskedPhone: otpResult.maskedPhone,
    summaryText: `Guest lender configured repayment UPI: ${maskUpiId(cleanUpi)}`,
  });

  await writeInboxItem(db, recordId, {
    type: "GUEST_UPI",
    title: "Repayment UPI Added",
    body: `Guest lender provided UPI: ${maskUpiId(cleanUpi)}`,
    metadata: { guestUpiMasked: maskUpiId(cleanUpi) },
  });

  await sendPushToCreator(db, record.creatorUid, recordId, "SET_UPI");

  res.status(200).json({ success: true });
});

/**
 * 9. Callable: Link guest actions to account when user signs in or installs app.
 */
exports.claimGuestActions = onCall({ region: "asia-south1" }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }
  const { recordId, secret, phone } = request.data || {};
  if (!recordId) throw new HttpsError("invalid-argument", "Missing recordId.");

  const db = getFirestore(DATABASE_ID.value());
  const linkRef = db.collection("webLinks").doc(recordId);
  const linkSnap = await linkRef.get();
  if (!linkSnap.exists) {
    return { success: false, linked: false, reason: "No web link associated with record." };
  }
  const linkData = linkSnap.data();

  // Validate secret or verified phone
  const userPhone = request.auth.token && request.auth.token.phone_number;
  const isSecretValid = secret && safeSecretEqual(secret, linkData.secretHash);
  const isPhoneMatch = (userPhone && linkData.phoneVerified && userPhone === linkData.phoneVerified) ||
                        (phone && linkData.phoneVerified && phone === linkData.phoneVerified);

  if (!isSecretValid && !isPhoneMatch && linkData.status !== "ACTIVE") {
    throw new HttpsError("permission-denied", "Invalid link secret or unmatched phone number.");
  }

  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) throw new HttpsError("not-found", "Shared record not found.");

  const record = recordSnap.data();
  const userUid = request.auth.uid;

  if (record.creatorUid === userUid) {
    return { success: false, linked: false, reason: "User is the creator of this record." };
  }

  await linkRef.update({
    status: "CLAIMED",
    claimedByUid: userUid,
    claimedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  await recordRef.update({
    counterpartyUid: userUid,
    participantUids: [record.creatorUid, userUid],
    trustLevel: "APP_VERIFIED",
    lastGuestAction: "CLAIMED_BY_APP",
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditEntry(db, recordId, {
    actorType: "app_counterparty",
    eventType: "CONFIRMED",
    summaryText: "Guest claimed and linked record via installed Udhaar app",
  });

  await writeInboxItem(db, recordId, {
    targetUid: record.creatorUid,
    title: "Udhaar App Linked",
    body: "The other person opened and linked this record with their Udhaar app.",
    type: "RECORD_CLAIMED",
  });

  await sendPushToCreator(db, record.creatorUid, "Udhaar Update", "The counterparty linked this record in their Udhaar app.");

  return { success: true, linked: true };
});

/**
 * 10. Callable: Creator exercises privacy control and purges guest claim attachments.
 */
exports.deleteGuestData = onCall({ region: "asia-south1" }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }
  const { recordId } = request.data || {};
  if (!recordId) throw new HttpsError("invalid-argument", "Missing recordId.");

  const db = getFirestore(DATABASE_ID.value());
  const recordRef = db.collection("shared_records").doc(recordId);
  const recordSnap = await recordRef.get();
  if (!recordSnap.exists) throw new HttpsError("not-found", "Record not found.");

  const record = recordSnap.data();
  if (record.creatorUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Only creator can delete guest data.");
  }

  const claims = await recordRef.collection("payment_claims").get();
  for (const doc of claims.docs) {
    const data = doc.data();
    if (data.proofStoragePath) {
      try {
        await getStorage().bucket().file(data.proofStoragePath).delete();
      } catch (e) {
        // file may already be deleted
      }
    }
    await doc.ref.delete();
  }

  const proposals = await recordRef.collection("date_proposals").get();
  for (const doc of proposals.docs) {
    await doc.ref.delete();
  }

  await writeAuditEntry(db, recordId, {
    actorType: "creator",
    eventType: "EDITED",
    summaryText: "Creator deleted all guest claim attachments and proposal data",
  });

  return { success: true };
});

/**
 * 11. Scheduled (24h) & Callable: Automated cleanup of expired web links and orphaned guest claim attachments.
 */
exports.cleanupExpiredGuestDataScheduled = onSchedule(
  {
    schedule: "every 24 hours",
    region: "asia-south1",
    timeZone: "Asia/Kolkata",
  },
  async () => {
    const db = getFirestore(DATABASE_ID.value());
    let bucket = null;
    try {
      bucket = getStorage().bucket();
    } catch (_) {}
    const result = await purgeExpiredGuestData(db, bucket, Date.now());
    console.log(`[Scheduled Cleanup] Expired links: ${result.expiredLinksCount}, Cleaned proofs: ${result.cleanedProofsCount}`);
    return result;
  }
);

exports.cleanupExpiredGuestData = onCall({ region: "asia-south1" }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated to invoke cleanup.");
  }
  const db = getFirestore(DATABASE_ID.value());
  let bucket = null;
  try {
    bucket = getStorage().bucket();
  } catch (_) {}
  const result = await purgeExpiredGuestData(db, bucket, Date.now());
  return { success: true, ...result };
});
