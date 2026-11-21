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

  // Legacy Blaze-only collections denied by catch-all
  await assertFails(ownerDb.collection("webLinks").doc("wl_1").set({ ownerUid: OWNER_UID }));
  await assertFails(ownerDb.collection("shared_records").doc("sr_1").set({ ownerUid: OWNER_UID }));
  await assertFails(ownerDb.collection("chat").doc("c_1").set({ ownerUid: OWNER_UID }));
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

test("B3. GuestLinks: guest get active OK, list DENIED, expired/revoked DENIED, stranger DENIED", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();
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
    allowedActions: ["CONFIRM"],
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
  await assertSucceeds(guestDb.collection("guestLinks").doc("link_active").get());
  await assertFails(guestDb.collection("guestLinks").get());
  await assertFails(guestDb.collection("guestLinks").doc("link_expired").get());
  await assertFails(guestDb.collection("guestLinks").doc("link_revoked").get());
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

test("B5. GuestActions CLAIM_PAYMENT: bounds, mode, extra field & duplicate overwrite", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();

  await ownerDb.collection("guestLinks").doc("link_claim_test").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_claim",
    payableAmountPaise: 10000,
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CLAIM_PAYMENT", "CONFIRM"],
  });

  const actionsRef = guestDb.collection("guestLinks").doc("link_claim_test").collection("actions");
  const baseValid = {
    schemaVersion: 1,
    actionType: "CLAIM_PAYMENT",
    guestUid: GUEST_ANON_UID,
    status: "PENDING",
    createdAtMs: Date.now(),
    amountPaise: 5000,
    paymentMode: "UPI",
    referenceNumber: "UPI123456789",
    note: "Paid installment",
    hasProof: false,
  };

  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 0 }));
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: -500 }));
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 50.5 }));
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 15000 }));
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, paymentMode: "BITCOIN" }));
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, injectedField: "malicious" }));
  await assertSucceeds(actionsRef.doc("CLAIM_PAYMENT").set(baseValid));

  // Second overwrite by guest -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, createdAtMs: Date.now() + 100 }));
  // Guest setting status ACCEPTED -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").update({ status: "ACCEPTED", reviewedAtMs: Date.now() }));
  // Owner Accept -> OK
  await assertSucceeds(
    ownerDb.collection("guestLinks").doc("link_claim_test").collection("actions").doc("CLAIM_PAYMENT").update({
      status: "ACCEPTED",
      reviewedAtMs: Date.now(),
    })
  );
});

test("B6. GuestAction Proof: other user read DENIED, oversized base64 DENIED, update DENIED", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();
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
      guestUid: GUEST_ANON_UID,
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

  await assertFails(proofRef(guestDb).set({ mime: "image/webp", dataB64: "A".repeat(700001) }));
  await assertSucceeds(proofRef(guestDb).set({ mime: "image/webp", dataB64: "A".repeat(1000) }));
  await assertFails(proofRef(guestDb).update({ dataB64: "B".repeat(100) }));
  await assertFails(proofRef(attackerDb).get());
  await assertSucceeds(proofRef(ownerDb).get());
});
