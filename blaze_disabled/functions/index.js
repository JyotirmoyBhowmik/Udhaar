const {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { defineString } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onRequest, onCall, HttpsError } = require("firebase-functions/v2/https");
const { getStorage } = require("firebase-admin/storage");
const { getMessaging } = require("firebase-admin/messaging");
const {
  maskPhoneNumber,
  maskUpiId,
  getCoarseDeviceType,
  computeContentHashServer,
  writeAuditEntry,
  writeInboxItem,
  sendPushToCreator,
  validateAndRateLimitWebLink,
  verifyGuestPhoneOtp,
} = require("./guestWebFlow");

initializeApp();

// Enforce asia-south1 region across all Cloud Functions v2 triggers and HTTP handlers
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

/**
 * Environment parameter for the named Cloud Firestore database ID.
 * Defaults to the current Udhaar database ID so it works out of the box,
 * and can be overridden via `.env` or during `firebase deploy --only functions`.
 */
const DATABASE_ID = defineString("FIRESTORE_DATABASE_ID", {
  default: "ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf",
  description: "Named Cloud Firestore database ID for Udhaar",
});

const ANDROID_CHANNEL_ID = "udhaar_chat_messages_channel";

/**
 * Sends a high-priority FCM push notification to `recipientUid` and automatically
 * cleans up expired or unregistered FCM tokens in `/user_profiles/{recipientUid}`.
 */
async function sendPushToUser(db, recipientUid, title, body, dataPayload) {
  if (!recipientUid) return;
  const profileRef = db.collection("user_profiles").doc(recipientUid);
  const profileSnap = await profileRef.get();
  if (!profileSnap.exists) return;

  const fcmToken = (profileSnap.data().fcmToken || "").trim();
  if (!fcmToken) return;

  try {
    await getMessaging().send({
      token: fcmToken,
      notification: {
        title,
        body,
      },
      data: {
        ...dataPayload,
        title,
        body,
      },
      android: {
        priority: "high",
        notification: {
          channelId: ANDROID_CHANNEL_ID,
          clickAction: "android.intent.action.MAIN",
        },
      },
    });
  } catch (err) {
    const code = err && err.code ? String(err.code) : "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      await profileRef.update({
        fcmToken: "",
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      console.error(`FCM send failed for user ${recipientUid}:`, err);
    }
  }
}

/**
 * Trigger 1: Sends an FCM push notification to the counterparty whenever a new message,
 * quick reply, payment proof, or due-date extension request is created in
 * `/chat_threads/{threadId}/messages/{messageId}`.
 */
exports.onNewChatMessageSendPush = onDocumentCreated(
  {
    database: DATABASE_ID,
    document: "chat_threads/{threadId}/messages/{messageId}",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const msg = snap.data();
    const threadId = event.params.threadId;
    const senderUid = msg.senderUid || "";
    const senderName = msg.senderName || "Udhaar Contact";
    const participantUids = Array.isArray(msg.participantUids) ? msg.participantUids : [];
    const recipientUids = participantUids.filter((uid) => uid && uid !== senderUid);
    if (recipientUids.length === 0) return;

    const db = getFirestore(DATABASE_ID.value());
    const threadSnap = await db.collection("chat_threads").doc(threadId).get();
    const threadData = threadSnap.exists ? threadSnap.data() : {};
    const mutedByUids = Array.isArray(threadData.mutedByUids) ? threadData.mutedByUids : [];
    const blockedByUid = threadData.blockedByUid || "";
    const remoteRecordId = threadData.remoteRecordId || "";
    const partyName = threadData.partyName || senderName;

    const messageType = (msg.messageType || "TEXT").toUpperCase();
    const title =
      messageType === "EXTENSION_REQUEST"
        ? `Due Date Extension Request • ${senderName}`
        : messageType === "PAYMENT_PROOF"
          ? `Payment Proof Shared • ${senderName}`
          : `New Message from ${senderName}`;
    const body = msg.bodyText || "Tap to open chat in Udhaar.";

    for (const recipientUid of recipientUids) {
      if (mutedByUids.includes(recipientUid) || blockedByUid === recipientUid) {
        continue;
      }
      await sendPushToUser(db, recipientUid, title, body, {
        type: messageType === "EXTENSION_REQUEST" ? "EXTENSION_REQUEST" : "CHAT_MESSAGE",
        threadKey: threadId,
        remoteId: remoteRecordId,
        partyName,
        senderName,
        messageType,
      });
    }
  }
);

