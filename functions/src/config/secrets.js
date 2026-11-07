/**
 * Secret Manager Configuration & Runtime Accessor (functions/src/config/secrets.js)
 * 
 * Defines and binds Cloud Secret Manager secrets for Cloud Functions v2.
 * Strictly fails closed in production/staging when required secrets are missing or fallback values are detected.
 * Provides an explicit interface to inject test secret providers during unit/integration tests.
 */

const { defineSecret } = require("firebase-functions/params");
const { isProductionOrStaging } = require("./environmentGuard");

// Cloud Functions v2 Secret Definitions
const udhaarServerSecret = defineSecret("UDHAAR_SERVER_SECRET");
const udhaarPhonePepper = defineSecret("UDHAAR_PHONE_PEPPER");
const udhaarEmailPepper = defineSecret("UDHAAR_EMAIL_PEPPER");
const whatsappAppSecret = defineSecret("META_APP_SECRET");
const whatsappWebhookVerifyToken = defineSecret("WHATSAPP_WEBHOOK_VERIFY_TOKEN");
const whatsappApiToken = defineSecret("WHATSAPP_API_TOKEN");
const resendApiKey = defineSecret("RESEND_API_KEY");

const ALL_SECRETS = [
  udhaarServerSecret,
  udhaarPhonePepper,
  udhaarEmailPepper,
  whatsappAppSecret,
  whatsappWebhookVerifyToken,
  whatsappApiToken,
  resendApiKey
];

let injectedSecretsProvider = null;

/**
 * Injects a test secrets provider for unit/integration testing.
 * Fails closed if called in production or staging.
 * 
 * @param {Object} provider
 * @param {Function} provider.getSecret - (name: string) => string
 */
function setSecretsProvider(provider) {
  if (isProductionOrStaging()) {
    const err = new Error("[Secrets] FATAL: Cannot inject mock secrets provider in production or staging.");
    err.code = "ERR_ILLEGAL_TEST_PROVIDER";
    throw err;
  }
  injectedSecretsProvider = provider;
}

/**
 * Resets injected test secrets provider.
 */
function resetSecretsProvider() {
  injectedSecretsProvider = null;
}

/**
 * Retrieves a named secret from Secret Manager or runtime environment.
 * 
 * @param {string} name - Name of the secret.
 * @returns {string} The secret string value.
 */
function getSecret(name) {
  // 1. Injected test provider (test mode only)
  if (injectedSecretsProvider && typeof injectedSecretsProvider.getSecret === "function") {
    const val = injectedSecretsProvider.getSecret(name);
    if (val !== undefined && val !== null) {
      return String(val);
    }
  }

  // 2. Direct process.env check (populated by Secret Manager in Cloud Functions runtime or deployment environment)
  const envVal = process.env[name];
  if (envVal && typeof envVal === "string" && envVal.trim().length > 0) {
    const trimmed = envVal.trim();
    if (isProductionOrStaging() && (trimmed.includes("fallback") || trimmed.includes("test_secret"))) {
      const err = new Error(`[Secrets] FATAL: Insecure fallback value found in environment variable '${name}' in production.`);
      err.code = "ERR_INSECURE_SECRET";
      throw err;
    }
    return trimmed;
  }

  // 3. Cloud Functions v2 defineSecret accessor
  const secretMap = {
    UDHAAR_SERVER_SECRET: udhaarServerSecret,
    UDHAAR_PHONE_PEPPER: udhaarPhonePepper,
    UDHAAR_EMAIL_PEPPER: udhaarEmailPepper,
    META_APP_SECRET: whatsappAppSecret,
    WHATSAPP_WEBHOOK_VERIFY_TOKEN: whatsappWebhookVerifyToken,
    WHATSAPP_API_TOKEN: whatsappApiToken,
    RESEND_API_KEY: resendApiKey
  };

  const secretObj = secretMap[name];
  if (secretObj) {
    try {
      const val = secretObj.value();
      if (val && typeof val === "string" && val.trim().length > 0) {
        return val.trim();
      }
    } catch (_) {
      // secretObj.value() throws outside Cloud Functions runtime when secrets are not loaded
    }
  }

  // 4. In Production or Staging, fail closed immediately
  if (isProductionOrStaging()) {
    const err = new Error(`[Secrets] FATAL: Secret '${name}' is not configured in Google Cloud Secret Manager.`);
    err.code = "ERR_MISSING_SECRET";
    throw err;
  }

  // 5. In test/development mode, provide deterministic test secrets if none were set
  if (name === "UDHAAR_PHONE_PEPPER" || name === "UDHAAR_EMAIL_PEPPER") {
    return getSecret("UDHAAR_SERVER_SECRET");
  }

  return "test_environment_secret_32_bytes_len!";
}

module.exports = {
  udhaarServerSecret,
  udhaarPhonePepper,
  udhaarEmailPepper,
  whatsappAppSecret,
  whatsappWebhookVerifyToken,
  whatsappApiToken,
  resendApiKey,
  ALL_SECRETS,
  getSecret,
  setSecretsProvider,
  resetSecretsProvider
};
