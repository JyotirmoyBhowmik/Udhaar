/**
 * Counterparty Chat & Due Date Extension Service (functions/src/chat/chatService.js)
 * Implements Stage S7 Chat Threads:
 * 1. Multi-party conversation threads bound to shared ledger records.
 * 2. Message Types: TEXT, PAYMENT_PROOF, EXTENSION_REQUEST, QUICK_REPLY.
 * 3. Interactive Due Date Extension Requests (ACCEPT/DECLINE with ledger sync).
 * 4. Mute & Block enforcement.
 * 5. Automated Push Notification dispatch to recipient.
 */

const crypto = require("crypto");
const { getRecord, updateRecord } = require("../ledger/recordService");
const { sendPushNotification } = require("../push/pushService");

const { getChatRepository } = require("../repositories");

const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const repo = getChatRepository();
    const map = (repo && getMapFn(repo)) || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryThreads = proxyMap((repo) => repo.threads);
const _inMemoryMessages = proxyMap((repo) => repo.messages);

function getThreadsMap() {
  const repo = getChatRepository();
  return (repo && repo.threads) || { get: () => null, set: () => {}, values: () => [] };
}

function getThreadMessagesMap(threadId) {
  const repo = getChatRepository();
  if (repo && typeof repo.getThreadMessagesMap === "function") {
    return repo.getThreadMessagesMap(threadId);
  }
  return { get: () => null, set: () => {}, values: () => [] };
}

function resetChatStore() {
  const repo = getChatRepository();
  if (repo && typeof repo.clear === "function") {
    repo.clear();
  }
}

/**
 * Get or create a chat thread between counterparties for a record
 */
async function getOrCreateChatThread({ uid, recordId, partyUid = null, partyName = "Counterparty" }) {
  if (!uid || !recordId) {
    const err = new Error("User ID and Record ID are required.");
    err.code = "ERR_INVALID_INPUT";
    err.status = 400;
    throw err;
  }

  // Check if thread already exists for this record
  for (const thread of getThreadsMap().values()) {
    if (thread.remoteRecordId === recordId && thread.participantUids.includes(uid)) {
      return thread;
    }
  }

  const threadId = `th_${recordId}_${crypto.randomBytes(3).toString("hex")}`;
  const now = Date.now();
  const participantUids = [uid];
  if (partyUid && partyUid !== uid) {
    participantUids.push(partyUid);
  }

  const thread = {
    threadId,
    remoteRecordId: recordId,
    participantUids,
    partyName,
    mutedByUids: [],
    blockedByUid: null,
    lastMessage: null,
    createdAtMs: now,
    updatedAtMs: now
  };

  getThreadsMap().set(threadId, thread);
  return thread;
}

/**
 * List chat threads for an authenticated user
 */
async function listUserChatThreads({ uid }) {
  if (!uid) return [];
  const results = [];
  for (const thread of getThreadsMap().values()) {
    if (thread.participantUids.includes(uid)) {
      results.push({
        ...thread,
        isMuted: thread.mutedByUids.includes(uid),
        isBlocked: Boolean(thread.blockedByUid)
      });
    }
  }
  return results.sort((a, b) => b.updatedAtMs - a.updatedAtMs);
}

/**
 * Post a message to a thread
 */
