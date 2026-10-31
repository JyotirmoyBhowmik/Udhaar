const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DATABASE_ID = "ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf";
const ANDROID_CHANNEL_ID = "udhaar_chat_messages_channel";

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

    const db = getFirestore(DATABASE_ID);
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
      const profileSnap = await db.collection("user_profiles").doc(recipientUid).get();
      if (!profileSnap.exists) continue;
      const fcmToken = (profileSnap.data().fcmToken || "").trim();
      if (!fcmToken) continue;

      await getMessaging().send({
        token: fcmToken,
        notification: {
          title,
          body,
        },
        data: {
          type: messageType === "EXTENSION_REQUEST" ? "EXTENSION_REQUEST" : "CHAT_MESSAGE",
          threadKey: threadId,
          remoteId: remoteRecordId,
          partyName,
          senderName,
          messageType,
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
    }
  }
);

/**
 * Trigger 2: Sends an FCM push notification when a shared verification record in
 * `/shared_records/{remoteId}` is created or when the counterparty confirms/disputes it.
 */
exports.onSharedRecordVerificationPush = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "shared_records/{remoteId}",
  },
  async (event) => {
    const afterSnap = event.data ? event.data.after : null;
    if (!afterSnap || !afterSnap.exists) return;

    const beforeData = event.data.before && event.data.before.exists ? event.data.before.data() : null;
    const record = afterSnap.data();
    const remoteId = event.params.remoteId;
    const db = getFirestore(DATABASE_ID);

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
      // Status changed (e.g. CONFIRMED or DISPUTED by counterparty): notify creator
      targetUid = (record.creatorUid || "").trim();
      senderName = record.creatorRole === "GIVEN" ? record.borrowerName : record.lenderName;
      if (record.verificationStatus === "CONFIRMED") {
        eventType = "VERIFICATION_CONFIRMED";
        title = `Record Confirmed • ${senderName}`;
        body = `${senderName} confirmed the ${record.currency} ${record.principalAmount} record.`;
      } else if (record.verificationStatus === "DISPUTED") {
        eventType = "VERIFICATION_DISPUTED";
        title = `Record Disputed • ${senderName}`;
        body = `${senderName} disputed the record: ${record.disputeReason || "Terms disputed"}`;
      } else {
        return;
      }
    } else {
      return;
    }

    if (!targetUid) return;
    const profileSnap = await db.collection("user_profiles").doc(targetUid).get();
    if (!profileSnap.exists) return;
    const fcmToken = (profileSnap.data().fcmToken || "").trim();
    if (!fcmToken) return;

    await getMessaging().send({
      token: fcmToken,
      notification: {
        title,
        body,
      },
      data: {
        type: eventType,
        remoteId,
        partyName: senderName,
        senderName,
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
  }
);
