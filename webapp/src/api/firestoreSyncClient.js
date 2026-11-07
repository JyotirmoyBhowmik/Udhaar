/**
 * Udhaar Client-Side Direct Firestore Sync Engine (Spark Mode)
 * (webapp/src/api/firestoreSyncClient.js)
 * 
 * In Spark Mode, sync operates directly from client devices to Cloud Firestore:
 * - Zero Cloud Functions calls (no /api/v1/**).
 * - Scoped strictly to the owner: /users/{userId}/records, people, events, plans.
 * - Enforces monotonic revision tracking: rev = existing.rev + 1.
 * - Conflict Resolution:
 *   1. Last-Write-Wins (LWW) on records based on higher revision (rev).
 *   2. CRDT-Lite Set Union for partial payments/events (events collection is append-only).
 * - Quota Guard: Graceful handling of RESOURCE_EXHAUSTED without crashing.
 */

class FirestoreSyncClient {
  constructor(firestoreInstance, authInstance) {
    this.db = firestoreInstance || (typeof window !== "undefined" && window.firebase?.firestore ? window.firebase.firestore() : null);
    this.auth = authInstance || (typeof window !== "undefined" && window.firebase?.auth ? window.firebase.auth() : null);
  }

  getCurrentUser() {
    return this.auth?.currentUser || null;
  }

  _assertAuthenticated() {
    const user = this.getCurrentUser();
    if (!user) {
      const err = new Error("[ERR_UNAUTHENTICATED] Authentication required. Please sign in to sync records.");
      err.code = "ERR_UNAUTHENTICATED";
      throw err;
    }
    if (!user.emailVerified) {
      const err = new Error("[ERR_EMAIL_UNVERIFIED] Email verification required. Please verify your email before syncing.");
      err.code = "ERR_EMAIL_UNVERIFIED";
      throw err;
    }
    return user;
  }

  /**
   * Save or update a record directly in Firestore.
   */
  async saveRecord(record) {
    const user = this._assertAuthenticated();
    const recordId = record.id || record.recordId;
    if (!recordId) throw new Error("Record ID is required");

    const docRef = this.db.collection("users").doc(user.uid).collection("records").doc(recordId);

    try {
      const snap = await docRef.get();
      const currentRev = snap.exists ? (snap.data().rev || 1) : 0;
      const nextRev = snap.exists ? currentRev + 1 : 1;

      const toSave = {
        ...record,
        id: recordId,
        recordId: recordId,
        uid: user.uid,
        rev: nextRev,
        updatedAtMs: Date.now(),
        createdAtMs: snap.exists ? (snap.data().createdAtMs || Date.now()) : (record.createdAtMs || Date.now())
      };

      await docRef.set(toSave, { merge: true });
      return toSave;
    } catch (err) {
      throw this._handleFirestoreError(err);
    }
  }

  /**
   * Pull records updated since last sync timestamp.
   */
  async pullRecords(sinceMs = 0) {
    const user = this._assertAuthenticated();
    try {
      let query = this.db.collection("users").doc(user.uid).collection("records");
      if (sinceMs > 0) {
        query = query.where("updatedAtMs", ">", sinceMs);
      }
      const snap = await query.get();
      return snap.docs.map(doc => doc.data());
    } catch (err) {
      throw this._handleFirestoreError(err);
    }
  }

  /**
   * Add an append-only event (e.g. payment, dispute, confirmation).
   */
  async addEvent(event) {
    const user = this._assertAuthenticated();
    const eventId = event.id || event.eventId || `evt_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const docRef = this.db.collection("users").doc(user.uid).collection("events").doc(eventId);

    const toSave = {
      ...event,
      id: eventId,
      eventId: eventId,
      uid: user.uid,
      timestampMs: event.timestampMs || Date.now(),
      createdAtMs: Date.now()
    };

    try {
      await docRef.set(toSave);
      return toSave;
    } catch (err) {
      throw this._handleFirestoreError(err);
    }
  }

  /**
   * List all events for a given record (used for CRDT-Lite payment set union).
   */
  async listEvents(recordId = null) {
    const user = this._assertAuthenticated();
    try {
      let query = this.db.collection("users").doc(user.uid).collection("events");
      if (recordId) {
        query = query.where("recordId", "==", recordId);
      }
      const snap = await query.get();
      return snap.docs.map(doc => doc.data()).sort((a, b) => (a.timestampMs || 0) - (b.timestampMs || 0));
    } catch (err) {
      throw this._handleFirestoreError(err);
    }
  }

  /**
   * Reconcile local records with incoming remote records using LWW and Payment Union.
   */
  reconcileRecords(localRecords, remoteRecords, allEvents = []) {
    const recordMap = new Map();

    for (const rec of localRecords) {
      recordMap.set(rec.recordId || rec.id, { ...rec });
    }

    for (const remote of remoteRecords) {
      const id = remote.recordId || remote.id;
      const local = recordMap.get(id);

      if (!local) {
        recordMap.set(id, { ...remote });
      } else {
        // LWW based on higher revision (rev)
        const localRev = local.rev || 1;
        const remoteRev = remote.rev || 1;

        if (remoteRev > localRev) {
          recordMap.set(id, { ...remote });
        } else if (remoteRev === localRev && (remote.updatedAtMs || 0) > (local.updatedAtMs || 0)) {
          recordMap.set(id, { ...remote });
        }
      }
    }

    // Apply CRDT-Lite Set Union for partial repayments
    const paymentsByRecord = new Map();
    for (const evt of allEvents) {
      if (evt.eventType === "PAYMENT" && evt.recordId) {
        if (!paymentsByRecord.has(evt.recordId)) {
          paymentsByRecord.set(evt.recordId, new Set());
        }
        paymentsByRecord.get(evt.recordId).add(evt.eventId || evt.id);
      }
    }

    const merged = Array.from(recordMap.values());
    for (const rec of merged) {
      const id = rec.recordId || rec.id;
      const paymentEventIds = paymentsByRecord.get(id);
      if (paymentEventIds) {
        rec.paymentEventCount = paymentEventIds.size;
      }
    }

    return merged;
  }

  _handleFirestoreError(err) {
    const code = err.code || "";
    if (code === "resource-exhausted" || err.message?.includes("RESOURCE_EXHAUSTED")) {
      const quotaErr = new Error(
        "Cloud sync is resting until tomorrow due to daily free quota limits. " +
        "All your records remain safely saved on your device and will sync automatically."
      );
      quotaErr.code = "ERR_QUOTA_EXHAUSTED";
      quotaErr.isQuota = true;
      return quotaErr;
    }
    if (code === "permission-denied") {
      const permErr = new Error(
        "Access denied. Please ensure you are signed in and your email address is verified."
      );
      permErr.code = "ERR_PERMISSION_DENIED";
      return permErr;
    }
    return err;
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    FirestoreSyncClient
  };
}
