const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("Stage S0: OpenAPI 3.1 Specification Validation", () => {
  const openApiPath = path.resolve(__dirname, "../../docs/openapi.yaml");
  assert.ok(fs.existsSync(openApiPath), "docs/openapi.yaml must exist");

  const content = fs.readFileSync(openApiPath, "utf8");
  assert.ok(content.length > 500, "docs/openapi.yaml must not be an empty stub");

  // 1. Verify OpenAPI version
  assert.match(content, /openapi:\s*3\.1\.0/, "Must specify OpenAPI 3.1.0");

  // 2. Verify base servers
  assert.match(content, /https:\/\/app\.udhaar\.jyotirmoyb\.com\/api\/v1/, "Must define app origin server in asia-south1");
  assert.match(content, /https:\/\/udhaar\.jyotirmoyb\.com\/api\/v1/, "Must define guest origin server");

  // 3. Verify mandatory endpoints from Stage S0 requirements
  const requiredEndpoints = [
    "/auth/signup/start",
    "/auth/otp/verify",
    "/auth/signup/email",
    "/auth/signup/complete",
    "/auth/signin/start",
    "/me",
    "/me/devices",
    "/sync/push",
    "/sync/pull",
    "/records",
    "/records/{id}",
    "/records/{id}/payments",
    "/attachments/upload-url",
    "/privacy/export",
    "/privacy/delete"
  ];

  for (const ep of requiredEndpoints) {
    assert.ok(content.includes(ep), `docs/openapi.yaml must specify endpoint: ${ep}`);
  }

  // 4. Verify RFC 9457 Problem Details standard error definition
  assert.match(content, /ProblemDetails:/, "Must define RFC 9457 ProblemDetails schema");
  assert.match(content, /application\/problem\+json/, "Must declare application/problem+json response content type");

  // 5. Verify security parameters
  assert.match(content, /name:\s*Idempotency-Key/, "Must declare Idempotency-Key header parameter");
  assert.match(content, /__session/, "Must declare __session cookie authentication scheme");
  assert.match(content, /BearerAuth:/, "Must declare BearerAuth HTTP scheme for Android client");
});

test("Stage S0: Architecture Decision Records (ADR) Completeness", () => {
  const adrDir = path.resolve(__dirname, "../../docs/adr");
  assert.ok(fs.existsSync(adrDir), "docs/adr directory must exist");

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
    const text = fs.readFileSync(filePath, "utf8");
    assert.ok(text.length > 300, `ADR ${adr} must contain detailed architectural reasoning`);
  }
});

test("Stage S0: Data Classification & Privacy Specifications", () => {
  const dataClassPath = path.resolve(__dirname, "../../docs/data-classification.md");
  assert.ok(fs.existsSync(dataClassPath), "docs/data-classification.md must exist");
  const dataClassText = fs.readFileSync(dataClassPath, "utf8");
  assert.match(dataClassText, /Envelope AES-256-GCM/, "Must document envelope encryption");
  assert.match(dataClassText, /phoneIndex/, "Must document blind index collections");

  const dpdpPath = path.resolve(__dirname, "../../docs/dpdp.md");
  assert.ok(fs.existsSync(dpdpPath), "docs/dpdp.md must exist");
  const dpdpText = fs.readFileSync(dpdpPath, "utf8");
  assert.match(dpdpText, /Digital Personal Data Protection Act/, "Must cover DPDP Act 2023");
  assert.match(dpdpText, /grievance@jyotirmoyb\.com/, "Must document Grievance Officer");
});

test("Stage S0: Environment & Multi-Site Firebase Hosting Configurations", () => {
  const infraDir = path.resolve(__dirname, "../../infra");
  assert.ok(fs.existsSync(infraDir), "infra directory must exist");

  const envs = ["firebase.local.json", "firebase.staging.json", "firebase.prod.json"];
  for (const env of envs) {
    const envPath = path.join(infraDir, env);
    assert.ok(fs.existsSync(envPath), `Missing infra config: ${env}`);
    const parsed = JSON.parse(fs.readFileSync(envPath, "utf8"));
    assert.ok(Array.isArray(parsed.hosting), `${env} must define multi-site hosting array`);
    assert.equal(parsed.hosting.length, 2, `${env} must contain exactly two hosting sites (marketing & app)`);
  }
});
