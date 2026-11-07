/**
 * Firestore Admin Repository (functions/src/repositories/firestore/adminRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreAdminRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getAdminRole(uid) {
    const snap = await this.db.collection("adminRoles").doc(uid).get();
    return snap.exists ? snap.data().role : null;
  }

  async setAdminRole(uid, role) {
    await this.db.collection("adminRoles").doc(uid).set({
      uid,
      role,
      assignedAtMs: Date.now()
    });
    return role;
  }
}

module.exports = { FirestoreAdminRepository };
