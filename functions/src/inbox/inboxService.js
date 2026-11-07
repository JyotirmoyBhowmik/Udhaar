const crypto = require("crypto");
const { getInboxRepository, assertRepoMethod } = require("../repositories");
const { getRecord, updateRecord, settleRecord } = require("../ledger/recordService");
const { recordPayment } = require("../ledger/paymentService");

function inboxRepo() {
  const repo = getInboxRepository();
  assertRepoMethod(repo, "addItem", "InboxRepository");
  assertRepoMethod(repo, "getItem", "InboxRepository");
  assertRepoMethod(repo, "listItems", "InboxRepository");
  assertRepoMethod(repo, "updateItem", "InboxRepository");
  return repo;
}

async function createInboxItem({
  uid,
  targetUid,
  senderUid = null,
  senderName = "Counterparty",
  recordId = null,
  remoteRecordId = null,
  type = "RECORD_APPROVAL_REQUEST",
  title = "Action Required",
  body = "",
  metadata = {},
  payload = null
}) {
  const resolvedUid = targetUid || uid;
  const resolvedRecordId = remoteRecordId || recordId || null;
  const resolvedMetadata = payload ? { ...metadata, ...payload } : metadata;

  if (!resolvedUid) {
    const err = new Error("uid or targetUid is required to create an inbox item");
    err.code = "ERR_INVALID_ARGS";
    err.status = 400;
    throw err;
  }

  const iRepo = inboxRepo();
  const itemId = "inb_" + crypto.randomBytes(8).toString("hex");
  const now = Date.now();
  const item = {
    id: itemId,
    inboxId: itemId,
    itemId,
    uid: resolvedUid,
    targetUid: resolvedUid,
    senderUid,
    senderName,
    recordId: resolvedRecordId,
    remoteRecordId: resolvedRecordId,
    type,
    title,
    body,
    metadata: resolvedMetadata,
    payload: resolvedMetadata,
    status: "PENDING",
    read: false,
    isRead: false,
    createdAtMs: now,
    updatedAtMs: now
  };

  await iRepo.addItem(resolvedUid, item);
  return item;
}

async function listInboxItems(uidOrParams, options = {}) {
  const uid = typeof uidOrParams === "object" && uidOrParams !== null ? uidOrParams.uid : uidOrParams;
  const opts = typeof uidOrParams === "object" && uidOrParams !== null ? uidOrParams : options;
  const iRepo = inboxRepo();
  let items = await iRepo.listItems(uid, Boolean(opts.unreadOnly));
  if (opts.status) {
    items = items.filter(i => i.status === opts.status);
  }
  return items;
}

async function listUserInboxItems(uidOrParams) {
  const items = await listInboxItems(uidOrParams);
  // Support both Array.isArray(result) and result.items
  items.items = items;
  items.unreadCount = items.filter(i => !i.read && !i.isRead).length;
  return items;
}

async function markInboxItemRead(uidOrParams, itemIdArg) {
  let uid = uidOrParams;
  let itemId = itemIdArg;
  if (typeof uidOrParams === "object" && uidOrParams !== null) {
    uid = uidOrParams.uid;
    itemId = uidOrParams.itemId || uidOrParams.inboxId || uidOrParams.id;
  }
  const iRepo = inboxRepo();
  const existing = await iRepo.getItem(uid, itemId);
  if (!existing) {
    const err = new Error("Inbox item not found");
    err.code = "ERR_INBOX_NOT_FOUND";
    err.status = 404;
    throw err;
  }
  return await iRepo.updateItem(uid, itemId, {
    read: true,
    isRead: true,
    readAtMs: Date.now()
  });
}

