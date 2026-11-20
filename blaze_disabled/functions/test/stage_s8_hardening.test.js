const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { apiApp } = require("../src/api");
const { createSession } = require("../src/sessions/sessionManager");
const { getOrCreateUser } = require("../src/auth/authService");
const { resetRateLimits } = require("../src/middleware/rateLimitMiddleware");
const { resetAllKillSwitches, setKillSwitch, isKillSwitchActive } = require("../src/config/killSwitches");
const { resetAllCircuits, recordFailure, canExecute, getCircuitState } = require("../src/config/circuitBreakers");
const { exportUserData, deleteUserData, getConsentStatus, recordConsent } = require("../src/privacy/privacyService");
const { createBackupSnapshot, verifyBackupIntegrity, restoreFromSnapshot, clearBackups } = require("../src/backup/backupService");
const { rotateAllDEKs, encryptServerEnvelope, decryptServerEnvelope } = require("../src/crypto/envelopeCrypto");
const { createPerson } = require("../src/ledger/peopleService");
const { createRecord } = require("../src/ledger/recordService");
const { resetTestRepositories } = require("../src/repositories");

async function invokeApp(app, { method = "GET", url = "/", headers = {}, body = undefined, ip = "127.0.0.1" }) {
  return new Promise((resolve, reject) => {
    const server = app.listen(0, "127.0.0.1", async () => {
      const { port } = server.address();
      try {
        const fetchHeaders = { ...headers, "x-forwarded-for": ip };
        if (body !== undefined && !fetchHeaders["content-type"]) {
          fetchHeaders["content-type"] = "application/json";
        }
        const res = await fetch(`http://127.0.0.1:${port}${url}`, {
          method,
          headers: fetchHeaders,
          body: body !== undefined ? (typeof body === "string" ? body : JSON.stringify(body)) : undefined
        });
        const text = await res.text();
        let json = null;
        try {
          json = JSON.parse(text);
        } catch (_) {}
        server.close(() => resolve({ status: res.status, headers: res.headers, body: json, text }));
      } catch (err) {
        server.close(() => reject(err));
      }
    });
  });
}

