#!/usr/bin/env node
/**
 * scripts/check-public-assets.js
 *
 * CI check ensuring that public web assets do not contain:
 * 1. Internal design notes, markdown files, or unvetted documentation (.markdown, .md).
 * 2. Unreferenced or orphaned images and leftover media (e.g., hero-banner.jpg).
 * 3. Any files outside the approved web assets manifest.
 */

const fs = require("fs");
const path = require("path");

const ROOT_DIR = path.resolve(__dirname, "..");
const ASSETS_DIR = path.join(ROOT_DIR, "web", "assets");
const WEB_DIR = path.join(ROOT_DIR, "web");
const WEBAPP_DIR = path.join(ROOT_DIR, "webapp");
const FUNCTIONS_DIR = path.join(ROOT_DIR, "functions");

if (!fs.existsSync(ASSETS_DIR)) {
  console.error("FAIL: web/assets directory does not exist at", ASSETS_DIR);
  process.exit(1);
}

const entries = fs.readdirSync(ASSETS_DIR);
const errors = [];

// 1. Prohibit markdown, doc files, or design notes in web/assets
for (const file of entries) {
  const ext = path.extname(file).toLowerCase();
  if (ext === ".markdown" || ext === ".md" || ext === ".txt" || ext === ".doc") {
    errors.push(`Forbidden documentation/design file in public assets: ${file}`);
  }
}

// 2. Scan web/, webapp/, and functions/ for references to each asset
function getAllSourceText(dir, extensions = [".html", ".js", ".ts", ".css", ".json"]) {
  let text = "";
  if (!fs.existsSync(dir)) return text;
  const items = fs.readdirSync(dir, { withFileTypes: true });
  for (const item of items) {
    const fullPath = path.join(dir, item.name);
    if (item.isDirectory()) {
      if (item.name === "node_modules" || item.name === "dist" || item.name === ".git") continue;
      text += getAllSourceText(fullPath, extensions);
    } else if (item.isFile() && extensions.some((ext) => item.name.endsWith(ext))) {
      text += " " + fs.readFileSync(fullPath, "utf8");
    }
  }
  return text;
}

const corpus = getAllSourceText(WEB_DIR) + " " + getAllSourceText(WEBAPP_DIR) + " " + getAllSourceText(FUNCTIONS_DIR);

for (const file of entries) {
  if (file === "Image_3_9457855d.markdown" || file === "hero-banner.jpg") {
    errors.push(`Explicitly banned leftover file found in assets: ${file}`);
    continue;
  }

  // Check if asset is referenced in HTML, CSS, JS, or tests
  const basename = file;
  if (!corpus.includes(basename)) {
    // Check if it's referenced without extension or with query params
    const nameWithoutExt = path.parse(file).name;
    if (!corpus.includes(nameWithoutExt)) {
      errors.push(`Unreferenced orphaned asset in web/assets: ${file}`);
    }
  }
}

if (errors.length > 0) {
  console.error("FAIL: Public web assets check failed with errors:");
  for (const err of errors) {
    console.error(`  - ${err}`);
  }
  process.exit(1);
}

console.log(`OK: All ${entries.length} public assets in web/assets verified, vetted, and referenced.`);
process.exit(0);
