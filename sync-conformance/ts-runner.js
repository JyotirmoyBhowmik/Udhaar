/**
 * Sync Conformance Test Runner (sync-conformance/ts-runner.js)
 * Executes all declarative JSON sync fixtures against the client SyncEngine
 * and server syncService, verifying CRDT-Lite convergence, conflict handling,
 * and mathematical consistency across multi-replica offline scenarios.
 */

const fs = require("fs");
const path = require("path");
const assert = require("assert");

const { LedgerDatabase } = require("../webapp/src/db/ledgerDb");
const { SyncEngine } = require("../webapp/src/sync/syncEngine");
const {
  processSyncPush,
  processSyncPull,
  resetSyncStore
} = require("../functions/src/sync/syncService");

// In-process transport connecting a SyncEngine client directly to syncService
function createInProcessTransport(uid) {
  let online = false; // Start offline to isolate local mutations
  return {
    async push({ deviceId, changes }) {
      if (!online) throw new Error("Network unavailable (offline)");
      return processSyncPush({ uid, deviceId, changes });
    },
    async pull({ cursor, limit }) {
      if (!online) throw new Error("Network unavailable (offline)");
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

async function runStepFixture(fixture, fixturePath) {
  console.log(`\n▶ Running Conformance Fixture: [${fixture.name}]`);
  console.log(`  Description: ${fixture.description}`);

  const uid = `user_conf_${fixture.name}`;
  resetSyncStore();

  // Create isolated in-memory databases for Device A and Device B
  const dbA = new LedgerDatabase();
  const dbB = new LedgerDatabase();
  await dbA.open();
  await dbB.open();

  const transportA = createInProcessTransport(uid);
  const transportB = createInProcessTransport(uid);

  const engineA = new SyncEngine({ db: dbA, deviceId: "deviceA", transport: transportA });
  const engineB = new SyncEngine({ db: dbB, deviceId: "deviceB", transport: transportB });
  await engineA.init();
  await engineB.init();

  // 1. Setup initial state
  if (fixture.initialState) {
    if (Array.isArray(fixture.initialState.records)) {
      for (const rec of fixture.initialState.records) {
        // Seed into server changeLog and store
        await processSyncPush({
          uid,
          deviceId: "initial_setup",
          changes: [
            {
              entity: "record",
              syncId: rec.syncId,
              op: "CREATE",
              fields: rec,
              clientRev: 1,
              hlc: rec.hlc || "1000000000000:0:initial"
            }
          ]
        });

        // Seed into local replicas with matching serverRev
        const localCopy = { ...rec, serverRev: rec.serverRev || 1, clientRev: rec.clientRev || 1, syncStatus: "SYNCED" };
        await dbA.putRecord(localCopy);
        await dbB.putRecord(localCopy);
      }
    }
  }

  // 2. Execute actions
  for (const action of fixture.actions) {
    const engine = action.actor === "deviceA" ? engineA : engineB;
    const transport = action.actor === "deviceA" ? transportA : transportB;

    switch (action.op) {
      case "CREATE_RECORD":
        await engine.createRecord(action.payload);
        break;

      case "UPDATE_RECORD":
        await engine.updateRecord(action.payload.syncId, action.payload);
        break;

      case "DELETE_RECORD":
        await engine.deleteRecord(action.payload.syncId, action.payload);
        break;

      case "CREATE_PAYMENT":
        await engine.createPayment(action.payload);
        break;

      case "SYNC":
        transport.setOnline(true);
        await engine.sync();
        transport.setOnline(false);
        break;

      default:
        throw new Error(`Unknown action op: ${action.op}`);
    }
  }

  // 3. Verify Expected State
  const exp = fixture.expectedState;

  if (exp.recordCount !== undefined) {
    const recsA = await dbA.getAllRecords();
    const recsB = await dbB.getAllRecords();
    assert.strictEqual(recsA.length, exp.recordCount, `Device A record count mismatch: expected ${exp.recordCount}, got ${recsA.length}`);
    assert.strictEqual(recsB.length, exp.recordCount, `Device B record count mismatch: expected ${exp.recordCount}, got ${recsB.length}`);
  }

  if (Array.isArray(exp.records)) {
    for (const expRec of exp.records) {
      const recA = await dbA.getRecord(expRec.syncId);
      const recB = await dbB.getRecord(expRec.syncId);
      assert.ok(recA, `Device A missing record ${expRec.syncId}`);
      assert.ok(recB, `Device B missing record ${expRec.syncId}`);

      if (expRec.partyName) {
        assert.strictEqual(recA.partyName, expRec.partyName);
        assert.strictEqual(recB.partyName, expRec.partyName);
      }
      if (expRec.principalAmountPaise !== undefined) {
        assert.strictEqual(recA.principalAmountPaise, expRec.principalAmountPaise);
        assert.strictEqual(recB.principalAmountPaise, expRec.principalAmountPaise);
      }
      if (expRec.syncStatus) {
        assert.strictEqual(recA.syncStatus, expRec.syncStatus);
        assert.strictEqual(recB.syncStatus, expRec.syncStatus);
      }
    }
  }

  if (exp.totalPaymentsCount !== undefined) {
    const paysA = await dbA.getAllPayments();
    const paysB = await dbB.getAllPayments();
    assert.strictEqual(paysA.length, exp.totalPaymentsCount, `Device A payments count mismatch: expected ${exp.totalPaymentsCount}, got ${paysA.length}`);
    assert.strictEqual(paysB.length, exp.totalPaymentsCount, `Device B payments count mismatch: expected ${exp.totalPaymentsCount}, got ${paysB.length}`);
  }

  if (exp.expectedRemainingAmountPaise !== undefined) {
    const recA = await dbA.getRecord(exp.recordSyncId);
    const recB = await dbB.getRecord(exp.recordSyncId);
    assert.ok(recA, `Record ${exp.recordSyncId} missing on device A`);
    assert.ok(recB, `Record ${exp.recordSyncId} missing on device B`);
    assert.strictEqual(recA.remainingAmountPaise, exp.expectedRemainingAmountPaise, `Device A remaining amount mismatch: expected ${exp.expectedRemainingAmountPaise}, got ${recA.remainingAmountPaise}`);
    assert.strictEqual(recB.remainingAmountPaise, exp.expectedRemainingAmountPaise, `Device B remaining amount mismatch: expected ${exp.expectedRemainingAmountPaise}, got ${recB.remainingAmountPaise}`);
  }

  if (exp.deviceBStatus) {
    const recB = await dbB.getRecord(exp.recordSyncId);
    assert.ok(recB, `Record ${exp.recordSyncId} missing on device B`);
    assert.strictEqual(recB.syncStatus, exp.deviceBStatus, `Device B syncStatus expected ${exp.deviceBStatus}, got ${recB.syncStatus}`);
    if (exp.conflictServerAmount !== undefined) {
      assert.strictEqual(recB.conflictDetails.serverVersion.principalAmountPaise, exp.conflictServerAmount);
    }
    if (exp.conflictClientAmount !== undefined) {
      assert.strictEqual(recB.conflictDetails.clientVersion.principalAmountPaise, exp.conflictClientAmount);
    }
  }

  if (exp.isDeleted !== undefined) {
    const recA = await dbA.getRecord(exp.recordSyncId);
    const recB = await dbB.getRecord(exp.recordSyncId);
    assert.ok(recA, `Record ${exp.recordSyncId} missing on device A`);
    assert.ok(recB, `Record ${exp.recordSyncId} missing on device B`);
    assert.strictEqual(Boolean(recA.deletedAtMs), exp.isDeleted, `Device A isDeleted expected ${exp.isDeleted}, got ${Boolean(recA.deletedAtMs)}`);
    assert.strictEqual(Boolean(recB.deletedAtMs), exp.isDeleted, `Device B isDeleted expected ${exp.isDeleted}, got ${Boolean(recB.deletedAtMs)}`);
  }

  if (exp.note !== undefined) {
    const recA = await dbA.getRecord(exp.recordSyncId);
    const recB = await dbB.getRecord(exp.recordSyncId);
    assert.strictEqual(recA.note, exp.note, `Device A note mismatch`);
    assert.strictEqual(recB.note, exp.note, `Device B note mismatch`);
  }

  if (exp.finalNote !== undefined) {
    const recA = await dbA.getRecord(exp.recordSyncId);
    const recB = await dbB.getRecord(exp.recordSyncId);
    assert.strictEqual(recA.note, exp.finalNote, `Device A finalNote mismatch: expected "${exp.finalNote}", got "${recA.note}"`);
    assert.strictEqual(recB.note, exp.finalNote, `Device B finalNote mismatch: expected "${exp.finalNote}", got "${recB.note}"`);
  }

  console.log(`  ✔ Passed: [${fixture.name}] converged successfully.`);
  return true;
}

async function runScenarioFixture(fixture, fixturePath) {
  const baseName = path.basename(fixturePath);
  console.log(`\n▶ Running Scenario Fixture: [${baseName}]`);
  console.log(`  Scenario: ${fixture.scenario}`);

  const uid = `user_scen_${baseName.replace(/\.json$/, "")}`;
  resetSyncStore();

  const dbA = new LedgerDatabase();
  const dbB = new LedgerDatabase();
  await dbA.open();
  await dbB.open();

  const transportA = createInProcessTransport(uid);
  const transportB = createInProcessTransport(uid);

  const engineA = new SyncEngine({ db: dbA, deviceId: "deviceA", transport: transportA });
  const engineB = new SyncEngine({ db: dbB, deviceId: "deviceB", transport: transportB });
  await engineA.init();
  await engineB.init();

  // Setup base record
  if (fixture.baseRecord) {
    const rec = fixture.baseRecord;
    await processSyncPush({
      uid,
      deviceId: "initial_setup",
      changes: [
        {
          entity: "record",
          syncId: rec.syncId,
          op: "CREATE",
          fields: rec,
          clientRev: rec.clientRevision || 1,
          hlc: "1000000000000:0:initial"
        }
      ]
    });
    const localCopy = { ...rec, serverRev: 1, clientRev: rec.clientRevision || 1, syncStatus: "SYNCED" };
    await dbA.putRecord(localCopy);
    await dbB.putRecord(localCopy);
  }

  // Device A mutation
  if (fixture.deviceA && fixture.deviceA.mutation) {
    const mut = fixture.deviceA.mutation;
    if (mut.op === "INSERT_PAYMENT") {
      await engineA.createPayment({
        paymentId: mut.payment.syncId,
        recordSyncId: fixture.baseRecord.syncId,
        amountPaise: mut.payment.amountPaise,
        paymentMode: mut.payment.paymentMode,
        timestampMs: mut.payment.timestampMs
      });
    } else if (mut.op === "UPDATE_TERMS") {
      await engineA.updateRecord(fixture.baseRecord.syncId, {
        ...mut.fields,
        clientRev: fixture.baseRecord.clientRevision || 1
      });
    }
  }

  // Device B mutation
  if (fixture.deviceB && fixture.deviceB.mutation) {
    const mut = fixture.deviceB.mutation;
    if (mut.op === "INSERT_PAYMENT") {
      await engineB.createPayment({
        paymentId: mut.payment.syncId,
        recordSyncId: fixture.baseRecord.syncId,
        amountPaise: mut.payment.amountPaise,
        paymentMode: mut.payment.paymentMode,
        timestampMs: mut.payment.timestampMs
      });
    } else if (mut.op === "UPDATE_TERMS") {
      await engineB.updateRecord(fixture.baseRecord.syncId, {
        ...mut.fields,
        clientRev: fixture.baseRecord.clientRevision || 1
      });
    }
  }

  // Sync Device A then Device B then Device A
  transportA.setOnline(true);
  await engineA.sync();
  transportA.setOnline(false);

  transportB.setOnline(true);
  await engineB.sync();
  transportB.setOnline(false);

  transportA.setOnline(true);
  await engineA.sync();
  transportA.setOnline(false);

  // Assert expected outcome
  const out = fixture.expectedOutcome;
  if (out.mergedPaymentsCount !== undefined) {
    const paysA = await dbA.getAllPayments();
    const paysB = await dbB.getAllPayments();
    assert.strictEqual(paysA.length, out.mergedPaymentsCount);
    assert.strictEqual(paysB.length, out.mergedPaymentsCount);
  }

  if (out.remainingAmountPaise !== undefined) {
    const recA = await dbA.getRecord(fixture.baseRecord.syncId);
    const recB = await dbB.getRecord(fixture.baseRecord.syncId);
    assert.strictEqual(recA.remainingAmountPaise, out.remainingAmountPaise);
    assert.strictEqual(recB.remainingAmountPaise, out.remainingAmountPaise);
  }

  if (out.status === "CONFLICT") {
    const recB = await dbB.getRecord(fixture.baseRecord.syncId);
    assert.strictEqual(recB.syncStatus, "CONFLICT");
  }

  console.log(`  ✔ Passed: [${baseName}] scenario verified successfully.`);
  return true;
}

async function runFixture(filePath) {
  const content = fs.readFileSync(filePath, "utf-8");
  const fixture = JSON.parse(content);
  if (fixture.actions) {
    return runStepFixture(fixture, filePath);
  } else if (fixture.scenario) {
    return runScenarioFixture(fixture, filePath);
  } else {
    throw new Error(`Unrecognized fixture format in ${filePath}`);
  }
}

async function runAllFixtures() {
  const fixturesDir = path.join(__dirname, "fixtures");
  const files = fs.readdirSync(fixturesDir).filter(f => f.endsWith(".json")).sort();

  console.log(`Found ${files.length} Conformance Fixtures in ${fixturesDir}:`);
  for (const f of files) {
    console.log(` - ${f}`);
  }

  let passed = 0;
  for (const file of files) {
    const filePath = path.join(fixturesDir, file);
    await runFixture(filePath);
    passed++;
  }

  console.log(`\n======================================================`);
  console.log(`All ${passed}/${files.length} Sync Conformance Fixtures PASSED!`);
  console.log(`======================================================\n`);
}

if (require.main === module) {
  runAllFixtures().catch(err => {
    console.error("Conformance test failed:", err);
    process.exit(1);
  });
}

module.exports = { runAllFixtures, runFixture };
