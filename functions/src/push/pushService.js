/**
 * Push Notification & Device Registry Service (functions/src/push/pushService.js)
 * Implements device token management, RFC 8292 Web Push formatting, and FCM dispatch.
 */

const crypto = require("crypto");
const { getPushProvider } = require("../providers/push");

// In-memory devices store: uid -> Map(deviceId -> deviceData)
const inMemoryUserDevices = new Map();

function getUserDevicesMap(uid) {
  if (!inMemoryUserDevices.has(uid)) {
    inMemoryUserDevices.set(uid, new Map());
  }
  return inMemoryUserDevices.get(uid);
}

function resetDevicesStore() {
  inMemoryUserDevices.clear();
}

/**
 * Register or update a user device (Web Push subscription or Android FCM token)
 */
async function registerDevice({ uid, deviceData }) {
  if (!uid) {
    const err = new Error("User ID is required to register a device.");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (!deviceData || typeof deviceData !== "object") {
    const err = new Error("Device data is required.");
    err.code = "ERR_INVALID_INPUT";
    err.status = 400;
    throw err;
  }

  const token = deviceData.fcmToken || deviceData.token || deviceData.endpoint;
  if (!token) {
    const err = new Error("A valid device token or Web Push endpoint is required.");
    err.code = "ERR_MISSING_TOKEN";
    err.status = 400;
    throw err;
  }

  const deviceId = deviceData.deviceId || `dev_${crypto.randomBytes(6).toString("hex")}`;
  const now = Date.now();
  const devicesMap = getUserDevicesMap(uid);

  const entry = {
    deviceId,
    platform: String(deviceData.platform || "WEB").toUpperCase(), // WEB | ANDROID | IOS
    fcmToken: String(token).trim(),
    endpoint: deviceData.endpoint || null,
    keys: deviceData.keys || null, // p256dh, auth for Web Push
    userAgent: deviceData.userAgent || "",
    appVersion: deviceData.appVersion || "2.0.0",
    createdAtMs: deviceData.createdAtMs || now,
    updatedAtMs: now
  };

  devicesMap.set(deviceId, entry);

  return entry;
}

/**
 * List all registered devices for a user
 */
async function listUserDevices({ uid }) {
  if (!uid) return [];
  const devicesMap = getUserDevicesMap(uid);
  return Array.from(devicesMap.values()).map(d => ({
    deviceId: d.deviceId,
    platform: d.platform,
    createdAtMs: d.createdAtMs,
    updatedAtMs: d.updatedAtMs
  }));
}

/**
 * Revoke/delete a registered device
 */
async function revokeDevice({ uid, deviceId }) {
  if (!uid || !deviceId) return { success: false };
  const devicesMap = getUserDevicesMap(uid);
  const deleted = devicesMap.delete(deviceId);
  return { success: deleted, deviceId };
}

/**
 * Send push notification to all devices registered by target user
 */
async function sendPushNotification({
  targetUid,
  title,
  body,
  data = {},
  priority = "high"
}) {
  if (!targetUid || !title || !body) {
    return { success: false, reason: "Missing parameters" };
  }

  const pushProvider = getPushProvider();
  const devicesMap = getUserDevicesMap(targetUid);
  const devices = Array.from(devicesMap.values());

  const results = [];
  if (devices.length === 0) {
    // If no devices registered in memory, notify provider directly using targetUid
    const res = await pushProvider.sendPush({
      targetUid,
      title,
      body,
      data,
      priority
    });
    results.push(res);
  } else {
    for (const dev of devices) {
      const res = await pushProvider.sendPush({
        targetUid,
        token: dev.fcmToken,
        title,
        body,
        data,
        priority
      });
      results.push(res);
    }
  }

  return {
    success: results.some(r => r.success),
    dispatchedCount: results.length,
    results
  };
}

module.exports = {
  registerDevice,
  listUserDevices,
  revokeDevice,
  sendPushNotification,
  resetDevicesStore,
  _inMemoryUserDevices: inMemoryUserDevices
};
