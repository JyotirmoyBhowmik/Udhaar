/**
 * Udhaar Web Firestore Sync Transport (Spark Mode)
 * (webapp/src/sync/firestoreWebTransport.js)
 * 
 * Implements the transport contract for SyncEngine:
 * - push({ deviceId, changes })
 * - pull({ cursor, limit })
 * 
 * Operates serverless directly against the Cloud Firestore JS SDK:
 * - Enforces client-side daily write rate limit (default 500 writes/day).
 * - Enforces owner-only Firestore rules with email verification.
 * - Gracefully maps RESOURCE_EXHAUSTED errors without crashing.
 */

const { FirestoreSyncClient } = require("../api/firestoreSyncClient");
const { ClientWriteLimiter } = require("../api/clientWriteLimiter");

class FirestoreWebTransport {
  constructor({ firestore = null, auth = null, writeLimiter = null } = {}) {
    this.client = new FirestoreSyncClient(firestore, auth);
    this.limiter = writeLimiter || new ClientWriteLimiter();
  }

  isOnline() {
    return typeof navigator === "undefined" ? true : navigator.onLine;
  }

  async push({ deviceId = "web", changes = [] }) {
    const results = [];

    for (const change of changes) {
      // 1. Check client write limiter
      const check = this.limiter.canWrite(1);
      if (!check.allowed) {
        results.push({
          syncId: change.syncId,
          status: "REJECTED",
          reason: check.warningMessage,
          isQuotaResting: true
        });
        continue;
      }

      try {
        if (change.entity === "record") {
          const saved = await this.client.saveRecord(change.fields);
          this.limiter.recordWrite(1);
          results.push({
            syncId: change.syncId,
            status: "ACCEPTED",
            serverRev: saved.rev
          });
        } else if (change.entity === "payment" || change.entity === "event") {
          await this.client.addEvent(change.fields);
          this.limiter.recordWrite(1);
          results.push({
            syncId: change.syncId,
            status: "ACCEPTED",
            serverRev: 1
          });
        } else {
          // Unsupported or local-only
          results.push({
            syncId: change.syncId,
            status: "ACCEPTED",
            serverRev: 1
          });
        }
      } catch (err) {
        results.push({
          syncId: change.syncId,
          status: "CONFLICT",
          reason: err.message,
          isQuotaResting: err.isQuota || false
        });
      }
    }

    return { results };
  }

  async pull({ cursor = 0, limit = 100 }) {
    try {
      const records = await this.client.pullRecords(cursor);
      let maxCursor = cursor;

      const changes = records.slice(0, limit).map((rec) => {
        const updatedAt = rec.updatedAtMs || cursor;
        if (updatedAt > maxCursor) maxCursor = updatedAt;

        return {
          seq: updatedAt,
          entity: "record",
          syncId: rec.recordId || rec.id,
          op: rec.isDeleted || rec.deleted ? "DELETE" : "UPSERT",
          fields: rec,
          serverRev: rec.rev || 1,
          hlc: rec.hlc || ""
        };
      });

      return {
        changes,
        nextCursor: maxCursor,
        hasMore: records.length >= limit
      };
    } catch (err) {
      return {
        changes: [],
        nextCursor: cursor,
        hasMore: false,
        error: err.message
      };
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    FirestoreWebTransport
  };
}
