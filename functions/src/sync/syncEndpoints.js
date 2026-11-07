/**
 * Sync REST API Endpoints (functions/src/sync/syncEndpoints.js)
 * Implements /api/v1/sync/push and /api/v1/sync/pull with authentication and problem details.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const { processSyncPush, processSyncPull } = require("./syncService");

function createSyncRouter() {
  const router = express.Router();

  router.use(requireAuth);

  router.post("/push", async (req, res, next) => {
    try {
      const { deviceId, changes } = req.body;
      const result = await processSyncPush({
        uid: req.user.uid,
        deviceId: deviceId || req.user.deviceId || "web_client",
        changes: changes || []
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.get("/pull", async (req, res, next) => {
    try {
      const cursor = Number(req.query.cursor) || 0;
      const limit = Number(req.query.limit) || 100;
      const result = await processSyncPull({
        uid: req.user.uid,
        cursor,
        limit
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = { createSyncRouter };
