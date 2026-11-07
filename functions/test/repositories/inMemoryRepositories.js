/**
 * In-Memory Repositories for Unit Testing (functions/src/repositories/inMemory/index.js)
 * Cleanly isolated classes with per-instance stores. Strictly forbidden in production/staging.
 */

const crypto = require("crypto");
const { computeOtpHash, constantTimeEquals } = require("../../src/crypto/serverCrypto");
const { OTP_LIMITS } = require("../../src/config");

class InMemoryAuthRepository {
  constructor() {
    this.users = new Map();
    this.phoneIndex = new Map();
    this.emailIndex = new Map();
    this.auditLogs = [];
  }

  async getUser(uid) {
    return this.users.get(uid) || null;
  }

  async getUserByPhoneHash(phoneHash) {
    const entry = this.phoneIndex.get(phoneHash);
    return entry ? (this.users.get(entry.uid) || null) : null;
  }

  async getUserByEmailHash(emailHash) {
    const entry = this.emailIndex.get(emailHash);
    return entry ? (this.users.get(entry.uid) || null) : null;
  }

  async createUserTransaction(userRecord, phoneIndexRecord, emailIndexRecord) {
    const phoneHash = phoneIndexRecord.phoneHash;
    const emailHash = emailIndexRecord.emailHash;
    const uid = userRecord.uid;

    if (this.phoneIndex.has(phoneHash) || this.emailIndex.has(emailHash)) {
      const err = new Error("An account with these credentials already exists.");
      err.code = "ERR_ACCOUNT_EXISTS";
      err.status = 409;
      throw err;
    }

    this.phoneIndex.set(phoneHash, { uid, createdAt: Date.now() });
    this.emailIndex.set(emailHash, { uid, createdAt: Date.now() });
    this.users.set(uid, { ...userRecord });
    return userRecord;
  }

  async updateUser(uid, updates) {
    const existing = this.users.get(uid) || { uid };
    const updated = { ...existing, ...updates };
    this.users.set(uid, updated);
    return updated;
  }

  async saveAuditEntry(entry) {
    this.auditLogs.push(entry);
    return entry;
  }

  clear() {
    this.users.clear();
    this.phoneIndex.clear();
    this.emailIndex.clear();
    this.auditLogs.length = 0;
  }
}

class InMemoryOtpRepository {
  constructor() {
    this.challenges = new Map();
    this.rateLimits = new Map();
    this.budgetAlerts = [];
    this.dailyBudget = {
      dateStr: new Date().toISOString().split("T")[0],
      sentCount: 0,
      killSwitchTriggered: false,
      alerted50: false,
      alerted80: false,
      alerted100: false
    };
  }

  async saveChallenge(challenge) {
    this.challenges.set(challenge.challengeId, challenge);
    return challenge;
  }

  async getChallenge(challengeId) {
    return this.challenges.get(challengeId) || null;
  }

  async getChallengeByFlowId(flowId, purpose) {
    for (const chal of this.challenges.values()) {
      if (chal.flowId === flowId && chal.purpose === purpose && !chal.consumed) {
        return chal;
      }
    }
    return null;
  }

  async updateChallenge(challengeId, updates) {
    const existing = this.challenges.get(challengeId) || {};
    Object.assign(existing, updates);
    this.challenges.set(challengeId, existing);
    return existing;
  }

  async updateChallengeDeliveryStatus(providerMessageId, status, timestamp, errors = null) {
    for (const chal of this.challenges.values()) {
      if (chal.providerMessageId === providerMessageId) {
        chal.deliveryStatus = (status || "").toUpperCase();
        chal.deliveryUpdatedAtMs = Date.now();
        chal.deliveryTimestamp = timestamp || null;
        chal.deliveryErrors = errors || null;
        return chal;
      }
    }
    return null;
  }

  async consumeChallenge(challengeId, updates) {
    const existing = this.challenges.get(challengeId);
    if (!existing) return null;
    Object.assign(existing, updates);
    return existing;
  }

