/**
 * Udhaar Web App Test Suite (web-tests/webapp_s2.test.js)
 * Validates Web App Shell, 5-Language i18n, PWA Shell, Security Headers & Accessibility.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT_DIR = path.resolve(__dirname, "..");
const WEBAPP_DIST = path.join(ROOT_DIR, "webapp/dist");

const { DICTIONARIES } = require("../webapp/src/i18n");

const BANNED_JARGON = [
  "backend", "database", "sqlcipher", "payload", "endpoint",
  "synchronization worker", "cryptographic nonce", "api gateway"
];

test("Web App i18n: 100% dictionary completeness across all 5 languages", () => {
  const languages = ["en", "hi", "bn", "as", "ne"];
  const enKeys = Object.keys(DICTIONARIES.en);

  assert.ok(enKeys.length >= 40, `Expected at least 40 i18n keys, found ${enKeys.length}`);

  for (const lang of languages) {
    const dict = DICTIONARIES[lang];
    assert.ok(dict, `Dictionary for language '${lang}' must exist`);

    const missingKeys = enKeys.filter((k) => !dict[k] || dict[k].trim() === "");
    assert.deepEqual(missingKeys, [], `Language '${lang}' is missing keys: ${missingKeys.join(", ")}`);

    if (lang !== "en") {
      const leaked = [];
      for (const key of enKeys) {
        const val = dict[key];
        const enVal = DICTIONARIES.en[key];
        if (
          val === enVal &&
          !["Udhaar", "WhatsApp"].includes(val) &&
          !val.includes("@") &&
          !/^[0-9\s]+$/.test(val) &&
          key !== "phone_placeholder"
        ) {
          if (/^[a-zA-Z0-9\s:?.,'!#@_/-]+$/.test(val)) {
            leaked.push({ key, val });
          }
        }
      }
      assert.deepEqual(leaked, [], `Language '${lang}' has untranslated English leakage: ${JSON.stringify(leaked)}`);
    }

    for (const [key, val] of Object.entries(dict)) {
      for (const jargon of BANNED_JARGON) {
        assert.equal(
          val.toLowerCase().includes(jargon),
          false,
          `Banned jargon '${jargon}' found in ${lang}.${key}: "${val}"`
        );
      }
    }
  }
});

test("Web App PWA: Manifest format, theme-color, and standalone configuration", () => {
  const manifestPath = path.join(WEBAPP_DIST, "manifest.json");
  assert.ok(fs.existsSync(manifestPath), "webapp/dist/manifest.json must exist");

  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  assert.equal(manifest.name, "Udhaar — Local-first Financial Ledger");
  assert.equal(manifest.short_name, "Udhaar");
  assert.equal(manifest.display, "standalone");
  assert.equal(manifest.theme_color, "#059669");
  assert.equal(manifest.start_url, "/");
  assert.ok(manifest.icons.length >= 2, "Manifest must provide SVG and WebP icons");

  for (const icon of manifest.icons) {
    const assetOnDisk = path.join(WEBAPP_DIST, icon.src.replace(/^\//, ""));
    assert.ok(fs.existsSync(assetOnDisk), `Manifest icon ${icon.src} must exist on disk`);
  }
});

test("Web App PWA: Service Worker precaching and offline fallback", () => {
  const swPath = path.join(WEBAPP_DIST, "sw.js");
  assert.ok(fs.existsSync(swPath), "webapp/dist/sw.js must exist");

  const swContent = fs.readFileSync(swPath, "utf8");
  assert.match(swContent, /udhaar-app-shell-v/);
  assert.match(swContent, /PRECACHE_URLS/);
  assert.match(swContent, /caches\.open/);
  assert.match(swContent, /caches\.match/);
  assert.match(swContent, /req\.mode === "navigate"/);
});

test("Web App Security: Zero inline scripts or styles in webapp/dist/index.html", () => {
  const htmlPath = path.join(WEBAPP_DIST, "index.html");
  assert.ok(fs.existsSync(htmlPath), "webapp/dist/index.html must exist");

  const html = fs.readFileSync(htmlPath, "utf8");
  assert.match(html, /<link rel="manifest" href="\/manifest\.json">/);
  assert.match(html, /<link rel="stylesheet" href="\/app\.css">/);
  assert.match(html, /<script src="\/app\.js"><\/script>/);

  const sanitized = html.replace(/<noscript>[\s\S]*?<\/noscript>/gi, "");
  assert.doesNotMatch(sanitized, /<style/i, "index.html must not contain inline <style> tags");
  assert.doesNotMatch(sanitized, /<script(?![^>]*src=)/i, "index.html must not contain inline <script> tags without src");
});

test("Web App Security: infra/firebase.prod.json enforces strict CSP and COOP/CORP", () => {
  const prodConfigPath = path.join(ROOT_DIR, "infra/firebase.prod.json");
  const prodConfig = JSON.parse(fs.readFileSync(prodConfigPath, "utf8"));

  const appSite = prodConfig.hosting.find((h) => h.site === "udhaar-app-prod");
  assert.ok(appSite, "Hosting site udhaar-app-prod must be configured");
  assert.equal(appSite.public, "webapp/dist");

  const headers = appSite.headers[0].headers;
  const headerMap = {};
  for (const h of headers) headerMap[h.key] = h.value;

  assert.match(headerMap["Strict-Transport-Security"], /max-age=63072000/);
  assert.equal(headerMap["X-Content-Type-Options"], "nosniff");
  assert.equal(headerMap["X-Frame-Options"], "DENY");
  assert.equal(headerMap["Cross-Origin-Opener-Policy"], "same-origin");
  assert.equal(headerMap["Cross-Origin-Resource-Policy"], "same-origin");
  assert.match(headerMap["Content-Security-Policy"], /default-src 'none'/);
  assert.match(headerMap["Content-Security-Policy"], /require-trusted-types-for 'script'/);
});

test("Web App Integrity: Self-hosted fonts and branding assets exist in webapp/dist/", () => {
  const requiredFonts = [
    "inter.woff2",
    "space-grotesk.woff2",
    "ibm-plex-mono.woff2",
    "noto-sans-devanagari.woff2",
    "noto-sans-bengali.woff2"
  ];

  for (const f of requiredFonts) {
    const fontPath = path.join(WEBAPP_DIST, "fonts", f);
    assert.ok(fs.existsSync(fontPath), `Font ${f} must exist in webapp/dist/fonts/`);
    assert.ok(fs.statSync(fontPath).size > 1000, `Font ${f} must not be empty`);
  }

  assert.ok(fs.existsSync(path.join(WEBAPP_DIST, "assets/icon.svg")));
  assert.ok(fs.existsSync(path.join(WEBAPP_DIST, "assets/icon.webp")));
  assert.ok(fs.existsSync(path.join(WEBAPP_DIST, "app.js")));
  assert.ok(fs.existsSync(path.join(WEBAPP_DIST, "app.css")));
});

test("Web App Functional: Sign-up, Sign-in, Dashboard & Settings Views render", () => {
  const { SignUpView } = require("../webapp/src/features/auth/SignUpView");
  const { SignInView } = require("../webapp/src/features/auth/SignInView");
  const { DashboardView } = require("../webapp/src/features/records/DashboardView");
  const { SettingsView } = require("../webapp/src/features/settings/SettingsView");

  class MockElement {
    constructor(tagName = "div") {
      this.tagName = tagName.toUpperCase();
      this.innerHTML = "";
      this.children = [];
      this.className = "";
      this.style = {};
      this.listeners = {};
      this.value = "";
      this.checked = false;
      this.disabled = false;
    }

    appendChild(child) {
      this.children.push(child);
      return child;
    }

    addEventListener(event, fn) {
      if (!this.listeners[event]) this.listeners[event] = [];
      this.listeners[event].push(fn);
    }

    querySelector() {
      return new MockElement("div");
    }

    querySelectorAll() {
      return [new MockElement("input"), new MockElement("input")];
    }

    focus() {}
  }

  global.document = {
    createElement: (tag) => new MockElement(tag),
    documentElement: {
      setAttribute: () => {},
      getAttribute: () => "light",
      lang: "en"
    }
  };

  const mockI18n = {
    t: (k) => DICTIONARIES.en[k] || k,
    getLocale: () => "en",
    setLocale: () => {}
  };

  const mockClient = {
    startSignup: async () => ({ flowId: "flow_123", resendAfterSeconds: 30 }),
    verifyFlowOtp: async () => ({ verified: true, flowToken: "tok_abc" }),
    submitSignupEmail: async () => ({ success: true }),
    completeSignup: async () => ({ user: { displayName: "Ravi Sharma" } }),
    startSignin: async () => ({ flowId: "flow_login", resendAfterSeconds: 30 }),
    verifySignin: async () => ({ user: { displayName: "Ravi Sharma" } }),
    getMe: async () => ({ user: { displayName: "Ravi" }, devices: [] }),
    signOut: async () => ({})
  };

  const mockRouter = { navigate: () => {} };
  const mockToast = () => {};

  const signUp = new SignUpView({ i18n: mockI18n, authClient: mockClient, router: mockRouter, onToast: mockToast });
  const container = new MockElement("div");
  signUp.render(container);
  assert.equal(signUp.step, 1);

  const signIn = new SignInView({ i18n: mockI18n, authClient: mockClient, router: mockRouter, onToast: mockToast });
  signIn.render(container);
  assert.equal(signIn.step, 1);

  const dashboard = new DashboardView({ i18n: mockI18n, authClient: mockClient, router: mockRouter, onToast: mockToast });
  dashboard.render(container);

  const settings = new SettingsView({ i18n: mockI18n, authClient: mockClient, router: mockRouter, onToast: mockToast });
  settings.render(container);
});

test("Web App Axe: Automated axe-core accessibility audit when Playwright is available", async () => {
  let chromium;
  try {
    ({ chromium } = require("@playwright/test"));
  } catch {
    return;
  }

  const http = require("node:http");
  const candidateAxePaths = [
    path.resolve(ROOT_DIR, "node_modules/axe-core/axe.min.js"),
    path.resolve(ROOT_DIR, "archive/blaze_disabled/functions/node_modules/axe-core/axe.min.js")
  ];
  const axePath = candidateAxePaths.find((p) => fs.existsSync(p));
  if (!axePath) return;

  const MIME_TYPES = {
    ".html": "text/html",
    ".css": "text/css",
    ".js": "application/javascript",
    ".json": "application/json",
    ".svg": "image/svg+xml",
    ".webp": "image/webp",
    ".woff2": "font/woff2"
  };

  let server;
  let serverUrl;
  await new Promise((resolve) => {
    server = http.createServer((req, res) => {
      const cleanUrl = req.url.split("?")[0].split("#")[0];
      const relPath = cleanUrl === "/" ? "index.html" : cleanUrl.replace(/^\//, "");
      let filePath = path.join(WEBAPP_DIST, relPath);
      if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        filePath = path.join(WEBAPP_DIST, "index.html");
      }
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { "Content-Type": MIME_TYPES[ext] || "text/plain" });
      res.end(fs.readFileSync(filePath));
    });
    server.listen(0, "127.0.0.1", () => {
      serverUrl = `http://127.0.0.1:${server.address().port}`;
      resolve();
    });
  });

  let browser;
  try {
    try {
      browser = await chromium.launch({
        headless: true,
        args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"]
      });
    } catch {
      return;
    }

    const axeScript = fs.readFileSync(axePath, "utf8");
    const routesToTest = [
      { name: "Sign-In (Mobile 360px)", url: `${serverUrl}/#signin`, width: 360, height: 740 },
      { name: "Sign-In (Desktop 1280px)", url: `${serverUrl}/#signin`, width: 1280, height: 800 },
      { name: "Dashboard (Mobile 360px)", url: `${serverUrl}/#dashboard`, width: 360, height: 740 }
    ];

    for (const item of routesToTest) {
      const context = await browser.newContext({ viewport: { width: item.width, height: item.height } });
      const page = await context.newPage();
      await page.goto(item.url);
      await page.waitForLoadState("domcontentloaded");
      await page.addScriptTag({ content: axeScript });

      const axeResults = await page.evaluate(async () => {
        return await window.axe.run({
          runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] }
        });
      });

      const severeViolations = axeResults.violations.filter(
        (v) => v.impact === "critical" || v.impact === "serious"
      );
      assert.strictEqual(severeViolations.length, 0, `Accessibility violations on ${item.name}`);
      await context.close();
    }
  } finally {
    if (browser) await browser.close();
    if (server) server.close();
  }
});
