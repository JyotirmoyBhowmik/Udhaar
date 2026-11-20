/**
 * In-Memory Repository Implementations for Unit & Contract Testing ONLY
 * (functions/test/repositories/inMemoryRepositories.js)
 *
 * NOTE: These classes live in functions/test/ and are strictly prohibited in staging/production.
 * Every class implements the exact same method contract as its Firestore counterpart in functions/src/repositories/firestore/.
 */

const crypto = require("crypto");
const { logger } = require("../../src/observability/logger");
const { computeOtpHash, constantTimeEquals } = require("../../src/crypto/serverCrypto");
const { OTP_LIMITS } = require("../../src/config");

class InMemoryAuthRepository {
  constructor() {
    this.users = new Map();
    this.phoneIndex = new Map();
    this.emailIndex = new Map();
    this.auditLogs = [];
  }

  async clear() {
    this.users.clear();
    this.phoneIndex.clear();
    this.emailIndex.clear();
    this.auditLogs.length = 0;
  }

  async getUser(uid) {
    return this.users.get(uid) || null;
  }

  async getUserByPhoneHash(phoneHash) {
    const entry = this.phoneIndex.get(phoneHash);
    if (!entry) return null;
    const uid = typeof entry === "string" ? entry : entry.uid;
    return this.users.get(uid) || null;
  }

  async getUserByEmailHash(emailHash) {
    const entry = this.emailIndex.get(emailHash);
    if (!entry) return null;
    const uid = typeof entry === "string" ? entry : entry.uid;
    return this.users.get(uid) || null;
  }

  async findUserByQueryHash(queryHash) {
    if (!queryHash) return null;
    const byUid = await this.getUser(queryHash);
    if (byUid) return byUid;
    const byPhone = await this.getUserByPhoneHash(queryHash);
    if (byPhone) return byPhone;
    const byEmail = await this.getUserByEmailHash(queryHash);
    if (byEmail) return byEmail;

    for (const u of this.users.values()) {
      if (u.uid === queryHash || u.phoneHash === queryHash || u.emailHash === queryHash) {
        return u;
      }
    }
    return null;
  }

  async listUsers() {
    return Array.from(this.users.values());
  }

  async countUsers() {
    return this.users.size;
  }

  async createUserWithIndexes(userDoc, phoneHash, emailHash) {
    const { uid } = userDoc;
    if (
      this.users.has(uid) ||
      (phoneHash && this.phoneIndex.has(phoneHash)) ||
      (emailHash && this.emailIndex.has(emailHash))
    ) {
      const err = new Error("An account with these credentials already exists.");
      err.code = "ERR_ACCOUNT_EXISTS";
      err.status = 409;
      throw err;
    }

    const now = Date.now();
    this.users.set(uid, { ...userDoc });
    if (phoneHash) this.phoneIndex.set(phoneHash, { uid, phoneHash, createdAtMs: now });
    if (emailHash) this.emailIndex.set(emailHash, { uid, emailHash, createdAtMs: now });
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
    const existing = this.users.get(uid) || { uid };
    const updated = { ...existing, ...updates, uid };
    this.users.set(uid, updated);
    if (updated.phoneHash) {
      this.phoneIndex.set(updated.phoneHash, { uid, phoneHash: updated.phoneHash, updatedAtMs: Date.now() });
    }
    if (updated.emailHash) {
      this.emailIndex.set(updated.emailHash, { uid, emailHash: updated.emailHash, updatedAtMs: Date.now() });
    }
    return updated;
  }

  async updateIdentifierTransaction(uid, { oldPhoneHash, newPhoneHash, oldEmailHash, newEmailHash, userUpdates }) {
    if (newPhoneHash && newPhoneHash !== oldPhoneHash) {
      const existing = this.phoneIndex.get(newPhoneHash);
      const existingUid = existing ? (typeof existing === "string" ? existing : existing.uid) : null;
      if (existingUid && existingUid !== uid) {
        const err = new Error("Phone number is already linked to another account.");
        err.code = "ERR_PHONE_ALREADY_IN_USE";
        err.status = 409;
        throw err;
      }
      if (oldPhoneHash) this.phoneIndex.delete(oldPhoneHash);
      this.phoneIndex.set(newPhoneHash, { uid, phoneHash: newPhoneHash, updatedAtMs: Date.now() });
    }

    if (newEmailHash && newEmailHash !== oldEmailHash) {
      const existing = this.emailIndex.get(newEmailHash);
      const existingUid = existing ? (typeof existing === "string" ? existing : existing.uid) : null;
      if (existingUid && existingUid !== uid) {
        const err = new Error("Email address is already linked to another account.");
        err.code = "ERR_EMAIL_ALREADY_IN_USE";
        err.status = 409;
        throw err;
      }
      if (oldEmailHash) this.emailIndex.delete(oldEmailHash);
      this.emailIndex.set(newEmailHash, { uid, emailHash: newEmailHash, updatedAtMs: Date.now() });
    }

    return this.updateUser(uid, userUpdates);
  }

