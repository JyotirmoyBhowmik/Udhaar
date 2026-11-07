/**
 * Disposable & Temporary Email Domain Blocklist (functions/src/auth/disposableEmails.js)
 * Prevents automated account farming using burner inbox services.
 */

const DISPOSABLE_DOMAINS = new Set([
  "mailinator.com",
  "tempmail.com",
  "10minutemail.com",
  "guerrillamail.com",
  "sharklasers.com",
  "trashmail.com",
  "throwawaymail.com",
  "yopmail.com",
  "getairmail.com",
  "dispostable.com",
  "fakemailgenerator.com",
  "maildrop.cc",
  "inboxkitten.com",
  "burnermail.io",
  "temp-mail.org",
  "tempmailo.com",
  "mohmal.com",
  "crazymailing.com",
  "mytemp.email",
  "emailondeck.com",
  "generator.email",
  "zillamail.com",
  "clipmail.eu"
]);

function isDisposableEmail(email) {
  if (!email || typeof email !== "string") return false;
  const parts = email.trim().toLowerCase().split("@");
  if (parts.length !== 2) return true;
  const domain = parts[1];
  return DISPOSABLE_DOMAINS.has(domain);
}

module.exports = {
  DISPOSABLE_DOMAINS,
  isDisposableEmail
};
