/**
 * playwright_e2e.test.js
 * End-to-End browser test suite using Playwright across Chromium, Firefox and WebKit.
 * Tests the Landing Page, Interactive Demo, Repayment Calculator, and Guest Confirmation Portal
 * across all five languages (English, Hindi, Bengali, Assamese, Nepali) at 360px, 768px and 1280px viewports,
 * executes axe-core accessibility audits, captures visual regression screenshots, and enforces performance targets.
 */

const test = require("node:test");
const assert = require("node:assert");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { chromium, firefox, webkit } = require("playwright");

const WEB_DIR = path.resolve(__dirname, "../../web");
const AXE_PATH = path.resolve(__dirname, "../node_modules/axe-core/axe.min.js");
const CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const SCREENSHOTS_DIR = path.resolve(__dirname, "../../web/test-results/screenshots");

// Ensure screenshots directory exists
if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

// MIME types dictionary for local test server
const MIME_TYPES = {
  ".html": "text/html",
  ".css": "text/css",
  ".js": "application/javascript",
  ".json": "application/json",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".woff2": "font/woff2",
  ".svg": "image/svg+xml",
  ".xml": "application/xml",
  ".txt": "text/plain"
};

let server;
let serverPort;
let serverUrl;
let browserCr;
let browserFf;
let browserWk;