  async appendAuditLog(event) {
    const record = { id: `aud_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`, ...event, timestampMs: event.timestampMs || Date.now() };
    this.auditLogs.push(record);
    return record;
  }

  async listAuditLogs(uid = null) {
    if (!uid) return [...this.auditLogs];
    return this.auditLogs.filter(a => a.uid === uid);
  }
}

class InMemoryOtpRepository {
  constructor() {
    this.challenges = new Map();
    this.rateLimits = new Map();
    this.budgetAlerts = [];
    this._maxDailyBudgetOverride = null;
    this.dailyBudget = {
      sentCount: 0,
      maxDailyBudget: null,
      dateStr: new Date().toISOString().split("T")[0],
      killSwitchTriggered: false,
      alerted50: false,
      alerted80: false,
      alerted100: false
    };
  }

  async clear() {
    this.challenges.clear();
    this.rateLimits.clear();
    this.budgetAlerts.length = 0;
    this._maxDailyBudgetOverride = null;
    this.dailyBudget.sentCount = 0;
    this.dailyBudget.maxDailyBudget = null;
    this.dailyBudget.dateStr = new Date().toISOString().split("T")[0];
    this.dailyBudget.killSwitchTriggered = false;
    this.dailyBudget.alerted50 = false;
    this.dailyBudget.alerted80 = false;
    this.dailyBudget.alerted100 = false;
  }

  async saveChallenge(challenge) {
    this.challenges.set(challenge.challengeId, { ...challenge });
    return challenge;
  }

  async getChallenge(challengeId) {
    return this.challenges.get(challengeId) || null;
  }

  async updateChallenge(challengeId, updates) {
    const existing = this.challenges.get(challengeId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    this.challenges.set(challengeId, updated);
    return updated;
  }

  async consumeChallenge(challengeId, updates) {
    return this.updateChallenge(challengeId, updates);
  }

  async verifyAndConsumeChallengeTransaction({ flowId, code, purpose, destination = null }) {
    let targetChallenge = null;
    for (const chal of this.challenges.values()) {
      if (chal.flowId === flowId && !chal.consumed) {
        if (!purpose || chal.purpose === purpose) {
          targetChallenge = chal;
          break;
        }
      }
    }

    if (!targetChallenge) {
      const err = new Error("No active verification challenge found for this flow.");
      err.code = "ERR_CHALLENGE_NOT_FOUND";
      err.status = 404;
      throw err;
    }

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
        await this.updateChallenge(targetChallenge.challengeId, updates);
        const err = new Error("Too many incorrect attempts. Account verification locked for 15 minutes.");
        err.code = "ERR_MAX_ATTEMPTS_EXCEEDED";
        err.status = 403;
        throw err;
      }

      await this.updateChallenge(targetChallenge.challengeId, updates);
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

    await this.consumeChallenge(targetChallenge.challengeId, {
      consumed: true,
      consumedAtMs: now,
      flowToken
    });

    return {
      verified: true,
      flowToken
    };
  }

  async getChallengeByFlowId(flowId, purpose) {
    for (const chal of this.challenges.values()) {
      if (chal.flowId === flowId && (!purpose || chal.purpose === purpose) && !chal.consumed) {
        return chal;
      }
    }
    for (const chal of this.challenges.values()) {
      if (chal.flowId === flowId && (!purpose || chal.purpose === purpose)) {
        return chal;
      }
    }
    return null;
  }

  async updateChallengeDeliveryStatus(providerMessageId, status, timestamp, errors = null) {
    for (const [id, chal] of this.challenges.entries()) {
      if (chal.providerMessageId === providerMessageId) {
        const updated = {
          ...chal,
          deliveryStatus: (status || "").toUpperCase(),
          deliveryUpdatedAtMs: Date.now(),
          deliveryTimestamp: timestamp || null,
          deliveryErrors: errors || null
        };
        this.challenges.set(id, updated);
        return updated;
      }
    }
    return null;
  }

  async getRateLimit(key) {
    return this.rateLimits.get(key) || null;
  }

  async setRateLimit(key, record) {
    const existing = this.rateLimits.get(key) || {};
    const merged = { ...existing, ...record };
    this.rateLimits.set(key, merged);
    return merged;
  }

  async checkAndIncrementIpLimit(ipHashed, maxLimit, windowMs = 3600000) {
    const ipKey = `ip_${ipHashed}`;
    const now = Date.now();
    let ipRecord = this.rateLimits.get(ipKey);
    if (!ipRecord || now > ipRecord.resetAt) {
      ipRecord = { count: 0, resetAt: now + windowMs };
    }
    if (ipRecord.count >= maxLimit) {
      const err = new Error("Too many verification requests from this network. Please try again later.");
      err.code = "ERR_RATE_LIMIT_IP";
      err.status = 429;
      err.resetAt = ipRecord.resetAt;
      throw err;
    }
    ipRecord.count += 1;
    this.rateLimits.set(ipKey, ipRecord);
    return ipRecord;
  }

