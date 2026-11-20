/**
 * Stage R0 Automated Verification Suite: Truth First & Environment Guards
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const {
  isProductionOrStaging,
  enforceProductionEnvironmentGuards,
  FALLBACK_SECRET_VALUE
} = require("../src/config/environmentGuard");

const ROOT_DIR = path.resolve(__dirname, "../..");

test("Stage R0: Environment Guard Invariants", async (t) => {
  const originalNodeEnv = process.env.NODE_ENV;
  const originalUdhaarEnv = process.env.UDHAAR_ENV;
  const originalFakeProviders = process.env.USE_FAKE_PROVIDERS;
  const originalServerSecret = process.env.UDHAAR_SERVER_SECRET;

  function restoreEnv() {
    if (originalNodeEnv !== undefined) process.env.NODE_ENV = originalNodeEnv;
    else delete process.env.NODE_ENV;

    if (originalUdhaarEnv !== undefined) process.env.UDHAAR_ENV = originalUdhaarEnv;
    else delete process.env.UDHAAR_ENV;

    if (originalFakeProviders !== undefined) process.env.USE_FAKE_PROVIDERS = originalFakeProviders;
    else delete process.env.USE_FAKE_PROVIDERS;

    if (originalServerSecret !== undefined) process.env.UDHAAR_SERVER_SECRET = originalServerSecret;
    else delete process.env.UDHAAR_SERVER_SECRET;
  }

  t.afterEach(restoreEnv);

  await t.test("1. Allows in-memory stores and test secrets in development/test environment", () => {
    process.env.NODE_ENV = "test";
    delete process.env.UDHAAR_ENV;
    const res = enforceProductionEnvironmentGuards({
      useInMemoryStore: true,
      providerMode: "fake",
      secretKey: FALLBACK_SECRET_VALUE
    });
    assert.strictEqual(res.ok, true);
  });

  await t.test("2. Rejects in-memory store in production environment with fatal error", () => {
    delete process.env.UDHAAR_ENV;
    process.env.NODE_ENV = "production";
    assert.throws(
      () => enforceProductionEnvironmentGuards({ useInMemoryStore: true }),
      /FATAL: In-memory store requested while running in production/
    );
  });

  await t.test("3. Rejects fake providers in staging environment with fatal error", () => {
    process.env.UDHAAR_ENV = "staging";
    process.env.USE_FAKE_PROVIDERS = "true";
    assert.throws(
      () => enforceProductionEnvironmentGuards({ useInMemoryStore: false }),
      /FATAL: Fake provider mode enabled in staging/
    );
  });

  await t.test("4. Rejects hardcoded fallback secrets in production with fatal error", () => {
    delete process.env.UDHAAR_ENV;
    process.env.NODE_ENV = "production";
    process.env.USE_FAKE_PROVIDERS = "false";
    assert.throws(
      () => enforceProductionEnvironmentGuards({
        useInMemoryStore: false,
        secretKey: FALLBACK_SECRET_VALUE
      }),
      /FATAL: Insecure fallback or test secret detected in production/
    );
  });
});

test("Stage R0: Launch Checklist & Reality Check Integrity", async (t) => {
  await t.test("1. docs/REALITY_CHECK.md exists and contains complete audit matrix", () => {
    const rcPath = path.join(ROOT_DIR, "docs/REALITY_CHECK.md");
    assert.ok(fs.existsSync(rcPath));
    const content = fs.readFileSync(rcPath, "utf8");
    assert.ok(content.includes("Launch Checklist Reality Check Matrix"));
    assert.ok(content.includes("Trust Center Claims Reality Check"));
  });

  await t.test("2. scripts/verify-launch-checklist.js passes verification on current repo", () => {
    const { verifyLaunchChecklist } = require("../../scripts/verify-launch-checklist.js");
    assert.doesNotThrow(() => verifyLaunchChecklist());
  });
});
