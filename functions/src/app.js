/**
 * Udhaar Platform v2 Express API Application (functions/src/app.js)
 * Implements Stage S1 Identity endpoints, RFC 9457 error handling,
 * and security middleware.
 */

const express = require("express");
const { createAuthRouter } = require("./auth/authEndpoints");
const { createLedgerRouter } = require("./ledger/ledgerEndpoints");
const { createSyncRouter } = require("./sync/syncEndpoints");
const { createLinksRouter } = require("./links/linkEndpoints");
const { problemDetailsErrorHandler } = require("./middleware/authMiddleware");

function createApiApp() {
  const app = express();

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
      res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Request-Id, X-CSRF-Token, Idempotency-Key, X-Firebase-AppCheck, X-Client-Platform");
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
  apiV1Router.use(createLinksRouter());
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
