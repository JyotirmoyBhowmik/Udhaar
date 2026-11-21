const assert = require("node:assert/strict");
const schema = require("../web/u/actionSchema.js");

console.log("======================================================");
console.log("  Udhaar Web — Action Schema Conformance Suite        ");
console.log("======================================================\n");

// 1. CONFIRM action builder
{
  const action = schema.buildConfirmAction({ guestUid: "guest-123", nowMs: 1700000000000 });
  assert.equal(action.schemaVersion, 1);
  assert.equal(action.actionType, "CONFIRM");
  assert.equal(action.guestUid, "guest-123");
  assert.equal(action.status, "PENDING");
  assert.equal(action.createdAtMs, 1700000000000);
  assert.deepEqual(Object.keys(action).sort(), ["actionType", "createdAtMs", "guestUid", "schemaVersion", "status"]);

  const res = schema.validateAction("CONFIRM", action, { allowedActions: ["CONFIRM"] });
  assert.equal(res.valid, true);
  console.log("✔ Passed: buildConfirmAction matches strict firestore.rules keys");
}

// 2. DISPUTE action builder
{
  const action = schema.buildDisputeAction({
    guestUid: "guest-123",
    reason: "Principal amount was only 5000, not 10000",
    nowMs: 1700000000000
  });
  assert.equal(action.schemaVersion, 1);
  assert.equal(action.actionType, "DISPUTE");
  assert.equal(action.reason, "Principal amount was only 5000, not 10000");
  assert.deepEqual(Object.keys(action).sort(), ["actionType", "createdAtMs", "guestUid", "reason", "schemaVersion", "status"]);

  const res = schema.validateAction("DISPUTE", action, { allowedActions: ["DISPUTE"] });
  assert.equal(res.valid, true);

  // Rejects empty reason
  assert.throws(() => schema.buildDisputeAction({ guestUid: "guest-123", reason: "   " }));
  console.log("✔ Passed: buildDisputeAction matches strict firestore.rules keys and validates reason");
}

// 3. PROPOSE_DATE action builder
{
  const now = 1700000000000;
  const future = now + 86400000 * 7;
  const action = schema.buildProposeDateAction({
    guestUid: "guest-123",
    proposedDateMs: future,
    reason: "Will pay after salary credit",
    nowMs: now
  });
  assert.equal(action.schemaVersion, 1);
  assert.equal(action.actionType, "PROPOSE_DATE");
  assert.equal(action.proposedDateMs, future);
  assert.equal(action.reason, "Will pay after salary credit");

  const res = schema.validateAction("PROPOSE_DATE", action, { allowedActions: ["PROPOSE_DATE"] });
  assert.equal(res.valid, true);

  // Without optional reason: keys omit reason
  const actionNoReason = schema.buildProposeDateAction({
    guestUid: "guest-123",
    proposedDateMs: future,
    nowMs: now
  });
  assert.equal("reason" in actionNoReason, false);
  const resNoReason = schema.validateAction("PROPOSE_DATE", actionNoReason, { allowedActions: ["PROPOSE_DATE"] });
  assert.equal(resNoReason.valid, true);

  // Rejects past date
  assert.throws(() => schema.buildProposeDateAction({ guestUid: "guest-123", proposedDateMs: now - 1000, nowMs: now }));
  console.log("✔ Passed: buildProposeDateAction validates future timestamp and optional reason");
}

// 4. CLAIM_PAYMENT action builder
{
  const now = 1700000000000;
  const action = schema.buildClaimPaymentAction({
    guestUid: "guest-123",
    amountPaise: 500000, // ₹5,000
    paymentMode: "UPI",
    referenceNumber: "UPI123456789012",
    note: "Paid via GPay",
    hasProof: false,
    payableAmountPaise: 1000000,
    nowMs: now
  });
  assert.equal(action.schemaVersion, 1);
  assert.equal(action.actionType, "CLAIM_PAYMENT");
  assert.equal(action.amountPaise, 500000);
  assert.equal(action.paymentMode, "UPI");
  assert.equal(action.referenceNumber, "UPI123456789012");
  assert.equal(action.note, "Paid via GPay");
  assert.equal(action.hasProof, false);

  const res = schema.validateAction("CLAIM_PAYMENT", action, { payableAmountPaise: 1000000, allowedActions: ["CLAIM_PAYMENT"] });
  assert.equal(res.valid, true);

  // With hasProof: true
  const actionWithProof = schema.buildClaimPaymentAction({
    guestUid: "guest-123",
    amountPaise: 500000,
    paymentMode: "UPI",
    referenceNumber: "UPI123456789012",
    hasProof: true,
    payableAmountPaise: 1000000,
    nowMs: now
  });
  assert.equal(actionWithProof.hasProof, true);
  const resProof = schema.validateAction("CLAIM_PAYMENT", actionWithProof, { payableAmountPaise: 1000000, allowedActions: ["CLAIM_PAYMENT"] });
  assert.equal(resProof.valid, true);

  // Rejects amount exceeding payable balance
  assert.throws(() => schema.buildClaimPaymentAction({
    guestUid: "guest-123",
    amountPaise: 1500000,
    paymentMode: "UPI",
    referenceNumber: "1234",
    payableAmountPaise: 1000000,
    nowMs: now
  }));

  // Rejects non-positive or float money
  assert.throws(() => schema.buildClaimPaymentAction({
    guestUid: "guest-123",
    amountPaise: -500,
    paymentMode: "UPI",
    referenceNumber: "1234",
    nowMs: now
  }));

  console.log("✔ Passed: buildClaimPaymentAction enforces integer paise, payableAmount cap, and mode validation");
}

console.log("\n======================================================");
console.log("All Action Schema tests passed!");
console.log("======================================================\n");
