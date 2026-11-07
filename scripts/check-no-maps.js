#!/usr/bin/env node
/**
 * scripts/check-no-maps.js
 * 
 * Production Invariant Guard: Stage R1
 * Enforces that no module-level Map instances or ephemeral memory stores
 * exist in `functions/src/`. All persistence must use repositories, and
 * in-memory doubles must reside exclusively under `functions/test/`.
 */

const fs = require("fs");
const path = require("path");

const SRC_DIR = path.resolve(__dirname, "../functions/src");

function getJsFiles(dir) {
  let files = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files = files.concat(getJsFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".js")) {
      files.push(fullPath);
    }
  }
  return files;
}

const jsFiles = getJsFiles(SRC_DIR);
const violations = [];

const MAP_PATTERN = /\bnew\s+Map\s*\(/;

for (const file of jsFiles) {
  const content = fs.readFileSync(file, "utf8");
  const lines = content.split(/\r?\n/);
  
  lines.forEach((line, index) => {
    // Ignore comments
    const trimmed = line.trim();
    if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
      return;
    }
    if (MAP_PATTERN.test(line)) {
      violations.push({
        file: path.relative(path.resolve(__dirname, ".."), file),
        line: index + 1,
        code: trimmed
      });
    }
  });
}

console.log("======================================================");
console.log("  Udhaar Platform v2 — Real Persistence Audit (R1)   ");
console.log("======================================================");

if (violations.length > 0) {
  console.error(`❌ FAILED: Found ${violations.length} forbidden 'new Map()' declaration(s) in functions/src/:`);
  violations.forEach(v => {
    console.error(`  - ${v.file}:${v.line} -> ${v.code}`);
  });
  console.error("\nStage R1 requires all in-memory Map stores to reside in functions/test/ and be injected as repository doubles.");
  process.exit(1);
}

console.log(`✓ Verified ${jsFiles.length} source files in functions/src/`);
console.log("🎉 SUCCESS: Zero forbidden module-level 'new Map()' declarations found in functions/src/!\n");
process.exit(0);
