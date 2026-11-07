/**
 * Chat REST API Endpoints (functions/src/chat/chatEndpoints.js)
 * Implements Stage S7 Chat API: threads, message stream, extensions, and mute.
 */

const express = require("express");
const { requireAuth } = require("../middleware/authMiddleware");
const {
  getOrCreateChatThread,
  listUserChatThreads,
  postChatMessage,
  listThreadMessages,
  respondToExtensionRequest,
  toggleThreadMute
} = require("./chatService");

function createChatRouter() {
  const router = express.Router();
  router.use(requireAuth);

  // 1. List user chat threads
  router.get("/threads", async (req, res, next) => {
    try {
      const threads = await listUserChatThreads({ uid: req.user.uid });
      res.status(200).json(threads);
    } catch (err) {
      next(err);
    }
  });

  // 2. Get or create thread for a record
  router.post("/threads", async (req, res, next) => {
    try {
      const { recordId, partyUid, partyName } = req.body || {};
      const thread = await getOrCreateChatThread({
        uid: req.user.uid,
        recordId,
        partyUid,
        partyName
      });
      res.status(201).json(thread);
    } catch (err) {
      next(err);
    }
  });

  // 3. List messages in a thread
  router.get("/threads/:threadId/messages", async (req, res, next) => {
    try {
      const { threadId } = req.params;
      const { limit, beforeMs } = req.query;
      const messages = await listThreadMessages({
        uid: req.user.uid,
        threadId,
        limit,
        beforeMs
      });
      res.status(200).json(messages);
    } catch (err) {
      next(err);
    }
  });

  // 4. Post a message to a thread
  router.post("/threads/:threadId/messages", async (req, res, next) => {
    try {
      const { threadId } = req.params;
      const { messageType, bodyText, attachment, extensionData } = req.body || {};
      const message = await postChatMessage({
        uid: req.user.uid,
        threadId,
        senderName: req.user.displayName || "Udhaar User",
        messageType,
        bodyText,
        attachment,
        extensionData
      });
      res.status(201).json(message);
    } catch (err) {
      next(err);
    }
  });

  // 5. Respond to an extension request (ACCEPT/DECLINE)
  router.post("/threads/:threadId/extension", async (req, res, next) => {
    try {
      const { threadId } = req.params;
      const { messageId, decision } = req.body || {};
      const result = await respondToExtensionRequest({
        uid: req.user.uid,
        threadId,
        messageId,
        decision
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  // 6. Toggle mute on a thread
  router.post("/threads/:threadId/mute", async (req, res, next) => {
    try {
      const { threadId } = req.params;
      const { isMuted } = req.body || {};
      const result = await toggleThreadMute({
        uid: req.user.uid,
        threadId,
        isMuted: isMuted !== false
      });
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = {
  createChatRouter
};
