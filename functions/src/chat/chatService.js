const crypto = require("crypto");
const { getChatRepository, assertRepoMethod } = require("../repositories");
const { getRecord, updateRecord } = require("../ledger/recordService");
const { sendPushNotification } = require("../push/pushService");

const VALID_MESSAGE_TYPES = [
  "TEXT",
  "PAYMENT_PROOF",
  "EXTENSION_REQUEST",
  "EXTENSION_RESPONSE",
  "SYSTEM_EVENT"
];

function chatRepo() {
  const repo = getChatRepository();
  assertRepoMethod(repo, "saveThread", "ChatRepository");
  assertRepoMethod(repo, "getThread", "ChatRepository");
  assertRepoMethod(repo, "listUserThreads", "ChatRepository");
  assertRepoMethod(repo, "findThreadByRecordAndParties", "ChatRepository");
  assertRepoMethod(repo, "saveMessage", "ChatRepository");
  assertRepoMethod(repo, "getMessage", "ChatRepository");
  assertRepoMethod(repo, "updateMessage", "ChatRepository");
  assertRepoMethod(repo, "listMessages", "ChatRepository");
  return repo;
}

async function getOrCreateChatThread({
  uid,
  recordId,
  counterpartyUid = null,
  partyUid = null,
  counterpartyName = "Contact",
  partyName = null
}) {
  if (!uid || !recordId) {
    const err = new Error("uid and recordId are required");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  const resolvedPartyUid = partyUid || counterpartyUid || null;
  const resolvedPartyName = partyName || counterpartyName || "Contact";

  const cRepo = chatRepo();
  const existing = await cRepo.findThreadByRecordAndParties(recordId, uid, resolvedPartyUid);
  if (existing) {
    const participants = new Set(existing.participants || existing.participantUids || []);
    participants.add(uid);
    if (resolvedPartyUid) participants.add(resolvedPartyUid);
    const participantList = Array.from(participants);
    const updated = {
      ...existing,
      participants: participantList,
      participantUids: participantList,
      updatedAtMs: Date.now()
    };
    await cRepo.saveThread(existing.threadId, updated);
    return updated;
  }

  const threadId = "th_" + crypto.randomBytes(8).toString("hex");
  const now = Date.now();
  const participantList = [uid, ...(resolvedPartyUid ? [resolvedPartyUid] : [])];
  const thread = {
    threadId,
    recordId,
    creatorUid: uid,
    ownerUid: uid,
    partyUid: resolvedPartyUid,
    counterpartyUid: resolvedPartyUid,
    partyName: resolvedPartyName,
    counterpartyName: resolvedPartyName,
    participants: participantList,
    participantUids: participantList,
    mutedBy: {},
    messageCount: 0,
    lastMessageAtMs: now,
    createdAtMs: now,
    updatedAtMs: now
  };

  await cRepo.saveThread(threadId, thread);
  return thread;
}

async function listUserChatThreads(uidOrParams) {
  const uid = typeof uidOrParams === "object" && uidOrParams !== null ? uidOrParams.uid : uidOrParams;
  const cRepo = chatRepo();
  return await cRepo.listUserThreads(uid);
}

async function postChatMessage({
  uid,
  threadId,
  senderName = "User",
  messageType = "TEXT",
  bodyText = "",
  attachment = null,
  extensionData = null
}) {
  if (!uid || !threadId) {
    const err = new Error("uid and threadId are required");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  if (!VALID_MESSAGE_TYPES.includes(messageType)) {
    const err = new Error(`Invalid messageType: ${messageType}`);
    err.code = "ERR_INVALID_MESSAGE_TYPE";
    err.status = 400;
    throw err;
  }

  const cRepo = chatRepo();
  const thread = await cRepo.getThread(threadId);
  if (!thread) {
    const err = new Error("Chat thread not found");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const participantList = thread.participants || thread.participantUids || [];
  if (participantList.length > 0 && !participantList.includes(uid) && thread.creatorUid !== uid && thread.ownerUid !== uid) {
    const err = new Error("User is not a participant in this chat thread");
    err.code = "ERR_FORBIDDEN";
    err.status = 403;
    throw err;
  }

  if (messageType === "PAYMENT_PROOF") {
    if (!attachment || (!attachment.utr && !attachment.mediaUrl && !attachment.fileUrl && !attachment.url && !attachment.fileHash && !attachment.storagePath)) {
      const err = new Error("PAYMENT_PROOF requires attachment with utr, mediaUrl, fileUrl, fileHash, or storagePath");
      err.code = "ERR_MISSING_PROOF_ATTACHMENT";
      err.status = 400;
      throw err;
    }
  }

  let normalizedExtension = null;
  if (messageType === "EXTENSION_REQUEST") {
    const proposedDueDateMs = Number(extensionData?.proposedDueDateMs || 0);
    if (!proposedDueDateMs || proposedDueDateMs <= 0) {
      const err = new Error("EXTENSION_REQUEST requires a valid proposedDueDateMs");
      err.code = "ERR_INVALID_EXTENSION_DATE";
      err.status = 400;
      throw err;
    }
    normalizedExtension = {
      proposedDueDateMs,
      reason: extensionData?.reason || "",
      status: "PENDING",
      requestedByUid: uid,
      requestedAtMs: Date.now()
    };
  }

  const now = Date.now();
  const messageId = "msg_" + crypto.randomBytes(8).toString("hex");
  const message = {
    messageId,
    threadId,
    recordId: thread.recordId,
    senderUid: uid,
    senderName,
    messageType,
    bodyText: String(bodyText || "").slice(0, 2000),
    attachment: attachment || null,
    extensionData: normalizedExtension,
    ...(normalizedExtension ? {
      extensionStatus: "PENDING",
      proposedDueDateMs: normalizedExtension.proposedDueDateMs
    } : {}),
    createdAtMs: now
  };

  await cRepo.saveMessage(threadId, messageId, message);

  const updatedThread = {
    ...thread,
    messageCount: (thread.messageCount || 0) + 1,
    lastMessageAtMs: now,
    lastMessagePreview: message.bodyText || messageType,
    updatedAtMs: now
  };
  await cRepo.saveThread(threadId, updatedThread);

  const mutedBy = thread.mutedBy || {};
  const recipients = participantList.filter(p => p && p !== uid && !mutedBy[p]);
  for (const recipientUid of recipients) {
    try {
      await sendPushNotification({
        targetUid: recipientUid,
        title: `New message from ${senderName}`,
        body: message.bodyText || `Sent a ${messageType}`,
        data: {
          type: "CHAT_MESSAGE",
          threadId,
          recordId: thread.recordId,
          messageId
        }
      });
    } catch (_) {
      // Non-fatal push error
    }
  }

  return message;
}

async function respondToExtensionRequest({
  uid,
  threadId,
  messageId,
  decision, // "APPROVE" | "ACCEPT" | "DECLINE"
  responderName = "User"
}) {
  if (!["APPROVE", "ACCEPT", "DECLINE"].includes(decision)) {
    const err = new Error("decision must be APPROVE, ACCEPT, or DECLINE");
    err.code = "ERR_INVALID_DECISION";
    err.status = 400;
    throw err;
  }

  const cRepo = chatRepo();
  const thread = await cRepo.getThread(threadId);
  if (!thread) {
    const err = new Error("Chat thread not found");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const origMsg = await cRepo.getMessage(threadId, messageId);
  if (!origMsg || origMsg.messageType !== "EXTENSION_REQUEST" || !origMsg.extensionData) {
    const err = new Error("Extension request message not found");
    err.code = "ERR_EXTENSION_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (origMsg.senderUid === uid) {
    const err = new Error("Cannot respond to your own extension request");
    err.code = "ERR_SELF_DECISION";
    err.status = 403;
    throw err;
  }

  if (origMsg.extensionData.status !== "PENDING" && origMsg.extensionStatus !== "PENDING") {
    const err = new Error("Extension request already resolved");
    err.code = "ERR_EXTENSION_ALREADY_RESOLVED";
    err.status = 409;
    throw err;
  }

  const isAccepted = decision === "APPROVE" || decision === "ACCEPT";
  const newStatus = isAccepted ? "APPROVED" : "DECLINED";
  const extensionStatusLabel = isAccepted ? "ACCEPTED" : "DECLINED";
  const updatedExtensionData = {
    ...origMsg.extensionData,
    status: newStatus,
    respondedByUid: uid,
    respondedAtMs: Date.now()
  };

  const updatedMessage = await cRepo.updateMessage(threadId, messageId, {
    extensionData: updatedExtensionData,
    extensionStatus: extensionStatusLabel,
    updatedAtMs: Date.now()
  });

  let updatedRecord = null;
  if (isAccepted && thread.recordId) {
    const candidateUids = Array.from(
      new Set([thread.creatorUid, thread.ownerUid, uid, ...(thread.participants || thread.participantUids || [])].filter(Boolean))
    );
    for (const candidateUid of candidateUids) {
      try {
        const rec = await getRecord({ uid: candidateUid, recordId: thread.recordId });
        if (rec) {
          const nextRec = await updateRecord({
            uid: candidateUid,
            recordId: thread.recordId,
            updates: { dueDateMs: origMsg.extensionData.proposedDueDateMs }
          });
          if (!updatedRecord) updatedRecord = nextRec;
        }
      } catch (_) {
        // Record doesn't belong to this candidateUid
      }
    }
  }

  const responseMessage = await postChatMessage({
    uid,
    threadId,
    senderName: responderName,
    messageType: "EXTENSION_RESPONSE",
    bodyText: isAccepted
      ? `Approved due date extension to ${new Date(origMsg.extensionData.proposedDueDateMs).toISOString().slice(0, 10)}`
      : "Declined due date extension request"
  });

  return {
    ...updatedMessage,
    extensionStatus: extensionStatusLabel,
    updatedMessage: {
      ...updatedMessage,
      extensionStatus: extensionStatusLabel
    },
    responseMessage,
    updatedRecord
  };
}

async function listThreadMessages({ uid = null, threadId, limit = 50, beforeMs = null }) {
  const cRepo = chatRepo();
  const thread = await cRepo.getThread(threadId);
  if (!thread) {
    const err = new Error("Chat thread not found");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }
  return await cRepo.listMessages(threadId, limit, beforeMs);
}

async function listChatMessages(threadId, limit = 100) {
  const cRepo = chatRepo();
  const thread = await cRepo.getThread(threadId);
  if (!thread) {
    const err = new Error("Chat thread not found");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }
  const messages = await cRepo.listMessages(threadId, limit);
  return { thread, messages };
}

async function toggleThreadMute({ uid, threadId, isMuted = true }) {
  const cRepo = chatRepo();
  const thread = await cRepo.getThread(threadId);
  if (!thread) {
    const err = new Error("Chat thread not found");
    err.code = "ERR_THREAD_NOT_FOUND";
    err.status = 404;
    throw err;
  }
  const mutedBy = { ...(thread.mutedBy || {}), [uid]: Boolean(isMuted) };
  const updated = await cRepo.saveThread(threadId, {
    ...thread,
    mutedBy,
    isMuted: Boolean(isMuted),
    updatedAtMs: Date.now()
  });
  return updated;
}

async function clearAllChatStore() {
  const cRepo = chatRepo();
  assertRepoMethod(cRepo, "clear", "ChatRepository");
  await cRepo.clear();
}

const resetChatStore = clearAllChatStore;

module.exports = {
  VALID_MESSAGE_TYPES,
  getOrCreateChatThread,
  listUserChatThreads,
  postChatMessage,
  respondToExtensionRequest,
  listThreadMessages,
  listChatMessages,
  toggleThreadMute,
  clearAllChatStore,
  resetChatStore
};
