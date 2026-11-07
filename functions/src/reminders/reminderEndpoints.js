/**
 * Reminders REST API Endpoints (functions/src/reminders/reminderEndpoints.js)
 * Implements Stage S7 Reminders API: policy, quiet hours, scheduling, snooze, and dispatch.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  getUserReminderPolicy,
  updateUserReminderPolicy,
  evaluatePendingReminders,
  snoozeRecordReminders,
  sendRecordReminder
} = require("./reminderService");

function createRemindersRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. Get user reminder policy & quiet hours
  router.get("/policy", async (req, res, next) => {
    try {
      const policy = await getUserReminderPolicy({ uid: req.user.uid });
      res.status(200).json(policy);
    } catch (err) {
      next(err);
    }
  });

  // 2. Update reminder policy (supports PUT or POST)
  const handleUpdatePolicy = async (req, res, next) => {
    try {
      const policy = await updateUserReminderPolicy({
        uid: req.user.uid,
        policyUpdates: req.body || {}
      });
      res.status(200).json(policy);
    } catch (err) {
      next(err);
    }
  };
  router.put("/policy", handleUpdatePolicy);
  router.post("/policy", handleUpdatePolicy);

  // 3. Evaluate due dates and list pending reminders
  router.post("/schedule", async (req, res, next) => {
    try {
      const result = await evaluatePendingReminders({
        uid: req.user.uid,
        nowMs: Date.now()
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 4. Snooze or stop reminders for a specific record
  router.post("/:recordId/snooze", async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const { snoozeDays, stop } = req.body || {};
      const result = await snoozeRecordReminders({
        uid: req.user.uid,
        recordId,
        snoozeDays,
        stop: Boolean(stop)
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 5. Send or format reminder for a specific record
  router.post("/:recordId/send", async (req, res, next) => {
    try {
      const { recordId } = req.params;
      const { locale, forceSendWhatsApp } = req.body || {};
      const result = await sendRecordReminder({
        uid: req.user.uid,
        recordId,
        locale,
        forceSendWhatsApp: Boolean(forceSendWhatsApp),
        nowMs: Date.now()
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createRemindersRouter
};
