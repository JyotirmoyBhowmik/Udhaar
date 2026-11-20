const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const { I18N, t, formatCurrency, formatDate } = require("../../web/u/guest.js");
const { computeCodeHmac, verifyGuestPhoneOtp, setTestServerSecret } = require("../guestWebFlow");
const endpoints = require("../guestWebFlowEndpoints");

test("Part 2 — Upgrade the guest page (web/u/index.html & guest.js)", async (t) => {
  const htmlPath = path.resolve(__dirname, "../../web/u/index.html");
  const cssPath = path.resolve(__dirname, "../../web/u/guest.css");
  const jsPath = path.resolve(__dirname, "../../web/u/guest.js");
  const qrPath = path.resolve(__dirname, "../../web/assets/qrcode.min.js");
  const firebaseJsonPath = path.resolve(__dirname, "../../firebase.json");

  const htmlContent = fs.readFileSync(htmlPath, "utf8");
  const cssContent = fs.readFileSync(cssPath, "utf8");
  const jsContent = fs.readFileSync(jsPath, "utf8");
  const qrContent = fs.readFileSync(qrPath, "utf8");
  const firebaseJson = JSON.parse(fs.readFileSync(firebaseJsonPath, "utf8"));

  await t.test("1. Full Translation Table Completeness (5 Languages, 0 Missing, 0 Blank)", () => {
    const requiredLangs = ["en", "hi", "bn", "as", "ne"];
    for (const lang of requiredLangs) {
      assert.ok(I18N[lang], `Dictionary for ${lang} must exist`);
    }

    const enKeys = Object.keys(I18N.en);
    assert.ok(enKeys.length >= 60, `English dictionary must have at least 60 keys, found ${enKeys.length}`);

    for (const lang of ["hi", "bn", "as", "ne"]) {
      const missingKeys = enKeys.filter((k) => !I18N[lang][k]);
      assert.deepStrictEqual(missingKeys, [], `Language '${lang}' is missing keys: ${missingKeys.join(", ")}`);

      const blankKeys = enKeys.filter((k) => typeof I18N[lang][k] !== "string" || !I18N[lang][k].trim());
      assert.deepStrictEqual(blankKeys, [], `Language '${lang}' has blank keys: ${blankKeys.join(", ")}`);
    }
  });

  await t.test("2. Zero Untranslated English Leakage in Regional Dictionaries", () => {
    const allowListWords = new Set([
      "UDHAAR", "WEB", "UPI", "QR", "GPAY", "PHONEPE", "PAYTM", "BHIM", ".ICS", "WHATSAPP", "ID", "PIN", "CASH"
    ]);

    for (const lang of ["hi", "bn", "as", "ne"]) {
      const untranslatedKeys = [];
      for (const [key, enVal] of Object.entries(I18N.en)) {
        const regionalVal = I18N[lang][key];
        if (regionalVal.trim() === enVal.trim()) {
          // Check if it only contains allowlisted words/punctuation
          const cleaned = regionalVal.toUpperCase().replace(/[^A-Z0-9]/g, " ").trim();
          const words = cleaned.split(/\s+/).filter(Boolean);
          const allAllowed = words.every((w) => allowListWords.has(w));
          if (!allAllowed) {
            untranslatedKeys.push(`${key}: "${regionalVal}"`);
          }
        }
      }
      assert.deepStrictEqual(
        untranslatedKeys,
        [],
        `Language '${lang}' has identical untranslated strings to English: ${untranslatedKeys.join("; ")}`
      );
    }
  });

  await t.test("3. Loading & State Views: No Demo Data and Clear Next Steps", () => {
    // Skeleton loader must NEVER leak hardcoded demo data
    const loadingMatch = htmlContent.match(/<div\s+id=["']loading-view["'][\s\S]*?<\/div>/i);
    assert.ok(loadingMatch, "Must have loading-view container");
    const loadingSnippet = loadingMatch[0];
    assert.doesNotMatch(loadingSnippet, /Due in \d+ days/i, "Skeleton must not contain sample due text");
    assert.doesNotMatch(loadingSnippet, /Principal Amount/i, "Skeleton must not contain 'Principal Amount'");
    assert.doesNotMatch(loadingSnippet, /Installments:\s*0 of 0 Paid/i, "Skeleton must not contain dummy installments");

    // Friendly state keys must provide actionable instructions
    const stateActions = [
      "state_expired_action",
      "state_revoked_action",
      "state_wrong_code_action",
      "state_too_many_tries_action",
      "state_already_confirmed_action",
      "state_disputed_action",
      "state_server_error_action"
    ];
    for (const actionKey of stateActions) {
      assert.ok(I18N.en[actionKey], `Must define ${actionKey}`);
      assert.ok(I18N.en[actionKey].includes("{name}") || I18N.en[actionKey].includes("hour") || I18N.en[actionKey].includes("receipt") || I18N.en[actionKey].includes("moments"));
    }
  });

  await t.test("4. Honest Code Step: HMAC, 15-Minute Expiration, Single-Use, and Code Checked Badge", async () => {
    setTestServerSecret("injected-i18n-test-secret-2026");
    const testCode = "729401";
    const hmac = computeCodeHmac(testCode);
    assert.strictEqual(typeof hmac, "string");
    assert.strictEqual(hmac.length, 64, "Code HMAC must be a 64-char hex string");

    const now = 1762260000000;
    const linkRefMock = {
      updates: [],
      async update(d) { this.updates.push(d); }
    };

    // 1. Valid code before 15-min expiry
    const validLink = {
      whatsappOtpHmac: hmac,
      whatsappOtpExpiresAtMillis: now + 10 * 60 * 1000,
      whatsappOtpUsed: false,
      counterpartyPhoneMasked: "98*** ***10",
      attemptCounters: { wrongOtpCount: 0 }
    };
    const resValid = await verifyGuestPhoneOtp(null, linkRefMock, validLink, testCode, now);
    assert.strictEqual(resValid.verified, true);
    assert.strictEqual(resValid.whatsappVerified, true);
    assert.strictEqual(linkRefMock.updates[0].whatsappOtpUsed, true, "Code must be marked as used");

    // 2. Expired code rejected
    const expiredLink = {
      whatsappOtpHmac: hmac,
      whatsappOtpExpiresAtMillis: now - 1000, // expired 1s ago
      whatsappOtpUsed: false,
    };
    const resExpired = await verifyGuestPhoneOtp(null, linkRefMock, expiredLink, testCode, now);
    assert.strictEqual(resExpired.verified, false);
    assert.strictEqual(resExpired.status, 403);
    assert.match(resExpired.error, /expired/i);

    // 3. Already used code rejected
    const usedLink = {
      whatsappOtpHmac: hmac,
      whatsappOtpExpiresAtMillis: now + 5 * 60 * 1000,
      whatsappOtpUsed: true,
    };
    const resUsed = await verifyGuestPhoneOtp(null, linkRefMock, usedLink, testCode, now);
    assert.strictEqual(resUsed.verified, false);
    assert.strictEqual(resUsed.status, 403);
    assert.match(resUsed.error, /already been used/i);

    // 4. renewable endpoint exists
    assert.ok(typeof endpoints.renewGuestCode === "function", "Callable function renewGuestCode must exist");

    // 5. Honest badge is 'Code checked'
    assert.strictEqual(I18N.en.badge_code_checked, "Code checked");
    assert.match(I18N.en.screen2_title, /Enter the code \{name\} sent you/i);
  });

  await t.test("5. Security Headers, Strict CSP, and Fragment-Only Secret Token", () => {
    // Separate files
    assert.match(htmlContent, /<link\s+rel=["']stylesheet["']\s+href=["']guest\.css["']/i);
    assert.match(htmlContent, /<script\s+src=["']guest\.js["']/i);
    assert.doesNotMatch(htmlContent, /<style>[\s\S]*<\/style>/i, "index.html must not contain inline style tags");

    // CSP
    const uHeaders = firebaseJson.hosting.headers.find((h) => h.source === "/u/**");
    assert.ok(uHeaders, "firebase.json headers for /u/** must exist");
    const csp = uHeaders.headers.find((h) => h.key === "Content-Security-Policy");
    assert.ok(csp, "CSP header must be present");
    assert.doesNotMatch(csp.value, /'unsafe-inline'/, "CSP must not contain 'unsafe-inline'");

    // HSTS
    const hsts = uHeaders.headers.find((h) => h.key === "Strict-Transport-Security");
    assert.ok(hsts, "Strict-Transport-Security must be configured");

    // Secret read from window.location.hash
    assert.match(jsContent, /window\.location\.hash/i);
    assert.doesNotMatch(jsContent, /console\.log\([^)]*urlSecret[^)]*\)/i, "Secret must never be logged");
  });

  await t.test("6. Performance & Total Page Weight under 250 KB", () => {
    const htmlBytes = Buffer.byteLength(htmlContent, "utf8");
    const cssBytes = Buffer.byteLength(cssContent, "utf8");
    const jsBytes = Buffer.byteLength(jsContent, "utf8");
    const qrBytes = Buffer.byteLength(qrContent, "utf8");
    const totalBytes = htmlBytes + cssBytes + jsBytes + qrBytes;

    const maxLimitBytes = 250 * 1024; // 250 KB
    assert.ok(
      totalBytes < maxLimitBytes,
      `Total page weight (${(totalBytes / 1024).toFixed(1)} KB) must be under 250 KB`
    );
  });
});
