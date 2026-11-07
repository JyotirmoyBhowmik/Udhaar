/**
 * Production & Staging Environment Guard (functions/src/config/environmentGuard.js)
 * 
 * Strict fail-closed guard preventing simulated, fake, or in-memory components
 * from ever executing in production or staging environments.
 */

const FALLBACK_SECRET_VALUE = "udhaar_fallback_kek_secret_32_bytes_long!";

function isProductionOrStaging() {
  const udhaarEnv = (process.env.UDHAAR_ENV || "").trim().toLowerCase();
  const nodeEnv = (process.env.NODE_ENV || "").trim().toLowerCase();
  return udhaarEnv === "production" || udhaarEnv === "staging" ||
         nodeEnv === "production" || nodeEnv === "staging";
}

/**
 * Validates runtime environment configuration against production safety invariants.
 * Throws a fatal Error if any insecure, fallback, fake, or in-memory component is active.
 * 
 * @param {Object} [options]
 * @param {boolean} [options.useInMemoryStore] - Whether in-memory persistence is requested.
 * @param {string} [options.providerMode] - Active provider adapter name.
 * @param {string} [options.secretKey] - Active server secret key.
 */
function enforceProductionEnvironmentGuards(options = {}) {
  if (!isProductionOrStaging()) {
    return { ok: true, environment: process.env.NODE_ENV || "development" };
  }

  const currentEnv = process.env.UDHAAR_ENV || process.env.NODE_ENV;

  // 1. In-memory store prohibition
  if (options.useInMemoryStore === true) {
    throw new Error(
      `[EnvironmentGuard] FATAL: In-memory store requested while running in ${currentEnv}. ` +
      `Ephemeral Map storage is strictly forbidden in production and staging environments. Real Firestore persistence is required.`
    );
  }

  // 2. Fake / Mock provider prohibition
  if (
    process.env.USE_FAKE_PROVIDERS === "true" ||
    options.providerMode === "fake" ||
    process.env.PROVIDER_MODE === "fake"
  ) {
    throw new Error(
      `[EnvironmentGuard] FATAL: Fake provider mode enabled in ${currentEnv}. ` +
      `Telephony and email providers must use verified production adapters.`
    );
  }

  // 3. Fallback secret prohibition
  const activeSecret = options.secretKey || process.env.UDHAAR_SERVER_SECRET;
  if (!activeSecret || activeSecret === FALLBACK_SECRET_VALUE || activeSecret.includes("fallback") || activeSecret.includes("test_secret")) {
    throw new Error(
      `[EnvironmentGuard] FATAL: Insecure fallback or test secret detected in ${currentEnv}. ` +
      `Production requires cryptographic secrets provisioned through Google Cloud Secret Manager.`
    );
  }

  // 4. Test key / bypass prohibition
  if (process.env.ALLOW_TEST_KEYS === "true" || process.env.BYPASS_APP_CHECK === "true") {
    throw new Error(
      `[EnvironmentGuard] FATAL: Test key bypass flags (ALLOW_TEST_KEYS/BYPASS_APP_CHECK) cannot be enabled in ${currentEnv}.`
    );
  }

  // 5. Cloud KMS provider validation
  if (options.kmsProvider && (options.kmsProvider.constructor.name.includes("Test") || options.isTestKms === true)) {
    throw new Error(
      `[EnvironmentGuard] FATAL: Test KMS provider detected in ${currentEnv}. ` +
      `Production requires hardware-backed Master KEK via @google-cloud/kms in region asia-south1.`
    );
  }

  return { ok: true, environment: currentEnv };
}

module.exports = {
  isProductionOrStaging,
  enforceProductionEnvironmentGuards,
  FALLBACK_SECRET_VALUE
};
