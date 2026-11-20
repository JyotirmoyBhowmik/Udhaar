/**
 * Hardened Capability Links & Guest Verification Endpoints (functions/src/links/linkEndpoints.js)
 * Implements Stage S6 REST API, anti-replay guards, signed upload URLs, and account upgrade bridge.
 */

const express = require("express");
const { requireAuth, sendProblemDetails } = require("../middleware/authMiddleware");
const {
  createCapabilityLink,
  getGuestRecordView,
  requestGuestOtp,
  verifyGuestOtp,
  submitGuestAction,
  generateProofUploadUrl,
  upgradeGuestToAccount
} = require("./linkService");

function extractToken(req) {
  return (
    req.headers["x-guest-token"] ||
    req.headers["x-capability-token"] ||
    (req.headers.authorization && req.headers.authorization.startsWith("Bearer ")
      ? req.headers.authorization.slice(7).trim()
      : null) ||
    (req.body && (req.body.token || req.body.secret || req.body.rawToken)) ||
    (req.query && (req.query.token || req.query.secret)) ||
    ""
  );
}

function extractClientIp(req) {
  return (
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    "127.0.0.1"
  );
}

function createLinksRouter() {
  const router = express.Router();

  // 1. Create Capability Link (Authenticated Creator)
  router.post(["/create", "/links/create"], requireAuth, async (req, res, next) => {
    try {
      const { recordId, expiresInDays } = req.body || {};
      const result = await createCapabilityLink({
        uid: req.user.uid,
        recordId,
        expiresInDays
      });
      res.status(201).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 2. Guest View Record
  router.get(["/:recordId", "/links/:recordId"], async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const record = await getGuestRecordView({
        recordId,
        rawToken,
        clientIp
      });

      res.status(200).json(record);
    } catch (err) {
      next(err);
    }
  });

  // 3. Request WhatsApp OTP
  router.post(["/:recordId/otp/request", "/links/:recordId/otp/request"], async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);
      const locale = req.body?.locale || "en";

      const result = await requestGuestOtp({
        recordId,
        rawToken,
        clientIp,
        locale
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. Verify WhatsApp OTP
  router.post(["/:recordId/otp/verify", "/links/:recordId/otp/verify"], async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);
      const { code, flowId } = req.body || {};

      const result = await verifyGuestOtp({
        recordId,
        rawToken,
        code,
        flowId,
        clientIp
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 5. Submit Guest Action (Confirm, Dispute, Propose Date, Payment Claim)
  router.post(["/:recordId/action", "/links/:recordId/action"], async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);
      const { action, payload, nonce, idempotencyKey } = req.body || {};

      const result = await submitGuestAction({
        recordId,
        rawToken,
        action,
        payload,
        nonce,
        idempotencyKey,
        clientIp
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 6. Generate Signed GCS Upload URL (5MB limit)
  router.post(["/:recordId/upload-proof", "/links/:recordId/upload-proof"], async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);
      const { contentType, sizeBytes, ext } = req.body || {};

      const result = await generateProofUploadUrl({
        recordId,
        rawToken,
        contentType,
        sizeBytes,
        ext,
        clientIp
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 7. Account Upgrade Bridge (Authenticated User Claims Record)
  router.post(["/:recordId/upgrade", "/links/:recordId/upgrade"], requireAuth, async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await upgradeGuestToAccount({
        recordId,
        rawToken,
        targetUid: req.user.uid,
        clientIp
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

// Separate Router for Legacy Endpoints (mounted at root without parameter wildcards)
function createLegacyGuestRouter() {
  const router = express.Router();

  // Backward Compatibility Handlers for web/u/guest.js
  router.post("/getGuestRecord", async (req, res, next) => {
    try {
      const { recordId } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const record = await getGuestRecordView({
        recordId,
        rawToken,
        clientIp
      });

      res.status(200).json({ success: true, record });
    } catch (err) {
      next(err);
    }
  });

  router.post("/verifyGuestPhoneOtp", async (req, res, next) => {
    try {
      const { recordId, code, flowId } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await verifyGuestOtp({
        recordId,
        rawToken,
        code,
        flowId,
        clientIp
      });

      res.status(200).json({ success: true, ...result });
    } catch (err) {
      next(err);
    }
  });

  router.post("/guestConfirm", async (req, res, next) => {
    try {
      const { recordId, code, flowId } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      if (code) {
        await verifyGuestOtp({
          recordId,
          rawToken,
          code,
          flowId,
          clientIp
        });
      }

      const result = await submitGuestAction({
        recordId,
        rawToken,
        action: "CONFIRM",
        clientIp
      });

      res.status(200).json({ success: true, ...result, trustLevel: "Private link" });
    } catch (err) {
      next(err);
    }
  });

  router.post("/guestDispute", async (req, res, next) => {
    try {
      const { recordId, reason } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await submitGuestAction({
        recordId,
        rawToken,
        action: "DISPUTE",
        payload: { reason },
        clientIp
      });

      res.status(200).json({ success: true, ...result });
    } catch (err) {
      next(err);
    }
  });

  router.post("/guestPaymentClaim", async (req, res, next) => {
    try {
      const { recordId, amount, amountPaise, mode, note, utrNumber, proof } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await submitGuestAction({
        recordId,
        rawToken,
        action: "PAYMENT_CLAIM",
        payload: {
          amount,
          amountPaise,
          paymentMode: mode,
          note,
          utrNumber,
          proof
        },
        clientIp
      });

      res.status(200).json({ success: true, ...result });
    } catch (err) {
      next(err);
    }
  });

  router.post("/guestProposeDate", async (req, res, next) => {
    try {
      const { recordId, proposedDate, note } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await submitGuestAction({
        recordId,
        rawToken,
        action: "PROPOSE_DATE",
        payload: { proposedDateMs: proposedDate, note },
        clientIp
      });

      res.status(200).json({ success: true, ...result });
    } catch (err) {
      next(err);
    }
  });

  router.post("/guestProofUploadUrl", async (req, res, next) => {
    try {
      const { recordId, mimeType, sizeBytes, ext } = req.body || {};
      const rawToken = extractToken(req);
      const clientIp = extractClientIp(req);

      const result = await generateProofUploadUrl({
        recordId,
        rawToken,
        contentType: mimeType,
        sizeBytes,
        ext,
        clientIp
      });

      res.status(200).json({ success: true, ...result });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createLinksRouter,
  createLegacyGuestRouter
};
