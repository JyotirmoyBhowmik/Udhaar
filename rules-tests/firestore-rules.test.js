const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");
const path = require("node:path");

let testEnv;
const PROJECT_ID = "demo-udhaar-spark";
const OWNER_UID = "alice_owner";
const ATTACKER_UID = "mallory_attacker";
const GUEST_ANON_UID = "guest_anon_uid";
const GUEST_GOOGLE_UID = "guest_google_uid";
const BORROWER_UID = "bob_borrower";
const BORROWER_EMAIL = "borrower@gmail.com";
const WRONG_EMAIL = "wrong@gmail.com";

function sha256Hex(str) {
  return crypto.createHash("sha256").update(str.toLowerCase()).digest("hex").toLowerCase();
}

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rulesPath = fs.existsSync("firestore.rules")
    ? "firestore.rules"
    : path.resolve(__dirname, "../firestore.rules");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(rulesPath, "utf8"),
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

function getStrangerDb() {
  return testEnv.unauthenticatedContext().firestore();
}

function getOwnerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: "alice@udhaar.test",
      email_verified: true,
      firebase: { sign_in_provider: "password" },
    })
    .firestore();
}

function getUnverifiedOwnerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: "alice@udhaar.test",
      email_verified: false,
      firebase: { sign_in_provider: "password" },
    })
    .firestore();
}

function getAttackerDb() {
  return testEnv
    .authenticatedContext(ATTACKER_UID, {
      email: "attacker@udhaar.test",
      email_verified: true,
      firebase: { sign_in_provider: "password" },
    })
    .firestore();
}

function getAnonGuestDb() {
  return testEnv
    .authenticatedContext(GUEST_ANON_UID, {
      firebase: { sign_in_provider: "anonymous" },
    })
    .firestore();
}

function getGoogleGuestDb(email = BORROWER_EMAIL, verified = true) {
  return testEnv
    .authenticatedContext(GUEST_GOOGLE_UID, {
      email,
      email_verified: verified,
      firebase: { sign_in_provider: "google.com" },
    })
    .firestore();
}

function getBorrowerDb() {
  return testEnv
    .authenticatedContext(BORROWER_UID, {
      email: "bob@udhaar.test",
      email_verified: true,
      firebase: { sign_in_provider: "password" },
    })
    .firestore();
}

function getUnverifiedBorrowerDb() {
  return testEnv
    .authenticatedContext(BORROWER_UID, {
      email: "bob@udhaar.test",
      email_verified: false,
      firebase: { sign_in_provider: "password" },
    })
    .firestore();
}

function getVerifiedGuestDb() {
  return testEnv
    .authenticatedContext(GUEST_GOOGLE_UID, {
      email: BORROWER_EMAIL,
      email_verified: true,
      firebase: { sign_in_provider: "google.com" },
    })
    .firestore();
}

// =========================================================================
// PART A: Per-User Spark Collections
// (users, records, people, events, plans, attachments, meta/syncSignal)
// Each collection tests: stranger, other user, unverified email, extra fields,
// oversized strings / withinSize budget, wrong types, rev mismatch, valid CRUD.
// =========================================================================

test("A1. /users/{uid}: auth, allow-list, sizes, types, withinSize & rev monotonicity", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db) => db.collection("users").doc(OWNER_UID);
  const now = Date.now();
  const validUser = {
    ownerUid: OWNER_UID,
    uid: OWNER_UID,
    email: "alice@udhaar.test",
    displayName: "Alice",
    currency: "INR",
    phoneVerified: false,
    rev: 1,
    createdAtMs: now,
    updatedAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validUser));
  await assertFails(ref(attackerDb).set(validUser));
  await assertFails(ref(unverifiedDb).set(validUser));

  // Extra field -> DENIED
  await assertFails(ref(ownerDb).set({ ...validUser, isAdmin: true }));

  // Oversized individual string (displayName > 512 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validUser, displayName: "A".repeat(513) }));

  // Aggregate withinSize(1536) breach even when individual fields are within caps -> DENIED
  await assertFails(
    ref(ownerDb).set({
      ...validUser,
      photoUrl: "https://example.com/" + "a".repeat(950),
      displayName: "B".repeat(500),
      email: "c".repeat(200) + "@udhaar.test",
    })
  );

  // Wrong types / enum violations -> DENIED
  await assertFails(ref(ownerDb).set({ ...validUser, updatedAtMs: "not-an-int" }));
  await assertFails(ref(ownerDb).set({ ...validUser, currency: "USD" }));
  await assertFails(ref(ownerDb).set({ ...validUser, phoneVerified: true }));

  // Rev mismatch on create (rev != 1) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validUser, rev: 2 }));
  await assertFails(ref(ownerDb).set({ ...validUser, rev: 0 }));

  // Valid create (rev == 1) -> OK
  await assertSucceeds(ref(ownerDb).set(validUser));

  // Stranger, other user, unverified read/delete -> DENIED
  await assertFails(ref(strangerDb).get());
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(unverifiedDb).get());
  await assertFails(ref(attackerDb).delete());

  // Update with rev mismatch (same rev=1, jump rev=3, float rev) -> DENIED
  await assertFails(ref(ownerDb).update({ displayName: "Alice 2", rev: 1, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ displayName: "Alice 2", rev: 3, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ displayName: "Alice 2", rev: 2.5, updatedAtMs: now + 10 }));

  // Update with valid rev == resource.rev + 1 -> OK
  await assertSucceeds(ref(ownerDb).update({ displayName: "Alice Updated", rev: 2, updatedAtMs: now + 20 }));

  // Owner read & delete -> OK
  await assertSucceeds(ref(ownerDb).get());
  await assertSucceeds(ref(ownerDb).delete());
});

test("A2. /users/{uid}/records/{id}: auth, allow-list, sizes, withinSize, types & rev monotonicity", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, id = "rec_1") =>
    db.collection("users").doc(OWNER_UID).collection("records").doc(id);
  const now = Date.now();
  const validRec = {
    id: "rec_1",
    recordId: "rec_1",
    ownerUid: OWNER_UID,
    uid: OWNER_UID,
    partyName: "Rohan",
    principalAmountPaise: 500000,
    amountPaidPaise: 0,
    currency: "INR",
    direction: "GIVEN",
    status: "PENDING",
    note: "Medical advance",
    rev: 1,
    createdAtMs: now,
    updatedAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validRec));
  await assertFails(ref(attackerDb).set(validRec));
  await assertFails(ref(unverifiedDb).set(validRec));

  // Extra field -> DENIED
  await assertFails(ref(ownerDb).set({ ...validRec, secretBackdoor: "x" }));

  // Oversized individual string (note > 1000 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validRec, note: "N".repeat(1001) }));

  // Aggregate withinSize(2048) breach (note=950 + notes=950 + overhead > 2048) -> DENIED
  await assertFails(
    ref(ownerDb).set({
      ...validRec,
      note: "A".repeat(950),
      notes: "B".repeat(950),
      partyName: "C".repeat(180),
      hlc: "H".repeat(100),
    })
  );

  // Wrong types / range / enum violations -> DENIED
  await assertFails(ref(ownerDb).set({ ...validRec, principalAmountPaise: "500000" }));
  await assertFails(ref(ownerDb).set({ ...validRec, principalAmountPaise: -100 }));
  await assertFails(ref(ownerDb).set({ ...validRec, principalAmountPaise: 500.5 }));
  await assertFails(ref(ownerDb).set({ ...validRec, currency: "EUR" }));
  await assertFails(ref(ownerDb).set({ ...validRec, direction: "STOLEN" }));
  await assertFails(ref(ownerDb).set({ ...validRec, isSettled: "yes" }));

  // Rev mismatch on create -> DENIED
  await assertFails(ref(ownerDb).set({ ...validRec, rev: 2 }));
  await assertFails(ref(ownerDb).set({ ...validRec, rev: "1" }));

  // Valid create -> OK
  await assertSucceeds(ref(ownerDb).set(validRec));

  // Stranger, attacker, unverified reads/writes/deletes -> DENIED
  await assertFails(ref(strangerDb).get());
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(unverifiedDb).get());
  await assertFails(ref(attackerDb).update({ note: "hacked", rev: 2, updatedAtMs: now + 5 }));
  await assertFails(ref(attackerDb).delete());

  // Update with rev mismatch -> DENIED
  await assertFails(ref(ownerDb).update({ note: "stale", rev: 1, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ note: "skip", rev: 3, updatedAtMs: now + 10 }));

  // Update attempting ownerUid hijack -> DENIED
  await assertFails(ref(ownerDb).update({ ownerUid: ATTACKER_UID, rev: 2, updatedAtMs: now + 10 }));

  // Valid update with rev == 2 -> OK
  await assertSucceeds(ref(ownerDb).update({ note: "Updated note", rev: 2, updatedAtMs: now + 20 }));

  // Valid owner delete -> OK
  await assertSucceeds(ref(ownerDb).delete());
});

