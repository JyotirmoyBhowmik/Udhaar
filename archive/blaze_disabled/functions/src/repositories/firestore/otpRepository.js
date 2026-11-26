/**
 * Firestore OTP Repository (functions/src/repositories/firestore/otpRepository.js)
 * Implements persistent challenges with TTL, atomic rate limits, and global send budget.
 */

const crypto = require("crypto");
const { getDb, clearCollection } = require("./firestoreClient");
const { logger } = require("../../observability/logger");
const { computeOtpHash, constantTimeEquals } = require("../../crypto/serverCrypto");
const { OTP_LIMITS } = require("../../config");

class FirestoreOtpRepository {
  constructor(db) {
    this._db = db;
    this._maxDailyBudgetOverride = null;
  }

  get db() {
    return this._db || getDb();
  }

  async saveChallenge(challenge) {
    const id = challenge.challengeId;
    const expiresAtDate = new Date(challenge.expiresAt || challenge.expiresAtMs || Date.now() + 600000);
    const docData = {
      ...challenge,
      ttlExpiresAt: expiresAtDate
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

  async verifyAndConsumeChallengeTransaction({ flowId, code, purpose, destination = null }) {
    return this.db.runTransaction(async (t) => {
      let query = this.db.collection("otpChallenges")
        .where("flowId", "==", flowId)
        .where("consumed", "==", false);

      if (purpose) {
        query = query.where("purpose", "==", purpose);
      }
      query = query.limit(1);

      const snap = typeof t.get === "function" ? await t.get(query) : await query.get();

      if (snap.empty) {
        const err = new Error("No active verification challenge found for this flow.");
        err.code = "ERR_CHALLENGE_NOT_FOUND";
        err.status = 404;
        throw err;
      }

      const docRef = snap.docs[0].ref;
      const targetChallenge = snap.docs[0].data();
      const now = Date.now();

      if (targetChallenge.lockedUntilMs && targetChallenge.lockedUntilMs > now) {
        const remainingMins = Math.ceil((targetChallenge.lockedUntilMs - now) / 60000);
        const err = new Error(`Too many incorrect attempts. Account verification is locked for ${remainingMins} more minutes.`);
        err.code = "ERR_ACCOUNT_LOCKED";
        err.status = 403;
        throw err;
      }

      if (now > targetChallenge.expiresAtMs) {
        const err = new Error("Verification code has expired. Please request a new code.");
        err.code = "ERR_OTP_EXPIRED";
        err.status = 400;
        throw err;
      }

      const candidateHash = computeOtpHash(code.trim(), flowId);
      const isValid = constantTimeEquals(candidateHash, targetChallenge.codeHash);

      if (!isValid) {
        const newAttempts = (targetChallenge.attempts || 0) + 1;
        const updates = { attempts: newAttempts };

        if (newAttempts >= targetChallenge.maxAttempts) {
          updates.lockedUntilMs = now + (OTP_LIMITS.LOCKOUT_DURATION_MINUTES || 15) * 60 * 1000;
          t.set(docRef, updates, { merge: true });
          const err = new Error("Too many incorrect attempts. Account verification locked for 15 minutes.");
          err.code = "ERR_MAX_ATTEMPTS_EXCEEDED";
          err.status = 403;
          throw err;
        }

        t.set(docRef, updates, { merge: true });
        const attemptsLeft = targetChallenge.maxAttempts - newAttempts;
        const err = new Error(`Incorrect verification code. ${attemptsLeft} attempts remaining.`);
        err.code = "ERR_INVALID_OTP";
        err.status = 400;
        err.attemptsRemaining = attemptsLeft;
        throw err;
      }

      const flowToken = crypto
        .createHmac("sha256", targetChallenge.codeHash)
        .update(`verified:${flowId}:${purpose}:${now}`)
        .digest("hex");

      t.set(docRef, {
        consumed: true,
        consumedAtMs: now,
        flowToken
      }, { merge: true });

      return {
        verified: true,
        flowToken
      };
    });
  }

  async getChallengeByFlowId(flowId, purpose) {
    let query = this.db.collection("otpChallenges").where("flowId", "==", flowId);
    if (purpose) {
      query = query.where("purpose", "==", purpose);
    }
    const snap = await query.get();
    if (snap.empty) return null;
    const unconsumed = snap.docs.map(d => d.data()).find(c => !c.consumed);
    return unconsumed || snap.docs[0].data();
  }

  async updateChallengeDeliveryStatus(providerMessageId, status, timestamp, errors = null) {
    const snap = await this.db.collection("otpChallenges")
      .where("providerMessageId", "==", providerMessageId)
      .limit(1)
      .get();
    if (snap.empty) return null;
    const docRef = snap.docs[0].ref;
    const updates = {
      deliveryStatus: (status || "").toUpperCase(),
      deliveryUpdatedAtMs: Date.now(),
      deliveryTimestamp: timestamp || null,
      deliveryErrors: errors || null
    };
    await docRef.set(updates, { merge: true });
    return { ...snap.docs[0].data(), ...updates };
  }

  async getRateLimit(key) {
    if (!key) return null;
    const snap = await this.db.collection("rateLimits").doc(key).get();
    return snap.exists ? snap.data() : null;
  }

  async setRateLimit(key, record) {
    await this.db.collection("rateLimits").doc(key).set(record, { merge: true });
    return this.getRateLimit(key);
  }

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

      if (now - record.lastSentAt < limits.RESEND_COOLDOWN_SECONDS * 1000) {
        const remainingSecs = Math.ceil((limits.RESEND_COOLDOWN_SECONDS * 1000 - (now - record.lastSentAt)) / 1000);
        const err = new Error(`Please wait ${remainingSecs} seconds before requesting another code.`);
        err.code = "ERR_RESEND_COOLDOWN";
        err.status = 429;
        throw err;
      }

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
          ttlExpiresAt: new Date(now + windowMs + 3600000)
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
      t.set(docRef, { count: record.count }, { merge: true });
      return record;
    });
  }

  setMaxDailyBudgetOverride(limit) {
    this._maxDailyBudgetOverride = limit;
  }

  async getDailyBudgetStatus(defaultMaxDailyBudget = 5000) {
    const maxDailyBudget = this._maxDailyBudgetOverride || defaultMaxDailyBudget;
    const today = new Date().toISOString().split("T")[0];
    const snap = await this.db.collection("system_counters").doc(`otp_daily_${today}`).get();
    const counter = snap.exists ? snap.data() : {
      dateStr: today,
      sentCount: 0,
      killSwitchTriggered: false,
      alerted50: false,
      alerted80: false,
      alerted100: false
    };
    return {
      ...counter,
      maxDailyBudget
    };
  }

  async checkAndIncrementDailyBudget(defaultMaxDailyBudget = 5000) {
    const maxDailyBudget = this._maxDailyBudgetOverride || defaultMaxDailyBudget;
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
          alerted50: false,
          alerted80: false,
          alerted100: false,
          ttlExpiresAt: new Date(Date.now() + 7 * 86400000)
        };
        const pct = (counter.sentCount / maxDailyBudget) * 100;
        if (pct >= 80) counter.alerted80 = true;
        else if (pct >= 50) counter.alerted50 = true;
        t.set(docRef, counter);
        return counter;
      }

      if (counter.killSwitchTriggered || counter.sentCount >= maxDailyBudget) {
        counter.killSwitchTriggered = true;
        if (!counter.alerted100) {
          counter.alerted100 = true;
          logger.warn(`[BUDGET_ALERT] 100% daily budget reached (${counter.sentCount}/${maxDailyBudget}). Kill switch activated.`, {
            threshold: "100%",
            sentCount: counter.sentCount,
            maxDailyBudget
          });
        }
        t.set(docRef, { killSwitchTriggered: true, alerted100: true }, { merge: true });
        const err = new Error("Emergency daily OTP dispatch budget reached. Send service paused.");
        err.code = "ERR_GLOBAL_BUDGET_EXCEEDED";
        err.status = 503;
        throw err;
      }

      counter.sentCount += 1;
      const pct = (counter.sentCount / maxDailyBudget) * 100;

      if (pct >= 80 && !counter.alerted80) {
        counter.alerted80 = true;
        logger.warn(`[BUDGET_ALERT] 80% daily budget reached (${counter.sentCount}/${maxDailyBudget}).`, {
          threshold: "80%",
          sentCount: counter.sentCount,
          maxDailyBudget
        });
      } else if (pct >= 50 && !counter.alerted50) {
        counter.alerted50 = true;
        logger.warn(`[BUDGET_ALERT] 50% daily budget reached (${counter.sentCount}/${maxDailyBudget}).`, {
          threshold: "50%",
          sentCount: counter.sentCount,
          maxDailyBudget
        });
      }

      t.set(docRef, counter, { merge: true });
      return counter;
    });
  }

  setMaxDailyBudgetOverride(limit) {
    this._maxDailyBudgetOverride = limit;
  }

  async setDailyBudgetMax(limit) {
    this.setMaxDailyBudgetOverride(limit);
    return limit;
  }

  async clear() {
    this._maxDailyBudgetOverride = null;
    await Promise.all([
      clearCollection(this.db, "otpChallenges"),
      clearCollection(this.db, "rateLimits"),
      clearCollection(this.db, "system_counters")
    ]);
  }
}

module.exports = { FirestoreOtpRepository };
