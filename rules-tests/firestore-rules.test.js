const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert");
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

function getOwnerDb() {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: "alice@udhaar.test",
      email_verified: true,
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
      email: email,
      email_verified: verified,
      firebase: { sign_in_provider: "google.com" },
    })
    .firestore();
}

// -------------------------------------------------------------
// 1. Owner creates link OK. Another user tries to update it -> DENIED
// -------------------------------------------------------------
test("1. Owner creates link OK, attacker update -> DENIED (takeover test)", async () => {
  const ownerDb = getOwnerDb();
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

  // Owner creation must succeed
  await assertSucceeds(ownerDb.collection("guestLinks").doc("link_takeover_test").set(linkData));

  // Attacker attempting update must be DENIED
  await assertFails(
    attackerDb.collection("guestLinks").doc("link_takeover_test").update({
      ownerUid: ATTACKER_UID,
      payableAmountPaise: 1,
    })
  );
});

// -------------------------------------------------------------
// 2. Link containing whatsappOtp -> DENIED
// -------------------------------------------------------------
test("2. Link containing whatsappOtp or otp -> DENIED", async () => {
  const ownerDb = getOwnerDb();

  const invalidLinkDataWithWhatsappOtp = {
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_otp_1",
    payableAmountPaise: 10000,
    whatsappOtp: "123456",
    expiresAtMs: Date.now() + 86400000,
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
  };

  await assertFails(
    ownerDb.collection("guestLinks").doc("link_with_whatsapp_otp").set(invalidLinkDataWithWhatsappOtp)
  );

  const invalidLinkDataWithOtp = {
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_otp_2",
    payableAmountPaise: 10000,
    otp: "654321",
    expiresAtMs: Date.now() + 86400000,
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
  };

  await assertFails(
    ownerDb.collection("guestLinks").doc("link_with_plain_otp").set(invalidLinkDataWithOtp)
  );
});

// -------------------------------------------------------------
// 3. Guest get() active link OK. list() all links -> DENIED. Expired/revoked -> DENIED
// -------------------------------------------------------------
test("3. Guest get() active link OK, list() -> DENIED, expired/revoked -> DENIED", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();

  const now = Date.now();
  // Setup active link
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

  // Setup expired link
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

  // Setup revoked link
  await ownerDb.collection("guestLinks").doc("link_revoked").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "REVOKED",
    recordId: "rec_revoked",
    payableAmountPaise: 20000,
    expiresAtMs: now + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  });

  // Guest get() active link -> OK
  await assertSucceeds(guestDb.collection("guestLinks").doc("link_active").get());

  // Guest list() links -> DENIED
  await assertFails(guestDb.collection("guestLinks").get());

  // Guest get() expired link -> DENIED
  await assertFails(guestDb.collection("guestLinks").doc("link_expired").get());

  // Guest get() revoked link -> DENIED
  await assertFails(guestDb.collection("guestLinks").doc("link_revoked").get());
});

// -------------------------------------------------------------
// 4. GOOGLE_VERIFIED link: anonymous -> DENIED; wrong email -> DENIED; right email -> OK
// -------------------------------------------------------------
test("4. GOOGLE_VERIFIED link: anonymous -> DENIED; wrong email -> DENIED; right email -> OK", async () => {
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

  // Anonymous guest -> DENIED
  const anonDb = getAnonGuestDb();
  await assertFails(anonDb.collection("guestLinks").doc("link_google_verified").get());

  // Wrong email Google guest -> DENIED
  const wrongDb = getGoogleGuestDb(WRONG_EMAIL, true);
  await assertFails(wrongDb.collection("guestLinks").doc("link_google_verified").get());

  // Right email Google guest -> OK
  const rightDb = getGoogleGuestDb(BORROWER_EMAIL, true);
  await assertSucceeds(rightDb.collection("guestLinks").doc("link_google_verified").get());
});

// -------------------------------------------------------------
// 5. CLAIM_PAYMENT: amount 0, negative, float, > payable -> DENIED; bad paymentMode -> DENIED; extra field -> DENIED; valid -> OK
// -------------------------------------------------------------
test("5. CLAIM_PAYMENT payload bounds and schema validation", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();

  await ownerDb.collection("guestLinks").doc("link_claim_test").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_claim",
    payableAmountPaise: 10000, // max 100.00
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CLAIM_PAYMENT"],
  });

  const actionsRef = guestDb
    .collection("guestLinks")
    .doc("link_claim_test")
    .collection("actions");

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

  // Amount 0 -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 0 }));

  // Negative amount -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: -500 }));

  // Float amount -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 50.5 }));

  // Amount > payableAmountPaise (15000 > 10000) -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, amountPaise: 15000 }));

  // Bad paymentMode -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, paymentMode: "BITCOIN" }));

  // Extra unauthorized field -> DENIED
  await assertFails(actionsRef.doc("CLAIM_PAYMENT").set({ ...baseValid, injectedField: "malicious" }));

  // Valid claim -> OK
  await assertSucceeds(actionsRef.doc("CLAIM_PAYMENT").set(baseValid));
});

