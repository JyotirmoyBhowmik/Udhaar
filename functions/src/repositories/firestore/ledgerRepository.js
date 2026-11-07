/**
 * Firestore Ledger Repository (functions/src/repositories/firestore/ledgerRepository.js)
 * Implements persistent storage for Records, People, Payments, Plans, and Events.
 */

const { getDb } = require("./firestoreClient");

class FirestoreLedgerRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  // --- Records ---
  async createRecord(uid, record) {
    const recordId = record.id;
    await this.db.collection("users").doc(uid).collection("records").doc(recordId).set(record);
    return record;
  }

  async getRecord(uid, recordId) {
    const snap = await this.db.collection("users").doc(uid).collection("records").doc(recordId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateRecord(uid, recordId, updates) {
    await this.db.collection("users").doc(uid).collection("records").doc(recordId).set(updates, { merge: true });
    return this.getRecord(uid, recordId);
  }

  async listRecords(uid, options = {}) {
    let query = this.db.collection("users").doc(uid).collection("records");
    if (options.status) {
      query = query.where("status", "==", options.status);
    }
    if (options.counterpartyRole) {
      query = query.where("counterpartyRole", "==", options.counterpartyRole);
    }
    const snap = await query.get();
    let results = snap.docs.map(d => d.data());
    // In-memory sort by updatedAtMs descending if not indexed in test
    results.sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
    if (options.limit) {
      results = results.slice(0, options.limit);
    }
    return results;
  }

  // --- People ---
  async createPerson(uid, person) {
    const personId = person.id;
    await this.db.collection("users").doc(uid).collection("people").doc(personId).set(person);
    return person;
  }

  async getPerson(uid, personId) {
    const snap = await this.db.collection("users").doc(uid).collection("people").doc(personId).get();
    return snap.exists ? snap.data() : null;
  }

  async updatePerson(uid, personId, updates) {
    await this.db.collection("users").doc(uid).collection("people").doc(personId).set(updates, { merge: true });
    return this.getPerson(uid, personId);
  }

  async listPeople(uid) {
    const snap = await this.db.collection("users").doc(uid).collection("people").get();
    return snap.docs.map(d => d.data());
  }

  // --- Payments & Balance Transactions ---
  async addPayment(uid, payment) {
    const paymentId = payment.id;
    const recordId = payment.recordId;
    const recordRef = this.db.collection("users").doc(uid).collection("records").doc(recordId);
    const eventRef = this.db.collection("users").doc(uid).collection("events").doc(paymentId);

    return this.db.runTransaction(async (t) => {
      const recSnap = await t.get(recordRef);
      if (!recSnap.exists) {
        const err = new Error("Record not found");
        err.code = "ERR_RECORD_NOT_FOUND";
        err.status = 404;
        throw err;
      }

      const rec = recSnap.data();
      const currentRemaining = rec.remainingAmountPaise || rec.principalAmountPaise;
      const newRemaining = Math.max(0, currentRemaining - payment.amountPaise);
      const isSettled = newRemaining === 0;

      t.set(eventRef, {
        ...payment,
        eventType: "PAYMENT",
        timestampMs: payment.paidAtMs || Date.now()
      });

      t.set(recordRef, {
        remainingAmountPaise: newRemaining,
        isSettled,
        updatedAtMs: Date.now()
      }, { merge: true });

      return payment;
    });
  }

  // --- Plans ---
  async createPlan(uid, plan) {
    const planId = plan.planId || plan.id;
    await this.db.collection("users").doc(uid).collection("plans").doc(planId).set(plan);
    return plan;
  }

  async getPlan(uid, planId) {
    const snap = await this.db.collection("users").doc(uid).collection("plans").doc(planId).get();
    return snap.exists ? snap.data() : null;
  }

  async listPlans(uid) {
    const snap = await this.db.collection("users").doc(uid).collection("plans").get();
    return snap.docs.map(d => d.data());
  }

  async updatePlan(uid, planId, updates) {
    await this.db.collection("users").doc(uid).collection("plans").doc(planId).set(updates, { merge: true });
    return this.getPlan(uid, planId);
  }

  // --- Events ---
  async addEvent(uid, event) {
    const eventId = event.id;
    await this.db.collection("users").doc(uid).collection("events").doc(eventId).set(event);
    return event;
  }

  async listEvents(uid, recordId) {
    let query = this.db.collection("users").doc(uid).collection("events");
    if (recordId) {
      query = query.where("recordId", "==", recordId);
    }
    const snap = await query.get();
    return snap.docs.map(d => d.data());
  }
}

module.exports = { FirestoreLedgerRepository };
