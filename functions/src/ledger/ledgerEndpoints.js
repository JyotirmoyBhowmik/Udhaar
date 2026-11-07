/**
 * Ledger REST API Endpoints (functions/src/ledger/ledgerEndpoints.js)
 * Implements online CRUD for records, people, partial payments, plans, and audit verification.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  createRecord,
  getRecord,
  listRecords,
  updateRecord,
  settleRecord,
  deleteRecord
} = require("./recordService");
const {
  createPerson,
  getPerson,
  listPeople,
  updatePerson,
  deletePerson
} = require("./peopleService");
const {
  recordPayment,
  listPayments
} = require("./paymentService");
const {
  createInstallmentPlan,
  getPlanByRecordId,
  payInstallment
} = require("./planService");
const {
  verifyAuditChain,
  getSyncSignal
} = require("./eventService");

function createLedgerRouter() {
  const router = express.Router();

  // All ledger routes require authentication
  router.use(requireAuth);

  // -------------------------------------------------------------
  // Records Endpoints
  // -------------------------------------------------------------
  router.post("/records", async (req, res, next) => {
    try {
      const record = await createRecord({
        uid: req.user.uid,
        recordData: req.body,
        clientRevision: req.body.clientRevision || 1,
        hlc: req.headers["x-hlc"] || "",
        idempotencyKey: req.headers["x-idempotency-key"] || ""
      });
      res.setHeader("ETag", `W/"${record.serverRev}"`);
      res.status(201).json(record);
    } catch (err) {
      next(err);
    }
  });

  router.get("/records", async (req, res, next) => {
    try {
      const result = await listRecords({
        uid: req.user.uid,
        status: req.query.status,
        role: req.query.role,
        cursor: req.query.cursor,
        limit: req.query.limit
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.get("/records/:recordId", async (req, res, next) => {
    try {
      const record = await getRecord({
        uid: req.user.uid,
        recordId: req.params.recordId
      });
      res.setHeader("ETag", `W/"${record.serverRev}"`);
      res.json(record);
    } catch (err) {
      next(err);
    }
  });

  router.put("/records/:recordId", async (req, res, next) => {
    try {
      const ifMatch = req.headers["if-match"];
      let expectedRev = req.body.expectedRev;
      if (ifMatch) {
        const match = ifMatch.match(/"?(\d+)"?/);
        if (match) expectedRev = Number(match[1]);
      }

      const updated = await updateRecord({
        uid: req.user.uid,
        recordId: req.params.recordId,
        updates: req.body,
        expectedRev,
        hlc: req.headers["x-hlc"] || ""
      });
      res.setHeader("ETag", `W/"${updated.serverRev}"`);
      res.json(updated);
    } catch (err) {
      next(err);
    }
  });

  router.post("/records/:recordId/settle", async (req, res, next) => {
    try {
      const settled = await settleRecord({
        uid: req.user.uid,
        recordId: req.params.recordId,
        note: req.body.note || ""
      });
      res.json(settled);
    } catch (err) {
      next(err);
    }
  });

  router.delete("/records/:recordId", async (req, res, next) => {
    try {
      const result = await deleteRecord({
        uid: req.user.uid,
        recordId: req.params.recordId
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  // -------------------------------------------------------------
  // Payments Endpoints
  // -------------------------------------------------------------
  router.post("/records/:recordId/payments", async (req, res, next) => {
    try {
      const payment = await recordPayment({
        uid: req.user.uid,
        recordId: req.params.recordId,
        amountPaise: req.body.amountPaise,
        paymentMode: req.body.paymentMode || "UPI",
        note: req.body.note || "",
        idempotencyKey: req.headers["x-idempotency-key"] || ""
      });
      res.status(201).json(payment);
    } catch (err) {
      next(err);
    }
  });

  router.get("/records/:recordId/payments", async (req, res, next) => {
    try {
      const payments = await listPayments({
        uid: req.user.uid,
        recordId: req.params.recordId
      });
      res.json({ payments });
    } catch (err) {
      next(err);
    }
  });

  // -------------------------------------------------------------
  // Installment Plans Endpoints
  // -------------------------------------------------------------
  router.post("/records/:recordId/plans", async (req, res, next) => {
    try {
      const plan = await createInstallmentPlan({
        uid: req.user.uid,
        recordId: req.params.recordId,
        totalInstallments: req.body.totalInstallments || 3,
        startDateMs: req.body.startDateMs,
        frequency: req.body.frequency || "MONTHLY"
      });
      res.status(201).json(plan);
    } catch (err) {
      next(err);
    }
  });

  router.get("/records/:recordId/plans", async (req, res, next) => {
    try {
      const plan = await getPlanByRecordId({
        uid: req.user.uid,
        recordId: req.params.recordId
      });
      res.json(plan);
    } catch (err) {
      next(err);
    }
  });

  router.post("/plans/:planId/installments/:installmentId/pay", async (req, res, next) => {
    try {
      const result = await payInstallment({
        uid: req.user.uid,
        planId: req.params.planId,
        installmentId: req.params.installmentId,
        paymentMode: req.body.paymentMode || "UPI"
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  // -------------------------------------------------------------
  // People Endpoints
  // -------------------------------------------------------------
  router.post("/people", async (req, res, next) => {
    try {
      const person = await createPerson({
        uid: req.user.uid,
        personData: req.body
      });
      res.status(201).json(person);
    } catch (err) {
      next(err);
    }
  });

  router.get("/people", async (req, res, next) => {
    try {
      const result = await listPeople({
        uid: req.user.uid,
        cursor: req.query.cursor,
        limit: req.query.limit
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.get("/people/:personId", async (req, res, next) => {
    try {
      const person = await getPerson({
        uid: req.user.uid,
        personId: req.params.personId
      });
      res.json(person);
    } catch (err) {
      next(err);
    }
  });

  router.put("/people/:personId", async (req, res, next) => {
    try {
      const updated = await updatePerson({
        uid: req.user.uid,
        personId: req.params.personId,
        updates: req.body
      });
      res.json(updated);
    } catch (err) {
      next(err);
    }
  });

  router.delete("/people/:personId", async (req, res, next) => {
    try {
      const result = await deletePerson({
        uid: req.user.uid,
        personId: req.params.personId
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  // -------------------------------------------------------------
  // Audit & Sync Verification
  // -------------------------------------------------------------
  router.get("/audit/verify", async (req, res, next) => {
    try {
      const auditResult = await verifyAuditChain(req.user.uid);
      const syncSignal = getSyncSignal(req.user.uid);
      res.json({
        audit: auditResult,
        syncSignal
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = { createLedgerRouter };
