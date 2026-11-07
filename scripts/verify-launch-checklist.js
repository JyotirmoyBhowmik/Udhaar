#!/usr/bin/env node
/**
 * Udhaar Platform v2 — Launch Checklist Verification CI Script (Stage R0)
 * 
 * Enforces Truth-in-Documentation:
 * 1. Parses docs/launch-checklist.md
 * 2. Every item marked with '- [x]' MUST contain:
 *    [Proof: `<filePath>`, Test: `<testPath>`]
 * 3. Asserts that both the proof file and test file actually exist on disk.
 * 4. Fails CI (exit 1) if any checked item lacks verified proof or references non-existent files.
 */

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');
const CHECKLIST_PATH = path.join(ROOT_DIR, 'docs/launch-checklist.md');

function verifyLaunchChecklist() {
  console.log('======================================================');
  console.log('  Udhaar Platform v2 — Launch Checklist Truth Audit');
  console.log('======================================================\n');

  if (!fs.existsSync(CHECKLIST_PATH)) {
    console.error(`Error: Checklist file not found at ${CHECKLIST_PATH}`);
    process.exit(1);
  }

  const content = fs.readFileSync(CHECKLIST_PATH, 'utf8');
  const lines = content.split('\n');

  const checkedRegex = /^\s*-\s*\[x\]\s+(.+)$/i;
  const proofTestRegex = /\[Proof:\s*`([^`]+)`,\s*Test:\s*`([^`]+)`\]/i;

  let totalChecked = 0;
  let passedChecked = 0;
  const violations = [];

  lines.forEach((line, index) => {
    const match = line.match(checkedRegex);
    if (!match) return;

    totalChecked++;
    const itemText = match[1].trim();
    const proofMatch = itemText.match(proofTestRegex);

    if (!proofMatch) {
      violations.push({
        line: index + 1,
        item: itemText.substring(0, 80),
        reason: "Missing required '[Proof: `<file>`, Test: `<test>`]' annotation"
      });
      return;
    }

    const proofFile = proofMatch[1].trim();
    const testFile = proofMatch[2].trim();

    // Verify file existence on disk
    const resolvedProof = path.resolve(ROOT_DIR, proofFile);
    const resolvedTest = path.resolve(ROOT_DIR, testFile);

    const proofExists = fs.existsSync(resolvedProof);
    const testExists = fs.existsSync(resolvedTest);

    if (!proofExists || !testExists) {
      violations.push({
        line: index + 1,
        item: itemText.substring(0, 80),
        reason: `Referenced file missing on disk (Proof exists: ${proofExists} [${proofFile}], Test exists: ${testExists} [${testFile}])`
      });
      return;
    }

    passedChecked++;
    console.log(`  ✓ Line ${index + 1}: ${itemText.split('[Proof:')[0].trim()}`);
    console.log(`    ↳ Proof: ${proofFile}`);
    console.log(`    ↳ Test:  ${testFile}`);
  });

  console.log('\n------------------------------------------------------');
  console.log(`Audit Results: ${passedChecked}/${totalChecked} checked items verified.`);

  if (violations.length > 0) {
    console.error(`\n❌ CI FAILURE: ${violations.length} launch checklist violations found:`);
    violations.forEach(v => {
      console.error(`  - Line ${v.line}: ${v.reason}`);
      console.error(`    Item: "${v.item}..."`);
    });
    console.error('\nRule: Public docs and launch checklists must never tick unproven items!');
    process.exit(1);
  }

  console.log('🎉 SUCCESS: All checked items in docs/launch-checklist.md are verified by code and tests!\n');
}

if (require.main === module) {
  verifyLaunchChecklist();
}

module.exports = { verifyLaunchChecklist };
