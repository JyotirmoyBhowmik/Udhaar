/**
 * Firestore Sync Repository (functions/src/repositories/firestore/syncRepository.js)
 * Implements atomic sequence number allocation, changeLog stream, syncSignal, and synced entities.
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreSyncRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async runTransaction(updateFn) {
    return this.db.runTransaction(async (tx) => {
      return updateFn(tx);
    });
  }

  /**
   * Atomically increments and returns the next sequence number for a user's change log
   */
  async allocateSequenceNumber(uid, tx = null) {
    const counterRef = this.db.collection("users").doc(uid).collection("syncMeta").doc("counter");
    if (tx) {
      const snap = await tx.get(counterRef);
      const currentSeq = snap.exists ? (snap.data().seq || 0) : 0;
      const nextSeq = currentSeq + 1;
      tx.set(counterRef, { seq: nextSeq, updatedAtMs: Date.now() }, { merge: true });
      return nextSeq;
    }

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(counterRef);
      const currentSeq = snap.exists ? (snap.data().seq || 0) : 0;
      const nextSeq = currentSeq + 1;
      t.set(counterRef, { seq: nextSeq, updatedAtMs: Date.now() }, { merge: true });
      return nextSeq;
    });
  }

  async appendChangeLog(uid, seq, changeEntry, tx = null) {
    const docId = String(seq).padStart(12, "0");
    const entry = {
      ...changeEntry,
      seq,
      timestampMs: changeEntry.timestampMs || Date.now()
    };
    const docRef = this.db.collection("users").doc(uid).collection("changeLog").doc(docId);
    if (tx) {
      tx.set(docRef, entry);
    } else {
      await docRef.set(entry);
    }
    return entry;
  }

  async pullChangeLog(uid, sinceSeq = 0, limit = 100) {
    const snap = await this.db.collection("users").doc(uid).collection("changeLog")
      .where("seq", ">", sinceSeq)
      .orderBy("seq", "asc")
      .limit(limit)
      .get();
    return snap.docs.map(d => d.data());
  }

  async touchSyncSignal(uid, seq, tx = null) {
    const docData = {
      uid,
      seq,
      updatedAtMs: Date.now()
    };
    const docRef = this.db.collection("users").doc(uid).collection("syncSignal").doc("current");
    if (tx) {
      tx.set(docRef, docData, { merge: true });
    } else {
      await docRef.set(docData, { merge: true });
    }
    return docData;
  }

  async getSyncSignal(uid) {
    if (!uid) return null;
    const snap = await this.db.collection("users").doc(uid).collection("syncSignal").doc("current").get();
    return snap.exists ? snap.data() : null;
  }

  async getSyncedRecord(uid, syncId, tx = null) {
    if (!uid || !syncId) return null;
    const docRef = this.db.collection("users").doc(uid).collection("syncedRecords").doc(syncId);
    const snap = tx ? await tx.get(docRef) : await docRef.get();
    return snap.exists ? snap.data() : null;
  }

  async saveSyncedRecord(uid, syncId, record, tx = null) {
    const docRef = this.db.collection("users").doc(uid).collection("syncedRecords").doc(syncId);
    if (tx) {
      tx.set(docRef, record);
    } else {
      await docRef.set(record);
    }
    return record;
  }

  async listSyncedRecords(uid) {
    if (!uid) return [];
    const snap = await this.db.collection("users").doc(uid).collection("syncedRecords").get();
    return snap.docs.map(d => d.data());
  }

  async getSyncedPerson(uid, syncId, tx = null) {
    if (!uid || !syncId) return null;
    const docRef = this.db.collection("users").doc(uid).collection("syncedPeople").doc(syncId);
    const snap = tx ? await tx.get(docRef) : await docRef.get();
    return snap.exists ? snap.data() : null;
  }

  async saveSyncedPerson(uid, syncId, person, tx = null) {
    const docRef = this.db.collection("users").doc(uid).collection("syncedPeople").doc(syncId);
    if (tx) {
      tx.set(docRef, person);
    } else {
      await docRef.set(person);
    }
    return person;
  }

  async getSyncedPayment(uid, paymentId, tx = null) {
    if (!uid || !paymentId) return null;
    const docRef = this.db.collection("users").doc(uid).collection("syncedPayments").doc(paymentId);
    const snap = tx ? await tx.get(docRef) : await docRef.get();
    return snap.exists ? snap.data() : null;
  }

  async saveSyncedPayment(uid, paymentId, payment, tx = null) {
    const docRef = this.db.collection("users").doc(uid).collection("syncedPayments").doc(paymentId);
    if (tx) {
      tx.set(docRef, payment);
    } else {
      await docRef.set(payment);
    }
    return payment;
  }

  async clear() {
    await clearCollection(this.db, "users");
  }
}

module.exports = { FirestoreSyncRepository };
