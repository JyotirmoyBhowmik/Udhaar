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

  // Navigation Links
  TRUST_CENTER_URL: "/trust/",
  UPDATES_URL: "/updates/"
};

// CommonJS support for Node.js test runners
if (typeof module !== "undefined" && module.exports) {
  module.exports = SITE_CONFIG;
}

// Browser global
if (typeof window !== "undefined") {
  window.SITE_CONFIG = SITE_CONFIG;
}
