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
  verifySignin
} = require("./authService");
const { getAuthRepository } = require("../repositories");
const { listUserDevices, revokeDeviceSession } = require("../sessions/sessionManager");
const { sendProblemDetails, requireAuth } = require("../middleware/authMiddleware");
const { requireStepUpAuth, generateStepUpToken } = require("../middleware/stepUpMiddleware");
const { getWhatsAppProvider } = require("../providers");
const { constantTimeEquals } = require("../crypto/serverCrypto");

function createAuthRouter() {
  const express = require("express");
  const router = express.Router();

  // 0. POST /auth/otp/send
  router.post("/auth/otp/send", async (req, res, next) => {
    try {
      const channel = String(req.body?.channel || "EMAIL").toUpperCase();
      const destination = req.body?.destination || req.body?.phone || req.body?.email;
      const purpose = req.body?.purpose || "LOGIN";

      const { isKillSwitchActive } = require("../config/killSwitches");
      if (channel === "WHATSAPP" && (await isKillSwitchActive("KILL_WHATSAPP_OTP"))) {
        return res.status(503).json({
          type: "https://udhaar.app/errors/feature-disabled",
          title: "Service Unavailable",
          status: 503,
          detail: "WhatsApp OTP service is temporarily suspended.",
          error: {
            code: "ERR_FEATURE_DISABLED",
            message: "WhatsApp OTP service is temporarily suspended."
          }
        });
      }

      const flowId = `flow_otp_${Date.now()}`;
      return res.status(200).json({
        flowId,
        channel,
        sent: true,
        destinationMasked: destination
      });
    } catch (err) {
      next(err);
    }
  });

  // 1. POST /auth/signup/start
  router.post("/auth/signup/start", async (req, res, next) => {
    try {
      const phoneNumber = req.body?.phoneNumber || req.body?.phone;
      const { locale, botCheckToken } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const clientType = req.headers["x-client-platform"] === "android" ? "ANDROID" : "WEB";

      const result = await startSignup({
        phoneNumber,
        locale,
        botCheckToken,
        clientIp,
        clientType
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
      const { phoneNumber, locale, botCheckToken } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const clientType = req.headers["x-client-platform"] === "android" ? "ANDROID" : "WEB";

      const result = await startSignin({
        phoneNumber,
        locale,
        botCheckToken,
        clientIp,
        clientType
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
      const authRepo = getAuthRepository();
      const user = await authRepo.getUser(req.user.uid);
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

  // 9. DELETE /me/devices/:deviceId (Revoke device session with Step-Up verification)
  router.delete("/me/devices/:deviceId", requireAuth, requireStepUpAuth, async (req, res, next) => {
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

  // 10. POST /auth/step-up/start (Trigger fresh email code for high-assurance action)
  router.post("/auth/step-up/start", requireAuth, async (req, res, next) => {
    try {
      const { getAuthRepository, getSessionRepository } = require("../repositories");
      const { sendOtpChallenge } = require("../otp/otpService");
      const authRepo = getAuthRepository();
      const user = await authRepo.getUser(req.user.uid);
      if (!user || !user.email) {
        return sendProblemDetails(res, {
          status: 400,
          code: "ERR_NO_EMAIL_CONFIGURED",
          title: "Bad Request",
          detail: "A registered email address is required for step-up verification."
        });
      }

      const flowId = `stepup_${require("crypto").randomUUID()}`;
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const challenge = await sendOtpChallenge({
        flowId,
        destination: user.email,
        channel: "EMAIL",
        purpose: "STEP_UP",
        locale: user.locale || "en",
        clientIp
      });

      return res.status(200).json({ flowId, destination: challenge.destinationMasked, expiresAtMs: challenge.expiresAtMs });
    } catch (err) {
      next(err);
    }
  });

  // 11. POST /auth/step-up/verify (Complete step-up verification)
  router.post("/auth/step-up/verify", requireAuth, async (req, res, next) => {
    try {
      const { flowId, code } = req.body || {};
      const { verifyFlowOtp } = require("./authService");
      const { getSessionRepository } = require("../repositories");

      await verifyFlowOtp({ flowId, code, purpose: "STEP_UP" });

      const now = Date.now();
      const sessionRepo = getSessionRepository();
      if (req.user.sessionId) {
        await sessionRepo.updateSession(req.user.sessionId, { lastStepUpAtMs: now }, req.user.uid);
      }

      const stepUpToken = generateStepUpToken(req.user.uid);
      return res.status(200).json({
        verified: true,
        stepUpToken,
        expiresAtMs: now + 600000
      });
    } catch (err) {
      next(err);
    }
  });

  // 12. GET /webhooks/whatsapp (Meta Hub subscription verification)
  router.get("/webhooks/whatsapp", (req, res) => {
    const mode = req.query["hub.mode"];
    const token = req.query["hub.verify_token"];
    const challenge = req.query["hub.challenge"];
    const expectedToken = process.env.META_WEBHOOK_VERIFY_TOKEN || process.env.WHATSAPP_WEBHOOK_VERIFY_TOKEN || "udhaar_webhook_verify_token_prod";

    if (mode === "subscribe" && token && constantTimeEquals(token, expectedToken)) {
      return res.status(200).send(challenge);
    }
    return res.status(403).send("Forbidden");
  });

  // 13. POST /webhooks/whatsapp (Meta status delivery updates with raw body signature check)
  router.post("/webhooks/whatsapp", async (req, res) => {
    const provider = getWhatsAppProvider();
    const rawBody = req.rawBody || (typeof req.body === "string" ? req.body : JSON.stringify(req.body));
    if (provider.verifyWebhookSignature && !provider.verifyWebhookSignature(req.headers, rawBody)) {
      return res.status(401).send("Invalid signature");
    }

    if (provider.parseWebhookPayload) {
      const parsed = provider.parseWebhookPayload(req.body);
      if (parsed && parsed.messageId) {
        const { getOtpRepository } = require("../repositories");
        const otpRepo = getOtpRepository();
        await otpRepo.updateChallengeDeliveryStatus(
          parsed.messageId,
          parsed.status,
          parsed.timestamp,
          parsed.errors
        );
      }
    }

    return res.status(200).send("EVENT_RECEIVED");
  });

  // 14. GET /auth/otp/status (Delivery inspection & alternative channel recommendation)
  router.get("/auth/otp/status", async (req, res, next) => {
    try {
      const { flowId } = req.query;
      if (!flowId) {
        const err = new Error("flowId is required");
        err.code = "ERR_INVALID_PARAMETERS";
        err.status = 400;
        throw err;
      }
      const { getOtpRepository } = require("../repositories");
      const otpRepo = getOtpRepository();
      let targetChallenge = null;
      if (otpRepo.challenges && typeof otpRepo.challenges.values === "function") {
        for (const chal of otpRepo.challenges.values()) {
          if (chal.flowId === flowId) {
            targetChallenge = chal;
            break;
          }
        }
      } else if (otpRepo.db) {
        const snap = await otpRepo.db.collection("otpChallenges").where("flowId", "==", flowId).limit(1).get();
        if (!snap.empty) targetChallenge = snap.docs[0].data();
      }

      if (!targetChallenge) {
        const err = new Error("No verification challenge found for this flow.");
        err.code = "ERR_CHALLENGE_NOT_FOUND";
        err.status = 404;
        throw err;
      }

      const isFailed = targetChallenge.deliveryStatus === "FAILED";
      return res.status(200).json({
        flowId,
        channel: targetChallenge.channel,
        deliveryStatus: targetChallenge.deliveryStatus || "SENT",
        canFallback: isFailed || (Date.now() - targetChallenge.createdAtMs > 30000),
        suggestedAlternative: isFailed ? "SMS or Email" : null,
        message: isFailed
          ? "WhatsApp delivery failed. Request verification code via SMS or Email instead."
          : "Verification code sent.",
        allowedFallbackChannels: ["SMS", "EMAIL"]
      });
    } catch (err) {
      next(err);
    }
  });

  // 15. POST /auth/otp/fallback (Fallback dispatch via SMS or Email)
  router.post("/auth/otp/fallback", async (req, res, next) => {
    try {
      const { flowId, channel, destination, locale } = req.body || {};
      const clientIp = req.ip || req.headers["x-forwarded-for"] || "127.0.0.1";
      const { fallbackOtpChallenge } = require("./authService");

      const result = await fallbackOtpChallenge({
        flowId,
        channel,
        destination,
        locale,
        clientIp
      });

      return res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createAuthRouter
};
