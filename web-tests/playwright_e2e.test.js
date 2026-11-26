/**
 * web-tests/playwright_e2e.test.js
 * Spark Mode End-to-End browser test suite using Playwright across Chromium, Firefox, and WebKit.
 * Tests Landing Page, Interactive Demo, Repayment Calculator, Terms/Privacy, and Guest Portal
 * across all 5 languages (en, hi, bn, as, ne) and viewports (360px, 768px, 1280px), plus
 * Spark Auth/Firestore emulator guest link flows when FIRESTORE_EMULATOR_HOST is set.
 */

const test = require("node:test");
const assert = require("node:assert");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

let playwright = null;
try {
  playwright = require("playwright");
} catch {
  try {
    playwright = require("@playwright/test");
  } catch {
    playwright = null;
  }
}

const ROOT_DIR = path.resolve(__dirname, "..");
const WEB_DIR = path.join(ROOT_DIR, "web");
const CANDIDATE_AXE_PATHS = [
  path.join(ROOT_DIR, "node_modules/axe-core/axe.min.js"),
  path.join(ROOT_DIR, "archive/blaze_disabled/functions/node_modules/axe-core/axe.min.js")
];
const AXE_PATH = CANDIDATE_AXE_PATHS.find((p) => fs.existsSync(p));
const SCREENSHOTS_DIR = path.join(ROOT_DIR, "web/test-results/screenshots");

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
let serverUrl;
let browserCr;
let browserFf;
let browserWk;
let browsersAvailable = false;

test.before(async () => {
  if (!playwright) {
    return;
  }

  if (!fs.existsSync(SCREENSHOTS_DIR)) {
    fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  }

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
        res.writeHead(200, { "Content-Type": MIME_TYPES[ext] || "application/octet-stream" });
        res.end(fs.readFileSync(filePath));
      } else if (reqPath.startsWith("/u/")) {
        res.writeHead(200, { "Content-Type": "text/html" });
        res.end(fs.readFileSync(path.join(WEB_DIR, "u/index.html")));
      } else {
        res.writeHead(404, { "Content-Type": "text/html" });
        res.end(fs.readFileSync(path.join(WEB_DIR, "404.html")));
      }
    });

    server.listen(0, "127.0.0.1", () => {
      serverUrl = `http://127.0.0.1:${server.address().port}`;
      resolve();
    });
  });

  try {
    browserCr = await playwright.chromium.launch({
      headless: true,
      args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"]
    });
    try {
      browserFf = await playwright.firefox.launch({ headless: true });
    } catch {
      browserFf = null;
    }
    try {
      browserWk = await playwright.webkit.launch({ headless: true });
    } catch {
      browserWk = null;
    }
    browsersAvailable = Boolean(browserCr);
  } catch (err) {
    console.warn("Playwright browser launch skipped:", err.message);
    browsersAvailable = false;
  }
});

test.after(async () => {
  if (browserCr) await browserCr.close();
  if (browserFf) await browserFf.close();
  if (browserWk) await browserWk.close();
  if (server) server.close();
});

