/**
 * Push Notification Providers (functions/src/providers/push/index.js)
 * Implements FCM Push Provider and Fake Push Provider for tests and CI/CD.
 */

let admin;
try {
  admin = require("firebase-admin");
} catch (_) {
  admin = null;
}

class FakePushProvider {
  constructor() {
    this.sentPushes = [];
  }

  reset() {
    this.sentPushes = [];
  }

  async sendPush(request) {
    const messageId = `fake_push_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const entry = {
      messageId,
      targetUid: request.targetUid,
      title: request.title,
      body: request.body,
      data: request.data || {},
      priority: request.priority || "high",
      timestamp: Date.now(),
      status: "SENT"
    };

    this.sentPushes.push(entry);

    return {
      success: true,
      messageId,
      deliveryStatus: "SENT"
    };
  }

  getLastPushFor(targetUid) {
    const matches = this.sentPushes.filter(p => p.targetUid === targetUid);
    return matches.length > 0 ? matches[matches.length - 1] : null;
  }

  getSentPushes() {
    return [...this.sentPushes];
  }
}

class FcmPushProvider {
  constructor(databaseId = null) {
    this.databaseId = databaseId;
  }

  async sendPush(request) {
    if (!admin || !admin.apps || !admin.apps.length) {
      return { success: false, messageId: "", deliveryStatus: "SKIPPED_NO_ADMIN" };
    }

    try {
      const messaging = admin.messaging();
      const payload = {
        notification: {
          title: request.title,
          body: request.body
        },
        data: request.data || {},
        android: {
          priority: request.priority === "high" ? "high" : "normal",
          notification: {
            channelId: "udhaar_reminders_channel"
          }
        },
        webpush: {
          headers: {
            Urgency: request.priority === "high" ? "high" : "normal"
          },
          notification: {
            title: request.title,
            body: request.body,
            icon: "/assets/icons/icon-192.png",
            badge: "/assets/icons/badge-72.png"
          }
        }
      };

      if (request.token) {
        payload.token = request.token;
      } else if (request.topic) {
        payload.topic = request.topic;
      }

      const messageId = await messaging.send(payload);
      return { success: true, messageId, deliveryStatus: "SENT" };
    } catch (err) {
      console.warn("FCM push send error:", err.message);
      return { success: false, messageId: "", error: err.message, deliveryStatus: "FAILED" };
    }
  }
}

const fakePush = new FakePushProvider();
const prodPush = new FcmPushProvider();

function isTestOrEmulator() {
  return (
    process.env.NODE_ENV === "test" ||
    process.env.USE_FAKE_PROVIDERS === "true" ||
    (Array.isArray(process.argv) && process.argv.some(arg => typeof arg === "string" && (arg.includes("test") || arg.endsWith(".test.js"))))
  );
}

function getPushProvider() {
  return isTestOrEmulator() ? fakePush : prodPush;
}

module.exports = {
  FakePushProvider,
  FcmPushProvider,
  getPushProvider,
  fakePush
};
