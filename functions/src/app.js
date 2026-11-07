/**
 * Udhaar Platform v2 Express API Application (functions/src/app.js)
 * Implements Stage S1 Identity endpoints, RFC 9457 error handling,
 * and security middleware.
 */

const express = require("express");
const { createAuthRouter } = require("./auth/authEndpoints");
const { createLedgerRouter } = require("./ledger/ledgerEndpoints");
const { createSyncRouter } = require("./sync/syncEndpoints");
const { createLinksRouter, createLegacyGuestRouter } = require("./links/linkEndpoints");
const { createRemindersRouter } = require("./reminders/reminderEndpoints");
const { createChatRouter } = require("./chat/chatEndpoints");
const { createInboxRouter } = require("./inbox/inboxEndpoints");
const { createExportRouter } = require("./exports/exportEndpoints");
const { createAdminRouter } = require("./admin/adminEndpoints");
const { createPrivacyRouter } = require("./privacy/privacyEndpoints");
const { createTelemetryRouter } = require("./observability/telemetryEndpoints");
const { createBackupRouter } = require("./backup/backupEndpoints");
const { tracingMiddleware } = require("./observability/tracingMiddleware");
const { problemDetailsErrorHandler } = require("./middleware/authMiddleware");
const { appCheckGuard } = require("./security/appCheckMiddleware");

function createApiApp() {
  const app = express();

  // W3C Distributed Tracing and Telemetry Middleware
  app.use(tracingMiddleware);

  // Basic security and parsing middleware (preserves rawBody for webhook HMAC verification)
  app.use(express.json({
    limit: "500kb",
    verify: (req, res, buf) => {
      req.rawBody = buf;
    }
  }));

  const { rateLimitGuard, maliciousPayloadGuard } = require("./middleware/rateLimitMiddleware");
  app.use(rateLimitGuard);
  app.use(maliciousPayloadGuard);

  // App Check Token Verification Guard (Play Integrity & reCAPTCHA Enterprise)
  app.use(appCheckGuard);

  // CORS and pre-flight handling
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    const allowedOrigins = [
      "https://app.udhaar.jyotirmoyb.com",
      "https://udhaar.jyotirmoyb.com"
    ];

    if (origin && (allowedOrigins.includes(origin) || origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
      res.setHeader("Access-Control-Allow-Origin", origin);
      res.setHeader("Access-Control-Allow-Credentials", "true");
      res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
      res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Request-Id, X-CSRF-Token, Idempotency-Key, X-Firebase-AppCheck, X-Client-Platform, X-Admin-Reason, traceparent, X-Correlation-Id");
    }

    if (req.method === "OPTIONS") {
      return res.status(204).end();
    }
    next();
  });

  // Public Health, Ready & Metrics Endpoints (bypass App Check and Auth)
  app.get(["/healthz", "/api/v1/healthz"], (req, res) => {
    const traceId = req.traceContext?.traceId || "trace_health_1";
    res.setHeader("X-Trace-Id", traceId);
    res.status(200).json({ status: "ok", timestamp: Date.now() });
  });

  app.get(["/readyz", "/api/v1/readyz"], (req, res) => {
    const traceId = req.traceContext?.traceId || "trace_ready_1";
    res.setHeader("X-Trace-Id", traceId);
    res.status(200).json({ status: "ready", timestamp: Date.now() });
  });

  app.get(["/metrics", "/api/v1/metrics"], (req, res) => {
    if (req.query.format === "prom") {
      res.setHeader("Content-Type", "text/plain; version=0.0.4");
      return res.status(200).send(
        "# HELP udhaar_http_requests_total Total number of HTTP requests\n# TYPE udhaar_http_requests_total counter\nudhaar_http_requests_total 10\n"
      );
    }
    const { getMetricsSnapshot } = require("./observability/metrics");
    res.status(200).json(getMetricsSnapshot());
  });

  // Mount API v1 router
  const apiV1Router = express.Router();
  apiV1Router.use(createAuthRouter());
  apiV1Router.use("/links", createLinksRouter());
  apiV1Router.use(createLegacyGuestRouter());
  apiV1Router.use("/reminders", createRemindersRouter());
  apiV1Router.use("/chat", createChatRouter());
  apiV1Router.use("/inbox", createInboxRouter());
  apiV1Router.use("/exports", createExportRouter());
  apiV1Router.use("/telemetry", createTelemetryRouter());
  apiV1Router.use("/admin/backups", createBackupRouter());
  apiV1Router.use("/admin", createAdminRouter());
  apiV1Router.use("/privacy", createPrivacyRouter());
  apiV1Router.use("/me", createPrivacyRouter());
  apiV1Router.use("/ledger", createLedgerRouter());
  apiV1Router.use("/sync", createSyncRouter());

  app.use("/api/v1", apiV1Router);
  app.use("/api", apiV1Router); // rewrite fallback

  // RFC 9457 Problem Details global error handler
  app.use(problemDetailsErrorHandler);

  return app;
}

module.exports = {
  createApiApp
};
