/**
 * Firestore Sync Repository (functions/src/repositories/firestore/syncRepository.js)
 * Implements atomic sequential change-log allocation, sync pulls, and sync signals.
 */

const { getDb } = require("./firestoreClient");

class FirestoreSyncRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  /**
   * Atomic sequence allocation transaction ensuring strictly monotonic changeLog sequence
   */
  async allocateSequenceNumber(uid) {
    const counterRef = this.db.collection("users").doc(uid).collection("syncMeta").doc("counters");

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(counterRef);
      const currentSeq = snap.exists ? (snap.data().lastSeq || 0) : 0;
      const nextSeq = currentSeq + 1;

      t.set(counterRef, {
        lastSeq: nextSeq,
        updatedAtMs: Date.now()
      }, { merge: true });

      return nextSeq;
    });
  }

  async appendChangeLog(uid, seq, changeEntry) {
    const docId = String(seq).padStart(12, "0");
    const docData = {
      ...changeEntry,
      seq,
      timestampMs: changeEntry.timestampMs || Date.now()
    };
    await this.db.collection("users").doc(uid).collection("changeLog").doc(docId).set(docData);
    return docData;
  }

  async pullChangeLog(uid, sinceSeq = 0, limit = 100) {
    const snap = await this.db
      .collection("users")
      .doc(uid)
      .collection("changeLog")
      .where("seq", ">", Number(sinceSeq))
      .get();

    let results = snap.docs.map(d => d.data());
    results.sort((a, b) => a.seq - b.seq);
    if (limit) {
      results = results.slice(0, limit);
    }
    return results;
  }

  async touchSyncSignal(uid, seq) {
    const signalData = {
      seq,
      updatedAtMs: Date.now()
    };
    await this.db.collection("users").doc(uid).collection("syncSignal").doc("current").set(signalData);
    return signalData;
  }

  async getSyncSignal(uid) {
    const snap = await this.db.collection("users").doc(uid).collection("syncSignal").doc("current").get();
    return snap.exists ? snap.data() : { seq: 0, updatedAtMs: 0 };
  }
}

module.exports = { FirestoreSyncRepository };
