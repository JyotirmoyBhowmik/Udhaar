const crypto = require("crypto");
const { getSessionRepository, assertRepoMethod } = require("../repositories");
const { getPushProvider } = require("../providers/push");

function sessionRepo() {
  const repo = getSessionRepository();
  assertRepoMethod(repo, "createDevice", "SessionRepository");
  assertRepoMethod(repo, "getDevice", "SessionRepository");
  assertRepoMethod(repo, "listUserDevices", "SessionRepository");
  assertRepoMethod(repo, "deleteDevice", "SessionRepository");
  return repo;
}

async function registerDevice(uidOrParams, deviceInfoArg = {}) {
  let uid = uidOrParams;
  let deviceInfo = deviceInfoArg;
  if (uidOrParams && typeof uidOrParams === "object" && !Array.isArray(uidOrParams)) {
    uid = uidOrParams.uid;
    deviceInfo = uidOrParams.deviceData || uidOrParams.deviceInfo || uidOrParams;
  }

  const sRepo = sessionRepo();
  const deviceId = deviceInfo.deviceId || ("dev_" + crypto.randomBytes(6).toString("hex"));
  const fcmToken = deviceInfo.fcmToken || deviceInfo.token || null;
  const webPushSubscription = deviceInfo.webPushSubscription || (deviceInfo.endpoint ? {
    endpoint: deviceInfo.endpoint,
    keys: deviceInfo.keys || {}
  } : null);

  if (!uid) {
    const err = new Error("uid is required");
    err.code = "ERR_INVALID_DEVICE";
    err.status = 400;
    throw err;
  }

  if (!fcmToken && !webPushSubscription) {
    const err = new Error("Either fcmToken/token or webPushSubscription/endpoint is required");
    err.code = "ERR_MISSING_PUSH_TOKEN";
    err.status = 400;
    throw err;
  }

  const now = Date.now();
  const record = {
    uid,
    deviceId,
    platform: deviceInfo.platform || "ANDROID",
    fcmToken,
    token: fcmToken || deviceInfo.endpoint,
    endpoint: deviceInfo.endpoint || null,
    webPushSubscription,
    appVersion: deviceInfo.appVersion || "24.0",
    isPushRegistered: true,
    active: true,
    registeredAtMs: now,
    updatedAtMs: now
  };

  await sRepo.createDevice(record);
  return record;
}

async function unregisterDevice(uidOrParams, deviceIdArg) {
  let uid = uidOrParams;
  let deviceId = deviceIdArg;
  if (uidOrParams && typeof uidOrParams === "object") {
    uid = uidOrParams.uid;
    deviceId = uidOrParams.deviceId;
  }
  const sRepo = sessionRepo();
  await sRepo.deleteDevice(uid, deviceId);
  return { success: true, deviceId };
}

async function listUserDevices(uidOrParams) {
  const uid = typeof uidOrParams === "object" && uidOrParams !== null ? uidOrParams.uid : uidOrParams;
  const sRepo = sessionRepo();
  const devices = await sRepo.listUserDevices(uid);
  return devices.filter(d => d.active !== false && (d.isPushRegistered || d.fcmToken || d.token || d.endpoint || d.webPushSubscription));
}

async function sendPushNotification({ targetUid, uid, title, body, data = {} }) {
  const resolvedUid = targetUid || uid;
  const devices = await listUserDevices(resolvedUid);
  const deliveries = [];
  const provider = getPushProvider ? getPushProvider() : null;

  if (devices.length === 0) {
    const entry = {
      deliveryId: "push_" + crypto.randomBytes(8).toString("hex"),
      targetUid: resolvedUid,
      deviceId: null,
      title,
      body,
      data,
      status: "SKIPPED_NO_DEVICES",
      createdAtMs: Date.now()
    };
    return { deliveredCount: 0, sentCount: 0, deliveries: [entry] };
  }

  for (const device of devices) {
    let providerRes = null;
    if (provider && typeof provider.sendPush === "function") {
      providerRes = await provider.sendPush({
        targetUid: resolvedUid,
        deviceId: device.deviceId,
        platform: device.platform,
        token: device.fcmToken || device.token || device.endpoint,
        endpoint: device.endpoint,
        title,
        body,
        data
      });
    }
    const entry = {
      deliveryId: (providerRes && providerRes.messageId) || ("push_" + crypto.randomBytes(8).toString("hex")),
      targetUid: resolvedUid,
      deviceId: device.deviceId,
      platform: device.platform,
      title,
      body,
      data,
      status: "DELIVERED",
      createdAtMs: Date.now()
    };
    deliveries.push(entry);
  }

  return {
    deliveredCount: deliveries.length,
    sentCount: deliveries.length,
    deliveries
  };
}

async function sendPushToUser(params) {
  return await sendPushNotification(params);
}

async function clearAllPushStore() {
  const sRepo = sessionRepo();
  assertRepoMethod(sRepo, "clear", "SessionRepository");
  await sRepo.clear();
}

const resetPushStore = clearAllPushStore;
const resetDevicesStore = clearAllPushStore;
const revokeDevice = unregisterDevice;

module.exports = {
  registerDevice,
  unregisterDevice,
  revokeDevice,
  listUserDevices,
  sendPushNotification,
  sendPushToUser,
  clearAllPushStore,
  resetPushStore,
  resetDevicesStore
};
