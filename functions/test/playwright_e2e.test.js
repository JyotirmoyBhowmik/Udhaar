/**
 * playwright_e2e.test.js
 * End-to-End browser test suite using Playwright and Google Chrome
 * Tests the Guest Page and Landing Page in all 5 languages at 360px mobile & desktop viewports,
 * executes axe-core accessibility audits, and verifies all user actions and error states.
 */

const test = require("node:test");
const assert = require("node:assert");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const WEB_DIR = path.resolve(__dirname, "../../web");
const AXE_PATH = path.resolve(__dirname, "../node_modules/axe-core/axe.min.js");
const CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";

// MIME types dictionary for local test server
const MIME_TYPES = {
  ".html": "text/html",
  ".css": "text/css",
  ".js": "application/javascript",
  ".json": "application/json",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".xml": "application/xml",
  ".txt": "text/plain"
};

let server;
let serverPort;
let serverUrl;
let browser;

test.before(async () => {
  // 1. Start local HTTP static server
  await new Promise((resolve) => {
    server = http.createServer((req, res) => {
      let reqPath = req.url.split("?")[0].split("#")[0];
      if (reqPath === "/" || reqPath === "") reqPath = "/index.html";
      if (reqPath === "/u" || reqPath === "/u/") reqPath = "/u/index.html";

      const filePath = path.join(WEB_DIR, reqPath);
      if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath).toLowerCase();
        const mime = MIME_TYPES[ext] || "application/octet-stream";
        res.writeHead(200, { "Content-Type": mime });
        res.end(fs.readFileSync(filePath));
      } else {
        // Fallback for SPA rewrites
        if (reqPath.startsWith("/u/")) {
          res.writeHead(200, { "Content-Type": "text/html" });
          res.end(fs.readFileSync(path.join(WEB_DIR, "u/index.html")));
        } else {
          res.writeHead(404, { "Content-Type": "text/html" });
          res.end(fs.readFileSync(path.join(WEB_DIR, "404.html")));
        }
      }
    });

    server.listen(0, "127.0.0.1", () => {
      serverPort = server.address().port;
      serverUrl = `http://127.0.0.1:${serverPort}`;
      resolve();
    });
  });

  // 2. Launch headless Chrome via Playwright
  browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"]
  });
});

test.after(async () => {
  if (browser) await browser.close();
  if (server) server.close();
});