  async checkAndIncrementDestinationLimit(destinationHash, limits) {
    const destKey = `dest_${destinationHash}`;
    const now = Date.now();
    let destRecord = this.rateLimits.get(destKey) || {
      hourlyCount: 0,
      hourlyResetAt: now + 3600000,
      dailyCount: 0,
      dailyResetAt: now + 86400000,
      lastSentAt: 0
    };

    if (now - destRecord.lastSentAt < limits.RESEND_COOLDOWN_SECONDS * 1000) {
      const remainingSecs = Math.ceil((limits.RESEND_COOLDOWN_SECONDS * 1000 - (now - destRecord.lastSentAt)) / 1000);
      const err = new Error(`Please wait ${remainingSecs} seconds before requesting another code.`);
      err.code = "ERR_RESEND_COOLDOWN";
      err.status = 429;
      throw err;
    }

    if (now > destRecord.hourlyResetAt) {
      destRecord.hourlyCount = 0;
      destRecord.hourlyResetAt = now + 3600000;
    }
    if (destRecord.hourlyCount >= limits.MAX_HOURLY_PER_DESTINATION) {
      const err = new Error("Hourly verification limit reached for this destination. Try again in an hour.");
      err.code = "ERR_RATE_LIMIT_DESTINATION_HOURLY";
      err.status = 429;
      throw err;
    }

    if (now > destRecord.dailyResetAt) {
      destRecord.dailyCount = 0;
      destRecord.dailyResetAt = now + 86400000;
    }
    if (destRecord.dailyCount >= limits.MAX_DAILY_PER_DESTINATION) {
      const err = new Error("Daily verification limit reached for this destination. Try again tomorrow.");
      err.code = "ERR_RATE_LIMIT_DESTINATION_DAILY";
      err.status = 429;
      throw err;
    }

    destRecord.lastSentAt = now;
    destRecord.hourlyCount += 1;
    destRecord.dailyCount += 1;
    this.rateLimits.set(destKey, destRecord);
    return destRecord;
  }

  async checkAndIncrementRateLimit(key, maxLimit, windowMs) {
    const now = Date.now();
    let record = this.rateLimits.get(key);
    if (!record || now > record.resetAt) {
      record = { count: 1, resetAt: now + windowMs };
      this.rateLimits.set(key, record);
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
    this.rateLimits.set(key, record);
    return record;
  }

  setMaxDailyBudgetOverride(limit) {
    this._maxDailyBudgetOverride = limit;
  }

  async setDailyBudgetMax(limit) {
    this.setMaxDailyBudgetOverride(limit);
    if (this.dailyBudget) {
      this.dailyBudget.maxDailyBudget = limit;
    }
    return limit;
  }

  async getDailyBudgetStatus(defaultMaxDailyBudget = 5000) {
    const maxDailyBudget = this._maxDailyBudgetOverride || defaultMaxDailyBudget;
    return {
      ...this.dailyBudget,
      maxDailyBudget
    };
  }

  async checkAndIncrementDailyBudget(defaultMaxDailyBudget = 5000) {
    const maxDailyBudget = this._maxDailyBudgetOverride || this.dailyBudget.maxDailyBudget || defaultMaxDailyBudget;
    const today = new Date().toISOString().split("T")[0];
    if (this.dailyBudget.dateStr !== today) {
      this.dailyBudget.dateStr = today;
      this.dailyBudget.sentCount = 0;
      this.dailyBudget.killSwitchTriggered = false;
      this.dailyBudget.alerted50 = false;
      this.dailyBudget.alerted80 = false;
      this.dailyBudget.alerted100 = false;
      this.budgetAlerts.length = 0;
    }

    if (this.dailyBudget.killSwitchTriggered || this.dailyBudget.sentCount >= maxDailyBudget) {
      this.dailyBudget.killSwitchTriggered = true;
      if (!this.dailyBudget.alerted100) {
        this.dailyBudget.alerted100 = true;
        this.budgetAlerts.push({ threshold: "100%", sentCount: this.dailyBudget.sentCount, maxDailyBudget });
        logger.warn(`[BUDGET_ALERT] 100% daily budget reached (${this.dailyBudget.sentCount}/${maxDailyBudget}). Kill switch activated.`, {
          threshold: "100%",
          sentCount: this.dailyBudget.sentCount,
          maxDailyBudget
        });
      }
      const err = new Error("Emergency daily OTP dispatch budget reached. Send service paused.");
      err.code = "ERR_GLOBAL_BUDGET_EXCEEDED";
      err.status = 503;
      throw err;
    }

    this.dailyBudget.sentCount += 1;
    const pct = (this.dailyBudget.sentCount / maxDailyBudget) * 100;

    if (pct >= 50 && !this.dailyBudget.alerted50) {
      this.dailyBudget.alerted50 = true;
      this.budgetAlerts.push({ threshold: "50%", sentCount: this.dailyBudget.sentCount, maxDailyBudget });
      logger.warn(`[BUDGET_ALERT] 50% daily budget reached (${this.dailyBudget.sentCount}/${maxDailyBudget}).`, {
        threshold: "50%",
        sentCount: this.dailyBudget.sentCount,
        maxDailyBudget
      });
    }
    if (pct >= 80 && !this.dailyBudget.alerted80) {
      this.dailyBudget.alerted80 = true;
      this.budgetAlerts.push({ threshold: "80%", sentCount: this.dailyBudget.sentCount, maxDailyBudget });
      logger.warn(`[BUDGET_ALERT] 80% daily budget reached (${this.dailyBudget.sentCount}/${maxDailyBudget}).`, {
        threshold: "80%",
        sentCount: this.dailyBudget.sentCount,
        maxDailyBudget
      });
    }
    if (pct >= 100 && !this.dailyBudget.alerted100) {
      this.dailyBudget.alerted100 = true;
      this.dailyBudget.killSwitchTriggered = true;
      this.budgetAlerts.push({ threshold: "100%", sentCount: this.dailyBudget.sentCount, maxDailyBudget });
      logger.warn(`[BUDGET_ALERT] 100% daily budget reached (${this.dailyBudget.sentCount}/${maxDailyBudget}).`, {
        threshold: "100%",
        sentCount: this.dailyBudget.sentCount,
        maxDailyBudget
      });
    }

    return { ...this.dailyBudget, maxDailyBudget };
  }
}

class InMemorySessionRepository {
  constructor() {
    this.sessions = new Map();
    this.devices = new Map();
  }

