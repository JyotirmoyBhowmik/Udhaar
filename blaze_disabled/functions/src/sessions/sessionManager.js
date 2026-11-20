/**
 * Session, Device Management & Step-Up Authentication Service (functions/src/sessions/sessionManager.js)
 * Implements Stage S1 / R3:
 * - Web `__session` cookie (HttpOnly, Secure, SameSite=Strict, Path=/) via Firebase Admin createSessionCookie
 * - Android Firebase Custom Token + Refresh Token Rotation via Firebase Admin createCustomToken
 * - Double-Submit CSRF Token bound to session
 * - Active Device Session Listing & Remote Revocation in Firestore
 * - 10-minute Step-Up Auth Token for high-risk actions (Account Deletion, Export, Recovery)
 * - Strictly backed by async SessionRepository without Proxy facades or module-level maps.
 */

const crypto = require("crypto");
const { SESSION_CONFIG } = require("../config");
const { getSessionRepository, assertRepoMethod } = require("../repositories");
const { getAuthAdapter } = require("./authAdapter");
const { getServerSecret } = require("../crypto/serverCrypto");

function getRequiredSessionRepo() {
  const repo = getSessionRepository();
  assertRepoMethod(repo, "createSession", "SessionRepository");
  assertRepoMethod(repo, "getSession", "SessionRepository");
  assertRepoMethod(repo, "updateSession", "SessionRepository");
  assertRepoMethod(repo, "revokeSession", "SessionRepository");
  assertRepoMethod(repo, "listUserSessions", "SessionRepository");
  assertRepoMethod(repo, "createDevice", "SessionRepository");
  return repo;
}

/**
 * Compute deterministic session-bound CSRF token
 */
function computeSessionCsrfToken(sessionId) {
  return crypto
    .createHmac("sha256", getServerSecret())
    .update(`csrf:${sessionId}`)
    .digest("hex");
}

/**
 * Create a new authenticated session for Web (`__session` cookie) or Android (Custom Token + Refresh Token)
 */
async function createSession({
  uid,
  clientType = "WEB",
  deviceInfo = {},
  ipAddress = "127.0.0.1",
  clientIp = null
}) {
  const now = Date.now();
  const sessionId = `sess_${crypto.randomBytes(16).toString("hex")}`;
  const deviceId = deviceInfo.deviceId || `dev_${crypto.randomBytes(8).toString("hex")}`;
  const expiresInMs = SESSION_CONFIG.COOKIE_MAX_AGE_SECONDS * 1000;
  const expiresAtMs = now + expiresInMs;
  const csrfToken = computeSessionCsrfToken(sessionId);

  const authAdapter = getAuthAdapter();
  const repo = getRequiredSessionRepo();

  const customToken = await authAdapter.createCustomToken(uid, {
    sessionId,
    deviceId,
    clientType
  });

  const sessionCookieValue = await authAdapter.createSessionCookie(
    {
      uid,
      sessionId,
      deviceId
    },
    {
      expiresIn: expiresInMs,
      uid,
      sessionId
    }
  );

  const sessionRecord = {
    sessionId,
    uid,
    deviceId,
    clientType,
    csrfToken,
    deviceName: deviceInfo.deviceName || (clientType === "ANDROID" ? "Android Phone" : "Web Browser"),
    osVersion: deviceInfo.osVersion || "Unknown",
    ipAddress: clientIp || ipAddress,
    createdAtMs: now,
    lastActiveAtMs: now,
    lastStepUpAtMs: now,
    expiresAtMs,
    revoked: false,
    stepUpVerifiedUntilMs: now + SESSION_CONFIG.STEP_UP_VALIDITY_MINUTES * 60 * 1000
  };

  await repo.createSession(sessionRecord);
  await repo.createDevice({
    deviceId,
    uid,
    sessionId,
    deviceName: sessionRecord.deviceName,
    clientType,
    platform: clientType,
    notificationsEnabled: true,
    registeredAtMs: now,
    lastActiveAtMs: now,
    lastSeenAt: new Date(now).toISOString(),
    revoked: false
  });

  const cookieHeader = `${SESSION_CONFIG.COOKIE_NAME}=${sessionCookieValue}; Max-Age=${SESSION_CONFIG.COOKIE_MAX_AGE_SECONDS}; Path=${SESSION_CONFIG.COOKIE_OPTIONS.path}; HttpOnly; Secure; SameSite=${SESSION_CONFIG.COOKIE_OPTIONS.sameSite}`;

  return {
    sessionId,
    deviceId,
    uid,
    clientType,
    csrfToken,
    sessionCookieValue,
    cookieHeader,
    customToken,
    accessToken: sessionCookieValue,
    expiresAtMs,
    expiresAt: new Date(expiresAtMs).toISOString()
  };
}

/**
 * Validate a session cookie or JWT custom/ID token, returning the session object or null if invalid/revoked
 */
