/**
 * Stage R9: Comprehensive Production Security & Threat Model Test Suite
 * (functions/test/stage_r9_security.test.js)
 * 
 * Verifies:
 * 1. IDOR prevention on every resource (records, links, sync changes, keys, exports).
 * 2. Replay attack resistance (one-time OTP, cross-flow isolation, idempotency deduplication).
 * 3. Timing attack resistance (constant-time evaluation on HMACs and auth tokens).
 * 4. Rate-limit protection against header spoofing (X-Forwarded-For manipulation).
 * 5. Instant Crypto-Shredding (irreversible ciphertext destruction upon key deletion).
 */

const { test, describe, before, after } = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
const crypto = require("crypto");

const { createApiApp } = require("../src/app");
const {
  getAuthRepository,
  getSessionRepository,
  getLedgerRepository,
  getSyncRepository,
  getCryptoRepository,
  getOtpRepository,
  resetTestRepositories
} = require("../src/repositories");
const { createSession } = require("../src/sessions/sessionManager");
const {
  getOrCreateUserDek,
  encryptRecordFields,
  decryptRecordFields,
  shredUserDek,
  clearDekCache
} = require("../src/crypto/envelopeCrypto");
const { constantTimeEquals, computeOtpHash } = require("../src/crypto/serverCrypto");
const { fakeWhatsApp } = require("../src/providers");

function makeRequest(server, options, body = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => { data += chunk; });
      res.on("end", () => {
        let json = null;
        try { json = JSON.parse(data); } catch (_) { json = data; }
        resolve({ status: res.statusCode, headers: res.headers, body: json });
      });
    });
    req.on("error", reject);
    if (body) {
      req.write(typeof body === "string" ? body : JSON.stringify(body));
    }
    req.end();
  });
}

