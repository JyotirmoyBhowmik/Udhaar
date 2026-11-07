const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  createInboxItem,
  listInboxItems,
  markInboxItemRead,
  processInboxAction
} = require("./inboxService");

function createInboxRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // List inbox items
  router.get(["/", "/inbox"], async (req, res, next) => {
    try {
      const items = await listInboxItems(req.user.uid, {
        status: req.query.status || null,
        unreadOnly: req.query.unreadOnly === "true"
      });
      return res.status(200).json(items);
    } catch (err) {
      return next(err);
    }
  });

  // Create inbox item
  router.post(["/", "/inbox"], async (req, res, next) => {
    try {
      const item = await createInboxItem({
        uid: req.body.targetUid || req.user.uid,
        senderUid: req.user.uid,
        senderName: req.body.senderName || "User",
        recordId: req.body.recordId || req.body.remoteRecordId || null,
        type: req.body.type || "RECORD_APPROVAL_REQUEST",
        title: req.body.title || "Notification",
        body: req.body.body || "",
        metadata: req.body.metadata || req.body.payload || {}
      });
      return res.status(201).json(item);
    } catch (err) {
      return next(err);
    }
  });

  // Mark item read
  router.post(["/:itemId/read", "/inbox/:itemId/read"], async (req, res, next) => {
    try {
      const item = await markInboxItemRead(req.user.uid, req.params.itemId);
      return res.status(200).json(item);
    } catch (err) {
      return next(err);
    }
  });

  // Process inbox action
  router.post(["/:itemId/action", "/inbox/:itemId/action"], async (req, res, next) => {
    try {
      const result = await processInboxAction({
        uid: req.user.uid,
        itemId: req.params.itemId,
        action: req.body.action || req.body.decision,
        note: req.body.note || ""
      });
      return res.status(200).json(result);
    } catch (err) {
      return next(err);
    }
  });

  return router;
}

module.exports = {
  createInboxRouter
};