/**
 * Trigger 2: Sends an FCM push notification when a due-date extension request inside
 * `/chat_threads/{threadId}/messages/{messageId}` is accepted or declined.
 */
exports.onChatMessageExtensionUpdatedPush = onDocumentUpdated(
  {
    database: DATABASE_ID,
    document: "chat_threads/{threadId}/messages/{messageId}",
  },
  async (event) => {
    const beforeSnap = event.data ? event.data.before : null;
    const afterSnap = event.data ? event.data.after : null;
    if (!beforeSnap || !afterSnap || !beforeSnap.exists || !afterSnap.exists) return;

    const beforeMsg = beforeSnap.data();
    const afterMsg = afterSnap.data();
    if (
      beforeMsg.extensionStatus === afterMsg.extensionStatus ||
      (afterMsg.extensionStatus !== "ACCEPTED" && afterMsg.extensionStatus !== "DECLINED")
    ) {
      return;
    }

    const threadId = event.params.threadId;
    const requesterUid = (afterMsg.senderUid || "").trim();
    if (!requesterUid) return;

    const db = getFirestore(DATABASE_ID.value());
    const threadSnap = await db.collection("chat_threads").doc(threadId).get();
    const threadData = threadSnap.exists ? threadSnap.data() : {};
    const remoteRecordId = threadData.remoteRecordId || "";
    const responderName = threadData.partyName || "Counterparty";

    const accepted = afterMsg.extensionStatus === "ACCEPTED";
    const title = accepted
      ? `Extension Accepted • ${responderName}`
      : `Extension Declined • ${responderName}`;
    const body = accepted
      ? `${responderName} accepted your proposed due date extension.`
      : `${responderName} declined your proposed due date extension.`;

    await sendPushToUser(db, requesterUid, title, body, {
      type: "EXTENSION_REQUEST",
      threadKey: threadId,
      remoteId: remoteRecordId,
      partyName: responderName,
      senderName: responderName,
      messageType: "EXTENSION_REQUEST",
    });
  }
);

/**
 * Trigger 3: Sends an FCM push notification when a shared verification record in
 * `/shared_records/{remoteId}` is created, confirmed, disputed, edited, or settled.
 */
