/**
 * Firestore Session & Device Repository (functions/src/repositories/firestore/sessionRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreSessionRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async createSession(sessionDoc) {
    const { uid, sessionId, expiresAtMs } = sessionDoc;
    const docData = {
      ...sessionDoc,
      ttlExpiresAt: new Date(expiresAtMs)
    };
    await this.db.collection("users").doc(uid).collection("sessions").doc(sessionId).set(docData);
    // Also store in root lookup for fast token authentication
    await this.db.collection("sessionsLookup").doc(sessionId).set({
      uid,
      sessionId,
      ttlExpiresAt: new Date(expiresAtMs)
    });
    return sessionDoc;
  }

  async getSession(sessionId, uidHint) {
    if (!sessionId) return null;
    let uid = uidHint;
    if (!uid) {
      const lookupSnap = await this.db.collection("sessionsLookup").doc(sessionId).get();
      if (!lookupSnap.exists) return null;
      uid = lookupSnap.data().uid;
    }
    const snap = await this.db.collection("users").doc(uid).collection("sessions").doc(sessionId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateSession(sessionId, updates, uidHint) {
    const session = await this.getSession(sessionId, uidHint);
    if (!session) return null;
    const uid = session.uid;
    await this.db.collection("users").doc(uid).collection("sessions").doc(sessionId).set(updates, { merge: true });
    return this.getSession(sessionId, uid);
  }

  async revokeSession(sessionId, uidHint) {
    return this.updateSession(sessionId, { revoked: true, revokedAtMs: Date.now() }, uidHint);
  }

  async createDevice(deviceDoc) {
    const { uid, deviceId } = deviceDoc;
    await this.db.collection("users").doc(uid).collection("devices").doc(deviceId).set(deviceDoc);
    return deviceDoc;
  }

  async listUserDevices(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("devices").get();
    return snap.docs.map(d => d.data());
  }

  async listUserSessions(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("sessions").get();
    return snap.docs.map(d => d.data());
  }
}

module.exports = { FirestoreSessionRepository };
