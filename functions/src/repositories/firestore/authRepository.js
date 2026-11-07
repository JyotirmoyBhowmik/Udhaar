/**
 * Firestore Auth Repository (functions/src/repositories/firestore/authRepository.js)
 * Implements atomic uniqueness indexes (phoneIndex, emailIndex) and user profile persistence.
 */

const { getDb, clearCollection } = require("./firestoreClient");

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
    const idxSnap = await this.db.collection("phoneIndex").doc(phoneHash).get();
    if (!idxSnap.exists) return null;
    const { uid } = idxSnap.data();
    return this.getUser(uid);
  }

  async getUserByEmailHash(emailHash) {
    if (!emailHash) return null;
    const idxSnap = await this.db.collection("emailIndex").doc(emailHash).get();
    if (!idxSnap.exists) return null;
    const { uid } = idxSnap.data();
    return this.getUser(uid);
  }

  async findUserByQueryHash(queryHash) {
    if (!queryHash) return null;
    const byUid = await this.getUser(queryHash);
    if (byUid) return byUid;
    const byPhone = await this.getUserByPhoneHash(queryHash);
    if (byPhone) return byPhone;
    const byEmail = await this.getUserByEmailHash(queryHash);
    if (byEmail) return byEmail;

    const phoneSnap = await this.db.collection("users").where("phoneHash", "==", queryHash).limit(1).get();
    if (!phoneSnap.empty) return phoneSnap.docs[0].data();

    const emailSnap = await this.db.collection("users").where("emailHash", "==", queryHash).limit(1).get();
    if (!emailSnap.empty) return emailSnap.docs[0].data();

    return null;
  }

  async listUsers() {
    const snap = await this.db.collection("users").get();
    return snap.docs.map(d => d.data()).filter(u => u && u.uid);
  }

  async countUsers() {
    const users = await this.listUsers();
    return users.length;
  }

  /**
   * Atomically creates user and reserves phoneHash and emailHash in Firestore transaction.
   * Throws ERR_ACCOUNT_EXISTS if either identifier is already registered.
   */
  async createUserWithIndexes(userDoc, phoneHash, emailHash) {
    const { uid } = userDoc;
    const userRef = this.db.collection("users").doc(uid);
    const phoneRef = phoneHash ? this.db.collection("phoneIndex").doc(phoneHash) : null;
    const emailRef = emailHash ? this.db.collection("emailIndex").doc(emailHash) : null;

    await this.db.runTransaction(async (t) => {
      const reads = [t.get(userRef)];
      if (phoneRef) reads.push(t.get(phoneRef));
      if (emailRef) reads.push(t.get(emailRef));

      const snaps = await Promise.all(reads);
      const userSnap = snaps[0];
      const phoneSnap = phoneRef ? snaps[1] : null;
      const emailSnap = emailRef ? snaps[phoneRef ? 2 : 1] : null;

      if (userSnap.exists || (phoneSnap && phoneSnap.exists) || (emailSnap && emailSnap.exists)) {
        const err = new Error("An account with these credentials already exists.");
        err.code = "ERR_ACCOUNT_EXISTS";
        err.status = 409;
        throw err;
      }

      const now = Date.now();
      t.set(userRef, userDoc);
      if (phoneRef) {
        t.set(phoneRef, { uid, phoneHash, createdAtMs: now });
      }
      if (emailRef) {
        t.set(emailRef, { uid, emailHash, createdAtMs: now });
      }
    });

    return userDoc;
  }

  async createUserTransaction(userDoc, phoneIdxOrHash, emailIdxOrHash) {
    const phoneHash = typeof phoneIdxOrHash === "string"
      ? phoneIdxOrHash
      : (phoneIdxOrHash?.phoneHash || userDoc.phoneHash || null);
    const emailHash = typeof emailIdxOrHash === "string"
      ? emailIdxOrHash
      : (emailIdxOrHash?.emailHash || userDoc.emailHash || null);
    return this.createUserWithIndexes(
      { ...userDoc, phoneHash: phoneHash || userDoc.phoneHash, emailHash: emailHash || userDoc.emailHash },
      phoneHash,
      emailHash
    );
  }

  async updateUser(uid, updates) {
    const userRef = this.db.collection("users").doc(uid);
    const payload = { ...updates, uid };
    await userRef.set(payload, { merge: true });
    if (payload.phoneHash) {
      await this.db.collection("phoneIndex").doc(payload.phoneHash).set({
        uid,
        phoneHash: payload.phoneHash,
        updatedAtMs: Date.now()
      }, { merge: true });
    }
    if (payload.emailHash) {
      await this.db.collection("emailIndex").doc(payload.emailHash).set({
        uid,
        emailHash: payload.emailHash,
        updatedAtMs: Date.now()
      }, { merge: true });
    }
    return this.getUser(uid);
  }

  /**
   * Atomically updates phoneHash or emailHash index and user record
   */
  async updateIdentifierTransaction(uid, { oldPhoneHash, newPhoneHash, oldEmailHash, newEmailHash, userUpdates }) {
    const userRef = this.db.collection("users").doc(uid);

    await this.db.runTransaction(async (t) => {
      if (newPhoneHash && newPhoneHash !== oldPhoneHash) {
        const newPhoneRef = this.db.collection("phoneIndex").doc(newPhoneHash);
        const snap = await t.get(newPhoneRef);
        if (snap.exists && snap.data().uid !== uid) {
          const err = new Error("Phone number is already linked to another account.");
          err.code = "ERR_PHONE_ALREADY_IN_USE";
          err.status = 409;
          throw err;
        }
        if (oldPhoneHash) {
          t.delete(this.db.collection("phoneIndex").doc(oldPhoneHash));
        }
        t.set(newPhoneRef, { uid, phoneHash: newPhoneHash, updatedAtMs: Date.now() });
      }

      if (newEmailHash && newEmailHash !== oldEmailHash) {
        const newEmailRef = this.db.collection("emailIndex").doc(newEmailHash);
        const snap = await t.get(newEmailRef);
        if (snap.exists && snap.data().uid !== uid) {
          const err = new Error("Email address is already linked to another account.");
          err.code = "ERR_EMAIL_ALREADY_IN_USE";
          err.status = 409;
          throw err;
        }
        if (oldEmailHash) {
          t.delete(this.db.collection("emailIndex").doc(oldEmailHash));
        }
        t.set(newEmailRef, { uid, emailHash: newEmailHash, updatedAtMs: Date.now() });
      }

      t.set(userRef, userUpdates, { merge: true });
    });

    return this.getUser(uid);
  }

  async appendAuditLog(event) {
    const id = event.id || `aud_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const record = { id, ...event, timestampMs: event.timestampMs || Date.now() };
    await this.db.collection("authAuditLogs").doc(id).set(record);
    return record;
  }

  async listAuditLogs(uid = null) {
    let query = this.db.collection("authAuditLogs");
    if (uid) {
      query = query.where("uid", "==", uid);
    }
    const snap = await query.get();
    return snap.docs.map(d => d.data());
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "users"),
      clearCollection(this.db, "phoneIndex"),
      clearCollection(this.db, "emailIndex"),
      clearCollection(this.db, "authAuditLogs")
    ]);
  }
}

module.exports = { FirestoreAuthRepository };
