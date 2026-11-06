const test = require("node:test");
const assert = require("node:assert/strict");

const Merkle = require("../../web/assets/merkle-chain.js");
const E2EE = require("../../web/assets/e2ee-crypto.js");
const IDB = require("../../web/assets/idb-outbox.js");
const ImageProcessor = require("../../web/assets/image-processor.js");
const Telemetry = require("../../web/assets/telemetry.js");
const WebAuthn = require("../../web/assets/webauthn-lock.js");

// =========================================================================
// 1. Merkle Hash Chain Tests
// =========================================================================
test("Merkle Hash Chain: Genesis block creation and format", async () => {
  const genesis = await Merkle.createGenesisBlock("rec_merkle_01", 1762400000000, {
    partyName: "Ravi Sharma",
    principalAmountPaise: 500000
  });

  assert.equal(genesis.index, 0);
  assert.equal(genesis.recordId, "rec_merkle_01");
  assert.equal(genesis.prevHash, Merkle.GENESIS_PREV_HASH);
  assert.equal(genesis.prevHash.length, 64);
  assert.equal(genesis.hash.length, 64);
  assert.equal(/^[0-9a-f]{64}$/.test(genesis.hash), true);

  const seal = Merkle.formatMerkleSeal(genesis);
  assert.match(seal, /^SEAL#0:[0-9A-F]{8}\.\.\.[0-9A-F]{4}$/);
});

test("Merkle Hash Chain: Appending blocks and validating unbroken chain", async () => {
  const b0 = await Merkle.createGenesisBlock("rec_merkle_02", 1762400000000, { amount: 10000 });
  const b1 = await Merkle.appendMerkleBlock(b0, "CONFIRM", 1762400100000, { via: "GUEST_WEB_LINK" });
  const b2 = await Merkle.appendMerkleBlock(b1, "PAYMENT", 1762400200000, { amount: 3000, mode: "UPI" });
  const b3 = await Merkle.appendMerkleBlock(b2, "PAYMENT", 1762400300000, { amount: 7000, mode: "CASH" });

  const chain = [b0, b1, b2, b3];
  assert.equal(chain.length, 4);
  assert.equal(b3.index, 3);
  assert.equal(b3.prevHash, b2.hash);

  const result = await Merkle.verifyMerkleChain(chain);
  assert.equal(result.isValid, true);
  assert.equal(result.length, 4);
  assert.equal(result.unbrokenLength, 4);
  assert.equal(result.brokenIndex, null);
  assert.equal(result.error, null);
});

test("Merkle Hash Chain: Tamper Detection catches payload or linkage corruption", async () => {
  const b0 = await Merkle.createGenesisBlock("rec_merkle_03", 1762400000000, { amount: 5000 });
  const b1 = await Merkle.appendMerkleBlock(b0, "CONFIRM", 1762400100000, { via: "GUEST_WEB_LINK" });
  const b2 = await Merkle.appendMerkleBlock(b1, "PAYMENT", 1762400200000, { amount: 2000 });

  // Tamper 1: Modify block 1 payload hash
  const tamperedPayload = [b0, { ...b1, payloadHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" }, b2];
  const res1 = await Merkle.verifyMerkleChain(tamperedPayload);
  assert.equal(res1.isValid, false);
  assert.equal(res1.brokenIndex, 1);
  assert.match(res1.error, /mismatch/i);

  // Tamper 2: Corrupt linkage prevHash
  const tamperedLinkage = [b0, b1, { ...b2, prevHash: "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad1" }];
  const res2 = await Merkle.verifyMerkleChain(tamperedLinkage);
  assert.equal(res2.isValid, false);
  assert.equal(res2.brokenIndex, 2);
  assert.match(res2.error, /broken linkage/i);
});

// =========================================================================
// 2. Zero-Knowledge End-to-End Encryption Tests
// =========================================================================
test("Zero-Knowledge E2EE: Derives AES-GCM-256 key and encrypts/decrypts arbitrary JSON", async () => {
  const token = "cap_secret_token_123456789";
  const key = await E2EE.deriveKeyFromToken(token, "salt_test");
  assert.ok(key, "CryptoKey must be generated");

  const payload = {
    counterparty: "Sunita Roy",
    secretNote: "Confidential private business loan",
    amount: 50000
  };

  const envelope = await E2EE.encryptJson(payload, key);
  assert.ok(envelope.iv, "Must have IV");
  assert.ok(envelope.ciphertext, "Must have ciphertext");
  assert.equal(envelope.tagLength, 128);

  const decrypted = await E2EE.decryptJson(envelope, key);
  assert.deepEqual(decrypted, payload);
});

test("Zero-Knowledge E2EE: Record sanitization and zero-knowledge roundtrip", async () => {
  const token = "tok_participant_access_key_998877";
  const record = {
    id: "rec_e2ee_99",
    principalAmountPaise: 2500000,
    partyName: "Vikram Malhotra",
    counterpartyPhone: "9876543210",
    upiId: "vikram@oksbi",
    note: "Emergency personal advance",
    items: [{ name: "Medical bills", price: 25000 }]
  };

  const encryptedRecord = await E2EE.encryptRecordSensitives(record, token);
  assert.equal(encryptedRecord.partyName, "[E2EE Encrypted]");
  assert.equal(encryptedRecord.note, "[E2EE Encrypted]");
  assert.equal(encryptedRecord.upiId, "e2ee@masked");
  assert.equal(encryptedRecord.items.length, 0);
  assert.ok(encryptedRecord.e2eeEnvelope, "Must contain E2EE envelope");

  const restored = await E2EE.decryptRecordSensitives(encryptedRecord, token);
  assert.equal(restored.isDecrypted, true);
  assert.equal(restored.partyName, "Vikram Malhotra");
  assert.equal(restored.counterpartyPhone, "9876543210");
  assert.equal(restored.upiId, "vikram@oksbi");
  assert.equal(restored.note, "Emergency personal advance");
  assert.deepEqual(restored.items, [{ name: "Medical bills", price: 25000 }]);

  // Wrong token fails safely
  const wrongAttempt = await E2EE.decryptRecordSensitives(encryptedRecord, "wrong_token_here");
  assert.equal(wrongAttempt.isDecrypted, false);
});

// =========================================================================
// 3. IndexedDB & Offline Outbox Engine Tests
// =========================================================================
test("IndexedDB & Outbox: Record persistence and revision tracking", async () => {
  const rec = { id: "rec_idb_01", partyName: "Pooja", amount: 4500, userId: "usr_alice" };
  const saved = await IDB.saveRecord(rec);
  assert.equal(saved.id, "rec_idb_01");
  assert.equal(saved.clientRevision, 1);
  assert.ok(saved.updatedAtMs > 0);

  const fetched = await IDB.getRecordById("rec_idb_01");
  assert.equal(fetched.id, "rec_idb_01");
  assert.equal(fetched.partyName, "Pooja");

  const all = await IDB.getAllRecords("usr_alice");
  assert.ok(all.some(r => r.id === "rec_idb_01"));
});

test("IndexedDB & Outbox: Mutation queuing with deterministic idempotency keys", async () => {
  const m = await IDB.enqueueMutation("RECORD_PAYMENT", "rec_idb_01", { amount: 1500, mode: "UPI" }, 2);
  assert.ok(m.mutationId);
  assert.ok(m.idempotencyKey);
  assert.equal(m.action, "RECORD_PAYMENT");
  assert.equal(m.status, "PENDING");
  assert.equal(m.clientRevision, 2);

  const pending = await IDB.getPendingMutations();
  assert.ok(pending.some(item => item.mutationId === m.mutationId));
});

test("IndexedDB & Outbox: Queue drainage with success and failure exponential backoff", async () => {
  const mSuccess = await IDB.enqueueMutation("CONFIRM", "rec_sync_ok", {});
  const mFail = await IDB.enqueueMutation("DISPUTE", "rec_sync_fail", {});

  const drainResult = await IDB.drainOutboxQueue(async (mutation) => {
    if (mutation.mutationId === mSuccess.mutationId) {
      return true;
    }
    throw new Error("Simulated 503 Service Unavailable");
  });

  assert.ok(drainResult.succeeded >= 1);
  assert.ok(drainResult.failed >= 1);

  // Verify failed mutation has incremented attempts and nextRetryAt in future
  const failedMutation = IDB._memoryStore.outbox_queue.get(mFail.mutationId);
  assert.equal(failedMutation.status, "FAILED");
  assert.equal(failedMutation.attempts, 1);
  assert.ok(failedMutation.nextRetryAt > Date.now());
});

test("CRDT-Lite Conflict Resolution: Preserves union of payments and higher revisions", () => {
  const local = {
    id: "rec_crdt_01",
    clientRevision: 2,
    updatedAtMs: 1762400100000,
    payments: [{ id: "pay_local_1", paise: 100000 }]
  };
  const remote = {
    id: "rec_crdt_01",
    clientRevision: 3,
    updatedAtMs: 1762400200000,
    payments: [{ id: "pay_remote_2", paise: 200000 }]
  };

  const resolved = IDB.resolveRecordConflict(local, remote);
  assert.equal(resolved.clientRevision, 3, "Higher revision must win base fields");
  assert.equal(resolved.payments.length, 2, "Payment set union must preserve both payments");
});

// =========================================================================
// 4. Client-Side Image Processor Tests
// =========================================================================
test("Image Processor: Constrains dimensions while preserving exact aspect ratios", () => {
  // 4000x3000 -> 1600x1200 (4:3 ratio)
  const d1 = ImageProcessor.calculateConstrainedDimensions(4000, 3000, 1600);
  assert.equal(d1.width, 1600);
  assert.equal(d1.height, 1200);
  assert.equal(d1.scaled, true);

  // 1200x1600 (portrait) -> 1200x1600 (within 1600 limit, not scaled)
  const d2 = ImageProcessor.calculateConstrainedDimensions(1200, 1600, 1600);
  assert.equal(d2.width, 1200);
  assert.equal(d2.height, 1600);
  assert.equal(d2.scaled, false);

  // Extreme panorama 5000x1000 -> 1600x320
  const d3 = ImageProcessor.calculateConstrainedDimensions(5000, 1000, 1600);
  assert.equal(d3.width, 1600);
  assert.equal(d3.height, 320);
  assert.equal(d3.scaled, true);
});

// =========================================================================
// 5. W3C Distributed Tracing & Telemetry Tests
// =========================================================================
test("W3C Distributed Tracing: Generates valid W3C traceparent and correlation IDs", () => {
  const trace = Telemetry.generateTraceparent();
  assert.match(trace, /^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);

  const corrId = Telemetry.createCorrelationId();
  assert.match(corrId, /^corr-\d+-[0-9a-f]{8}$/);
});

// =========================================================================
// 6. WebAuthn Passkeys Helper Tests
// =========================================================================
test("WebAuthn Passkey: Buffer to Base64 and Base64 to Buffer roundtrip", () => {
  const original = new Uint8Array([0, 15, 30, 45, 60, 128, 200, 255]);
  const b64 = WebAuthn.bufferToBase64(original.buffer);
  assert.ok(typeof b64 === "string");

  const restored = new Uint8Array(WebAuthn.base64ToBuffer(b64));
  assert.deepEqual(Array.from(restored), Array.from(original));
});
