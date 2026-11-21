const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  computeCanaryBucket,
  testPopulationDistribution,
  executeRollback
} = require("../scripts/rollback.js");

const ROOT_DIR = path.resolve(__dirname, "..");

test("Release & Rollout: Canary Routing & Distribution Integrity", async (t) => {
  await t.test("1. Deterministic Canary Bucket allocation", () => {
    const userId1 = "usr_98a76b54_mumbai";
    const res1 = computeCanaryBucket(userId1, 5);
    const res2 = computeCanaryBucket(userId1, 5);
    assert.strictEqual(res1.bucket, res2.bucket);
    assert.strictEqual(res1.isCanary, res2.isCanary);
    assert.ok(res1.bucket >= 0 && res1.bucket < 100);
  });

  await t.test("2. Canary allocation distributes across 5%, 25%, 100%", () => {
    const sampleIds = Array.from({ length: 1000 }, (_, i) => `device_node_sim_${i}_salt`);
    const dist5 = testPopulationDistribution(sampleIds, 5);
    assert.ok(dist5.actualPercentage >= 3.0 && dist5.actualPercentage <= 7.0);

    const dist25 = testPopulationDistribution(sampleIds, 25);
    assert.ok(dist25.actualPercentage >= 20.0 && dist25.actualPercentage <= 30.0);

    const dist100 = testPopulationDistribution(sampleIds, 100);
    assert.strictEqual(dist100.canaryCount, 1000);
  });
});

test("Release & Rollout: Emergency Rollback Execution Engine", async () => {
  const report = await executeRollback({
    projectId: "udhaar-prod",
    targetVersion: "v29.0",
    reason: "Canary error rate spike > 1.2%"
  });

  assert.strictEqual(report.status, "SUCCESS");
  assert.strictEqual(report.action, "PRODUCTION_ROLLBACK_EXECUTED");
  assert.strictEqual(report.targetVersion, "v29.0");
  assert.ok(report.stepsCompleted.length >= 4);
  assert.ok(report.executionDurationMs < 1000);
  assert.ok(new Date(report.timestamp).getTime() > 0);
});

test("Release & Rollout: CI/CD Workflow Configurations (Spark Mode)", async (t) => {
  const ciPath = path.join(ROOT_DIR, ".github/workflows/ci.yml");
  const stagingPath = path.join(ROOT_DIR, ".github/workflows/deploy-staging.yml");
  const prodPath = path.join(ROOT_DIR, ".github/workflows/deploy-prod.yml");

  await t.test("1. CI workflow enforces Detekt, Rules Tests, Web Tests, Sync Conformance & Secret Scan", () => {
    assert.ok(fs.existsSync(ciPath), "ci.yml must exist");
    const content = fs.readFileSync(ciPath, "utf8");
    assert.ok(content.includes(":app:detekt"), "ci.yml must run Detekt");
    assert.ok(content.includes("rules-tests"), "ci.yml must run Firestore rules tests");
    assert.ok(content.includes("web-tests"), "ci.yml must run Spark web-tests");
    assert.ok(content.includes("sync-conformance/ts-runner.js"), "ci.yml must run sync conformance");
    assert.ok(content.includes("secret-scan"), "ci.yml must include secret scan");
  });

  await t.test("2. Staging deployment workflow exists", () => {
    assert.ok(fs.existsSync(stagingPath), "deploy-staging.yml must exist");
    const content = fs.readFileSync(stagingPath, "utf8");
    assert.ok(content.includes("udhaar-staging"));
  });

  await t.test("3. Production deployment workflow configures canary rollout, approval gate & rollback", () => {
    assert.ok(fs.existsSync(prodPath), "deploy-prod.yml must exist");
    const content = fs.readFileSync(prodPath, "utf8");
    assert.ok(content.includes("canary_percentage"));
    assert.ok(content.includes("environment:"));
    assert.ok(content.includes("rollback.js"));
  });
});

test("Release & Rollout: Operational Runbooks Completeness", async (t) => {
  const requiredRunbooks = [
    "bad-deploy-rollback.md",
    "data-breach.md",
    "whatsapp-outage.md",
    "email-provider-outage.md",
    "otp-abuse-spike.md",
    "leaked-provider-key.md",
    "incident-response.md",
    "emergency-kill-switches.md",
    "key-rotation.md",
    "cost-and-quotas.md"
  ];

  for (const rb of requiredRunbooks) {
    await t.test(`Runbook exists: ${rb}`, () => {
      const rbPath = path.join(ROOT_DIR, "docs/runbooks", rb);
      assert.ok(fs.existsSync(rbPath), `Runbook docs/runbooks/${rb} must exist`);
      const content = fs.readFileSync(rbPath, "utf8");
      assert.ok(content.length > 500, `Runbook ${rb} must have substantive content`);
    });
  }
});

test("Release & Rollout: Launch Checklist, Store Listing & Legal Specifications", async (t) => {
  await t.test("1. docs/launch-checklist.md covers all release gates", () => {
    const checklistPath = path.join(ROOT_DIR, "docs/launch-checklist.md");
    assert.ok(fs.existsSync(checklistPath));
    const content = fs.readFileSync(checklistPath, "utf8");
    assert.ok(content.includes("Cloud Infrastructure & Networking Gate"));
    assert.ok(content.includes("Identity, Authentication & Anti-Fraud Gate"));
    assert.ok(content.includes("Data Integrity & Firestore Security Rules Gate"));
    assert.ok(content.includes("Web Application & PWA Client Gate"));
    assert.ok(content.includes("Android Native Application Gate"));
    assert.ok(content.includes("Privacy & Legal Compliance Gate"));
    assert.ok(content.includes("Observability, Backups & SRE Gate"));
    assert.ok(content.includes("Staged Rollout Timeline & Sign-Off"));
  });

  await t.test("2. docs/store-listing-updates.md contains Data Safety declarations and 5-language notes", () => {
    const storePath = path.join(ROOT_DIR, "docs/store-listing-updates.md");
    assert.ok(fs.existsSync(storePath));
    const content = fs.readFileSync(storePath, "utf8");
    assert.ok(content.includes("Data Safety Declarations"));
    assert.ok(content.includes("English (en-IN, en-US)"));
    assert.ok(content.includes("Hindi (hi-IN)"));
    assert.ok(content.includes("Bengali (bn-IN, bn-BD)"));
    assert.ok(content.includes("Assamese (as-IN)"));
    assert.ok(content.includes("Nepali (ne-NP)"));
  });

  await t.test("3. docs/legal-and-dpdp-updates.md specifies DPDP, Nepal Privacy Act & Grievance Officer", () => {
    const legalPath = path.join(ROOT_DIR, "docs/legal-and-dpdp-updates.md");
    assert.ok(fs.existsSync(legalPath));
    const content = fs.readFileSync(legalPath, "utf8");
    assert.ok(content.includes("Digital Personal Data Protection Act, 2023"));
    assert.ok(content.includes("Privacy Act, 2075"));
    assert.ok(content.includes("grievance@jyotirmoyb.com"));
  });
});