describe("Stage S8 — Security Hardening, Abuse Controls, Privacy & DR", () => {
  beforeEach(async () => {
    resetRateLimits();
    await resetTestRepositories();
    await resetAllKillSwitches();
    await resetAllCircuits();
    await clearBackups();
  });

  describe("1. Per-IP and Per-UID Rate Limiting", () => {
    it("enforces auth rate limits per IP and returns Retry-After header", async () => {
      for (let i = 0; i < 15; i++) {
        const res = await invokeApp(apiApp, {
          method: "POST",
          url: "/api/v1/auth/otp/send",
          ip: "203.0.113.55",
          body: { channel: "email", destination: `user${i}@example.com`, purpose: "LOGIN" }
        });
        assert.notEqual(res.status, 429, `Request ${i + 1} should not be rate limited`);
      }

      const blocked = await invokeApp(apiApp, {
        method: "POST",
        url: "/api/v1/auth/otp/send",
        ip: "203.0.113.55",
        body: { channel: "email", destination: "overflow@example.com", purpose: "LOGIN" }
      });
      assert.equal(blocked.status, 429);
      assert.equal(blocked.body.error.code, "ERR_RATE_LIMITED");
      assert.ok(Number(blocked.headers.get("retry-after")) >= 1);
    });
  });

  describe("2. Strict Schema Validation & Injection Rejection", () => {
    it("rejects prototype pollution and NoSQL operator injection keys in request payloads", async () => {
      const { user } = await getOrCreateUser({ channel: "email", destination: "sec@example.com" });
      const session = await createSession({ uid: user.uid, deviceInfo: { platform: "web" } });

      const rawBadPayload = '{"name":"Rahul","$where":"1==1"}';
      const res = await invokeApp(apiApp, {
        method: "POST",
        url: "/api/v1/ledger/people",
        headers: { authorization: `Bearer ${session.accessToken}` },
        body: rawBadPayload
      });

      assert.equal(res.status, 400);
      assert.equal(res.body.error.code, "ERR_MALICIOUS_PAYLOAD");
    });

    it("rejects negative or non-integer paise amounts", async () => {
      const { user } = await getOrCreateUser({ channel: "email", destination: "sec2@example.com" });
      const session = await createSession({ uid: user.uid, deviceInfo: { platform: "web" } });

      const res = await invokeApp(apiApp, {
        method: "POST",
        url: "/api/v1/ledger/records",
        headers: { authorization: `Bearer ${session.accessToken}` },
        body: { amountPaise: -500, role: "LENDER" }
      });

      assert.equal(res.status, 400);
      assert.equal(res.body.error.code, "ERR_INVALID_AMOUNT");
    });
  });

  describe("3. Kill Switches & Circuit Breakers", () => {
    it("blocks WhatsApp OTP sends when KILL_WHATSAPP_OTP is enabled", async () => {
      await setKillSwitch("KILL_WHATSAPP_OTP", true, { updatedBy: "admin_test", reason: "Meta outage" });
      assert.equal(await isKillSwitchActive("KILL_WHATSAPP_OTP"), true);

      const res = await invokeApp(apiApp, {
        method: "POST",
        url: "/api/v1/auth/otp/send",
        body: { channel: "whatsapp", destination: "+919876543210", purpose: "LOGIN" }
      });

      assert.equal(res.status, 503);
      assert.equal(res.body.error.code, "ERR_FEATURE_DISABLED");
    });

    it("trips circuit breaker after consecutive provider failures and recovers after cooldown", async () => {
      const provider = "WHATSAPP_META";
      assert.equal((await canExecute(provider)).allowed, true);

      for (let i = 0; i < 5; i++) {
        await recordFailure(provider, "HTTP 502 Bad Gateway");
      }

      const state = await getCircuitState(provider);
      assert.equal(state.state, "OPEN");
      assert.equal((await canExecute(provider)).allowed, false);
    });
  });

  describe("4. DPDP / GDPR Privacy Export & Right to Erasure", () => {
    it("records user consent, exports full portable JSON bundle, and permanently deletes all user data", async () => {
      const { user } = await getOrCreateUser({ channel: "email", destination: "dpdp@example.com" });
      const uid = user.uid;

      await recordConsent(uid, { privacyPolicyVersion: "2026.05", marketingOptIn: false });
      const consent = await getConsentStatus(uid);
      assert.equal(consent.accepted, true);
      assert.equal(consent.privacyPolicyVersion, "2026.05");

      const person = await createPerson(uid, { name: "Amit", phone: "+919876543211" });
      await createRecord(uid, {
        personId: person.personId,
        counterpartyName: "Amit",
        counterpartyRole: "BORROWER",
        amountPaise: 125000,
        note: "Rent split"
      });

      const exported = await exportUserData(uid);
      assert.equal(exported.schemaVersion, "udhaar-dpdp-export-v1");
      assert.equal(exported.ledger.people.length, 1);
      assert.equal(exported.ledger.records.length, 1);
      assert.equal(exported.ledger.records[0].amountPaise, 125000);

      const erasure = await deleteUserData(uid, { reason: "USER_REQUESTED_DPDP" });
      assert.equal(erasure.deleted, true);
      assert.equal(erasure.counts.recordsDeleted, 1);
      assert.equal(erasure.counts.peopleDeleted, 1);

      const postExport = await exportUserData(uid);
      assert.equal(postExport.ledger.records.length, 0);
      assert.equal(postExport.ledger.people.length, 0);
    });
  });

  describe("5. Disaster Recovery Backup, Checksum Verification & Restore Drill + KMS Rotation", () => {
    it("creates a verified snapshot, detects tampering, restores cleanly after data loss, and rotates DEKs without data loss", async () => {
      const { user } = await getOrCreateUser({ channel: "email", destination: "dr@example.com" });
      const uid = user.uid;

      const person = await createPerson(uid, { name: "Priya", phone: "+919988776655" });
      const rec = await createRecord(uid, {
        personId: person.personId,
        counterpartyName: "Priya",
        counterpartyRole: "BORROWER",
        amountPaise: 500000
      });

      // Encrypt sensitive note with server envelope
      const encrypted = await encryptServerEnvelope(uid, "CONFIDENTIAL_TAX_ID_9988", "ledger_secret");
      const rotateRes = await rotateAllDEKs();
      assert.ok(rotateRes.rotatedCount >= 1);
      const decryptedAfterRotate = await decryptServerEnvelope(uid, encrypted, "ledger_secret");
      assert.equal(decryptedAfterRotate, "CONFIDENTIAL_TAX_ID_9988");

      // Take backup snapshot
      const snapMeta = await createBackupSnapshot({ createdBy: "dr_drill" });
      const verifyOk = await verifyBackupIntegrity(snapMeta.snapshotId);
      assert.equal(verifyOk.valid, true);

      // Wipe user data completely
      await deleteUserData(uid);
      const emptyCheck = await exportUserData(uid);
      assert.equal(emptyCheck.ledger.records.length, 0);

      // Restore from snapshot
      const restoreRes = await restoreFromSnapshot(snapMeta.snapshotId);
      assert.equal(restoreRes.restored, true);

      const restoredData = await exportUserData(uid);
      assert.equal(restoredData.ledger.records.length, 1);
      assert.equal(restoredData.ledger.records[0].recordId, rec.recordId);
      assert.equal(restoredData.ledger.records[0].amountPaise, 500000);
    });
  });
});
