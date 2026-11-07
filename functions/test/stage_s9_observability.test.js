/**
 * Stage S9 Test Suite (functions/test/stage_s9_observability.test.js)
 *
 * Comprehensive verification of:
 * 1. Structured JSON Logging & PII Redaction: Enterprise rules 4.1 & 4.3.
 * 2. W3C Distributed Tracing Middleware: traceparent 00-{traceId}-{spanId}-01 & Correlation-IDs.
 * 3. Real-Time SLI / SLO Metrics: Availability, p50/p95/p99 latency calculations, error rates.
 * 4. Client Core Web Vitals RUM Telemetry: LCP, INP, CLS beacon ingestion & admin SLO query.
 * 5. Automated Backups & Disaster Recovery: Point-in-time snapshots, SHA-256 seals, restore drill.
 * 6. Synthetic Load Test Execution: Concurrency, spike burst, and sync throughput under SLO bounds.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
const crypto = require("crypto");

const { createApiApp } = require("../src/app");
const logger = require("../src/observability/logger");
const {
  recordHttpRequest,
  calculatePercentiles,
  ingestRumMetric,
  getSloSnapshot,
  resetMetrics
} = require("../src/observability/metrics");

const {
  createBackupSnapshot,
  listBackupSnapshots,
  verifySnapshotIntegrity,
  executeRestoreDrill,
  resetBackupStore
} = require("../src/backup/backupService");

const { setAdminRole, clearAdminRoles } = require("../src/middleware/adminMiddleware");
const { resetAdminStore } = require("../src/admin/adminService");
const { resetAuthStore, _inMemoryUsers } = require("../src/auth/authService");
const { resetRecordsStore, createRecord } = require("../src/ledger/recordService");
const { createSession, resetSessionsStore } = require("../src/sessions/sessionManager");
const { runLoadTestSuite } = require("../../scripts/load-test");

test.beforeEach(() => {
  logger.resetLogBuffer();
  resetMetrics();
  resetBackupStore();
  clearAdminRoles();
  resetAdminStore();
  resetAuthStore();
  resetRecordsStore();
  resetSessionsStore();
});

function makeRequest(server, options, requestBody = null) {
  return new Promise((resolve, reject) => {
    const payload = requestBody ? (typeof requestBody === "string" ? requestBody : JSON.stringify(requestBody)) : null;
    const reqOptions = { ...options };

    if (payload) {
      reqOptions.headers = {
        ...reqOptions.headers,
        "Content-Length": Buffer.byteLength(payload)
      };
    }

    const req = http.request(reqOptions, (res) => {
      let data = "";
      res.on("data", (chunk) => {
        data += chunk;
      });
      res.on("end", () => {
        let json = null;
        try {
          json = JSON.parse(data);
        } catch (_) {}
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: json !== null ? json : data
        });
      });
    });

    req.on("error", reject);

    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

// -------------------------------------------------------------
// 1. Structured JSON Logging & PII Redaction
// -------------------------------------------------------------
test("Stage S9: 1.1 Structured JSON Logging & Automatic PII Masking", () => {
  logger.info("User registered new debt record with auth token Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123", {
    correlationId: "corr_test_001",
    traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
    spanId: "00f067aa0ba902b7",
    functionName: "createRecordHandler",
    clientIp: "127.0.0.1",
    phoneNumber: "+919876543210",
    email: "alice.sharma@example.com",
    authHeader: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123",
    secretKey: "super_secret_kms_key",
    password: "UserPassword123!",
    notes: "Contacted debtor on +919876543210 via email bob@builder.org"
  });

  const logs = logger.getRecentLogs({ limit: 1 });
  assert.equal(logs.length, 1);
  const entry = logs[0];

  assert.equal(entry.level, "INFO");
  assert.equal(entry.service_name, "udhaar-platform");
  assert.equal(entry.correlation_id, "corr_test_001");
  assert.equal(entry.trace_id, "4bf92f3577b34da6a3ce929d0e0e4736");
  assert.equal(entry.span_id, "00f067aa0ba902b7");
  assert.equal(entry.function_name, "createRecordHandler");

  // PII Redaction Checks
  assert.ok(entry.message.includes("Bearer [REDACTED]"));
  assert.equal(entry.metadata.password, "[REDACTED]");
  assert.equal(entry.metadata.secretKey, "[REDACTED]");
  assert.equal(entry.metadata.authHeader, "[REDACTED]");
  assert.ok(!entry.metadata.phoneNumber.includes("9876543210"));
  assert.ok(entry.metadata.phoneNumber.includes("+91 ***-***-210"));
  assert.ok(entry.metadata.email.includes("a***a@example.com"));
  assert.ok(entry.metadata.notes.includes("+91 ***-***-210"));
  assert.ok(entry.metadata.notes.includes("b***b@builder.org"));
});

// -------------------------------------------------------------
// 2. W3C Distributed Tracing Middleware
// -------------------------------------------------------------
test("Stage S9: 2.1 W3C Trace Context Propagation & Correlation-ID Tracking", async () => {
  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const inboundTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    const inboundSpanId = "00f067aa0ba902b7";
    const inboundTraceparent = `00-${inboundTraceId}-${inboundSpanId}-01`;
    const correlationId = "corr_custom_test_999";

    const res = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/rum",
      method: "POST",
      headers: {
        traceparent: inboundTraceparent,
        "X-Correlation-Id": correlationId,
        "Content-Type": "application/json"
      }
    }, { metricName: "LCP", value: 920 });

    assert.equal(res.status, 204);

    // Verify response headers contain propagated traceparent & correlation ID
    const outboundTraceparent = res.headers["traceparent"];
    assert.ok(outboundTraceparent);
    assert.ok(outboundTraceparent.startsWith(`00-${inboundTraceId}-`));
    assert.ok(outboundTraceparent.endsWith("-01"));
    assert.equal(res.headers["x-correlation-id"], correlationId);

    // Verify access log recorded structured trace context
    const accessLogs = logger.getRecentLogs({ limit: 10 });
    const match = accessLogs.find(l => l.correlation_id === correlationId);
    assert.ok(match);
    assert.equal(match.trace_id, inboundTraceId);
    assert.ok(match.metadata.durationMs >= 0);
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 3. Real-Time SLI Metrics & Percentile Reservoir
// -------------------------------------------------------------
test("Stage S9: 3.1 SLI Latency Percentiles & Availability Calculation", () => {
  // Inject simulated latency distribution: 95 requests at 20ms, 4 at 150ms, 1 at 300ms
  for (let i = 0; i < 95; i++) {
    recordHttpRequest({ method: "GET", path: "/api/v1/ledger/records", statusCode: 200, durationMs: 20 });
  }
  for (let i = 0; i < 4; i++) {
    recordHttpRequest({ method: "POST", path: "/api/v1/ledger/records", statusCode: 201, durationMs: 150 });
  }
  recordHttpRequest({ method: "GET", path: "/api/v1/ledger/records", statusCode: 500, durationMs: 300 });

  const snapshot = getSloSnapshot();

  assert.equal(snapshot.sli.totalRequests, 100);
  assert.equal(snapshot.sli.availabilityPercentage, 99.0); // 1 out of 100 failed -> 99%
  assert.equal(snapshot.sli.errorRatePercentage, 1.0);

  // Latency percentiles check
  assert.equal(snapshot.sli.latencyMs.p50, 20);
  assert.equal(snapshot.sli.latencyMs.p90, 20);
  assert.equal(snapshot.sli.latencyMs.p95, 150);
  assert.equal(snapshot.sli.latencyMs.p99, 300);

  // Status should be AT_RISK because errorRate (1%) exceeds SLO target (0.1%)
  assert.equal(snapshot.status, "AT_RISK");
  assert.equal(snapshot.sloCompliance.errorRateOk, false);
});

// -------------------------------------------------------------
// 4. Client Core Web Vitals (RUM) Telemetry Ingestion
// -------------------------------------------------------------
test("Stage S9: 4.1 Client Core Web Vitals Ingestion & Admin SLO Telemetry Query", async () => {
  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_s9_admin";
    const regularUid = "usr_s9_regular";

    _inMemoryUsers.set(adminUid, { uid: adminUid, status: "ACTIVE" });
    _inMemoryUsers.set(regularUid, { uid: regularUid, status: "ACTIVE" });

    setAdminRole(adminUid, "AUDITOR");
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });
    const sessionRegular = await createSession({ uid: regularUid, authTime: Date.now() });

    // 1. Submit RUM metrics from client
    const rumResLcp = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/rum",
      method: "POST",
      headers: { "Content-Type": "application/json" }
    }, { metricName: "LCP", value: 1050, pageUrl: "https://app.udhaar.jyotirmoyb.com/dashboard" });
    assert.equal(rumResLcp.status, 204);

    const rumResInp = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/rum",
      method: "POST",
      headers: { "Content-Type": "application/json" }
    }, { metricName: "INP", value: 65, pageUrl: "https://app.udhaar.jyotirmoyb.com/dashboard" });
    assert.equal(rumResInp.status, 204);

    const rumResCls = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/rum",
      method: "POST",
      headers: { "Content-Type": "application/json" }
    }, { metricName: "CLS", value: 0.01, pageUrl: "https://app.udhaar.jyotirmoyb.com/dashboard" });
    assert.equal(rumResCls.status, 204);

    // 2. Regular user querying /telemetry/slo returns 403 Forbidden
    const unauthSlo = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/slo",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionRegular.sessionCookieValue}` }
    });
    assert.equal(unauthSlo.status, 403);

    // 3. Admin / Auditor querying /telemetry/slo returns 200 with ingested RUM metrics
    const adminSlo = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/telemetry/slo",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAdmin.sessionCookieValue}` }
    });
    assert.equal(adminSlo.status, 200);
    assert.ok(adminSlo.body.rumWebVitals);
    assert.equal(adminSlo.body.rumWebVitals.lcp.count, 1);
    assert.equal(adminSlo.body.rumWebVitals.inp.count, 1);
    assert.equal(adminSlo.body.rumWebVitals.cls.count, 1);
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 5. Automated Backup & Disaster Recovery (DR) Engine
// -------------------------------------------------------------
test("Stage S9: 5.1 Automated Point-in-Time Backup, Checksum Seal & Restore Drill", async () => {
  // Seed sample data in users and ledger
  const testUid = "usr_backup_test_01";
  _inMemoryUsers.set(testUid, { uid: testUid, status: "ACTIVE" });
  await createRecord({
    uid: testUid,
    recordData: {
      counterpartyRole: "LENDER",
      partyName: "Farmer Co-op",
      principalAmountPaise: 1200000,
      note: "Fertilizer seasonal advance"
    }
  });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_s9_backup_admin";
    _inMemoryUsers.set(adminUid, { uid: adminUid, status: "ACTIVE" });
    setAdminRole(adminUid, "ADMIN");
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });

    // 1. Create on-demand backup snapshot
    const createRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/backups/snapshot",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Pre-deployment database baseline snapshot"
      }
    }, { type: "FULL" });

    assert.equal(createRes.status, 201);
    const snapshot = createRes.body;
    assert.ok(snapshot.snapshotId.startsWith("bck_"));
    assert.equal(snapshot.type, "FULL");
    assert.ok(snapshot.manifestSha256);
    assert.ok(snapshot.collections.records.count >= 1);

    // 2. List backup snapshots
    const listRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/backups",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAdmin.sessionCookieValue}` }
    });
    assert.equal(listRes.status, 200);
    assert.ok(listRes.body.length >= 1);

    // 3. Verify SHA-256 manifest integrity
    const verifyRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/admin/backups/${snapshot.snapshotId}/verify`,
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Routine integrity audit"
      }
    });
    assert.equal(verifyRes.status, 200);
    assert.equal(verifyRes.body.status, "VERIFIED");
    assert.equal(verifyRes.body.isHashValid, true);

    // 4. Execute Disaster Recovery Restore Drill
    const drillRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/admin/backups/${snapshot.snapshotId}/restore-drill`,
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Quarterly disaster recovery restore simulation drill"
      }
    }, { targetStagingDatabase: "restore-drill-stage-s9" });

    assert.equal(drillRes.status, 200);
    assert.equal(drillRes.body.drillStatus, "SUCCESS");
    assert.equal(drillRes.body.targetStagingDatabase, "restore-drill-stage-s9");
    assert.equal(drillRes.body.merkleIntegrityVerified, true);
    assert.ok(drillRes.body.rtoAchievedMinutes < 60); // Must be within 1 hour target
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 6. Synthetic Load Test Execution & Resilience
// -------------------------------------------------------------
test("Stage S9: 6.1 Synthetic Load Test Execution & SLO Compliance", async () => {
  const loadResult = await runLoadTestSuite({ targetConcurrency: 60 });

  assert.ok(loadResult.scenario1ConcurrentWorkload);
  assert.ok(loadResult.scenario2SpikeBurst);
  assert.ok(loadResult.scenario3SyncThroughput);

  // Check throughput and latencies
  assert.ok(loadResult.scenario1ConcurrentWorkload.throughputOpsPerSec > 0);
  assert.ok(loadResult.scenario1ConcurrentWorkload.successRatePercentage >= 99.0);
  assert.ok(loadResult.scenario1ConcurrentWorkload.errorRatePercentage <= 0.1);

  // Assert p95 latency is within SLO bounds
  assert.ok(loadResult.scenario1ConcurrentWorkload.latencies.p95 <= 250);
});
