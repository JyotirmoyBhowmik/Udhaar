/**
 * Real User Monitoring (RUM) & SLI Telemetry REST Endpoints
 * (functions/src/observability/telemetryEndpoints.js)
 * Implements Stage S9 Observability Endpoints:
 * - POST /api/v1/telemetry/rum: Ingest client Core Web Vitals (LCP, INP, CLS) & client error logs.
 * - GET /api/v1/telemetry/slo: Query real-time SLI/SLO metrics, latency percentiles & availability.
 */

const express = require("express");
const { ingestRumMetric, getSloSnapshot } = require("./metrics");
const { requireAuth } = require("../middleware/authMiddleware");
const { requireAdminRole } = require("../middleware/adminMiddleware");

function createTelemetryRouter() {
  const router = express.Router();

  // 1. Ingest Client RUM Metrics (Public / Unauthenticated for Web Landing, Guest Portal & WebApp)
  router.post("/rum", (req, res) => {
    try {
      const payload = req.body || {};
      const { metricName, value, pageUrl, errorDetails } = payload;
      const userAgent = req.headers["user-agent"] || "";

      if (!metricName && !errorDetails) {
        return res.status(400).json({ error: "Missing metricName or errorDetails in telemetry payload." });
      }

      ingestRumMetric({
        metricName: metricName || "CLIENT_ERROR",
        value,
        pageUrl,
        userAgent,
        errorDetails
      });

      // Beacon standard: return 204 No Content
      res.status(204).end();
    } catch (err) {
      res.status(400).json({ error: err.message });
    }
  });

  // 2. Query Real-Time SLI/SLO Telemetry Snapshot (Admin, Operator, Auditor)
  router.get("/slo", requireAuth, requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), (req, res) => {
    try {
      const snapshot = getSloSnapshot();
      res.status(200).json(snapshot);
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  return router;
}

module.exports = {
  createTelemetryRouter
};