async function postChatMessage({
  uid,
  threadId,
  senderName = "Udhaar User",
  messageType = "TEXT",
  bodyText = "",
  attachment = null,
  extensionData = null
}) {
  const thread = getThreadsMap().get(threadId);
  if (!thread || !thread.participantUids.includes(uid)) {
    const err = new Error(`Chat thread ${threadId} not found or access denied.`);
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (thread.blockedByUid) {
    const err = new Error("This conversation is blocked.");
    err.code = "ERR_CHAT_BLOCKED";
    err.status = 403;
    throw err;
  }

  const validTypes = ["TEXT", "PAYMENT_PROOF", "EXTENSION_REQUEST", "QUICK_REPLY"];
  const type = String(messageType || "TEXT").toUpperCase();
  if (!validTypes.includes(type)) {
    const err = new Error(`Invalid message type '${messageType}'.`);
    err.code = "ERR_INVALID_MESSAGE_TYPE";
    err.status = 400;
    throw err;
  }

  const now = Date.now();
  const messageId = `msg_${now}_${crypto.randomBytes(4).toString("hex")}`;

  const message = {
    messageId,
    threadId,
    senderUid: uid,
    senderName,
    participantUids: thread.participantUids,
    messageType: type,
    bodyText: String(bodyText || "").trim().slice(0, 1000),
    attachment: attachment || null,
    createdAtMs: now
  };

  // Extension request specifics
  if (type === "EXTENSION_REQUEST") {
    const proposedDueDateMs = Number(extensionData?.proposedDueDateMs);
    if (!Number.isFinite(proposedDueDateMs) || proposedDueDateMs <= now) {
      const err = new Error("Proposed due date must be a valid future timestamp.");
      err.code = "ERR_INVALID_EXTENSION_DATE";
      err.status = 400;
      throw err;
    }
    message.proposedDueDateMs = proposedDueDateMs;
    message.extensionStatus = "PENDING"; // PENDING | ACCEPTED | DECLINED
    message.note = extensionData?.note || "";
    message.bodyText = message.bodyText || `Due date extension requested to ${new Date(proposedDueDateMs).toLocaleDateString("en-IN")}.`;
  }

  const messagesMap = getThreadMessagesMap(threadId);
  messagesMap.set(messageId, message);

  // Update thread lastMessage
  thread.lastMessage = {
    messageId,
    senderName,
    messageType: type,
    bodyText: message.bodyText,
    createdAtMs: now
  };
  thread.updatedAtMs = now;

  // Dispatch push to recipients
  const recipients = thread.participantUids.filter(pUid => pUid !== uid && !thread.mutedByUids.includes(pUid));
  for (const recipientUid of recipients) {
    const title = type === "EXTENSION_REQUEST"
      ? `Due Date Extension Request • ${senderName}`
      : type === "PAYMENT_PROOF"
        ? `Payment Proof Shared • ${senderName}`
        : `New Message • ${senderName}`;

    await sendPushNotification({
      targetUid: recipientUid,
      title,
      body: message.bodyText,
      data: {
        type: "CHAT_MESSAGE",
        threadId,
        messageId,
        remoteRecordId: thread.remoteRecordId
      }
    });
  }

  return message;
}

/**
 * List messages in a thread with pagination
 */
async function listThreadMessages({ uid, threadId, limit = 50, beforeMs = null }) {
  const thread = getThreadsMap().get(threadId);
  if (!thread || !thread.participantUids.includes(uid)) {
    const err = new Error("Thread not found or access denied.");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const messagesMap = getThreadMessagesMap(threadId);
  let messages = Array.from(messagesMap.values());

  if (beforeMs) {
    messages = messages.filter(m => m.createdAtMs < Number(beforeMs));
  }

  messages.sort((a, b) => a.createdAtMs - b.createdAtMs);
  return messages.slice(-Math.min(100, Math.max(1, Number(limit) || 50)));
}

/**
 * Respond to an Extension Request (ACCEPT or DECLINE)
 */
async function respondToExtensionRequest({ uid, threadId, messageId, decision = "ACCEPT" }) {
  const thread = getThreadsMap().get(threadId);
  if (!thread || !thread.participantUids.includes(uid)) {
    const err = new Error("Thread not found or access denied.");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const messagesMap = getThreadMessagesMap(threadId);
  const message = messagesMap.get(messageId);
  if (!message || message.messageType !== "EXTENSION_REQUEST") {
    const err = new Error("Extension request message not found.");
    err.code = "ERR_MESSAGE_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (message.senderUid === uid) {
    const err = new Error("Cannot accept or decline your own extension request.");
    err.code = "ERR_SELF_DECISION";
    err.status = 400;
    throw err;
  }

  if (message.extensionStatus !== "PENDING") {
    return {
      success: true,
      alreadyDecided: true,
      extensionStatus: message.extensionStatus
    };
  }

  const isAccepted = decision.toUpperCase() === "ACCEPT";
  const newStatus = isAccepted ? "ACCEPTED" : "DECLINED";
  message.extensionStatus = newStatus;
  message.respondedByUid = uid;
  message.respondedAtMs = Date.now();

  // If accepted, update the linked record's due date on the ledger
  if (isAccepted && thread.remoteRecordId && message.proposedDueDateMs) {
    try {
      await updateRecord({
        uid,
        recordId: thread.remoteRecordId,
        updates: {
          dueDateMs: message.proposedDueDateMs,
          note: `Due date extended via counterparty chat agreement`
        }
      });
    } catch (_) {
      // Record may belong to counterparty, will update during sync
    }
  }

  // Notify original requester
  await sendPushNotification({
    targetUid: message.senderUid,
    title: isAccepted ? "Due Date Extension Accepted" : "Extension Request Declined",
    body: isAccepted
      ? `Your counterparty accepted the new due date (${new Date(message.proposedDueDateMs).toLocaleDateString("en-IN")}).`
      : `Your requested due date extension was declined.`,
    data: {
      type: "EXTENSION_DECISION",
      threadId,
      messageId,
      status: newStatus
    }
  });

  return {
    success: true,
    messageId,
    extensionStatus: newStatus,
    proposedDueDateMs: message.proposedDueDateMs
  };
}

/**
 * Mute or un-mute a chat thread
 */
async function toggleThreadMute({ uid, threadId, isMuted = true }) {
  const thread = getThreadsMap().get(threadId);
  if (!thread || !thread.participantUids.includes(uid)) {
    const err = new Error("Thread not found.");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (isMuted && !thread.mutedByUids.includes(uid)) {
    thread.mutedByUids.push(uid);
  } else if (!isMuted) {
    thread.mutedByUids = thread.mutedByUids.filter(id => id !== uid);
  }

  return { success: true, threadId, isMuted: thread.mutedByUids.includes(uid) };
}

module.exports = {
  resetChatStore,
  getOrCreateChatThread,
  listUserChatThreads,
  postChatMessage,
  listThreadMessages,
  respondToExtensionRequest,
  toggleThreadMute,
  _inMemoryThreads,
  _inMemoryMessages
};