  async verifyAndConsumeChallengeTransaction({ flowId, code, purpose, destination = null }) {
    if (!this._flowLocks) this._flowLocks = new Map();
    let prevLock = this._flowLocks.get(flowId) || Promise.resolve();
    let release;
    const currentLock = new Promise(r => { release = r; });
    this._flowLocks.set(flowId, currentLock);
    await prevLock;

    try {
      let targetChallenge = null;
      for (const chal of this.challenges.values()) {
        if (chal.flowId === flowId && chal.purpose === purpose && !chal.consumed) {
          targetChallenge = chal;
          break;
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
        targetChallenge.attempts = newAttempts;

        if (newAttempts >= targetChallenge.maxAttempts) {
          targetChallenge.lockedUntilMs = now + (OTP_LIMITS.LOCKOUT_DURATION_MINUTES || 15) * 60 * 1000;
          const err = new Error("Too many incorrect attempts. Account verification locked for 15 minutes.");
          err.code = "ERR_MAX_ATTEMPTS_EXCEEDED";
          err.status = 403;
          throw err;
        }

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

      targetChallenge.consumed = true;
      targetChallenge.consumedAtMs = now;
      targetChallenge.flowToken = flowToken;

      return {
        verified: true,
        flowToken
      };
    } finally {
      release();
    }
  }

  async checkAndIncrementIpLimit(ipHashed, maxLimit, windowMs = 3600000) {
    const now = Date.now();
    const ipKey = `ip_${ipHashed}`;
    let ipRecord = this.rateLimits.get(ipKey) || { count: 0, resetAt: now + windowMs };
    if (now > ipRecord.resetAt) {
      ipRecord.count = 0;
      ipRecord.resetAt = now + windowMs;
    }
    if (ipRecord.count >= maxLimit) {
      const err = new Error("Too many verification requests from this network. Please try again later.");
      err.code = "ERR_RATE_LIMIT_IP";
      err.status = 429;
      throw err;
    }
    ipRecord.count++;
    this.rateLimits.set(ipKey, ipRecord);
    return ipRecord;
  }

  async checkAndIncrementDestinationLimit(destinationHash, limits) {
    const now = Date.now();
    const destKey = `dest_${destinationHash}`;
    const destRecord = this.rateLimits.get(destKey) || {
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
    destRecord.hourlyCount++;
    destRecord.dailyCount++;
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

  async checkAndIncrementDailyBudget(maxDailyBudget) {
    const today = new Date().toISOString().split("T")[0];
    const budgetCap = (this.dailyBudget && this.dailyBudget.maxDailyBudget !== undefined)
      ? this.dailyBudget.maxDailyBudget
      : maxDailyBudget;

    if (this.dailyBudget.dateStr !== today) {
      this.dailyBudget.dateStr = today;
      this.dailyBudget.sentCount = 0;
      this.dailyBudget.killSwitchTriggered = false;
      this.dailyBudget.alerted50 = false;
      this.dailyBudget.alerted80 = false;
      this.dailyBudget.alerted100 = false;
    }

    if (this.dailyBudget.killSwitchTriggered || this.dailyBudget.sentCount >= budgetCap) {
      this.dailyBudget.killSwitchTriggered = true;
      if (!this.dailyBudget.alerted100) {
        this.dailyBudget.alerted100 = true;
        this.budgetAlerts.push({ threshold: "100%", sentCount: this.dailyBudget.sentCount, maxDailyBudget: budgetCap });
      }
      const err = new Error("Emergency daily OTP dispatch budget reached. Send service paused.");
      err.code = "ERR_GLOBAL_BUDGET_EXCEEDED";
      err.status = 503;
      throw err;
    }

    this.dailyBudget.sentCount += 1;
    const pct = (this.dailyBudget.sentCount / budgetCap) * 100;

    if (pct >= 100 && !this.dailyBudget.alerted100) {
      this.dailyBudget.alerted100 = true;
      this.budgetAlerts.push({ threshold: "100%", sentCount: this.dailyBudget.sentCount, maxDailyBudget: budgetCap });
    } else if (pct >= 80 && !this.dailyBudget.alerted80) {
      this.dailyBudget.alerted80 = true;
      this.budgetAlerts.push({ threshold: "80%", sentCount: this.dailyBudget.sentCount, maxDailyBudget: budgetCap });
    } else if (pct >= 50 && !this.dailyBudget.alerted50) {
      this.dailyBudget.alerted50 = true;
      this.budgetAlerts.push({ threshold: "50%", sentCount: this.dailyBudget.sentCount, maxDailyBudget: budgetCap });
    }

    return { ...this.dailyBudget };
  }

  clear() {
    this.challenges.clear();
    this.rateLimits.clear();
    this.budgetAlerts.length = 0;
    this.dailyBudget.sentCount = 0;
    this.dailyBudget.killSwitchTriggered = false;
    this.dailyBudget.alerted50 = false;
    this.dailyBudget.alerted80 = false;
    this.dailyBudget.alerted100 = false;
  }
}

class InMemorySessionRepository {
  constructor() {
    this.sessions = new Map();
    this.devices = new Map();
  }

  getUserDevicesMap(uid) {
    if (!this.devices.has(uid)) {
      this.devices.set(uid, new Map());
    }
    return this.devices.get(uid);
  }

  async createSession(sessionDoc) {
    this.sessions.set(sessionDoc.sessionId, { ...sessionDoc });
    return sessionDoc;
  }

  async getSession(sessionId) {
    return this.sessions.get(sessionId) || null;
  }

  async updateSession(sessionId, updates) {
    const sess = this.sessions.get(sessionId);
    if (!sess) return null;
    const updated = { ...sess, ...updates };
    this.sessions.set(sessionId, updated);
    return updated;
  }

  async revokeSession(sessionId) {
    return this.updateSession(sessionId, { revoked: true, revokedAtMs: Date.now() });
  }

  async createDevice(deviceDoc) {
    this.devices.set(`${deviceDoc.uid}_${deviceDoc.deviceId}`, { ...deviceDoc });
    return deviceDoc;
  }

  async listUserDevices(uid) {
    const results = [];
    for (const [k, v] of this.devices.entries()) {
      if (v.uid === uid) results.push(v);
    }
    return results;
  }

  async listUserSessions(uid) {
    const results = [];
    for (const [k, v] of this.sessions.entries()) {
      if (v.uid === uid) results.push(v);
    }
    return results;
  }

  clear() {
    this.sessions.clear();
    this.devices.clear();
  }
}

class InMemoryLedgerRepository {
  constructor() {
    this.records = new Map();
    this.people = new Map();
    this.events = new Map();
    this.plans = new Map();
  }

  _getUserRecordsMap(uid) {
    if (!this.records.has(uid)) {
      this.records.set(uid, new Map());
    }
    return this.records.get(uid);
  }

  async createRecord(uid, record) {
    const userMap = this._getUserRecordsMap(uid);
    const recId = record.recordId || record.id;
    const item = { ...record, id: recId, recordId: recId };
    userMap.set(recId, item);
    return item;
  }

  async getRecord(uid, recordId) {
    const userMap = this.records.get(uid);
    return userMap ? (userMap.get(recordId) || null) : null;
  }

  async updateRecord(uid, recordId, updates) {
    const userMap = this.records.get(uid);
    if (!userMap || !userMap.has(recordId)) return null;
    const existing = userMap.get(recordId);
    const updated = { ...existing, ...updates };
    userMap.set(recordId, updated);
    return updated;
  }

  async listRecords(uid, options = {}) {
    const userMap = this.records.get(uid);
    if (!userMap) return [];
    let results = Array.from(userMap.values());
    if (options.status) results = results.filter(v => v.status === options.status);
    if (options.counterpartyRole) results = results.filter(v => v.counterpartyRole === options.counterpartyRole);
    results.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
    if (options.limit) {
      results = results.slice(0, options.limit);
    }
    return results;
  }

  _getUserPeopleMap(uid) {
    if (!this.people.has(uid)) {
      this.people.set(uid, new Map());
    }
    return this.people.get(uid);
  }

  async createPerson(uid, person) {
    const userMap = this._getUserPeopleMap(uid);
    const id = person.personId || person.id;
    const item = { ...person, id, personId: id };
    userMap.set(id, item);
    return item;
  }

  async getPerson(uid, personId) {
    const userMap = this.people.get(uid);
    return userMap ? (userMap.get(personId) || null) : null;
  }

  async listPeople(uid) {
    const userMap = this.people.get(uid);
    return userMap ? Array.from(userMap.values()) : [];
  }

  async updatePerson(uid, personId, updates) {
    const userMap = this.people.get(uid);
    if (!userMap || !userMap.has(personId)) return null;
    const existing = userMap.get(personId);
    const updated = { ...existing, ...updates };
    userMap.set(personId, updated);
    return updated;
  }

  async addPayment(uid, payment) {
    const userRecords = this._getUserRecordsMap(uid);
    const record = userRecords.get(payment.recordId);
    if (!record) {
      const err = new Error("Record not found");
      err.code = "ERR_RECORD_NOT_FOUND";
      err.status = 404;
      throw err;
    }

    const currentRemaining = record.remainingAmountPaise || record.principalAmountPaise;
    const newRemaining = Math.max(0, currentRemaining - payment.amountPaise);
    record.remainingAmountPaise = newRemaining;
    record.isSettled = newRemaining === 0;
    record.updatedAtMs = Date.now();

    const userEvents = this._getUserEventsMap(uid);
    userEvents.set(payment.id, {
      ...payment,
      eventType: "PAYMENT",
      timestampMs: payment.paidAtMs || Date.now()
    });

    return payment;
  }

  _getUserPlansMap(uid) {
    if (!this.plans.has(uid)) {
      this.plans.set(uid, new Map());
    }
    return this.plans.get(uid);
  }

  async createPlan(uid, plan) {
    const userMap = this._getUserPlansMap(uid);
    const id = plan.planId || plan.id;
    const item = { ...plan, id, planId: id };
    userMap.set(id, item);
    return item;
  }

  async getPlan(uid, planId) {
    const userMap = this.plans.get(uid);
    return userMap ? (userMap.get(planId) || null) : null;
  }

  async listPlans(uid) {
    const userMap = this.plans.get(uid);
    return userMap ? Array.from(userMap.values()) : [];
  }

  async updatePlan(uid, planId, updates) {
    const userMap = this.plans.get(uid);
    if (!userMap || !userMap.has(planId)) return null;
    const existing = userMap.get(planId);
    const updated = { ...existing, ...updates };
    userMap.set(planId, updated);
    return updated;
  }

  _getUserEventsMap(uid) {
    if (!this.events.has(uid)) {
      this.events.set(uid, new Map());
    }
    return this.events.get(uid);
  }

  async addEvent(uid, event) {
    const userMap = this._getUserEventsMap(uid);
    const id = event.eventId || event.id;
    const item = { ...event, id, eventId: id };
    userMap.set(id, item);
    return item;
  }

  async listEvents(uid, recordId) {
    const userMap = this.events.get(uid);
    if (!userMap) return [];
    let list = Array.from(userMap.values());
    if (recordId) list = list.filter(e => e.recordId === recordId);
    list.sort((a, b) => (a.timestampMs || 0) - (b.timestampMs || 0));
    return list;
  }

  clear() {
    this.records.clear();
    this.people.clear();
    this.events.clear();
    this.plans.clear();
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

  getUserSyncedRecords(uid) {
    if (!this.syncedRecords.has(uid)) this.syncedRecords.set(uid, new Map());
    return this.syncedRecords.get(uid);
  }
  getUserSyncedPeople(uid) {
    if (!this.syncedPeople.has(uid)) this.syncedPeople.set(uid, new Map());
    return this.syncedPeople.get(uid);
  }
  getUserSyncedPayments(uid) {
    if (!this.syncedPayments.has(uid)) this.syncedPayments.set(uid, new Map());
    return this.syncedPayments.get(uid);
  }

  async allocateSequenceNumber(uid) {
    const current = this.counters.get(uid) || 0;
    const next = current + 1;
    this.counters.set(uid, next);
    return next;
  }

  async appendChangeLog(uid, seq, changeEntry) {
    const list = this.changeLogs.get(uid) || [];
    const entry = { ...changeEntry, seq, timestampMs: changeEntry.timestampMs || Date.now() };
    list.push(entry);
    this.changeLogs.set(uid, list);
    return entry;
  }

  async pullChangeLog(uid, sinceSeq = 0, limit = 100) {
    const list = this.changeLogs.get(uid) || [];
    let results = list.filter(e => e.seq > sinceSeq);
    results.sort((a, b) => a.seq - b.seq);
    if (limit) results = results.slice(0, limit);
    return results;
  }

  async touchSyncSignal(uid, seq) {
    const signal = { seq, updatedAtMs: Date.now() };
    this.syncSignals.set(uid, signal);
    return signal;
  }

  async getSyncSignal(uid) {
    return this.syncSignals.get(uid) || { seq: 0, updatedAtMs: 0 };
  }

  clear() {
    this.counters.clear();
    this.changeLogs.clear();
    this.syncSignals.clear();
    this.syncedRecords.clear();
    this.syncedPeople.clear();
    this.syncedPayments.clear();
  }
}

class InMemoryLinkRepository {
  constructor() {
    this.links = new Map();
    this.ipRateLimits = new Map();
  }
  async saveWebLink(recordId, linkDoc) {
    this.links.set(recordId, linkDoc);
    return linkDoc;
  }
  async getWebLink(recordId) {
    return this.links.get(recordId) || null;
  }
  async updateWebLink(recordId, updates) {
    const existing = this.links.get(recordId) || {};
    Object.assign(existing, updates);
    this.links.set(recordId, existing);
    return existing;
  }
  async deleteWebLink(recordId) {
    this.links.delete(recordId);
    return true;
  }
  clear() {
    this.links.clear();
    this.ipRateLimits.clear();
  }
}

class InMemoryChatRepository {
  constructor() {
    this.threads = new Map();
    this.messages = new Map();
  }
  getThreadMessagesMap(threadId) {
    if (!this.messages.has(threadId)) {
      this.messages.set(threadId, new Map());
    }
    return this.messages.get(threadId);
  }
  async saveThread(threadId, threadDoc) {
    this.threads.set(threadId, { ...threadDoc });
    return threadDoc;
  }
  async getThread(threadId) {
    return this.threads.get(threadId) || null;
  }
  async saveMessage(threadId, messageId, messageDoc) {
    const list = this.messages.get(threadId) || [];
    list.push(messageDoc);
    this.messages.set(threadId, list);
    return messageDoc;
  }
  async listMessages(threadId, limit = 50) {
    const list = this.messages.get(threadId) || [];
    list.sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
    return limit ? list.slice(-limit) : list;
  }
  clear() {
    this.threads.clear();
    this.messages.clear();
  }
}

class InMemoryInboxRepository {
  constructor() {
    this.items = new Map();
    this.userInboxes = new Map();
  }
  getUserInboxMap(uid) {
    if (!this.userInboxes.has(uid)) {
      this.userInboxes.set(uid, new Map());
    }
    return this.userInboxes.get(uid);
  }
  clear() {
    this.items.clear();
    this.userInboxes.clear();
  }
  async addItem(uid, itemDoc) {
    const list = this.items.get(uid) || [];
    list.push(itemDoc);
    this.items.set(uid, list);
    return itemDoc;
  }
  async getItem(uid, itemId) {
    const list = this.items.get(uid) || [];
    return list.find(i => i.id === itemId) || null;
  }
  async updateItem(uid, itemId, updates) {
    const list = this.items.get(uid) || [];
    const idx = list.findIndex(i => i.id === itemId);
    if (idx !== -1) {
      list[idx] = { ...list[idx], ...updates };
      return list[idx];
    }
    return null;
  }
  async listItems(uid, limit = 50) {
    const list = this.items.get(uid) || [];
    list.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
    return limit ? list.slice(0, limit) : list;
  }
  clear() { this.items.clear(); }
}

class InMemoryReminderRepository {
  constructor() {
    this.policies = new Map();
    this.states = new Map();
    this.events = [];
  }
  async saveReminderEvent(eventDoc) {
    this.events.push(eventDoc);
    return eventDoc;
  }
  async listReminderEvents(recordId) {
    return this.events.filter(e => e.recordId === recordId);
  }
  clear() {
    this.policies.clear();
    this.states.clear();
    this.events.length = 0;
  }
}

class InMemoryPrivacyRepository {
  constructor() {
    this.requests = new Map();
  }
  async savePrivacyRequest(reqDoc) {
    this.requests.set(reqDoc.id, reqDoc);
    return reqDoc;
  }
  async getPrivacyRequest(id) {
    return this.requests.get(id) || null;
  }
  async deleteUserData(uid) {
    return true;
  }
  clear() { this.requests.clear(); }
}

class InMemoryAdminRepository {
  constructor() {
    this.roles = new Map();
  }
  async getAdminRole(uid) {
    return this.roles.get(uid) || null;
  }
  async setAdminRole(uid, role) {
    this.roles.set(uid, role);
    return role;
  }
  clear() { this.roles.clear(); }
}

class InMemoryConfigRepository {
  constructor() {
    this.killSwitches = {};
    this.circuitBreakers = {};
  }
  async getKillSwitches() {
    return { ...this.killSwitches };
  }
  async setKillSwitches(switches) {
    this.killSwitches = { ...this.killSwitches, ...switches };
    return { ...this.killSwitches };
  }
  async getCircuitBreakers() {
    return { ...this.circuitBreakers };
  }
  async setCircuitBreakers(breakers) {
    this.circuitBreakers = { ...this.circuitBreakers, ...breakers };
    return { ...this.circuitBreakers };
  }
  clear() {
    this.killSwitches = {};
    this.circuitBreakers = {};
  }
}

class InMemoryCryptoRepository {
  constructor() {
    this.deks = new Map();
  }
  async getUserDekDoc(uid) {
    const doc = this.deks.get(uid);
    return doc ? { ...doc } : null;
  }
  async saveUserDekDoc(uid, dekDoc) {
    this.deks.set(uid, { ...dekDoc });
    return { ...dekDoc };
  }
  async deleteUserDekDoc(uid) {
    return this.deks.delete(uid);
  }
  clear() {
    this.deks.clear();
  }
}

function createInMemoryRepositories() {
  return {
    auth: new InMemoryAuthRepository(),
    otp: new InMemoryOtpRepository(),
    session: new InMemorySessionRepository(),
    ledger: new InMemoryLedgerRepository(),
    sync: new InMemorySyncRepository(),
    link: new InMemoryLinkRepository(),
    chat: new InMemoryChatRepository(),
    inbox: new InMemoryInboxRepository(),
    reminder: new InMemoryReminderRepository(),
    privacy: new InMemoryPrivacyRepository(),
    admin: new InMemoryAdminRepository(),
    config: new InMemoryConfigRepository(),
    crypto: new InMemoryCryptoRepository()
  };
}

module.exports = {
  createInMemoryRepositories,
  InMemoryAuthRepository,
  InMemoryOtpRepository,
  InMemorySessionRepository,
  InMemoryLedgerRepository,
  InMemorySyncRepository,
  InMemoryLinkRepository,
  InMemoryChatRepository,
  InMemoryInboxRepository,
  InMemoryReminderRepository,
  InMemoryPrivacyRepository,
  InMemoryAdminRepository,
  InMemoryConfigRepository,
  InMemoryCryptoRepository
};
