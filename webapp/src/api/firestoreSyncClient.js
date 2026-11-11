/**
 * Udhaar Web App — Direct Firestore Sync Client (Spark Plan SP-2 / SP-3)
 *
 * Uses the locally bundled `firebase` npm package (`firebaseSdk.js`) for direct
 * Firestore reads and writes with monotonic `rev` increments, Last-Write-Wins (`updatedAtMs`),
 * and append-only payment event set-union reconciliation.
 */

const { initFirebaseSdk } = require("./firebaseSdk");

class FirestoreSyncClient {
  /**
   * Supports both `new FirestoreSyncClient(db, auth)` and `new FirestoreSyncClient({ db, auth })`.
   */
  constructor(dbOrOptions = null, auth = null) {
    if (
      dbOrOptions &&
      typeof dbOrOptions === "object" &&
      !dbOrOptions.collection &&
      (dbOrOptions.db || dbOrOptions.auth || dbOrOptions.authClient)
    ) {
      this.db = dbOrOptions.db || null;
      this.auth = dbOrOptions.auth || dbOrOptions.authClient || null;
    } else {
      this.db = dbOrOptions;
      this.auth = auth;
    }
  }

  _resolveDbAndAuth() {
    if (this.db && this.auth) {
      return { db: this.db, auth: this.auth, sdk: null };
    }
    const services = initFirebaseSdk();
    return {
      db: this.db || services.firestore,
      auth: this.auth || services.auth,
      sdk: services.sdk
    };
  }

  _requireVerifiedUser() {
    const { auth } = this._resolveDbAndAuth();
    const user = auth && (auth.currentUser || (typeof auth.getCurrentUser === "function" ? auth.getCurrentUser() : null));
    if (!user || !user.uid) {
      const err = new Error("ERR_UNAUTHENTICATED: User must be signed in to sync.");
      err.code = "ERR_UNAUTHENTICATED";
      throw err;
    }
    if (!user.emailVerified) {
      const err = new Error("ERR_EMAIL_UNVERIFIED: Cloud sync stays off until your email is verified.");
      err.code = "ERR_EMAIL_UNVERIFIED";
      throw err;
    }
    return user;
  }

  async saveRecord(record) {
    const user = this._requireVerifiedUser();
    const { db, sdk } = this._resolveDbAndAuth();
    if (!record || !record.id) {
      throw new Error("Record id is required");
    }

    try {
      let existingData = null;
      if (db && typeof db.collection === "function") {
        const docRef = db.collection("users").doc(user.uid).collection("records").doc(record.id);
        const snap = await docRef.get();
        if (snap && snap.exists) {
          existingData = typeof snap.data === "function" ? snap.data() : snap.data;
        }
        const nextRev = existingData && typeof existingData.rev === "number" ? existingData.rev + 1 : 1;
        const payload = {
          ...(existingData || {}),
          ...record,
          uid: user.uid,
          ownerUid: user.uid,
          rev: nextRev,
          updatedAtMs: Date.now()
        };
        await docRef.set(payload, { merge: true });
        return payload;
      }

      if (db && sdk && typeof sdk.doc === "function") {
        const ref = sdk.doc(db, "users", user.uid, "records", record.id);
        const snap = await sdk.getDoc(ref);
        if (snap.exists()) {
          existingData = snap.data();
        }
        const nextRev = existingData && typeof existingData.rev === "number" ? existingData.rev + 1 : 1;
        const payload = {
          ...(existingData || {}),
          ...record,
          uid: user.uid,
          ownerUid: user.uid,
          rev: nextRev,
          updatedAtMs: Date.now()
        };
        await sdk.setDoc(ref, payload, { merge: true });
        return payload;
      }

      throw new Error("Firestore instance is not configured.");
    } catch (err) {
      throw this._handleFirestoreError(err);
    }
  }

  reconcileRecords(localRecords = [], remoteRecords = [], paymentEvents = []) {
    const mergedMap = new Map();

    for (const loc of localRecords) {
      if (loc && loc.id) {
        mergedMap.set(loc.id, { ...loc });
      }
    }

    for (const rem of remoteRecords) {
      if (!rem || !rem.id) continue;
      const existing = mergedMap.get(rem.id);
      if (!existing) {
        mergedMap.set(rem.id, { ...rem });
      } else {
        const remRev = Number(rem.rev || 0);
        const locRev = Number(existing.rev || 0);
        const remTime = Number(rem.updatedAtMs || 0);
        const locTime = Number(existing.updatedAtMs || 0);
        if (remRev > locRev || (remRev === locRev && remTime >= locTime)) {
          mergedMap.set(rem.id, { ...existing, ...rem });
        }
      }
    }

    // Apply append-only payment events set-union by event id
    const eventsByRecord = new Map();
    const seenEventIds = new Set();
    for (const evt of paymentEvents) {
      if (!evt || !evt.id || seenEventIds.has(evt.id)) continue;
      seenEventIds.add(evt.id);
      if (evt.eventType === "PAYMENT" && evt.recordId) {
        const list = eventsByRecord.get(evt.recordId) || [];
        list.push(evt);
        eventsByRecord.set(evt.recordId, list);
      }
    }

    const result = [];
    for (const [id, rec] of mergedMap.entries()) {
      const evts = eventsByRecord.get(id) || [];
      result.push({
        ...rec,
        paymentEventCount: evts.length
      });
    }
    return result;
  }

  _handleFirestoreError(err) {
    const code = (err && err.code) || "";
    const msg = (err && err.message) || "";
    if (
      code === "resource-exhausted" ||
      code === "RESOURCE_EXHAUSTED" ||
      msg.includes("RESOURCE_EXHAUSTED") ||
      msg.toLowerCase().includes("exhausted")
    ) {
      const wrapped = new Error(
        "Free daily cloud sync quota reached — your local ledger is safe and cloud sync is resting until tomorrow (midnight Pacific Time)."
      );
      wrapped.code = "ERR_QUOTA_EXHAUSTED";
      wrapped.isQuota = true;
      return wrapped;
    }
    return err;
  }
}

module.exports = {
  FirestoreSyncClient
};
