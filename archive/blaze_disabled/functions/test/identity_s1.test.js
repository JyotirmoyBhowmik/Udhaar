process.env.NODE_ENV = "test";
process.env.USE_FAKE_PROVIDERS = "true";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  validateAndNormalizePhone,
  validateAndNormalizeEmail,
  startSignup,
  verifyFlowOtp,
  submitSignupEmail,
  completeSignup,
  startSignin,
  verifySignin,
  resetAuthStore
} = require("../src/auth/authService");

const {
  resetDailyBudget,
  sendOtpChallenge,
  verifyOtpChallenge
} = require("../src/otp/otpService");

const {
  getAuthRepository,
  getOtpRepository,
  resetTestRepositories
} = require("../src/repositories");

const {
  computePhoneHash,
  hashIp
} = require("../src/crypto/serverCrypto");

const {
  fakeWhatsApp,
  fakeEmail,
  fakeSms
} = require("../src/providers");

const {
  createSession,
  validateSession,
  revokeDeviceSession,
  listUserDevices
} = require("../src/sessions/sessionManager");

test.beforeEach(async () => {
  resetTestRepositories();
  await resetAuthStore();
  await resetDailyBudget();
  fakeWhatsApp.reset();
  fakeEmail.reset();
  fakeSms.reset();
});

// =========================================================================
// 1. Phone & Email Validation Tests
// =========================================================================
test("Identity S1: Phone Number Validation & Country Restriction (+91, +977)", () => {
  // Valid Indian mobile
  const p1 = validateAndNormalizePhone("+91 98765 43210");
  assert.equal(p1.e164, "+919876543210");
  assert.equal(p1.country, "IN");

  // Valid Nepali mobile
  const p2 = validateAndNormalizePhone("+977 9841234567");
  assert.equal(p2.e164, "+9779841234567");
  assert.equal(p2.country, "NP");

  // Unsupported country (US +1)
  assert.throws(
    () => validateAndNormalizePhone("+1 650 253 0000"),
    (err) => err.code === "ERR_UNSUPPORTED_COUNTRY"
  );

  // Malformed numbers
  assert.throws(() => validateAndNormalizePhone("123"), (err) => err.code === "ERR_INVALID_PHONE");
  assert.throws(() => validateAndNormalizePhone(""), (err) => err.code === "ERR_INVALID_PHONE");
});

test("Identity S1: Email Normalization & Disposable Email Rejection", () => {
  // Normalization: trim, lowercase, preserve dots/tags
  const e1 = validateAndNormalizeEmail("  User.Test+tag@Example.COM  ");
  assert.equal(e1, "user.test+tag@example.com");

  // Disposable domain rejection
  assert.throws(
    () => validateAndNormalizeEmail("burner@mailinator.com"),
    (err) => err.code === "ERR_DISPOSABLE_EMAIL"
  );
  assert.throws(
    () => validateAndNormalizeEmail("temp@tempmail.com"),
    (err) => err.code === "ERR_DISPOSABLE_EMAIL"
  );
  assert.throws(
    () => validateAndNormalizeEmail("fake@10minutemail.com"),
    (err) => err.code === "ERR_DISPOSABLE_EMAIL"
  );
});

// =========================================================================
// 2. Full Sign-Up Flow End-to-End
// =========================================================================
test("Identity S1: Full Sign-Up Flow (Phone OTP -> Email OTP -> Complete Registration)", async () => {
  const phone = "+91 98765 43210";
  const email = "ravi.sharma@example.com";

  // Step 1: Start sign-up
  const startRes = await startSignup({
    phoneNumber: phone,
    locale: "hi",
    clientIp: "103.21.244.2"
  });
  assert.ok(startRes.flowId);
  assert.equal(startRes.channel, "WHATSAPP");
  assert.equal(startRes.resendAfterSeconds, 30);

  // Retrieve code from fake WhatsApp provider
  const waCode = fakeWhatsApp.getLastCodeFor("+919876543210");
  assert.ok(waCode);
  assert.match(waCode, /^[0-9]{6}$/);

  // Step 2: Verify phone OTP
  const phoneVerifyRes = await verifyFlowOtp({
    flowId: startRes.flowId,
    code: waCode,
    purpose: "SIGNUP"
  });
  assert.equal(phoneVerifyRes.verified, true);
  assert.ok(phoneVerifyRes.flowToken);

  // Step 3: Submit email address
  const emailRes = await submitSignupEmail({
    flowId: startRes.flowId,
    flowToken: phoneVerifyRes.flowToken,
    email,
    locale: "hi",
    clientIp: "103.21.244.2"
  });
  assert.equal(emailRes.channel, "EMAIL");

  const emailCode = fakeEmail.getLastCodeFor(email);
  assert.ok(emailCode);
  assert.match(emailCode, /^[0-9]{6}$/);

  // Step 4: Verify email OTP
  const emailVerifyRes = await verifyFlowOtp({
    flowId: startRes.flowId,
    code: emailCode,
    purpose: "SIGNUP_EMAIL"
  });
  assert.equal(emailVerifyRes.verified, true);

  // Step 5: Complete registration
  const completeRes = await completeSignup({
    flowId: startRes.flowId,
    flowToken: emailVerifyRes.flowToken,
    phoneNumber: phone,
    email,
    displayName: "Ravi Sharma",
    acceptedTermsVersion: "v2.0",
    acceptedPrivacyVersion: "v2.0",
    isAge18Plus: true,
    allowWhatsAppReminders: true,
    clientType: "WEB"
  });

  assert.ok(completeRes.uid.startsWith("usr_"));
  assert.ok(completeRes.sessionCookieValue);
  assert.equal(completeRes.user.displayName, "Ravi Sharma");

  // Verify unique index creation in repository
  const authRepo = getAuthRepository();
  assert.equal(await authRepo.countUsers(), 1);
  assert.ok(await authRepo.getUserByPhoneHash(computePhoneHash("+919876543210")));
  assert.ok(await authRepo.getUserByEmailHash(completeRes.user.emailHash));

  // Duplicate registration collision check
  await assert.rejects(
    async () => {
      await completeSignup({
        flowId: "new_flow_id",
        flowToken: "some_token",
        phoneNumber: phone,
        email: "different@example.com",
        displayName: "Imposter",
        isAge18Plus: true
      });
    },
    (err) => err.code === "ERR_ACCOUNT_EXISTS" && err.status === 409
  );
});

