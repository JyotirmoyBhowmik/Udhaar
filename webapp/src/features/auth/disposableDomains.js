/**
 * Disposable Email Domain Blocklist (webapp/src/features/auth/disposableDomains.js)
 * 
 * Filters known temporary / disposable email providers to prevent registration abuse.
 */

const DISPOSABLE_EMAIL_DOMAINS = new Set([
  "mailinator.com",
  "tempmail.com",
  "10minutemail.com",
  "guerrillamail.com",
  "trashmail.com",
  "yopmail.com",
  "sharklasers.com",
  "getairmail.com",
  "throwawaymail.com",
  "temp-mail.org",
  "dispostable.com",
  "fakeinbox.com",
  "mytrashmail.com",
  "maildrop.cc",
  "inboxkitten.com"
]);

/**
 * Checks if an email uses a disposable domain
 * @param {string} email
 * @returns {{ isDisposable: boolean, domain: string }}
 */
function checkDisposableEmail(email) {
  if (!email || typeof email !== "string" || !email.includes("@")) {
    return { isDisposable: false, domain: "" };
  }

  const domain = email.trim().toLowerCase().split("@")[1] || "";
  const isDisposable = DISPOSABLE_EMAIL_DOMAINS.has(domain);

  return {
    isDisposable,
    domain
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DISPOSABLE_EMAIL_DOMAINS,
    checkDisposableEmail
  };
}
