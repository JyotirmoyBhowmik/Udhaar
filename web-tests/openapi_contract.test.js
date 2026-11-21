const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("Architecture & Contracts: OpenAPI, ADRs, Data Classification & Hosting Configs", async (t) => {
  await t.test("1. OpenAPI 3.1 Specification exists and is well-formed", () => {
    const openApiPath = path.resolve(__dirname, "../docs/openapi.yaml");
    assert.ok(fs.existsSync(openApiPath), "docs/openapi.yaml must exist");
    const content = fs.readFileSync(openApiPath, "utf8");
    assert.ok(content.length > 500);
    assert.match(content, /openapi:\s*3\.1\.0/);
    assert.match(content, /ProblemDetails:/);
    assert.match(content, /application\/problem\+json/);
  });

  await t.test("2. Architecture Decision Records (ADR) completeness", () => {
    const adrDir = path.resolve(__dirname, "../docs/adr");
    assert.ok(fs.existsSync(adrDir));
    const requiredAdrs = [
      "0001-local-first-architecture.md",
      "0002-dual-origins-and-region-pinning.md",
      "0003-unified-custom-identity-service.md",
      "0004-data-protection-envelope-encryption.md",
      "0005-api-mediated-ledger-access.md",
      "0006-provider-abstraction-layer.md"
    ];
    for (const adr of requiredAdrs) {
      const filePath = path.join(adrDir, adr);
      assert.ok(fs.existsSync(filePath), `Required ADR missing: ${adr}`);
      assert.ok(fs.readFileSync(filePath, "utf8").length > 300);
    }
  });

  await t.test("3. Data Classification, DPDP & Firestore Indexes", () => {
    const dataClassPath = path.resolve(__dirname, "../docs/data-classification.md");
    assert.ok(fs.existsSync(dataClassPath));
    const dpdpPath = path.resolve(__dirname, "../docs/dpdp.md");
    assert.ok(fs.existsSync(dpdpPath));
    assert.match(fs.readFileSync(dpdpPath, "utf8"), /grievance@jyotirmoyb\.com/);

    const indexesPath = path.resolve(__dirname, "../firestore.indexes.json");
    assert.ok(fs.existsSync(indexesPath));
    const indexes = JSON.parse(fs.readFileSync(indexesPath, "utf8"));
    assert.ok(Array.isArray(indexes.indexes) && indexes.indexes.length > 0);
  });

  await t.test("4. Multi-Site Firebase Hosting Configurations", () => {
    const infraDir = path.resolve(__dirname, "../infra");
    assert.ok(fs.existsSync(infraDir));
    const envs = ["firebase.local.json", "firebase.staging.json", "firebase.prod.json"];
    for (const env of envs) {
      const envPath = path.join(infraDir, env);
      assert.ok(fs.existsSync(envPath), `Missing infra config: ${env}`);
      const parsed = JSON.parse(fs.readFileSync(envPath, "utf8"));
      assert.ok(Array.isArray(parsed.hosting));
      assert.equal(parsed.hosting.length, 2);
    }
  });
});
