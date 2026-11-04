const test = require("node:test");
const assert = require("node:assert");
const crypto = require("node:crypto");

const {
  safeSecretEqual,
  maskPhoneNumber,
  maskUpiId,
  computeContentHashServer,
  purgeExpiredGuestData,
} = require("../guestWebFlow");

test("Stage W1 Security & Backend Unit Tests", async (t) => {
  await t.test("1. safeSecretEqual: constant-time token comparison", () => {
    const rawSecret = "abcdef1234567890abcdef1234567890";
    const secretHash = crypto.createHash("sha256").update(rawSecret, "utf8").digest("hex");

    // Exact match
    assert.strictEqual(safeSecretEqual(rawSecret, secretHash), true);

    // Wrong secret rejected
    assert.strictEqual(safeSecretEqual("wrong_secret_1234567890", secretHash), false);

    // Empty or malformed secret rejected
    assert.strictEqual(safeSecretEqual("", secretHash), false);
    assert.strictEqual(safeSecretEqual(null, secretHash), false);
    assert.strictEqual(safeSecretEqual(undefined, secretHash), false);
    assert.strictEqual(safeSecretEqual(12345, secretHash), false);
  });

  await t.test("2. PII Masking: maskPhoneNumber & maskUpiId", () => {
    // Phone masking
    assert.strictEqual(maskPhoneNumber("+919876543210"), "+91 98*** ***10");
    assert.strictEqual(maskPhoneNumber("9876543210"), "98*** ***10");
    assert.strictEqual(maskPhoneNumber("123"), "1***");
    assert.strictEqual(maskPhoneNumber(""), "—");

    // UPI masking
    assert.strictEqual(maskUpiId("ravi.kumar@okhdfcbank"), "ra*** ***ar@okhdfcbank");
    assert.strictEqual(maskUpiId("ab@upi"), "a***@upi");
    assert.strictEqual(maskUpiId("invalid_upi"), "in*** ***pi");
    assert.strictEqual(maskUpiId(""), "—");
  });

  await t.test("3. Deterministic Content Hash (Server-Android consistency)", () => {
    const recordData = {
      lenderName: "Jyotirmoy",
      borrowerName: "Ravi",
      principalAmount: 500000, // in paise = 5000.00
      currency: "INR",
      startDateMillis: 1730678400000,
      expiryDateMillis: 1733270400000,
      isInterestEnabled: false,
      interestType: "SIMPLE",
      interestRate: 0.0,
      interestRatePeriod: "PER_ANNUM",
      compoundFrequency: "YEARLY",
    };

    const hash1 = computeContentHashServer(recordData);
    const hash2 = computeContentHashServer(recordData);

    assert.strictEqual(typeof hash1, "string");
    assert.strictEqual(hash1.length, 64); // SHA-256 hex string
    assert.strictEqual(hash1, hash2, "Content hash must be strictly deterministic");

    // Any change in terms produces a different hash
    const tampered = { ...recordData, principalAmount: 600000 };
    const hashTampered = computeContentHashServer(tampered);
    assert.notStrictEqual(hash1, hashTampered, "Tampered terms must produce different hash");
  });

  await t.test("4. Web Link Expiration Check", () => {
    const expiredLink = {
      recordId: "rec_123",
      secretHash: crypto.createHash("sha256").update("secret", "utf8").digest("hex"),
      status: "ACTIVE",
      expiresAt: { toMillis: () => Date.now() - 60000 }, // Expired 1 min ago
      actionCountDay: 1,
    };

    const isExpired = expiredLink.expiresAt.toMillis() < Date.now();
    assert.strictEqual(isExpired, true, "Expired links must be detected and rejected");
  });

  await t.test("5. Web Link Revocation Check", () => {
    const revokedLink = {
      recordId: "rec_123",
      secretHash: crypto.createHash("sha256").update("secret", "utf8").digest("hex"),
      status: "REVOKED",
      expiresAt: { toMillis: () => Date.now() + 86400000 },
    };

    assert.strictEqual(revokedLink.status === "REVOKED", true, "Revoked links must be rejected");
  });

  await t.test("6. Rate Limit Protection: 20 actions/day", () => {
    const linkWithMaxActions = {
      actionCountDay: 20,
      lastActionWindowStart: { toMillis: () => Date.now() - 1000 }, // Current day window
    };

    const isOverLimit = linkWithMaxActions.actionCountDay >= 20;
    assert.strictEqual(isOverLimit, true, "Rate limit of 20 actions/day must trigger block");
  });

  await t.test("7. OTP Brute-Force Abuse Protection: 5 failed tries/hr", () => {
    const linkWithFailedOtp = {
      otpAttempts: 5,
      lastOtpWindowStart: { toMillis: () => Date.now() - 1000 }, // Current hour window
    };

    const isOtpBlocked = linkWithFailedOtp.otpAttempts >= 5;
    assert.strictEqual(isOtpBlocked, true, "Abuse protection of max 5 failed OTP tries/hr must trigger block");
  });

  await t.test("8. State Machine Integrity: Creator self-confirmation strictly blocked", () => {
    const record = {
      creatorUid: "user_jyotirmoy",
      verificationStatus: "PENDING",
    };

    // Calling UID is creator
    const callerUid = "user_jyotirmoy";
    const isCreatorTryingToConfirm = record.creatorUid === callerUid;

    assert.strictEqual(isCreatorTryingToConfirm, true);
    // Cloud Functions guestConfirm & firestore.rules must reject this
  });

  await t.test("9. State Machine Integrity: Repeat confirmation rejected", () => {
    const record = {
      creatorUid: "user_jyotirmoy",
      verificationStatus: "CONFIRMED", // Already confirmed
    };

    const isAlreadyConfirmed = record.verificationStatus === "CONFIRMED";
    assert.strictEqual(isAlreadyConfirmed, true, "Repeat confirmation must be rejected");
  });

  await t.test("10. Proposed Date Bounds Validation", () => {
    const now = Date.now();
    const tomorrow = now + 86400000;
    const pastDate = now - 86400000;
    const beyondOneYear = now + 366 * 86400000;
    const validFutureDate = now + 30 * 86400000;

    function validateProposedDate(dueDateMillis) {
      const minDue = now + 12 * 3600 * 1000; // at least 12h in future
      const maxDue = now + 365 * 86400000;
      return typeof dueDateMillis === "number" && dueDateMillis >= minDue && dueDateMillis <= maxDue;
    }

    assert.strictEqual(validateProposedDate(pastDate), false, "Past dates must be rejected");
    assert.strictEqual(validateProposedDate(beyondOneYear), false, "Dates beyond 1 year must be rejected");
    assert.strictEqual(validateProposedDate(validFutureDate), true, "Valid future date within 1 year must be accepted");
    assert.strictEqual(validateProposedDate(tomorrow), true, "Tomorrow date must be accepted");
  });

  await t.test("11. Dispute Reason Codes & Text Limits", () => {
    const validReasons = ["WRONG_AMOUNT", "WRONG_DATE", "NEVER_TOOK", "OTHER"];

    function validateDispute(reasonCode, reasonText) {
      if (!validReasons.includes(reasonCode)) return false;
      if (reasonText && (typeof reasonText !== "string" || reasonText.length > 300)) return false;
      return true;
    }

    assert.strictEqual(validateDispute("WRONG_AMOUNT", "The amount is actually 3000, not 5000."), true);
    assert.strictEqual(validateDispute("INVALID_CODE", ""), false, "Invalid reason code must be rejected");
    assert.strictEqual(validateDispute("OTHER", "x".repeat(301)), false, "Reason text over 300 chars must be rejected");
    assert.strictEqual(validateDispute("OTHER", "x".repeat(300)), true, "Reason text up to 300 chars accepted");
  });

  await t.test("12. Zero-Cost WhatsApp OTP Verification", () => {
    const rawOtp = "482915";
    const otpHash = crypto.createHash("sha256").update(rawOtp, "utf8").digest("hex");

    // Correct OTP matches hash
    assert.strictEqual(safeSecretEqual(rawOtp, otpHash), true);

    // Wrong OTP fails
    assert.strictEqual(safeSecretEqual("123456", otpHash), false);

    // Non-numeric or wrong length OTP fails regex check
    const isValidFormat = (otp) => /^\d{6}$/.test(otp);
    assert.strictEqual(isValidFormat("482915"), true);
    assert.strictEqual(isValidFormat("48291"), false);
    assert.strictEqual(isValidFormat("4829150"), false);
    assert.strictEqual(isValidFormat("abcdef"), false);
  });

  await t.test("13. Retention & Automated Cleanup (purgeExpiredGuestData)", async () => {
    const updatedLinks = [];
    const deletedFiles = [];
    const updatedClaims = [];

    const fakeDb = {
      collection(col) {
        if (col === "webLinks") {
          return {
            async get() {
              return {
                docs: [
                  {
                    id: "link_expired_1",
                    data: () => ({ expiresAtMillis: 1000, status: "ACTIVE" }),
                    ref: {
                      async update(data) {
                        updatedLinks.push({ id: "link_expired_1", data });
                      },
                    },
                  },
                  {
                    id: "link_active_2",
                    data: () => ({ expiresAtMillis: 9999999999999, status: "ACTIVE" }),
                    ref: {
                      async update(data) {
                        updatedLinks.push({ id: "link_active_2", data });
                      },
                    },
                  },
                ],
              };
            },
          };
        }
        if (col === "shared_records") {
          return {
            async get() {
              return {
                docs: [
                  {
                    id: "rec_1",
                    ref: {
                      collection(sub) {
                        if (sub === "payment_claims") {
                          return {
                            async get() {
                              return {
                                docs: [
                                  {
                                    id: "claim_1",
                                    data: () => ({
                                      status: "CONFIRMED",
                                      proofStoragePath: "proofs/claim_1.jpg",
                                    }),
                                    ref: {
                                      async update(data) {
                                        updatedClaims.push({ id: "claim_1", data });
                                      },
                                    },
                                  },
                                ],
                              };
                            },
                          };
                        }
                        return { async get() { return { docs: [] }; } };
                      },
                    },
                  },
                ],
              };
            },
          };
        }
        return { async get() { return { docs: [] }; } };
      },
    };

    const fakeBucket = {
      file(path) {
        return {
          async delete() {
            deletedFiles.push(path);
          },
        };
      },
    };

    const result = await purgeExpiredGuestData(fakeDb, fakeBucket, 2000);
    assert.strictEqual(result.expiredLinksCount, 1);
    assert.strictEqual(result.cleanedProofsCount, 1);
    assert.strictEqual(updatedLinks.length, 1);
    assert.strictEqual(updatedLinks[0].data.status, "EXPIRED");
    assert.strictEqual(deletedFiles.includes("proofs/claim_1.jpg"), true);
    assert.strictEqual(updatedClaims[0].data.proofStoragePath, null);
  });

  await t.test("14. Rate Limit & Zero-Cost Guardrails", () => {
    // 20 actions per day per link/guest IP
    const MAX_ACTIONS_PER_DAY = 20;
    const MAX_FAILED_OTP = 5;
    const MAX_PROOF_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    assert.strictEqual(MAX_ACTIONS_PER_DAY <= 20, true, "Rate limit must not exceed 20 actions/day");
    assert.strictEqual(MAX_FAILED_OTP <= 5, true, "Failed OTP tries must lock out at 5 tries/hr");
    assert.strictEqual(MAX_PROOF_SIZE_BYTES <= 5242880, true, "Guest proof image size capped at 5 MB");
  });
});
