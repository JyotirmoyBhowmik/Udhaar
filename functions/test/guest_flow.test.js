const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");

const {
  UDHAAR_SERVER_SECRET,
  setTestServerSecret,
  resolveServerSecret,
  normalizePhoneDigits,
  computeSaltedPhoneHash,
  hashGuestToken,
  verifyGuestCapabilityToken,
  buildGuestActionUpdate
} = require("../guestWebFlow");

const {
  STORAGE_LIFECYCLE_RULE_90_DAYS,
  buildProofSignedUrlOptions,
  ensureBucketLifecycle90Days
} = require("../guestWebFlowEndpoints");

test("computeSaltedPhoneHash fails closed when UDHAAR_SERVER_SECRET is missing and works when injected by test", () => {
  const prevEnv = process.env.UDHAAR_SERVER_SECRET;
  delete process.env.UDHAAR_SERVER_SECRET;
  setTestServerSecret(null);

  try {
    // Secret Manager param is registered via defineSecret("UDHAAR_SERVER_SECRET")
    assert.ok(UDHAAR_SERVER_SECRET, "Expected UDHAAR_SERVER_SECRET defineSecret param to be exported");

    // Must fail closed when no secret is configured
    assert.throws(
      () => resolveServerSecret(),
      /UDHAAR_SERVER_SECRET is not configured\. Failing closed\./
    );
    assert.throws(
      () => computeSaltedPhoneHash("+91 98765-43210"),
      /UDHAAR_SERVER_SECRET is not configured\. Failing closed\./
    );

    // Inject test secret and verify canonicalization + deterministic HMAC
    setTestServerSecret("injected-unit-test-secret-2026");
    const a = computeSaltedPhoneHash("+91 98765-43210");
    const b = computeSaltedPhoneHash("09876543210");
    const c = computeSaltedPhoneHash("9876543210");

    assert.equal(a.normalizedDigits, "9876543210");
    assert.equal(a.phoneHmac, b.phoneHmac);
    assert.equal(b.phoneHmac, c.phoneHmac);
    assert.equal(a.phoneHmac.length, 64);

    // Changing the secret changes the HMAC (prevents rainbow table enumeration)
    const differentSecret = computeSaltedPhoneHash("9876543210", "another-secret-key");
    assert.notEqual(a.phoneHmac, differentSecret.phoneHmac);
  } finally {
    setTestServerSecret(null);
    if (prevEnv !== undefined) {
      process.env.UDHAAR_SERVER_SECRET = prevEnv;
    }
  }
});

test("normalizePhoneDigits rejects malformed inputs", () => {
  assert.throws(() => normalizePhoneDigits(""), /INVALID_PHONE/);
  assert.throws(() => normalizePhoneDigits("12345"), /INVALID_PHONE/);
});

test("buildProofSignedUrlOptions includes contentType and x-goog-content-length-range: 0,5242880", async () => {
  const nowMs = 1_700_000_000_000;
  const options = buildProofSignedUrlOptions("image/jpeg", nowMs);

  assert.equal(options.version, "v4");
  assert.equal(options.action, "write");
  assert.equal(options.contentType, "image/jpeg");
  assert.equal(options.expires, nowMs + 15 * 60 * 1000);
  assert.deepEqual(options.extensionHeaders, {
    "x-goog-content-length-range": "0,5242880"
  });

  assert.throws(
    () => buildProofSignedUrlOptions("application/x-msdownload", nowMs),
    /INVALID_PROOF_TYPE/
  );

  // Verify 90-day lifecycle rule constant and bucket helper
  assert.equal(STORAGE_LIFECYCLE_RULE_90_DAYS.action.type, "Delete");
  assert.equal(STORAGE_LIFECYCLE_RULE_90_DAYS.condition.age, 90);
  assert.deepEqual(STORAGE_LIFECYCLE_RULE_90_DAYS.condition.matchesPrefix, [
    "guest_proofs/",
    "payment_proofs/"
  ]);

  let capturedMetadata = null;
  const fakeBucket = {
    async setMetadata(meta) {
      capturedMetadata = meta;
    }
  };
  await ensureBucketLifecycle90Days(fakeBucket);
  assert.deepEqual(capturedMetadata, {
    lifecycle: {
      rule: [STORAGE_LIFECYCLE_RULE_90_DAYS]
    }
  });
});