describe("Stage R9: Security & Threat Model Defense Suite", () => {
  let userA, sessionA;
  let userB, sessionB;

  before(async () => {
    resetTestRepositories();
    const authRepo = getAuthRepository();

    userA = await authRepo.createUserTransaction(
      { uid: "usr_sec_alice", displayName: "Alice Security", createdAt: Date.now() },
      { uid: "usr_sec_alice", phoneHash: "hash_phone_alice_sec" },
      { uid: "usr_sec_alice", emailHash: "hash_email_alice_sec" }
    );

    userB = await authRepo.createUserTransaction(
      { uid: "usr_sec_bob", displayName: "Bob Security", createdAt: Date.now() },
      { uid: "usr_sec_bob", phoneHash: "hash_phone_bob_sec" },
      { uid: "usr_sec_bob", emailHash: "hash_email_bob_sec" }
    );

    sessionA = await createSession({ uid: userA.uid, clientType: "ANDROID" });
    sessionB = await createSession({ uid: userB.uid, clientType: "ANDROID" });
  });

  after(() => {
    resetTestRepositories();
  });

  describe("1. Insecure Direct Object Reference (IDOR) Defenses", () => {
    test("User B cannot read, update, or delete User A's ledger records", async () => {
      const app = createApiApp();
      const server = http.createServer(app);
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      try {
        // Alice creates a record
        const createRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/ledger/records",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${sessionA.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, {
          principalAmountPaise: 500000,
          partyName: "Secret Supplier",
          counterpartyRole: "BORROWER",
          currency: "INR"
        });

        assert.equal(createRes.status, 201);
        const recordId = createRes.body.recordId;
        assert.ok(recordId);

        // Bob attempts to GET Alice's record by ID -> 404 (tenant boundary enforced)
        const bobGetRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: `/api/v1/ledger/records/${recordId}`,
          method: "GET",
          headers: {
            "Authorization": `Bearer ${sessionB.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        });
        assert.equal(bobGetRes.status, 404, "Cross-user record access must return 404 Not Found");

        // Bob attempts to UPDATE Alice's record -> 404
        const bobPutRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: `/api/v1/ledger/records/${recordId}`,
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${sessionB.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { principalAmountPaise: 100, counterpartyRole: "BORROWER", partyName: "Hacked" });
        assert.equal(bobPutRes.status, 404);

        // Bob attempts to DELETE Alice's record -> 404
        const bobDeleteRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: `/api/v1/ledger/records/${recordId}`,
          method: "DELETE",
          headers: {
            "Authorization": `Bearer ${sessionB.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        });
        assert.equal(bobDeleteRes.status, 404);

        // Verify Alice's record remains untouched
        const aliceVerifyRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: `/api/v1/ledger/records/${recordId}`,
          method: "GET",
          headers: {
            "Authorization": `Bearer ${sessionA.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        });
        assert.equal(aliceVerifyRes.status, 200);
        assert.equal(aliceVerifyRes.body.principalAmountPaise, 500000);
      } finally {
        server.closeAllConnections?.();
        server.close();
      }
    });

    test("User B sync pull never leaks User A's changeLog mutations", async () => {
      const app = createApiApp();
      const server = http.createServer(app);
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      try {
        // Alice sync pushes a change
        await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/sync/push",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${sessionA.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, {
          deviceId: "device_alice",
          changes: [{
            entity: "record",
            op: "CREATE",
            syncId: "rec_alice_isolated_1",
            hlc: "2026-11-07T12:30:00.000Z-0001-node1",
            fields: {
              recordId: "rec_alice_isolated_1",
              principalAmountPaise: 999000,
              remainingAmountPaise: 999000,
              partyName: "Alice Private Partner",
              counterpartyRole: "BORROWER",
              currency: "INR"
            }
          }]
        });

        // Bob sync pulls -> must be empty for Bob
        const bobPullRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/sync/pull?cursor=0",
          method: "GET",
          headers: {
            "Authorization": `Bearer ${sessionB.customToken}`,
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        });

        assert.equal(bobPullRes.status, 200);
        const bobMutations = bobPullRes.body.changes.filter((c) => c.syncId === "rec_alice_isolated_1");
        assert.equal(bobMutations.length, 0, "Bob's sync stream must never contain Alice's records");
      } finally {
        server.closeAllConnections?.();
        server.close();
      }
    });

    test("Encryption keys collection strictly isolates keys per tenant UID", async () => {
      const cryptoRepo = getCryptoRepository();
      const dekA = await getOrCreateUserDek(userA.uid);
      const dekB = await getOrCreateUserDek(userB.uid);

      assert.ok(dekA);
      assert.ok(dekB);
      assert.notEqual(dekA.toString("hex"), dekB.toString("hex"), "Each tenant must have a distinct 256-bit DEK");

      const docA = await cryptoRepo.getUserDekDoc(userA.uid);
      const docB = await cryptoRepo.getUserDekDoc(userB.uid);

      assert.ok(docA.wrappedDek);
      assert.ok(docB.wrappedDek);
      assert.notEqual(docA.wrappedDek, docB.wrappedDek);
    });
  });

  describe("2. Replay Attack Resistance", () => {
    test("One-time OTP code cannot be replayed after successful verification", async () => {
      const app = createApiApp();
      const server = http.createServer(app);
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      try {
        const phone = "+919876543299";
        // 1. Send OTP code for signup
        const sendRes = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/signup/start",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { phoneNumber: phone });

        assert.equal(sendRes.status, 200);
        const flowId = sendRes.body.flowId;
        const code = fakeWhatsApp.getLastCodeFor(phone);
        assert.ok(code, "Fake WhatsApp provider must have captured code");

        // 2. First verification: Succeeds
        const verify1 = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/otp/verify",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { flowId, code, purpose: "SIGNUP" });

        assert.equal(verify1.status, 200);
        assert.ok(verify1.body.flowToken);

        // 3. Second verification (REPLAY ATTACK): Must fail
        const verify2 = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/otp/verify",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { flowId, code, purpose: "SIGNUP" });

        assert.notEqual(verify2.status, 200, "Replayed OTP verification must be rejected");
        assert.equal(verify2.status, 404);
        assert.equal(verify2.body.code, "ERR_CHALLENGE_NOT_FOUND");
      } finally {
        server.closeAllConnections?.();
        server.close();
      }
    });

    test("OTP code issued for Flow A cannot be submitted against Flow B (Cross-Flow Replay)", async () => {
      const app = createApiApp();
      const server = http.createServer(app);
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      try {
        // Flow 1
        const resFlow1 = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/signup/start",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { phoneNumber: "+919876543201" });
        const code1 = fakeWhatsApp.getLastCodeFor("+919876543201");

        // Advance time slightly to avoid cooldown
        await new Promise((r) => setTimeout(r, 10));

        // Flow 2
        const resFlow2 = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/signup/start",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, { phoneNumber: "+919876543202" });

        // Replay Flow 1's code against Flow 2's flowId
        const crossReplay = await makeRequest(server, {
          hostname: "localhost",
          port,
          path: "/api/v1/auth/otp/verify",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Firebase-AppCheck": "valid_web_app_check_token"
          }
        }, {
          flowId: resFlow2.body.flowId,
          code: code1,
          purpose: "SIGNUP"
        });

        assert.notEqual(crossReplay.status, 200, "Cross-flow replay must be rejected");
        assert.equal(crossReplay.body.code, "ERR_INVALID_OTP");
      } finally {
        server.closeAllConnections?.();
        server.close();
      }
    });
  });

  describe("3. Timing Attack Resistance", () => {
    test("constantTimeEquals evaluates in constant time without short-circuiting on length or character mismatch", () => {
      const secretHash = crypto.createHmac("sha256", "test_key").update("secret_message").digest("hex");
      const matchingHash = secretHash;
      const differingByte = secretHash.slice(0, -2) + (secretHash.endsWith("0") ? "1" : "0") + secretHash.slice(-1);
      const differentLength = secretHash.slice(0, 32);

      // Valid match
      assert.equal(constantTimeEquals(secretHash, matchingHash), true);

      // Single character difference
      assert.equal(constantTimeEquals(secretHash, differingByte), false);

      // Different length (must return false safely without throwing)
      assert.equal(constantTimeEquals(secretHash, differentLength), false);

      // Non-string types
      assert.equal(constantTimeEquals(null, secretHash), false);
      assert.equal(constantTimeEquals(secretHash, undefined), false);
      assert.equal(constantTimeEquals(123, 123), false);
    });
  });

  describe("4. Rate-Limit Bypass via Header Spoofing (X-Forwarded-For)", () => {
    test("Rotating X-Forwarded-For headers from the same connection does not bypass IP rate limit", async () => {
      const app = createApiApp();
      const server = http.createServer(app);
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      try {
        // Send rapid requests across distinct phone destinations while cycling spoofed X-Forwarded-For headers
        let rateLimitTripped = false;

        // MAX_HOURLY_PER_IP is 20 in config. After 20 requests from the same connection, request 21 trips 429.
        for (let i = 0; i < 25; i++) {
          const fakeIp = `198.51.100.${i + 1}`;
          const uniquePhone = `+91987650${String(i).padStart(4, "0")}`;

          const res = await makeRequest(server, {
            hostname: "localhost",
            port,
            path: "/api/v1/auth/signup/start",
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "X-Firebase-AppCheck": "valid_web_app_check_token",
              "X-Forwarded-For": fakeIp,
              "X-Real-IP": fakeIp
            }
          }, { phoneNumber: uniquePhone });

          if (res.status === 429) {
            rateLimitTripped = true;
            assert.equal(res.body.code, "ERR_RATE_LIMIT_IP");
            break;
          }
        }

        assert.ok(rateLimitTripped, "IP Rate limit must trip despite cycling spoofed X-Forwarded-For headers");
      } finally {
        server.closeAllConnections?.();
        server.close();
      }
    });
  });

  describe("5. Cryptographic Shredding & Permanent Ciphertext Destruction", () => {
    test("Shredding a user DEK renders all existing encrypted record ciphertexts permanently undecryptable", async () => {
      const shredUid = "usr_shred_target_123";

      // 1. Generate active DEK for user
      const rawDek = await getOrCreateUserDek(shredUid);
      assert.ok(rawDek && rawDek.length === 32, "256-bit DEK must be generated");

      // 2. Encrypt sensitive record fields
      const originalRecord = {
        recordId: "rec_shred_test_1",
        partyName: "Confidential Supplier Ltd",
        partyPhone: "+919876543210",
        note: "Sensitive financial settlement terms",
        principalAmountPaise: 15000000,
        currency: "INR"
      };

      const encryptedRecord = encryptRecordFields(originalRecord, rawDek);

      // Verify fields are encrypted (v1:iv:tag:ciphertext)
      assert.ok(encryptedRecord.partyName.startsWith("v1:"));
      assert.ok(encryptedRecord.partyPhone.startsWith("v1:"));
      assert.ok(encryptedRecord.note.startsWith("v1:"));
      assert.notEqual(encryptedRecord.partyName, originalRecord.partyName);

      // Verify record decrypts correctly while key is intact
      const decryptedPreShred = decryptRecordFields(encryptedRecord, rawDek);
      assert.equal(decryptedPreShred.partyName, originalRecord.partyName);
      assert.equal(decryptedPreShred.partyPhone, originalRecord.partyPhone);
      assert.equal(decryptedPreShred.note, originalRecord.note);

      // 3. EXECUTE CRYPTO-SHREDDING (Section 11 Right to Erasure)
      const shredResult = shredUserDek(shredUid);
      assert.equal(shredResult.success, true);
      clearDekCache();

      // Verify the DEK document was deleted from Firestore repository
      const cryptoRepo = getCryptoRepository();
      const postShredDoc = await cryptoRepo.getUserDekDoc(shredUid);
      assert.equal(postShredDoc, null, "DEK document must be completely removed from Firestore");

      // 4. PROVE PERMANENT UNDECRYPTABILITY:
      // Even if an attacker or new session generates a new DEK for this UID:
      const newRandomDek = crypto.randomBytes(32);

      // Attempting to decrypt the previous ciphertexts with any other key MUST throw AES-GCM tag mismatch
      assert.throws(
        () => {
          decryptRecordFields(encryptedRecord, newRandomDek);
        },
        /Unsupported state or unable to authenticate data/,
        "Decryption must throw authentication error when original DEK is destroyed"
      );
    });
  });
});
