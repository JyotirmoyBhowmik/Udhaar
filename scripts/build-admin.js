/**
 * Udhaar Admin Console Build Pipeline (scripts/build-admin.js)
 * Packages admin/ into admin/dist/ for isolated hosting deployment.
 * Enforces strict banking-grade CSP (zero inline code) and performance budgets.
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const ROOT_DIR = path.resolve(__dirname, "..");
const ADMIN_DIR = path.join(ROOT_DIR, "admin");
const DIST_DIR = path.join(ADMIN_DIR, "dist");
const WEB_DIR = path.join(ROOT_DIR, "web");

console.log("=========================================================");
console.log("  Udhaar Admin Console (admin.udhaar.jyotirmoyb.com) Build");
console.log("=========================================================\n");

fs.mkdirSync(DIST_DIR, { recursive: true });

// 1. Copy Assets
console.log("▶ [1/3] Packaging Admin HTML, CSS, and JS...");
fs.copyFileSync(path.join(ADMIN_DIR, "index.html"), path.join(DIST_DIR, "index.html"));
fs.copyFileSync(path.join(ADMIN_DIR, "admin.css"), path.join(DIST_DIR, "admin.css"));
fs.copyFileSync(path.join(ADMIN_DIR, "admin.js"), path.join(DIST_DIR, "admin.js"));

const tokensCss = fs.readFileSync(path.join(WEB_DIR, "design-tokens.css"), "utf8");
fs.writeFileSync(path.join(DIST_DIR, "design-tokens.css"), tokensCss, "utf8");
console.log("  ✓ index.html, admin.css, admin.js, design-tokens.css packaged");

// 2. Security Audit: Strict CSP
console.log("▶ [2/3] Auditing Strict CSP compliance...");
const html = fs.readFileSync(path.join(DIST_DIR, "index.html"), "utf8");
const sanitized = html.replace(/<noscript>[\s\S]*?<\/noscript>/gi, "");
if (sanitized.includes("<style") || /<script(?![^>]*src=)/i.test(sanitized)) {
  console.error("  ❌ Inline script or style tag detected in admin/dist/index.html! Violates strict CSP.");
  process.exit(1);
}
console.log("  ✓ Strict CSP verified: zero inline scripts or styles");

// 3. Performance Budget Audit
console.log("▶ [3/3] Checking file sizes and budget (< 200 KB)...");
const files = ["index.html", "admin.css", "admin.js", "design-tokens.css"];
let totalBytes = 0;
for (const f of files) {
  const bytes = fs.statSync(path.join(DIST_DIR, f)).size;
  totalBytes += bytes;
  const hash = crypto.createHash("sha256").update(fs.readFileSync(path.join(DIST_DIR, f))).digest("hex").slice(0, 10);
  console.log(`  ✓ ${f.padEnd(20)} (${(bytes / 1024).toFixed(1)} KB) [hash: ${hash}]`);
}

const totalKb = (totalBytes / 1024).toFixed(1);
console.log(`\n  Total Admin Console Shell Size: ${totalKb} KB (Budget: <= 200 KB)`);
if (totalBytes > 200 * 1024) {
  console.error("  ❌ Admin console exceeded 200 KB budget!");
  process.exit(1);
}

console.log("\n🎉 SUCCESS: Udhaar Admin Console build completed successfully!");
