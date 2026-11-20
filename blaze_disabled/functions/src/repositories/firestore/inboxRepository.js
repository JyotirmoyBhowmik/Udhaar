/**
 * Firestore Inbox Repository (functions/src/repositories/firestore/inboxRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreInboxRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async addItem(uid, itemDoc) {
    const id = itemDoc.id || itemDoc.inboxId;
    const docData = { ...itemDoc, id, inboxId: id, uid };
    await this.db.collection("users").doc(uid).collection("inbox").doc(id).set(docData);
    return docData;
  }

  async getItem(uid, itemId) {
    if (!uid || !itemId) return null;
    const snap = await this.db.collection("users").doc(uid).collection("inbox").doc(itemId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateItem(uid, itemId, updates) {
    await this.db.collection("users").doc(uid).collection("inbox").doc(itemId).set(updates, { merge: true });
    return this.getItem(uid, itemId);
  }

  async listItems(uid, unreadOnly = false) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("inbox").get();
    let items = snap.docs.map(d => d.data());
    if (unreadOnly) {
      items = items.filter(i => !i.isRead);
    }
    return items.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
  }

  async clear() {
    await clearCollection(this.db, "users");
  }
}

module.exports = { FirestoreInboxRepository };
