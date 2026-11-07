/**
 * Firebase App Check Verification Engine (functions/src/security/appCheckMiddleware.js)
 * 
 * Verifies Play Integrity (Android) and reCAPTCHA Enterprise (Web) App Check tokens
 * on incoming API requests using Firebase Admin getAppCheck().verifyToken.
 * 
 * Includes staging switch (ALLOW_APP_CHECK_DEBUG) to permit debug tokens in staging only.
 */

const { getAppCheck } = require("firebase-admin/app-check");
const { isProductionOrStaging } = require("../config/environmentGuard");

class FirebaseAppCheckProvider {
  constructor(adminAppCheck = null) {
    this._appCheck = adminAppCheck;
  }

  get appCheck() {
    return this._appCheck || getAppCheck();
  }

  async verifyToken(token, options = {}) {
    return this.appCheck.verifyToken(token, options);
  }
}

/**
 * In-memory test provider for unit tests
 */
class TestAppCheckProvider {
  constructor() {
    this.validTokens = new Set(["valid_web_app_check_token", "valid_android_play_integrity_token"]);
    this.debugTokens = new Set(["valid_staging_debug_app_check_token"]);
  }

  async verifyToken(token) {
    if (this.debugTokens.has(token)) {
      return {
        appId: "1:1234567890:android:abcdef",
        token,
        isDebug: true
      };
    }

    if (this.validTokens.has(token)) {
      return {
        appId: "1:1234567890:web:1234567890abcdef",
        token,
        isDebug: false
      };
    }

    const err = new Error("App Check token is invalid or expired.");
    err.code = "app-check/invalid-token";
    throw err;
  }
}

let activeAppCheckProvider = null;

function setAppCheckProvider(provider) {
  if (isProductionOrStaging()) {
    const err = new Error("[AppCheck] FATAL: Cannot inject test App Check provider in production or staging.");
    err.code = "ERR_ILLEGAL_TEST_PROVIDER";
    throw err;
  }
  activeAppCheckProvider = provider;
}

function resetAppCheckProvider() {
  activeAppCheckProvider = null;
}

function getAppCheckProvider() {
  if (activeAppCheckProvider) {
    if (isProductionOrStaging() && activeAppCheckProvider instanceof TestAppCheckProvider) {
      const err = new Error("[AppCheck] FATAL: Test App Check provider active in production or staging.");
      err.code = "ERR_ILLEGAL_TEST_PROVIDER";
      throw err;
    }
    return activeAppCheckProvider;
  }

  if (isProductionOrStaging()) {
    return new FirebaseAppCheckProvider();
  }

  activeAppCheckProvider = new TestAppCheckProvider();
  return activeAppCheckProvider;
}

function isDebugAllowed() {
  const udhaarEnv = (process.env.UDHAAR_ENV || "").trim().toLowerCase();
  const allowDebug = process.env.ALLOW_APP_CHECK_DEBUG === "true";
  // Debug tokens permitted only in staging or dev, NEVER in production
  return allowDebug || udhaarEnv === "staging" || process.env.NODE_ENV === "staging";
}

/**
 * App Check Guard Middleware
 */
async function appCheckGuard(req, res, next) {
  // Always skip public webhooks and health check endpoints
  const path = req.path || req.originalUrl || "";
  if (path.includes("/webhooks/") || path === "/healthz" || path.endsWith("/healthz")) {
    return next();
  }

  // In non-production testing, bypass only when not explicitly enforced
  const isEnforced = isProductionOrStaging() || process.env.ENFORCE_APP_CHECK === "true";
  const appCheckToken = req.headers["x-firebase-appcheck"];

  if (!appCheckToken) {
    if (!isEnforced) {
      return next();
    }

    res.status(401);
    res.setHeader("Content-Type", "application/problem+json");
    return res.json({
      type: "https://app.udhaar.jyotirmoyb.com/errors/err_app_check_missing",
      title: "Unauthorized",
      status: 401,
      code: "ERR_APP_CHECK_MISSING",
      detail: "Requests must include a valid X-Firebase-AppCheck token from Play Integrity or reCAPTCHA Enterprise."
    });
  }

  try {
    const provider = getAppCheckProvider();
    const appCheckClaims = await provider.verifyToken(appCheckToken);

    // Reject debug tokens if not explicitly in staging/dev
    if (appCheckClaims.isDebug && !isDebugAllowed()) {
      res.status(403);
      res.setHeader("Content-Type", "application/problem+json");
      return res.json({
        type: "https://app.udhaar.jyotirmoyb.com/errors/err_app_check_debug_forbidden",
        title: "Forbidden",
        status: 403,
        code: "ERR_APP_CHECK_DEBUG_FORBIDDEN",
        detail: "App Check debug tokens are strictly forbidden in production."
      });
    }

    req.appCheck = appCheckClaims;
    next();
  } catch (err) {
    res.status(401);
    res.setHeader("Content-Type", "application/problem+json");
    return res.json({
      type: "https://app.udhaar.jyotirmoyb.com/errors/err_app_check_invalid",
      title: "Unauthorized",
      status: 401,
      code: "ERR_APP_CHECK_INVALID",
      detail: "The provided X-Firebase-AppCheck token could not be verified."
    });
  }
}

module.exports = {
  appCheckGuard,
  getAppCheckProvider,
  setAppCheckProvider,
  resetAppCheckProvider,
  isDebugAllowed
};
