/**
 * DPDP Act 2023 Privacy Tools & Device Management REST API Endpoints
 * (functions/src/privacy/privacyEndpoints.js)
 * Implements Stage S7 & Stage S8 Privacy & Device Endpoints:
 * - DELETE /api/v1/me/account (Right to Erasure with cryptographic key shredding)
 * - POST /api/v1/me/account/cancel-deletion (Cooling period recovery)
 * - GET /api/v1/me/consent (Consent inspection)
 * - POST /api/v1/me/consent (Consent withdrawal / modification)
 * - POST /api/v1/me/devices (Register Web Push / FCM device)
 * - GET /api/v1/me/devices (List user devices)
 * - DELETE /api/v1/me/devices/:deviceId (Revoke device)
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
const {
  registerDevice,
  listUserDevices,
  revokeDevice
} = require("../push/pushService");

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

  // 5. Register Push Device
  router.post(["/devices", "/me/devices"], async (req, res, next) => {
    try {
      const device = await registerDevice({
        uid: req.user.uid,
        deviceData: req.body || {}
      });
      res.status(201).json(device);
    } catch (err) {
      next(err);
    }
  });

  // 6. List Push Devices
  router.get(["/devices", "/me/devices"], async (req, res, next) => {
    try {
      const devices = await listUserDevices({ uid: req.user.uid });
      res.status(200).json(devices);
    } catch (err) {
      next(err);
    }
  });

  // 7. Revoke Push Device
  router.delete(["/devices/:deviceId", "/me/devices/:deviceId"], async (req, res, next) => {
    try {
      const result = await revokeDevice({
        uid: req.user.uid,
        deviceId: req.params.deviceId
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
