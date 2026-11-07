/**
 * Firestore OTP Repository (functions/src/repositories/firestore/otpRepository.js)
 * Implements persistent challenges with TTL, atomic rate limits, and global send budget.
 */

const { getDb } = require("./firestoreClient");

class FirestoreOtpRepository {
  constructor(db) {
    this._db = db;
  }

  get db() {
    return this._db || getDb();
  }

  async saveChallenge(challenge) {
    const id = challenge.challengeId;
    const expiresAtDate = new Date(challenge.expiresAt);
    const docData = {
      ...challenge,
      ttlExpiresAt: expiresAtDate // Firestore TTL field
    };
    await this.db.collection("otpChallenges").doc(id).set(docData);
    return challenge;
  }

  async getChallenge(challengeId) {
    if (!challengeId) return null;
    const snap = await this.db.collection("otpChallenges").doc(challengeId).get();
    return snap.exists ? snap.data() : null;
  }

  async updateChallenge(challengeId, updates) {
    await this.db.collection("otpChallenges").doc(challengeId).set(updates, { merge: true });
    return this.getChallenge(challengeId);
  }

  async consumeChallenge(challengeId, updates) {
    const docRef = this.db.collection("otpChallenges").doc(challengeId);
    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      if (!snap.exists) {
        return null;
      }
      t.set(docRef, updates, { merge: true });
      return { ...snap.data(), ...updates };
    });
  }

  /**
   * Atomic Firestore transaction for multi-instance distributed IP rate limiting with TTL
   */
  async checkAndIncrementIpLimit(ipHashed, maxLimit, windowMs = 3600000) {
    const key = `ip_${ipHashed}`;
    const docRef = this.db.collection("rateLimits").doc(key);
    const now = Date.now();

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      let record = snap.exists ? snap.data() : null;

      if (!record || now > record.resetAt) {
        record = {
          count: 1,
          resetAt: now + windowMs,
          ttlExpiresAt: new Date(now + windowMs + 3600000)
        };
        t.set(docRef, record);
        return record;
      }

      if (record.count >= maxLimit) {
        const err = new Error("Too many verification requests from this network. Please try again later.");
        err.code = "ERR_RATE_LIMIT_IP";
        err.status = 429;
        err.resetAt = record.resetAt;
        throw err;
      }

      record.count += 1;
      t.set(docRef, { count: record.count }, { merge: true });
      return record;
    });
  }

  /**
   * Atomic Firestore transaction for multi-instance destination limits (cooldown, hourly, daily)
   */
  async checkAndIncrementDestinationLimit(destinationHash, limits) {
    const key = `dest_${destinationHash}`;
    const docRef = this.db.collection("rateLimits").doc(key);
    const now = Date.now();

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      let record = snap.exists ? snap.data() : {
        hourlyCount: 0,
        hourlyResetAt: now + 3600000,
        dailyCount: 0,
        dailyResetAt: now + 86400000,
        lastSentAt: 0
      };

      // Cooldown check (30 seconds)
      if (now - record.lastSentAt < limits.RESEND_COOLDOWN_SECONDS * 1000) {
        const remainingSecs = Math.ceil((limits.RESEND_COOLDOWN_SECONDS * 1000 - (now - record.lastSentAt)) / 1000);
        const err = new Error(`Please wait ${remainingSecs} seconds before requesting another code.`);
        err.code = "ERR_RESEND_COOLDOWN";
        err.status = 429;
        throw err;
      }

      // Hourly limit
      if (now > record.hourlyResetAt) {
        record.hourlyCount = 0;
        record.hourlyResetAt = now + 3600000;
      }
      if (record.hourlyCount >= limits.MAX_HOURLY_PER_DESTINATION) {
        const err = new Error("Hourly verification limit reached for this destination. Try again in an hour.");
        err.code = "ERR_RATE_LIMIT_DESTINATION_HOURLY";
        err.status = 429;
        throw err;
      }

      // Daily limit
      if (now > record.dailyResetAt) {
        record.dailyCount = 0;
        record.dailyResetAt = now + 86400000;
      }
      if (record.dailyCount >= limits.MAX_DAILY_PER_DESTINATION) {
        const err = new Error("Daily verification limit reached for this destination. Try again tomorrow.");
        err.code = "ERR_RATE_LIMIT_DESTINATION_DAILY";
        err.status = 429;
        throw err;
      }

      record.lastSentAt = now;
      record.hourlyCount += 1;
      record.dailyCount += 1;
      record.ttlExpiresAt = new Date(record.dailyResetAt + 3600000);

      t.set(docRef, record, { merge: true });
      return record;
    });
  }

  /**
   * Generic key increment for backward compatibility
   */
  async checkAndIncrementRateLimit(key, maxLimit, windowMs) {
    const docRef = this.db.collection("rateLimits").doc(key);
    const now = Date.now();

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      let record = snap.exists ? snap.data() : null;

      if (!record || now > record.resetAt) {
        record = {
          count: 1,
          resetAt: now + windowMs,
          ttlExpiresAt: new Date(now + windowMs + 3600000) // TTL cleans up after 1hr
        };
        t.set(docRef, record);
        return record;
      }

      if (record.count >= maxLimit) {
        const err = new Error("Rate limit exceeded. Please wait before retrying.");
        err.code = "ERR_RATE_LIMIT";
        err.status = 429;
        err.resetAt = record.resetAt;
        throw err;
      }

      record.count += 1;
      t.set(docRef, record, { merge: true });
      return record;
    });
  }

  /**
   * Atomic Firestore transaction for global daily send budget across all Cloud Function instances
   */
  async checkAndIncrementDailyBudget(maxDailyBudget) {
    const today = new Date().toISOString().split("T")[0];
    const docRef = this.db.collection("system_counters").doc(`otp_daily_${today}`);

    return this.db.runTransaction(async (t) => {
      const snap = await t.get(docRef);
      let counter = snap.exists ? snap.data() : null;

      if (!counter) {
        counter = {
          dateStr: today,
          sentCount: 1,
          killSwitchTriggered: false,
          ttlExpiresAt: new Date(Date.now() + 7 * 86400000) // Keep for 7 days
        };
        t.set(docRef, counter);
        return counter;
      }

      if (counter.killSwitchTriggered || counter.sentCount >= maxDailyBudget) {
        counter.killSwitchTriggered = true;
        t.set(docRef, { killSwitchTriggered: true }, { merge: true });
        const err = new Error("Emergency daily OTP dispatch budget reached. Send service paused.");
        err.code = "ERR_GLOBAL_BUDGET_EXCEEDED";
        err.status = 503;
        throw err;
      }

      counter.sentCount += 1;
      t.set(docRef, { sentCount: counter.sentCount }, { merge: true });
      return counter;
    });
  }
}

module.exports = { FirestoreOtpRepository };