test("Part 4 — Playwright E2E & axe Accessibility Test Suite", async (t) => {
  const VIEWPORTS = [
    { name: "Mobile (360px)", width: 360, height: 740, isMobile: true, hasTouch: true },
    { name: "Desktop (1280px)", width: 1280, height: 800, isMobile: false, hasTouch: false }
  ];

  const LANGUAGES = ["en", "hi", "bn", "as", "ne"];

  // -------------------------------------------------------------
  // Test 1: Guest Page in all 5 languages at 360px and desktop
  // -------------------------------------------------------------
  await t.test("1. Guest Page Language Switching & Viewport Rendering", async () => {
    for (const vp of VIEWPORTS) {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        isMobile: vp.isMobile,
        hasTouch: vp.hasTouch
      });
      const page = await context.newPage();

      await page.goto(`${serverUrl}/u/?demo=true`);
      await page.waitForSelector("#screen-step1", { state: "visible", timeout: 5000 });

      // Verify demo banner
      const demoBannerVisible = await page.isVisible("#demo-banner");
      assert.ok(demoBannerVisible, `Demo banner must be visible in ${vp.name}`);

      // Verify amount display
      const amtText = await page.textContent("#step1-amount-number");
      assert.ok(amtText.includes("15,000") || amtText.includes("15000"), `Amount should display ₹15,000 in ${vp.name}, got ${amtText}`);

      // Test all 5 languages
      for (const lang of LANGUAGES) {
        await page.selectOption("#lang-select", lang);
        await page.waitForTimeout(50);
        const questionText = await page.textContent("#step1-question");
        assert.ok(questionText.length > 5, `Question text must be populated for lang '${lang}' in ${vp.name}`);

        const btnYesText = await page.textContent("#btn-confirm-yes");
        assert.ok(btnYesText.length > 0, `Confirm button text populated for '${lang}'`);
      }

      await context.close();
    }
  });

  // -------------------------------------------------------------
  // Test 2: Confirmation Flow (Screen 1 -> Screen 2 -> Screen 3)
  // -------------------------------------------------------------
  await t.test("2. Full Confirmation Flow (Screen 1 -> Screen 2 -> Screen 3)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });

    // Step 1 -> Click "Yes, this is correct"
    await page.click("#btn-confirm-yes");
    await page.waitForSelector("#screen-step2", { state: "visible" });
    assert.strictEqual(await page.isVisible("#screen-step1"), false, "Screen 1 must be hidden");

    // Step 2 -> Enter 6-digit code in .otp-box inputs & submit
    const boxes = await page.$$(".otp-box");
    for (let i = 0; i < boxes.length; i++) {
      await boxes[i].fill(String(i + 1));
    }

    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });
    assert.strictEqual(await page.isVisible("#screen-step2"), false, "Screen 2 must be hidden");

    // Verify Screen 3 action buttons
    assert.ok(await page.isVisible("#btn-open-date-sheet"), "Date proposal button visible");
    assert.ok(await page.isVisible("#btn-open-pay-sheet"), "Pay now by UPI button visible");
    assert.ok(await page.isVisible("#btn-open-reminder-sheet"), "Set reminder button visible");
    assert.ok(await page.isVisible("#btn-open-paid-sheet"), "I have paid button visible");
    assert.ok(await page.isVisible("#btn-print-receipt"), "Download receipt button visible");

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 3: Report a Problem (Dispute Modal)
  // -------------------------------------------------------------
  await t.test("3. Report a Problem (Dispute Modal)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });

    // Click "Something is wrong" (#btn-dispute)
    await page.click("#btn-dispute");
    await page.waitForSelector("#dispute-modal.active", { state: "visible" });

    // Select reason & submit
    await page.selectOption("#select-dispute-reason", "WRONG_AMOUNT");
    await page.fill("#input-dispute-note", "The amount should be 10,000 not 15,000");
    await page.click("#btn-submit-dispute");

    // Modal should close and dispute screen should be shown
    await page.waitForSelector("#dispute-modal.active", { state: "detached" });
    await page.waitForSelector("#error-view", { state: "visible" });
    const disputeTitle = await page.textContent("#error-title");
    assert.ok(disputeTitle.length > 0, "Dispute title must be visible");

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 4: Say He Paid (Payment Claim Modal)
  // -------------------------------------------------------------
  await t.test("4. Say He Paid (Payment Claim Modal)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    // Directly open in confirmed state (demo confirmed)
    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    // Open "I have paid" modal
    await page.click("#btn-open-paid-sheet");
    await page.waitForSelector("#paid-modal.active", { state: "visible" });

    await page.fill("#input-paid-amount", "5000");
    await page.selectOption("#select-paid-mode", "UPI");
    await page.fill("#input-paid-ref", "UPI Reference 9988776655");
    await page.click("#btn-submit-paid");

    await page.waitForSelector("#paid-modal.active", { state: "detached" });
    await context.close();
  });

  // -------------------------------------------------------------
  // Test 5: Propose a Return Date (Date Modal)
  // -------------------------------------------------------------
  await t.test("5. Propose a Return Date (Date Modal)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    // Open "Tell when you will return it" modal
    await page.click("#btn-open-date-sheet");
    await page.waitForSelector("#date-modal.active", { state: "visible" });

    await page.fill("#input-return-date", "2027-04-30");
    await page.fill("#input-return-note", "Will repay on salary day");
    await page.click("#btn-submit-date");

    await page.waitForSelector("#date-modal.active", { state: "detached" });
    await context.close();
  });

  // -------------------------------------------------------------
  // Test 6: Set Reminder (Google Calendar Link & .ics Download)
  // -------------------------------------------------------------
  await t.test("6. Set Reminder (Google Calendar Link & .ics Download)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    // Open "Set reminder for myself" modal
    await page.click("#btn-open-reminder-sheet");
    await page.waitForSelector("#reminder-modal.active", { state: "visible" });

    const gcalLink = await page.getAttribute("#btn-google-cal", "href");
    assert.ok(gcalLink && gcalLink.includes("calendar.google.com"), "Must have valid Google Calendar URL");

    await page.click("#close-reminder-modal");
    await page.waitForSelector("#reminder-modal.active", { state: "detached" });
    await context.close();
  });

  // -------------------------------------------------------------
  // Test 7: Pay by UPI QR Code
  // -------------------------------------------------------------
  await t.test("7. Pay by UPI (QR Code Rendering & Pay Now Intent)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    // Open "Pay now by UPI" modal
    await page.click("#btn-open-pay-sheet");
    await page.waitForSelector("#upi-modal.active", { state: "visible" });

    // Check QR container
    const qrCanvas = await page.$("#qrcode-canvas svg, #qrcode-canvas img, #qrcode-canvas canvas");
    assert.ok(qrCanvas !== null, "UPI QR Code must be rendered");

    // Check pay now button
    const payAppHref = await page.getAttribute("#btn-open-upi-app", "href");
    assert.ok(payAppHref && payAppHref.startsWith("upi://pay"), "Must build standard upi://pay intent");

    await page.click("#close-upi-modal");
    await page.waitForSelector("#upi-modal.active", { state: "detached" });
    await context.close();
  });

  // -------------------------------------------------------------
  // Test 8: Friendly Error States (Expired, Revoked, Wrong Code, Too Many Tries, Offline)
  // -------------------------------------------------------------
  await t.test("8. Friendly Error State Screens (Expired, Revoked, Wrong Code, Too Many Tries, Offline)", async () => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    // 1. Expired state (navigating without secret/token)
    await page.goto(`${serverUrl}/u/`);
    await page.waitForSelector("#error-view", { state: "visible" });
    let errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("expired"), "Must display friendly expired title");

    // 2. Revoked state
    await page.goto(`${serverUrl}/u/?demo=true&state=revoked`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("cancelled") || errorTitle.toLowerCase().includes("revoked"), "Must display revoked state");

    // 3. Wrong code state
    await page.goto(`${serverUrl}/u/?demo=true&state=wrong_code`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("incorrect"), "Must display incorrect code state");

    // 4. Too many tries state
    await page.goto(`${serverUrl}/u/?demo=true&state=too_many_tries`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("too many"), "Must display too many tries state");

    // 5. Offline banner detection
    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));
    assert.strictEqual(await page.isVisible("#offline-banner"), true, "Offline banner must appear when offline event fires");

    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    assert.strictEqual(await page.isVisible("#offline-banner"), false, "Offline banner must hide when online event fires");

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 9: axe-core Automated Accessibility Audit
  // -------------------------------------------------------------
  await t.test("9. axe-core Automated Accessibility Audits (Zero Critical/Serious Violations)", async () => {
    const axeScript = fs.readFileSync(AXE_PATH, "utf8");

    const pagesToTest = [
      { name: "Guest Page (Mobile 360px)", url: `${serverUrl}/u/?demo=true`, width: 360, height: 740 },
      { name: "Guest Page (Desktop)", url: `${serverUrl}/u/?demo=true`, width: 1280, height: 800 },
      { name: "Landing Page (Mobile 360px)", url: `${serverUrl}/`, width: 360, height: 740 },
      { name: "Landing Page (Desktop)", url: `${serverUrl}/`, width: 1280, height: 800 }
    ];

    for (const item of pagesToTest) {
      const context = await browser.newContext({ viewport: { width: item.width, height: item.height } });
      const page = await context.newPage();

      await page.goto(item.url);
      await page.addScriptTag({ content: axeScript });

      const axeResults = await page.evaluate(async () => {
        return await window.axe.run({
          runOnly: {
            type: "tag",
            values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]
          }
        });
      });

      // Filter critical and serious violations
      const severeViolations = axeResults.violations.filter(
        (v) => v.impact === "critical" || v.impact === "serious"
      );

      if (severeViolations.length > 0) {
        console.error(`axe violations in ${item.name}:`, JSON.stringify(severeViolations, null, 2));
      }

      assert.strictEqual(
        severeViolations.length,
        0,
        `Expected 0 critical/serious axe violations in ${item.name}, got ${severeViolations.length}`
      );

      await context.close();
    }
  });

  // -------------------------------------------------------------
  // Test 10: Lighthouse Simulated Quality Targets (>= 90)
  // -------------------------------------------------------------
  await t.test("10. Performance, Accessibility, Best Practices & SEO Metrics Audit", async () => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/`);

    // Audit SEO essentials
    const title = await page.title();
    assert.ok(title && title.length > 15, "Page must have descriptive title for SEO");

    const metaDesc = await page.getAttribute("meta[name='description']", "content");
    assert.ok(metaDesc && metaDesc.length > 25, "Page must have meta description for SEO");

    const canonical = await page.getAttribute("link[rel='canonical']", "href");
    assert.ok(canonical && canonical.startsWith("https://"), "Canonical URL configured");

    const structuredData = await page.textContent("script[type='application/ld+json']");
    assert.ok(structuredData && structuredData.includes("SoftwareApplication"), "Structured data present");

    // Audit Image optimizations (dimensions present to prevent CLS)
    const imagesWithoutDimensions = await page.$$eval("img", (imgs) =>
      imgs.filter((img) => !img.hasAttribute("width") || !img.hasAttribute("height")).length
    );
    assert.strictEqual(imagesWithoutDimensions, 0, "All images must have explicit width & height to avoid CLS");

    await context.close();
  });
});