async function validateSession(tokenValue) {
  if (!tokenValue || typeof tokenValue !== "string") {
    return null;
  }

  let decoded;
  try {
    const authAdapter = getAuthAdapter();
    decoded = await authAdapter.verifySessionCookie(tokenValue, true);
  } catch (_) {
    return null;
  }

  if (!decoded || !decoded.uid) {
    return null;
  }

  const uid = decoded.uid;
  const sessionId = decoded.sessionId || decoded.claims?.sessionId;

  if (sessionId) {
    const repo = getRequiredSessionRepo();
    const session = await repo.getSession(sessionId, uid);
    if (!session || session.uid !== uid) {
      return null;
    }
    if (session.revoked) {
      return null;
    }
    if (Date.now() > session.expiresAtMs) {
      return null;
    }
    await repo.updateSession(sessionId, { lastActiveAtMs: Date.now() }, uid);
    return {
      ...session,
      csrfToken: session.csrfToken || computeSessionCsrfToken(sessionId)
    };
  }

  // Raw customToken / ID token without a persistent sessionId record (e.g. aged token test)
  const authTimeSec = decoded.auth_time || decoded.claims?.auth_time || decoded.iat || Math.floor(Date.now() / 1000);
  const authTimeMs = authTimeSec * 1000;
  return {
    uid,
    sessionId: null,
    deviceId: decoded.deviceId || decoded.claims?.deviceId || null,
    clientType: decoded.claims?.clientType || "WEB",
    createdAtMs: authTimeMs,
    lastStepUpAtMs: authTimeMs
  };
}

/**
 * Verify a session token or throw a structured 401 error
 */
async function verifySessionToken(tokenValue) {
  const session = await validateSession(tokenValue);
  if (!session) {
    const err = new Error("Session is invalid, expired, or has been revoked.");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }
  return session;
}

/**
 * Revoke all sessions associated with a user's device and invalidate Firebase refresh tokens
 */
async function revokeDeviceSession(uid, deviceId) {
  const repo = getRequiredSessionRepo();
  const sessions = await repo.listUserSessions(uid);
  for (const sess of sessions) {
    if (!deviceId || sess.deviceId === deviceId || sess.sessionId === deviceId) {
      await repo.revokeSession(sess.sessionId, uid);
    }
  }
  if (deviceId && typeof repo.deleteDevice === "function") {
    await repo.deleteDevice(uid, deviceId);
  }

  const authAdapter = getAuthAdapter();
  await authAdapter.revokeRefreshTokens(uid);

  return { revoked: true, uid, deviceId };
}

/**
 * Elevate an existing session with a 10-minute Step-Up Authentication window
 */
async function elevateSessionStepUp(sessionId) {
  const repo = getRequiredSessionRepo();
  const session = await repo.getSession(sessionId);
  if (!session || session.revoked) {
    const err = new Error("Valid session required for step-up authentication.");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const now = Date.now();
  const stepUpVerifiedUntilMs = now + SESSION_CONFIG.STEP_UP_VALIDITY_MINUTES * 60 * 1000;
  await repo.updateSession(
    sessionId,
    { stepUpVerifiedUntilMs, lastStepUpAtMs: now },
    session.uid
  );

  return {
    sessionId,
    stepUpVerifiedUntil: new Date(stepUpVerifiedUntilMs).toISOString()
  };
}

/**
 * Check if session currently holds a valid 10-minute step-up elevation
 */
async function requireStepUpAuth(sessionId) {
  const repo = getRequiredSessionRepo();
  const session = await repo.getSession(sessionId);
  if (!session || session.revoked) {
    const err = new Error("Unauthorized session.");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  if (!session.stepUpVerifiedUntilMs || Date.now() > session.stepUpVerifiedUntilMs) {
    const err = new Error("Step-up OTP verification is required within the last 10 minutes for this sensitive action.");
    err.code = "ERR_STEP_UP_REQUIRED";
    err.status = 403;
    throw err;
  }

  return true;
}

/**
 * List active device sessions for a user
 */
async function listUserDevices(uid) {
  const repo = getRequiredSessionRepo();
  const sessions = await repo.listUserSessions(uid);
  const devices = [];
  for (const sess of sessions) {
    if (!sess.revoked && sess.expiresAtMs > Date.now()) {
      devices.push({
        sessionId: sess.sessionId,
        deviceId: sess.deviceId,
        deviceName: sess.deviceName,
        clientType: sess.clientType,
        createdAt: new Date(sess.createdAtMs).toISOString(),
        lastActiveAt: new Date(sess.lastActiveAtMs).toISOString()
      });
    }
  }
  return devices;
}

/**
 * Revoke a specific session or all sessions for a user, including Firebase refresh tokens
 */
async function revokeSession({ uid, sessionId }) {
  const repo = getRequiredSessionRepo();
  const session = await repo.getSession(sessionId, uid);
  if (!session || session.uid !== uid) {
    const err = new Error("Session not found or access denied.");
    err.code = "ERR_SESSION_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  await repo.revokeSession(sessionId, uid);

  const authAdapter = getAuthAdapter();
  await authAdapter.revokeRefreshTokens(uid);

  return { revoked: true, sessionId };
}

async function resetSessionsStore() {
  const repo = getSessionRepository();
  assertRepoMethod(repo, "clear", "SessionRepository");
  await repo.clear();
  const authAdapter = getAuthAdapter();
  if (authAdapter && typeof authAdapter.reset === "function") {
    authAdapter.reset();
  }
}

module.exports = {
  createSession,
  validateSession,
  verifySessionToken,
  computeSessionCsrfToken,
  revokeDeviceSession,
  elevateSessionStepUp,
  requireStepUpAuth,
  listUserDevices,
  revokeSession,
  resetSessionsStore
};
