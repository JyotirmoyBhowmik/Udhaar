/**
 * Stage S6 Hardened Guest Flow & Account Upgrade Bridge Tests (functions/test/guest_s6.test.js)
 *
 * Validates:
 * 1. Capability token generation, SHA-256 hashing, and 30-day TTL expiry.
 * 2. Multi-layer rate limiting (20 req/hr per IP).
 * 3. WhatsApp 6-digit OTP verification and 5-attempt brute-force lockout (15 minutes).
 * 4. Anti-replay and idempotency guarantees for guest actions.
 * 5. Signed GCS upload URL parameters (5MB limit, MIME types, 15m expiry).
 * 6. Account Upgrade Bridge: reciprocal record creation, role reversal, and zero re-entry of data.
 * 7. Express API integration: RESTful endpoints and web/u/guest.js backward compatibility.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
const { createApiApp } = require("../src/app");
const {
  resetLinksStore,
  createCapabilityLink,
  getGuestRecordView,
  requestGuestOtp,
  verifyGuestOtp,
  submitGuestAction,
  generateProofUploadUrl,
  upgradeGuestToAccount,
  GUEST_LINK_TTL_MS,
  MAX_PROOF_SIZE_BYTES
} = require("../src/links/linkService");
const { createRecord, resetRecordsStore, getRecord } = require("../src/ledger/recordService");
const { resetPeopleStore } = require("../src/ledger/peopleService");
const { resetAuthStore, completeSignup } = require("../src/auth/authService");
const { resetDailyBudget } = require("../src/otp/otpService");
const {
  getLinkRepository,
  getOtpRepository,
  resetTestRepositories
} = require("../src/repositories");
const { fakeWhatsApp } = require("../src/providers");
const { setTestServerSecret } = require("../guestWebFlow");

// Setup test environment
test.beforeEach(async () => {
  setTestServerSecret("test_server_secret_for_guest_s6_testing_32_bytes_long!");
  resetTestRepositories();
  await resetLinksStore();
  await resetRecordsStore();
  await resetPeopleStore();
  await resetAuthStore();
  await resetDailyBudget();
  fakeWhatsApp.reset();
});

test("Stage S6: 1. Capability Token Generation & 30-Day TTL Expiry", async () => {
  const uid = "usr_creator_01";
  const record = await createRecord({
    uid,
    recordData: {
      partyName: "Rahul Sharma",
      partyPhone: "+919876543210",
      counterpartyRole: "BORROWER", // Creator is LENDER
      principalAmountPaise: 500000, // ₹5,000.00
      dueDateMs: Date.now() + 14 * 86400000,
      note: "Business equipment advance"
    }
  });

  const link = await createCapabilityLink({
    uid,
    recordId: record.recordId,
    expiresInDays: 30
  });

  // Verify rawToken entropy and hash
  assert.ok(link.rawToken && link.rawToken.length >= 32, "Raw token must have sufficient entropy");
  assert.ok(link.capabilityHash && link.capabilityHash.length === 64, "Capability hash must be 64-char hex SHA-256");
  assert.equal(link.guestUrl, `https://udhaar.jyotirmoyb.com/u/${record.recordId}#${link.rawToken}`);

  // Verify 30-day TTL calculation
  const expectedExpiry = link.expiresAtMs;
  assert.ok(expectedExpiry > Date.now() + 29 * 86400000, "Link must expire ~30 days in future");

  // Verify guest view with valid token
  const view = await getGuestRecordView({
    recordId: record.recordId,
    rawToken: link.rawToken,
    clientIp: "10.0.0.1"
  });
  assert.equal(view.recordId, record.recordId);
  assert.equal(view.principalAmountPaise, 500000);
  assert.equal(view.directionForRecipient, "YOU_OWE"); // Creator is LENDER, so guest owes

  // Verify expired link fails closed
  const linkRepo = getLinkRepository();
  await linkRepo.updateWebLink(record.recordId, { expiresAtMs: Date.now() - 1000 }); // Expired 1 second ago

  await assert.rejects(
    async () => {
      await getGuestRecordView({
        recordId: record.recordId,
        rawToken: link.rawToken,
        clientIp: "10.0.0.1"
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_LINK_EXPIRED");
      assert.equal(err.status, 403);
      return true;
    }
  );
});

test("Stage S6: 2. Multi-Layer IP Rate Limiting (20 requests per hour)", async () => {
  const uid = "usr_creator_02";
  const record = await createRecord({
    uid,
    recordData: {
      partyName: "Anita Roy",
      partyPhone: "+919876543211",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 250000
    }
  });

  const link = await createCapabilityLink({ uid, recordId: record.recordId });
  const testIp = "192.168.1.100";

  // First 20 requests from testIp must succeed
  for (let i = 0; i < 20; i++) {
    const view = await getGuestRecordView({
      recordId: record.recordId,
      rawToken: link.rawToken,
      clientIp: testIp
    });
    assert.ok(view);
  }

  // 21st request from same IP must be throttled with HTTP 429
  await assert.rejects(
    async () => {
      await getGuestRecordView({
        recordId: record.recordId,
        rawToken: link.rawToken,
        clientIp: testIp
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_RATE_LIMIT_EXCEEDED");
      assert.equal(err.status, 429);
      return true;
    }
  );

  // A different IP address is not affected
  const otherIpView = await getGuestRecordView({
    recordId: record.recordId,
    rawToken: link.rawToken,
    clientIp: "192.168.1.101"
  });
  assert.ok(otherIpView);
});

test("Stage S6: 3. WhatsApp OTP Verification & 5-Attempt Brute-Force Lockout (15 mins)", async () => {
  const uid = "usr_creator_03";
  const record = await createRecord({
    uid,
    recordData: {
      partyName: "Suresh Das",
      partyPhone: "+919876543212",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 100000
    }
  });

  const link = await createCapabilityLink({ uid, recordId: record.recordId });

  // 1. Request OTP challenge
  const otpRes = await requestGuestOtp({
    recordId: record.recordId,
    rawToken: link.rawToken,
    clientIp: "10.0.0.2"
  });
  assert.ok(otpRes.flowId);
  assert.equal(otpRes.channel, "WHATSAPP");

  // Retrieve actual generated OTP for this test via fakeWhatsApp provider
  const correctCode = fakeWhatsApp.getLastCodeFor(record.partyPhone);
  assert.ok(correctCode && correctCode.length === 6, "Must generate valid 6-digit WhatsApp OTP");
  const otpRepo = getOtpRepository();
  const challenge = await otpRepo.getChallengeByFlowId(otpRes.flowId, "GUEST_VERIFY");
  assert.ok(challenge, "Challenge must exist in challenge store");

  // 2. Submit 4 incorrect attempts
  for (let i = 1; i <= 4; i++) {
    await assert.rejects(
      async () => {
        await verifyGuestOtp({
          recordId: record.recordId,
          rawToken: link.rawToken,
          code: "000000",
          flowId: otpRes.flowId,
          clientIp: "10.0.0.2"
        });
      },
      (err) => {
        assert.equal(err.code, "ERR_INVALID_CODE");
        assert.equal(err.status, 403);
        assert.equal(err.remainingAttempts, 5 - i);
        return true;
      }
    );
  }

  // 3. 5th incorrect attempt must trigger 15-minute lockout (HTTP 429)
  await assert.rejects(
    async () => {
      await verifyGuestOtp({
        recordId: record.recordId,
        rawToken: link.rawToken,
        code: "000000",
        flowId: otpRes.flowId,
        clientIp: "10.0.0.2"
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_ACCOUNT_LOCKED");
      assert.equal(err.status, 429);
      assert.equal(err.retryAfterSeconds, 15 * 60);
      return true;
    }
  );

  // Even with the correct code, attempts while locked must be rejected
  await assert.rejects(
    async () => {
      await verifyGuestOtp({
        recordId: record.recordId,
        rawToken: link.rawToken,
        code: correctCode,
        flowId: otpRes.flowId,
        clientIp: "10.0.0.2"
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_ACCOUNT_LOCKED");
      assert.equal(err.status, 429);
      return true;
    }
  );

  // 4. Reset lockout manually for verification test
  const linkRepo = getLinkRepository();
  await linkRepo.updateWebLink(record.recordId, { lockedUntilMs: 0, failedOtpAttempts: 0 });
  await otpRepo.updateChallenge(challenge.challengeId, { lockedUntilMs: null, attempts: 0 });

  const successVerify = await verifyGuestOtp({
    recordId: record.recordId,
    rawToken: link.rawToken,
    code: correctCode,
    flowId: otpRes.flowId,
    clientIp: "10.0.0.2"
  });
  assert.equal(successVerify.verified, true);
  assert.equal(successVerify.trustLevel, "Code checked");
  const updatedLink = await linkRepo.getWebLink(record.recordId);
  assert.equal(updatedLink.isPhoneVerified, true);
});

test("Stage S6: 4. Anti-Replay Guarantees & Guest Actions", async () => {
  const uid = "usr_creator_04";
  const record = await createRecord({
    uid,
    recordData: {
      partyName: "Manoj Sen",
      partyPhone: "+919876543213",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 750000
    }
  });

  const link = await createCapabilityLink({ uid, recordId: record.recordId });
  const nonce = "nonce_client_uuid_12345";

  // 1. Submit CONFIRM_RECORD action with nonce
  const confirmResult1 = await submitGuestAction({
    recordId: record.recordId,
    rawToken: link.rawToken,
    action: "CONFIRM_RECORD",
    nonce,
    clientIp: "10.0.0.3"
  });
  assert.equal(confirmResult1.success, true);
  assert.equal(confirmResult1.status, "CONFIRMED");

  // 2. Anti-replay: repeating with same nonce returns cached idempotent result
  const confirmResult2 = await submitGuestAction({
    recordId: record.recordId,
    rawToken: link.rawToken,
    action: "CONFIRM_RECORD",
    nonce,
    clientIp: "10.0.0.3"
  });
  assert.equal(confirmResult2.idempotentReplay, true);
  assert.equal(confirmResult2.status, "CONFIRMED");

  // 3. Propose Date action
  const proposeResult = await submitGuestAction({
    recordId: record.recordId,
    rawToken: link.rawToken,
    action: "PROPOSE_DATE",
    payload: {
      proposedDateMs: Date.now() + 7 * 86400000,
      note: "Will pay next Monday"
    },
    nonce: "nonce_propose_01",
    clientIp: "10.0.0.3"
  });
  assert.equal(proposeResult.success, true);
  assert.equal(proposeResult.note, "Will pay next Monday");

  // 4. Submit Payment Claim with Proof
  const claimResult = await submitGuestAction({
    recordId: record.recordId,
    rawToken: link.rawToken,
    action: "PAYMENT_CLAIM",
    payload: {
      amountPaise: 250000,
      paymentMode: "UPI",
      utrNumber: "UPI1234567890",
      proof: {
        mimeType: "image/jpeg",
        sizeBytes: 150000,
        storagePath: `guest_proofs/${record.recordId}/proof.jpg`
      }
    },
    nonce: "nonce_claim_01",
    clientIp: "10.0.0.3"
  });
  assert.equal(claimResult.success, true);
  assert.equal(claimResult.amountPaise, 250000);
  assert.equal(claimResult.status, "PENDING_LENDER_APPROVAL");
});

test("Stage S6: 5. Signed GCS Upload URL (5MB limit & MIME types)", async () => {
  const uid = "usr_creator_05";
  const record = await createRecord({
    uid,
    recordData: {
      partyName: "Kavita Dey",
      partyPhone: "+919876543214",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 400000
    }
  });

  const link = await createCapabilityLink({ uid, recordId: record.recordId });

  // 1. Rejects file size > 5MB (5,242,880 bytes)
  await assert.rejects(
    async () => {
      await generateProofUploadUrl({
        recordId: record.recordId,
        rawToken: link.rawToken,
        contentType: "image/jpeg",
        sizeBytes: 5242881, // 5MB + 1 byte
        clientIp: "10.0.0.4"
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_FILE_TOO_LARGE");
      assert.equal(err.status, 400);
      return true;
    }
  );

  // 2. Rejects disallowed MIME types (e.g. executables or html)
  await assert.rejects(
    async () => {
      await generateProofUploadUrl({
        recordId: record.recordId,
        rawToken: link.rawToken,
        contentType: "text/html",
        sizeBytes: 1024,
        clientIp: "10.0.0.4"
      });
    },
    (err) => {
      assert.equal(err.code, "ERR_INVALID_PROOF_TYPE");
      assert.equal(err.status, 400);
      return true;
    }
  );

  // 3. Accepts valid JPEG, PNG, WEBP, or PDF proofs <= 5MB
  const uploadInfo = await generateProofUploadUrl({
    recordId: record.recordId,
    rawToken: link.rawToken,
    contentType: "image/webp",
    sizeBytes: 350000,
    ext: "webp",
    clientIp: "10.0.0.4"
  });

  assert.ok(uploadInfo.uploadUrl.startsWith("https://storage.googleapis.com/"));
  assert.ok(uploadInfo.storagePath.includes(`guest_proofs/${record.recordId}/`));
  assert.equal(uploadInfo.maxSizeBytes, MAX_PROOF_SIZE_BYTES);
  assert.equal(uploadInfo.requiredHeaders["Content-Type"], "image/webp");
  assert.equal(uploadInfo.requiredHeaders["x-goog-content-length-range"], "0,5242880");
});

test("Stage S6: 6. Account Upgrade Bridge (Reciprocal ledger creation without data re-entry)", async () => {
  // Creator: Alice (LENDER)
  const creatorUid = "usr_alice_lender";
  const record = await createRecord({
    uid: creatorUid,
    recordData: {
      partyName: "Bob Counterparty",
      partyPhone: "+919876543215",
      counterpartyRole: "BORROWER", // Alice lent money to Bob
      principalAmountPaise: 800000, // ₹8,000.00
      dueDateMs: Date.now() + 30 * 86400000,
      note: "Shared home appliance expense"
    }
  });

  const link = await createCapabilityLink({ uid: creatorUid, recordId: record.recordId });

  // Guest Bob confirms the record
  await submitGuestAction({
    recordId: record.recordId,
    rawToken: link.rawToken,
    action: "CONFIRM_RECORD",
    clientIp: "10.0.0.5"
  });

  // Bob now decides to open an account in Udhaar
  const bobUid = "usr_bob_account";

  // Upgrade guest link to Bob's full account
  const upgradeResult = await upgradeGuestToAccount({
    recordId: record.recordId,
    rawToken: link.rawToken,
    targetUid: bobUid,
    clientIp: "10.0.0.5"
  });

  assert.equal(upgradeResult.success, true);
  assert.equal(upgradeResult.upgraded, true);
  assert.equal(upgradeResult.targetUid, bobUid);

  const bobRecord = upgradeResult.record;
  assert.ok(bobRecord.recordId, "Bob must receive a new unique record ID");
  assert.equal(bobRecord.principalAmountPaise, 800000, "Must preserve exact principal paise");
  assert.equal(bobRecord.remainingAmountPaise, 800000, "Must preserve exact remaining paise");
  assert.equal(bobRecord.note, "Shared home appliance expense", "Must preserve note");
  assert.equal(bobRecord.counterpartyRole, "LENDER", "Role must be inverted: for Bob, Alice is LENDER");
  assert.equal(bobRecord.linkedRemoteRecordId, record.recordId, "Must link to original record");
  assert.equal(bobRecord.status, "CONFIRMED", "Must preserve confirmed status");

  // Idempotent: repeating upgrade returns existing reciprocal record
  const repeatUpgrade = await upgradeGuestToAccount({
    recordId: record.recordId,
    rawToken: link.rawToken,
    targetUid: bobUid,
    clientIp: "10.0.0.5"
  });
  assert.equal(repeatUpgrade.alreadyUpgraded, true);
  assert.equal(repeatUpgrade.record.recordId, bobRecord.recordId);
});

test("Stage S6: 7. Central API Router Integration & Backward Compatibility Handlers", async () => {
  const app = createApiApp();
  const server = http.createServer(app);

  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    const creatorUid = "usr_creator_07";
    const record = await createRecord({
      uid: creatorUid,
      recordData: {
        partyName: "Deepak Verma",
        partyPhone: "+919876543216",
        counterpartyRole: "BORROWER",
        principalAmountPaise: 300000
      }
    });

    const link = await createCapabilityLink({ uid: creatorUid, recordId: record.recordId });

    // Test 1: GET /api/v1/links/:recordId with X-Guest-Token
    const resView = await fetch(`${baseUrl}/api/v1/links/${record.recordId}`, {
      headers: { "X-Guest-Token": link.rawToken }
    });
    assert.equal(resView.status, 200);
    const viewData = await resView.json();
    assert.equal(viewData.recordId, record.recordId);
    assert.equal(viewData.principalAmountPaise, 300000);

    // Test 2: Backward compatibility POST /api/getGuestRecord
    const resLegacy = await fetch(`${baseUrl}/api/getGuestRecord`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        recordId: record.recordId,
        secret: link.rawToken
      })
    });
    assert.equal(resLegacy.status, 200);
    const legacyData = await resLegacy.json();
    assert.equal(legacyData.success, true);
    assert.equal(legacyData.record.recordId, record.recordId);

    // Test 3: Backward compatibility POST /api/guestConfirm
    const resConfirm = await fetch(`${baseUrl}/api/guestConfirm`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        recordId: record.recordId,
        token: link.rawToken
      })
    });
    assert.equal(resConfirm.status, 200);
    const confirmData = await resConfirm.json();
    assert.equal(confirmData.success, true);
    assert.equal(confirmData.status, "CONFIRMED");

    // Test 4: Backward compatibility POST /api/guestProofUploadUrl
    const resUpload = await fetch(`${baseUrl}/api/guestProofUploadUrl`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        recordId: record.recordId,
        token: link.rawToken,
        mimeType: "application/pdf",
        sizeBytes: 102400
      })
    });
    assert.equal(resUpload.status, 200);
    const uploadData = await resUpload.json();
    assert.equal(uploadData.success, true);
    assert.equal(uploadData.maxSizeBytes, 5242880);
    assert.ok(uploadData.uploadUrl.startsWith("https://storage.googleapis.com/"));
  } finally {
    server.close();
  }
});
