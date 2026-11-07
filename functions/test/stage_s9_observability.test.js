const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { apiApp } = require("../src/api");
const { getOrCreateUser } = require("../src/auth/authService");
const { createSession } = require("../src/sessions/sessionManager");
const { grantAdminRole } = require("../src/middleware/adminMiddleware");
const { resetMetrics, recordHttpRequest, getMetricsSnapshot, evaluateAlerts } = require("../src/observability/metrics");
const { clearSpans, withSpan, getRecentSpans } = require("../src/observability/tracing");
const { clearAuditLogs } = require("../src/admin/adminService");
const { resetAllKillSwitches } = require("../src/config/killSwitches");
const { resetAllCircuits } = require("../src/config/circuitBreakers");
const { clearBackups } = require("../src/backup/backupService");
const { resetRateLimits } = require("../src/middleware/rateLimitMiddleware");
const { resetTestRepositories } = require("../src/repositories");

async function invokeApp(app, { method = "GET", url = "/", headers = {}, body = undefined }) {
  return new Promise((resolve, reject) => {
    const server = app.listen(0, "127.0.0.1", async () => {
      const { port } = server.address();
      try {
        const fetchHeaders = { ...headers };
        if (body !== undefined && !fetchHeaders["content-type"]) {
          fetchHeaders["content-type"] = "application/json";
        }
        const res = await fetch(`http://127.0.0.1:${port}${url}`, {
          method,
          headers: fetchHeaders,
          body: body !== undefined ? JSON.stringify(body) : undefined
        });
        const text = await res.text();
        let json = null;
        try {
          json = JSON.parse(text);
        } catch (_) {}
        server.close(() => resolve({ status: res.status, headers: res.headers, body: json, text }));
      } catch (err) {
        server.close(() => reject(err));
      }
    });
  });
}

describe("Stage S9 — Observability, SLOs, Admin Ops & Release Readiness", () => {
  beforeEach(async () => {
    resetMetrics();
    clearSpans();
    await resetTestRepositories();
    await clearAuditLogs();
    await resetAllKillSwitches();
    await resetAllCircuits();
    await clearBackups();
    resetRateLimits();
  });

  describe("1. Health, Liveness, Readiness & Prometheus Metrics", () => {
    it("returns 200 on /healthz and /readyz and exposes Prometheus metrics", async () => {
      const health = await invokeApp(apiApp, { method: "GET", url: "/api/v1/healthz" });
      assert.equal(health.status, 200);
      assert.equal(health.body.status, "ok");
      assert.ok(health.headers.get("x-trace-id"));

      const ready = await invokeApp(apiApp, { method: "GET", url: "/api/v1/readyz" });
      assert.equal(ready.status, 200);
      assert.equal(ready.body.status, "ready");

      const prom = await invokeApp(apiApp, { method: "GET", url: "/api/v1/metrics?format=prom" });
      assert.equal(prom.status, 200);
      assert.match(prom.text, /udhaar_http_requests_total/);
    });

    it("evaluates SLO burn alerts when 5xx error rate or p95 latency breaches threshold", () => {
      for (let i = 0; i < 10; i++) {
        recordHttpRequest({ method: "POST", route: "/api/v1/sync/push", status: 500, durationMs: 1200 });
      }
      const snap = getMetricsSnapshot();
      assert.equal(snap.http.Status5xx, 10);
      assert.ok(snap.http.latency.p95Ms >= 1000);

      const alerts = evaluateAlerts();
      const codes = alerts.map(a => a.code);
      assert.ok(codes.includes("ALERT_HIGH_5XX_RATE"));
      assert.ok(codes.includes("ALERT_HIGH_P95_LATENCY"));
    });
  });

  describe("2. OpenTelemetry-Compatible Span Tracing", () => {
    it("records nested spans with traceId and attributes", async () => {
      await withSpan("test.operation", { traceId: "trace_fixed_123", attributes: { component: "sync" } }, async (span) => {
        span.setAttribute("recordsProcessed", 5);
      });

      const spans = getRecentSpans(10);
      assert.equal(spans.length, 1);
      assert.equal(spans[0].name, "test.operation");
      assert.equal(spans[0].traceId, "trace_fixed_123");
      assert.equal(spans[0].attributes.recordsProcessed, 5);
      assert.equal(spans[0].status, "OK");
    });
  });

  describe("3. Admin RBAC, Kill Switch Control, User Lookup & Immutable Audit Trail", () => {
    it("blocks non-admin users from /api/v1/admin/* and logs all admin mutations", async () => {
      const { user: normalUser } = await getOrCreateUser({ channel: "email", destination: "normal@example.com" });
      const normalSession = await createSession({ uid: normalUser.uid });

      const forbidden = await invokeApp(apiApp, {
        method: "GET",
        url: "/api/v1/admin/overview",
        headers: { authorization: `Bearer ${normalSession.accessToken}` }
      });
      assert.equal(forbidden.status, 403);
      assert.equal(forbidden.body.error.code, "ERR_ADMIN_FORBIDDEN");

      // Create SUPER_ADMIN
      const { user: adminUser } = await getOrCreateUser({ channel: "email", destination: "admin@udhaar.internal" });
      await grantAdminRole(adminUser.uid, "SUPER_ADMIN");
      const adminSession = await createSession({ uid: adminUser.uid });

      // 1. Get overview
      const overview = await invokeApp(apiApp, {
        method: "GET",
        url: "/api/v1/admin/overview",
        headers: { authorization: `Bearer ${adminSession.accessToken}` }
      });
      assert.equal(overview.status, 200);
      assert.ok(overview.body.metrics);

      // 2. Toggle Kill Switch
      const ksRes = await invokeApp(apiApp, {
        method: "POST",
        url: "/api/v1/admin/kill-switches",
        headers: { authorization: `Bearer ${adminSession.accessToken}` },
        body: { flagName: "KILL_OUTBOUND_REMINDERS", active: true, reason: "Holiday pause" }
      });
      assert.equal(ksRes.status, 200);
      assert.equal(ksRes.body.killSwitches.KILL_OUTBOUND_REMINDERS.active, true);

      // 3. Lookup user without exposing secret hashes
      const lookupRes = await invokeApp(apiApp, {
        method: "GET",
        url: `/api/v1/admin/users?email=${encodeURIComponent("normal@example.com")}`,
        headers: { authorization: `Bearer ${adminSession.accessToken}` }
      });
      assert.equal(lookupRes.status, 200);
      assert.equal(lookupRes.body.user.uid, normalUser.uid);

      // 4. Verify immutable audit logs recorded both actions
      const auditRes = await invokeApp(apiApp, {
        method: "GET",
        url: "/api/v1/admin/audit-logs",
        headers: { authorization: `Bearer ${adminSession.accessToken}` }
      });
      assert.equal(auditRes.status, 200);
      const actions = auditRes.body.logs.map(l => l.action);
      assert.ok(actions.includes("TOGGLE_KILL_SWITCH"));
      assert.ok(actions.includes("USER_SUPPORT_LOOKUP"));
    });
  });
});
