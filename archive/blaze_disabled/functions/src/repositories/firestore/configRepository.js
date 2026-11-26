/**
 * Firestore Config Repository (functions/src/repositories/firestore/configRepository.js)
 * Persists kill switches and circuit breakers in system_config collection.
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreConfigRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getKillSwitches() {
    const snap = await this.db.collection("system_config").doc("kill_switches").get();
    return snap.exists ? snap.data() : {};
  }

  async setKillSwitches(switches) {
    await this.db.collection("system_config").doc("kill_switches").set(switches);
    return this.getKillSwitches();
  }

  async getCircuitBreakers() {
    const snap = await this.db.collection("system_config").doc("circuit_breakers").get();
    return snap.exists ? snap.data() : {};
  }

  async setCircuitBreakers(breakers) {
    await this.db.collection("system_config").doc("circuit_breakers").set(breakers);
    return this.getCircuitBreakers();
  }

  async clear() {
    await clearCollection(this.db, "system_config");
  }
}

module.exports = { FirestoreConfigRepository };
