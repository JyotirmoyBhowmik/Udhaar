/**
 * Authentication REST Endpoints Router (functions/src/auth/authEndpoints.js)
 * Implements Stage S1 Identity API routes with RFC 9457 error handling.
 */

const {
  startSignup,
  verifyFlowOtp,
  submitSignupEmail,
  completeSignup,
  startSignin,
  verifySignin,
  _inMemoryUsers
} = require("./authService");
const { listUserDevices, revokeDeviceSession } = require("../sessions/sessionManager");
const { sendProblemDetails, requireAuth } = require("../middleware/authMiddleware");
const { getWhatsAppProvider } = require("../providers");

function createAuthRouter() {
  const express = require("express");
  const router = express.Router();

  // 1. POST /auth/signup/start
  router.post("/auth/signup/start", async (req, res, next) => {
    try {
      const phoneNumber = req.body?.phoneNumber || req.body?.phone;
      const { locale, botCheckToken } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";

      const result = await startSignup({
        phoneNumber,
        locale,
        botCheckToken,
        clientIp
      });

      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 2. POST /auth/otp/verify
  router.post("/auth/otp/verify", async (req, res, next) => {
    try {
      const { flowId, code, purpose = "SIGNUP" } = req.body || {};
      const result = await verifyFlowOtp({ flowId, code, purpose });
      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 3. POST /auth/signup/email
  router.post("/auth/signup/email", async (req, res, next) => {
    try {
      const { flowId, flowToken, email, locale } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";

      const result = await submitSignupEmail({
        flowId,
        flowToken,
        email,
        locale,
        clientIp
      });

      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. POST /auth/signup/complete
  router.post("/auth/signup/complete", async (req, res, next) => {
    try {
      const {
        flowId,
        flowToken,
        phoneNumber,
        email,
        displayName,
        acceptedTermsVersion,
        acceptedPrivacyVersion,
        isAge18Plus,
        allowWhatsAppReminders
      } = req.body || {};

      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const userAgent = req.headers["user-agent"] || "";
      const clientType = req.headers["x-client-platform"] === "android" ? "ANDROID" : "WEB";

      const result = await completeSignup({
        flowId,
        flowToken,
        phoneNumber,
        email,
        displayName,
        acceptedTermsVersion,
        acceptedPrivacyVersion,
        isAge18Plus,
        allowWhatsAppReminders,
        clientType,
        clientIp,
        userAgent
      });

      // Set __session cookie if web client
      if (clientType === "WEB") {
        res.cookie("__session", result.sessionCookieValue, {
          httpOnly: true,
          secure: true,
          sameSite: "strict",
          maxAge: 7 * 24 * 60 * 60 * 1000 // 7 days
        });
      }

      return res.status(201).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 5. POST /auth/signin/start (Non-enumerable uniform response)
  router.post("/auth/signin/start", async (req, res, next) => {
    try {
      const { phoneNumber, locale } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";

      const result = await startSignin({
        phoneNumber,
        locale,
        clientIp
      });

      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 6. POST /auth/signin/verify
  router.post("/auth/signin/verify", async (req, res, next) => {
    try {
      const { flowId, code, phoneNumber } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const userAgent = req.headers["user-agent"] || "";
      const clientType = req.headers["x-client-platform"] === "android" ? "ANDROID" : "WEB";

      const result = await verifySignin({
        flowId,
        code,
        phoneNumber,
        clientType,
        clientIp,
        userAgent
      });

      if (clientType === "WEB") {
        res.cookie("__session", result.sessionCookieValue, {
          httpOnly: true,
          secure: true,
          sameSite: "strict",
          maxAge: 7 * 24 * 60 * 60 * 1000
        });
      }

      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 7. GET /me (Profile)
  router.get("/me", requireAuth, async (req, res, next) => {
    try {
      const user = _inMemoryUsers.get(req.user.uid);
      if (!user) {
        return sendProblemDetails(res, {
          status: 404,
          code: "ERR_USER_NOT_FOUND",
          title: "Not Found",
          detail: "User profile could not be found."
        });
      }

      return res.status(200).json({
        uid: user.uid,
        displayName: user.displayName,
        locale: user.locale,
        defaultCurrency: user.defaultCurrency,
        createdAtMs: user.createdAtMs
      });
    } catch (err) {
      next(err);
    }
  });

  // 8. GET /me/devices
  router.get("/me/devices", requireAuth, async (req, res, next) => {
    try {
      const devices = await listUserDevices(req.user.uid);
      return res.status(200).json(devices);
    } catch (err) {
      next(err);
    }
  });

  // 8b. POST /me/devices (Register Web Push or Mobile Device)
  router.post("/me/devices", requireAuth, async (req, res, next) => {
    try {
      const { registerDevice } = require("../push/pushService");
      const device = await registerDevice({
        uid: req.user.uid,
        deviceData: req.body || {}
      });
      return res.status(201).json(device);
    } catch (err) {
      next(err);
    }
  });

  // 9. DELETE /me/devices/:deviceId
  router.delete("/me/devices/:deviceId", requireAuth, async (req, res, next) => {
    try {
      const { deviceId } = req.params;
      const { revokeDevice } = require("../push/pushService");
      await revokeDevice({ uid: req.user.uid, deviceId });
      await revokeDeviceSession(req.user.uid, deviceId);
      return res.status(204).send();
    } catch (err) {
      next(err);
    }
  });

  // 10. POST /webhooks/whatsapp
  router.post("/webhooks/whatsapp", async (req, res) => {
    const provider = getWhatsAppProvider();
    if (provider.verifyWebhookSignature && !provider.verifyWebhookSignature(req.headers, req.body)) {
      return res.status(401).send("Invalid signature");
    }

    // Process delivery status idempotently
    return res.status(200).send("EVENT_RECEIVED");
  });

  return router;
}

module.exports = {
  createAuthRouter
};
