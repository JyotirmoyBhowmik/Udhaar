/**
 * Firestore Link Repository (functions/src/repositories/firestore/linkRepository.js)
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreLinkRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveWebLink(recordId, linkDoc) {
    await this.db.collection("webLinks").doc(recordId).set(linkDoc);
    return linkDoc;
  }

  async getWebLink(recordId) {
    if (!recordId) return null;
    const snap = await this.db.collection("webLinks").doc(recordId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateWebLink(recordId, updates) {
    await this.db.collection("webLinks").doc(recordId).set(updates, { merge: true });
    return this.getWebLink(recordId);
  }

  async deleteWebLink(recordId) {
    await this.db.collection("webLinks").doc(recordId).delete();
    return true;
  }

  async listWebLinks() {
    const snap = await this.db.collection("webLinks").get();
    return snap.docs.map(d => d.data());
  }

  async countActiveWebLinks() {
    const now = Date.now();
    const links = await this.listWebLinks();
    return links.filter(l => !l.isRevoked && (!l.expiresAtMs || l.expiresAtMs > now)).length;
  }

  async checkAndIncrementIpRateLimit(clientIp, windowMs = 3600000, maxRequests = 20) {
    const ip = String(clientIp || "127.0.0.1").replace(/[^a-zA-Z0-9_.:-]/g, "_");
    const docRef = this.db.collection("linkIpRateLimits").doc(ip);
    const now = Date.now();

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      let entry = snap.exists ? snap.data() : null;

      if (!entry || now - entry.windowStartMs >= windowMs) {
        entry = { count: 1, windowStartMs: now };
        t.set(docRef, entry);
        return entry;
      }

      entry.count += 1;
      if (entry.count > maxRequests) {
        const err = new Error("Too many guest portal requests from this IP. Please try again in an hour.");
        err.code = "ERR_RATE_LIMIT_EXCEEDED";
        err.status = 429;
        err.retryAfterSeconds = Math.ceil((entry.windowStartMs + windowMs - now) / 1000);
        throw err;
      }

      t.set(docRef, entry, { merge: true });
      return entry;
    });
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "webLinks"),
      clearCollection(this.db, "linkIpRateLimits")
    ]);
  }
}

module.exports = { FirestoreLinkRepository };
