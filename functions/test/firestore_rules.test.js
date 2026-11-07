const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const rulesPath = path.resolve(__dirname, "../../firestore.rules");
const storageRulesPath = path.resolve(__dirname, "../../storage.rules");
const storageLifecyclePath = path.resolve(__dirname, "../../storage.lifecycle.json");

const rulesText = fs.readFileSync(rulesPath, "utf8");
const storageRulesText = fs.readFileSync(storageRulesPath, "utf8");
const storageLifecycleJson = JSON.parse(fs.readFileSync(storageLifecyclePath, "utf8"));

test("firestore.rules enforces participant-only read/write on shared_records and prevents self-confirmation", () => {
  assert.match(rulesText, /match \/shared_records\/\{remoteId\}/);
  assert.match(rulesText, /request\.auth\.uid in existing\(\)\.participantUids/);
  assert.match(rulesText, /request\.auth\.uid != existing\(\)\.creatorUid/);
  assert.match(rulesText, /match \/audit_trail\/\{eventId\}/);
  assert.match(rulesText, /allow update:\s*if false/);
});

test("firestore.rules locks down /webLinks/{recordId} to Cloud Functions Admin SDK only", () => {
  assert.match(rulesText, /match \/webLinks\/\{recordId\}/);
  assert.match(rulesText, /allow read, write:\s*if false/);
});

test("firestore.rules locks down Platform v2 ledger collections and indexes (deny-by-default for clients)", () => {
  const deniedCollections = [
    "/phoneIndex/{document=**}",
    "/emailIndex/{document=**}",
    "/otpChallenges/{document=**}",
    "/rateLimits/{document=**}",
    "/auditLogs/{document=**}",
    "/users/{userId}/records/{document=**}",
    "/users/{userId}/people/{document=**}",
    "/users/{userId}/events/{document=**}",
    "/users/{userId}/plans/{document=**}",
    "/users/{userId}/attachments/{document=**}",
    "/users/{userId}/changeLog/{document=**}",
  ];

  for (const coll of deniedCollections) {
    const escaped = coll.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const pattern = new RegExp(`match\\s+${escaped}\\s*\\{\\s*allow\\s+read,\\s*write:\\s*if\\s+false;?\\s*\\}`);
    assert.match(rulesText, pattern, `Expected deny rule for ${coll}`);
  }
});

test("firestore.rules allows owner-only read on /users/{userId}/syncSignal/current and denies client writes", () => {
  assert.match(rulesText, /match \/users\/\{userId\}\/syncSignal\/current/);
  assert.match(rulesText, /allow read:\s*if isSignedIn\(\) && isValidId\(userId\) && request\.auth\.uid == userId;/);
  assert.match(rulesText, /allow write:\s*if false;/);
});

test("storage.rules sets allow create: if false for clients so uploads happen only via signed URLs or Admin SDK", () => {
  assert.match(storageRulesText, /match \/payment_proofs\/\{recordId\}\/\{fileName\}/);
  assert.match(storageRulesText, /match \/guest_proofs\/\{recordId\}\/\{fileName\}/);
  const createMatches = [...storageRulesText.matchAll(/allow\s+create\s*:\s*if\s+([^;]+);/g)];
  assert.ok(createMatches.length >= 2, "Expected allow create rules for payment_proofs and guest_proofs");
  for (const match of createMatches) {
    assert.equal(match[1].trim(), "false", `Expected allow create to be false, got: ${match[0]}`);
  }
  assert.match(storageRulesText, /allow update, delete:\s*if false/);
});

test("storage.lifecycle.json configures a 90-day Delete lifecycle rule for proof uploads", () => {
  assert.ok(storageLifecycleJson.lifecycle && Array.isArray(storageLifecycleJson.lifecycle.rule));
  const deleteRule = storageLifecycleJson.lifecycle.rule.find(
    (r) => r.action && r.action.type === "Delete"
  );
  assert.ok(deleteRule, "Expected a Delete lifecycle rule");
  assert.equal(deleteRule.condition.age, 90);
  assert.deepEqual(deleteRule.condition.matchesPrefix, ["guest_proofs/", "payment_proofs/"]);
});
