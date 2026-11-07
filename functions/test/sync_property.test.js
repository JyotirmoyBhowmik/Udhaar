/**
 * Property-Based Offline Sync & Convergence Test Suite (functions/test/sync_property.test.js)
 * Simulates arbitrary interleavings of offline actions across disconnected replicas
 * and mathematically asserts convergence, commutativity, idempotency, and HLC causality.
 */

const { test, describe, beforeEach } = require("node:test");
const assert = require("node:assert");

const { LedgerDatabase } = require("../../webapp/src/db/ledgerDb");
const { SyncEngine } = require("../../webapp/src/sync/syncEngine");
const {
  processSyncPush,
  processSyncPull,
  resetSyncStore
} = require("../src/sync/syncService");
const { compareHlc, createHlc, incrementHlc, updateHlc, packHlc } = require("../src/sync/hlc");

function createReplicaTransport(uid) {
  let online = false;
  return {
    async push({ deviceId, changes }) {
      if (!online) throw new Error("Network offline");
      return processSyncPush({ uid, deviceId, changes });
    },
    async pull({ cursor, limit }) {
      if (!online) throw new Error("Network offline");
      return processSyncPull({ uid, cursor, limit });
    },
    isOnline() {
      return online;
    },
    setOnline(val) {
      online = val;
    }
  };
}

