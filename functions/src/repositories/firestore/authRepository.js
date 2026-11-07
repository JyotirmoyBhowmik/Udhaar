/**
 * Firestore Auth Repository (functions/src/repositories/firestore/authRepository.js)
 * Implements persistent user storage, blind indexes, and atomic sign-up transactions.
 */

const { getDb } = require("./firestoreClient");

class FirestoreAuthRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getUser(uid) {
    if (!uid) return null;
    const snap = await this.db.collection("users").doc(uid).get();
    return snap.exists ? snap.data() : null;
  }

  async getUserByPhoneHash(phoneHash) {
    if (!phoneHash) return null;
    const indexSnap = await this.db.collection("phoneIndex").doc(phoneHash).get();
    if (!indexSnap.exists) return null;
    const { uid } = indexSnap.data();
    return this.getUser(uid);
  }

  async getUserByEmailHash(emailHash) {
    if (!emailHash) return null;
    const indexSnap = await this.db.collection("emailIndex").doc(emailHash).get();
    if (!indexSnap.exists) return null;
    const { uid } = indexSnap.data();
    return this.getUser(uid);
  }

  /**
   * Atomic user registration transaction enforcing unique phone and email indexes
   */
  async createUserTransaction(userRecord, phoneIndexRecord, emailIndexRecord) {
    const db = this.db;
    const phoneHash = phoneIndexRecord.phoneHash;
    const emailHash = emailIndexRecord.emailHash;
    const uid = userRecord.uid;

    const phoneRef = db.collection("phoneIndex").doc(phoneHash);
    const emailRef = db.collection("emailIndex").doc(emailHash);
    const userRef = db.collection("users").doc(uid);

    return db.runTransaction(async (transaction) => {
      const [phoneSnap, emailSnap] = await Promise.all([
        transaction.get(phoneRef),
        transaction.get(emailRef)
      ]);

      if (phoneSnap.exists || emailSnap.exists) {
        const err = new Error("An account with these credentials already exists.");
        err.code = "ERR_ACCOUNT_EXISTS";
        err.status = 409;
        throw err;
      }

      transaction.set(phoneRef, {
        uid,
        createdAt: phoneIndexRecord.createdAt || Date.now()
      });

      transaction.set(emailRef, {
        uid,
        createdAt: emailIndexRecord.createdAt || Date.now()
      });

      transaction.set(userRef, userRecord);

      return userRecord;
    });
  }

  async updateUser(uid, updates) {
    await this.db.collection("users").doc(uid).set(updates, { merge: true });
    return this.getUser(uid);
  }

  async saveAuditEntry(entry) {
    const docId = entry.id || `audit_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    await this.db.collection("auditLogs").doc(docId).set(entry);
    return entry;
  }
}

module.exports = { FirestoreAuthRepository };
