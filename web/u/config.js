/**
 * Udhaar Web Tier Configuration
 *
 * In Spark Mode (default), all operations run serverless via Firebase Auth,
 * Cloud Firestore direct SDK calls (governed by firestore.rules), and Firebase Hosting.
 * No Cloud Functions or Cloud Storage are deployed or invoked on the free Spark plan.
 */
const BLAZE_FEATURES_ENABLED = false;

// App Check: reCAPTCHA v3 / Enterprise free-tier provider site key
// Register site in Firebase Console -> App Check -> Apps -> Web -> reCAPTCHA v3
const RECAPTCHA_SITE_KEY = (typeof window !== "undefined" && window.RECAPTCHA_SITE_KEY)
  ? window.RECAPTCHA_SITE_KEY
  : "6Ld_placeholder_site_key_spark_free_tier";

if (typeof module !== "undefined" && module.exports) {
  module.exports = { BLAZE_FEATURES_ENABLED, RECAPTCHA_SITE_KEY };
}
if (typeof window !== "undefined") {
  window.BLAZE_FEATURES_ENABLED = BLAZE_FEATURES_ENABLED;
  window.RECAPTCHA_SITE_KEY = RECAPTCHA_SITE_KEY;
}
