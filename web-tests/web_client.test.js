const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Guest Web Client UI, Security Headers & Zero-Cost WhatsApp OTP Verification", async (t) => {
  const htmlPath = path.resolve(__dirname, "../web/u/index.html");
  const cssPath = path.resolve(__dirname, "../web/u/guest.css");
  const jsPath = path.resolve(__dirname, "../web/u/guest.js");
  const firebaseJsonPath = path.resolve(__dirname, "../firebase.json");

  const htmlContent = fs.readFileSync(htmlPath, "utf8");
  const cssContent = fs.readFileSync(cssPath, "utf8");
  const jsContent = fs.readFileSync(jsPath, "utf8");
  const firebaseJson = JSON.parse(fs.readFileSync(firebaseJsonPath, "utf8"));
  const bundleContent = htmlContent + "\n" + cssContent + "\n" + jsContent;

  await t.test("1. Mobile-First (360px viewport ready) & Responsive Meta", () => {
    assert.match(htmlContent, /<meta\s+name=["']viewport["']\s+content=["'][^"']*width=device-width[^"']*["']/i, "Must have standard mobile viewport");
    assert.match(cssContent, /max-width:\s*480px/i, "Container max-width must gracefully scale down to 360px mobile view");
    assert.match(cssContent, /min-height:\s*48px/i, "Touch targets must satisfy minimum 48px accessibility guideline");
  });

  await t.test("2. Zero-Cost WhatsApp OTP Verification Flow", () => {
    assert.match(htmlContent, /WhatsApp Verification/i, "Must include WhatsApp verification banner and sheet");
    assert.match(htmlContent, /otp-box/i, "Must render 6-digit verification code input boxes");
    assert.match(htmlContent, /btn-ask-wa/i, "Must include one-tap 'Ask on WhatsApp' link via wa.me");
    assert.match(jsContent, /wa\.me/i, "Must use standard zero-cost WhatsApp universal link");
    assert.match(htmlContent, /btn-report-not-me/i, "Must have 'This is not me' reporting action");
  });

  await t.test("3. Multi-Language Support (5 Languages)", () => {
    const requiredLanguages = ["en", "hi", "bn", "as", "ne"];
    for (const lang of requiredLanguages) {
      assert.match(jsContent, new RegExp(`(?:["']${lang}["']|\\b${lang}\\b)\\s*:\\s*\\{`), `Must include dictionary for '${lang}'`);
      assert.match(htmlContent, new RegExp(`<option\\s+value=["']${lang}["']`, "i"), `Language dropdown must include '${lang}'`);
    }
  });

  await t.test("4. Full Suite of Guest Actions (Cards & Sheets)", () => {
    assert.match(htmlContent, /id=["']btn-confirm-yes["']/i, "Must have 'Yes, this is correct' button");
    assert.match(htmlContent, /id=["']btn-dispute["']/i, "Must have 'Something is wrong' button");
    assert.match(htmlContent, /id=["']btn-open-paid-sheet["']/i, "Must have 'I have paid' action");
    assert.match(htmlContent, /id=["']btn-open-date-sheet["']/i, "Must have 'Choose return date' action");
    assert.match(htmlContent, /id=["']btn-open-reminder-sheet["']/i, "Must have 'Add reminder' action");
    assert.match(htmlContent, /id=["']btn-open-pay-sheet["']/i, "Must have 'Pay now' action");
    assert.match(htmlContent, /id=["']btn-print-receipt["']/i, "Must have 'Download receipt' action");
  });

  await t.test("5. Installment Progress Bar", () => {
    assert.match(htmlContent, /id=["']installment-progress-box["']/i, "Must have installment progress box");
    assert.match(htmlContent, /inst-progress-fill/i, "Must have progress bar fill element");
    assert.match(htmlContent, /inst-next-due/i, "Must show next due amount & date");
  });

  await t.test("6. Local QR Code Generator & UPI Flow", () => {
    assert.match(htmlContent, /src=["'][^"']*qrcode\.min\.js["']/i, "Must link to bundled local QR code generator (no third party CDN)");
    assert.match(jsContent, /upi:\/\/pay\?/i, "Must build standard UPI pay intent URI");
    assert.match(htmlContent, /btn-toggle-upi/i, "Must have toggle to show/mask UPI ID");
    assert.match(htmlContent, /text-upi-warning/i, "Must warn user to check recipient name in UPI app");
  });

  await t.test("7. Calendar (.ics) & Google Calendar Integration", () => {
    assert.match(jsContent, /BEGIN:VCALENDAR/i, "Must generate valid .ics iCalendar format");
    assert.match(jsContent, /BEGIN:VALARM/i, "Calendar event must configure reminder alarm 1 day prior");
    assert.match(jsContent, /calendar\.google\.com\/calendar\/render/i, "Must support Google Calendar template link");
    assert.match(jsContent, /Notification\.requestPermission/i, "Must support permission-gated browser notification switch");
  });

  await t.test("8. Accessibility & Dark/Light Mode", () => {
    assert.match(htmlContent, /data-theme/i, "Must support manual & system theme switcher");
    assert.match(cssContent, /prefers-color-scheme:\s*dark/i, "Must have native prefers-color-scheme media query");
    assert.match(htmlContent, /aria-label/i, "Must include aria-label accessibility landmarks");
    assert.match(cssContent, /@media\s+print/i, "Must include print stylesheet for receipt generation");
  });

  await t.test("9. Security & Privacy Assurance", () => {
    assert.match(htmlContent, /href=["'](\/privacy|\/privacy\.html|privacy\.html)["']/i, "Must link to data privacy and deletion policy");
    assert.match(htmlContent, /href=["']\/delete-account["']/i, "Must link to account and data deletion page");
    assert.match(jsContent, /window\.location\.hash/i, "Secret token must only be read from URL fragment");
    assert.doesNotMatch(bundleContent, /\bgtag\b|\bgoogle-analytics\b|\bmixpanel\b/i, "Strictly zero third-party trackers");

    assert.match(htmlContent, /<link\s+rel=["']stylesheet["']\s+href=["']guest\.css["']/i, "Styles must be in separate guest.css");
    assert.match(htmlContent, /<script\s+src=["']guest\.js["']/i, "Scripts must be in separate guest.js");

    const uHeaders = (firebaseJson.hosting.headers || []).find((h) => h.source === "/u/**");
    assert.ok(uHeaders, "firebase.json must define headers for /u/**");
    const csp = (uHeaders.headers || []).find((h) => h.key === "Content-Security-Policy");
    assert.ok(csp, "CSP header must be defined for /u/**");
    assert.doesNotMatch(csp.value, /'unsafe-inline'/, "CSP must not contain 'unsafe-inline'");
    const hsts = (uHeaders.headers || []).find((h) => h.key === "Strict-Transport-Security");
    assert.ok(hsts, "Strict-Transport-Security must be present");
  });

  await t.test("10. OpenGraph WhatsApp Preview & Self-Contained Verification Link Decoding", () => {
    assert.match(htmlContent, /property=["']og:title["']/i, "Must include OpenGraph og:title");
    assert.match(htmlContent, /property=["']og:description["']/i, "Must include OpenGraph og:description");
    assert.match(htmlContent, /property=["']og:image["']/i, "Must include OpenGraph og:image");

    const { decodeEmbeddedVerificationToken } = require("../web/u/guest.js");
    assert.equal(typeof decodeEmbeddedVerificationToken, "function");

    const startMs = 1730000000000;
    const rawV2 = `v2|k8m2p9x4q1|a7B9cD2eF4gH6jK8|G|Jyotirmoy|Ravi|1500||${startMs.toString(36)}`;
    const tokenV2 = Buffer.from(rawV2, "utf8").toString("base64url");
    const decodedV2 = decodeEmbeddedVerificationToken(tokenV2);
    assert.ok(decodedV2, "Must decode compact v2 verification token");
    assert.equal(decodedV2.recordId, "k8m2p9x4q1");
    assert.equal(decodedV2.lenderName, "Jyotirmoy");
    assert.equal(decodedV2.borrowerName, "Ravi");
    assert.equal(decodedV2.principalAmount, 1500);
    assert.equal(decodedV2.amountPaise, 150000);
    assert.equal(decodedV2.isSelfContainedToken, true);

    const rawV1 = `rid=rec-legacy&tok=tok-legacy&role=GIVEN&lender=Jyotirmoy&borrower=Ravi&amt=2500.00&cur=INR&start=${startMs}`;
    const tokenV1 = Buffer.from(rawV1, "utf8").toString("base64url");
    const decodedV1 = decodeEmbeddedVerificationToken(tokenV1);
    assert.ok(decodedV1, "Must decode legacy v1 verification token");
    assert.equal(decodedV1.recordId, "rec-legacy");
    assert.equal(decodedV1.principalAmount, 2500);

    const apiRewrites = (firebaseJson.hosting.rewrites || []).filter((r) => r.source && r.source.startsWith("/api/"));
    assert.equal(apiRewrites.length, 0, "Expected zero /api/* rewrites in firebase.json in Spark Mode");
    const guestModule = require("../web/u/guest.js");
    assert.equal(typeof guestModule.fetchGuestLinkFromFirestore, "function");
    assert.equal(typeof guestModule.writeGuestActionToFirestore, "function");
    assert.equal(typeof guestModule.sha256Hex, "function");
  });

  await t.test("11. Nested Route Asset Resolution (/u/r/*), Spark Mode Exports & High-Contrast Tokens", () => {
    assert.match(htmlContent, /<base\s+href=["']\/u\/["']\s*\/?>/i, "Must include <base href=\"/u/\">");
    assert.match(cssContent, /\.hidden,\s*\[hidden\]\s*\{\s*display:\s*none\s*!important;/i, "Must enforce .hidden with !important");

    const rewrites = firebaseJson.hosting.rewrites || [];
    const cssRewriteIdx = rewrites.findIndex((r) => r.source === "/u/**/guest.css" && r.destination === "/u/guest.css");
    const jsRewriteIdx = rewrites.findIndex((r) => r.source === "/u/**/guest.js" && r.destination === "/u/guest.js");
    const urRewriteIdx = rewrites.findIndex((r) => r.source === "/u/r/**");
    assert.ok(cssRewriteIdx >= 0 && cssRewriteIdx < urRewriteIdx, "/u/**/guest.css rewrite must precede /u/r/**");
    assert.ok(jsRewriteIdx >= 0 && jsRewriteIdx < urRewriteIdx, "/u/**/guest.js rewrite must precede /u/r/**");

    // In Spark Mode, server-side WhatsApp OTP is disabled (marked [BLAZE-DISABLED]) and
    // bindOtpInputs safely exports a callable function without throwing when invoked.
    const guestExports = require("../web/u/guest.js");
    assert.equal(typeof guestExports.bindOtpInputs, "function");
    assert.doesNotThrow(() => guestExports.bindOtpInputs([], () => {}));

    // Verify deterministic SHA-256 hashing helper for capability link IDs
    assert.equal(typeof guestExports.sha256Hex, "function");
    assert.equal(typeof guestExports.maskUpi, "function");
    assert.match(guestExports.maskUpi("ravi.kumar@okhdfcbank"), /\*+@okhdfcbank/);

    assert.match(cssContent, /\.skip-link\s*\{[^}]*top:\s*-120px/i);
    assert.match(cssContent, /--amount-text:\s*#f8fafc/i);
    assert.match(cssContent, /--badge-slate-text:\s*#e2e8f0/i);
    assert.doesNotMatch(jsContent, /expiryDateMillis:\s*Date\.now\(\)\s*\+\s*30\s*\*\s*86400000/);
  });
});