test("A3. /users/{uid}/people/{id}: auth, allow-list, sizes, types & rev monotonicity", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, id = "person_1") =>
    db.collection("users").doc(OWNER_UID).collection("people").doc(id);
  const now = Date.now();
  const validPerson = {
    id: "person_1",
    personId: "person_1",
    ownerUid: OWNER_UID,
    name: "Priya Sharma",
    phoneNumber: "+919876543210",
    upiId: "priya@upi",
    note: "Neighbor",
    rev: 1,
    createdAtMs: now,
    updatedAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validPerson));
  await assertFails(ref(attackerDb).set(validPerson));
  await assertFails(ref(unverifiedDb).set(validPerson));

  // Extra field -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPerson, creditScore: 999 }));

  // Oversized string (name > 120 bytes, note > 500 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPerson, name: "P".repeat(121) }));
  await assertFails(ref(ownerDb).set({ ...validPerson, note: "X".repeat(501) }));

  // Aggregate withinSize(1536) breach -> DENIED
  await assertFails(
    ref(ownerDb).set({
      ...validPerson,
      note: "A".repeat(490),
      notes: "B".repeat(490),
      email: "c".repeat(240) + "@x.in",
      upiId: "d".repeat(120),
      displayName: "E".repeat(110),
    })
  );

  // Wrong types / missing required name -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPerson, name: "" }));
  await assertFails(ref(ownerDb).set({ ...validPerson, name: 12345 }));
  await assertFails(ref(ownerDb).set({ ...validPerson, isArchived: "false" }));

  // Rev mismatch on create -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPerson, rev: 5 }));

  // Valid create -> OK
  await assertSucceeds(ref(ownerDb).set(validPerson));

  // Update rev mismatch -> DENIED
  await assertFails(ref(ownerDb).update({ name: "Priya S.", rev: 1, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ name: "Priya S.", rev: 4, updatedAtMs: now + 10 }));

  // Valid update rev=2 -> OK
  await assertSucceeds(ref(ownerDb).update({ name: "Priya S.", rev: 2, updatedAtMs: now + 20 }));

  // Other user read/delete -> DENIED, owner delete -> OK
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(attackerDb).delete());
  await assertSucceeds(ref(ownerDb).delete());
});

test("A4. /users/{uid}/events/{id}: create-only append-only log, schema checks, NO update/delete", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, id = "evt_1") =>
    db.collection("users").doc(OWNER_UID).collection("events").doc(id);
  const now = Date.now();
  const validEvent = {
    id: "evt_1",
    eventId: "evt_1",
    ownerUid: OWNER_UID,
    recordId: "rec_1",
    eventType: "PAYMENT",
    amountPaise: 25000,
    paymentMode: "UPI",
    referenceNumber: "UTR123456",
    note: "First installment",
    rev: 1,
    createdAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validEvent));
  await assertFails(ref(attackerDb).set(validEvent));
  await assertFails(ref(unverifiedDb).set(validEvent));

  // Extra field -> DENIED
  await assertFails(ref(ownerDb).set({ ...validEvent, forgedFlag: true }));

  // Oversized string (note > 500 bytes, referenceNumber > 64 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validEvent, note: "Z".repeat(501) }));
  await assertFails(ref(ownerDb).set({ ...validEvent, referenceNumber: "R".repeat(65) }));

  // Wrong types / invalid eventType / invalid paymentMode / rev != 1 -> DENIED
  await assertFails(ref(ownerDb).set({ ...validEvent, eventType: "DROP_TABLE" }));
  await assertFails(ref(ownerDb).set({ ...validEvent, paymentMode: "CRYPTO" }));
  await assertFails(ref(ownerDb).set({ ...validEvent, amountPaise: "25000" }));
  await assertFails(ref(ownerDb).set({ ...validEvent, createdAtMs: "now" }));
  await assertFails(ref(ownerDb).set({ ...validEvent, rev: 2 }));

  // Valid create -> OK
  await assertSucceeds(ref(ownerDb).set(validEvent));

  // Owner read -> OK; stranger / attacker / unverified read -> DENIED
  await assertSucceeds(ref(ownerDb).get());
  await assertFails(ref(strangerDb).get());
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(unverifiedDb).get());

  // Update and delete of events MUST ALWAYS FAIL (even for verified owner!)
  await assertFails(ref(ownerDb).update({ note: "tampered" }));
  await assertFails(ref(ownerDb).set({ ...validEvent, note: "overwrite" }));
  await assertFails(ref(ownerDb).delete());
  await assertFails(ref(attackerDb).delete());
});

test("A5. /users/{uid}/plans/{id}: auth, allow-list, sizes, types & rev monotonicity", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, id = "plan_1") =>
    db.collection("users").doc(OWNER_UID).collection("plans").doc(id);
  const now = Date.now();
  const validPlan = {
    id: "plan_1",
    planId: "plan_1",
    ownerUid: OWNER_UID,
    recordId: "rec_1",
    frequency: "MONTHLY",
    installmentCount: 6,
    installmentAmountPaise: 100000,
    totalAmountPaise: 600000,
    status: "ACTIVE",
    rev: 1,
    createdAtMs: now,
    updatedAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validPlan));
  await assertFails(ref(attackerDb).set(validPlan));
  await assertFails(ref(unverifiedDb).set(validPlan));

  // Extra field -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPlan, extraField: 1 }));

  // Oversized string (note > 500 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPlan, note: "M".repeat(501) }));

  // Wrong types / invalid enum / out-of-range installmentCount -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPlan, frequency: "HOURLY" }));
  await assertFails(ref(ownerDb).set({ ...validPlan, installmentCount: 0 }));
  await assertFails(ref(ownerDb).set({ ...validPlan, installmentCount: 500 }));
  await assertFails(ref(ownerDb).set({ ...validPlan, installmentAmountPaise: "100000" }));

  // Rev mismatch on create -> DENIED
  await assertFails(ref(ownerDb).set({ ...validPlan, rev: 3 }));

  // Valid create -> OK
  await assertSucceeds(ref(ownerDb).set(validPlan));

  // Rev mismatch on update -> DENIED
  await assertFails(ref(ownerDb).update({ status: "PAUSED", rev: 1, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ status: "PAUSED", rev: 3, updatedAtMs: now + 10 }));

  // Valid update rev=2 -> OK
  await assertSucceeds(ref(ownerDb).update({ status: "PAUSED", rev: 2, updatedAtMs: now + 20 }));

  // Attacker denied, owner delete OK
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(attackerDb).delete());
  await assertSucceeds(ref(ownerDb).delete());
});

