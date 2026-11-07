/**
 * Firestore Link & Capability Repository (functions/src/repositories/firestore/linkRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreLinkRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveWebLink(recordId, linkDoc) {
    await this.db.collection("webLinks").doc(recordId).set(linkDoc);
    return linkDoc;
  }

  async getWebLink(recordId) {
    const snap = await this.db.collection("webLinks").doc(recordId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateWebLink(recordId, updates) {
    await this.db.collection("webLinks").doc(recordId).set(updates, { merge: true });
    return this.getWebLink(recordId);
  }

  async deleteWebLink(recordId) {
    await this.db.collection("webLinks").doc(recordId).delete();
    return true;
  }
}

module.exports = { FirestoreLinkRepository };
