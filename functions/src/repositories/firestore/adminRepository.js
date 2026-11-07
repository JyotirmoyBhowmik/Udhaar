/**
 * Firestore Admin Repository (functions/src/repositories/firestore/adminRepository.js)
 * Persists admin roles, append-only admin audit logs, and backup snapshots.
 */

const { getDb, clearCollection } = require("./firestoreClient");

class FirestoreAdminRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async getAdminRole(uid) {
    if (!uid) return null;
    const snap = await this.db.collection("admin_roles").doc(uid).get();
    return snap.exists ? snap.data().role : null;
  }

  async setAdminRole(uid, role) {
    await this.db.collection("admin_roles").doc(uid).set({
      uid,
      role,
      updatedAtMs: Date.now()
    });
    return role;
  }

  async removeAdminRole(uid) {
    if (!uid) return false;
    await this.db.collection("admin_roles").doc(uid).delete();
    return true;
  }

  /**
   * Append-only audit log persistence in Firestore
   */
  async appendAuditLog(entry) {
    const id = entry.id || entry.logId || `adm_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const docData = {
      ...entry,
      id,
      logId: id,
      timestampMs: entry.timestampMs || Date.now()
    };
    const docRef = this.db.collection("admin_audit_logs").doc(id);
    await docRef.create(docData);
    return Object.freeze(docData);
  }

  async listAuditLogs(options = {}) {
    const limit = typeof options === "number" ? options : (Number(options.limit) || 100);
    const action = typeof options === "object" && options !== null ? options.action : null;

    const snap = await this.db.collection("admin_audit_logs").get();
    let logs = snap.docs.map(d => Object.freeze(d.data()));
    if (action) {
      logs = logs.filter(l => l.action === action);
    }
    logs.sort((a, b) => (b.timestampMs || 0) - (a.timestampMs || 0));
    const maxLimit = Math.min(Math.max(1, limit), 500);
    return logs.slice(0, maxLimit);
  }

  async saveBackupSnapshot(snapshot) {
    const id = snapshot.snapshotId || snapshot.id;
    const docData = { ...snapshot, id, snapshotId: id };
    await this.db.collection("backup_snapshots").doc(id).set(docData);
    return Object.freeze(docData);
  }

  async getBackupSnapshot(snapshotId) {
    if (!snapshotId) return null;
    const snap = await this.db.collection("backup_snapshots").doc(snapshotId).get();
    return snap.exists ? Object.freeze(snap.data()) : null;
  }

  async listBackupSnapshots(limit = 50) {
    const snap = await this.db.collection("backup_snapshots").get();
    const list = snap.docs
      .map(d => Object.freeze(d.data()))
      .sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
    const maxLimit = Math.min(Math.max(1, Number(limit) || 50), 200);
    return list.slice(0, maxLimit);
  }

  async clear() {
    await Promise.all([
      clearCollection(this.db, "admin_roles"),
      clearCollection(this.db, "admin_audit_logs"),
      clearCollection(this.db, "backup_snapshots")
    ]);
  }
}

module.exports = { FirestoreAdminRepository };
