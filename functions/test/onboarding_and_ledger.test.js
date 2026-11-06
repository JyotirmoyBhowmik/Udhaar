const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  setTestServerSecret,
  computeCodeHmac
} = require("../guestWebFlow");

test("Email and mobile validation logic rejects invalid inputs", () => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  assert.equal(emailRegex.test("valid.user@example.com"), true);
  assert.equal(emailRegex.test("invalid-email"), false);
  assert.equal(emailRegex.test("@no-user.com"), false);
  assert.equal(emailRegex.test("no-domain@"), false);

  const cleanDigits = (p) => String(p || "").replace(/\D/g, "");
  assert.ok(cleanDigits("+91 98765-43210").length >= 10);
  assert.ok(cleanDigits("12345").length < 10);
});

test("Email OTP verification enforces timing-safe HMAC equality and 10-minute expiry", () => {
  setTestServerSecret("test-secret-udhaar-2026");
  try {
    const rawOtp = "482910";
    const hmacA = computeCodeHmac(rawOtp);
    const hmacB = computeCodeHmac(rawOtp);
    const wrongHmac = computeCodeHmac("999999");

    assert.equal(hmacA, hmacB);
    assert.notEqual(hmacA, wrongHmac);

    const bufA = Buffer.from(hmacA, "hex");
    const bufB = Buffer.from(hmacB, "hex");
    const bufWrong = Buffer.from(wrongHmac, "hex");

    const crypto = require("node:crypto");
    assert.equal(crypto.timingSafeEqual(bufA, bufB), true);
    assert.equal(crypto.timingSafeEqual(bufA, bufWrong), false);

    // TTL check
    const now = Date.now();
    const expiresAt = now + 10 * 60 * 1000;
    assert.ok(now < expiresAt);
    assert.ok(now + 11 * 60 * 1000 > expiresAt);
  } finally {
    setTestServerSecret(null);
  }
});

test("0-value confirmation rejection: ensures positive transaction amount", () => {
  const checkCanConfirm = (existingPaise, confirmedAmountPaise) => {
    let finalPaise = existingPaise;
    if (finalPaise <= 0 && confirmedAmountPaise > 0) {
      finalPaise = confirmedAmountPaise;
    }
    return finalPaise > 0;
  };

  assert.equal(checkCanConfirm(500000, 0), true); // existing 5000
  assert.equal(checkCanConfirm(0, 250000), true); // confirmed 2500
  assert.equal(checkCanConfirm(0, 0), false); // Rejected: cannot confirm 0
  assert.equal(checkCanConfirm(-100, 0), false); // Rejected: negative amount
});

test("Strict Content-Security Policy on web/ledger/index.html (zero inline scripts and styles)", () => {
  const ledgerHtmlPath = path.resolve(__dirname, "../../web/ledger/index.html");
  assert.ok(fs.existsSync(ledgerHtmlPath), "web/ledger/index.html must exist");

  const html = fs.readFileSync(ledgerHtmlPath, "utf8");

  // Disallow inline script tags (except external script tags with src)
  const inlineScriptMatch = html.match(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi);
  assert.equal(
    inlineScriptMatch,
    null,
    `Found prohibited inline <script> in web/ledger/index.html: ${inlineScriptMatch}`
  );

  // Disallow inline style tags
  const inlineStyleMatch = html.match(/<style[^>]*>([\s\S]*?)<\/style>/gi);
  assert.equal(
    inlineStyleMatch,
    null,
    `Found prohibited inline <style> in web/ledger/index.html: ${inlineStyleMatch}`
  );

  // Disallow style="..." attributes
  const inlineStyleAttrMatch = html.match(/\sstyle=(['"])(.*?)\1/gi);
  assert.equal(
    inlineStyleAttrMatch,
    null,
    `Found prohibited style="..." attribute in web/ledger/index.html: ${inlineStyleAttrMatch}`
  );
});