test("verifyGuestCapabilityToken enforces hash match, 30-day TTL, and single-use consumption", () => {
  const rawToken = crypto.randomBytes(24).toString("hex");
  const tokenHash = hashGuestToken(rawToken);
  const createdAt = 1_700_000_000_000;

  const baseRecord = {
    recordId: "rec_123",
    principalAmountPaise: 250000,
    amountPaidPaise: 50000,
    status: "PENDING_CONFIRMATION",
    createdAt,
    guestTokenHash: tokenHash
  };

  // Valid token within 30 days
  const validRes = verifyGuestCapabilityToken(baseRecord, rawToken, createdAt + 1000);
  assert.equal(validRes.valid, true);

  // Wrong token rejected
  const wrongRes = verifyGuestCapabilityToken(baseRecord, "wrong_token_value_1234567890", createdAt + 1000);
  assert.equal(wrongRes.valid, false);
  assert.equal(wrongRes.reason, "TOKEN_MISMATCH");

  // Expired after 31 days
  const expiredRes = verifyGuestCapabilityToken(
    baseRecord,
    rawToken,
    createdAt + 31 * 24 * 60 * 60 * 1000
  );
  assert.equal(expiredRes.valid, false);
  assert.equal(expiredRes.reason, "TOKEN_EXPIRED");

  // Consumed & settled token rejected
  const consumedRes = verifyGuestCapabilityToken(
    { ...baseRecord, status: "SETTLED", guestTokenConsumedAt: createdAt + 500 },
    rawToken,
    createdAt + 1000
  );
  assert.equal(consumedRes.valid, false);
  assert.equal(consumedRes.reason, "TOKEN_ALREADY_CONSUMED");
});

test("buildGuestActionUpdate handles CONFIRM_RECORD, DISPUTE_RECORD, and SUBMIT_PAYMENT_CLAIM with proof validation", () => {
  const rawToken = crypto.randomBytes(24).toString("hex");
  const tokenHash = hashGuestToken(rawToken);
  const nowMs = 1_700_000_050_000;

  const record = {
    recordId: "rec_456",
    principalAmountPaise: 500000,
    amountPaidPaise: 100000,
    status: "PENDING_CONFIRMATION",
    createdAt: nowMs - 10_000,
    guestTokenHash: tokenHash,
    pendingPaymentClaims: []
  };

  // 1. Confirm Record
  const confirmUpdate = buildGuestActionUpdate(
    record,
    rawToken,
    { action: "CONFIRM_RECORD" },
    nowMs
  );
  assert.equal(confirmUpdate.status, "CONFIRMED");
  assert.equal(confirmUpdate.confirmedVia, "GUEST_WEB_LINK");

  // 2. Dispute Record
  const disputeUpdate = buildGuestActionUpdate(
    record,
    rawToken,
    { action: "DISPUTE_RECORD", reason: "Amount was 4,000 not 5,000" },
    nowMs
  );
  assert.equal(disputeUpdate.status, "DISPUTED");
  assert.equal(disputeUpdate.disputeReason, "Amount was 4,000 not 5,000");

  // 3. Submit Payment Claim with valid proof
  const claimUpdate = buildGuestActionUpdate(
    record,
    rawToken,
    {
      action: "SUBMIT_PAYMENT_CLAIM",
      amountPaise: 200000,
      note: "Paid via PhonePe",
      utrNumber: "UTR9988776655",
      proof: {
        mimeType: "image/jpeg",
        sizeBytes: 245_000,
        storagePath: "payment_proofs/rec_456/receipt1.jpg",
        downloadUrl: "https://storage.googleapis.com/example/receipt1.jpg"
      }
    },
    nowMs
  );
  assert.equal(claimUpdate.hasPendingPaymentClaim, true);
  assert.equal(claimUpdate.pendingPaymentClaims.length, 1);
  assert.equal(claimUpdate.pendingPaymentClaims[0].amountPaise, 200000);
  assert.equal(claimUpdate.pendingPaymentClaims[0].utrNumber, "UTR9988776655");

  // 4. Reject oversized proof (> 5 MB)
  assert.throws(
    () =>
      buildGuestActionUpdate(
        record,
        rawToken,
        {
          action: "SUBMIT_PAYMENT_CLAIM",
          amountPaise: 50000,
          proof: {
            mimeType: "image/png",
            sizeBytes: 6 * 1024 * 1024,
            storagePath: "payment_proofs/rec_456/huge.png"
          }
        },
        nowMs
      ),
    /INVALID_PROOF_SIZE/
  );
});
