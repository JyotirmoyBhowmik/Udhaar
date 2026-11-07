/**
 * Firestore Privacy & Erasure Repository (functions/src/repositories/firestore/privacyRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestorePrivacyRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async savePrivacyRequest(reqDoc) {
    const docId = reqDoc.id || `priv_${Date.now()}`;
    await this.db.collection("privacyRequests").doc(docId).set(reqDoc);
    return reqDoc;
  }

  async getPrivacyRequest(id) {
    const snap = await this.db.collection("privacyRequests").doc(id).get();
    return snap.exists ? snap.data() : null;
  }

  async deleteUserData(uid) {
    const userRef = this.db.collection("users").doc(uid);
    // Mark tombstone on root doc and clear sensitive profile
    await userRef.set({
      isDeleted: true,
      deletedAtMs: Date.now(),
      displayName: "Deleted User",
      phoneNumberHashed: null,
      emailHashed: null,
      upiIdEncrypted: null
    }, { merge: true });
    return true;
  }
}

module.exports = { FirestorePrivacyRepository };