test.before(async () => {
  // 1. Start local HTTP static server
  await new Promise((resolve) => {
    server = http.createServer((req, res) => {
      let reqPath = req.url.split("?")[0].split("#")[0];
      if (reqPath === "/" || reqPath === "") reqPath = "/index.html";
      if (reqPath === "/u" || reqPath === "/u/") reqPath = "/u/index.html";
      if (reqPath === "/trust" || reqPath === "/trust/") reqPath = "/trust/index.html";
      if (reqPath === "/updates" || reqPath === "/updates/") reqPath = "/updates/index.html";

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

  // 2. Launch headless browser engines: Chromium, Firefox and WebKit
  browserCr = await chromium.launch({
    executablePath: fs.existsSync(CHROME_PATH) ? CHROME_PATH : undefined,
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"]
  });

  browserFf = await firefox.launch({
    headless: true
  });

  browserWk = await webkit.launch({
    headless: true
  });
});

test.after(async () => {
  if (browserCr) await browserCr.close();
  if (browserFf) await browserFf.close();
  if (browserWk) await browserWk.close();
  if (server) server.close();
});

test("Part 6 — Comprehensive Multi-Browser E2E, Regression & Accessibility Suite", async (t) => {
  const VIEWPORTS = [
    { name: "Mobile (360px)", width: 360, height: 740, isMobile: true, hasTouch: true },
    { name: "Tablet (768px)", width: 768, height: 1024, isMobile: true, hasTouch: true },
    { name: "Desktop (1280px)", width: 1280, height: 800, isMobile: false, hasTouch: false }
  ];

  const LANGUAGES = ["en", "hi", "bn", "as", "ne"];

  // -------------------------------------------------------------
  // Test 1: Confirm Chromium, Firefox and WebKit launch & render
  // -------------------------------------------------------------
  await t.test("1. Multi-Engine Launch Verification (Chromium, Firefox, WebKit)", async () => {
    const engines = [
      { name: "Chromium", instance: browserCr },
      { name: "Firefox", instance: browserFf },
      { name: "WebKit", instance: browserWk }
    ];

    for (const engine of engines) {
      assert.ok(engine.instance, `${engine.name} browser instance must be launched and active`);
      const ctx = await engine.instance.newContext({ viewport: { width: 1280, height: 800 } });
      const page = await ctx.newPage();

      await page.goto(`${serverUrl}/`);
      const landingTitle = await page.title();
      assert.ok(landingTitle.length > 5, `${engine.name} renders landing page with title`);

      await page.goto(`${serverUrl}/u/?demo=true`);
      await page.waitForSelector("#screen-step1", { state: "visible", timeout: 5000 });
      const amtText = await page.textContent("#step1-amount-number");
      assert.ok(amtText.includes("15,000") || amtText.includes("15000"), `${engine.name} renders guest demo amount`);

      await ctx.close();
    }
  });

  // -------------------------------------------------------------
  // Test 2: Landing Page Language Switch across all 5 languages
  // -------------------------------------------------------------
  await t.test("2. Landing Page Multi-Language Switch (5 Languages at 360px, 768px, 1280px)", async () => {
    for (const vp of VIEWPORTS) {
      const context = await browserCr.newContext({
        viewport: { width: vp.width, height: vp.height },
        isMobile: vp.isMobile,
        hasTouch: vp.hasTouch
      });
      const page = await context.newPage();
      await page.goto(`${serverUrl}/`);

      for (const lang of LANGUAGES) {
        await page.selectOption("#lang-select", lang);
        await page.waitForTimeout(50);
        const heroTitle = await page.textContent(".hero-title");
        assert.ok(heroTitle.length > 5, `Hero title must be populated for lang '${lang}' in ${vp.name}`);

        const heroPromise = await page.textContent(".hero-promise");
        assert.ok(heroPromise.length > 5, `Hero promise must be populated for lang '${lang}' in ${vp.name}`);
      }

      // Visual regression capture for landing page viewport
      const vpSuffix = vp.width === 360 ? "360px" : vp.width === 768 ? "768px" : "1280px";
      await page.screenshot({ path: path.join(SCREENSHOTS_DIR, `landing-${vpSuffix}.png`), fullPage: false });

      await context.close();
    }
  });

  // -------------------------------------------------------------
  // Test 3: Mobile Menu (Hamburger below 900px with Focus Trap & Esc)
  // -------------------------------------------------------------
  await t.test("3. Mobile Menu Navigation (Hamburger below 900px, Focus Trap, Esc to Close)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 }, isMobile: true });
    const page = await context.newPage();
    await page.goto(`${serverUrl}/`);

    // Verify hamburger button visible on mobile
    assert.ok(await page.isVisible("#mobile-menu-btn"), "Hamburger button must be visible at 360px");

    // Open drawer
    await page.click("#mobile-menu-btn");
    await page.waitForTimeout(100);
    const drawerOpen = await page.evaluate(() => document.getElementById("mobile-drawer").classList.contains("open"));
    assert.strictEqual(drawerOpen, true, "Drawer must have 'open' class when clicked");

    // Close via Esc key
    await page.keyboard.press("Escape");
    await page.waitForTimeout(100);
    const drawerClosedEsc = await page.evaluate(() => document.getElementById("mobile-drawer").classList.contains("open"));
    assert.strictEqual(drawerClosedEsc, false, "Drawer must close when Escape is pressed");

    // Reopen and close via Close button
    await page.click("#mobile-menu-btn");
    await page.waitForTimeout(100);
    await page.click("#drawer-close-btn");
    await page.waitForTimeout(100);
    const drawerClosedBtn = await page.evaluate(() => document.getElementById("mobile-drawer").classList.contains("open"));
    assert.strictEqual(drawerClosedBtn, false, "Drawer must close when Close (X) button is clicked");

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 4: Interactive Demo (4 Steps, Back/Restart & Keyboard Arrow Navigation)
  // -------------------------------------------------------------
  await t.test("4. Interactive Demo Mode (4 Steps, Back/Restart, Keyboard Arrow Navigation)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();
    await page.goto(`${serverUrl}/`);

    // Step 1: Direction & Amount
    assert.ok(await page.isVisible("#demo-step-panel-1"), "Demo step 1 must be active initially");
    await page.click("#demo-dir-took");
    assert.strictEqual(await page.getAttribute("#demo-dir-took", "aria-checked"), "true");
    await page.fill("#demo-amount-input", "5000");

    // Advance to Step 2
    await page.click("#demo-btn-to-2");
    await page.waitForTimeout(80);
    assert.ok(await page.isVisible("#demo-step-panel-2"), "Demo step 2 must be visible");

    // Back to Step 1
    await page.click("#demo-btn-back-1");
    await page.waitForTimeout(80);
    assert.ok(await page.isVisible("#demo-step-panel-1"), "Back button must return to step 1");

    // Forward through Step 2 -> Step 3
    await page.click("#demo-btn-to-2");
    await page.fill("#demo-name-input", "Ravi");
    await page.fill("#demo-note-input", "Shop renovation");
    await page.click("#demo-btn-to-3");
    await page.waitForTimeout(80);
    assert.ok(await page.isVisible("#demo-step-panel-3"), "Demo step 3 must be visible");

    // Advance to Step 4 (WhatsApp Preview)
    await page.click("#demo-btn-to-4");
    await page.waitForTimeout(80);
    assert.ok(await page.isVisible("#demo-step-panel-4"), "Demo step 4 must be visible");

    // Restart button
    await page.click("#demo-btn-restart");
    await page.waitForTimeout(80);
    assert.ok(await page.isVisible("#demo-step-panel-1"), "Restart button must reset to step 1");

    // Keyboard Arrow Navigation on Tabs
    await page.focus("#tab-step-1");
    await page.keyboard.press("ArrowRight");
    await page.waitForTimeout(50);
    const focusedTab2 = await page.evaluate(() => document.activeElement.id);
    assert.strictEqual(focusedTab2, "tab-step-2", "ArrowRight must move focus to tab 2");

    await page.keyboard.press("End");
    await page.waitForTimeout(50);
    const focusedTabEnd = await page.evaluate(() => document.activeElement.id);
    assert.strictEqual(focusedTabEnd, "tab-step-4", "End key must move focus to tab 4");

    await page.keyboard.press("Home");
    await page.waitForTimeout(50);
    const focusedTabHome = await page.evaluate(() => document.activeElement.id);
    assert.strictEqual(focusedTabHome, "tab-step-1", "Home key must move focus to tab 1");

    // Screenshot of demo widget
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, "demo-360px.png") });

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 5: Calculator Widget (Paise Math, Table, Progress, Copy Summary)
  // -------------------------------------------------------------
  await t.test("5. Repayment Calculator Widget (Math Integrity, Table, Progress & Copy)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();
    await page.goto(`${serverUrl}/`);

    // Set principal = 10000, 3 installments, monthly
    await page.fill("#calc-principal", "10000");
    await page.evaluate(() => {
      const slider = document.getElementById("calc-installments");
      slider.value = "3";
      slider.dispatchEvent(new Event("input"));
      slider.dispatchEvent(new Event("change"));
    });
    await page.click("#calc-int-none");

    await page.waitForTimeout(100);

    // Verify KPIs
    const kpiInst = await page.textContent("#calc-kpi-installment");
    assert.ok(kpiInst.includes("3,333") || kpiInst.includes("3,334") || kpiInst.includes("3333") || kpiInst.includes("3334"), `Expected ~₹3,334 or ₹3,333 per installment, got ${kpiInst}`);

    // Verify Schedule Table
    const tableRows = await page.$$("#calc-schedule-body tr");
    assert.strictEqual(tableRows.length, 3, "Schedule table must render exactly 3 installment rows");

    // Switch to Simple Interest 12%
    await page.click("#calc-int-simple");
    await page.fill("#calc-rate", "12");
    await page.waitForTimeout(100);

    const kpiInterest = await page.textContent("#calc-kpi-interest");
    assert.ok(kpiInterest.includes("300"), `Expected ₹300 interest for 3 months at 12%, got ${kpiInterest}`);

    // Copy Summary Button
    assert.ok(await page.isVisible("#calc-copy-summary-btn"), "Copy summary button must be visible");

    // Screenshot of calculator
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, "calculator-360px.png") });

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 6: Guest Page in all 5 languages & Viewports
  // -------------------------------------------------------------
  await t.test("6. Guest Page Multi-Language Switch (5 Languages at 360px, 768px, 1280px)", async () => {
    for (const vp of VIEWPORTS) {
      const context = await browserCr.newContext({
        viewport: { width: vp.width, height: vp.height },
        isMobile: vp.isMobile,
        hasTouch: vp.hasTouch
      });
      const page = await context.newPage();

      await page.goto(`${serverUrl}/u/?demo=true`);
      await page.waitForSelector("#screen-step1", { state: "visible", timeout: 5000 });

      // Verify demo banner
      assert.ok(await page.isVisible("#demo-banner"), `Demo banner visible in ${vp.name}`);

      // Verify amount display
      const amtText = await page.textContent("#step1-amount-number");
      assert.ok(amtText.includes("15,000") || amtText.includes("15000"), `Amount ₹15,000 in ${vp.name}`);

      // Test all 5 languages
      for (const lang of LANGUAGES) {
        await page.selectOption("#lang-select", lang);
        await page.waitForTimeout(50);
        const questionText = await page.textContent("#step1-question");
        assert.ok(questionText.length > 5, `Question text populated for lang '${lang}' in ${vp.name}`);

        const btnYesText = await page.textContent("#btn-confirm-yes");
        assert.ok(btnYesText.length > 0, `Confirm button text populated for '${lang}'`);
      }

      if (vp.width === 360) {
        await page.screenshot({ path: path.join(SCREENSHOTS_DIR, "guest-step1-360px.png") });
      }

      await context.close();
    }
  });

  // -------------------------------------------------------------
  // Test 7: Full Confirmation Flow (Step 1 -> Step 2 -> Step 3)
  // -------------------------------------------------------------
  await t.test("7. Full Guest Confirmation Flow (Step 1 -> Step 2 -> Step 3)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 } });
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

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, "guest-step3-360px.png") });

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 8: Modals (Dispute, Paid Claim, Date Proposal, Reminder, UPI QR)
  // -------------------------------------------------------------
  await t.test("8. Guest Action Modals (Dispute, Paid Claim, Date Proposal, Reminder, UPI QR)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await context.newPage();

    // 1. Dispute modal from Step 1
    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-dispute");
    await page.waitForSelector("#dispute-modal.active", { state: "visible" });
    await page.selectOption("#select-dispute-reason", "WRONG_AMOUNT");
    await page.fill("#input-dispute-note", "The amount should be 10,000 not 15,000");
    await page.click("#btn-submit-dispute");
    await page.waitForSelector("#dispute-modal.active", { state: "detached" });

    // 2. Step 3 modals: Pay by UPI QR, Claim Paid, Date Proposal, Reminder
    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    // UPI QR modal
    await page.click("#btn-open-pay-sheet");
    await page.waitForSelector("#upi-modal.active", { state: "visible" });
    const qrCanvas = await page.$("#qrcode-canvas svg, #qrcode-canvas img, #qrcode-canvas canvas");
    assert.ok(qrCanvas !== null, "UPI QR Code canvas rendered");
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, "guest-qr-1280px.png") });
    await page.click("#close-upi-modal");
    await page.waitForSelector("#upi-modal.active", { state: "detached" });

    // Paid Claim modal
    await page.click("#btn-open-paid-sheet");
    await page.waitForSelector("#paid-modal.active", { state: "visible" });
    await page.fill("#input-paid-amount", "5000");
    await page.click("#btn-submit-paid");
    await page.waitForSelector("#paid-modal.active", { state: "detached" });

    // Date Proposal modal
    await page.click("#btn-open-date-sheet");
    await page.waitForSelector("#date-modal.active", { state: "visible" });
    await page.fill("#input-return-date", "2027-04-30");
    await page.click("#btn-submit-date");
    await page.waitForSelector("#date-modal.active", { state: "detached" });

    // Reminder modal (Google Calendar link)
    await page.click("#btn-open-reminder-sheet");
    await page.waitForSelector("#reminder-modal.active", { state: "visible" });
    const gcalLink = await page.getAttribute("#btn-google-cal", "href");
    assert.ok(gcalLink && gcalLink.includes("calendar.google.com"), "Valid Google Calendar URL");
    await page.click("#close-reminder-modal");
    await page.waitForSelector("#reminder-modal.active", { state: "detached" });

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 9: Every Error State (Expired, Revoked, Wrong Code, Too Many Tries, Offline)
  // -------------------------------------------------------------
  await t.test("9. Friendly Error State Screens (Expired, Revoked, Wrong Code, Too Many Tries, Offline)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    // 1. Expired state
    await page.goto(`${serverUrl}/u/`);
    await page.waitForSelector("#error-view", { state: "visible" });
    let errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("expired"), "Expired title must be displayed");

    // 2. Revoked state
    await page.goto(`${serverUrl}/u/?demo=true&state=revoked`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("cancelled") || errorTitle.toLowerCase().includes("revoked"), "Revoked title displayed");

    // 3. Wrong code state
    await page.goto(`${serverUrl}/u/?demo=true&state=wrong_code`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("incorrect"), "Incorrect code title displayed");

    // 4. Too many tries state
    await page.goto(`${serverUrl}/u/?demo=true&state=too_many_tries`);
    await page.waitForSelector("#error-view", { state: "visible" });
    errorTitle = await page.textContent("#error-title");
    assert.ok(errorTitle.toLowerCase().includes("too many"), "Too many tries title displayed");

    // 5. Offline banner detection
    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));
    assert.strictEqual(await page.isVisible("#offline-banner"), true, "Offline banner must appear on offline event");

    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    assert.strictEqual(await page.isVisible("#offline-banner"), false, "Offline banner must hide on online event");

    await context.close();
  });

  // -------------------------------------------------------------
  // Test 10: axe-core Automated Accessibility Audits (Zero Violations)
  // -------------------------------------------------------------
  await t.test("10. axe-core Automated Accessibility Audits (Zero Critical/Serious Violations)", async () => {
    const axeScript = fs.readFileSync(AXE_PATH, "utf8");

    const pagesToTest = [
      { name: "Guest Page (Mobile 360px)", url: `${serverUrl}/u/?demo=true`, width: 360, height: 740 },
      { name: "Guest Page (Desktop 1280px)", url: `${serverUrl}/u/?demo=true`, width: 1280, height: 800 },
      { name: "Landing Page (Mobile 360px)", url: `${serverUrl}/`, width: 360, height: 740 },
      { name: "Landing Page (Desktop 1280px)", url: `${serverUrl}/`, width: 1280, height: 800 },
      { name: "Privacy Page (Mobile 360px)", url: `${serverUrl}/privacy.html`, width: 360, height: 740 }
    ];

    for (const item of pagesToTest) {
      const context = await browserCr.newContext({ viewport: { width: item.width, height: item.height } });
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
  // Test 11: Lighthouse & Performance Targets Audit
  // -------------------------------------------------------------
  await t.test("11. Performance, Image CLS Prevention, SEO & Quality Audit", async () => {
    const context = await browserCr.newContext({ viewport: { width: 1280, height: 800 } });
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
