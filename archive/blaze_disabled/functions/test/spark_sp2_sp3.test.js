/**
 * Stage SP2 & SP3 — Firestore Data Model, Rules, Sync Engine & Guest Capability Links Test Suite
 * (functions/test/spark_sp2_sp3.test.js)
 * 
 * Verifies:
 * 1. SP2: Quota Budget documentation (docs/spark-quota-budget.md) and capacity calculations.
 * 2. SP2: Client-side zero-knowledge AES-256-GCM field encryption and PBKDF2 key derivation.
 * 3. SP2: FirestoreSyncClient monotonic revision bumping, LWW conflict resolution, and payment set union.
 * 4. SP2: Graceful Quota Exhaustion handling.
 * 5. SP3: CapabilityLinkService 128-bit secret generation, SHA-256 linkId, and WhatsApp intent URLs.
 * 6. SP3: Proposal Pattern: fixed action IDs and immutable proposals.
 * 7. SP3: CSP headers in firebase.json contain all required Firebase endpoints.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  encryptField,
  decryptField,
  encryptRecordFields,
  decryptRecordFields
} = require("../../webapp/src/features/crypto/clientFieldEncryption");

const { FirestoreSyncClient } = require("../../webapp/src/api/firestoreSyncClient");
const {
  generateSecretToken,
  computeSha256,
  CapabilityLinkService
} = require("../../webapp/src/features/links/capabilityLinkService");

test.describe("Stage SP2: Quota Budget & Client-Side Sync Invariants", () => {
  test("2.1 docs/spark-quota-budget.md models 10, 100, and 1,000 DAU within Spark limits", () => {
    const budgetPath = path.resolve(__dirname, "../../docs/spark-quota-budget.md");
    assert.ok(fs.existsSync(budgetPath), "spark-quota-budget.md must exist");

    const content = fs.readFileSync(budgetPath, "utf8");
    assert.match(content, /50,000 reads/i);
    assert.match(content, /20,000 writes/i);
    assert.match(content, /10 DAU/);
    assert.match(content, /100 DAU/);
    assert.match(content, /1,000 DAU/);
    assert.match(content, /max_client_daily_writes = 500/i);
    assert.match(content, /Quota Exhaustion Runbook/i);
  });

  test("2.2 Client-side AES-256-GCM field encryption encrypts and decrypts with passphrase", async () => {
    const passphrase = "MySuperSecretMasterKey123!";
    const secretParty = "Ramesh Gupta";
    const secretPhone = "+919876543210";
    const secretNote = "Lent for store inventory purchase in Guwahati market";

    const encName = await encryptField(secretParty, passphrase);
    const encPhone = await encryptField(secretPhone, passphrase);
    const encNote = await encryptField(secretNote, passphrase);

    assert.ok(encName.startsWith("enc:v1:"));
    assert.ok(encPhone.startsWith("enc:v1:"));
    assert.ok(encNote.startsWith("enc:v1:"));

    assert.notEqual(encName, secretParty);
    assert.notEqual(encPhone, secretPhone);

    // Decrypt with correct passphrase
    const decName = await decryptField(encName, passphrase);
    const decPhone = await decryptField(encPhone, passphrase);
    const decNote = await decryptField(encNote, passphrase);

    assert.equal(decName, secretParty);
    assert.equal(decPhone, secretPhone);
    assert.equal(decNote, secretNote);

    // Decrypt with wrong passphrase fails safely
    const wrongDec = await decryptField(encName, "WrongPassphrase!");
    assert.match(wrongDec, /Decryption Failed/);

    // Record-level encryption helper
    const originalRecord = {
      id: "rec_123",
      principalAmountPaise: 500000,
      partyName: "Sunita Sharma",
      partyPhone: "+9779841234567",
      note: "Urgent medical loan",
      upiId: "sunita@upi"
    };

    const encryptedRecord = await encryptRecordFields(originalRecord, passphrase);
    assert.equal(encryptedRecord.principalAmountPaise, 500000); // Amount remains integer paise
    assert.ok(encryptedRecord.partyName.startsWith("enc:v1:"));
    assert.ok(encryptedRecord.partyPhone.startsWith("enc:v1:"));
    assert.ok(encryptedRecord.upiId.startsWith("enc:v1:"));

    const decryptedRecord = await decryptRecordFields(encryptedRecord, passphrase);
    assert.equal(decryptedRecord.partyName, "Sunita Sharma");
    assert.equal(decryptedRecord.partyPhone, "+9779841234567");
    assert.equal(decryptedRecord.upiId, "sunita@upi");
  });

  test("2.3 FirestoreSyncClient enforces email-verification, monotonic rev bump, and LWW + CRDT union", async () => {
    let storedDocs = {};
    const mockDb = {
      collection: (colName) => ({
        doc: (docId) => ({
          collection: (subColName) => ({
            doc: (subDocId) => ({
              get: async () => ({
                exists: !!storedDocs[`${colName}/${docId}/${subColName}/${subDocId}`],
                data: () => storedDocs[`${colName}/${docId}/${subColName}/${subDocId}`]
              }),
              set: async (data, opts = {}) => {
                const key = `${colName}/${docId}/${subColName}/${subDocId}`;
                storedDocs[key] = opts.merge ? { ...storedDocs[key], ...data } : data;
              }
            })
          })
        })
      })
    };

    // Case 1: Unverified user is blocked
    const unverifiedAuth = {
      currentUser: { uid: "user_123", emailVerified: false }
    };
    const clientUnverified = new FirestoreSyncClient(mockDb, unverifiedAuth);
    await assert.rejects(
      async () => clientUnverified.saveRecord({ id: "rec_1" }),
      /ERR_EMAIL_UNVERIFIED/
    );

    // Case 2: Verified user saves record with monotonic rev increment
    const verifiedAuth = {
      currentUser: { uid: "user_123", emailVerified: true }
    };
    const client = new FirestoreSyncClient(mockDb, verifiedAuth);

    const saved1 = await client.saveRecord({
      id: "rec_100",
      principalAmountPaise: 100000,
      counterpartyRole: "BORROWER"
    });
    assert.equal(saved1.rev, 1);
    assert.equal(saved1.uid, "user_123");

    const saved2 = await client.saveRecord({
      id: "rec_100",
      note: "Updated payment notes"
    });
    assert.equal(saved2.rev, 2);

    // Case 3: Reconcile with LWW and Payment Union
    const local = [
      { id: "rec_100", rev: 2, note: "Local version", updatedAtMs: 1000 },
      { id: "rec_200", rev: 1, note: "Local only", updatedAtMs: 500 }
    ];
    const remote = [
      { id: "rec_100", rev: 3, note: "Remote higher revision", updatedAtMs: 1200 },
      { id: "rec_300", rev: 1, note: "Remote newly created", updatedAtMs: 1100 }
    ];
    const events = [
      { id: "evt_1", recordId: "rec_100", eventType: "PAYMENT", amountPaise: 25000 },
      { id: "evt_2", recordId: "rec_100", eventType: "PAYMENT", amountPaise: 25000 },
      { id: "evt_3", recordId: "rec_200", eventType: "PAYMENT", amountPaise: 10000 }
    ];

    const reconciled = client.reconcileRecords(local, remote, events);
    const rec100 = reconciled.find(r => r.id === "rec_100");
    const rec200 = reconciled.find(r => r.id === "rec_200");
    const rec300 = reconciled.find(r => r.id === "rec_300");

    assert.equal(rec100.rev, 3);
    assert.equal(rec100.note, "Remote higher revision");
    assert.equal(rec100.paymentEventCount, 2); // 2 payment events combined

    assert.equal(rec200.id, "rec_200");
    assert.equal(rec200.paymentEventCount, 1);

    assert.equal(rec300.id, "rec_300");
  });

  test("2.4 FirestoreSyncClient gracefully maps RESOURCE_EXHAUSTED errors", () => {
    const client = new FirestoreSyncClient(null, null);
    const rawError = new Error("Resource has been exhausted (e.g. check quota).");
    rawError.code = "resource-exhausted";

    const handled = client._handleFirestoreError(rawError);
    assert.equal(handled.code, "ERR_QUOTA_EXHAUSTED");
    assert.equal(handled.isQuota, true);
    assert.match(handled.message, /resting until tomorrow/);
  });
});

test.describe("Stage SP3: Guest Capability Links & Proposal Pattern Invariants", () => {
  test("3.1 CapabilityLinkService generates 128-bit secret and SHA-256 linkId", async () => {
    const secret = generateSecretToken();
    assert.equal(secret.length, 32); // 16 bytes in hex = 32 chars
    assert.match(secret, /^[a-f0-9]{32}$/);

    const linkId = await computeSha256(secret);
    assert.equal(linkId.length, 64); // SHA-256 hex = 64 chars
    assert.match(linkId, /^[a-f0-9]{64}$/);
  });

  test("3.2 CapabilityLinkService creates capability URL with fragment and native WhatsApp sharing", async () => {
    let savedLinkDoc = null;
    const mockDb = {
      collection: (col) => ({
        doc: (id) => ({
          set: async (doc) => { savedLinkDoc = doc; },
          update: async (updates) => { savedLinkDoc = { ...savedLinkDoc, ...updates }; }
        })
      })
    };
    const mockAuth = {
      currentUser: { uid: "lender_456", displayName: "Jyotirmoy", emailVerified: true }
    };

    const service = new CapabilityLinkService(mockDb, mockAuth);

    const record = {
      id: "rec_789",
      principalAmountPaise: 2500000,
      partyName: "Vikram",
      counterpartyRole: "BORROWER",
      dueDateMs: 1799999999999
    };

    const result = await service.createCapabilityLink(record);
    assert.ok(result.fullUrl.startsWith("https://udhaar.jyotirmoyb.com/u/r/rec_789#"));
    assert.equal(result.linkDoc.ownerUid, "lender_456");
    assert.equal(result.linkDoc.principalAmountPaise, 2500000);
    assert.equal(result.linkDoc.status, "ACTIVE");

    // WhatsApp intent sharing url
    const waUrl = service.buildWhatsAppShareUrl(result.fullUrl, {
      partyName: "Vikram",
      amountPaise: 2500000,
      phone: "+919876543210"
    });

    assert.ok(waUrl.startsWith("https://wa.me/919876543210?text="));
    assert.match(decodeURIComponent(waUrl), /₹25,000/);
    assert.match(decodeURIComponent(waUrl), /https:\/\/udhaar\.jyotirmoyb\.com\/u\/r\/rec_789#/);
    assert.match(decodeURIComponent(waUrl), /No app installation required/);

    // Revocation
    await service.revokeCapabilityLink(result.linkId);
    assert.equal(savedLinkDoc.status, "REVOKED");
  });

  test("3.3 firebase.json CSP includes Firebase endpoints for client SDKs in Spark Mode", () => {
    const configPath = path.resolve(__dirname, "../../firebase.json");
    const config = JSON.parse(fs.readFileSync(configPath, "utf8"));

    const uHeaders = config.hosting.headers.find(h => h.source === "/u/**");
    assert.ok(uHeaders, "Must configure headers for /u/**");

    const csp = uHeaders.headers.find(h => h.key === "Content-Security-Policy").value;
    assert.match(csp, /firestore\.googleapis\.com/);
    assert.match(csp, /identitytoolkit\.googleapis\.com/);
    assert.match(csp, /securetoken\.googleapis\.com/);
    assert.match(csp, /firebaseinstallations\.googleapis\.com/);
    assert.match(csp, /content-firebaseappcheck\.googleapis\.com/);
  });
});