  async clear() {
    this.sessions.clear();
    this.devices.clear();
  }

  async createSession(sessionDoc) {
    this.sessions.set(sessionDoc.sessionId, { ...sessionDoc });
    return sessionDoc;
  }

  async getSession(sessionId, uidHint) {
    return this.sessions.get(sessionId) || null;
  }

  async updateSession(sessionId, updates, uidHint) {
    const existing = this.sessions.get(sessionId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    this.sessions.set(sessionId, updated);
    return updated;
  }

  async revokeSession(sessionId, uidHint) {
    return this.updateSession(sessionId, { revoked: true, revokedAtMs: Date.now() }, uidHint);
  }

  async listUserSessions(uid) {
    const list = [];
    for (const s of this.sessions.values()) {
      if (s.uid === uid) list.push(s);
    }
    return list;
  }

  async createDevice(deviceDoc) {
    const { uid, deviceId } = deviceDoc;
    if (!this.devices.has(uid)) {
      this.devices.set(uid, new Map());
    }
    this.devices.get(uid).set(deviceId, { ...deviceDoc });
    return deviceDoc;
  }

  async getDevice(uid, deviceId) {
    if (!uid || !deviceId) return null;
    const userDevices = this.devices.get(uid);
    if (!userDevices) return null;
    return userDevices.get(deviceId) || null;
  }

  async listUserDevices(uid) {
    const userDevices = this.devices.get(uid);
    if (!userDevices) return [];
    return Array.from(userDevices.values());
  }

  async deleteDevice(uid, deviceId) {
    if (!uid || !deviceId) return false;
    const userDevices = this.devices.get(uid);
    if (!userDevices || !userDevices.has(deviceId)) return false;
    userDevices.delete(deviceId);
    return true;
  }

  async deleteAllUserDevices(uid) {
    if (!uid) return 0;
    const userDevices = this.devices.get(uid);
    if (!userDevices) return 0;
    const count = userDevices.size;
    userDevices.clear();
    return count;
  }

  async countActiveDevices() {
    let total = 0;
    for (const devMap of this.devices.values()) {
      total += devMap.size;
    }
    return total;
  }
}

class InMemoryLedgerRepository {
  constructor() {
    this.records = new Map();
    this.people = new Map();
    this.events = new Map();
    this.plans = new Map();
  }

  async clear() {
    this.records.clear();
    this.people.clear();
    this.events.clear();
    this.plans.clear();
  }

  _getUserMap(store, uid) {
    if (!store.has(uid)) store.set(uid, new Map());
    return store.get(uid);
  }

  async createRecord(uid, recordDoc) {
    const id = recordDoc.id || recordDoc.recordId;
    const docWithOwner = { ...recordDoc, uid, recordId: id, id };
    this._getUserMap(this.records, uid).set(id, docWithOwner);
    return docWithOwner;
  }

  async getRecord(uid, recordId) {
    return this._getUserMap(this.records, uid).get(recordId) || null;
  }

  async updateRecord(uid, recordId, updates, expectedRev = null) {
    const userRecords = this._getUserMap(this.records, uid);
    const current = userRecords.get(recordId);
    if (!current) {
      const err = new Error(`Record ${recordId} not found.`);
      err.code = "ERR_RECORD_NOT_FOUND";
      err.status = 404;
      throw err;
    }

    if (expectedRev !== null && expectedRev !== undefined && current.serverRev !== expectedRev) {
      const err = new Error("Record revision conflict (ETag mismatch).");
      err.code = "ERR_CONFLICT";
      err.status = 409;
      err.serverRecord = current;
      throw err;
    }

    const updated = { ...current, ...updates, updatedAtMs: updates.updatedAtMs || Date.now() };
    userRecords.set(recordId, updated);
    return updated;
  }

