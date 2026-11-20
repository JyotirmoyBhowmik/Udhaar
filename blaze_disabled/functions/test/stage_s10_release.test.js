/**
 * Stage S10 Automated Test Suite: Release, Rollout, Rollback & Launch Readiness
 * 
 * Tests:
 * 1. Deterministic Canary Allocation (5%, 25%, 100%) via SHA-256 modulo 100.
 * 2. Instant Emergency Rollback execution and audit trail generation.
 * 3. GitHub Actions CI/CD workflow schema & step integrity.
 * 4. Operational runbook completeness across all required operational incidents.
 * 5. Production Launch Checklist & Store Listing localized release notes (5 languages).
 * 6. Legal and DPDP statutory mapping and compliance specifications.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const {
  computeCanaryBucket,
  testPopulationDistribution,
  executeRollback
} = require('../../scripts/rollback.js');

const ROOT_DIR = path.resolve(__dirname, '../..');

test('Stage S10: Canary Rollout Routing & Distribution Integrity', async (t) => {
  await t.test('1. Deterministic Canary Bucket allocation (same ID yields same bucket)', () => {
    const userId1 = 'usr_98a76b54_mumbai';
    const res1 = computeCanaryBucket(userId1, 5);
    const res2 = computeCanaryBucket(userId1, 5);
    assert.strictEqual(res1.bucket, res2.bucket, 'Same identifier must produce identical bucket');
    assert.strictEqual(res1.isCanary, res2.isCanary);
    assert.ok(res1.bucket >= 0 && res1.bucket < 100, 'Bucket must be in range [0, 99]');
  });

  await t.test('2. 5% Canary Allocation uniformly distributes across sample population', () => {
    const sampleIds = Array.from({ length: 1000 }, (_, i) => `device_node_sim_${i}_salt`);
    const dist5 = testPopulationDistribution(sampleIds, 5);
    // Over 1,000 random samples, a 5% allocation typically falls between 3.5% and 6.5%
    assert.ok(dist5.actualPercentage >= 3.0 && dist5.actualPercentage <= 7.0,
      `Expected ~5% distribution, got ${dist5.actualPercentage}% (${dist5.canaryCount}/1000)`);
    
    // Test 25% allocation
    const dist25 = testPopulationDistribution(sampleIds, 25);
    assert.ok(dist25.actualPercentage >= 20.0 && dist25.actualPercentage <= 30.0,
      `Expected ~25% distribution, got ${dist25.actualPercentage}% (${dist25.canaryCount}/1000)`);

    // Test 100% allocation
    const dist100 = testPopulationDistribution(sampleIds, 100);
    assert.strictEqual(dist100.canaryCount, 1000, '100% canary must allocate all users');
  });
});

test('Stage S10: Emergency Rollback Execution Engine', async (t) => {
  await t.test('1. executeRollback generates structured audit report meeting RTO SLA', async () => {
    const report = await executeRollback({
      projectId: 'udhaar-prod',
      targetVersion: 'v20.9.4-stable',
      reason: 'Canary error rate spike > 1.2%'
    });

    assert.strictEqual(report.status, 'SUCCESS');
    assert.strictEqual(report.action, 'PRODUCTION_ROLLBACK_EXECUTED');
    assert.strictEqual(report.targetVersion, 'v20.9.4-stable');
    assert.ok(report.stepsCompleted.length >= 4, 'Must report all completed rollback steps');
    assert.ok(report.executionDurationMs < 1000, 'Rollback orchestrator must execute in under 1 second');
    assert.ok(new Date(report.timestamp).getTime() > 0, 'Timestamp must be valid ISO 8601');
  });
});

test('Stage S10: CI/CD Pipeline Workflow Configurations', async (t) => {
  const ciPath = path.join(ROOT_DIR, '.github/workflows/ci.yml');
  const stagingPath = path.join(ROOT_DIR, '.github/workflows/deploy-staging.yml');
  const prodPath = path.join(ROOT_DIR, '.github/workflows/deploy-prod.yml');

  await t.test('1. CI workflow exists and defines multi-engine testing, linting and security scans', () => {
    assert.ok(fs.existsSync(ciPath), 'ci.yml must exist');
    const content = fs.readFileSync(ciPath, 'utf8');
    assert.ok(content.includes('test-suite'), 'ci.yml must include test-suite job');
    assert.ok(content.includes('sync-conformance/ts-runner.js'), 'ci.yml must include sync conformance runner');
    assert.ok(content.includes('playwright-e2e-and-accessibility'), 'ci.yml must include Playwright E2E');
    assert.ok(content.includes('security-and-dependency-audit'), 'ci.yml must include security audit');
  });

  await t.test('2. Staging deployment workflow configures preview channel and ZAP baseline', () => {
    assert.ok(fs.existsSync(stagingPath), 'deploy-staging.yml must exist');
    const content = fs.readFileSync(stagingPath, 'utf8');
    assert.ok(content.includes('udhaar-staging'), 'staging workflow must target udhaar-staging');
    assert.ok(content.includes('action-hosting-deploy'), 'staging workflow must deploy hosting preview');
  });

  await t.test('3. Production deployment workflow configures canary rollout, approval gate & rollback', () => {
    assert.ok(fs.existsSync(prodPath), 'deploy-prod.yml must exist');
    const content = fs.readFileSync(prodPath, 'utf8');
    assert.ok(content.includes('canary_percentage'), 'prod workflow must support canary percentage');
    assert.ok(content.includes('environment:'), 'prod workflow must include environment protection gate');
    assert.ok(content.includes('rollback.js'), 'prod workflow must integrate automated rollback verification');
  });
});

test('Stage S10: Operational Runbooks Completeness', async (t) => {
  const requiredRunbooks = [
    'bad-deploy-rollback.md',
    'data-breach.md',
    'whatsapp-outage.md',
    'email-provider-outage.md',
    'otp-abuse-spike.md',
    'leaked-provider-key.md',
    'incident-response.md',
    'emergency-kill-switches.md',
    'key-rotation.md',
    'cost-and-quotas.md'
  ];

  for (const rb of requiredRunbooks) {
    await t.test(`Runbook exists and has content: ${rb}`, () => {
      const rbPath = path.join(ROOT_DIR, 'docs/runbooks', rb);
      assert.ok(fs.existsSync(rbPath), `Runbook docs/runbooks/${rb} must exist`);
      const content = fs.readFileSync(rbPath, 'utf8');
      assert.ok(content.length > 500, `Runbook ${rb} must have comprehensive content`);
    });
  }
});

test('Stage S10: Production Launch Checklist & Store Listing Updates', async (t) => {
  const checklistPath = path.join(ROOT_DIR, 'docs/launch-checklist.md');
  const storePath = path.join(ROOT_DIR, 'docs/store-listing-updates.md');

  await t.test('1. docs/launch-checklist.md covers all 8 gates with sign-off criteria', () => {
    assert.ok(fs.existsSync(checklistPath), 'launch-checklist.md must exist');
    const content = fs.readFileSync(checklistPath, 'utf8');
    assert.ok(content.includes('Cloud Infrastructure & Networking Gate'));
    assert.ok(content.includes('Identity, Authentication & Anti-Fraud Gate'));
    assert.ok(content.includes('Data Integrity & Firestore Security Rules Gate'));
    assert.ok(content.includes('Web Application & PWA Client Gate'));
    assert.ok(content.includes('Android Native Application Gate'));
    assert.ok(content.includes('Privacy & Legal Compliance Gate'));
    assert.ok(content.includes('Observability, Backups & SRE Gate'));
    assert.ok(content.includes('Staged Rollout Timeline & Sign-Off'));
  });

  await t.test('2. docs/store-listing-updates.md contains Data Safety declarations and 5-language release notes', () => {
    assert.ok(fs.existsSync(storePath), 'store-listing-updates.md must exist');
    const content = fs.readFileSync(storePath, 'utf8');
    assert.ok(content.includes('Data Safety Declarations'));
    assert.ok(content.includes('English (en-IN, en-US)'));
    assert.ok(content.includes('Hindi (hi-IN)'));
    assert.ok(content.includes('Bengali (bn-IN, bn-BD)'));
    assert.ok(content.includes('Assamese (as-IN)'));
    assert.ok(content.includes('Nepali (ne-NP)'));
  });
});

test('Stage S10: Legal, DPDP & Nepal Privacy Compliance Specifications', async (t) => {
  const legalPath = path.join(ROOT_DIR, 'docs/legal-and-dpdp-updates.md');

  await t.test('1. docs/legal-and-dpdp-updates.md specifies statutory framework, Grievance Officer and legal flags', () => {
    assert.ok(fs.existsSync(legalPath), 'legal-and-dpdp-updates.md must exist');
    const content = fs.readFileSync(legalPath, 'utf8');
    assert.ok(content.includes('Digital Personal Data Protection Act, 2023'));
    assert.ok(content.includes('Privacy Act, 2075'));
    assert.ok(content.includes('grievance@jyotirmoyb.com'));
    assert.ok(content.includes('shredUserDek()'));
    assert.ok(content.includes('Points Flagged for External Legal Counsel Review'));
  });
});