test("A6. /users/{uid}/attachments/{id}: metadata-only allow-list, sizes, types & rev monotonicity", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, id = "att_1") =>
    db.collection("users").doc(OWNER_UID).collection("attachments").doc(id);
  const now = Date.now();
  const validAtt = {
    id: "att_1",
    attachmentId: "att_1",
    ownerUid: OWNER_UID,
    recordId: "rec_1",
    filename: "receipt_2026.webp",
    mimeType: "image/webp",
    sizeBytes: 42000,
    sha256: "a".repeat(64),
    rev: 1,
    createdAtMs: now,
    updatedAtMs: now,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validAtt));
  await assertFails(ref(attackerDb).set(validAtt));
  await assertFails(ref(unverifiedDb).set(validAtt));

  // Extra field (e.g., smuggling raw binary base64 blob into metadata doc) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validAtt, dataB64: "AAAA" }));

  // Oversized string (filename > 255 bytes, sha256 > 64 bytes) -> DENIED
  await assertFails(ref(ownerDb).set({ ...validAtt, filename: "f".repeat(256) }));
  await assertFails(ref(ownerDb).set({ ...validAtt, sha256: "a".repeat(65) }));

  // Wrong types / disallowed mimeType / oversized sizeBytes -> DENIED
  await assertFails(ref(ownerDb).set({ ...validAtt, mimeType: "application/x-msdownload" }));
  await assertFails(ref(ownerDb).set({ ...validAtt, sizeBytes: -1 }));
  await assertFails(ref(ownerDb).set({ ...validAtt, sizeBytes: 20 * 1024 * 1024 }));
  await assertFails(ref(ownerDb).set({ ...validAtt, sizeBytes: "42000" }));

  // Rev mismatch on create -> DENIED
  await assertFails(ref(ownerDb).set({ ...validAtt, rev: 2 }));

  // Valid create -> OK
  await assertSucceeds(ref(ownerDb).set(validAtt));

  // Rev mismatch on update -> DENIED
  await assertFails(ref(ownerDb).update({ note: "verified", rev: 1, updatedAtMs: now + 10 }));
  await assertFails(ref(ownerDb).update({ note: "verified", rev: 4, updatedAtMs: now + 10 }));

  // Valid update rev=2 -> OK
  await assertSucceeds(ref(ownerDb).update({ note: "verified", rev: 2, updatedAtMs: now + 20 }));

  // Attacker denied, owner delete OK
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(attackerDb).delete());
  await assertSucceeds(ref(ownerDb).delete());
});

test("A7. /users/{uid}/meta/syncSignal & catch-all denied collections", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db) => db.collection("users").doc(OWNER_UID).collection("meta").doc("syncSignal");
  const now = Date.now();
  const validSignal = {
    ownerUid: OWNER_UID,
    updatedAtMs: now,
    deviceId: "pixel_6_prod",
    rev: 1,
  };

  // Stranger, other user, unverified email -> DENIED
  await assertFails(ref(strangerDb).set(validSignal));
  await assertFails(ref(attackerDb).set(validSignal));
  await assertFails(ref(unverifiedDb).set(validSignal));

  // Extra field & oversized deviceId -> DENIED
  await assertFails(ref(ownerDb).set({ ...validSignal, extra: "no" }));
  await assertFails(ref(ownerDb).set({ ...validSignal, deviceId: "D".repeat(129) }));

  // Valid create -> OK, rev mismatch update -> DENIED, valid rev=2 update -> OK
  await assertSucceeds(ref(ownerDb).set(validSignal));
  await assertFails(ref(ownerDb).update({ updatedAtMs: now + 10, rev: 1 }));
  await assertSucceeds(ref(ownerDb).update({ updatedAtMs: now + 10, rev: 2 }));
  await assertSucceeds(ref(ownerDb).delete());

  // Server-mediated collections denied by catch-all
  await assertFails(ownerDb.collection("webLinks").doc("wl_1").set({ ownerUid: OWNER_UID }));
  await assertFails(ownerDb.collection("chat").doc("c_1").set({ ownerUid: OWNER_UID }));
  // Malformed / non-conforming shared_records document denied
  await assertFails(ownerDb.collection("shared_records").doc("sr_1").set({ ownerUid: OWNER_UID }));
});

test("A8. /users/{uid}/blocked_users/{targetUid}: CRUD, self-block deny, unverified deny & fuzz checks", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const ref = (db, target = BORROWER_UID) =>
    db.collection("users").doc(OWNER_UID).collection("blocked_users").doc(target);
  const validBlock = {
    targetUid: BORROWER_UID,
    isMuted: true,
    isBlocked: true,
    isReported: false,
    reportReason: "Repeated spam messages",
    updatedAt: new Date(),
  };

  // Stranger, attacker, unverified -> DENIED
  await assertFails(ref(strangerDb).set(validBlock));
  await assertFails(ref(attackerDb).set(validBlock));
  await assertFails(ref(unverifiedDb).set(validBlock));

  // Self-block -> DENIED
  await assertFails(ref(ownerDb, OWNER_UID).set({ ...validBlock, targetUid: OWNER_UID }));

  // Fuzz: extra field, oversized reason, wrong types, mismatched targetUid -> DENIED
  await assertFails(ref(ownerDb).set({ ...validBlock, extraField: true }));
  await assertFails(ref(ownerDb).set({ ...validBlock, reportReason: "R".repeat(301) }));
  await assertFails(ref(ownerDb).set({ ...validBlock, isBlocked: "yes" }));
  await assertFails(ref(ownerDb).set({ ...validBlock, isMuted: 1 }));
  await assertFails(ref(ownerDb).set({ ...validBlock, targetUid: ATTACKER_UID }));
  await assertFails(ref(ownerDb).set({ ...validBlock, updatedAt: Date.now() }));

  // Valid create, get, list, update, delete -> OK
  await assertSucceeds(ref(ownerDb).set(validBlock));
  await assertSucceeds(ref(ownerDb).get());
  await assertSucceeds(ownerDb.collection("users").doc(OWNER_UID).collection("blocked_users").get());
  await assertFails(ref(attackerDb).get());
  await assertFails(ref(unverifiedDb).get());
  await assertSucceeds(ref(ownerDb).update({ isBlocked: false, isReported: true, updatedAt: new Date() }));
  await assertFails(ref(attackerDb).delete());
  await assertSucceeds(ref(ownerDb).delete());
});

// =========================================================================
// PART B: Guest Capability Links, Actions & Payment Proofs (Spark Mode)
// =========================================================================

test("B1. GuestLinks: owner creates OK, unverified owner DENIED, attacker takeover DENIED", async () => {
  const ownerDb = getOwnerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const attackerDb = getAttackerDb();

  const linkData = {
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_100",
    payableAmountPaise: 50000,
    paidPaise: 0,
    principalPaise: 50000,
    expiresAtMs: Date.now() + 86400000,
    allowedActions: ["CONFIRM", "DISPUTE", "PROPOSE_DATE", "CLAIM_PAYMENT"],
    verificationLevel: "PRIVATE_LINK",
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
  };

  await assertFails(unverifiedDb.collection("guestLinks").doc("link_unverified").set(linkData));
  await assertSucceeds(ownerDb.collection("guestLinks").doc("link_takeover_test").set(linkData));
  await assertFails(
    attackerDb.collection("guestLinks").doc("link_takeover_test").update({
      ownerUid: ATTACKER_UID,
      payableAmountPaise: 1,
    })
  );
});

test("B2. GuestLinks: whatsappOtp or otp field -> DENIED", async () => {
  const ownerDb = getOwnerDb();
  const base = {
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_otp_1",
    payableAmountPaise: 10000,
    expiresAtMs: Date.now() + 86400000,
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
  };
  await assertFails(ownerDb.collection("guestLinks").doc("l_wotp").set({ ...base, whatsappOtp: "123456" }));
  await assertFails(ownerDb.collection("guestLinks").doc("l_otp").set({ ...base, otp: "654321" }));
});

