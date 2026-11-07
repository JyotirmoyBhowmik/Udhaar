/**
 * Firestore Chat Repository (functions/src/repositories/firestore/chatRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreChatRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveThread(threadId, threadDoc) {
    await this.db.collection("chat_threads").doc(threadId).set(threadDoc, { merge: true });
    return threadDoc;
  }

  async getThread(threadId) {
    const snap = await this.db.collection("chat_threads").doc(threadId).get();
    return snap.exists ? snap.data() : null;
  }

  async saveMessage(threadId, messageId, messageDoc) {
    await this.db.collection("chat_threads").doc(threadId).collection("messages").doc(messageId).set(messageDoc);
    await this.db.collection("chat_threads").doc(threadId).set({
      lastMessageSnippet: messageDoc.body,
      lastMessageAtMs: messageDoc.createdAtMs || Date.now()
    }, { merge: true });
    return messageDoc;
  }

  async listMessages(threadId, limit = 50) {
    const snap = await this.db.collection("chat_threads").doc(threadId).collection("messages").get();
    let msgs = snap.docs.map(d => d.data());
    msgs.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
    if (limit) {
      msgs = msgs.slice(-limit);
    }
    return msgs;
  }
}

module.exports = { FirestoreChatRepository };