// =========================================================================
// 3. Brute-Force Lockout & Flow Isolation
// =========================================================================
test("Identity S1: Brute-Force Lockout after 5 Failed Attempts", async () => {
  const phone = "+91 91234 56789";
  const startRes = await startSignup({ phoneNumber: phone });

  // Attempt 4 wrong codes
  for (let i = 1; i <= 4; i++) {
    await assert.rejects(
      async () => {
        await verifyFlowOtp({ flowId: startRes.flowId, code: "000000", purpose: "SIGNUP" });
      },
      (err) => err.code === "ERR_INVALID_OTP" && err.attemptsRemaining === (5 - i)
    );
  }

  // 5th wrong attempt triggers 15-minute account lock
  await assert.rejects(
    async () => {
      await verifyFlowOtp({ flowId: startRes.flowId, code: "000000", purpose: "SIGNUP" });
    },
    (err) => err.code === "ERR_MAX_ATTEMPTS_EXCEEDED" && err.status === 403
  );

  // Even the correct code is now locked out
  const correctCode = fakeWhatsApp.getLastCodeFor("+919123456789");
  await assert.rejects(
    async () => {
      await verifyFlowOtp({ flowId: startRes.flowId, code: correctCode, purpose: "SIGNUP" });
    },
    (err) => err.code === "ERR_ACCOUNT_LOCKED" && err.status === 403
  );
});

test("Identity S1: Flow Isolation (Code from Flow A cannot be used in Flow B)", async () => {
  const resA = await startSignup({ phoneNumber: "+91 99887 76655" });
  const codeA = fakeWhatsApp.getLastCodeFor("+919988776655");

  // Different flowId
  await assert.rejects(
    async () => {
      await verifyFlowOtp({ flowId: "completely_different_flow_id", code: codeA, purpose: "SIGNUP" });
    },
    (err) => err.code === "ERR_CHALLENGE_NOT_FOUND"
  );
});

// =========================================================================
// 4. Rate Limiting & Send Quotas
// =========================================================================
test("Identity S1: Rate Limiting: 30s Cooldown, Destination Limits, and IP Caps", async () => {
  const phone = "+91 98111 22233";

  // 1. Request 1: OK
  await startSignup({ phoneNumber: phone, clientIp: "1.2.3.4" });

  // 2. Request 2 immediately: Cooldown rejection (30 seconds)
  await assert.rejects(
    async () => {
      await startSignup({ phoneNumber: phone, clientIp: "1.2.3.4" });
    },
    (err) => err.code === "ERR_RESEND_COOLDOWN" && err.status === 429
  );

  // 3. Hourly Destination Limit (max 3/hr)
  // Clear lastSentAt to bypass cooldown for subsequent sends
  const otpRepo = getOtpRepository();
  const destHash = computePhoneHash("+919811122233");
  const destRec = await otpRepo.getRateLimit(`dest_${destHash}`);
  assert.ok(destRec);
  assert.equal(destRec.hourlyCount, 1);

  // Send #2 (cooldown bypassed)
  await otpRepo.setRateLimit(`dest_${destHash}`, { ...destRec, lastSentAt: 0 });
  await sendOtpChallenge({ flowId: "f_rate2", destination: "+919811122233", clientIp: "1.2.3.4" });
  const destRec2 = await otpRepo.getRateLimit(`dest_${destHash}`);
  assert.equal(destRec2.hourlyCount, 2);

  // Send #3 (cooldown bypassed)
  await otpRepo.setRateLimit(`dest_${destHash}`, { ...destRec2, lastSentAt: 0 });
  await sendOtpChallenge({ flowId: "f_rate3", destination: "+919811122233", clientIp: "1.2.3.4" });
  const destRec3 = await otpRepo.getRateLimit(`dest_${destHash}`);
  assert.equal(destRec3.hourlyCount, 3);

  // Send #4: Hourly cap reached!
  await otpRepo.setRateLimit(`dest_${destHash}`, { ...destRec3, lastSentAt: 0 });
  await assert.rejects(
    async () => {
      await sendOtpChallenge({ flowId: "f_rate4", destination: "+919811122233", clientIp: "1.2.3.4" });
    },
    (err) => err.code === "ERR_RATE_LIMIT_DESTINATION_HOURLY" && err.status === 429
  );

  // 4. Daily Destination Limit (max 10/day)
  await otpRepo.setRateLimit(`dest_${destHash}`, { ...destRec3, lastSentAt: 0, hourlyCount: 0, dailyCount: 10 });
  await assert.rejects(
    async () => {
      await sendOtpChallenge({ flowId: "f_rate_daily", destination: "+919811122233", clientIp: "1.2.3.4" });
    },
    (err) => err.code === "ERR_RATE_LIMIT_DESTINATION_DAILY" && err.status === 429
  );

  // 5. IP Rate Limit (max 20/hr per IP)
  const clientIp = "192.168.1.100";
  const ipHashed = hashIp(clientIp);
  await otpRepo.setRateLimit(`ip_${ipHashed}`, { count: 20, resetAt: Date.now() + 3600000 });

  await assert.rejects(
    async () => {
      await sendOtpChallenge({ flowId: "f_ip_test", destination: "+919777788888", clientIp });
    },
    (err) => err.code === "ERR_RATE_LIMIT_IP" && err.status === 429
  );
});