async function processInboxAction({
  uid,
  itemId,
  inboxId,
  action, // "APPROVE" | "ACCEPT" | "REJECT" | "DISPUTE" | "DISMISS"
  decision,
  note = ""
}) {
  const resolvedId = itemId || inboxId;
  const resolvedAction = action || decision;
  if (!["APPROVE", "ACCEPT", "REJECT", "DISPUTE", "DISMISS"].includes(resolvedAction)) {
    const err = new Error(`Invalid inbox action: ${resolvedAction}`);
    err.code = "ERR_INVALID_ACTION";
    err.status = 400;
    throw err;
  }

  const iRepo = inboxRepo();
  const item = await iRepo.getItem(uid, resolvedId);
  if (!item) {
    const err = new Error("Inbox item not found");
    err.code = "ERR_INBOX_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (item.status !== "PENDING") {
    const err = new Error(`Inbox item already processed with status ${item.status}`);
    err.code = "ERR_INBOX_ALREADY_PROCESSED";
    err.status = 409;
    throw err;
  }

  const now = Date.now();
  let updatedRecord = null;
  let paymentResult = null;
  const isApproved = resolvedAction === "APPROVE" || resolvedAction === "ACCEPT";
  const targetRecordId = item.recordId || item.remoteRecordId;
  const meta = item.metadata || item.payload || {};

  if (targetRecordId) {
    try {
      const rec = await getRecord({ uid, recordId: targetRecordId });
      if (rec) {
        if ((item.type === "RECORD_APPROVAL_REQUEST" || item.type === "CONFIRMATION_REQUEST") && isApproved) {
          updatedRecord = await updateRecord({
            uid,
            recordId: targetRecordId,
            updates: { status: "CONFIRMED", verificationStatus: "CONFIRMED" }
          });
        } else if (
          item.type === "RECORD_APPROVAL_REQUEST" ||
          item.type === "CONFIRMATION_REQUEST" ||
          item.type === "DISPUTE_ALERT"
        ) {
          if (resolvedAction === "DISPUTE" || resolvedAction === "REJECT") {
            updatedRecord = await updateRecord({
              uid,
              recordId: targetRecordId,
              updates: { status: "DISPUTED" }
            });
          }
        } else if (
          (item.type === "PAYMENT_APPROVAL_REQUEST" || item.type === "PAYMENT_CLAIM") &&
          isApproved
        ) {
          const amountPaise = Number(meta.amountPaise || 0);
          if (amountPaise > 0) {
            paymentResult = await recordPayment({
              uid,
              recordId: targetRecordId,
              amountPaise,
              paymentMode: meta.paymentMode || meta.method || "UPI",
              note: note || meta.note || "Approved via Inbox"
            });
            updatedRecord = await getRecord({ uid, recordId: targetRecordId });
          }
        } else if (item.type === "DUE_DATE_EXTENSION" && isApproved) {
          const proposedDueDateMs = Number(meta.proposedDueDateMs || 0);
          if (proposedDueDateMs > 0) {
            updatedRecord = await updateRecord({
              uid,
              recordId: targetRecordId,
              updates: { dueDateMs: proposedDueDateMs }
            });
          }
        }
      }
    } catch (_) {
      // Target record not found or already settled
    }
  }

  const nextStatus =
    isApproved ? "APPROVED" :
    resolvedAction === "REJECT" ? "REJECTED" :
    resolvedAction === "DISPUTE" ? "DISPUTED" : "DISMISSED";

  const updatedItem = await iRepo.updateItem(uid, resolvedId, {
    status: nextStatus,
    decision: resolvedAction,
    read: true,
    isRead: true,
    actionNote: note,
    processedAtMs: now
  });

  return {
    success: true,
    status: nextStatus,
    decision: resolvedAction,
    paymentId: paymentResult ? (paymentResult.paymentId || paymentResult.id) : undefined,
    inboxItem: updatedItem,
    updatedRecord,
    payment: paymentResult
  };
}

async function clearAllInboxStore() {
  const iRepo = inboxRepo();
  assertRepoMethod(iRepo, "clear", "InboxRepository");
  await iRepo.clear();
}

const resetInboxStore = clearAllInboxStore;
const createInboxNotification = createInboxItem;

module.exports = {
  createInboxItem,
  createInboxNotification,
  listInboxItems,
  listUserInboxItems,
  markInboxItemRead,
  processInboxAction,
  clearAllInboxStore,
  resetInboxStore
};
