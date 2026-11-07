/**
 * Firestore Reminder Repository (functions/src/repositories/firestore/reminderRepository.js)
 */

const { getDb } = require("./firestoreClient");

class FirestoreReminderRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveReminderEvent(eventDoc) {
    const docId = eventDoc.id || `rem_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    await this.db.collection("reminder_events").doc(docId).set(eventDoc);
    return eventDoc;
  }

  async listReminderEvents(recordId) {
    const snap = await this.db.collection("reminder_events").where("recordId", "==", recordId).get();
    return snap.docs.map(d => d.data());
  }
}

module.exports = { FirestoreReminderRepository };
