/**
 * Firestore Crypto Repository (functions/src/repositories/firestore/cryptoRepository.js)
 *
 * Manages per-user Cloud KMS wrapped Data Encryption Keys (DEKs) in Firestore.
 * Document Path: users/{uid}/crypto/dek
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreCryptoRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getUserDekDoc(uid) {
    if (!uid) return null;
    const snap = await this.db.collection("users").doc(uid).collection("crypto").doc("dek").get();
    return snap.exists ? snap.data() : null;
  }

  async saveUserDekDoc(uid, dekDoc) {
    await this.db.collection("users").doc(uid).collection("crypto").doc("dek").set(dekDoc);
    return dekDoc;
  }

  async deleteUserDekDoc(uid) {
    await this.db.collection("users").doc(uid).collection("crypto").doc("dek").delete();
    return true;
  }

  async getWrappedDek(uid) {
    const doc = await this.getUserDekDoc(uid);
    return doc ? doc.wrappedDek : null;
  }

  async saveWrappedDek(uid, wrappedDek, metadata = {}) {
    const docData = {
      uid,
      wrappedDek,
      ...metadata,
      updatedAtMs: Date.now()
    };
    await this.saveUserDekDoc(uid, docData);
    return docData;
  }

  async deleteWrappedDek(uid) {
    return this.deleteUserDekDoc(uid);
  }

  async deleteUserDek(uid) {
    return this.deleteUserDekDoc(uid);
  }

  async clear() {
    await clearCollection(this.db, "users");
  }
}

module.exports = { FirestoreCryptoRepository };
