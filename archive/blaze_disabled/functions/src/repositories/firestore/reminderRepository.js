/**
 * Firestore Reminder Repository (functions/src/repositories/firestore/reminderRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreReminderRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getPolicy(uid) {
    if (!uid) return null;
    const snap = await this.db.collection("reminderPolicies").doc(uid).get();
    return snap.exists ? snap.data() : null;
  }

  async savePolicy(uid, policyDoc) {
    await this.db.collection("reminderPolicies").doc(uid).set(policyDoc, { merge: true });
    return this.getPolicy(uid);
  }

  async getReminderState(recordId) {
    if (!recordId) return null;
    const snap = await this.db.collection("reminderStates").doc(recordId).get();
    return snap.exists ? snap.data() : null;
  }

  async saveReminderState(recordId, stateDoc) {
    await this.db.collection("reminderStates").doc(recordId).set(stateDoc, { merge: true });
    return this.getReminderState(recordId);
  }

  async saveReminderEvent(eventDoc) {
    const id = eventDoc.id || eventDoc.reminderId || `rem_${Date.now()}`;
    const docData = { ...eventDoc, id };
    await this.db.collection("reminderEvents").doc(id).set(docData);
    return docData;
  }

  async listReminderEvents(recordId) {
    const snap = await this.db.collection("reminderEvents")
      .where("recordId", "==", recordId)
      .get();
    return snap.docs.map(d => d.data());
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "reminderPolicies"),
      clearCollection(this.db, "reminderStates"),
      clearCollection(this.db, "reminderEvents")
    ]);
  }
}

module.exports = { FirestoreReminderRepository };
