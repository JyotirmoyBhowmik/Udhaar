/**
 * Firestore Ledger Repository (functions/src/repositories/firestore/ledgerRepository.js)
 * Implements persistent storage for records, people, payments/events, and plans.
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreLedgerRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async createRecord(uid, recordDoc) {
    const id = recordDoc.id || recordDoc.recordId;
    const docWithOwner = { ...recordDoc, uid, recordId: id, id };
    await this.db.collection("users").doc(uid).collection("records").doc(id).set(docWithOwner);
    await this.db.collection("recordsIndex").doc(`${uid}_${id}`).set({
      uid,
      recordId: id,
      linkedCreatorUid: docWithOwner.linkedCreatorUid || null,
      remoteRecordId: docWithOwner.remoteRecordId || null,
      deletedAtMs: docWithOwner.deletedAtMs || null
    });
    return docWithOwner;
  }

  async getRecord(uid, recordId) {
    if (!uid || !recordId) return null;
    const snap = await this.db.collection("users").doc(uid).collection("records").doc(recordId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateRecord(uid, recordId, updates, expectedRev = null) {
    const docRef = this.db.collection("users").doc(uid).collection("records").doc(recordId);
    const idxRef = this.db.collection("recordsIndex").doc(`${uid}_${recordId}`);

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      if (!snap.exists) {
        const err = new Error(`Record ${recordId} not found.`);
        err.code = "ERR_RECORD_NOT_FOUND";
        err.status = 404;
        throw err;
      }

      const current = snap.data();
      if (expectedRev !== null && expectedRev !== undefined && current.serverRev !== expectedRev) {
        const err = new Error("Record revision conflict (ETag mismatch).");
        err.code = "ERR_CONFLICT";
        err.status = 409;
        err.serverRecord = current;
        throw err;
      }

      const updated = {
        ...current,
        ...updates,
        updatedAtMs: updates.updatedAtMs || Date.now()
      };
      t.set(docRef, updated);
      t.set(idxRef, {
        uid,
        recordId,
        linkedCreatorUid: updated.linkedCreatorUid || null,
        remoteRecordId: updated.remoteRecordId || null,
        deletedAtMs: updated.deletedAtMs || null
      }, { merge: true });
      return updated;
    });
  }

  async deleteRecord(uid, recordId) {
    return this.updateRecord(uid, recordId, {
      deleted: true,
      deletedAtMs: Date.now()
    });
  }

  async listRecords(uid, filters = {}) {
    if (!uid) return [];
    let query = this.db.collection("users").doc(uid).collection("records");
    if (filters.status) {
      query = query.where("status", "==", filters.status);
    }
    const snap = await query.get();
    let list = snap.docs.map(d => d.data());
    if (!filters.includeDeleted) {
      list = list.filter(r => !r.deleted && !r.deletedAtMs);
    }
    return list.sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
  }

  async countRecords(uid) {
    const list = await this.listRecords(uid);
    return list.length;
  }

  async countAllRecords() {
    const snap = await this.db.collection("recordsIndex").get();
    return snap.docs.length;
  }

  async findLinkedRecord(uid, remoteRecordId) {
    if (!uid || !remoteRecordId) return null;
    const records = await this.listRecords(uid, { includeDeleted: true });
    return records.find(r => r.remoteRecordId === remoteRecordId) || null;
  }

  async anonymizeCounterpartyRecords(deletedUid) {
    if (!deletedUid) return 0;
    const idxSnap = await this.db.collection("recordsIndex")
      .where("linkedCreatorUid", "==", deletedUid)
      .get();

    let count = 0;
    for (const idxDoc of idxSnap.docs) {
      const { uid, recordId } = idxDoc.data();
      if (uid && uid !== deletedUid && recordId) {
        const rec = await this.getRecord(uid, recordId);
        if (rec) {
          await this.updateRecord(uid, recordId, {
            partyName: "Deleted User",
            partyPhone: "+910000000000",
            counterpartyAnonymizedAtMs: Date.now()
          });
          count++;
        }
      }
    }
    return count;
  }

  async createPerson(uid, personDoc) {
    const id = personDoc.id || personDoc.personId;
    await this.db.collection("users").doc(uid).collection("people").doc(id).set(personDoc);
    return personDoc;
  }

  async getPerson(uid, personId) {
    if (!uid || !personId) return null;
    const snap = await this.db.collection("users").doc(uid).collection("people").doc(personId).get();
    return snap.exists ? snap.data() : null;
  }

  async updatePerson(uid, personId, updates) {
    await this.db.collection("users").doc(uid).collection("people").doc(personId).set(updates, { merge: true });
    return this.getPerson(uid, personId);
  }

  async deletePerson(uid, personId) {
    return this.updatePerson(uid, personId, { deleted: true, deletedAtMs: Date.now() });
  }

  async listPeople(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("people").get();
    return snap.docs.map(d => d.data()).filter(p => !p.deleted);
  }

  async addEvent(uid, eventDoc) {
    const id = eventDoc.id || eventDoc.eventId;
    const docWithId = { ...eventDoc, id, eventId: id, uid };
    await this.db.collection("users").doc(uid).collection("events").doc(id).set(docWithId);
    await this.db.collection("eventsIndex").doc(`${uid}_${id}`).set({ uid, eventId: id });
    return docWithId;
  }

  async listEvents(uid, recordId = null) {
    if (!uid) return [];
    let query = this.db.collection("users").doc(uid).collection("events");
    if (recordId) {
      query = query.where("recordId", "==", recordId);
    }
    const snap = await query.get();
    return snap.docs.map(d => d.data()).sort((a, b) => (a.timestampMs || 0) - (b.timestampMs || 0));
  }

  async countAllEvents() {
    const snap = await this.db.collection("eventsIndex").get();
    return snap.docs.length;
  }

  async createPlan(uid, planDoc) {
    const id = planDoc.id || planDoc.planId;
    await this.db.collection("users").doc(uid).collection("plans").doc(id).set(planDoc);
    return planDoc;
  }

  async getPlan(uid, planId) {
    if (!uid || !planId) return null;
    const snap = await this.db.collection("users").doc(uid).collection("plans").doc(planId).get();
    return snap.exists ? snap.data() : null;
  }

  async updatePlan(uid, planId, updates) {
    await this.db.collection("users").doc(uid).collection("plans").doc(planId).set(updates, { merge: true });
    return this.getPlan(uid, planId);
  }

  async listPlans(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("plans").get();
    return snap.docs.map(d => d.data());
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "recordsIndex"),
      clearCollection(this.db, "eventsIndex"),
      clearCollection(this.db, "users")
    ]);
  }
}

module.exports = { FirestoreLedgerRepository };
