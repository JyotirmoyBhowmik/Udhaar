/**
 * Backup & Disaster Recovery REST API Endpoints
 * (functions/src/backup/backupEndpoints.js)
 * Implements Stage S9 Backup Governance Endpoints:
 * - POST /api/v1/admin/backups/snapshot: Create on-demand point-in-time snapshot.
 * - GET /api/v1/admin/backups: List snapshots with SHA-256 manifests.
 * - POST /api/v1/admin/backups/:snapshotId/verify: Verify manifest integrity.
 * - POST /api/v1/admin/backups/:snapshotId/restore-drill: Run staging restore drill.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const { requireAdminRole } = require("../middleware/adminMiddleware");
const {
  createBackupSnapshot,
  listBackupSnapshots,
  verifySnapshotIntegrity,
  executeRestoreDrill
} = require("./backupService");
const { logAdminAction } = require("../admin/adminService");

function extractClientIp(req) {
  return (
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    "127.0.0.1"
  );
}

function createBackupRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. Trigger On-Demand Point-in-Time Snapshot (Admin, Operator)
  router.post("/snapshot", requireAdminRole(["ADMIN", "OPERATOR"]), async (req, res, next) => {
    try {
      const { type } = req.body || {};
      const snapshot = await createBackupSnapshot({
        type: type || "INCREMENTAL",
        triggeredBy: req.user.uid,
        adminReason: req.adminReason
      });

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "CREATE_BACKUP_SNAPSHOT",
        target: snapshot.snapshotId,
        reason: req.adminReason,
        clientIp: extractClientIp(req),
        metadata: { gcsPath: snapshot.gcsPath, manifestSha256: snapshot.manifestSha256 }
      });

      res.status(201).json(snapshot);
    } catch (err) {
      next(err);
    }
  });

  // 2. List Backup Snapshots (Admin, Operator, Auditor)
  router.get("/", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const { limit } = req.query;
      const snapshots = await listBackupSnapshots({ limit });
      res.status(200).json(snapshots);
    } catch (err) {
      next(err);
    }
  });

  // 3. Verify Snapshot SHA-256 Integrity (Admin, Operator, Auditor)
  router.post("/:snapshotId/verify", requireAdminRole(["ADMIN", "OPERATOR", "AUDITOR"]), async (req, res, next) => {
    try {
      const { snapshotId } = req.params;
      const result = await verifySnapshotIntegrity({ snapshotId });

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "VERIFY_BACKUP_SNAPSHOT",
        target: snapshotId,
        reason: req.adminReason || "Routine verification query",
        clientIp: extractClientIp(req),
        metadata: { status: result.status, isHashValid: result.isHashValid }
      });

      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. Execute Disaster Recovery Restore Drill (Admin Only)
  router.post("/:snapshotId/restore-drill", requireAdminRole(["ADMIN"]), async (req, res, next) => {
    try {
      const { snapshotId } = req.params;
      const { targetStagingDatabase } = req.body || {};

      const drillResult = await executeRestoreDrill({
        snapshotId,
        targetStagingDatabase,
        adminUid: req.user.uid,
        adminReason: req.adminReason
      });

      await logAdminAction({
        adminUid: req.user.uid,
        adminRole: req.adminRole,
        action: "EXECUTE_RESTORE_DRILL",
        target: snapshotId,
        reason: req.adminReason,
        clientIp: extractClientIp(req),
        metadata: {
          drillId: drillResult.drillId,
          targetStagingDatabase: drillResult.targetStagingDatabase,
          rtoAchievedMinutes: drillResult.rtoAchievedMinutes
        }
      });

      res.status(200).json(drillResult);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createBackupRouter
};
