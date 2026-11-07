/**
 * Firestore System Config Repository (functions/src/repositories/firestore/configRepository.js)
 * Synchronizes Kill Switches and Circuit Breakers across all instances via Firestore.
 */

const { getDb } = require("./firestoreClient");

class FirestoreConfigRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getKillSwitches() {
    const snap = await this.db.collection("system_config").doc("killSwitches").get();
    return snap.exists ? snap.data() : {};
  }

  async setKillSwitches(switches) {
    await this.db.collection("system_config").doc("killSwitches").set(switches, { merge: true });
    return this.getKillSwitches();
  }

  async getCircuitBreakers() {
    const snap = await this.db.collection("system_config").doc("circuitBreakers").get();
    return snap.exists ? snap.data() : {};
  }

  async setCircuitBreakers(breakers) {
    await this.db.collection("system_config").doc("circuitBreakers").set(breakers, { merge: true });
    return this.getCircuitBreakers();
  }
}

module.exports = { FirestoreConfigRepository };