// -------------------------------------------------------------
// 6. Second pending action of the same type -> DENIED
// -------------------------------------------------------------
test("6. Second pending action of the same type cannot overwrite", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();

  await ownerDb.collection("guestLinks").doc("link_confirm_dup").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_dup",
    payableAmountPaise: 10000,
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  });

  const confirmRef = guestDb
    .collection("guestLinks")
    .doc("link_confirm_dup")
    .collection("actions")
    .doc("CONFIRM");

  const validConfirm = {
    schemaVersion: 1,
    actionType: "CONFIRM",
    guestUid: GUEST_ANON_UID,
    status: "PENDING",
    createdAtMs: Date.now(),
  };

  // First create succeeds
  await assertSucceeds(confirmRef.set(validConfirm));

  // Second pending attempt cannot update/overwrite existing action as guest
  await assertFails(
    confirmRef.set({
      ...validConfirm,
      createdAtMs: Date.now() + 100,
    })
  );
});

// -------------------------------------------------------------
// 7. Guest tries to set status ACCEPTED -> DENIED. Owner Accept -> OK
// -------------------------------------------------------------
test("7. Guest setting status ACCEPTED -> DENIED. Owner Accept -> OK", async () => {
  const ownerDb = getOwnerDb();
  const guestDb = getAnonGuestDb();

  await ownerDb.collection("guestLinks").doc("link_status_test").set({
    ownerUid: OWNER_UID,
    schemaVersion: 1,
    status: "ACTIVE",
    recordId: "rec_status",
    payableAmountPaise: 10000,
    expiresAtMs: Date.now() + 3600000,
    verificationLevel: "PRIVATE_LINK",
    allowedActions: ["CONFIRM"],
  });

  const guestActionRef = guestDb
    .collection("guestLinks")
    .doc("link_status_test")
    .collection("actions")
    .doc("CONFIRM");

  // Create PENDING
  await assertSucceeds(
    guestActionRef.set({
      schemaVersion: 1,
      actionType: "CONFIRM",
      guestUid: GUEST_ANON_UID,
      status: "PENDING",
      createdAtMs: Date.now(),
    })
  );

  // Guest tries to update to ACCEPTED -> DENIED
  await assertFails(
    guestActionRef.update({
      status: "ACCEPTED",
      reviewedAtMs: Date.now(),
    })
  );

  // Owner updating to ACCEPTED -> OK
  const ownerActionRef = ownerDb
    .collection("guestLinks")
    .doc("link_status_test")
    .collection("actions")
    .doc("CONFIRM");

  await assertSucceeds(
    ownerActionRef.update({
      status: "ACCEPTED",
      reviewedAtMs: Date.now(),
    })
  );
});

// -------------------------------------------------------------
// 8. Proof: other user reads it -> DENIED; oversized base64 -> DENIED
// -------------------------------------------------------------
test("8. Proof: other user reads it -> DENIED; oversized base64 -> DENIED", async () => {
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

  const proofRef = guestDb
    .collection("guestLinks")
    .doc("link_proof_test")
    .collection("actions")
    .doc("CLAIM_PAYMENT")
    .collection("proof")
    .doc("image");

  // Oversized base64 (> 700,000 chars) -> DENIED
  const oversizedData = "A".repeat(700001);
  await assertFails(
    proofRef.set({
      mime: "image/webp",
      dataB64: oversizedData,
    })
  );

  // Valid proof -> OK
  const validData = "A".repeat(1000);
  await assertSucceeds(
    proofRef.set({
      mime: "image/webp",
      dataB64: validData,
    })
  );

  // Attacker reading proof -> DENIED
  const attackerProofRef = attackerDb
    .collection("guestLinks")
    .doc("link_proof_test")
    .collection("actions")
    .doc("CLAIM_PAYMENT")
    .collection("proof")
    .doc("image");

  await assertFails(attackerProofRef.get());

  // Owner reading proof -> OK
  const ownerProofRef = ownerDb
    .collection("guestLinks")
    .doc("link_proof_test")
    .collection("actions")
    .doc("CLAIM_PAYMENT")
    .collection("proof")
    .doc("image");

  await assertSucceeds(ownerProofRef.get());
});

// -------------------------------------------------------------
// 9. /users/{uid} cross-user access -> DENIED (regression)
// -------------------------------------------------------------
test("9. /users/{uid} cross-user access -> DENIED (regression)", async () => {
  const ownerDb = getOwnerDb();
  const attackerDb = getAttackerDb();

  // Owner can create record in own path
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("records").doc("rec_alice").set({
      ownerUid: OWNER_UID,
      principalAmountPaise: 10000,
      currency: "INR",
      direction: "GIVEN",
      updatedAtMs: Date.now(),
    })
  );

  // Attacker cannot read owner's record
  await assertFails(
    attackerDb.collection("users").doc(OWNER_UID).collection("records").doc("rec_alice").get()
  );

  // Attacker cannot write to owner's record
  await assertFails(
    attackerDb.collection("users").doc(OWNER_UID).collection("records").doc("rec_alice").update({
      principalAmountPaise: 1,
    })
  );

  // Attacker cannot delete owner's record
  await assertFails(
    attackerDb.collection("users").doc(OWNER_UID).collection("records").doc("rec_alice").delete()
  );
});
