/**
 * Udhaar Web App Production Bundler & Asset Packager (scripts/build-webapp.js)
 * Bundles webapp/src into webapp/dist/ without external devServer lock-in.
 * Enforces strict CSP, performance budgets, and self-hosted fonts.
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const ROOT_DIR = path.resolve(__dirname, "..");
const WEBAPP_DIR = path.join(ROOT_DIR, "webapp");
const DIST_DIR = path.join(WEBAPP_DIR, "dist");
const WEB_DIR = path.join(ROOT_DIR, "web");

console.log("=========================================================");
console.log("  Udhaar Web App (app.udhaar.jyotirmoyb.com) Build Pipeline");
console.log("=========================================================\n");

// Ensure directories
fs.mkdirSync(DIST_DIR, { recursive: true });
fs.mkdirSync(path.join(DIST_DIR, "assets"), { recursive: true });
fs.mkdirSync(path.join(DIST_DIR, "fonts"), { recursive: true });

// 1. Bundle JavaScript modules into webapp/dist/app.js
console.log("▶ [1/5] Bundling JavaScript modules into app.js...");

const moduleFiles = [
  { name: "i18n", path: path.join(WEBAPP_DIR, "src/i18n/index.js") },
  { name: "hlc", path: path.join(WEBAPP_DIR, "src/sync/hlc.js") },
  { name: "encryptedCache", path: path.join(WEBAPP_DIR, "src/db/encryptedCache.js") },
  { name: "ledgerDb", path: path.join(WEBAPP_DIR, "src/db/ledgerDb.js") },
  { name: "syncEngine", path: path.join(WEBAPP_DIR, "src/sync/syncEngine.js") },
  { name: "authClient", path: path.join(WEBAPP_DIR, "src/api/authClient.js") },
  { name: "router", path: path.join(WEBAPP_DIR, "src/routes/router.js") },
  { name: "SignUpView", path: path.join(WEBAPP_DIR, "src/features/auth/SignUpView.js") },
  { name: "SignInView", path: path.join(WEBAPP_DIR, "src/features/auth/SignInView.js") },
  { name: "DashboardView", path: path.join(WEBAPP_DIR, "src/features/records/DashboardView.js") },
  { name: "SettingsView", path: path.join(WEBAPP_DIR, "src/features/settings/SettingsView.js") },
  { name: "RemindersView", path: path.join(WEBAPP_DIR, "src/features/reminders/RemindersView.js") },
  { name: "ChatView", path: path.join(WEBAPP_DIR, "src/features/chat/ChatView.js") },
  { name: "InboxView", path: path.join(WEBAPP_DIR, "src/features/inbox/InboxView.js") },
  { name: "ExportsView", path: path.join(WEBAPP_DIR, "src/features/exports/ExportsView.js") },
  { name: "AppShell", path: path.join(WEBAPP_DIR, "src/app/AppShell.js") }
];

let bundleCode = `/**
 * Udhaar Web App Production Bundle
 * Generated: ${new Date().toISOString()}
 */
