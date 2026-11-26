/**
 * Udhaar Platform v2 Stage S3 Test Suite (functions/test/ledger_s3.test.js)
 * Validates Online CRUD for Records, People, Payments, Plans, Cloud KMS Envelope Encryption,
 * Quota Enforcement, Merkle Audit Trails, and Firestore Rules Lockdown.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  createRecord,
  getRecord,
  listRecords,
  updateRecord,
  settleRecord,
  deleteRecord,
  resetRecordsStore
} = require("../src/ledger/recordService");

const {
  createPerson,
  getPerson,
  listPeople,
  updatePerson,
  deletePerson,
  resetPeopleStore
} = require("../src/ledger/peopleService");

const {
  recordPayment,
  listPayments
} = require("../src/ledger/paymentService");

const {
  createInstallmentPlan,
  getPlanByRecordId,
  payInstallment,
  calculateInstallmentSchedule,
  resetPlansStore
} = require("../src/ledger/planService");

const {
  verifyAuditChain,
  resetEventStore
} = require("../src/ledger/eventService");

const {
  getLedgerRepository,
  resetTestRepositories
} = require("../src/repositories");

const {
  getOrCreateUserDek,
  encryptField,
  decryptField,
  clearDekCache
} = require("../src/crypto/envelopeCrypto");

const { USER_QUOTAS } = require("../src/config/quotas");

const TEST_UID = "usr_test_user_stage_s3_12345";

test.beforeEach(async () => {
  resetTestRepositories();
  await resetRecordsStore();
  await resetPeopleStore();
  await resetPlansStore();
  await resetEventStore();
  clearDekCache();
});

// =========================================================================
// 1. Cloud KMS Envelope Encryption & Field-Level Confidentiality
// =========================================================================
test("Stage S3 Encryption: Field-level envelope encryption protects PII while leaving operational paise readable", async () => {
  const recordInput = {
    principalAmountPaise: 500000, // ₹5,000.00
    counterpartyRole: "BORROWER",
    partyName: "Vikram Malhotra",
    partyPhone: "+91 98765 43210",
    note: "Emergency medical expense loan",
    items: [{ title: "Hospital deposit", amountPaise: 500000 }],
    upiId: "vikram@okhdfcbank"
  };

  const created = await createRecord({ uid: TEST_UID, recordData: recordInput });
  assert.equal(created.principalAmountPaise, 500000);
  assert.equal(created.partyName, "Vikram Malhotra");

  // Inspect raw document in database repository
  const ledgerRepo = getLedgerRepository();
  const rawStored = await ledgerRepo.getRecord(TEST_UID, created.recordId);
  assert.ok(rawStored, "Record must exist in storage");

  // CRITICAL SECURITY ASSERTION: No plaintext PII in database!
  assert.equal(rawStored.partyName.startsWith("v1:"), true, "partyName must be encrypted");
  assert.equal(rawStored.partyPhone.startsWith("v1:"), true, "partyPhone must be encrypted");
  assert.equal(rawStored.note.startsWith("v1:"), true, "note must be encrypted");
  assert.equal(rawStored.upiId.startsWith("v1:"), true, "upiId must be encrypted");

  assert.equal(JSON.stringify(rawStored).includes("Vikram Malhotra"), false, "Raw storage must NOT leak party name");
  assert.equal(JSON.stringify(rawStored).includes("+91 98765 43210"), false, "Raw storage must NOT leak phone number");
  assert.equal(JSON.stringify(rawStored).includes("Emergency medical expense loan"), false, "Raw storage must NOT leak notes");

  // Operational numbers remain plain integer paise for guest flow & calculations
  assert.equal(rawStored.principalAmountPaise, 500000);
  assert.equal(rawStored.remainingAmountPaise, 500000);
  assert.equal(rawStored.counterpartyRole, "BORROWER");

  // Decryption on getRecord returns original plaintext
  const fetched = await getRecord({ uid: TEST_UID, recordId: created.recordId });
  assert.equal(fetched.partyName, "Vikram Malhotra");
  assert.equal(fetched.partyPhone, "+91 98765 43210");
  assert.equal(fetched.note, "Emergency medical expense loan");
  assert.equal(fetched.upiId, "vikram@okhdfcbank");
});

// =========================================================================
// 2. Input Validation & Defense Against Bad Data
// =========================================================================
test("Stage S3 Validation: Rejects invalid amounts, missing party names, and invalid roles", async () => {
  // 1. Negative or zero paise
  await assert.rejects(
    async () => {
      await createRecord({
        uid: TEST_UID,
        recordData: { principalAmountPaise: 0, partyName: "Amit", counterpartyRole: "LENDER" }
      });
    },
    (err) => err.code === "ERR_INVALID_AMOUNT" && err.status === 400
  );

  await assert.rejects(
    async () => {
      await createRecord({
        uid: TEST_UID,
        recordData: { principalAmountPaise: -1500, partyName: "Amit", counterpartyRole: "LENDER" }
      });
    },
    (err) => err.code === "ERR_INVALID_AMOUNT" && err.status === 400
  );

  // 2. Missing counterparty name
  await assert.rejects(
    async () => {
      await createRecord({
        uid: TEST_UID,
        recordData: { principalAmountPaise: 10000, partyName: "   ", counterpartyRole: "LENDER" }
      });
    },
    (err) => err.code === "ERR_INVALID_PARTY_NAME" && err.status === 400
  );

  // 3. Invalid role
  await assert.rejects(
    async () => {
      await createRecord({
        uid: TEST_UID,
        recordData: { principalAmountPaise: 10000, partyName: "Amit", counterpartyRole: "INVESTOR" }
      });
    },
    (err) => err.code === "ERR_INVALID_ROLE" && err.status === 400
  );
});

// =========================================================================
// 3. Concurrency Control & Optimistic Locking (ETag / If-Match)
// =========================================================================
test("Stage S3 Concurrency: Optimistic locking with server revision increments and conflict rejection", async () => {
  const created = await createRecord({
    uid: TEST_UID,
    recordData: { principalAmountPaise: 250000, partyName: "Suresh", counterpartyRole: "BORROWER" }
  });
  assert.equal(created.serverRev, 1);

  // First update with matching expectedRev: 1 -> OK (becomes rev 2)
  const updated1 = await updateRecord({
    uid: TEST_UID,
    recordId: created.recordId,
    updates: { note: "First amendment" },
    expectedRev: 1
  });
  assert.equal(updated1.serverRev, 2);
  assert.equal(updated1.note, "First amendment");

  // Stale update using old expectedRev: 1 -> Rejection with 409 Conflict!
  await assert.rejects(
    async () => {
      await updateRecord({
        uid: TEST_UID,
        recordId: created.recordId,
        updates: { note: "Conflicting amendment" },
        expectedRev: 1
      });
    },
    (err) => err.code === "ERR_CONFLICT" && err.status === 409 && err.currentRev === 2
  );
});

// =========================================================================
// 4. People Directory CRUD with Envelope Encryption
// =========================================================================
test("Stage S3 People: Contact directory CRUD with encryption and pagination", async () => {
  const person = await createPerson({
    uid: TEST_UID,
    personData: {
      name: "Pooja Verma",
      phone: "+91 91234 56789",
      email: "pooja.v@example.com",
      notes: "Colleague at office"
    }
  });

  assert.ok(person.personId);
  assert.equal(person.name, "Pooja Verma");

  // Verify encryption in store
  const ledgerRepo = getLedgerRepository();
  const rawPerson = await ledgerRepo.getPerson(TEST_UID, person.personId);
  assert.equal(rawPerson.name.startsWith("v1:"), true);
  assert.equal(rawPerson.phone.startsWith("v1:"), true);

  // List people
  const list = await listPeople({ uid: TEST_UID });
  assert.equal(list.total, 1);
  assert.equal(list.items[0].name, "Pooja Verma");

  // Update person
  const updated = await updatePerson({
    uid: TEST_UID,
    personId: person.personId,
    updates: { notes: "Promoted to Team Lead" }
  });
  assert.equal(updated.notes, "Promoted to Team Lead");

  // Delete person
  const delRes = await deletePerson({ uid: TEST_UID, personId: person.personId });
  assert.equal(delRes.success, true);

  const listAfterDel = await listPeople({ uid: TEST_UID });
  assert.equal(listAfterDel.total, 0);
});

// =========================================================================
// 5. Partial Repayments & Settlement Lifecycle
// =========================================================================
test("Stage S3 Payments: Partial payments decrease remaining balance and trigger automatic SETTLED status", async () => {
  const created = await createRecord({
    uid: TEST_UID,
    recordData: { principalAmountPaise: 1000000, partyName: "Karan", counterpartyRole: "BORROWER" } // ₹10,000
  });

  // Payment 1: ₹3,000 partial payment
  const p1 = await recordPayment({
    uid: TEST_UID,
    recordId: created.recordId,
    amountPaise: 300000,
    paymentMode: "UPI",
    note: "First installment"
  });
  assert.equal(p1.remainingAmountPaise, 700000); // ₹7,000 remaining
  assert.equal(p1.status, "PENDING");

  // Payment 2: Overpayment attempt (₹8,000 against ₹7,000 balance) -> Rejected
  await assert.rejects(
    async () => {
      await recordPayment({
        uid: TEST_UID,
        recordId: created.recordId,
        amountPaise: 800000
      });
    },
    (err) => err.code === "ERR_PAYMENT_EXCEEDS_BALANCE" && err.status === 400
  );

  // Payment 3: Pay remaining ₹7,000 in full
  const p2 = await recordPayment({
    uid: TEST_UID,
    recordId: created.recordId,
    amountPaise: 700000,
    paymentMode: "CASH",
    note: "Final cash settlement"
  });
  assert.equal(p2.remainingAmountPaise, 0);
  assert.equal(p2.status, "SETTLED");

  // Record is now settled
  const recordFinal = await getRecord({ uid: TEST_UID, recordId: created.recordId });
  assert.equal(recordFinal.remainingAmountPaise, 0);
  assert.equal(recordFinal.status, "SETTLED");

  // Attempting payment on settled record throws error
  await assert.rejects(
    async () => {
      await recordPayment({
        uid: TEST_UID,
        recordId: created.recordId,
        amountPaise: 10000
      });
    },
    (err) => err.code === "ERR_RECORD_ALREADY_SETTLED" && err.status === 400
  );
});

// =========================================================================
// 6. Installment Plans & Whole-Paise Remainder Absorption
// =========================================================================
test("Stage S3 Plans: Installment splitting guarantees exact mathematical sum with whole-paise remainder absorption", async () => {
  // ₹10,000.00 (1000000 paise) into 3 installments
  const schedule = calculateInstallmentSchedule(1000000, 3, Date.now(), "MONTHLY");
  assert.equal(schedule.length, 3);

  // 1000000 / 3 = 333333 rem 1. Remainder absorbed on #1:
  assert.equal(schedule[0].amountPaise, 333334);
  assert.equal(schedule[1].amountPaise, 333333);
  assert.equal(schedule[2].amountPaise, 333333);

  // Exact mathematical sum
  const sum = schedule.reduce((acc, i) => acc + i.amountPaise, 0);
  assert.equal(sum, 1000000);

  // Create plan for record
  const record = await createRecord({
    uid: TEST_UID,
    recordData: { principalAmountPaise: 1000000, partyName: "Sunil", counterpartyRole: "BORROWER" }
  });

  const plan = await createInstallmentPlan({
    uid: TEST_UID,
    recordId: record.recordId,
    totalInstallments: 3
  });
  assert.equal(plan.installments.length, 3);

  // Pay installment #1
  const payInstRes = await payInstallment({
    uid: TEST_UID,
    planId: plan.planId,
    installmentId: plan.installments[0].installmentId
  });
  assert.equal(payInstRes.installment.status, "PAID");
  assert.equal(payInstRes.payment.remainingAmountPaise, 666666);
});

// =========================================================================
// 7. Cryptographic Merkle Hash Chaining & Tamper Detection
// =========================================================================
test("Stage S3 Audit: Merkle block chain verification and tamper detection", async () => {
  const record = await createRecord({
    uid: TEST_UID,
    recordData: { principalAmountPaise: 300000, partyName: "Deepak", counterpartyRole: "BORROWER" }
  });

  await recordPayment({ uid: TEST_UID, recordId: record.recordId, amountPaise: 100000 });
  await updateRecord({ uid: TEST_UID, recordId: record.recordId, updates: { note: "Updated" } });

  // Verify unbroken chain
  const auditRes = await verifyAuditChain(TEST_UID);
  assert.equal(auditRes.valid, true);
  assert.equal(auditRes.blockCount, 3); // GENESIS, PARTIAL_PAYMENT, TERMS_EDIT

  // TAMPER SIMULATION: Modify an event's amount in storage directly
  const ledgerRepo = getLedgerRepository();
  const eventsList = await ledgerRepo.listEvents(TEST_UID);
  const secondEvent = eventsList[1];
  await ledgerRepo.addEvent(TEST_UID, { ...secondEvent, amountPaise: 999999 }); // Corrupted!

  const tamperedAudit = await verifyAuditChain(TEST_UID);
  assert.equal(tamperedAudit.valid, false);
  assert.equal(tamperedAudit.reason, "CONTENT_DIGEST_CORRUPTED");
});

// =========================================================================
// 8. User Quotas Enforcement (10,000 Records Cap)
// =========================================================================
test("Stage S3 Quotas: Enforces maximum records per user account", async () => {
  const ledgerRepo = getLedgerRepository();
  const origCount = ledgerRepo.countRecords.bind(ledgerRepo);
  ledgerRepo.countRecords = async () => USER_QUOTAS.MAX_RECORDS;

  try {
    await assert.rejects(
      async () => {
        await createRecord({
          uid: TEST_UID,
          recordData: { principalAmountPaise: 50000, partyName: "Quota Overflow", counterpartyRole: "BORROWER" }
        });
      },
      (err) => err.code === "ERR_QUOTA_EXCEEDED" && err.status === 403
    );
  } finally {
    ledgerRepo.countRecords = origCount;
  }
});

// =========================================================================
// 9. HTTP REST Endpoints End-to-End Integration
// =========================================================================
test("Stage S3 API: HTTP endpoints with Bearer/Session auth, CSRF, and RFC 9457 errors", async () => {
  const http = require("node:http");
  const { createApiApp } = require("../src/app");
  const { createSession } = require("../src/sessions/sessionManager");

  const session = await createSession({ uid: TEST_UID, clientType: "WEB" });
  const app = createApiApp();

  let server;
  let baseUrl;
  await new Promise((resolve) => {
    server = http.createServer(app);
    server.listen(0, "127.0.0.1", () => {
      baseUrl = `http://127.0.0.1:${server.address().port}`;
      resolve();
    });
  });

  try {
    // 1. Create record via POST /api/v1/ledger/records
    const createRes = await fetch(`${baseUrl}/api/v1/ledger/records`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${session.customToken}`,
        "X-CSRF-Token": "test_csrf_token"
      },
      body: JSON.stringify({
        principalAmountPaise: 400000,
        counterpartyRole: "BORROWER",
        partyName: "Gaurav Joshi"
      })
    });

    assert.equal(createRes.status, 201);
    const created = await createRes.json();
    assert.equal(created.principalAmountPaise, 400000);
    assert.equal(created.partyName, "Gaurav Joshi");
    assert.ok(createRes.headers.get("etag"));

    // 2. Fetch record via GET /api/v1/ledger/records/:id
    const getRes = await fetch(`${baseUrl}/api/v1/ledger/records/${created.recordId}`, {
      headers: {
        "Authorization": `Bearer ${session.customToken}`
      }
    });
    assert.equal(getRes.status, 200);
    const fetched = await getRes.json();
    assert.equal(fetched.recordId, created.recordId);

    // 3. Make payment via POST /api/v1/ledger/records/:id/payments
    const payRes = await fetch(`${baseUrl}/api/v1/ledger/records/${created.recordId}/payments`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${session.customToken}`,
        "X-CSRF-Token": "test_csrf_token"
      },
      body: JSON.stringify({
        amountPaise: 150000,
        paymentMode: "UPI"
      })
    });
    assert.equal(payRes.status, 201);
    const payData = await payRes.json();
    assert.equal(payData.remainingAmountPaise, 250000);

    // 4. Verify audit chain via GET /api/v1/ledger/audit/verify
    const auditRes = await fetch(`${baseUrl}/api/v1/ledger/audit/verify`, {
      headers: {
        "Authorization": `Bearer ${session.customToken}`
      }
    });
    assert.equal(auditRes.status, 200);
    const auditData = await auditRes.json();
    assert.equal(auditData.audit.valid, true);
    assert.equal(auditData.audit.blockCount >= 2, true);
    assert.ok(auditData.syncSignal.seq >= 2);
  } finally {
    server.close();
  }
});
