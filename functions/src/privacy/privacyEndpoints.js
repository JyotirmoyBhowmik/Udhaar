/**
 * DPDP Act 2023 Privacy Tools REST API Endpoints (functions/src/privacy/privacyEndpoints.js)
 * Implements Stage S8 Privacy Endpoints:
 * - DELETE /api/v1/me/account (Right to Erasure with cryptographic key shredding)
 * - POST /api/v1/me/account/cancel-deletion (Cooling period recovery)
 * - GET /api/v1/me/consent (Consent inspection)
 * - POST /api/v1/me/consent (Consent withdrawal / modification)
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const { requireStepUpAuth } = require("../middleware/stepUpMiddleware");
const {
  requestAccountDeletion,
  cancelAccountDeletion,
  getUserConsents,
  updateUserConsents
} = require("./privacyService");

function extractClientIp(req) {
  return (
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    "127.0.0.1"
  );
}

function createPrivacyRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. Request Account Deletion (Cryptographic Shredding)
  router.delete(["/account", "/me/account"], requireStepUpAuth, async (req, res, next) => {
    try {
      const { reason } = req.body || {};
      const clientIp = extractClientIp(req);
      const result = await requestAccountDeletion({
        uid: req.user.uid,
        reason,
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 2. Cancel Account Deletion (30-day cooling period recovery)
  router.post(["/account/cancel-deletion", "/me/account/cancel-deletion"], async (req, res, next) => {
    try {
      const clientIp = extractClientIp(req);
      const result = await cancelAccountDeletion({
        uid: req.user.uid,
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 3. Get Consents
  router.get(["/consent", "/me/consent"], async (req, res, next) => {
    try {
      const result = await getUserConsents({ uid: req.user.uid });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. Update / Withdraw Consents
  router.post(["/consent", "/me/consent"], async (req, res, next) => {
    try {
      const clientIp = extractClientIp(req);
      const result = await updateUserConsents({
        uid: req.user.uid,
        consentsUpdates: req.body || {},
        clientIp
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createPrivacyRouter
};