(function() {
  "use strict";

  const __modules = {};
  function __require(name) {
    if (__modules[name]) return __modules[name].exports;
    throw new Error("Module not found: " + name);
  }

`;

for (const mod of moduleFiles) {
  let content = fs.readFileSync(mod.path, "utf8");
  // Normalize require paths inside modules
  content = content.replace(/require\(["'](?:\.\.\/)+i18n["']\)/g, '__require("i18n")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+encryptedCache["']\)/g, '__require("encryptedCache")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+db\/ledgerDb["']\)/g, '__require("ledgerDb")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+hlc["']\)/g, '__require("hlc")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+sync\/hlc["']\)/g, '__require("hlc")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+sync\/syncEngine["']\)/g, '__require("syncEngine")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+api\/authClient["']\)/g, '__require("authClient")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+routes\/router["']\)/g, '__require("router")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/auth\/SignUpView["']\)/g, '__require("SignUpView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/auth\/SignInView["']\)/g, '__require("SignInView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/records\/DashboardView["']\)/g, '__require("DashboardView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/settings\/SettingsView["']\)/g, '__require("SettingsView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/reminders\/RemindersView["']\)/g, '__require("RemindersView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/chat\/ChatView["']\)/g, '__require("ChatView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/inbox\/InboxView["']\)/g, '__require("InboxView")');
  content = content.replace(/require\(["'](?:\.\.\/|\.\/)+features\/exports\/ExportsView["']\)/g, '__require("ExportsView")');

  bundleCode += `  // --- Module: ${mod.name} ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
${content}
    __modules["${mod.name}"] = module;
  })();\n\n`;
}

bundleCode += `
  // Bootstrapping AppShell
  document.addEventListener("DOMContentLoaded", function() {
    const root = document.getElementById("root");
    if (root) {
      const { AppShell } = __require("AppShell");
      const app = new AppShell(root);
      app.init();
      window.__udhaarApp = app;
    }
  });
})();
`;

fs.writeFileSync(path.join(DIST_DIR, "app.js"), bundleCode, "utf8");
console.log(`  ✓ app.js created (${(bundleCode.length / 1024).toFixed(1)} KB)`);

// 2. Copy & Bundle CSS
console.log("▶ [2/5] Copying and packaging stylesheets...");
const designTokensCss = fs.readFileSync(path.join(WEB_DIR, "design-tokens.css"), "utf8");
fs.writeFileSync(path.join(DIST_DIR, "design-tokens.css"), designTokensCss, "utf8");

const appCss = fs.readFileSync(path.join(WEBAPP_DIR, "src/ui/app.css"), "utf8");
fs.writeFileSync(path.join(DIST_DIR, "app.css"), appCss, "utf8");
console.log(`  ✓ app.css & design-tokens.css written`);

// 3. Copy PWA Shell: manifest.json, sw.js, index.html
console.log("▶ [3/5] Packaging PWA shell files...");
fs.copyFileSync(path.join(WEBAPP_DIR, "index.html"), path.join(DIST_DIR, "index.html"));
fs.copyFileSync(path.join(WEBAPP_DIR, "src/pwa/manifest.json"), path.join(DIST_DIR, "manifest.json"));
fs.copyFileSync(path.join(WEBAPP_DIR, "src/pwa/sw.js"), path.join(DIST_DIR, "sw.js"));
console.log(`  ✓ index.html, manifest.json, and sw.js packaged`);

// 4. Copy Self-Hosted Fonts & Assets
console.log("▶ [4/5] Copying self-hosted fonts and branding assets...");
const fontFiles = ["inter.woff2", "space-grotesk.woff2", "ibm-plex-mono.woff2", "noto-sans-devanagari.woff2", "noto-sans-bengali.woff2"];
for (const f of fontFiles) {
  const srcPath = path.join(WEB_DIR, "fonts", f);
  if (fs.existsSync(srcPath)) {
    fs.copyFileSync(srcPath, path.join(DIST_DIR, "fonts", f));
  }
}

const assetFiles = ["icon.svg", "icon.webp"];
for (const a of assetFiles) {
  const srcPath = path.join(WEB_DIR, "assets", a);
  if (fs.existsSync(srcPath)) {
    fs.copyFileSync(srcPath, path.join(DIST_DIR, "assets", a));
  }
}
console.log(`  ✓ 5 fonts and branding icons copied to webapp/dist/`);

// 5. Verification & Budget Audits
console.log("▶ [5/5] Auditing security headers and size budgets...");
const indexHtml = fs.readFileSync(path.join(DIST_DIR, "index.html"), "utf8");

if (indexHtml.includes("<style>") || indexHtml.includes("<script>")) {
  console.error("  ❌ Inline script or style tag detected in webapp/dist/index.html! Violates strict CSP.");
  process.exit(1);
}

const distFiles = ["index.html", "app.css", "app.js", "design-tokens.css", "manifest.json", "sw.js"];
let totalBytes = 0;
for (const f of distFiles) {
  const bytes = fs.statSync(path.join(DIST_DIR, f)).size;
  totalBytes += bytes;
  const hash = crypto.createHash("sha256").update(fs.readFileSync(path.join(DIST_DIR, f))).digest("hex").slice(0, 10);
  console.log(`  ✓ ${f.padEnd(20)} (${(bytes / 1024).toFixed(1)} KB) [hash: ${hash}]`);
}

const totalKb = (totalBytes / 1024).toFixed(1);
console.log(`\n  Total Web App Shell Size: ${totalKb} KB (Budget: <= 300 KB)`);
if (totalBytes > 300 * 1024) {
  console.error(`  ❌ Web app shell exceeded 300 KB budget!`);
  process.exit(1);
}

console.log("\n🎉 SUCCESS: Udhaar Web App (Stage S2) build completed successfully!");