test("B3. GuestLinks: verified guest get OK, anon/unverified DENIED, list own OK, expired/revoked DENIED", async () => {
  const ownerDb = getOwnerDb();
  const anonGuestDb = getAnonGuestDb();
  const unverifiedGuestDb = getGoogleGuestDb(BORROWER_EMAIL, false);
  const verifiedGuestDb = getVerifiedGuestDb();
  const strangerDb = getStrangerDb();
  const now = Date.now();

  await ownerDb.collection("guestLinks").doc("link_active").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_active",
    payableAmountPaise: 20000,
    expiresAtMs: now + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM", "DISPUTE", "PROPOSE_DATE"],
  });

  await ownerDb.collection("guestLinks").doc("link_expired").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_expired",
    payableAmountPaise: 20000,
    expiresAtMs: now - 1000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  });

  await ownerDb.collection("guestLinks").doc("link_revoked").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_revoked",
    payableAmountPaise: 20000,
    expiresAtMs: now + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  });
  await assertSucceeds(
    ownerDb.collection("guestLinks").doc("link_revoked").update({
      status: "REVOKED",
      revokedAtMs: now,
    })
  );

  await assertFails(strangerDb.collection("guestLinks").doc("link_active").get());
  await assertFails(anonGuestDb.collection("guestLinks").doc("link_active").get());
  await assertFails(unverifiedGuestDb.collection("guestLinks").doc("link_active").get());
  await assertSucceeds(verifiedGuestDb.collection("guestLinks").doc("link_active").get());
  await assertFails(verifiedGuestDb.collection("guestLinks").get());
  await assertFails(verifiedGuestDb.collection("guestLinks").doc("link_expired").get());
  await assertFails(verifiedGuestDb.collection("guestLinks").doc("link_revoked").get());

  await assertSucceeds(
    ownerDb.collection("guestLinks").where("ownerUid", "==", OWNER_UID).limit(50).get()
  );
  await assertFails(getUnverifiedOwnerDb().collection("guestLinks").doc("link_revoked").delete());
  await assertSucceeds(ownerDb.collection("guestLinks").doc("link_revoked").delete());
});

test("B4. GuestLinks GOOGLE_VERIFIED: anonymous DENIED, wrong email DENIED, right email OK", async () => {
  const ownerDb = getOwnerDb();
  const emailHash = sha256Hex(BORROWER_EMAIL);

  await ownerDb.collection("guestLinks").doc("link_google_verified").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_gv",
    payableAmountPaise: 30000,
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "GOOGLE_VERIFIED",
    expectedGuestEmailHash: emailHash,
    allowedActions: ["CONFIRM"],
  });

  await assertFails(getAnonGuestDb().collection("guestLinks").doc("link_google_verified").get());
  await assertFails(getGoogleGuestDb(WRONG_EMAIL, true).collection("guestLinks").doc("link_google_verified").get());
  await assertFails(getGoogleGuestDb(BORROWER_EMAIL, false).collection("guestLinks").doc("link_google_verified").get());
  await assertSucceeds(getGoogleGuestDb(BORROWER_EMAIL, true).collection("guestLinks").doc("link_google_verified").get());
});

test("B5. GuestActions CONFIRM/DISPUTE/PROPOSE_DATE/CLAIM_PAYMENT: verified email, bounds, modes & review", async () => {
  const ownerDb = getOwnerDb();
  const anonGuestDb = getAnonGuestDb();
  const unverifiedGuestDb = getGoogleGuestDb(BORROWER_EMAIL, false);
  const guestDb = getVerifiedGuestDb();
  const now = Date.now();

  await ownerDb.collection("guestLinks").doc("link_claim_test").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_claim",
    payableAmountPaise: 10000,
    expiresAtMs: now + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CLAIM_PAYMENT", "CONFIRM", "DISPUTE", "PROPOSE_DATE"],
  });

  const actionsRef = (db) => db.collection("guestLinks").doc("link_claim_test").collection("actions");
  const baseValid = {
    schemaVersion: 1,
    actionType: "CLAIM_PAYMENT",
    guestUid: GUEST_GOOGLE_UID,
    status: "PENDING",
    createdAtMs: now,
    amountPaise: 5000,
    paymentMode: "UPI",
    referenceNumber: "UPI123456789",
    note: "Paid installment",
    hasProof: false,
  };

  await assertFails(actionsRef(anonGuestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, guestUid: GUEST_ANON_UID }));
  await assertFails(actionsRef(unverifiedGuestDb).doc("CLAIM_PAYMENT").set(baseValid));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 0 }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: -500 }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 50.5 }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 15000 }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, paymentMode: "BITCOIN" }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, injectedField: "malicious" }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, referenceNumber: "R".repeat(65) }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, hasProof: "true" }));

  await assertSucceeds(actionsRef(guestDb).doc("CLAIM_PAYMENT").set(baseValid));
  await assertSucceeds(
    actionsRef(guestDb).doc("CONFIRM").set({
      schemaVersion: 1,
      actionType: "CONFIRM",
      guestUid: GUEST_GOOGLE_UID,
      status: "PENDING",
      createdAtMs: now,
    })
  );
  await assertSucceeds(
    actionsRef(guestDb).doc("DISPUTE").set({
      schemaVersion: 1,
      actionType: "DISPUTE",
      guestUid: GUEST_GOOGLE_UID,
      status: "PENDING",
      createdAtMs: now,
      reason: "Amount differs from cash receipt",
    })
  );
  await assertSucceeds(
    actionsRef(guestDb).doc("PROPOSE_DATE").set({
      schemaVersion: 1,
      actionType: "PROPOSE_DATE",
      guestUid: GUEST_GOOGLE_UID,
      status: "PENDING",
      createdAtMs: now,
      proposedDateMs: now + 86400000,
      reason: "Payday is Friday",
    })
  );

  await assertSucceeds(actionsRef(guestDb).doc("CLAIM_PAYMENT").get());
  await assertFails(actionsRef(guestDb).get());
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").set({ ...baseValid, createdAtMs: now + 100 }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").update({ status: "ACCEPTED", reviewedAtMs: now }));
  await assertFails(actionsRef(guestDb).doc("CLAIM_PAYMENT").delete());

  await assertSucceeds(actionsRef(ownerDb).get());
  await assertSucceeds(
    actionsRef(ownerDb).doc("CLAIM_PAYMENT").update({
      status: "ACCEPTED",
      reviewedAtMs: now,
    })
  );
  await assertSucceeds(actionsRef(ownerDb).doc("CONFIRM").delete());
});

test("B6. GuestAction Proof: verified email required, extra fields & oversized base64 DENIED, owner read/delete OK", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getVerifiedGuestDb();
  const unverifiedGuestDb = getGoogleGuestDb(BORROWER_EMAIL, false);
  const attackerDb = getAttackerDb();

  await ownerDb.collection("guestLinks").doc("link_proof_test").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_proof",
    payableAmountPaise: 10000,
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CLAIM_PAYMENT"],
  });

  await guestDb
    .collection("guestLinks")
    .doc("link_proof_test")
    .collection("actions")
    .doc("CLAIM_PAYMENT")
    .set({
      schemaVersion: 1,
      actionType: "CLAIM_PAYMENT",
      guestUid: GUEST_GOOGLE_UID,
      status: "PENDING",
      createdAtMs: Date.now(),
      amountPaise: 5000,
      paymentMode: "UPI",
      referenceNumber: "UPI999",
      note: "with proof",
      hasProof: true,
    });

  const proofRef = (db) =>
    db
      .collection("guestLinks")
      .doc("link_proof_test")
      .collection("actions")
      .doc("CLAIM_PAYMENT")
      .collection("proof")
      .doc("image");

  await assertFails(proofRef(unverifiedGuestDb).set({ mime: "image/webp", dataB64: "A".repeat(100) }));
  await assertFails(proofRef(guestDb).set({ mime: "image/webp", dataB64: "A".repeat(700001) }));
  await assertFails(proofRef(guestDb).set({ mime: "image/webp", dataB64: "A".repeat(100), extra: 1 }));
  await assertFails(proofRef(guestDb).set({ mime: "application/pdf", dataB64: "A".repeat(100) }));
  await assertSucceeds(proofRef(guestDb).set({ mime: "image/webp", dataB64: "A".repeat(1000) }));
  await assertFails(proofRef(guestDb).update({ dataB64: "B".repeat(100) }));
  await assertFails(proofRef(attackerDb).get());
  await assertSucceeds(proofRef(guestDb).get());
  await assertSucceeds(proofRef(ownerDb).get());
  await assertFails(proofRef(guestDb).delete());
  await assertSucceeds(proofRef(ownerDb).delete());
});

// =========================================================================
// PART C: Participant-Scoped Real-Time Collections (Spark Mode)
// (user_profiles, shared_records, partial_payments, audit_trail, chat_threads, messages)
// =========================================================================

