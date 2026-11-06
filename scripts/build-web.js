/**
 * Udhaar Web Build & Bundler Script (scripts/build-web.js)
 * Validates web modular assets, ensures strict CSP and byte budgets,
 * computes content hashes for immutable asset caching, and verifies bundle integrity.
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const ROOT_DIR = path.resolve(__dirname, "..");
const WEB_DIR = path.join(ROOT_DIR, "web");

console.log("=========================================================");
console.log("  Udhaar Web Production Build & Asset Integrity Pipeline ");
console.log("=========================================================\n");

const CRITICAL_FILES = [
  "index.html",
  "landing.css",
  "landing.js",
  "calculator.js",
  "site-config.js",
  "design-tokens.css",
  "manifest.json",
  "sw.js",
  "privacy.html",
  "privacy.css",
  "privacy.js",
  "404.html",
  "u/index.html",
  "u/guest.css",
  "u/guest.js"
];

let hasErrors = false;
let totalLandingBytes = 0;
let totalGuestBytes = 0;

console.log("▶ [1/4] Checking file integrity and computing content hashes...");
const hashes = {};

for (const relPath of CRITICAL_FILES) {
  const fullPath = path.join(WEB_DIR, relPath);
  if (!fs.existsSync(fullPath)) {
    console.error(`  ❌ Missing required web asset: ${relPath}`);
    hasErrors = true;
    continue;
  }

  const content = fs.readFileSync(fullPath);
  const hash = crypto.createHash("sha256").update(content).digest("hex").slice(0, 10);
  hashes[relPath] = hash;

  const sizeKb = (content.length / 1024).toFixed(1);
  console.log(`  ✓ ${relPath.padEnd(22)} (${sizeKb} KB) [hash: ${hash}]`);

  // Accumulate landing page weight
  if (["index.html", "landing.css", "landing.js", "calculator.js", "site-config.js", "design-tokens.css"].includes(relPath)) {
    totalLandingBytes += content.length;
  }

  // Accumulate guest page weight
  if (["u/index.html", "u/guest.css", "u/guest.js", "design-tokens.css"].includes(relPath)) {
    totalGuestBytes += content.length;
  }
}

console.log("\n▶ [2/4] Enforcing Performance Weight Budgets...");
const LANDING_LIMIT = 600 * 1024; // 600 KB
const GUEST_LIMIT = 250 * 1024;   // 250 KB

const landingKb = (totalLandingBytes / 1024).toFixed(1);
const guestKb = (totalGuestBytes / 1024).toFixed(1);

if (totalLandingBytes <= LANDING_LIMIT) {
  console.log(`  ✓ Landing page weight: ${landingKb} KB (Budget: <= 600 KB)`);
} else {
  console.error(`  ❌ Landing page weight EXCEEDED: ${landingKb} KB > 600 KB`);
  hasErrors = true;
}

if (totalGuestBytes <= GUEST_LIMIT) {
  console.log(`  ✓ Guest portal weight: ${guestKb} KB (Budget: <= 250 KB)`);
} else {
  console.error(`  ❌ Guest portal weight EXCEEDED: ${guestKb} KB > 250 KB`);
  hasErrors = true;
}

console.log("\n▶ [3/4] Verifying Strict Content-Security Policy & Zero Inline Code...");
const htmlFiles = ["index.html", "privacy.html", "404.html", "u/index.html", "trust/index.html", "updates/index.html", "ledger/index.html"];

for (const relHtml of htmlFiles) {
  const fullHtmlPath = path.join(WEB_DIR, relHtml);
  if (!fs.existsSync(fullHtmlPath)) continue;
  const htmlContent = fs.readFileSync(fullHtmlPath, "utf8");

  // Check for disallowed inline styles
  const inlineStyles = htmlContent.match(/<style[\s\S]*?<\/style>/gi);
  if (inlineStyles && inlineStyles.length > 0) {
    console.error(`  ❌ Disallowed inline <style> found in ${relHtml}`);
    hasErrors = true;
  } else {
    console.log(`  ✓ ${relHtml}: zero inline <style> blocks`);
  }

  // Check for disallowed inline scripts (excluding JSON-LD data block)
  const scripts = htmlContent.match(/<script(?![^>]*type=["']application\/ld\+json["'])[\s\S]*?<\/script>/gi);
  if (scripts) {
    const inlineNonSrc = scripts.filter((s) => !/src=["']/i.test(s));
    if (inlineNonSrc.length > 0) {
      console.error(`  ❌ Disallowed inline <script> found in ${relHtml}`);
      hasErrors = true;
    }
  }
}

console.log("\n▶ [4/4] Writing Build Metadata Manifest (web/dist-manifest.json)...");
const manifestPath = path.join(WEB_DIR, "dist-manifest.json");
fs.writeFileSync(manifestPath, JSON.stringify({
  version: "21.0.0",
  timestamp: new Date().toISOString(),
  budgets: {
    landingKb: parseFloat(landingKb),
    guestKb: parseFloat(guestKb)
  },
  hashes
}, null, 2), "utf8");
console.log("  ✓ Generated web/dist-manifest.json");

if (hasErrors) {
  console.error("\n❌ Build failed with errors.");
  process.exit(1);
} else {
  console.log("\n🎉 SUCCESS: Web production build passed all performance, security, and budget audits!");
}
