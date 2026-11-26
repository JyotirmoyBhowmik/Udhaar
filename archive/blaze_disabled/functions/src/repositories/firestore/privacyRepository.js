/**
 * Firestore Privacy Repository (functions/src/repositories/firestore/privacyRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestorePrivacyRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async savePrivacyRequest(reqDoc) {
    const id = reqDoc.id || reqDoc.requestId;
    const docData = { ...reqDoc, id, requestId: id };
    await this.db.collection("privacy_requests").doc(id).set(docData, { merge: true });
    return docData;
  }

  async getPrivacyRequest(id) {
    if (!id) return null;
    const snap = await this.db.collection("privacy_requests").doc(id).get();
    return snap.exists ? snap.data() : null;
  }

  async listPrivacyRequests(uid = null) {
    let query = this.db.collection("privacy_requests");
    if (uid) {
      query = query.where("uid", "==", uid);
    }
    const snap = await query.get();
    return snap.docs.map(d => d.data()).sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
  }

  async deleteUserData(uid) {
    const userRef = this.db.collection("users").doc(uid);
    if (typeof this.db.recursiveDelete === "function") {
      await this.db.recursiveDelete(userRef);
    } else {
      await userRef.delete();
    }
    return true;
  }

  async clear() {
    await clearCollection(this.db, "privacy_requests");
  }
}

module.exports = { FirestorePrivacyRepository };