function buildValidSharedRecord(overrides = {}) {
  return {
    remoteId: "rec_c1001",
    creatorUid: OWNER_UID,
    counterpartyUid: "",
    participantUids: [OWNER_UID],
    lenderName: "Alice Owner",
    borrowerName: "Bob Borrower",
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
    contentHash: "a".repeat(64),
    verificationStatus: "PENDING",
    verifiedAtMillis: -1,
    disputeReason: "",
    encryptedNotes: "AES_GCM_CIPHERTEXT_SAMPLE",
    attachmentRefs: [],
    isSettled: false,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function buildValidChatThread(overrides = {}) {
  return {
    threadId: "tx_rec_c1001",
    threadScope: "TRANSACTION",
    remoteRecordId: "rec_c1001",
    partyName: "Bob Borrower",
    creatorUid: OWNER_UID,
    counterpartyUid: "",
    participantUids: [OWNER_UID],
    typingUid: "",
    typingUpdatedAtMillis: -1,
    mutedByUids: [],
    blockedByUid: "",
    reportedByUid: "",
    reportReason: "",
    lastMessagePreview: "Record created",
    lastMessageAtMillis: 1730000000000,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

test("C1. /user_profiles/{uid}: owner create, read, update, delete OK, outsider denied", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const strangerDb = getStrangerDb();

  const profileRef = (db, uid = OWNER_UID) => db.collection("user_profiles").doc(uid);
  const now = new Date();
  const validProfile = {
    userId: OWNER_UID,
    displayName: "Alice Owner",
    phoneNumber: "+919876543210",
    upiId: "alice@upi",
    photoUri: "https://example.com/avatar.webp",
    fcmToken: "fcm_token_initial",
    createdAt: now,
    updatedAt: now,
  };

  // Stranger and outsider -> DENIED
  await assertFails(profileRef(strangerDb).set(validProfile));
  await assertFails(profileRef(borrowerDb).set(validProfile));

  // Owner create -> OK
  await assertSucceeds(profileRef(ownerDb).set(validProfile));

  // Owner read -> OK, outsider read -> DENIED
  await assertSucceeds(profileRef(ownerDb).get());
  await assertFails(profileRef(borrowerDb).get());

  // Owner update fcmToken -> OK
  await assertSucceeds(
    profileRef(ownerDb).update({
      fcmToken: "fcm_token_refreshed",
      updatedAt: new Date(),
    })
  );

  // Owner delete -> OK
  await assertSucceeds(profileRef(ownerDb).delete());
});

test("C2. /shared_records/{id}: create, counterparty claim & confirm, query, terms edit & outsider deny", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();
  const strangerDb = getStrangerDb();

  const recRef = (db) => db.collection("shared_records").doc("rec_c1001");

  // Stranger & outsider cannot create with forged creatorUid
  await assertFails(recRef(strangerDb).set(buildValidSharedRecord()));
  await assertFails(recRef(attackerDb).set(buildValidSharedRecord()));

  // Owner (Alice) creates valid record
  await assertSucceeds(recRef(ownerDb).set(buildValidSharedRecord()));

  // Stranger and non-participant attacker cannot read
  await assertFails(recRef(strangerDb).get());
  await assertFails(recRef(attackerDb).get());

  // Creator cannot confirm their own record
  await assertFails(
    recRef(ownerDb).update({
      verificationStatus: "CONFIRMED",
      updatedAt: new Date(),
    })
  );

  // Borrower claims and confirms the record
  await assertSucceeds(
    recRef(borrowerDb).update({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
      verificationStatus: "CONFIRMED",
      verifiedAtMillis: 1730000500000,
      disputeReason: "",
      updatedAt: new Date(),
    })
  );

  // Both participants can read and list with array-contains
  await assertSucceeds(recRef(ownerDb).get());
  await assertSucceeds(recRef(borrowerDb).get());
  await assertSucceeds(
    ownerDb.collection("shared_records").where("participantUids", "array-contains", OWNER_UID).get()
  );
  await assertSucceeds(
    borrowerDb.collection("shared_records").where("participantUids", "array-contains", BORROWER_UID).get()
  );

  // Attacker cannot read or hijack already claimed record
  await assertFails(recRef(attackerDb).get());
  await assertFails(
    recRef(attackerDb).update({
      counterpartyUid: ATTACKER_UID,
      participantUids: [OWNER_UID, ATTACKER_UID],
      updatedAt: new Date(),
    })
  );

  // Term modification requires EDITED_AFTER_CONFIRM status
  await assertFails(
    recRef(ownerDb).update({
      principalAmount: 7000.0,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    recRef(ownerDb).update({
      principalAmount: 7000.0,
      verificationStatus: "EDITED_AFTER_CONFIRM",
      updatedAt: new Date(),
    })
  );
});

test("C3. /shared_records/{id}/partial_payments/{id}: participant record, acknowledge, tamper deny", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();

  // Create base shared record
  await ownerDb.collection("shared_records").doc("rec_c1001").set(
    buildValidSharedRecord({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
      verificationStatus: "CONFIRMED",
    })
  );

  const payRef = (db) =>
    db.collection("shared_records").doc("rec_c1001").collection("partial_payments").doc("pay_1");

  const validPayment = {
    paymentId: "pay_1",
    remoteId: "rec_c1001",
    recordedByUid: BORROWER_UID,
    recordedByName: "Bob Borrower",
    participantUids: [OWNER_UID, BORROWER_UID],
    amount: 1500.0,
    paymentMode: "UPI",
    paidAtMillis: 1730001000000,
    encryptedNote: "",
    isAcknowledged: false,
    acknowledgedByUid: "",
    acknowledgedAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  // Attacker cannot write payment
  await assertFails(payRef(attackerDb).set(validPayment));

  // Bob records partial payment -> OK
  await assertSucceeds(payRef(borrowerDb).set(validPayment));

  // Attacker cannot read payment
  await assertFails(payRef(attackerDb).get());

  // Alice acknowledges partial payment -> OK
  await assertSucceeds(
    payRef(ownerDb).update({
      isAcknowledged: true,
      acknowledgedByUid: OWNER_UID,
      acknowledgedAtMillis: 1730002000000,
      updatedAt: new Date(),
    })
  );

  // Alice cannot tamper with recorded amount -> DENIED
  await assertFails(
    payRef(ownerDb).update({
      amount: 500.0,
      updatedAt: new Date(),
    })
  );
});

test("C4. /shared_records/{id}/audit_trail/{id}: append-only, tamper deny, outsider deny", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();

  await ownerDb.collection("shared_records").doc("rec_c1001").set(
    buildValidSharedRecord({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
      verificationStatus: "CONFIRMED",
    })
  );

  const auditRef = (db) =>
    db.collection("shared_records").doc("rec_c1001").collection("audit_trail").doc("evt_1");

  const validAudit = {
    eventId: "evt_1",
    remoteId: "rec_c1001",
    eventType: "CONFIRMED",
    actorUid: BORROWER_UID,
    actorName: "Bob Borrower",
    participantUids: [OWNER_UID, BORROWER_UID],
    summaryText: "Bob confirmed the record",
    contentHashSnapshot: "a".repeat(64),
    acknowledged: true,
    eventTimeMillis: 1730000500000,
    createdAt: new Date(),
  };

  // Attacker cannot create audit event
  await assertFails(auditRef(attackerDb).set(validAudit));

  // Bob appends audit event -> OK
  await assertSucceeds(auditRef(borrowerDb).set(validAudit));

  // Both participants can read audit event
  await assertSucceeds(auditRef(ownerDb).get());
  await assertSucceeds(auditRef(borrowerDb).get());

  // Attacker cannot read audit event
  await assertFails(auditRef(attackerDb).get());

  // Updating audit event MUST FAIL (immutable history)
  await assertFails(auditRef(ownerDb).update({ summaryText: "Tampered note" }));
  await assertFails(auditRef(borrowerDb).update({ summaryText: "Tampered note" }));
});

test("C5. /chat_threads/{id}: tx_ and dm_ creation, joining, typing/mute updates, outsider deny & 3rd UID cap", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();

  const txThreadRef = (db) => db.collection("chat_threads").doc("tx_rec_c1001");

  // Alice creates transaction thread with 1 participant
  await assertSucceeds(txThreadRef(ownerDb).set(buildValidChatThread()));

  // Attacker cannot read
  await assertFails(txThreadRef(attackerDb).get());

  // Bob joins thread as counterparty
  await assertSucceeds(
    txThreadRef(borrowerDb).update({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
      partyName: "Alice Owner",
      typingUid: BORROWER_UID,
      typingUpdatedAtMillis: 1730001000000,
      lastMessagePreview: "Joined thread",
      lastMessageAtMillis: 1730001000000,
      updatedAt: new Date(),
    })
  );

  // Attacker cannot join already-joined thread
  await assertFails(
    txThreadRef(attackerDb).update({
      counterpartyUid: ATTACKER_UID,
      participantUids: [OWNER_UID, ATTACKER_UID],
      updatedAt: new Date(),
    })
  );

  // Bob updates typing status and mute using SetOptions.merge
  await assertSucceeds(
    txThreadRef(borrowerDb).set(
      {
        typingUid: BORROWER_UID,
        typingUpdatedAtMillis: 1730002000000,
        mutedByUids: [BORROWER_UID],
        updatedAt: new Date(),
      },
      { merge: true }
    )
  );

  // Attempting to inject a third UID into participantUids (cap <= 2) -> DENIED
  await assertFails(
    txThreadRef(ownerDb).update({
      participantUids: [OWNER_UID, BORROWER_UID, ATTACKER_UID],
      updatedAt: new Date(),
    })
  );

  // DM thread: Alice creates deterministic dm_<uidA>_<uidB>
  const dmId = `dm_${OWNER_UID}_${BORROWER_UID}`;
  const validDm = {
    threadId: dmId,
    threadScope: "PARTY",
    remoteRecordId: "",
    partyName: "Bob Borrower",
    creatorUid: OWNER_UID,
    counterpartyUid: BORROWER_UID,
    participantUids: [OWNER_UID, BORROWER_UID],
    typingUid: "",
    typingUpdatedAtMillis: -1,
    mutedByUids: [],
    blockedByUid: "",
    reportedByUid: "",
    reportReason: "",
    lastMessagePreview: "Hello",
    lastMessageAtMillis: 1730000000000,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  // Attacker cannot create dm_ thread between Alice and Bob
  await assertFails(attackerDb.collection("chat_threads").doc(dmId).set(validDm));

  // Alice can create dm_ thread
  await assertSucceeds(ownerDb.collection("chat_threads").doc(dmId).set(validDm));
});

test("C6. /chat_threads/{id}/messages/{id}: send, read receipt, extension request & body tamper deny", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();

  await ownerDb.collection("chat_threads").doc("tx_rec_c1001").set(
    buildValidChatThread({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
    })
  );

  const msgRef = (db, id = "msg_1") =>
    db.collection("chat_threads").doc("tx_rec_c1001").collection("messages").doc(id);

  const validMsg = {
    messageId: "msg_1",
    threadId: "tx_rec_c1001",
    senderUid: BORROWER_UID,
    senderName: "Bob Borrower",
    participantUids: [OWNER_UID, BORROWER_UID],
    messageType: "EXTENSION_REQUEST",
    bodyText: "Requesting extension until Friday",
    attachmentUri: "",
    attachmentMimeType: "",
    attachmentName: "",
    deliveryStatus: "SENT",
    proposedDueDateMillis: 1731000000000,
    extensionStatus: "PENDING",
    sentAtMillis: 1730003000000,
    deliveredAtMillis: -1,
    readAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  // Attacker cannot send message
  await assertFails(msgRef(attackerDb).set(validMsg));

  // Bob sends message -> OK
  await assertSucceeds(msgRef(borrowerDb).set(validMsg));

  // Attacker cannot read message
  await assertFails(msgRef(attackerDb).get());

  // Alice marks message READ and ACCEPTS extension
  await assertSucceeds(
    msgRef(ownerDb).update({
      deliveryStatus: "READ",
      deliveredAtMillis: 1730003100000,
      readAtMillis: 1730003200000,
      extensionStatus: "ACCEPTED",
      updatedAt: new Date(),
    })
  );

  // Alice attempts to tamper with Bob's message bodyText -> DENIED
  await assertFails(
    msgRef(ownerDb).update({
      bodyText: "Forged message body",
      updatedAt: new Date(),
    })
  );

  // Sender can delete own message, non-sender cannot
  await assertFails(msgRef(ownerDb).delete());
  await assertSucceeds(msgRef(borrowerDb).delete());
});

test("C7. Verified email required on EVERY profile, ledger, chat, and guest path", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const unverifiedOwnerDb = getUnverifiedOwnerDb();
  const unverifiedBorrowerDb = getUnverifiedBorrowerDb();

  // Seed valid documents with verified users
  await assertSucceeds(
    ownerDb.collection("user_profiles").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "Alice",
      phoneNumber: "+919876543210",
      upiId: "alice@upi",
      photoUri: "",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("shared_records").doc("rec_v1").set(
      buildValidSharedRecord({
        remoteId: "rec_v1",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
      })
    )
  );
  await assertSucceeds(
    borrowerDb.collection("shared_records").doc("rec_v1").collection("partial_payments").doc("pay_v1").set({
      paymentId: "pay_v1",
      remoteId: "rec_v1",
      recordedByUid: BORROWER_UID,
      recordedByName: "Bob",
      participantUids: [OWNER_UID, BORROWER_UID],
      amount: 100.0,
      paymentMode: "UPI",
      paidAtMillis: 1730001000000,
      encryptedNote: "",
      isAcknowledged: false,
      acknowledgedByUid: "",
      acknowledgedAtMillis: -1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("shared_records").doc("rec_v1").collection("audit_trail").doc("aud_v1").set({
      eventId: "aud_v1",
      remoteId: "rec_v1",
      eventType: "CREATED",
      actorUid: OWNER_UID,
      actorName: "Alice",
      participantUids: [OWNER_UID, BORROWER_UID],
      summaryText: "Created",
      contentHashSnapshot: "a".repeat(64),
      acknowledged: false,
      eventTimeMillis: 1730001000000,
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("chat_threads").doc("tx_rec_v1").set(
      buildValidChatThread({
        threadId: "tx_rec_v1",
        remoteRecordId: "rec_v1",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("chat_threads").doc("tx_rec_v1").collection("messages").doc("msg_v1").set({
      messageId: "msg_v1",
      threadId: "tx_rec_v1",
      senderUid: OWNER_UID,
      senderName: "Alice",
      participantUids: [OWNER_UID, BORROWER_UID],
      messageType: "TEXT",
      bodyText: "Hello",
      attachmentUri: "",
      attachmentMimeType: "",
      attachmentName: "",
      deliveryStatus: "SENT",
      proposedDueDateMillis: -1,
      extensionStatus: "NONE",
      sentAtMillis: 1730003000000,
      deliveredAtMillis: -1,
      readAtMillis: -1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Unverified owner / borrower DENIED on every read, create, update, delete
  await assertFails(unverifiedOwnerDb.collection("user_profiles").doc(OWNER_UID).get());
  await assertFails(unverifiedOwnerDb.collection("user_profiles").doc(OWNER_UID).update({ displayName: "No", updatedAt: new Date() }));
  await assertFails(unverifiedOwnerDb.collection("user_profiles").doc(OWNER_UID).delete());

  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v1").get());
  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v2").set(buildValidSharedRecord({ remoteId: "rec_v2" })));
  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v1").update({ disputeReason: "x", updatedAt: new Date() }));
  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v1").delete());

  await assertFails(unverifiedBorrowerDb.collection("shared_records").doc("rec_v1").collection("partial_payments").doc("pay_v1").get());
  await assertFails(unverifiedBorrowerDb.collection("shared_records").doc("rec_v1").collection("partial_payments").doc("pay_v1").delete());
  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v1").collection("audit_trail").doc("aud_v1").get());
  await assertFails(unverifiedOwnerDb.collection("shared_records").doc("rec_v1").collection("audit_trail").doc("aud_v1").delete());

  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v1").get());
  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v2").set(buildValidChatThread({ threadId: "tx_rec_v2" })));
  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v1").update({ typingUid: OWNER_UID, updatedAt: new Date() }));
  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v1").delete());

  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v1").collection("messages").doc("msg_v1").get());
  await assertFails(
    unverifiedBorrowerDb.collection("chat_threads").doc("tx_rec_v1").collection("messages").doc("msg_v2").set({
      messageId: "msg_v2",
      threadId: "tx_rec_v1",
      senderUid: BORROWER_UID,
      senderName: "Bob",
      participantUids: [OWNER_UID, BORROWER_UID],
      messageType: "TEXT",
      bodyText: "Unverified attempt",
      attachmentUri: "",
      attachmentMimeType: "",
      attachmentName: "",
      deliveryStatus: "SENT",
      proposedDueDateMillis: -1,
      extensionStatus: "NONE",
      sentAtMillis: 1730003500000,
      deliveredAtMillis: -1,
      readAtMillis: -1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(unverifiedOwnerDb.collection("chat_threads").doc("tx_rec_v1").collection("messages").doc("msg_v1").delete());
});

test("C8. Chat Safety Enforcement: thread block and user block prevent message creation, unblock restores it", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();

  const threadId = "tx_rec_safety";
  const threadRef = (db) => db.collection("chat_threads").doc(threadId);
  const msgRef = (db, id) => db.collection("chat_threads").doc(threadId).collection("messages").doc(id);

  await assertSucceeds(
    threadRef(ownerDb).set(
      buildValidChatThread({
        threadId,
        remoteRecordId: "rec_safety",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
        blockedByUids: [],
      })
    )
  );

  const buildMsg = (id, senderUid) => ({
    messageId: id,
    threadId,
    senderUid,
    senderName: senderUid === OWNER_UID ? "Alice" : "Bob",
    participantUids: [OWNER_UID, BORROWER_UID],
    messageType: "TEXT",
    bodyText: "Checking balance",
    attachmentUri: "",
    attachmentMimeType: "",
    attachmentName: "",
    deliveryStatus: "SENT",
    proposedDueDateMillis: -1,
    extensionStatus: "NONE",
    sentAtMillis: Date.now(),
    deliveredAtMillis: -1,
    readAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  // 1. Before any block, Bob can send a message -> OK
  await assertSucceeds(msgRef(borrowerDb, "m1").set(buildMsg("m1", BORROWER_UID)));

  // 2. Thread-level block (blockedByUid + blockedByUids) -> Bob CANNOT create message
  await assertSucceeds(
    threadRef(ownerDb).update({
      blockedByUid: OWNER_UID,
      blockedByUids: [OWNER_UID],
      updatedAt: new Date(),
    })
  );
  await assertFails(msgRef(borrowerDb, "m2").set(buildMsg("m2", BORROWER_UID)));

  // 3. Unblock thread -> Bob CAN send again
  await assertSucceeds(
    threadRef(ownerDb).update({
      blockedByUid: "",
      blockedByUids: [],
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(msgRef(borrowerDb, "m3").set(buildMsg("m3", BORROWER_UID)));

  // 4. User-level block (/users/{OWNER_UID}/blocked_users/{BORROWER_UID}) -> Bob CANNOT create message
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("blocked_users").doc(BORROWER_UID).set({
      targetUid: BORROWER_UID,
      isMuted: true,
      isBlocked: true,
      isReported: true,
      reportReason: "Abusive messages",
      updatedAt: new Date(),
    })
  );
  await assertFails(msgRef(borrowerDb, "m4").set(buildMsg("m4", BORROWER_UID)));

  // 5. Also blocked in a separate DM thread between Alice & Bob
  const dmId = `dm_${OWNER_UID}_${BORROWER_UID}`;
  await assertSucceeds(
    ownerDb.collection("chat_threads").doc(dmId).set(
      buildValidChatThread({
        threadId: dmId,
        threadScope: "PARTY",
        remoteRecordId: "",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
        blockedByUids: [],
      })
    )
  );
  await assertFails(
    borrowerDb.collection("chat_threads").doc(dmId).collection("messages").doc("dm_m1").set({
      ...buildMsg("dm_m1", BORROWER_UID),
      threadId: dmId,
    })
  );

  // 6. Unblock user (isBlocked = false) -> Bob CAN send again in both threads
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("blocked_users").doc(BORROWER_UID).update({
      isBlocked: false,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(msgRef(borrowerDb, "m5").set(buildMsg("m5", BORROWER_UID)));
  await assertSucceeds(
    borrowerDb.collection("chat_threads").doc(dmId).collection("messages").doc("dm_m2").set({
      ...buildMsg("dm_m2", BORROWER_UID),
      threadId: dmId,
    })
  );
});

// =========================================================================
// PART D: Fuzz Tests (Extra Fields, Oversized Strings, Wrong Types, Invalid Enums)
// =========================================================================

test("D1. Fuzz matrix: extra fields, oversized strings & wrong types denied across guest, profile, shared & chat paths", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const now = Date.now();

  // 1. guestLinks fuzzing
  const validGuestLink = {
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_fuzz",
    payableAmountPaise: 5000,
    expiresAtMs: now + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  };
  await assertFails(ownerDb.collection("guestLinks").doc("gl_extra").set({ ...validGuestLink, unknownKey: "x" }));
  await assertFails(ownerDb.collection("guestLinks").doc("gl_big").set({ ...validGuestLink, note: "Z".repeat(501) }));
  await assertFails(ownerDb.collection("guestLinks").doc("gl_type").set({ ...validGuestLink, payableAmountPaise: "5000" }));
  await assertFails(ownerDb.collection("guestLinks").doc("gl_neg").set({ ...validGuestLink, payableAmountPaise: -1 }));
  await assertFails(ownerDb.collection("guestLinks").doc("gl_enum").set({ ...validGuestLink, verificationLevel: "ADMIN" }));

  // 2. user_profiles fuzzing
  const validProfile = {
    userId: OWNER_UID,
    displayName: "Alice",
    phoneNumber: "+919876543210",
    upiId: "alice@upi",
    photoUri: "",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assertFails(ownerDb.collection("user_profiles").doc(OWNER_UID).set({ ...validProfile, role: "admin" }));
  await assertFails(ownerDb.collection("user_profiles").doc(OWNER_UID).set({ ...validProfile, displayName: "A".repeat(101) }));
  await assertFails(ownerDb.collection("user_profiles").doc(OWNER_UID).set({ ...validProfile, upiId: 12345 }));

  // 3. shared_records fuzzing
  await assertFails(
    ownerDb.collection("shared_records").doc("sr_extra").set({
      ...buildValidSharedRecord({ remoteId: "sr_extra" }),
      injectedFlag: true,
    })
  );
  await assertFails(
    ownerDb.collection("shared_records").doc("sr_big").set({
      ...buildValidSharedRecord({ remoteId: "sr_big", encryptedNotes: "E".repeat(2001) }),
    })
  );
  await assertFails(
    ownerDb.collection("shared_records").doc("sr_type").set({
      ...buildValidSharedRecord({ remoteId: "sr_type", principalAmount: "5000" }),
    })
  );
  await assertFails(
    ownerDb.collection("shared_records").doc("sr_neg").set({
      ...buildValidSharedRecord({ remoteId: "sr_neg", principalAmount: -50 }),
    })
  );
  await assertFails(
    ownerDb.collection("shared_records").doc("sr_enum").set({
      ...buildValidSharedRecord({ remoteId: "sr_enum", currency: "BTC" }),
    })
  );

  // Seed valid shared record & thread for subcollection fuzzing
  await assertSucceeds(
    ownerDb.collection("shared_records").doc("sr_ok").set(
      buildValidSharedRecord({
        remoteId: "sr_ok",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("chat_threads").doc("tx_sr_ok").set(
      buildValidChatThread({
        threadId: "tx_sr_ok",
        remoteRecordId: "sr_ok",
        counterpartyUid: BORROWER_UID,
        participantUids: [OWNER_UID, BORROWER_UID],
      })
    )
  );

  // 4. partial_payments fuzzing
  const validPay = {
    paymentId: "pay_fuzz",
    remoteId: "sr_ok",
    recordedByUid: BORROWER_UID,
    recordedByName: "Bob",
    participantUids: [OWNER_UID, BORROWER_UID],
    amount: 250.0,
    paymentMode: "UPI",
    paidAtMillis: now,
    encryptedNote: "",
    isAcknowledged: false,
    acknowledgedByUid: "",
    acknowledgedAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assertFails(borrowerDb.collection("shared_records").doc("sr_ok").collection("partial_payments").doc("pay_fuzz").set({ ...validPay, extra: "x" }));
  await assertFails(borrowerDb.collection("shared_records").doc("sr_ok").collection("partial_payments").doc("pay_fuzz").set({ ...validPay, encryptedNote: "N".repeat(1025) }));
  await assertFails(borrowerDb.collection("shared_records").doc("sr_ok").collection("partial_payments").doc("pay_fuzz").set({ ...validPay, amount: "250" }));

  // 5. audit_trail fuzzing
  const validAud = {
    eventId: "aud_fuzz",
    remoteId: "sr_ok",
    eventType: "CREATED",
    actorUid: OWNER_UID,
    actorName: "Alice",
    participantUids: [OWNER_UID, BORROWER_UID],
    summaryText: "Created",
    contentHashSnapshot: "a".repeat(64),
    acknowledged: false,
    eventTimeMillis: now,
    createdAt: new Date(),
  };
  await assertFails(ownerDb.collection("shared_records").doc("sr_ok").collection("audit_trail").doc("aud_fuzz").set({ ...validAud, extra: 1 }));
  await assertFails(ownerDb.collection("shared_records").doc("sr_ok").collection("audit_trail").doc("aud_fuzz").set({ ...validAud, summaryText: "S".repeat(501) }));
  await assertFails(ownerDb.collection("shared_records").doc("sr_ok").collection("audit_trail").doc("aud_fuzz").set({ ...validAud, eventType: "INVALID" }));

  // 6. chat_threads fuzzing
  await assertFails(ownerDb.collection("chat_threads").doc("tx_fuzz").set({ ...buildValidChatThread({ threadId: "tx_fuzz" }), extraField: 1 }));
  await assertFails(ownerDb.collection("chat_threads").doc("tx_fuzz").set(buildValidChatThread({ threadId: "tx_fuzz", reportReason: "R".repeat(301) })));
  await assertFails(ownerDb.collection("chat_threads").doc("tx_fuzz").set(buildValidChatThread({ threadId: "tx_fuzz", threadScope: "GROUP" })));

  // 7. chat messages fuzzing
  const validMsg = {
    messageId: "msg_fuzz",
    threadId: "tx_sr_ok",
    senderUid: OWNER_UID,
    senderName: "Alice",
    participantUids: [OWNER_UID, BORROWER_UID],
    messageType: "TEXT",
    bodyText: "Hello",
    attachmentUri: "",
    attachmentMimeType: "",
    attachmentName: "",
    deliveryStatus: "SENT",
    proposedDueDateMillis: -1,
    extensionStatus: "NONE",
    sentAtMillis: now,
    deliveredAtMillis: -1,
    readAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assertFails(ownerDb.collection("chat_threads").doc("tx_sr_ok").collection("messages").doc("msg_fuzz").set({ ...validMsg, unknownKey: 1 }));
  await assertFails(ownerDb.collection("chat_threads").doc("tx_sr_ok").collection("messages").doc("msg_fuzz").set({ ...validMsg, bodyText: "B".repeat(1001) }));
  await assertFails(ownerDb.collection("chat_threads").doc("tx_sr_ok").collection("messages").doc("msg_fuzz").set({ ...validMsg, sentAtMillis: "now" }));
  await assertFails(ownerDb.collection("chat_threads").doc("tx_sr_ok").collection("messages").doc("msg_fuzz").set({ ...validMsg, messageType: "EXECUTABLE" }));
});

test("D2. Regression: non-existent chat_threads/shared_records get() and creator Action 3 linking counterpartyUid", async () => {
  const ownerDb = getOwnerDb();
  const borrowerDb = getBorrowerDb();
  const attackerDb = getAttackerDb();
  const unverifiedDb = getUnverifiedOwnerDb();
  const strangerDb = getStrangerDb();

  // 1. Verified user get() on non-existent docs succeeds (prevents upsertChatThread PERMISSION_DENIED deadlock)
  const missingThreadSnap = await assertSucceeds(
    ownerDb.collection("chat_threads").doc("tx_nonexistent_123").get()
  );
  if (missingThreadSnap.exists) {
    throw new Error("Expected non-existent chat_thread snapshot");
  }
  const missingSharedSnap = await assertSucceeds(
    ownerDb.collection("shared_records").doc("rec_nonexistent_123").get()
  );
  if (missingSharedSnap.exists) {
    throw new Error("Expected non-existent shared_record snapshot");
  }

  // 2. Unverified and unauthenticated users are still DENIED even on non-existent docs
  await assertFails(unverifiedDb.collection("chat_threads").doc("tx_nonexistent_123").get());
  await assertFails(strangerDb.collection("chat_threads").doc("tx_nonexistent_123").get());
  await assertFails(unverifiedDb.collection("shared_records").doc("rec_nonexistent_123").get());
  await assertFails(strangerDb.collection("shared_records").doc("rec_nonexistent_123").get());

  // 3. Creator creates a 1-participant thread, then links counterpartyUid (Action 3)
  const threadRef = (db) => db.collection("chat_threads").doc("tx_creator_link");
  await assertSucceeds(
    threadRef(ownerDb).set(
      buildValidChatThread({
        threadId: "tx_creator_link",
        remoteRecordId: "rec_creator_link",
        counterpartyUid: "",
        participantUids: [OWNER_UID],
      })
    )
  );

  // Outsider still CANNOT read existing thread
  await assertFails(threadRef(attackerDb).get());

  // Creator CANNOT link themselves as counterpartyUid
  await assertFails(
    threadRef(ownerDb).update({
      counterpartyUid: OWNER_UID,
      participantUids: [OWNER_UID, OWNER_UID],
      updatedAt: new Date(),
    })
  );

  // Creator links Bob via Action 3 -> OK
  await assertSucceeds(
    threadRef(ownerDb).update({
      counterpartyUid: BORROWER_UID,
      participantUids: [OWNER_UID, BORROWER_UID],
      updatedAt: new Date(),
    })
  );

  // Once linked, creator CANNOT reassign counterpartyUid to someone else
  await assertFails(
    threadRef(ownerDb).update({
      counterpartyUid: ATTACKER_UID,
      participantUids: [OWNER_UID, ATTACKER_UID],
      updatedAt: new Date(),
    })
  );

  // Both Alice and Bob can now read the thread and exchange messages; attacker remains denied
  await assertSucceeds(threadRef(ownerDb).get());
  await assertSucceeds(threadRef(borrowerDb).get());
  await assertFails(threadRef(attackerDb).get());

  const now = Date.now();
  const baseMsg = (id, senderUid) => ({
    messageId: id,
    threadId: "tx_creator_link",
    senderUid,
    senderName: senderUid === OWNER_UID ? "Alice" : "Bob",
    participantUids: [OWNER_UID, BORROWER_UID],
    messageType: "TEXT",
    bodyText: "Hello after link",
    attachmentUri: "",
    attachmentMimeType: "",
    attachmentName: "",
    deliveryStatus: "SENT",
    proposedDueDateMillis: -1,
    extensionStatus: "NONE",
    sentAtMillis: now,
    deliveredAtMillis: -1,
    readAtMillis: -1,
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  await assertSucceeds(threadRef(ownerDb).collection("messages").doc("m_alice").set(baseMsg("m_alice", OWNER_UID)));
  await assertSucceeds(threadRef(borrowerDb).collection("messages").doc("m_bob").set(baseMsg("m_bob", BORROWER_UID)));
  await assertSucceeds(threadRef(borrowerDb).collection("messages").doc("m_alice").get());
  await assertSucceeds(threadRef(ownerDb).collection("messages").doc("m_bob").get());
  await assertFails(threadRef(attackerDb).collection("messages").doc("m_alice").get());
});

