/**
 * Udhaar Web Tier Configuration
 *
 * In Spark Mode (default), all operations run serverless via Firebase Auth,
 * Cloud Firestore direct SDK calls (governed by firestore.rules), and Firebase Hosting.
 * No Cloud Functions or Cloud Storage are deployed or invoked on the free Spark plan.
 */
const BLAZE_FEATURES_ENABLED = false;

if (typeof module !== "undefined" && module.exports) {
  module.exports = { BLAZE_FEATURES_ENABLED };
}
if (typeof window !== "undefined") {
  window.BLAZE_FEATURES_ENABLED = BLAZE_FEATURES_ENABLED;
}
