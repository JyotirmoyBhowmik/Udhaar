/**
 * Firestore Chat Repository (functions/src/repositories/firestore/chatRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreChatRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveThread(threadId, threadDoc) {
    await this.db.collection("chatThreads").doc(threadId).set(threadDoc, { merge: true });
    return threadDoc;
  }

  async getThread(threadId) {
    if (!threadId) return null;
    const snap = await this.db.collection("chatThreads").doc(threadId).get();
    return snap.exists ? snap.data() : null;
  }

  async listUserThreads(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("chatThreads").get();
    return snap.docs
      .map(d => d.data())
      .filter(t => t && (t.creatorUid === uid || t.partyUid === uid || (Array.isArray(t.participants) && t.participants.includes(uid))))
      .sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
  }

  async findThreadByRecordAndParties(recordId, uid, partyUid = null) {
    if (!recordId || !uid) return null;
    const snap = await this.db.collection("chatThreads").where("recordId", "==", recordId).get();
    for (const doc of snap.docs) {
      const t = doc.data();
      if (t.creatorUid === uid || (partyUid && t.partyUid === partyUid)) {
        return t;
      }
    }
    return null;
  }

  async saveMessage(threadId, messageId, messageDoc) {
    await this.db.collection("chatThreads").doc(threadId).collection("messages").doc(messageId).set(messageDoc);
    return messageDoc;
  }

  async getMessage(threadId, messageId) {
    if (!threadId || !messageId) return null;
    const snap = await this.db.collection("chatThreads").doc(threadId).collection("messages").doc(messageId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateMessage(threadId, messageId, updates) {
    await this.db.collection("chatThreads").doc(threadId).collection("messages").doc(messageId).set(updates, { merge: true });
    return this.getMessage(threadId, messageId);
  }

  async listMessages(threadId, limit = 50, beforeMs = null) {
    if (!threadId) return [];
    const snap = await this.db.collection("chatThreads").doc(threadId).collection("messages").get();
    let msgs = snap.docs.map(d => d.data());
    if (beforeMs && Number.isFinite(Number(beforeMs))) {
      msgs = msgs.filter(m => m.createdAtMs < Number(beforeMs));
    }
    msgs.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
    const maxLimit = Math.min(Math.max(1, Number(limit) || 50), 200);
    return msgs.slice(-maxLimit);
  }

  async clear() {
    await clearCollection(this.db, "chatThreads");
  }
}

module.exports = { FirestoreChatRepository };