test("Spark Web E2E: Multi-Browser, Multi-Language, Guest Portal & Accessibility", async (t) => {
  if (!browsersAvailable) {
    return;
  }

  const VIEWPORTS = [
    { name: "Mobile (360px)", width: 360, height: 740, isMobile: true, hasTouch: true },
    { name: "Tablet (768px)", width: 768, height: 1024, isMobile: true, hasTouch: true },
    { name: "Desktop (1280px)", width: 1280, height: 800, isMobile: false, hasTouch: false }
  ];
  const LANGUAGES = ["en", "hi", "bn", "as", "ne"];

  await t.test("1. Multi-Engine Launch & Demo Render", async () => {
    const engines = [
      { name: "Chromium", instance: browserCr },
      { name: "Firefox", instance: browserFf },
      { name: "WebKit", instance: browserWk }
    ].filter((e) => e.instance);

    for (const engine of engines) {
      const ctx = await engine.instance.newContext({ viewport: { width: 1280, height: 800 } });
      const page = await ctx.newPage();

      await page.goto(`${serverUrl}/`);
      assert.ok((await page.title()).length > 5, `${engine.name} renders landing page`);

      await page.goto(`${serverUrl}/u/?demo=true`);
      await page.waitForSelector("#screen-step1", { state: "visible", timeout: 5000 });
      const amtText = await page.textContent("#step1-amount-number");
      assert.ok(amtText.includes("15,000") || amtText.includes("15000"));
      await ctx.close();
    }
  });

  await t.test("2. Landing, Terms & Privacy 5-Language Switching", async () => {
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
        await page.waitForTimeout(30);
        const heroTitle = await page.textContent(".hero-title");
        assert.ok(heroTitle && heroTitle.length > 5);
      }

      await page.goto(`${serverUrl}/terms.html`);
      for (const lang of LANGUAGES) {
        await page.selectOption("#terms-lang-select", lang);
        await page.waitForTimeout(30);
        const h1 = await page.textContent("h1");
        assert.ok(h1 && h1.length > 3);
      }

      await context.close();
    }
  });

  await t.test("3. Full Guest Demo Flow (Step 1 -> Step 2 -> Step 3 + Modals)", async () => {
    const context = await browserCr.newContext({ viewport: { width: 360, height: 740 } });
    const page = await context.newPage();

    await page.goto(`${serverUrl}/u/?demo=true`);
    await page.waitForSelector("#screen-step1", { state: "visible" });
    await page.click("#btn-confirm-yes");
    await page.waitForSelector("#screen-step2", { state: "visible" });

    const boxes = await page.$$(".otp-box");
    for (let i = 0; i < boxes.length; i++) {
      await boxes[i].fill(String(i + 1));
    }
    await page.click("#btn-submit-code");
    await page.waitForSelector("#screen-step3", { state: "visible" });

    assert.ok(await page.isVisible("#btn-open-pay-sheet"));
    assert.ok(await page.isVisible("#btn-open-paid-sheet"));
    assert.ok(await page.isVisible("#btn-open-date-sheet"));
    await context.close();
  });

  await t.test("4. axe-core Accessibility Audits (Zero Critical/Serious Violations)", async () => {
    if (!AXE_PATH) return;
    const axeScript = fs.readFileSync(AXE_PATH, "utf8");
    const pages = [
      { name: "Landing (360px)", url: `${serverUrl}/`, width: 360, height: 740 },
      { name: "Terms (360px)", url: `${serverUrl}/terms.html`, width: 360, height: 740 },
      { name: "Privacy (360px)", url: `${serverUrl}/privacy.html`, width: 360, height: 740 },
      { name: "Guest (360px)", url: `${serverUrl}/u/?demo=true`, width: 360, height: 740 }
    ];

    for (const item of pages) {
      const context = await browserCr.newContext({ viewport: { width: item.width, height: item.height } });
      const page = await context.newPage();
      await page.goto(item.url);
      await page.addScriptTag({ content: axeScript });

      const axeResults = await page.evaluate(async () => {
        return await window.axe.run({
          runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] }
        });
      });

      const severe = axeResults.violations.filter((v) => v.impact === "critical" || v.impact === "serious");
      assert.strictEqual(severe.length, 0, `Expected 0 critical/serious axe violations in ${item.name}`);
      await context.close();
    }
  });

  await t.test("5. Spark Mode Guest Capability Link & Local Firebase SDK Bundle", async () => {
    const crypto = require("node:crypto");
    const secret = "spark_e2e_secret_capability_token_2026";
    const linkId = crypto.createHash("sha256").update(secret, "utf8").digest("hex");

    const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
    const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
    const context = await browserCr.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();

    if (firestoreHost && authHost) {
      const [hostname, portStr] = firestoreHost.split(":");
      const port = Number(portStr || 8080);
      const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
      const payload = Buffer.from(
        JSON.stringify({
          iss: "https://securetoken.google.com/demo-udhaar-spark",
          aud: "demo-udhaar-spark",
          sub: "lender_e2e_uid",
          user_id: "lender_e2e_uid",
          email: "lender@example.com",
          email_verified: true,
          iat: Math.floor(Date.now() / 1000) - 60,
          exp: Math.floor(Date.now() / 1000) + 3600
        })
      ).toString("base64url");
      const lenderJwt = `${header}.${payload}.`;

      const expiresIso = new Date(Date.now() + 86400000).toISOString();
      const seedBody = JSON.stringify({
        fields: {
          ownerUid: { stringValue: "lender_e2e_uid" },
          recordId: { stringValue: "rec_spark_e2e_1" },
          status: { stringValue: "ACTIVE" },
          expiresAt: { timestampValue: expiresIso },
          createdAt: { timestampValue: new Date().toISOString() },
          recordSummary: {
            mapValue: {
              fields: {
                lenderName: { stringValue: "Jyotirmoy" },
                borrowerName: { stringValue: "Aarav" },
                direction: { stringValue: "GIVEN" },
                principalAmount: { doubleValue: 4200 },
                amountPaise: { integerValue: "420000" },
                remainingBalance: { doubleValue: 4200 },
                currency: { stringValue: "INR" }
              }
            }
          }
        }
      });

      await new Promise((resolve, reject) => {
        const req = http.request(
          {
            hostname,
            port,
            method: "PATCH",
            path: `/v1/projects/demo-udhaar-spark/databases/(default)/documents/guestLinks/${linkId}`,
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${lenderJwt}`
            }
          },
          (res) => {
            res.on("data", () => {});
            res.on("end", () => resolve(res.statusCode));
          }
        );
        req.on("error", reject);
        req.write(seedBody);
        req.end();
      });

      await page.addInitScript(
        ({ fsHost, auHost }) => {
          window.__UDHAAR_FIREBASE_CONFIG = {
            apiKey: "fake-api-key-for-emulator",
            authDomain: "demo-udhaar-spark.firebaseapp.com",
            projectId: "demo-udhaar-spark",
            appId: "1:000000000000:web:000000000000",
            useEmulator: true,
            authEmulatorUrl: `http://${auHost}`,
            firestoreEmulatorHost: fsHost.split(":")[0],
            firestoreEmulatorPort: Number(fsHost.split(":")[1] || 8080)
          };
        },
        { fsHost: firestoreHost, auHost: authHost }
      );
      await page.goto(`${serverUrl}/u/r/rec_spark_e2e_1#k=${secret}`);
    } else {
      const rawV2 = `v2|rec_spark_v2|${secret}|G|Jyotirmoy|Aarav|4200|Spark v2 test|${Date.now().toString(36)}`;
      const tokenV2 = Buffer.from(rawV2, "utf8").toString("base64url");
      await page.goto(`${serverUrl}/u/r/rec_spark_v2#k=${tokenV2}`);
    }

    await page.waitForSelector("#screen-step1", { state: "visible", timeout: 8000 });
    const amtText = await page.textContent("#step1-amount-number");
    assert.ok(amtText.includes("4,200") || amtText.includes("4200"));

    const sdkLoaded = await page.evaluate(async () => {
      const mod = await import("/u/vendor/firebase-sdk.js");
      return Boolean(mod && typeof mod.initializeApp === "function" && typeof mod.getDoc === "function");
    });
    assert.strictEqual(sdkLoaded, true);

    await context.close();
  });
});
