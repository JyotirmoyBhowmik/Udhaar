/**
 * Password Strength & Common Password Validator (webapp/src/features/auth/passwordValidator.js)
 * 
 * Enforces:
 * - Minimum 10 characters length
 * - Strength scoring (0 to 4)
 * - Blocklist of common/predictable passwords
 */

const COMMON_PASSWORDS = new Set([
  "password123",
  "password1234",
  "password12345",
  "1234567890",
  "12345678901",
  "qwerty1234",
  "qwerty12345",
  "admin12345",
  "letmein123",
  "welcome123",
  "iloveyou123",
  "udhaar1234",
  "udhaar12345",
  "monkey1234",
  "dragon1234",
  "master1234",
  "football123",
  "shadow1234",
  "superman123",
  "trustnoone1"
]);

/**
 * Validate password requirements and compute strength score
 * @param {string} password
 * @returns {{ valid: boolean, score: number, label: string, errors: string[] }}
 */
function validatePassword(password) {
  const errors = [];
  const pwd = String(password || "");

  if (pwd.length < 10) {
    errors.push("Password must be at least 10 characters long.");
  }

  const normalized = pwd.toLowerCase().trim();
  if (COMMON_PASSWORDS.has(normalized)) {
    errors.push("This password is too common or easily guessable. Please choose a unique password.");
  }

  // Calculate score (0-4)
  let score = 0;
  if (pwd.length >= 10) score += 1;
  if (pwd.length >= 14) score += 1;
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score += 1;
  if (/[0-9]/.test(pwd)) score += 1;
  if (/[^a-zA-Z0-9]/.test(pwd)) score += 1;

  // Bound score 0 to 4
  const boundedScore = Math.min(4, Math.max(0, score > 1 ? score - 1 : score));

  let label = "Weak";
  if (boundedScore === 2) label = "Fair";
  if (boundedScore === 3) label = "Good";
  if (boundedScore === 4) label = "Strong";

  return {
    valid: errors.length === 0,
    score: boundedScore,
    label,
    errors
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    validatePassword,
    COMMON_PASSWORDS
  };
}
