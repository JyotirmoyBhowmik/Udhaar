/**
 * Firestore Crypto Repository (functions/src/repositories/firestore/cryptoRepository.js)
 * 
 * Manages per-user Cloud KMS wrapped Data Encryption Keys (DEKs) in Firestore.
 * Document Path: users/{uid}/crypto/dek
 */

const { getFirestore } = require("./firestoreClient");

class FirestoreCryptoRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getFirestore();
  }

  async getUserDekDoc(uid) {
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
}

module.exports = { FirestoreCryptoRepository };