test("Identity S1: Global Daily Send Budget Kill Switch", async () => {
  const otpRepo = getOtpRepository();
  await otpRepo.setDailyBudgetMax(2);

  await sendOtpChallenge({ flowId: "f1", destination: "+919800000001", clientIp: "10.0.0.1" });
  await sendOtpChallenge({ flowId: "f2", destination: "+919800000002", clientIp: "10.0.0.2" });

  // 3rd send trips kill switch
  await assert.rejects(
    async () => {
      await sendOtpChallenge({ flowId: "f3", destination: "+919800000003", clientIp: "10.0.0.3" });
    },
    (err) => err.code === "ERR_GLOBAL_BUDGET_EXCEEDED" && err.status === 503
  );

  // Reset budget for remaining tests
  await resetDailyBudget();
});

// =========================================================================
// 5. Non-Enumerable Sign-In & Session Verification
// =========================================================================
test("Identity S1: Non-Enumerable Sign-In (Known and Unknown return identical structures)", async () => {
  // First register known user
  const phoneKnown = "+91 98765 00001";
  const startUp = await startSignup({ phoneNumber: phoneKnown });
  const code1 = fakeWhatsApp.getLastCodeFor("+919876500001");
  const pTok = await verifyFlowOtp({ flowId: startUp.flowId, code: code1 });
  await submitSignupEmail({ flowId: startUp.flowId, flowToken: pTok.flowToken, email: "known@example.com" });
  const code2 = fakeEmail.getLastCodeFor("known@example.com");
  const eTok = await verifyFlowOtp({ flowId: startUp.flowId, code: code2, purpose: "SIGNUP_EMAIL" });
  await completeSignup({
    flowId: startUp.flowId,
    flowToken: eTok.flowToken,
    phoneNumber: phoneKnown,
    email: "known@example.com",
    displayName: "Known User",
    isAge18Plus: true
  });

  // Sign-in with known user (reset rate limit cooldown so test does not have to sleep 30s)
  resetDailyBudget();
  const knownRes = await startSignin({ phoneNumber: phoneKnown });
  assert.equal(knownRes.channel, "WHATSAPP");
  assert.equal(knownRes.resendAfterSeconds, 30);
  assert.ok(knownRes.flowId);

  // Sign-in with unknown user
  const phoneUnknown = "+91 98765 99999";
  const unknownRes = await startSignin({ phoneNumber: phoneUnknown });
  assert.equal(unknownRes.channel, "WHATSAPP");
  assert.equal(unknownRes.resendAfterSeconds, 30);
  assert.ok(unknownRes.flowId);

  // Both return identical keys
  assert.deepEqual(Object.keys(knownRes).sort(), Object.keys(unknownRes).sort());
});

test("Identity S1: Session Lifecycle and Instant Revocation (checkRevoked)", async () => {
  const session = await createSession({
    uid: "usr_alice_123",
    clientType: "WEB",
    clientIp: "192.168.1.10"
  });

  // Validate active session
  const valid = await validateSession(session.sessionCookieValue);
  assert.ok(valid);
  assert.equal(valid.uid, "usr_alice_123");

  // List devices
  const devices = await listUserDevices("usr_alice_123");
  assert.equal(devices.length, 1);
  assert.equal(devices[0].deviceId, session.deviceId);

  // Revoke session
  await revokeDeviceSession("usr_alice_123", session.deviceId);

  // Subsequent validation fails (checkRevoked)
  const revokedCheck = await validateSession(session.sessionCookieValue);
  assert.equal(revokedCheck, null, "Revoked session must evaluate to null");
});