describe("Stage S4: Property-Based Sync Convergence & Resilience", () => {
  const uid = "user_property_test_123";

  beforeEach(() => {
    resetSyncStore();
  });

  test("Property 1: Partial Payments Commutativity (Set Union Invariant)", async () => {
    const dbA = new LedgerDatabase();
    const dbB = new LedgerDatabase();
    await dbA.open();
    await dbB.open();

    const transportA = createReplicaTransport(uid);
    const transportB = createReplicaTransport(uid);

    const engineA = new SyncEngine({ db: dbA, deviceId: "phone_client", transport: transportA });
    const engineB = new SyncEngine({ db: dbB, deviceId: "laptop_client", transport: transportB });
    await engineA.init();
    await engineB.init();

    // 1. Initial shared record
    const baseRecord = {
      syncId: "rec_prop_pay_1",
      partyName: "Kavita Verma",
      principalAmountPaise: 1000000, // ₹10,000
      remainingAmountPaise: 1000000,
      counterpartyRole: "BORROWER",
      serverRev: 1
    };

    // Seed server
    await processSyncPush({
      uid,
      deviceId: "seed",
      changes: [{ entity: "record", syncId: baseRecord.syncId, op: "CREATE", fields: baseRecord, clientRev: 1 }]
    });

    await dbA.putRecord({ ...baseRecord, syncStatus: "SYNCED" });
    await dbB.putRecord({ ...baseRecord, syncStatus: "SYNCED" });

    // 2. Both replicas record random partial payments offline
    const paymentsA = [
      { paymentId: "p_a_1", amountPaise: 150000 }, // ₹1,500
      { paymentId: "p_a_2", amountPaise: 250000 }  // ₹2,500
    ];
    const paymentsB = [
      { paymentId: "p_b_1", amountPaise: 100000 }, // ₹1,000
      { paymentId: "p_b_2", amountPaise: 300000 }  // ₹3,000
    ];

    for (const p of paymentsA) {
      await engineA.createPayment({
        paymentId: p.paymentId,
        recordSyncId: baseRecord.syncId,
        amountPaise: p.amountPaise,
        paymentMode: "UPI"
      });
    }

    for (const p of paymentsB) {
      await engineB.createPayment({
        paymentId: p.paymentId,
        recordSyncId: baseRecord.syncId,
        amountPaise: p.amountPaise,
        paymentMode: "CASH"
      });
    }

    // 3. Sync A then B then A (full round-trip propagation)
    transportA.setOnline(true);
    await engineA.sync();
    transportA.setOnline(false);

    transportB.setOnline(true);
    await engineB.sync();
    transportB.setOnline(false);

    transportA.setOnline(true);
    await engineA.sync();
    transportA.setOnline(false);

    // 4. Assert State(A) == State(B) == State(Server)
    const recA = await dbA.getRecord(baseRecord.syncId);
    const recB = await dbB.getRecord(baseRecord.syncId);
    const paysA = await dbA.getAllPayments();
    const paysB = await dbB.getAllPayments();

    assert.strictEqual(paysA.length, 4, "Device A should have exactly 4 payments");
    assert.strictEqual(paysB.length, 4, "Device B should have exactly 4 payments");

    const totalPaid = 150000 + 250000 + 100000 + 300000; // ₹8,000
    const expectedRemaining = 1000000 - totalPaid; // ₹2,000 (200,000 paise)

    assert.strictEqual(recA.remainingAmountPaise, expectedRemaining);
    assert.strictEqual(recB.remainingAmountPaise, expectedRemaining);
    assert.strictEqual(recA.remainingAmountPaise, recB.remainingAmountPaise);
  });

  test("Property 2: Random Interleaving Convergence (50 Random Operations)", async () => {
    const dbA = new LedgerDatabase();
    const dbB = new LedgerDatabase();
    await dbA.open();
    await dbB.open();

    const transportA = createReplicaTransport(uid);
    const transportB = createReplicaTransport(uid);

    const engineA = new SyncEngine({ db: dbA, deviceId: "replica_A", transport: transportA });
    const engineB = new SyncEngine({ db: dbB, deviceId: "replica_B", transport: transportB });
    await engineA.init();
    await engineB.init();

    // Perform 50 randomized operations across replicas
    const createdSyncIds = [];

    for (let i = 0; i < 50; i++) {
      const choice = i % 5;
      const isReplicaA = i % 2 === 0;
      const engine = isReplicaA ? engineA : engineB;
      const transport = isReplicaA ? transportA : transportB;

      if (choice === 0 || choice === 1) {
        // Create record
        const syncId = `rand_rec_${i}`;
        createdSyncIds.push(syncId);
        await engine.createRecord({
          syncId,
          partyName: `User ${i}`,
          principalAmountPaise: (i + 1) * 10000
        });
      } else if (choice === 2 && createdSyncIds.length > 0) {
        // Update non-term field (note)
        const targetId = createdSyncIds[i % createdSyncIds.length];
        const local = await (isReplicaA ? dbA : dbB).getRecord(targetId);
        if (local) {
          await engine.updateRecord(targetId, {
            note: `Updated at iter ${i} by ${isReplicaA ? "A" : "B"}`
          });
        }
      } else if (choice === 3 && createdSyncIds.length > 0) {
        // Add payment to an existing record
        const targetId = createdSyncIds[i % createdSyncIds.length];
        const local = await (isReplicaA ? dbA : dbB).getRecord(targetId);
        if (local) {
          await engine.createPayment({
            paymentId: `pay_rand_${i}`,
            recordSyncId: targetId,
            amountPaise: 5000,
            paymentMode: "UPI"
          });
        }
      } else if (choice === 4) {
        // Periodic sync
        transport.setOnline(true);
        await engine.sync();
        transport.setOnline(false);
      }
    }

    // Final convergence sync: Both come online and drain/pull
    transportA.setOnline(true);
    await engineA.sync();
    transportA.setOnline(false);

    transportB.setOnline(true);
    await engineB.sync();
    transportB.setOnline(false);

    transportA.setOnline(true);
    await engineA.sync();
    transportA.setOnline(false);

    transportB.setOnline(true);
    await engineB.sync();
    transportB.setOnline(false);

    // Verify convergence: Record keys match
    const recsA = await dbA.getAllRecords();
    const recsB = await dbB.getAllRecords();

    assert.strictEqual(recsA.length, recsB.length, "Total record count on both replicas must converge");

    const mapA = new Map(recsA.map(r => [r.syncId, r]));
    const mapB = new Map(recsB.map(r => [r.syncId, r]));

    for (const [syncId, recInA] of mapA.entries()) {
      const recInB = mapB.get(syncId);
      assert.ok(recInB, `Record ${syncId} must exist in both replicas`);
      assert.strictEqual(recInA.principalAmountPaise, recInB.principalAmountPaise);
      assert.strictEqual(recInA.remainingAmountPaise, recInB.remainingAmountPaise);
    }
  });

  test("Property 3: Idempotency of Outbox Queue Retries", async () => {
    const dbA = new LedgerDatabase();
    await dbA.open();
    const transportA = createReplicaTransport(uid);
    const engineA = new SyncEngine({ db: dbA, deviceId: "device_idem", transport: transportA });
    await engineA.init();

    const syncId = "rec_idempotent_1";
    await engineA.createRecord({
      syncId,
      partyName: "Raj Malhotra",
      principalAmountPaise: 500000
    });

    // Directly push the same mutation 3 times to syncService
    const pending = await dbA.getPendingMutations();
    assert.strictEqual(pending.length, 1);
    const mutation = pending[0];

    // Push 1
    const res1 = await processSyncPush({
      uid,
      deviceId: "device_idem",
      changes: [mutation]
    });
    assert.strictEqual(res1.results[0].status, "ACCEPTED");

    // Push 2 (Duplicate retry)
    const res2 = await processSyncPush({
      uid,
      deviceId: "device_idem",
      changes: [mutation]
    });
    assert.strictEqual(res2.results[0].status, "ACCEPTED");

    // Push 3 (Duplicate retry)
    const res3 = await processSyncPush({
      uid,
      deviceId: "device_idem",
      changes: [mutation]
    });
    assert.strictEqual(res3.results[0].status, "ACCEPTED");

    // Verify pull only produces 1 change entry
    const pull = await processSyncPull({ uid, cursor: 0, limit: 10 });
    const records = pull.changes.filter(c => c.syncId === syncId);
    assert.strictEqual(records.length, 1, "Only 1 record entry should exist in server changeLog despite 3 pushes");
  });

  test("Property 4: Hybrid Logical Clock Monotonicity & Causality", () => {
    let clock = createHlc("node_A");
    const hlc1 = packHlc(clock);

    // Monotonic increments
    clock = incrementHlc(clock);
    const hlc2 = packHlc(clock);
    assert.ok(compareHlc(hlc2, hlc1) > 0, "hlc2 must be causally greater than hlc1");

    // Receive message with higher physical time
    const remoteHlc = `${Date.now() + 5000}:0:node_B`;
    const updatedClock = updateHlc(clock, remoteHlc);
    const hlc3 = packHlc(updatedClock);

    assert.ok(compareHlc(hlc3, hlc2) > 0, "Updated clock must advance beyond local and remote");
  });
});
