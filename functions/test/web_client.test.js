const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Stage W2 Web Client UI & Zero-Cost WhatsApp OTP Verification", async (t) => {
  const htmlPath = path.resolve(__dirname, "../../web/u/index.html");
  const htmlContent = fs.readFileSync(htmlPath, "utf8");

  await t.test("1. Mobile-First (360px viewport ready) & Responsive Meta", () => {
    assert.match(htmlContent, /<meta\s+name=["']viewport["']\s+content=["'][^"']*width=device-width[^"']*["']/i, "Must have standard mobile viewport");
    assert.match(htmlContent, /max-width:\s*480px/i, "Container max-width must gracefully scale down to 360px mobile view");
    assert.match(htmlContent, /min-height:\s*48px/i, "Touch targets must satisfy minimum 48px accessibility guideline");
  });

  await t.test("2. Zero-Cost WhatsApp OTP Verification Flow", () => {
    assert.match(htmlContent, /WhatsApp Verification/i, "Must include WhatsApp verification banner and sheet");
    assert.match(htmlContent, /otp-box/i, "Must render 6-digit verification code input boxes");
    assert.match(htmlContent, /btn-ask-wa/i, "Must include one-tap 'Ask on WhatsApp' link via wa.me");
    assert.match(htmlContent, /wa\.me/i, "Must use standard zero-cost WhatsApp universal link");
    assert.match(htmlContent, /btn-report-not-me/i, "Must have 'This is not me' reporting action");
  });

  await t.test("3. Multi-Language Support (5 Languages)", () => {
    const requiredLanguages = ["en", "hi", "bn", "as", "ne"];
    for (const lang of requiredLanguages) {
      assert.match(htmlContent, new RegExp(`(?:["']${lang}["']|\\b${lang}\\b)\\s*:\\s*\\{`), `Must include dictionary for '${lang}'`);
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
    assert.match(htmlContent, /upi:\/\/pay\?/i, "Must build standard UPI pay intent URI");
    assert.match(htmlContent, /btn-toggle-upi/i, "Must have toggle to show/mask UPI ID");
    assert.match(htmlContent, /text-upi-warning/i, "Must warn user to check recipient name in UPI app");
  });

  await t.test("7. Calendar (.ics) & Google Calendar Integration", () => {
    assert.match(htmlContent, /BEGIN:VCALENDAR/i, "Must generate valid .ics iCalendar format");
    assert.match(htmlContent, /BEGIN:VALARM/i, "Calendar event must configure reminder alarm 1 day prior");
    assert.match(htmlContent, /calendar\.google\.com\/calendar\/render/i, "Must support Google Calendar template link");
    assert.match(htmlContent, /Notification\.requestPermission/i, "Must support permission-gated browser notification switch");
  });

  await t.test("8. Accessibility & Dark/Light Mode", () => {
    assert.match(htmlContent, /data-theme/i, "Must support manual & system theme switcher");
    assert.match(htmlContent, /prefers-color-scheme:\s*dark/i, "Must have native prefers-color-scheme media query");
    assert.match(htmlContent, /aria-label/i, "Must include aria-label accessibility landmarks");
    assert.match(htmlContent, /@media\s+print/i, "Must include print stylesheet for receipt generation");
  });

  await t.test("9. Security & Privacy Assurance", () => {
    assert.match(htmlContent, /privacy\.html/i, "Must link to data privacy and deletion policy");
    assert.match(htmlContent, /window\.location\.hash/i, "Secret token must only be read from URL fragment (never query params or server logs)");
    assert.doesNotMatch(htmlContent, /\bgtag\b|\bgoogle-analytics\b|\bmixpanel\b/i, "Strictly zero third-party trackers");
  });
});
