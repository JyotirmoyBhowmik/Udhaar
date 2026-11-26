/**
 * Udhaar Platform v2 Environment & Service Configuration (functions/src/config/index.js)
 */

const REGION = "asia-south1";

const ALLOWED_COUNTRY_CODES = Object.freeze(["+91", "+977"]); // India & Nepal

const OTP_LIMITS = Object.freeze({
  CODE_TTL_MINUTES: 10,
  RESEND_COOLDOWN_SECONDS: 30,
  MAX_HOURLY_PER_DESTINATION: 3,
  MAX_DAILY_PER_DESTINATION: 10,
  MAX_HOURLY_PER_IP: 20,
  MAX_FAILED_ATTEMPTS: 5,
  MAX_VERIFY_ATTEMPTS: 5,
  LOCKOUT_DURATION_MINUTES: 15,
  WHATSAPP_EXPIRY_MINUTES: 5,
  SMS_EXPIRY_MINUTES: 5,
  EMAIL_EXPIRY_MINUTES: 10,
  MAX_DAILY_GLOBAL_BUDGET: 5000,
  GLOBAL_DAILY_SEND_BUDGET: 5000
});

const SESSION_CONFIG = Object.freeze({
  COOKIE_NAME: "__session",
  COOKIE_MAX_AGE_SECONDS: 14 * 24 * 60 * 60, // 14 days
  STEP_UP_VALIDITY_MINUTES: 10,
  COOKIE_OPTIONS: Object.freeze({
    path: "/",
    httpOnly: true,
    secure: true,
    sameSite: "Strict"
  })
});

const QUOTAS = Object.freeze({
  MAX_RECORDS_PER_USER: 10000,
  MAX_ATTACHMENT_BYTES_PER_USER: 50 * 1024 * 1024 // 50 MB
});

module.exports = {
  REGION,
  ALLOWED_COUNTRY_CODES,
  OTP_LIMITS,
  SESSION_CONFIG,
  QUOTAS
};
