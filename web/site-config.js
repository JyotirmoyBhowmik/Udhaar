/**
 * Udhaar Web Site Configuration
 * Single source of truth for Google Play availability, testing opt-in links, and app metadata.
 */
const SITE_CONFIG = {
  // When false: shows "Join early access" (Play Console closed-testing link)
  // When true: shows "Get it on Google Play" (public store listing)
  PLAY_STORE_LIVE: false,

  // Public Play Store Listing URL
  PLAY_STORE_URL: "https://play.google.com/store/apps/details?id=com.jyotirmoyb.udhaar",

  // Play Console Closed-Testing Opt-In URL
  CLOSED_TESTING_URL: "https://play.google.com/apps/testing/com.jyotirmoyb.udhaar",

  // Early access fallback waitlist anchor
  EARLY_ACCESS_URL: "https://play.google.com/apps/testing/com.jyotirmoyb.udhaar",

  // Current production app version
  APP_VERSION: "21.0",

  // Support & Data Deletion Contact
  SUPPORT_EMAIL: "admin@jyotirmoyb.com",

  // Operational Tier: "spark" (100% free: Firebase Auth, Firestore rules, Hosting, Remote Config, client FCM)
  // or "blaze" (Cloud Functions v2, Cloud KMS, Secret Manager, Cloud Storage, paid telephony/email)
  UDHAAR_TIER: "spark",

  isSparkTier() {
    return (this.UDHAAR_TIER || "spark").toLowerCase() === "spark";
  },

  isBlazeTier() {
    return (this.UDHAAR_TIER || "").toLowerCase() === "blaze";
  },

  assertAllowedInTier(featureName) {
    if (this.isSparkTier()) {
      const blazeOnly = [
        "cloud_functions",
        "cloud_kms",
        "cloud_storage",
        "secret_manager",
        "whatsapp_cloud_api",
        "recaptcha_enterprise",
        "brevo_email",
        "paid_sms",
        "server_sync_hub"
      ];
      if (blazeOnly.includes(featureName)) {
        throw new Error(
          `[TierViolation] '${featureName}' is a Blaze-only paid feature and is disabled in Spark Mode. ` +
          `Spark Mode operates strictly with Firebase Auth and direct Firestore rules.`
        );
      }
    }
  },

  // Navigation Links
  TRUST_CENTER_URL: "/trust/",
  UPDATES_URL: "/updates/",
  WEB_LEDGER_URL: "/ledger/"
};

// CommonJS support for Node.js test runners
if (typeof module !== "undefined" && module.exports) {
  module.exports = SITE_CONFIG;
}

// Browser global
if (typeof window !== "undefined") {
  window.SITE_CONFIG = SITE_CONFIG;
}