exports.onSharedRecordVerificationPush = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "shared_records/{remoteId}",
  },
  async (event) => {
    const afterSnap = event.data ? event.data.after : null;
    if (!afterSnap || !afterSnap.exists) return;

    const beforeData =
      event.data.before && event.data.before.exists ? event.data.before.data() : null;
    const record = afterSnap.data();
    const remoteId = event.params.remoteId;
    const db = getFirestore(DATABASE_ID.value());

    let targetUid = "";
    let eventType = "VERIFICATION_REQUEST";
    let senderName = record.lenderName || "Udhaar User";
    let title = "";
    let body = "";

    if (!beforeData) {
      // Newly created shared record: notify counterparty if already linked
      targetUid = (record.counterpartyUid || "").trim();
      senderName = record.creatorRole === "GIVEN" ? record.lenderName : record.borrowerName;
      eventType = "VERIFICATION_REQUEST";
      title = `Verification Requested • ${senderName}`;
      body = `${senderName} shared a ${record.currency} ${record.principalAmount} record for confirmation.`;
    } else if (beforeData.verificationStatus !== record.verificationStatus) {
      // Status changed (CONFIRMED, DISPUTED, or EDITED_AFTER_CONFIRM)
      if (record.verificationStatus === "CONFIRMED") {
        targetUid = (record.creatorUid || "").trim();
        senderName = record.creatorRole === "GIVEN" ? record.borrowerName : record.lenderName;
        eventType = "VERIFICATION_CONFIRMED";
        title = `Record Confirmed • ${senderName}`;
        body = `${senderName} confirmed the ${record.currency} ${record.principalAmount} record.`;
      } else if (record.verificationStatus === "DISPUTED") {
        targetUid = (record.creatorUid || "").trim();
        senderName = record.creatorRole === "GIVEN" ? record.borrowerName : record.lenderName;
        eventType = "VERIFICATION_DISPUTED";
        title = `Record Disputed • ${senderName}`;
        body = `${senderName} disputed the record: ${record.disputeReason || "Terms disputed"}`;
      } else if (record.verificationStatus === "EDITED_AFTER_CONFIRM") {
        targetUid = (record.counterpartyUid || "").trim();
        senderName = record.creatorRole === "GIVEN" ? record.lenderName : record.borrowerName;
        eventType = "VERIFICATION_REQUEST";
        title = `Record Updated • ${senderName}`;
        body = `${senderName} edited the ${record.currency} ${record.principalAmount} record. Please re-verify.`;
      } else {
        return;
      }
    } else if (!beforeData.isSettled && record.isSettled) {
      targetUid = (record.counterpartyUid || "").trim();
      senderName = record.creatorRole === "GIVEN" ? record.lenderName : record.borrowerName;
      eventType = "VERIFICATION_CONFIRMED";
      title = `Record Settled • ${senderName}`;
      body = `${senderName} marked the ${record.currency} ${record.principalAmount} record as settled.`;
    } else {
      return;
    }

    if (!targetUid) return;

    await sendPushToUser(db, targetUid, title, body, {
      type: eventType,
      remoteId,
      partyName: senderName,
      senderName,
    });
  }
);

/**
 * Trigger 4: Sends an FCM push notification when a partial repayment in
 * `/shared_records/{remoteId}/partial_payments/{paymentId}` is recorded or acknowledged.
 */
exports.onPartialPaymentWrittenPush = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "shared_records/{remoteId}/partial_payments/{paymentId}",
  },
  async (event) => {
    const afterSnap = event.data ? event.data.after : null;
    if (!afterSnap || !afterSnap.exists) return;

    const beforeData =
      event.data.before && event.data.before.exists ? event.data.before.data() : null;
    const payment = afterSnap.data();
    const remoteId = event.params.remoteId;
    const db = getFirestore(DATABASE_ID.value());

    const participantUids = Array.isArray(payment.participantUids) ? payment.participantUids : [];
    const recordedByUid = (payment.recordedByUid || "").trim();
    const recordedByName = payment.recordedByName || "Udhaar Contact";

    if (!beforeData) {
      // Newly recorded partial payment: notify the other participant
      const recipientUids = participantUids.filter((uid) => uid && uid !== recordedByUid);
      const title = `Payment Recorded • ${recordedByName}`;
      const body = `${recordedByName} recorded a payment of ${payment.amount} (${payment.paymentMode || "CASH"}).`;
      for (const recipientUid of recipientUids) {
        await sendPushToUser(db, recipientUid, title, body, {
          type: "PAYMENT_UPDATE",
          remoteId,
          partyName: recordedByName,
          senderName: recordedByName,
          messageType: "PAYMENT_PROOF",
        });
      }
    } else if (!beforeData.isAcknowledged && payment.isAcknowledged) {
      // Counterparty acknowledged the partial payment: notify the person who recorded it
      if (!recordedByUid) return;
      const title = "Payment Acknowledged";
      const body = `Your recorded payment of ${payment.amount} was acknowledged.`;
      await sendPushToUser(db, recordedByUid, title, body, {
        type: "PAYMENT_UPDATE",
        remoteId,
        partyName: recordedByName,
        senderName: recordedByName,
        messageType: "PAYMENT_PROOF",
      });
    }
  }
);

// Guest Web Flow Endpoints (Stage W1)
Object.assign(exports, require('./guestWebFlowEndpoints'));

// Udhaar Platform v2 Central API (Stage S1)
const { createApiApp } = require("./src/app");
const { ALL_SECRETS } = require("./src/config/secrets");
exports.api = onRequest({ region: "asia-south1", secrets: ALL_SECRETS }, createApiApp());

