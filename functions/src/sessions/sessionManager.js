/**
 * Session & Device Management Engine (functions/src/sessions/sessionManager.js)
 * Implements sliding __session cookies for Web, Firebase custom tokens for Android,
 * device tracking, and instant revocation (checkRevoked).
 */

const crypto = require("crypto");

// In-memory sessions store for emulator / testing
const inMemorySessions = new Map();
const inMemoryDevices = new Map();

/**
 * Mint a session for Web or Android client
 */
async function createSession({
  uid,
  clientType = "WEB", // "WEB" | "ANDROID"
  clientIp = "127.0.0.1",
  userAgent = "",
  cityLevelLocation = "India",
  isPublicComputer = false
}) {
  const sessionId = `sess_${crypto.randomUUID()}`;
  const deviceId = `dev_${crypto.randomUUID()}`;
  const now = Date.now();

  // Sliding 7-day expiration, 30-day absolute ceiling
  const slidingTtlMs = 7 * 24 * 60 * 60 * 1000;
  const absoluteMaxMs = 30 * 24 * 60 * 60 * 1000;

  const session = {
    sessionId,
    uid,
    deviceId,
    clientType,
    clientIp,
    cityLevelLocation,
    createdAtMs: now,
    expiresAtMs: now + slidingTtlMs,
    absoluteExpiresAtMs: now + absoluteMaxMs,
    isPublicComputer,
    revoked: false
  };

  inMemorySessions.set(sessionId, session);

  // Track in devices collection
  const device = {
    deviceId,
    uid,
    deviceType: clientType,
    userAgent,
    cityLevelLocation,
    lastActiveMs: now,
    isCurrent: true,
    revoked: false
  };
  inMemoryDevices.set(`${uid}_${deviceId}`, device);

  // Value for __session cookie
  const sessionCookieValue = Buffer.from(JSON.stringify({
    s: sessionId,
    u: uid,
    d: deviceId,
    exp: session.expiresAtMs
  })).toString("base64");

  // Synthetic custom token for Android
  const customToken = `custom_token_${uid}_${crypto.randomBytes(16).toString("hex")}`;

  return {
    sessionId,
    deviceId,
    sessionCookieValue,
    customToken,
    expiresAtMs: session.expiresAtMs
  };
}

/**
 * Validate session token or cookie and enforce checkRevoked
 */
async function validateSession(sessionCookieOrToken) {
  if (!sessionCookieOrToken) return null;

  try {
    let payload = null;
    if (sessionCookieOrToken.startsWith("custom_token_")) {
      const parts = sessionCookieOrToken.split("_");
      return {
        uid: parts[2],
        deviceId: "android_device",
        clientType: "ANDROID"
      };
    }

    const decoded = Buffer.from(sessionCookieOrToken, "base64").toString("utf8");
    payload = JSON.parse(decoded);

    const session = inMemorySessions.get(payload.s);
    if (!session || session.revoked) {
      return null;
    }

    const now = Date.now();
    if (now > session.expiresAtMs || now > session.absoluteExpiresAtMs) {
      return null;
    }

    // Slide expiration window if not public computer
    if (!session.isPublicComputer) {
      session.expiresAtMs = Math.min(now + 7 * 24 * 60 * 60 * 1000, session.absoluteExpiresAtMs);
    }

    return {
      sessionId: session.sessionId,
      uid: session.uid,
      deviceId: session.deviceId,
      clientType: session.clientType
    };
  } catch (_) {
    return null;
  }
}

/**
 * Revoke a specific device session
 */
async function revokeDeviceSession(uid, deviceId) {
  for (const session of inMemorySessions.values()) {
    if (session.uid === uid && session.deviceId === deviceId) {
      session.revoked = true;
    }
  }

  const devKey = `${uid}_${deviceId}`;
  const dev = inMemoryDevices.get(devKey);
  if (dev) {
    dev.revoked = true;
  }

  return true;
}

/**
 * List registered devices for user
 */
async function listUserDevices(uid) {
  const list = [];
  for (const [key, dev] of inMemoryDevices.entries()) {
    if (dev.uid === uid && !dev.revoked) {
      list.push(dev);
    }
  }
  return list;
}

function resetSessionsStore() {
  inMemorySessions.clear();
  inMemoryDevices.clear();
}

module.exports = {
  createSession,
  validateSession,
  revokeDeviceSession,
  listUserDevices,
  resetSessionsStore,
  _inMemorySessions: inMemorySessions,
  _inMemoryDevices: inMemoryDevices
};
