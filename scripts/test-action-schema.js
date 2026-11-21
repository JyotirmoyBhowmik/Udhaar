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

// 5. Amount Conversion Suite (Rupees <-> Integer Paise)
{
  // Valid conversions
  assert.equal(schema.rupeesToPaise(100), 10000);
  assert.equal(schema.rupeesToPaise("100"), 10000);
  assert.equal(schema.rupeesToPaise(0.5), 50);
  assert.equal(schema.rupeesToPaise("0.05"), 5);
  assert.equal(schema.rupeesToPaise(19.99), 1999);
  assert.equal(schema.rupeesToPaise("2500.75"), 250075);

  // Round-trip conversion
  assert.equal(schema.paiseToRupees(10000), 100);
  assert.equal(schema.paiseToRupees(250075), 2500.75);
  assert.equal(schema.paiseToRupees(0), 0);

  // Invalid conversions rejected fail-closed
  assert.throws(() => schema.rupeesToPaise(0), /greater than zero/);
  assert.throws(() => schema.rupeesToPaise(-10), /greater than zero/);
  assert.throws(() => schema.rupeesToPaise("-5.50"), /greater than zero/);
  assert.throws(() => schema.rupeesToPaise(null), /required/);
  assert.throws(() => schema.rupeesToPaise(undefined), /required/);
  assert.throws(() => schema.rupeesToPaise(""), /required/);
  assert.throws(() => schema.rupeesToPaise("abc"), /Invalid amount/);
  assert.throws(() => schema.rupeesToPaise(NaN), /Invalid amount/);
  assert.throws(() => schema.rupeesToPaise(Infinity), /Invalid amount/);

  assert.throws(() => schema.paiseToRupees(-1), /non-negative integer/);
  assert.throws(() => schema.paiseToRupees(10.5), /non-negative integer/);
  assert.throws(() => schema.paiseToRupees("100"), /non-negative integer/);

  console.log("✔ Passed: Amount conversion accurately transforms currencies and fails closed on malformed values");
}

// 6. UPI Validation Suite (Handles, Masking, Intent URIs)
{
  // Valid UPI IDs
  assert.equal(schema.isValidUpi("user@okaxis"), true);
  assert.equal(schema.isValidUpi("merchant.store@okhdfcbank"), true);
  assert.equal(schema.isValidUpi("user_123-abc@paytm"), true);
  assert.equal(schema.isValidUpi("9876543210@ybl"), true);

  // Invalid UPI IDs
  assert.equal(schema.isValidUpi(""), false);
  assert.equal(schema.isValidUpi(null), false);
  assert.equal(schema.isValidUpi("plainstring"), false);
  assert.equal(schema.isValidUpi("@okaxis"), false);
  assert.equal(schema.isValidUpi("user@"), false);
  assert.equal(schema.isValidUpi("user@@okaxis"), false);
  assert.equal(schema.isValidUpi("user@ok axis"), false);
  assert.equal(schema.isValidUpi("a<script>@okaxis"), false);

  // Masking
  assert.equal(schema.maskUpi("jyotirmoy@okaxis"), "jyo***@okaxis");
  assert.equal(schema.maskUpi("ab@okaxis"), "ab***@okaxis");
  assert.equal(schema.maskUpi(null), "—");

  // URI Generation
  const uri = schema.buildUpiUri({
    upiId: "merchant@okhdfcbank",
    payeeName: "Jyotirmoy",
    amountPaise: 500000,
    note: "Udhaar Loan Return"
  });
  assert.equal(
    uri,
    "upi://pay?pa=merchant@okhdfcbank&pn=Jyotirmoy&cu=INR&am=5000.00&tn=Udhaar%20Loan%20Return"
  );

  // Reject URI build with invalid UPI
  assert.throws(() => schema.buildUpiUri({ upiId: "invalid", payeeName: "Test" }), /Valid UPI ID is required/);

  console.log("✔ Passed: UPI validation, privacy masking, and URI generation conform to banking standards");
}

console.log("\n======================================================");
console.log("All Action Schema tests passed!");
console.log("======================================================\n");
