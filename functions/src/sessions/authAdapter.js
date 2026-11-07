/**
 * Firebase Auth Adapter & Token Minting Engine (functions/src/sessions/authAdapter.js)
 * 
 * Production: Uses Firebase Admin SDK (firebase-admin/auth)
 * - createCustomToken(uid, claims) for Android
 * - createSessionCookie(idToken, { expiresIn }) for Web (__session)
 * - verifySessionCookie(cookie, checkRevoked)
 * - revokeRefreshTokens(uid)
 * 
 * Testing: Uses an injectable TestAuthAdapter that creates cryptographically signed JWTs
 * using HMAC-SHA256, strictly adhering to the same interface and claims structure.
 */

const crypto = require("crypto");
const { getAuth } = require("firebase-admin/auth");
const { isProductionOrStaging } = require("../config/environmentGuard");
const { getSecret } = require("../config/secrets");

class FirebaseAuthAdapter {
  constructor(adminAuth = null) {
    this._auth = adminAuth;
  }

  get auth() {
    return this._auth || getAuth();
  }

  async createCustomToken(uid, developerClaims = {}) {
    return this.auth.createCustomToken(uid, developerClaims);
  }

  async createSessionCookie(idToken, options = {}) {
    return this.auth.createSessionCookie(idToken, options);
  }

  async verifySessionCookie(sessionCookie, checkRevoked = true) {
    return this.auth.verifySessionCookie(sessionCookie, checkRevoked);
  }

  async revokeRefreshTokens(uid) {
    return this.auth.revokeRefreshTokens(uid);
  }
}

/**
 * Deterministic Test Double producing valid cryptographic JWTs signed with HMAC-SHA256
 */
class TestAuthAdapter {
  constructor() {
    this.revokedUids = new Set();
  }

  getSecretKey() {
    return getSecret("UDHAAR_SERVER_SECRET");
  }

  async createCustomToken(uid, developerClaims = {}) {
    const now = Math.floor(Date.now() / 1000);
    const authTime = developerClaims.auth_time || now;
    const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
    const payload = Buffer.from(JSON.stringify({
      iss: "https://securetoken.google.com/udhaar-test",
      aud: "udhaar-test",
      sub: uid,
      uid,
      auth_time: authTime,
      iat: authTime,
      exp: now + 3600,
      claims: developerClaims
    })).toString("base64url");

    const signature = crypto.createHmac("sha256", this.getSecretKey())
      .update(`${header}.${payload}`)
      .digest("base64url");

    return `${header}.${payload}.${signature}`;
  }

  async createSessionCookie(idTokenOrPayload, options = {}) {
    const now = Math.floor(Date.now() / 1000);
    const expiresInSec = Math.floor((options.expiresIn || 7 * 24 * 60 * 60 * 1000) / 1000);
    const uid = typeof idTokenOrPayload === "string" ? idTokenOrPayload : (idTokenOrPayload.uid || "test_user");

    const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
    const payload = Buffer.from(JSON.stringify({
      iss: "https://session.firebase.google.com/udhaar-test",
      sub: uid,
      uid,
      auth_time: now,
      iat: now,
      exp: now + expiresInSec,
      sessionId: idTokenOrPayload.sessionId,
      deviceId: idTokenOrPayload.deviceId
    })).toString("base64url");

    const signature = crypto.createHmac("sha256", this.getSecretKey())
      .update(`${header}.${payload}`)
      .digest("base64url");

    return `${header}.${payload}.${signature}`;
  }

  async verifySessionCookie(sessionCookie, checkRevoked = true) {
    if (!sessionCookie || typeof sessionCookie !== "string") {
      throw new Error("Decoding Firebase session cookie failed");
    }

    const parts = sessionCookie.split(".");
    if (parts.length !== 3) {
      throw new Error("Invalid session cookie structure");
    }

    const [header, payload, signature] = parts;
    const expectedSignature = crypto.createHmac("sha256", this.getSecretKey())
      .update(`${header}.${payload}`)
      .digest("base64url");

    if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expectedSignature))) {
      throw new Error("Firebase session cookie signature verification failed");
    }

    const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    const now = Math.floor(Date.now() / 1000);

    if (claims.exp && claims.exp < now) {
      const err = new Error("Firebase session cookie has expired");
      err.code = "auth/session-cookie-expired";
      throw err;
    }

    if (checkRevoked && this.revokedUids.has(claims.uid)) {
      const err = new Error("Firebase session cookie has been revoked");
      err.code = "auth/session-cookie-revoked";
      throw err;
    }

    return claims;
  }

  async revokeRefreshTokens(uid) {
    this.revokedUids.add(uid);
    return true;
  }

  reset() {
    this.revokedUids.clear();
  }
}

let activeAuthAdapter = null;

function setAuthAdapter(adapter) {
  if (isProductionOrStaging()) {
    const err = new Error("[AuthAdapter] FATAL: Cannot inject test auth adapter in production or staging.");
    err.code = "ERR_ILLEGAL_TEST_PROVIDER";
    throw err;
  }
  activeAuthAdapter = adapter;
}

function resetAuthAdapter() {
  activeAuthAdapter = null;
}

function getAuthAdapter() {
  if (activeAuthAdapter) {
    if (isProductionOrStaging() && activeAuthAdapter instanceof TestAuthAdapter) {
      const err = new Error("[AuthAdapter] FATAL: Test auth adapter active in production or staging.");
      err.code = "ERR_ILLEGAL_TEST_PROVIDER";
      throw err;
    }
    return activeAuthAdapter;
  }

  if (isProductionOrStaging()) {
    return new FirebaseAuthAdapter();
  }

  // Non-production test default
  activeAuthAdapter = new TestAuthAdapter();
  return activeAuthAdapter;
}

module.exports = {
  FirebaseAuthAdapter,
  TestAuthAdapter,
  getAuthAdapter,
  setAuthAdapter,
  resetAuthAdapter
};
