/**
 * Firestore Session & Device Repository (functions/src/repositories/firestore/sessionRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

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
      ttlExpiresAt: new Date(expiresAtMs || Date.now() + 86400000)
    };
    await this.db.collection("users").doc(uid).collection("sessions").doc(sessionId).set(docData);
    await this.db.collection("sessionsLookup").doc(sessionId).set({
      uid,
      sessionId,
      ttlExpiresAt: new Date(expiresAtMs || Date.now() + 86400000)
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

  async listUserSessions(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("sessions").get();
    return snap.docs.map(d => d.data());
  }

  async createDevice(deviceDoc) {
    const { uid, deviceId } = deviceDoc;
    await this.db.collection("users").doc(uid).collection("devices").doc(deviceId).set(deviceDoc);
    await this.db.collection("devicesLookup").doc(`${uid}_${deviceId}`).set(deviceDoc);
    return deviceDoc;
  }

  async getDevice(uid, deviceId) {
    if (!uid || !deviceId) return null;
    const snap = await this.db.collection("users").doc(uid).collection("devices").doc(deviceId).get();
    return snap.exists ? snap.data() : null;
  }

  async listUserDevices(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("devices").get();
    return snap.docs.map(d => d.data());
  }

  async deleteDevice(uid, deviceId) {
    if (!uid || !deviceId) return false;
    const ref = this.db.collection("users").doc(uid).collection("devices").doc(deviceId);
    const snap = await ref.get();
    if (!snap.exists) return false;
    await ref.delete();
    await this.db.collection("devicesLookup").doc(`${uid}_${deviceId}`).delete();
    return true;
  }

  async deleteAllUserDevices(uid) {
    if (!uid) return 0;
    const snap = await this.db.collection("users").doc(uid).collection("devices").get();
    if (snap.empty) return 0;
    const batch = this.db.batch();
    for (const doc of snap.docs) {
      batch.delete(doc.ref);
      batch.delete(this.db.collection("devicesLookup").doc(`${uid}_${doc.id}`));
    }
    await batch.commit();
    return snap.docs.length;
  }

  async countActiveDevices() {
    const snap = await this.db.collection("devicesLookup").get();
    return snap.docs.length;
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "sessionsLookup"),
      clearCollection(this.db, "devicesLookup"),
      clearCollection(this.db, "users")
    ]);
  }
}

module.exports = { FirestoreSessionRepository };
