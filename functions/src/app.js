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

function createApiApp() {
  const app = express();

  // W3C Distributed Tracing and Telemetry Middleware
  app.use(tracingMiddleware);

  // Basic security and parsing middleware
  app.use(express.json({ limit: "500kb" }));

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
  apiV1Router.use(createLedgerRouter());
  apiV1Router.use("/sync", createSyncRouter());
  apiV1Router.use(createSyncRouter());

  app.use("/api/v1", apiV1Router);
  app.use("/api", apiV1Router); // rewrite fallback

  // RFC 9457 Problem Details global error handler
  app.use(problemDetailsErrorHandler);

  return app;
}

module.exports = {
  createApiApp
};
