/**
 * Inbox REST API Endpoints (functions/src/inbox/inboxEndpoints.js)
 * Implements Stage S7 Inbox API: notifications list, read flags, and approve/decline actions.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  listUserInboxItems,
  markInboxItemRead,
  processInboxAction
} = require("./inboxService");

function createInboxRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. List user inbox notifications (supports /inbox and /inbox/)
  router.get(["/", ""], async (req, res, next) => {
    try {
      const { unread } = req.query;
      const items = await listUserInboxItems({
        uid: req.user.uid,
        unreadOnly: unread === "true" || unread === "1"
      });
      res.status(200).json(items);
    } catch (err) {
      next(err);
    }
  });

  // 2. Mark notification as read
  router.post("/:inboxId/read", async (req, res, next) => {
    try {
      const { inboxId } = req.params;
      const result = await markInboxItemRead({
        uid: req.user.uid,
        inboxId
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 3. Process action (APPROVE or DECLINE payment claim / date proposal)
  router.post("/:inboxId/action", async (req, res, next) => {
    try {
      const { inboxId } = req.params;
      const { decision, note } = req.body || {};
      const result = await processInboxAction({
        uid: req.user.uid,
        inboxId,
        decision,
        note
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createInboxRouter
};
