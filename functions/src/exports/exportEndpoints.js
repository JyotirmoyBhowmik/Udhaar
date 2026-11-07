/**
 * Data Exports REST API Endpoints (functions/src/exports/exportEndpoints.js)
 * Implements Stage S7 Exports: CSV, Printable Statement, and Full Portability Archive.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const { requireStepUpAuth } = require("../middleware/stepUpMiddleware");
const {
  generateLedgerCsv,
  generateStatementHtml,
  generateFullDataArchive
} = require("./exportService");

function createExportRouter() {
  const router = express.Router();
  router.use(requireAuth);
  router.use(requireStepUpAuth);

  // 1. Download CSV export of ledger
  router.get("/csv", async (req, res, next) => {
    try {
      const csv = await generateLedgerCsv({ uid: req.user.uid });
      const filename = `udhaar_ledger_${new Date().toISOString().split("T")[0]}.csv`;

      res.setHeader("Content-Type", "text/csv; charset=utf-8");
      res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
      res.status(200).send(csv);
    } catch (err) {
      next(err);
    }
  });

  // 2. View/Print HTML statement of account
  router.get("/statement", async (req, res, next) => {
    try {
      const { recordId, partyId } = req.query;
      const html = await generateStatementHtml({
        uid: req.user.uid,
        recordId,
        partyId
      });

      res.setHeader("Content-Type", "text/html; charset=utf-8");
      res.status(200).send(html);
    } catch (err) {
      next(err);
    }
  });

  // 3. Full Data Portability JSON backup (DPDP Act 2023)
  router.get("/full-backup", async (req, res, next) => {
    try {
      const archive = await generateFullDataArchive({ uid: req.user.uid });
      const filename = `udhaar_backup_${new Date().toISOString().split("T")[0]}.json`;

      res.setHeader("Content-Type", "application/json; charset=utf-8");
      res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
      res.status(200).json(archive);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createExportRouter
};
