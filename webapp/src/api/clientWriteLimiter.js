/**
 * Udhaar Client-Side Write Limiter & Daily Cap Guard (Spark Mode)
 * (webapp/src/api/clientWriteLimiter.js)
 * 
 * Guards the Firebase Spark free plan write quota (20,000 writes/day across all users).
 * Enforces a default local limit of 500 writes/day per client.
 * Automatically resets at local midnight.
 */

const DEFAULT_MAX_DAILY_WRITES = 500;
const STORAGE_KEY_PREFIX = "udhaar_client_writes_";

function getTodayDateString() {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getNextMidnight() {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0, 0, 0, 0);
  return tomorrow;
}

class ClientWriteLimiter {
  constructor(storageOrOptions = null, maxDailyWrites = DEFAULT_MAX_DAILY_WRITES) {
    if (storageOrOptions && typeof storageOrOptions === "object" && !("getItem" in storageOrOptions)) {
      this.storage = storageOrOptions.storage || (typeof window !== "undefined" ? window.localStorage : null);
      this.maxDailyWrites = storageOrOptions.maxDailyWrites || DEFAULT_MAX_DAILY_WRITES;
    } else {
      this.storage = storageOrOptions || (typeof window !== "undefined" ? window.localStorage : null);
      this.maxDailyWrites = maxDailyWrites;
    }
  }

  setMaxDailyWrites(limit) {
    if (typeof limit === "number" && limit > 0) {
      this.maxDailyWrites = limit;
    }
  }

  getTodayKey() {
    return STORAGE_KEY_PREFIX + getTodayDateString();
  }

  getTodayWriteCount() {
    if (!this.storage) return 0;
    const val = this.storage.getItem(this.getTodayKey());
    return val ? parseInt(val, 10) || 0 : 0;
  }

  getUsage() {
    const count = this.getTodayWriteCount();
    return {
      count,
      remaining: Math.max(0, this.maxDailyWrites - count),
      max: this.maxDailyWrites
    };
  }

  canWrite(count = 1) {
    const current = this.getTodayWriteCount();
    const remaining = Math.max(0, this.maxDailyWrites - current);
    const allowed = current + count <= this.maxDailyWrites;

    let warningMessage = null;
    if (!allowed) {
      warningMessage = "Daily sync quota reached. Sync is resting until tomorrow to keep Udhaar free for everyone. Your records are safe on your device.";
    } else if (remaining <= 100) {
      warningMessage = `You are approaching the free daily sync limit (${remaining} writes remaining today).`;
    }

    return {
      allowed,
      current,
      remaining,
      max: this.maxDailyWrites,
      resetsAt: getNextMidnight(),
      warningMessage
    };
  }

  recordWrite(count = 1) {
    if (!this.storage) return;
    const current = this.getTodayWriteCount();
    const newCount = current + count;
    this.storage.setItem(this.getTodayKey(), String(newCount));
  }

  resetForTesting() {
    if (this.storage) {
      this.storage.removeItem(this.getTodayKey());
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DEFAULT_MAX_DAILY_WRITES,
    ClientWriteLimiter,
    getTodayDateString,
    getNextMidnight
  };
}
