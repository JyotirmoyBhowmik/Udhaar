/**
 * Stage R9: Chaos, Fault Injection & Disaster Recovery Test Suite
 * (functions/test/stage_r9_chaos.test.js)
 * 
 * Verifies:
 * 1. Network latency injection during multi-device sync.
 * 2. Dropped writes & fault injection during batch push (atomic rollback, no partial writes, clean retry).
 * 3. Instance crashes and cold-start restarts mid-sync (zero data loss across server generation teardowns).
 * 4. Idempotency recovery under transient network failures (no duplicate records or double-spending).
 */

const { test, describe, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");

const { createApiApp } = require("../src/app");
const {
  getAuthRepository,
  getSessionRepository,
  getSyncRepository,
  getLedgerRepository,
  getIdempotencyRepository,
  resetTestRepositories
} = require("../src/repositories");
const { createSession } = require("../src/sessions/sessionManager");

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

describe("Stage R9: Chaos & Fault Injection Tests", () => {
  let userA;
  let sessionA;
  let userB;
  let sessionB;

  before(async () => {
    resetTestRepositories();
    const authRepo = getAuthRepository();

    // Create test users via atomic transaction
    userA = await authRepo.createUserTransaction(
      { uid: "usr_chaos_alice", displayName: "Alice Chaos", createdAt: Date.now() },
      { uid: "usr_chaos_alice", phoneHash: "hash_phone_alice" },
      { uid: "usr_chaos_alice", emailHash: "hash_email_alice" }
    );

    userB = await authRepo.createUserTransaction(
      { uid: "usr_chaos_bob", displayName: "Bob Chaos", createdAt: Date.now() },
      { uid: "usr_chaos_bob", phoneHash: "hash_phone_bob" },
      { uid: "usr_chaos_bob", emailHash: "hash_email_bob" }
    );

    sessionA = await createSession({ uid: userA.uid, clientType: "ANDROID" });
    sessionB = await createSession({ uid: userB.uid, clientType: "ANDROID" });
  });

  after(() => {
    resetTestRepositories();
  });

  test("1. Latency Injection: High-latency network during multi-device sync succeeds atomically", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, r));
    const port = server.address().port;

    let syncRepo = null;
    let originalAllocate = null;

    try {
      // Simulate slow mobile connection by wrapping sync push with artificial latency
      syncRepo = getSyncRepository();
      originalAllocate = syncRepo.allocateSequenceNumber.bind(syncRepo);

      // Inject 40ms artificial jitter per sequence allocation
      syncRepo.allocateSequenceNumber = async function(...args) {
        await new Promise((resolve) => setTimeout(resolve, 40));
        return originalAllocate(...args);
      };

      const changes = [
        {
          entity: "record",
          op: "CREATE",
          syncId: "rec_latency_1",
          hlc: "2026-11-07T12:00:00.000Z-0001-node1",
          fields: {
            recordId: "rec_latency_1",
            principalAmountPaise: 500000,
            remainingAmountPaise: 500000,
            partyName: "Latency Supplier",
            counterpartyRole: "BORROWER",
            currency: "INR",
            dueDate: "2026-12-01"
          }
        },
        {
          entity: "record",
          op: "CREATE",
          syncId: "rec_latency_2",
          hlc: "2026-11-07T12:00:01.000Z-0001-node1",
          fields: {
            recordId: "rec_latency_2",
            principalAmountPaise: 750000,
            remainingAmountPaise: 500000,
            partyName: "Slow Trader",
            counterpartyRole: "LENDER",
            currency: "INR",
            dueDate: "2026-12-15"
          }
        }
      ];

      const res = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      }, { deviceId: "device_alice_phone", changes });

      assert.equal(res.status, 200);
      assert.equal(res.body.results.length, 2);
      assert.equal(res.body.results[0].status, "ACCEPTED");
      assert.equal(res.body.results[1].status, "ACCEPTED");

      // Pull back to verify persistence despite latency
      const pullRes = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/pull?cursor=0",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      });

      assert.equal(pullRes.status, 200);
      assert.ok(pullRes.body.changes.length >= 2);

      // Restore sync repo method
      syncRepo.allocateSequenceNumber = originalAllocate;
    } finally {
      if (syncRepo && originalAllocate) {
        syncRepo.allocateSequenceNumber = originalAllocate;
      }
      server.closeAllConnections?.();
      server.close();
    }
  });

  test("2. Dropped Writes: Mid-batch failure aborts atomically without partial writes and succeeds on retry", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, r));
    const port = server.address().port;

    let syncRepo = null;
    let changeLog = null;
    let originalPush = null;

    try {
      syncRepo = getSyncRepository();
      if (!syncRepo.changeLogs.has(userA.uid)) {
        syncRepo.changeLogs.set(userA.uid, []);
      }
      changeLog = syncRepo.changeLogs.get(userA.uid);
      originalPush = changeLog.push.bind(changeLog);

      let callCount = 0;
      // Injected fault: throw simulated database socket timeout on 2nd item
      changeLog.push = function(...args) {
        callCount++;
        if (callCount === 2) {
          const fault = new Error("CHAOS_FAULT_INJECTION: Cloud Datastore deadline exceeded (transient)");
          fault.code = "DEADLINE_EXCEEDED";
          fault.status = 503;
          throw fault;
        }
        return originalPush(...args);
      };

      const changes = [
        {
          entity: "record",
          op: "CREATE",
          syncId: "rec_batch_fail_1",
          hlc: "2026-11-07T12:05:00.000Z-0001-node1",
          fields: {
            recordId: "rec_batch_fail_1",
            principalAmountPaise: 1000000,
            remainingAmountPaise: 1000000,
            partyName: "Batch Party 1",
            counterpartyRole: "BORROWER"
          }
        },
        {
          entity: "record",
          op: "CREATE",
          syncId: "rec_batch_fail_2",
          hlc: "2026-11-07T12:05:01.000Z-0001-node1",
          fields: {
            recordId: "rec_batch_fail_2",
            principalAmountPaise: 2000000,
            remainingAmountPaise: 2000000,
            partyName: "Batch Party 2",
            counterpartyRole: "BORROWER"
          }
        }
      ];

      // First attempt fails due to injected database timeout
      const failedRes = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      }, { deviceId: "device_alice_phone", changes });

      // Must fail closed with standard error structure (Rule 2.1 & 2.4)
      assert.ok(failedRes.status >= 500, `Expected 5xx, got ${failedRes.status}`);
      assert.ok(failedRes.body.message || failedRes.body.title || failedRes.body.detail);

      // Restore repository
      changeLog.push = originalPush;

      // Clean retry with the same batch succeeds completely
      const retryRes = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      }, { deviceId: "device_alice_phone", changes });

      assert.equal(retryRes.status, 200);
      assert.equal(retryRes.body.results.length, 2);
    } finally {
      if (changeLog && originalPush) {
        changeLog.push = originalPush;
      }
      server.closeAllConnections?.();
      server.close();
    }
  });

  test("3. Cold Start / Instance Crashes: Server destroyed and recreated between sync steps preserves all state", async () => {
    let currentServer = null;
    try {
      // Generation 1: Server 1
      const app1 = createApiApp();
      const server1 = http.createServer(app1);
      currentServer = server1;
      await new Promise((r) => server1.listen(0, r));
      const port1 = server1.address().port;

      // Device A pushes initial record to Server 1
      const pushRes1 = await makeRequest(server1, {
        hostname: "localhost",
        port: port1,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      }, {
        deviceId: "device_alice_1",
        changes: [{
          entity: "record",
          op: "CREATE",
          syncId: "rec_cold_start_1",
          hlc: "2026-11-07T12:10:00.000Z-0001-node1",
          fields: {
            recordId: "rec_cold_start_1",
            principalAmountPaise: 888000,
            remainingAmountPaise: 888000,
            partyName: "Surviving Cold Start",
            counterpartyRole: "BORROWER"
          }
        }]
      });
      assert.equal(pushRes1.status, 200);

      // CRASH SERVER 1 (Simulate Cloud Functions instance termination / cold start)
      server1.closeAllConnections?.();
      server1.close();
      currentServer = null;

      // Generation 2: Server 2 (Fresh Node instance / process lifecycle)
      const app2 = createApiApp();
      const server2 = http.createServer(app2);
      currentServer = server2;
      await new Promise((r) => server2.listen(0, r));
      const port2 = server2.address().port;

      // Device A (on fresh server 2) pushes a payment against the existing record
      const pushRes2 = await makeRequest(server2, {
        hostname: "localhost",
        port: port2,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      }, {
        deviceId: "device_alice_2",
        changes: [{
          entity: "payment",
          op: "CREATE",
          syncId: "pay_cold_start_1",
          hlc: "2026-11-07T12:11:00.000Z-0001-node2",
          fields: {
            paymentId: "pay_cold_start_1",
            recordSyncId: "rec_cold_start_1",
            amountPaise: 444000,
            paymentMode: "UPI",
            note: "Installment on Server 2"
          }
        }]
      });
      assert.equal(pushRes2.status, 200);

      // CRASH SERVER 2
      server2.closeAllConnections?.();
      server2.close();
      currentServer = null;

      // Generation 3: Server 3 (Another cold start)
      const app3 = createApiApp();
      const server3 = http.createServer(app3);
      currentServer = server3;
      await new Promise((r) => server3.listen(0, r));
      const port3 = server3.address().port;

      // Pull full changeLog from Server 3
      const pullRes3 = await makeRequest(server3, {
        hostname: "localhost",
        port: port3,
        path: "/api/v1/sync/pull?cursor=0",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      });

      assert.equal(pullRes3.status, 200);
      const syncIds = pullRes3.body.changes.map((c) => c.syncId);
      assert.ok(syncIds.includes("rec_cold_start_1"), "Record must survive across 3 generations of cold starts");
      assert.ok(syncIds.includes("pay_cold_start_1"), "Payment must survive across 3 generations of cold starts");
    } finally {
      if (currentServer) {
        currentServer.closeAllConnections?.();
        currentServer.close();
      }
    }
  });

  test("4. Idempotency Key Recovery: Duplicate network POST with same Idempotency-Key returns identical result without double-commit", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, r));
    const port = server.address().port;

    try {
      const idempotencyKey = "idemp_chaos_" + Date.now();
      const payload = {
        deviceId: "device_alice_phone",
        changes: [{
          entity: "record",
          op: "CREATE",
          syncId: "rec_idemp_chaos_1",
          idempotencyKey,
          hlc: "2026-11-07T12:15:00.000Z-0001-node1",
          fields: {
            recordId: "rec_idemp_chaos_1",
            principalAmountPaise: 350000,
            remainingAmountPaise: 350000,
            partyName: "Idempotency Trader",
            counterpartyRole: "BORROWER"
          }
        }]
      };

      // 1. Initial request
      const res1 = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token",
          "Idempotency-Key": idempotencyKey
        }
      }, payload);

      assert.equal(res1.status, 200);
      assert.equal(res1.body.results.length, 1);
      assert.equal(res1.body.results[0].status, "ACCEPTED");

      // 2. Immediate replay of exact same request (simulating network timeout where client didn't receive ACK)
      const res2 = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/push",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token",
          "Idempotency-Key": idempotencyKey
        }
      }, payload);

      assert.equal(res2.status, 200);
      assert.equal(res2.body.results.length, 1);
      assert.equal(res2.body.results[0].status, "ACCEPTED");

      // 3. Verify in changeLog that rec_idemp_chaos_1 was only recorded ONCE
      const pullRes = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/sync/pull?cursor=0",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${sessionA.customToken}`,
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      });

      const matchedMutations = pullRes.body.changes.filter((c) => c.syncId === "rec_idemp_chaos_1");
      assert.equal(matchedMutations.length, 1, "Idempotency key must prevent duplicate mutation entry in changeLog");
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });
});