  async deleteRecord(uid, recordId) {
    return this.updateRecord(uid, recordId, { deleted: true, deletedAtMs: Date.now() });
  }

  async listRecords(uid, filters = {}) {
    let list = Array.from(this._getUserMap(this.records, uid).values());
    if (!filters.includeDeleted) {
      list = list.filter(r => !r.deleted && !r.deletedAtMs);
    }
    if (filters.status) {
      list = list.filter(r => r.status === filters.status);
    }
    return list.sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
  }

  async countRecords(uid) {
    const list = await this.listRecords(uid);
    return list.length;
  }

  async countAllRecords() {
    let count = 0;
    for (const userRecords of this.records.values()) {
      count += userRecords.size;
    }
    return count;
  }

  async findLinkedRecord(uid, remoteRecordId) {
    if (!uid || !remoteRecordId) return null;
    const records = await this.listRecords(uid, { includeDeleted: true });
    return records.find(r => r.remoteRecordId === remoteRecordId) || null;
  }

  async anonymizeCounterpartyRecords(deletedUid) {
    if (!deletedUid) return 0;
    let count = 0;
    for (const [otherUid, recordsMap] of this.records.entries()) {
      if (otherUid === deletedUid) continue;
      for (const [recId, rec] of recordsMap.entries()) {
        if (rec.linkedCreatorUid === deletedUid || rec.counterpartyUid === deletedUid || rec.partyUid === deletedUid) {
          recordsMap.set(recId, {
            ...rec,
            partyName: "Deleted User",
            partyPhone: "+910000000000",
            counterpartyAnonymizedAtMs: Date.now()
          });
          count++;
        }
      }
    }
    return count;
  }

  async createPerson(uid, personDoc) {
    const id = personDoc.id || personDoc.personId;
    this._getUserMap(this.people, uid).set(id, { ...personDoc });
    return personDoc;
  }

  async getPerson(uid, personId) {
    return this._getUserMap(this.people, uid).get(personId) || null;
  }

