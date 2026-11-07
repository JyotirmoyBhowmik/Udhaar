/**
 * Client-Side Synchronization Engine (webapp/src/sync/syncEngine.js)
 * Implements Local-First write paths, optimistic UI updates, outbox mutation queue,
 * exponential backoff retry with jitter, and delta sync pull.
 */

const { ledgerDb } = require("../db/ledgerDb");
const { createHlc, incrementHlc, updateHlc, packHlc } = require("./hlc");

class SyncEngine {
  constructor({ db = ledgerDb, authClient, deviceId = "web_replica_1", transport = null }) {
    this.db = db;
    this.authClient = authClient;
    this.deviceId = deviceId;
    this.transport = transport; // Custom transport for in-process testing or direct sync
    this.currentHlc = createHlc(deviceId);
    this.isSyncing = false;
    this.syncListeners = new Set();
  }

  async init() {
    await this.db.open();
    const savedHlc = await this.db.getSyncState("local_hlc");
    if (savedHlc) {
      this.currentHlc = savedHlc;
    }
  }

  _tickHlc() {
    this.currentHlc = incrementHlc(this.currentHlc);
    this.db.setSyncState("local_hlc", this.currentHlc).catch(() => {});
    return packHlc(this.currentHlc);
  }

  _generateIdempotencyKey() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return `idem_${Date.now()}_${Math.random().toString(36).slice(2)}`;
  }

  _isOnline() {
    if (this.transport && typeof this.transport.isOnline === "function") {
      return this.transport.isOnline();
    }
    return typeof navigator === "undefined" ? true : navigator.onLine;
  }

  // -----------------------------------------------------------
  // Local-First Optimistic Write Methods: Record
  // -----------------------------------------------------------
  async createRecord(recordData) {
    const syncId = recordData.syncId || (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `sync_${Date.now()}`);
    const hlcStr = recordData.hlc || this._tickHlc();
    const idempotencyKey = recordData.idempotencyKey || this._generateIdempotencyKey();
    const clientRev = recordData.clientRev !== undefined ? Number(recordData.clientRev) : 1;
    const now = Date.now();

    const localRecord = {
      ...recordData,
      syncId,
      recordId: recordData.recordId || `rec_${syncId.slice(0, 10)}`,
      partyName: recordData.partyName || "Counterparty",
      principalAmountPaise: Number(recordData.principalAmountPaise) || 0,
      remainingAmountPaise: Number(recordData.remainingAmountPaise !== undefined ? recordData.remainingAmountPaise : recordData.principalAmountPaise) || 0,
      counterpartyRole: recordData.counterpartyRole || "BORROWER",
      status: recordData.status || "PENDING",
      clientRev,
      serverRev: 0,
      hlc: hlcStr,
      syncStatus: "LOCAL_ONLY",
      createdAtMs: now,
      updatedAtMs: now,
      deletedAtMs: null
    };

    // 1. Write immediately to local replica database
    await this.db.putRecord(localRecord);

    // 2. Enqueue mutation in local outbox
    await this.db.enqueueMutation({
      entity: "record",
      syncId,
      op: "CREATE",
      fields: localRecord,
      clientRev,
      hlc: hlcStr,
      idempotencyKey
    });

    this._notifyListeners("RECORD_CREATED", localRecord);

    // 3. Trigger sync in background if online
    if (this._isOnline()) {
      this.sync().catch(() => {});
    }

    return localRecord;
  }

  async updateRecord(syncId, updates) {
    const existing = await this.db.getRecord(syncId);
    if (!existing) {
      throw new Error(`Record ${syncId} not found in local database`);
    }

    const hlcStr = updates.hlc || this._tickHlc();
    const newClientRev = updates.clientRev !== undefined ? Number(updates.clientRev) : (existing.clientRev || 1) + 1;
    const now = Date.now();

    const updated = {
      ...existing,
      ...updates,
      clientRev: newClientRev,
      hlc: hlcStr,
      syncStatus: "LOCAL_ONLY",
      updatedAtMs: now
    };

    await this.db.putRecord(updated);

    await this.db.enqueueMutation({
      entity: "record",
      syncId,
      op: "UPDATE",
      fields: updates,
      clientRev: newClientRev,
      hlc: hlcStr,
      idempotencyKey: updates.idempotencyKey || this._generateIdempotencyKey()
    });

    this._notifyListeners("RECORD_UPDATED", updated);

    if (this._isOnline()) {
      this.sync().catch(() => {});
    }

    return updated;
  }

  async deleteRecord(syncId, options = {}) {
    const existing = await this.db.getRecord(syncId);
    if (!existing) return null;

    const hlcStr = options.hlc || this._tickHlc();
    const newClientRev = options.clientRev !== undefined ? Number(options.clientRev) : (existing.clientRev || 1) + 1;
    const now = Date.now();

    const updated = {
      ...existing,
      deletedAtMs: now,
      clientRev: newClientRev,
      hlc: hlcStr,
      syncStatus: "LOCAL_ONLY",
      updatedAtMs: now
    };

    await this.db.putRecord(updated);

    await this.db.enqueueMutation({
      entity: "record",
      syncId,
      op: "DELETE",
      fields: { deletedAtMs: now },
      clientRev: newClientRev,
      hlc: hlcStr,
      idempotencyKey: options.idempotencyKey || this._generateIdempotencyKey()
    });

    this._notifyListeners("RECORD_DELETED", updated);

    if (this._isOnline()) {
      this.sync().catch(() => {});
    }

    return updated;
  }

  // -----------------------------------------------------------
  // Local-First Optimistic Write Methods: Payment
  // -----------------------------------------------------------
  async createPayment(paymentData) {
    const paymentId = paymentData.paymentId || (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `pay_${Date.now()}`);
    const hlcStr = paymentData.hlc || this._tickHlc();
    const idempotencyKey = paymentData.idempotencyKey || this._generateIdempotencyKey();
    const now = Date.now();
    const recordSyncId = paymentData.recordSyncId;

    const paymentRecord = {
      ...paymentData,
      paymentId,
      recordSyncId,
      amountPaise: Number(paymentData.amountPaise) || 0,
      paymentMode: paymentData.paymentMode || "UPI",
      note: paymentData.note || "",
      timestampMs: paymentData.timestampMs || now,
      hlc: hlcStr,
      syncStatus: "LOCAL_ONLY"
    };

    await this.db.putPayment(paymentRecord);

    // Optimistically update local record remaining balance
    if (recordSyncId) {
      const rec = await this.db.getRecord(recordSyncId);
      if (rec) {
        rec.remainingAmountPaise = Math.max(0, (rec.remainingAmountPaise !== undefined ? rec.remainingAmountPaise : rec.principalAmountPaise) - paymentRecord.amountPaise);
        if (rec.remainingAmountPaise === 0) {
          rec.status = "SETTLED";
        }
        rec.updatedAtMs = now;
        await this.db.putRecord(rec);
      }
    }

    await this.db.enqueueMutation({
      entity: "payment",
      syncId: paymentId,
      op: "CREATE",
      fields: paymentRecord,
      clientRev: 1,
      hlc: hlcStr,
      idempotencyKey
    });

    this._notifyListeners("PAYMENT_CREATED", paymentRecord);

    if (this._isOnline()) {
      this.sync().catch(() => {});
    }

    return paymentRecord;
  }

  // -----------------------------------------------------------
  // Sync Worker & Outbox Drainage
  // -----------------------------------------------------------
  async sync() {
    if (this.isSyncing) return;
    this.isSyncing = true;

    try {
      await this.drainOutbox();
      await this.pullDeltas();
    } finally {
      this.isSyncing = false;
    }
  }

  async drainOutbox() {
    const pending = await this.db.getPendingMutations();
    if (pending.length === 0) return;

    const changes = pending.map(m => ({
      entity: m.entity,
      syncId: m.syncId,
      op: m.op,
      fields: m.fields,
      clientRev: m.clientRev,
      hlc: m.hlc,
      idempotencyKey: m.idempotencyKey
    }));

    try {
      let results;

      if (this.transport) {
        const res = await this.transport.push({
          deviceId: this.deviceId,
          changes
        });
        results = res.results;
      } else {
        const response = await fetch("/api/v1/sync/push", {
          method: "POST",
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            deviceId: this.deviceId,
            changes
          })
        });

        if (!response.ok) {
          throw new Error(`Sync push failed with status ${response.status}`);
        }

        const data = await response.json();
        results = data.results;
      }

      for (let i = 0; i < results.length; i++) {
        const res = results[i];
        const mutation = pending[i];
        if (!mutation) continue;

        if (res.status === "ACCEPTED") {
          // Success: Remove from outbox & update record to SYNCED
          await this.db.removeMutation(mutation.id);
          if (mutation.entity === "record") {
            const localRecord = await this.db.getRecord(mutation.syncId);
            if (localRecord) {
              localRecord.serverRev = res.serverRev;
              localRecord.syncStatus = "SYNCED";
              await this.db.putRecord(localRecord);
            }
          } else if (mutation.entity === "payment") {
            const localPayment = await this.db.getPayment(mutation.syncId);
            if (localPayment) {
              localPayment.syncStatus = "SYNCED";
              await this.db.putPayment(localPayment);
            }
          }
        } else if (res.status === "CONFLICT") {
          // Conflict: mark conflict on record
          await this.db.removeMutation(mutation.id);
          const localRecord = await this.db.getRecord(mutation.syncId);
          if (localRecord) {
            localRecord.syncStatus = "CONFLICT";
            localRecord.conflictDetails = res.conflictDetails;
            await this.db.putRecord(localRecord);
          }
        } else if (res.status === "REJECTED") {
          await this.db.removeMutation(mutation.id);
        }
      }

      this._notifyListeners("OUTBOX_DRAINED", { count: results.length });
    } catch (err) {
      // Exponential backoff retry with jitter: 1s, 2s, 4s, 8s (max 30s)
      const now = Date.now();
      for (const m of pending) {
        const attempts = (m.attempts || 0) + 1;
        const baseDelayMs = Math.min(1000 * Math.pow(2, attempts - 1), 30000);
        const jitterMs = Math.floor(Math.random() * 500);
        m.attempts = attempts;
        m.status = "FAILED";
        m.nextRetryAtMs = now + baseDelayMs + jitterMs;
        await this.db.updateMutation(m);
      }
      this._notifyListeners("SYNC_FAILED", { error: err.message });
    }
  }

  async pullDeltas() {
    const cursor = (await this.db.getSyncState("last_pulled_cursor")) || 0;

    try {
      let changes;
      let nextCursor;

      if (this.transport) {
        const pullRes = await this.transport.pull({ cursor, limit: 100 });
        changes = pullRes.changes;
        nextCursor = pullRes.nextCursor;
      } else {
        const response = await fetch(`/api/v1/sync/pull?cursor=${cursor}&limit=100`, {
          method: "GET",
          headers: { "Accept": "application/json" }
        });

        if (!response.ok) return;

        const data = await response.json();
        changes = data.changes;
        nextCursor = data.nextCursor;
      }

      if (!Array.isArray(changes) || changes.length === 0) return;

      for (const entry of changes) {
        if (entry.entity === "record") {
          const existing = await this.db.getRecord(entry.syncId);
          if (!existing) {
            // New record from server
            await this.db.putRecord({
              ...entry.fields,
              syncId: entry.syncId,
              serverRev: entry.serverRev,
              syncStatus: "SYNCED"
            });
          } else {
            // If local record is in CONFLICT, preserve the conflict state until user resolves it
            if (existing.syncStatus === "CONFLICT") {
              continue;
            }
            // Merge server delta
            if (entry.serverRev >= (existing.serverRev || 0)) {
              await this.db.putRecord({
                ...existing,
                ...entry.fields,
                serverRev: entry.serverRev,
                syncStatus: "SYNCED"
              });
            }
          }
        } else if (entry.entity === "payment") {
          await this.db.putPayment({
            ...entry.fields,
            syncStatus: "SYNCED"
          });

          // Recalculate remaining balance for the target record
          const recSyncId = entry.fields.recordSyncId;
          if (recSyncId) {
            const targetRec = await this.db.getRecord(recSyncId);
            if (targetRec) {
              const allPayments = await this.db.getAllPayments();
              const recPayments = allPayments.filter(p => p.recordSyncId === recSyncId);
              const totalPaid = recPayments.reduce((acc, p) => acc + (p.amountPaise || 0), 0);
              targetRec.remainingAmountPaise = Math.max(0, targetRec.principalAmountPaise - totalPaid);
              if (targetRec.remainingAmountPaise === 0) {
                targetRec.status = "SETTLED";
              }
              await this.db.putRecord(targetRec);
            }
          }
        }

        // Update local HLC with remote timestamp
        if (entry.hlc) {
          this.currentHlc = updateHlc(this.currentHlc, entry.hlc);
        }
      }

      await this.db.setSyncState("last_pulled_cursor", nextCursor);
      await this.db.setSyncState("local_hlc", this.currentHlc);
      this._notifyListeners("DELTAS_PULLED", { count: changes.length });
    } catch (_) {}
  }

  onSyncEvent(callback) {
    this.syncListeners.add(callback);
    return () => this.syncListeners.delete(callback);
  }

  _notifyListeners(type, data) {
    for (const listener of this.syncListeners) {
      try {
        listener({ type, data });
      } catch (_) {}
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SyncEngine };
}
