/**
 * Firestore Inbox Repository (functions/src/repositories/firestore/inboxRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreInboxRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async addItem(uid, itemDoc) {
    const itemId = itemDoc.id;
    await this.db.collection("users").doc(uid).collection("inbox").doc(itemId).set(itemDoc);
    return itemDoc;
  }

  async getItem(uid, itemId) {
    const snap = await this.db.collection("users").doc(uid).collection("inbox").doc(itemId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateItem(uid, itemId, updates) {
    await this.db.collection("users").doc(uid).collection("inbox").doc(itemId).set(updates, { merge: true });
    return this.getItem(uid, itemId);
  }

  async listItems(uid, limit = 50) {
    const snap = await this.db.collection("users").doc(uid).collection("inbox").get();
    let items = snap.docs.map(d => d.data());
    items.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
    if (limit) {
      items = items.slice(0, limit);
    }
    return items;
  }
}

module.exports = { FirestoreInboxRepository };