  async updatePerson(uid, personId, updates) {
    const userPeople = this._getUserMap(this.people, uid);
    const existing = userPeople.get(personId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    userPeople.set(personId, updated);
    return updated;
  }

  async deletePerson(uid, personId) {
    return this.updatePerson(uid, personId, { deleted: true, deletedAtMs: Date.now() });
  }

  async listPeople(uid) {
    return Array.from(this._getUserMap(this.people, uid).values()).filter(p => !p.deleted);
  }

  async addEvent(uid, eventDoc) {
    const id = eventDoc.id || eventDoc.eventId;
    const docWithId = { ...eventDoc, id, eventId: id, uid };
    this._getUserMap(this.events, uid).set(id, docWithId);
    return docWithId;
  }

  async listEvents(uid, recordId = null) {
    let list = Array.from(this._getUserMap(this.events, uid).values());
    if (recordId) {
      list = list.filter(e => e.recordId === recordId);
    }
    return list.sort((a, b) => (a.timestampMs || 0) - (b.timestampMs || 0));
  }

  async countAllEvents() {
    let count = 0;
    for (const userEvents of this.events.values()) {
      count += userEvents.size;
    }
    return count;
  }

  async createPlan(uid, planDoc) {
    const id = planDoc.id || planDoc.planId;
    this._getUserMap(this.plans, uid).set(id, { ...planDoc });
    return planDoc;
  }

  async getPlan(uid, planId) {
    return this._getUserMap(this.plans, uid).get(planId) || null;
  }

  async updatePlan(uid, planId, updates) {
    const userPlans = this._getUserMap(this.plans, uid);
    const existing = userPlans.get(planId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    userPlans.set(planId, updated);
    return updated;
  }

  async listPlans(uid) {
    return Array.from(this._getUserMap(this.plans, uid).values());
  }
}

class InMemorySyncRepository {
  constructor() {
    this.counters = new Map();
    this.changeLogs = new Map();
    this.syncSignals = new Map();
    this.syncedRecords = new Map();
    this.syncedPeople = new Map();
    this.syncedPayments = new Map();
  }

  async clear() {
    this.counters.clear();
    this.changeLogs.clear();
    this.syncSignals.clear();
    this.syncedRecords.clear();
    this.syncedPeople.clear();
    this.syncedPayments.clear();
  }

  async runTransaction(updateFn) {
    const cloneScalarMap = (m) => new Map(m);
    const cloneArrayMap = (m) => {
      const next = new Map();
      for (const [k, v] of m.entries()) {
        next.set(k, Array.isArray(v) ? v.map(e => ({ ...e })) : v);
      }
      return next;
    };
    const cloneNestedMap = (m) => {
      const next = new Map();
      for (const [uid, userMap] of m.entries()) {
        const uNext = new Map();
        if (userMap instanceof Map) {
          for (const [k, v] of userMap.entries()) {
            uNext.set(k, v ? { ...v } : v);
          }
        }
        next.set(uid, uNext);
      }
      return next;
    };

    const snapCounters = cloneScalarMap(this.counters);
    const snapChangeLogs = cloneArrayMap(this.changeLogs);
    const snapSyncSignals = cloneScalarMap(this.syncSignals);
    const snapSyncedRecords = cloneNestedMap(this.syncedRecords);
    const snapSyncedPeople = cloneNestedMap(this.syncedPeople);
    const snapSyncedPayments = cloneNestedMap(this.syncedPayments);

    try {
      return await updateFn(null);
    } catch (err) {
      this.counters = snapCounters;
      this.changeLogs = snapChangeLogs;
      this.syncSignals = snapSyncSignals;
      this.syncedRecords = snapSyncedRecords;
      this.syncedPeople = snapSyncedPeople;
      this.syncedPayments = snapSyncedPayments;
      throw err;
    }
  }

  async allocateSequenceNumber(uid, tx = null) {
    const current = this.counters.get(uid) || 0;
    const next = current + 1;
    this.counters.set(uid, next);
    return next;
  }

  async appendChangeLog(uid, seq, changeEntry, tx = null) {
    if (!this.changeLogs.has(uid)) this.changeLogs.set(uid, []);
    const entry = { ...changeEntry, seq, timestampMs: changeEntry.timestampMs || Date.now() };
    this.changeLogs.get(uid).push(entry);
    return entry;
  }

  async pullChangeLog(uid, sinceSeq = 0, limit = 100) {
    const logs = this.changeLogs.get(uid) || [];
    return logs.filter(e => e.seq > sinceSeq).slice(0, limit);
  }

  async touchSyncSignal(uid, seq, tx = null) {
    const sig = { uid, seq, updatedAtMs: Date.now() };
    this.syncSignals.set(uid, sig);
    return sig;
  }

  async getSyncSignal(uid) {
    return this.syncSignals.get(uid) || null;
  }

  _getUserMap(store, uid) {
    if (!store.has(uid)) store.set(uid, new Map());
    return store.get(uid);
  }

  async getSyncedRecord(uid, syncId, tx = null) {
    if (!uid || !syncId) return null;
    const rec = this._getUserMap(this.syncedRecords, uid).get(syncId);
    return rec ? { ...rec } : null;
  }

  async saveSyncedRecord(uid, syncId, record, tx = null) {
    const copy = { ...record };
    this._getUserMap(this.syncedRecords, uid).set(syncId, copy);
    return copy;
  }

  async listSyncedRecords(uid) {
    if (!uid) return [];
    return Array.from(this._getUserMap(this.syncedRecords, uid).values()).map(r => ({ ...r }));
  }

  async getSyncedPerson(uid, syncId, tx = null) {
    if (!uid || !syncId) return null;
    const p = this._getUserMap(this.syncedPeople, uid).get(syncId);
    return p ? { ...p } : null;
  }

  async saveSyncedPerson(uid, syncId, person, tx = null) {
    const copy = { ...person };
    this._getUserMap(this.syncedPeople, uid).set(syncId, copy);
    return copy;
  }

  async getSyncedPayment(uid, paymentId, tx = null) {
    if (!uid || !paymentId) return null;
    const pay = this._getUserMap(this.syncedPayments, uid).get(paymentId);
    return pay ? { ...pay } : null;
  }

  async saveSyncedPayment(uid, paymentId, payment, tx = null) {
    const copy = { ...payment };
    this._getUserMap(this.syncedPayments, uid).set(paymentId, copy);
    return copy;
  }
}

class InMemoryLinkRepository {
  constructor() {
    this.links = new Map();
    this.ipRateLimits = new Map();
  }

  async clear() {
    this.links.clear();
    this.ipRateLimits.clear();
  }

  async saveWebLink(recordId, linkDoc) {
    this.links.set(recordId, { ...linkDoc });
    return linkDoc;
  }

  async getWebLink(recordId) {
    return this.links.get(recordId) || null;
  }

  async updateWebLink(recordId, updates) {
    const existing = this.links.get(recordId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    this.links.set(recordId, updated);
    return updated;
  }

  async deleteWebLink(recordId) {
    this.links.delete(recordId);
    return true;
  }

  async listWebLinks() {
    return Array.from(this.links.values());
  }

  async countActiveWebLinks() {
    const now = Date.now();
    let count = 0;
    for (const l of this.links.values()) {
      if (!l.isRevoked && (!l.expiresAtMs || l.expiresAtMs > now)) {
        count++;
      }
    }
    return count;
  }

  async checkAndIncrementIpRateLimit(clientIp, windowMs = 3600000, maxRequests = 20) {
    const ip = String(clientIp || "127.0.0.1");
    const now = Date.now();
    let entry = this.ipRateLimits.get(ip);

    if (!entry || now - entry.windowStartMs >= windowMs) {
      entry = { count: 1, windowStartMs: now };
      this.ipRateLimits.set(ip, entry);
      return entry;
    }

    entry.count += 1;
    if (entry.count > maxRequests) {
      const err = new Error("Too many guest portal requests from this IP. Please try again in an hour.");
      err.code = "ERR_RATE_LIMIT_EXCEEDED";
      err.status = 429;
      err.retryAfterSeconds = Math.ceil((entry.windowStartMs + windowMs - now) / 1000);
      throw err;
    }

    this.ipRateLimits.set(ip, entry);
    return entry;
  }
}

class InMemoryChatRepository {
  constructor() {
    this.threads = new Map();
    this.messages = new Map();
  }

  async clear() {
    this.threads.clear();
    this.messages.clear();
  }

  async saveThread(threadId, threadDoc) {
    const existing = this.threads.get(threadId) || {};
    const merged = { ...existing, ...threadDoc };
    this.threads.set(threadId, merged);
    return merged;
  }

  async getThread(threadId) {
    return this.threads.get(threadId) || null;
  }

  async listUserThreads(uid) {
    if (!uid) return [];
    const results = [];
    for (const thread of this.threads.values()) {
      if (thread.creatorUid === uid || thread.partyUid === uid || (Array.isArray(thread.participants) && thread.participants.includes(uid))) {
        results.push(thread);
      }
    }
    return results.sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
  }

  async findThreadByRecordAndParties(recordId, uid, partyUid = null) {
    if (!recordId || !uid) return null;
    for (const thread of this.threads.values()) {
      if (thread.recordId === recordId && (thread.creatorUid === uid || (partyUid && thread.partyUid === partyUid))) {
        return thread;
      }
    }
    return null;
  }

  async saveMessage(threadId, messageId, messageDoc) {
    if (!this.messages.has(threadId)) this.messages.set(threadId, new Map());
    this.messages.get(threadId).set(messageId, { ...messageDoc });
    return messageDoc;
  }

  async getMessage(threadId, messageId) {
    if (!threadId || !messageId) return null;
    const threadMsgs = this.messages.get(threadId);
    if (!threadMsgs) return null;
    return threadMsgs.get(messageId) || null;
  }

  async updateMessage(threadId, messageId, updates) {
    if (!threadId || !messageId) return null;
    const threadMsgs = this.messages.get(threadId);
    if (!threadMsgs || !threadMsgs.has(messageId)) return null;
    const updated = { ...threadMsgs.get(messageId), ...updates };
    threadMsgs.set(messageId, updated);
    return updated;
  }

  async listMessages(threadId, limit = 50, beforeMs = null) {
    if (!this.messages.has(threadId)) return [];
    let msgs = Array.from(this.messages.get(threadId).values());
    if (beforeMs && Number.isFinite(Number(beforeMs))) {
      msgs = msgs.filter(m => m.createdAtMs < Number(beforeMs));
    }
    msgs.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
    const maxLimit = Math.min(Math.max(1, Number(limit) || 50), 200);
    return msgs.slice(-maxLimit);
  }
}

class InMemoryInboxRepository {
  constructor() {
    this.userInboxes = new Map();
  }

  async clear() {
    this.userInboxes.clear();
  }

  _getUserMap(uid) {
    if (!this.userInboxes.has(uid)) this.userInboxes.set(uid, new Map());
    return this.userInboxes.get(uid);
  }

  async addItem(uid, itemDoc) {
    const id = itemDoc.id || itemDoc.inboxId;
    const docData = { ...itemDoc, id, inboxId: id, uid };
    this._getUserMap(uid).set(id, docData);
    return docData;
  }

  async getItem(uid, itemId) {
    if (!uid || !itemId) return null;
    return this._getUserMap(uid).get(itemId) || null;
  }

  async updateItem(uid, itemId, updates) {
    const map = this._getUserMap(uid);
    const existing = map.get(itemId);
    if (!existing) return null;
    const updated = { ...existing, ...updates };
    map.set(itemId, updated);
    return updated;
  }

  async listItems(uid, unreadOnly = false) {
    let items = Array.from(this._getUserMap(uid).values());
    if (unreadOnly) {
      items = items.filter(i => !i.isRead);
    }
    return items.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
  }
}

class InMemoryReminderRepository {
  constructor() {
    this.policies = new Map();
    this.states = new Map();
    this.events = new Map();
  }

  async clear() {
    this.policies.clear();
    this.states.clear();
    this.events.clear();
  }

  async getPolicy(uid) {
    return this.policies.get(uid) || null;
  }

  async savePolicy(uid, policyDoc) {
    const existing = this.policies.get(uid) || {};
    const merged = { ...existing, ...policyDoc };
    this.policies.set(uid, merged);
    return merged;
  }

  async getReminderState(recordId) {
    if (!recordId) return null;
    return this.states.get(recordId) || null;
  }

  async saveReminderState(recordId, stateDoc) {
    const existing = this.states.get(recordId) || {};
    const merged = { ...existing, ...stateDoc };
    this.states.set(recordId, merged);
    return merged;
  }

  async saveReminderEvent(eventDoc) {
    const id = eventDoc.id || eventDoc.reminderId || `rem_${Date.now()}`;
    const docData = { ...eventDoc, id };
    this.events.set(id, docData);
    return docData;
  }

  async listReminderEvents(recordId) {
    return Array.from(this.events.values()).filter(e => e.recordId === recordId);
  }
}

class InMemoryConfigRepository {
  constructor() {
    this.killSwitches = {};
    this.circuitBreakers = {};
  }

  async clear() {
    this.killSwitches = {};
    this.circuitBreakers = {};
  }

  async getKillSwitches() {
    return JSON.parse(JSON.stringify(this.killSwitches));
  }

  async setKillSwitches(switches) {
    this.killSwitches = JSON.parse(JSON.stringify(switches));
    return this.getKillSwitches();
  }

  async getCircuitBreakers() {
    return JSON.parse(JSON.stringify(this.circuitBreakers));
  }

  async setCircuitBreakers(breakers) {
    this.circuitBreakers = JSON.parse(JSON.stringify(breakers));
    return this.getCircuitBreakers();
  }
}

class InMemoryAdminRepository {
  constructor() {
    this.roles = new Map();
    this.auditLogs = [];
    this.backupSnapshots = new Map();
  }

  async clear() {
    this.roles.clear();
    this.auditLogs.length = 0;
    this.backupSnapshots.clear();
  }

  async getAdminRole(uid) {
    return this.roles.get(uid) || null;
  }

  async setAdminRole(uid, role) {
    this.roles.set(uid, role);
    return role;
  }

  async removeAdminRole(uid) {
    if (!uid) return false;
    return this.roles.delete(uid);
  }

  async appendAuditLog(entry) {
    const id = entry.id || entry.logId || `adm_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const record = Object.freeze({
      ...entry,
      id,
      logId: id,
      timestampMs: entry.timestampMs || Date.now()
    });
    this.auditLogs.push(record);
    return record;
  }

  async listAuditLogs(options = {}) {
    const limit = typeof options === "number" ? options : (Number(options.limit) || 100);
    const action = typeof options === "object" && options !== null ? options.action : null;

    let logs = [...this.auditLogs];
    if (action) {
      logs = logs.filter(l => l.action === action);
    }
    logs.sort((a, b) => (b.timestampMs || 0) - (a.timestampMs || 0));
    const maxLimit = Math.min(Math.max(1, limit), 500);
    return logs.slice(0, maxLimit);
  }

  async saveBackupSnapshot(snapshot) {
    const id = snapshot.snapshotId || snapshot.id;
    const frozen = Object.freeze({ ...snapshot, id, snapshotId: id });
    this.backupSnapshots.set(id, frozen);
    return frozen;
  }

  async getBackupSnapshot(snapshotId) {
    if (!snapshotId) return null;
    return this.backupSnapshots.get(snapshotId) || null;
  }

  async listBackupSnapshots(limit = 50) {
    const list = Array.from(this.backupSnapshots.values()).sort(
      (a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0)
    );
    const maxLimit = Math.min(Math.max(1, Number(limit) || 50), 200);
    return list.slice(0, maxLimit);
  }
}

class InMemoryPrivacyRepository {
  constructor() {
    this.requests = new Map();
  }

  async clear() {
    this.requests.clear();
  }

  async savePrivacyRequest(reqDoc) {
    const id = reqDoc.id || reqDoc.requestId;
    const docData = { ...reqDoc, id, requestId: id };
    this.requests.set(id, docData);
    return docData;
  }

  async getPrivacyRequest(id) {
    return this.requests.get(id) || null;
  }

  async listPrivacyRequests(uid = null) {
    let list = Array.from(this.requests.values());
    if (uid) {
      list = list.filter(r => r.uid === uid);
    }
    return list.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
  }

  async deleteUserData(uid) {
    return true;
  }
}

class InMemoryCryptoRepository {
  constructor() {
    this.deks = new Map();
  }

  async clear() {
    this.deks.clear();
  }

  async getUserDekDoc(uid) {
    return this.deks.get(uid) || null;
  }

  async saveUserDekDoc(uid, dekDoc) {
    this.deks.set(uid, { ...dekDoc });
    return dekDoc;
  }

  async deleteUserDekDoc(uid) {
    this.deks.delete(uid);
    return true;
  }

  async getWrappedDek(uid) {
    const doc = await this.getUserDekDoc(uid);
    return doc ? doc.wrappedDek : null;
  }

  async saveWrappedDek(uid, wrappedDek, metadata = {}) {
    const docData = {
      uid,
      wrappedDek,
      ...metadata,
      updatedAtMs: Date.now()
    };
    await this.saveUserDekDoc(uid, docData);
    return docData;
  }

  async deleteWrappedDek(uid) {
    return this.deleteUserDekDoc(uid);
  }

  async deleteUserDek(uid) {
    return this.deleteUserDekDoc(uid);
  }
}

module.exports = {
  InMemoryAuthRepository,
  InMemoryOtpRepository,
  InMemorySessionRepository,
  InMemoryLedgerRepository,
  InMemorySyncRepository,
  InMemoryLinkRepository,
  InMemoryChatRepository,
  InMemoryInboxRepository,
  InMemoryReminderRepository,
  InMemoryConfigRepository,
  InMemoryAdminRepository,
  InMemoryPrivacyRepository,
  InMemoryCryptoRepository
};
