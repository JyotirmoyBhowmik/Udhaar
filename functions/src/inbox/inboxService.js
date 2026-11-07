/**
 * Inbox Notification & Counterparty Actions Service (functions/src/inbox/inboxService.js)
 * Implements Stage S7 Inbox:
 * 1. Counterparty events (payment claims, date proposals, verification notices).
 * 2. Interactive action decisions (approve/decline payment claims with ledger balance sync).
 * 3. Two-way push notifications on action resolutions.
 */

const crypto = require("crypto");
const { getRecord, updateRecord } = require("../ledger/recordService");
const { recordPayment } = require("../ledger/paymentService");
const { sendPushNotification } = require("../push/pushService");

// In-memory inbox store: uid -> Map(inboxId -> item)
const inMemoryUserInbox = new Map();

function getUserInboxMap(uid) {
  if (!inMemoryUserInbox.has(uid)) {
    inMemoryUserInbox.set(uid, new Map());
  }
  return inMemoryUserInbox.get(uid);
}

function resetInboxStore() {
  inMemoryUserInbox.clear();
}

/**
 * Create a new inbox notification item for a user
 */
async function createInboxItem({
  targetUid,
  senderUid = null,
  senderName = "Counterparty",
  remoteRecordId,
  type = "PAYMENT_CLAIM", // PAYMENT_CLAIM | DATE_PROPOSAL | VERIFICATION_CONFIRMED | DISPUTE_RAISED
  title,
  body,
  payload = {}
}) {
  if (!targetUid || !remoteRecordId || !title) {
    const err = new Error("targetUid, remoteRecordId, and title are required.");
    err.code = "ERR_INVALID_INPUT";
    err.status = 400;
    throw err;
  }

  const inboxId = `inb_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const now = Date.now();
  const inboxMap = getUserInboxMap(targetUid);

  const item = {
    inboxId,
    targetUid,
    senderUid,
    senderName,
    remoteRecordId,
    type,
    title,
    body: body || title,
    payload,
    isRead: false,
    status: "PENDING", // PENDING | APPROVED | DECLINED
    createdAtMs: now,
    updatedAtMs: now
  };

  inboxMap.set(inboxId, item);

  // Dispatch push notification
  await sendPushNotification({
    targetUid,
    title,
    body: item.body,
    data: {
      type: "INBOX_ITEM",
      inboxId,
      remoteRecordId
    }
  });

  return item;
}

/**
 * List inbox items for an authenticated user
 */
async function listUserInboxItems({ uid, unreadOnly = false }) {
  if (!uid) return [];
  const inboxMap = getUserInboxMap(uid);
  let items = Array.from(inboxMap.values());

  if (unreadOnly) {
    items = items.filter(i => !i.isRead);
  }

  return items.sort((a, b) => b.createdAtMs - a.createdAtMs);
}

/**
 * Mark an inbox item as read
 */
async function markInboxItemRead({ uid, inboxId }) {
  const inboxMap = getUserInboxMap(uid);
  const item = inboxMap.get(inboxId);
  if (!item) {
    const err = new Error(`Inbox item ${inboxId} not found.`);
    err.code = "ERR_INBOX_ITEM_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  item.isRead = true;
  item.updatedAtMs = Date.now();
  return { success: true, inboxId, isRead: true };
}

/**
 * Process a decision action on an inbox item (APPROVE or DECLINE)
 */
async function processInboxAction({ uid, inboxId, decision = "APPROVE", note = "" }) {
  const inboxMap = getUserInboxMap(uid);
  const item = inboxMap.get(inboxId);
  if (!item) {
    const err = new Error(`Inbox item ${inboxId} not found.`);
    err.code = "ERR_INBOX_ITEM_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (item.status !== "PENDING") {
    return {
      success: true,
      alreadyDecided: true,
      status: item.status
    };
  }

  const isApproved = decision.toUpperCase() === "APPROVE";
  const newStatus = isApproved ? "APPROVED" : "DECLINED";
  const now = Date.now();

  let ledgerResult = null;

  // Handle Payment Claim Approval
  if (item.type === "PAYMENT_CLAIM") {
    if (isApproved) {
      const amountPaise = Number(item.payload.amountPaise);
      if (amountPaise > 0) {
        ledgerResult = await recordPayment({
          uid,
          recordId: item.remoteRecordId,
          amountPaise,
          paymentMode: item.payload.paymentMode || "UPI",
          note: note || `Approved payment claim: ${item.payload.note || ""}`
        });
      }
    }

    // Notify sender of payment claim decision
    if (item.senderUid) {
      await sendPushNotification({
        targetUid: item.senderUid,
        title: isApproved ? "Payment Claim Approved" : "Payment Claim Declined",
        body: isApproved
          ? `Your payment of ₹${((item.payload.amountPaise || 0) / 100).toFixed(2)} was approved and recorded.`
          : `Your payment claim was declined. Please verify with ${item.senderName}.`,
        data: {
          type: "PAYMENT_CLAIM_DECISION",
          remoteRecordId: item.remoteRecordId,
          status: newStatus
        }
      });
    }
  }

  // Handle Date Proposal Approval
  if (item.type === "DATE_PROPOSAL") {
    if (isApproved && item.payload.proposedDateMs) {
      ledgerResult = await updateRecord({
        uid,
        recordId: item.remoteRecordId,
        updates: {
          dueDateMs: item.payload.proposedDateMs,
          note: `Due date updated per agreed proposal: ${item.payload.note || ""}`
        }
      });
    }

    if (item.senderUid) {
      await sendPushNotification({
        targetUid: item.senderUid,
        title: isApproved ? "Proposed Date Accepted" : "Proposed Date Declined",
        body: isApproved
          ? `Your proposed return date was accepted.`
          : `Your proposed return date was declined.`,
        data: {
          type: "DATE_PROPOSAL_DECISION",
          remoteRecordId: item.remoteRecordId,
          status: newStatus
        }
      });
    }
  }

  item.status = newStatus;
  item.isRead = true;
  item.decisionNote = note;
  item.resolvedAtMs = now;
  item.updatedAtMs = now;

  return {
    success: true,
    inboxId,
    status: newStatus,
    decision: isApproved ? "APPROVE" : "DECLINE",
    type: item.type,
    ledgerResult,
    paymentId: ledgerResult ? ledgerResult.paymentId : null
  };
}

module.exports = {
  resetInboxStore,
  createInboxItem,
  listUserInboxItems,
  markInboxItemRead,
  processInboxAction,
  _inMemoryUserInbox: inMemoryUserInbox
};
