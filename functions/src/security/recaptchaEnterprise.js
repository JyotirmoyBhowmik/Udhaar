/**
 * Google Cloud reCAPTCHA Enterprise Server-Side Assessment Engine
 * (functions/src/security/recaptchaEnterprise.js)
 * 
 * Verifies bot protection assessments on OTP code requests from the Web client.
 * Enforces risk score threshold (default >= 0.5) to block automated credential abuse.
 */

const { isProductionOrStaging } = require("../config/environmentGuard");

const RECAPTCHA_MIN_SCORE = 0.5;

class CloudRecaptchaProvider {
  constructor(options = {}) {
    this.projectId = options.projectId || process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT || "udhaar-prod";
    this.siteKey = options.siteKey || process.env.RECAPTCHA_ENTERPRISE_SITE_KEY;
  }

  async createAssessment({ token, expectedAction, clientIp }) {
    // In production, instantiate RecaptchaEnterpriseServiceClient
    const { RecaptchaEnterpriseServiceClient } = require("@google-cloud/recaptcha-enterprise");
    const client = new RecaptchaEnterpriseServiceClient();
    const projectPath = client.projectPath(this.projectId);

    const request = {
      parent: projectPath,
      assessment: {
        event: {
          token,
          siteKey: this.siteKey,
          expectedAction,
          userIpAddress: clientIp
        }
      }
    };

    const [response] = await client.createAssessment(request);

    if (!response.tokenProperties || !response.tokenProperties.valid) {
      return {
        valid: false,
        score: 0.0,
        reasons: response.tokenProperties?.invalidReason || "INVALID_TOKEN"
      };
    }

    return {
      valid: true,
      score: response.riskAnalysis?.score || 0.0,
      reasons: response.riskAnalysis?.reasons || [],
      action: response.tokenProperties.action
    };
  }
}

/**
 * In-memory test provider for unit tests
 */
class TestRecaptchaProvider {
  constructor() {
    this.customScores = Object.create(null);
  }

  setTokenScore(token, score, valid = true) {
    this.customScores[token] = { score, valid };
  }

  async createAssessment({ token, expectedAction, clientIp }) {
    if (this.customScores[token]) {
      return this.customScores[token];
    }

    if (token === "valid_high_score_human_token" || token === "test_bot_check_token") {
      return { valid: true, score: 0.9, action: expectedAction, reasons: [] };
    }

    if (token === "low_score_bot_token") {
      return { valid: true, score: 0.1, action: expectedAction, reasons: ["AUTOMATED_AGENT"] };
    }

    if (token === "invalid_tampered_token") {
      return { valid: false, score: 0.0, reasons: ["EXPIRED_OR_INVALID"] };
    }

    // Default test token
    return { valid: true, score: 0.9, action: expectedAction, reasons: [] };
  }
}

let activeRecaptchaProvider = null;

function setRecaptchaProvider(provider) {
  if (isProductionOrStaging()) {
    const err = new Error("[reCAPTCHA] FATAL: Cannot inject test reCAPTCHA provider in production or staging.");
    err.code = "ERR_ILLEGAL_TEST_PROVIDER";
    throw err;
  }
  activeRecaptchaProvider = provider;
}

function resetRecaptchaProvider() {
  activeRecaptchaProvider = null;
}

function getRecaptchaProvider() {
  if (activeRecaptchaProvider) {
    if (isProductionOrStaging() && activeRecaptchaProvider instanceof TestRecaptchaProvider) {
      const err = new Error("[reCAPTCHA] FATAL: Test reCAPTCHA provider active in production or staging.");
      err.code = "ERR_ILLEGAL_TEST_PROVIDER";
      throw err;
    }
    return activeRecaptchaProvider;
  }

  if (isProductionOrStaging()) {
    return new CloudRecaptchaProvider();
  }

  activeRecaptchaProvider = new TestRecaptchaProvider();
  return activeRecaptchaProvider;
}

/**
 * Assesses bot risk on code send requests
 * @param {Object} params
 * @param {string} params.token - Client reCAPTCHA token
 * @param {string} params.expectedAction - Expected action identifier (e.g. "signup_start")
 * @param {string} [params.clientIp] - Client IP address
 * @param {string} [params.clientType] - "WEB" or "ANDROID"
 */
async function verifyWebBotAssessment({ token, expectedAction, clientIp, clientType = "WEB" }) {
  // Play Integrity protects Android directly via App Check; reCAPTCHA applies to Web
  if (clientType !== "WEB") {
    return { verified: true, bypass: true, clientType };
  }

  // If token is missing from web request
  if (!token || typeof token !== "string" || token.trim().length === 0) {
    const err = new Error("Bot detection verification (reCAPTCHA Enterprise) token is required for web requests.");
    err.code = "ERR_BOT_CHECK_MISSING";
    err.status = 403;
    throw err;
  }

  const provider = getRecaptchaProvider();
  const assessment = await provider.createAssessment({ token: token.trim(), expectedAction, clientIp });

  if (!assessment.valid) {
    const err = new Error("Bot detection assessment failed: token is invalid or expired.");
    err.code = "ERR_BOT_CHECK_FAILED";
    err.status = 403;
    throw err;
  }

  if (assessment.score < RECAPTCHA_MIN_SCORE) {
    const err = new Error(`Bot detection assessment failed: risk score (${assessment.score}) below required threshold (${RECAPTCHA_MIN_SCORE}).`);
    err.code = "ERR_BOT_CHECK_FAILED";
    err.status = 403;
    err.score = assessment.score;
    throw err;
  }

  return { verified: true, score: assessment.score };
}

module.exports = {
  verifyWebBotAssessment,
  getRecaptchaProvider,
  setRecaptchaProvider,
  resetRecaptchaProvider,
  RECAPTCHA_MIN_SCORE
};
