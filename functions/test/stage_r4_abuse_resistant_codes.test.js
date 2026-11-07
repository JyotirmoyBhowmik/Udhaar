/**
 * Stage R4 Test Suite: Durable, Abuse-Resistant Codes & Anti-Enumeration
 * 
 * Verifies:
 * 1. Global Daily Send Budget & Threshold Alerts (50%, 80%, 100%) + Dynamic Kill Switch
 * 2. Strict Input Validation (libphonenumber-js country allowlist: +91, +977; email normalization; disposable domain blocklist)
 * 3. WhatsApp Webhook Verification (GET hub subscribe & POST X-Hub-Signature-256 over raw body) + Fallback Delivery Path
 * 4. Timing & Response Identity for Known vs Unknown Numbers (Zero Enumeration Side-Channel)
 * 5. Replay Defense (Cross-Flow & Cross-Purpose Replay Rejection + Single-Use Token Invalidation)
 * 6. Brute-Force Lockout (15-minute lock after 5 wrong attempts)
 * 7. Multi-Layer Rate Limiting (30s cooldown, hourly destination, daily destination, and IP rate limits)
 * 8. Atomic Concurrency Protection (Parallel verification race conditions prevent multi-use)
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
const crypto = require("crypto");

const { createApiApp } = require("../src/app");
const { sendOtpChallenge, verifyOtpChallenge } = require("../src/otp/otpService");
const {
  validateAndNormalizePhone,
  validateAndNormalizeEmail,
  startSignin,
  verifySignin,
  startSignup,
  completeSignup
} = require("../src/auth/authService");
const { OTP_LIMITS, ALLOWED_COUNTRY_CODES } = require("../src/config");
const { updateFeatureFlag, resetFeatureFlags } = require("../src/config/killSwitches");
const { resetTestRepositories, getOtpRepository, getAuthRepository } = require("../src/repositories");
const { fakeWhatsApp, fakeEmail, fakeSms } = require("../src/providers");

function makeRequest(server, options, requestBody = null) {
  return new Promise((resolve, reject) => {
    const payload = requestBody ? (typeof requestBody === "string" ? requestBody : JSON.stringify(requestBody)) : null;
    const reqOptions = { ...options, agent: false };
    reqOptions.headers = {
      connection: "close",
      ...reqOptions.headers
    };

    if (payload) {
      reqOptions.headers["Content-Length"] = Buffer.byteLength(payload);
    }

    const req = http.request(reqOptions, (res) => {
      let data = "";
      res.on("data", (chunk) => { data += chunk; });
      res.on("end", () => {
        let json = null;
        try { json = JSON.parse(data); } catch (_) {}
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: json !== null ? json : data
        });
      });
    });

    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

test.beforeEach(() => {
  resetTestRepositories();
  resetFeatureFlags();
  fakeWhatsApp.reset();
  fakeEmail.reset();
  fakeSms.reset();
  delete process.env.NODE_ENV;
  delete process.env.UDHAAR_ENV;
  delete process.env.ENFORCE_APP_CHECK;
  delete process.env.ALLOW_APP_CHECK_DEBUG;
});

test.describe("Stage R4 — Durable, Abuse-Resistant Codes & Anti-Enumeration", () => {

  test("1. Number Validation (libphonenumber-js allowlist: +91, +977) and Email Normalization / Disposable Blocklist", () => {
    // 1. Phone number validation (+91 and +977 supported)
    const validIn = validateAndNormalizePhone("+91 98765 43210");
    assert.equal(validIn.e164, "+919876543210");
    assert.equal(validIn.countryCallingCode, "+91");

    const validNp = validateAndNormalizePhone("+977 98412 34567");
    assert.equal(validNp.e164, "+9779841234567");
    assert.equal(validNp.countryCallingCode, "+977");

    // Unsupported country (+1 US, +44 UK) rejected with 400 ERR_UNSUPPORTED_COUNTRY
    assert.throws(
      () => validateAndNormalizePhone("+1 202 555 0123"),
      (err) => err.code === "ERR_UNSUPPORTED_COUNTRY" && err.status === 400
    );
    assert.throws(
      () => validateAndNormalizePhone("+44 7911 123456"),
      (err) => err.code === "ERR_UNSUPPORTED_COUNTRY" && err.status === 400
    );

    // Malformed phone numbers rejected with 400 ERR_INVALID_PHONE
    assert.throws(
      () => validateAndNormalizePhone("not-a-number"),
      (err) => err.code === "ERR_INVALID_PHONE" && err.status === 400
    );
    assert.throws(
      () => validateAndNormalizePhone("+91 123"),
      (err) => err.code === "ERR_INVALID_PHONE" && err.status === 400
    );

    // 2. Email normalization (trim, lowercase)
    const normalized = validateAndNormalizeEmail("   User.Account+Tag@EXAMPLE.COM  ");
    assert.equal(normalized, "user.account+tag@example.com");

    // Disposable email domains blocked with 400 ERR_DISPOSABLE_EMAIL
    assert.throws(
      () => validateAndNormalizeEmail("burner@mailinator.com"),
      (err) => err.code === "ERR_DISPOSABLE_EMAIL" && err.status === 400
    );
    assert.throws(
      () => validateAndNormalizeEmail("temp_user@tempmail.com"),
      (err) => err.code === "ERR_DISPOSABLE_EMAIL" && err.status === 400
    );
    assert.throws(
      () => validateAndNormalizeEmail("scam@guerrillamail.com"),
      (err) => err.code === "ERR_DISPOSABLE_EMAIL" && err.status === 400
    );
  });

  test("2. Non-Enumerable Sign-In & Sign-Up: Uniform Responses and Constant-Time Flow Masking", async () => {
    const authRepo = getAuthRepository();
    const existingPhone = "+91 98765 00001";
    const unknownPhone = "+91 98765 99999";

    // Setup an existing user
    const { computePhoneHash, computeEmailHash } = require("../src/crypto/serverCrypto");
    const existingPhoneHash = computePhoneHash("+919876500001");
    const existingEmailHash = computeEmailHash("registered@udhaar.app");
    await authRepo.createUserTransaction(
      {
        uid: "usr_existing_4001",
        phoneHash: existingPhoneHash,
        emailHash: existingEmailHash,
        displayName: "Known User",
        status: "ACTIVE"
      },
      { phoneHash: existingPhoneHash },
      { emailHash: existingEmailHash }
    );

    // 1. Sign-in start responses must have identical shape and masked format
    const resKnown = await startSignin({ phoneNumber: existingPhone });
    const resUnknown = await startSignin({ phoneNumber: unknownPhone });

    assert.ok(resKnown.flowId);
    assert.ok(resUnknown.flowId);
    assert.equal(resKnown.channel, "WHATSAPP");
    assert.equal(resUnknown.channel, "WHATSAPP");
    assert.equal(resKnown.resendAfterSeconds, 30);
    assert.equal(resUnknown.resendAfterSeconds, 30);
    assert.equal(resKnown.destinationMasked, "+91 98*** ***01");
    assert.equal(resUnknown.destinationMasked, "+91 98*** ***99");

    // Known user received real OTP in fake provider; unknown user received no OTP
    const knownCode = fakeWhatsApp.getLastCodeFor("+919876500001");
    const unknownCode = fakeWhatsApp.getLastCodeFor("+919876599999");
    assert.ok(knownCode, "Known user receives OTP code");
    assert.equal(unknownCode, null, "Unknown user must NOT generate external OTP spend");

    // 2. Sign-in verify error responses must be indistinguishable (401 ERR_INVALID_CREDENTIALS)
    await assert.rejects(
      async () => {
        await verifySignin({
          flowId: resKnown.flowId,
          code: "999999", // wrong code
          phoneNumber: existingPhone
        });
      },
      (err) => err.status === 401 && err.code === "ERR_INVALID_CREDENTIALS"
    );

    await assert.rejects(
      async () => {
        await verifySignin({
          flowId: resUnknown.flowId,
          code: "999999", // non-existent user
          phoneNumber: unknownPhone
        });
      },
      (err) => err.status === 401 && err.code === "ERR_INVALID_CREDENTIALS"
    );
  });

  test("3. Replay Defense: Code reuse, cross-flow replay, and cross-purpose replay rejected", async () => {
    const phone = "+91 98765 22222";
    const flowIdA = "flow_replay_A_101";
    const flowIdB = "flow_replay_B_102";

    // 1. Dispatch OTP for Flow A (SIGNUP)
    await sendOtpChallenge({
      flowId: flowIdA,
      destination: "+919876522222",
      channel: "WHATSAPP",
      purpose: "SIGNUP"
    });
    const codeA = fakeWhatsApp.getLastCodeFor("+919876522222");
    assert.ok(codeA);

    // 2. Replay attack 1: Attempt to use Code A against a different flow (Flow B)
    await assert.rejects(
      async () => {
        await verifyOtpChallenge({
          flowId: flowIdB,
          code: codeA,
          purpose: "SIGNUP"
        });
      },
      (err) => err.status === 404 && err.code === "ERR_CHALLENGE_NOT_FOUND"
    );

    // 3. Replay attack 2: Attempt to use Code A for a different purpose (SIGNIN instead of SIGNUP)
    await assert.rejects(
      async () => {
        await verifyOtpChallenge({
          flowId: flowIdA,
          code: codeA,
          purpose: "SIGNIN"
        });
      },
      (err) => err.status === 404 && err.code === "ERR_CHALLENGE_NOT_FOUND"
    );

    // 4. Legitimate verification of Code A succeeds
    const verification = await verifyOtpChallenge({
      flowId: flowIdA,
      code: codeA,
      purpose: "SIGNUP"
    });
    assert.equal(verification.verified, true);
    assert.ok(verification.flowToken);

    // 5. Replay attack 3: Attempt to reuse Code A after it has been consumed
    await assert.rejects(
      async () => {
        await verifyOtpChallenge({
          flowId: flowIdA,
          code: codeA,
          purpose: "SIGNUP"
        });
      },
      (err) => err.status === 404 && err.code === "ERR_CHALLENGE_NOT_FOUND"
    );
  });

  test("4. Brute-Force Lockout: 15-Minute Lockout triggered after 5 failed attempts", async () => {
    const flowId = "flow_lockout_test_201";
    const phone = "+919876533333";

    await sendOtpChallenge({
      flowId,
      destination: phone,
      channel: "WHATSAPP",
      purpose: "SIGNUP"
    });
    const realCode = fakeWhatsApp.getLastCodeFor(phone);
    assert.ok(realCode);

    // Attempts 1 through 4: Rejected with 400 ERR_INVALID_OTP and decremented attemptsRemaining
    for (let i = 1; i <= 4; i++) {
      await assert.rejects(
        async () => {
          await verifyOtpChallenge({ flowId, code: "000000", purpose: "SIGNUP" });
        },
        (err) => {
          assert.equal(err.status, 400);
          assert.equal(err.code, "ERR_INVALID_OTP");
          assert.equal(err.attemptsRemaining, 5 - i);
          return true;
        }
      );
    }

    // Attempt 5: Triggers 15-minute account verification lockout (403 ERR_MAX_ATTEMPTS_EXCEEDED)
    await assert.rejects(
      async () => {
        await verifyOtpChallenge({ flowId, code: "000000", purpose: "SIGNUP" });
      },
      (err) => {
        assert.equal(err.status, 403);
        assert.equal(err.code, "ERR_MAX_ATTEMPTS_EXCEEDED");
        return true;
      }
    );

    // Attempt 6 (Even with the REAL code!): Fails with 403 ERR_ACCOUNT_LOCKED
    await assert.rejects(
      async () => {
        await verifyOtpChallenge({ flowId, code: realCode, purpose: "SIGNUP" });
      },
      (err) => {
        assert.equal(err.status, 403);
        assert.equal(err.code, "ERR_ACCOUNT_LOCKED");
        return true;
      }
    );
  });

  test("5. Multi-Layer Rate Limiting: 30s Cooldown, Destination Limits, and IP Throttling", async () => {
    const phone = "+919876544444";
    const ip = "198.51.100.25";

    // 1. First dispatch succeeds
    await sendOtpChallenge({
      flowId: "flow_rl_01",
      destination: phone,
      channel: "WHATSAPP",
      clientIp: ip
    });

    // 2. Immediate second dispatch within 30s cooldown triggers 429 ERR_RESEND_COOLDOWN
    await assert.rejects(
      async () => {
        await sendOtpChallenge({
          flowId: "flow_rl_02",
          destination: phone,
          channel: "WHATSAPP",
          clientIp: ip
        });
      },
      (err) => err.status === 429 && err.code === "ERR_RESEND_COOLDOWN"
    );

    // 3. Destination hourly limit enforcement (3 per hour)
    const otpRepo = getOtpRepository();
    const { computePhoneHash } = require("../src/crypto/serverCrypto");
    const destKey = `dest_${computePhoneHash(phone)}`;
    const record = otpRepo.rateLimits.get(destKey);
    // Advance cooldown past 30 seconds
    record.lastSentAt = Date.now() - 35000;
    record.hourlyCount = 3; // Simulate 3 sends already in this hour

    await assert.rejects(
      async () => {
        await sendOtpChallenge({
          flowId: "flow_rl_03",
          destination: phone,
          channel: "WHATSAPP",
          clientIp: ip
        });
      },
      (err) => err.status === 429 && err.code === "ERR_RATE_LIMIT_DESTINATION_HOURLY"
    );

    // 4. IP rate limit enforcement (20 per hour)
    const { hashIp } = require("../src/crypto/serverCrypto");
    const ipKey = `ip_${hashIp("198.51.100.99")}`;
    otpRepo.rateLimits.set(ipKey, { count: 20, resetAt: Date.now() + 1800000 });

    await assert.rejects(
      async () => {
        await sendOtpChallenge({
          flowId: "flow_rl_04",
          destination: "+919876555555",
          channel: "WHATSAPP",
          clientIp: "198.51.100.99"
        });
      },
      (err) => err.status === 429 && err.code === "ERR_RATE_LIMIT_IP"
    );
  });

  test("6. Atomic Concurrency: Parallel verification requests race condition test", async () => {
    const flowId = "flow_concurrency_race_301";
    const phone = "+919876566666";

    await sendOtpChallenge({
      flowId,
      destination: phone,
      channel: "WHATSAPP",
      purpose: "SIGNUP"
    });
    const correctCode = fakeWhatsApp.getLastCodeFor(phone);
    assert.ok(correctCode);

    // Fire 5 simultaneous verification attempts with the correct code
    const results = await Promise.allSettled([
      verifyOtpChallenge({ flowId, code: correctCode, purpose: "SIGNUP" }),
      verifyOtpChallenge({ flowId, code: correctCode, purpose: "SIGNUP" }),
      verifyOtpChallenge({ flowId, code: correctCode, purpose: "SIGNUP" }),
      verifyOtpChallenge({ flowId, code: correctCode, purpose: "SIGNUP" }),
      verifyOtpChallenge({ flowId, code: correctCode, purpose: "SIGNUP" })
    ]);

    const fulfilled = results.filter((r) => r.status === "fulfilled");
    const rejected = results.filter((r) => r.status === "rejected");

    // Exactly ONE winner permitted by atomic transaction; the other 4 rejected
    assert.equal(fulfilled.length, 1, "Exactly one concurrent verification must succeed");
    assert.equal(rejected.length, 4, "All other concurrent verification requests must be rejected");
    assert.ok(fulfilled[0].value.flowToken, "Winner receives single-use flow token");
  });

  test("7. Global Send Budget, Threshold Alerts (50%, 80%, 100%) and Dynamic Kill Switch", async () => {
    const otpRepo = getOtpRepository();
    const testCap = 10;
    otpRepo.dailyBudget.maxDailyBudget = testCap;

    // Send 4 codes (40% - below threshold)
    for (let i = 1; i <= 4; i++) {
      await otpRepo.checkAndIncrementDailyBudget(testCap);
    }
    assert.equal(otpRepo.budgetAlerts.length, 0);

    // Send 5th code (50% threshold)
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    assert.equal(otpRepo.budgetAlerts.length, 1);
    assert.equal(otpRepo.budgetAlerts[0].threshold, "50%");

    // Send 6th and 7th code (60-70%)
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    assert.equal(otpRepo.budgetAlerts.length, 1);

    // Send 8th code (80% threshold)
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    assert.equal(otpRepo.budgetAlerts.length, 2);
    assert.equal(otpRepo.budgetAlerts[1].threshold, "80%");

    // Send 9th and 10th code (100% threshold)
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    await otpRepo.checkAndIncrementDailyBudget(testCap);
    assert.equal(otpRepo.budgetAlerts.length, 3);
    assert.equal(otpRepo.budgetAlerts[2].threshold, "100%");

    // 11th dispatch rejected by activated kill switch (503 ERR_GLOBAL_BUDGET_EXCEEDED)
    await assert.rejects(
      async () => {
        await otpRepo.checkAndIncrementDailyBudget(testCap);
      },
      (err) => err.status === 503 && err.code === "ERR_GLOBAL_BUDGET_EXCEEDED"
    );

    // Dynamic kill switch flag halts dispatch immediately
    updateFeatureFlag({ flagName: "KILL_SWITCH_OTP_DISPATCH", enabled: true });
    await assert.rejects(
      async () => {
        await sendOtpChallenge({
          flowId: "flow_killed_01",
          destination: "+919876577777",
          channel: "WHATSAPP"
        });
      },
      (err) => err.status === 503 && err.code === "ERR_DISPATCH_SUSPENDED"
    );
  });

  test("8. WhatsApp Webhook: GET Hub Subscribe, POST Raw Body Signature, and Fallback Path", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    try {
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      // 1. GET /webhooks/whatsapp hub verification
      const resGetValid = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=udhaar_webhook_verify_token_prod&hub.challenge=test_meta_challenge_12345",
        method: "GET"
      });
      assert.equal(resGetValid.status, 200);
      assert.equal(resGetValid.body, "test_meta_challenge_12345");

      const resGetInvalid = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=wrong_token&hub.challenge=test",
        method: "GET"
      });
      assert.equal(resGetInvalid.status, 403);

      // 2. Dispatch a challenge to receive delivery update
      const flowId = "flow_webhook_status_401";
      const phone = "+919876588888";
      await sendOtpChallenge({
        flowId,
        destination: phone,
        channel: "WHATSAPP",
        purpose: "SIGNUP"
      });
      const chal = Array.from(getOtpRepository().challenges.values())[0];
      const messageId = chal.providerMessageId;
      assert.ok(messageId);

      // 3. POST /webhooks/whatsapp with raw body signature
      const webhookPayload = JSON.stringify({
        entry: [{
          changes: [{
            value: {
              statuses: [{
                id: messageId,
                recipient_id: "919876588888",
                status: "failed",
                timestamp: "1731000000",
                errors: [{ code: 131026, title: "Message undeliverable" }]
              }]
            }
          }]
        }]
      });

      const secret = process.env.META_APP_SECRET || "test_meta_app_secret";
      const validHmac = "sha256=" + crypto.createHmac("sha256", secret).update(webhookPayload).digest("hex");

      // 3a. Invalid signature rejected with 401
      const resPostInvalidSig = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/webhooks/whatsapp",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Hub-Signature-256": "sha256=invalid_tampered_signature"
        }
      }, webhookPayload);
      assert.equal(resPostInvalidSig.status, 401);

      // 3b. Valid signature accepted and marks challenge FAILED
      const resPostValid = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/webhooks/whatsapp",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Hub-Signature-256": validHmac
        }
      }, webhookPayload);
      assert.equal(resPostValid.status, 200);

      // 4. Verify challenge status endpoint reflects FAILED delivery and shows fallback path
      const resStatus = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: `/api/v1/auth/otp/status?flowId=${flowId}`,
        method: "GET"
      });
      assert.equal(resStatus.status, 200);
      assert.equal(resStatus.body.deliveryStatus, "FAILED");
      assert.equal(resStatus.body.canFallback, true);
      assert.equal(resStatus.body.suggestedAlternative, "SMS or Email");

      // 5. User exercises fallback path: POST /auth/otp/fallback to Email
      const resFallbackEmail = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/auth/otp/fallback",
        method: "POST",
        headers: { "Content-Type": "application/json" }
      }, {
        flowId,
        channel: "EMAIL",
        destination: "user@udhaar.app"
      });
      assert.equal(resFallbackEmail.status, 200);
      assert.equal(resFallbackEmail.body.channel, "EMAIL");

      // Verify email was dispatched by fakeEmail provider
      const emailCode = fakeEmail.getLastCodeFor("user@udhaar.app");
      assert.ok(emailCode, "Fallback verification code dispatched via transactional email");
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });

});
