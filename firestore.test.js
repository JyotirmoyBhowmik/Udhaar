const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const MALLORY_UID = "mallory_789";
const VALID_HASH = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date();
}

function buildValidSharedRecord(overrides = {}) {
  return {
    remoteId: "rec_1001",
    creatorUid: ALICE_UID,
    counterpartyUid: "",
    participantUids: [ALICE_UID],
    lenderName: "Alice",
    borrowerName: "Bob",
    creatorRole: "GIVEN",
    currency: "INR",
    principalAmount: 5000.0,
    amountPaid: 0.0,
    paymentMode: "UPI",
    isInterestEnabled: true,
    interestType: "SIMPLE",
    interestRate: 2.0,
    interestRatePeriod: "PER_MONTH",
    compoundFrequency: "MONTHLY",
    startDateMillis: 1730000000000,
    expiryDateMillis: -1,
    contentHash: VALID_HASH,
    verificationStatus: "PENDING",
    verifiedAtMillis: -1,
    disputeReason: "",
    encryptedNotes: "AES_GCM_CIPHERTEXT_SAMPLE",
    attachmentRefs: [],
    isSettled: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated user cannot read shared_records or user_profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("shared_records").doc("rec_1001").get());
  await assertFails(unauthDb.collection("user_profiles").doc(ALICE_UID).get());
});

test("2. Owner can create and read their own user_profile, but Bob cannot read Alice's profile (PII isolation)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sharma",
      phoneNumber: "+91 9876543210",
      upiId: "alice@okaxis",
      photoUri: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("user_profiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("user_profiles").doc(ALICE_UID).get());
});

test("3. Alice can create a shared_record, and Mallory (non-participant) cannot read it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("shared_records")
      .doc("rec_1001")
      .set(buildValidSharedRecord())
  );

  await assertSucceeds(
    aliceDb.collection("shared_records").doc("rec_1001").get()
  );
  await assertFails(
    malloryDb.collection("shared_records").doc("rec_1001").get()
  );
});

test("4. Bob (counterparty) can claim and confirm an unclaimed shared_record, after which both Alice and Bob can read and query it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("shared_records")
      .doc("rec_1001")
      .set(buildValidSharedRecord())
  );

  // Bob claims and confirms the record
  await assertSucceeds(
    bobDb
      .collection("shared_records")
      .doc("rec_1001")
      .update({
        counterpartyUid: BOB_UID,
        participantUids: [ALICE_UID, BOB_UID],
        verificationStatus: "CONFIRMED",
        verifiedAtMillis: 1730000500000,
        disputeReason: "",
        updatedAt: serverTimestamp(),
      })
  );

  // Both Alice and Bob can query using array-contains on participantUids
  await assertSucceeds(
    aliceDb
      .collection("shared_records")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("shared_records")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );

  // Mallory still cannot read or hijack the already-claimed record
  await assertFails(
    malloryDb.collection("shared_records").doc("rec_1001").get()
  );
  await assertFails(
    malloryDb
      .collection("shared_records")
      .doc("rec_1001")
      .update({
        counterpartyUid: MALLORY_UID,
        participantUids: [ALICE_UID, MALLORY_UID],
        verificationStatus: "CONFIRMED",
        updatedAt: serverTimestamp(),
      })
  );
});

test("5. Unfiltered list query on shared_records fails (rules are not filters)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("shared_records").get());
});

test("6. Partial payments and immutable audit trail enforce two-party isolation and block audit mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("shared_records")
      .doc("rec_1001")
      .set(
        buildValidSharedRecord({
          counterpartyUid: BOB_UID,
          participantUids: [ALICE_UID, BOB_UID],
        })
      )
  );

  // Alice records a partial payment
  await assertSucceeds(
    aliceDb
      .collection("shared_records")
      .doc("rec_1001")
      .collection("partial_payments")
      .doc("pay_1")
      .set({
        paymentId: "pay_1",
        remoteId: "rec_1001",
        recordedByUid: ALICE_UID,
        recordedByName: "Alice",
        participantUids: [ALICE_UID, BOB_UID],
        amount: 2000.0,
        paymentMode: "UPI",
        paidAtMillis: 1730001000000,
        encryptedNote: "",
        isAcknowledged: false,
        acknowledgedByUid: "",
        acknowledgedAtMillis: -1,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );

  // Bob acknowledges the partial payment
  await assertSucceeds(
    bobDb
      .collection("shared_records")
      .doc("rec_1001")
      .collection("partial_payments")
      .doc("pay_1")
      .update({
        isAcknowledged: true,
        acknowledgedByUid: BOB_UID,
        acknowledgedAtMillis: 1730002000000,
        updatedAt: serverTimestamp(),
      })
  );

  // Alice writes an audit trail entry
  const auditRef = aliceDb
    .collection("shared_records")
    .doc("rec_1001")
    .collection("audit_trail")
    .doc("evt_1");

  await assertSucceeds(
    auditRef.set({
      eventId: "evt_1",
      remoteId: "rec_1001",
      eventType: "CREATED",
      actorUid: ALICE_UID,
      actorName: "Alice",
      participantUids: [ALICE_UID, BOB_UID],
      summaryText: "Alice created and shared Udhaar record of ₹5,000",
      contentHashSnapshot: VALID_HASH,
      acknowledged: false,
      eventTimeMillis: 1730000000000,
      createdAt: serverTimestamp(),
    })
  );

  // Audit trail is strictly immutable: even Alice cannot update an existing audit event
  await assertFails(
    auditRef.update({
      summaryText: "Tampered history",
    })
  );

  // Mallory cannot read the audit trail
  await assertFails(
    malloryDb
      .collection("shared_records")
      .doc("rec_1001")
      .collection("audit_trail")
      .doc("evt_1")
      .get()
  );
});
