/**
 * Stage SP0 & SP1 — Spark Mode Tier Switch & Free Firebase Auth Test Suite
 * (functions/test/spark_sp0_sp1.test.js)
 * 
 * Verifies:
 * 1. SP0: Tier switch configuration (spark vs blaze), Remote Config template, and runtime guards.
 * 2. SP0: Truthful documentation and ADR completeness.
 * 3. SP1: Password strength validator and common password blocklist.
 * 4. SP1: Disposable email domain blocklist.
 * 5. SP1: Contact phone normalization (+91, +977) with phoneStatus: "unverified".
 * 6. SP1: FirebaseAuthClient error translation, sign-up email verification requirement, and device revocation notice.
 * 7. SP1: AuthClient tier guards blocking /api/v1/** in Spark Mode.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const siteConfig = require("../../web/site-config");
const { enforceTierGuard } = require("../src/config/environmentGuard");
const { validatePassword, COMMON_PASSWORDS } = require("../../webapp/src/features/auth/passwordValidator");
const { checkDisposableEmail, DISPOSABLE_EMAIL_DOMAINS } = require("../../webapp/src/features/auth/disposableDomains");
const { validateAndNormalizeContactPhone } = require("../../webapp/src/features/auth/phoneValidation");
const { FirebaseAuthClient } = require("../../webapp/src/api/firebaseAuthClient");
const { AuthClient } = require("../../webapp/src/api/authClient");

test.describe("Stage SP0: Tier Switch and Governance Invariants", () => {
  test("1.1 site-config.js defaults to Spark Mode and exposes tier checkers", () => {
    assert.equal(siteConfig.UDHAAR_TIER, "spark");
    assert.equal(siteConfig.isSparkTier(), true);
    assert.equal(siteConfig.isBlazeTier(), false);

    // Paid Blaze features throw TierViolation in Spark Mode
    assert.throws(
      () => siteConfig.assertAllowedInTier("cloud_functions"),
      /TierViolation.*Blaze-only/
    );
    assert.throws(
      () => siteConfig.assertAllowedInTier("cloud_kms"),
      /TierViolation.*Blaze-only/
    );
    assert.throws(
      () => siteConfig.assertAllowedInTier("whatsapp_cloud_api"),
      /TierViolation.*Blaze-only/
    );
    assert.throws(
      () => siteConfig.assertAllowedInTier("cloud_storage"),
      /TierViolation.*Blaze-only/
    );
  });

  test("1.2 environmentGuard.js enforces fail-fast error when Cloud Functions invoked in Spark Mode", () => {
    const prevTier = process.env.UDHAAR_TIER;
    try {
      process.env.UDHAAR_TIER = "spark";
      assert.throws(
        () => enforceTierGuard(),
        /TierGuard.*Cloud Functions execution attempted while UDHAAR_TIER='spark'/
      );

      process.env.UDHAAR_TIER = "blaze";
      assert.doesNotThrow(() => enforceTierGuard());
    } finally {
      if (prevTier !== undefined) {
        process.env.UDHAAR_TIER = prevTier;
      } else {
        delete process.env.UDHAAR_TIER;
      }
    }
  });

  test("1.3 Remote Config template exists and configures Spark defaults", () => {
    const rcPath = path.resolve(__dirname, "../../docs/remote-config-template.json");
    assert.ok(fs.existsSync(rcPath), "Remote Config template must exist at docs/remote-config-template.json");

    const rc = JSON.parse(fs.readFileSync(rcPath, "utf8"));
    assert.equal(rc.parameters.UDHAAR_TIER.defaultValue.value, "spark");
    assert.equal(rc.parameters.signup_enabled.defaultValue.value, "true");
    assert.equal(rc.parameters.guest_links_enabled.defaultValue.value, "true");
    assert.equal(rc.parameters.max_client_daily_writes.defaultValue.value, "500");
  });

  test("1.4 ADR 0008 documents Spark Mode zero-cost architecture and limitations", () => {
    const adrPath = path.resolve(__dirname, "../../docs/adr/0008-spark-mode-zero-cost-tier.md");
    assert.ok(fs.existsSync(adrPath), "ADR 0008 must exist at docs/adr/0008-spark-mode-zero-cost-tier.md");

    const adr = fs.readFileSync(adrPath, "utf8");
    assert.match(adr, /SPARK_MODE|Spark Mode/i);
    assert.match(adr, /Unverified contact detail/i);
    assert.match(adr, /50,000 reads/i);
    assert.match(adr, /No billing-required/i);
  });

  test("1.5 Trust Center and Privacy Policy state truthful Spark guarantees", () => {
    const trustPath = path.resolve(__dirname, "../../web/trust/index.html");
    const privacyPath = path.resolve(__dirname, "../../web/privacy.html");

    const trustHtml = fs.readFileSync(trustPath, "utf8");
    assert.match(trustHtml, /Number not verified/);
    assert.match(trustHtml, /Zero cloud photo uploads/i);
    assert.match(trustHtml, /Lender approval required/);

    const privacyHtml = fs.readFileSync(privacyPath, "utf8");
    assert.match(privacyHtml, /Spark Mode/);
    assert.match(privacyHtml, /phoneStatus: 'unverified'|unverified contact detail/i);
  });
});

test.describe("Stage SP1: Free Firebase Authentication & Client Invariants", () => {
  test("2.1 Password validator rejects < 10 chars and common passwords", () => {
    // Length check (< 10)
    const short = validatePassword("Abc!1");
    assert.equal(short.valid, false);
    assert.ok(short.errors.some(e => e.includes("at least 10 characters")));

    // Common passwords
    const common1 = validatePassword("password123");
    assert.equal(common1.valid, false);
    assert.ok(common1.errors.some(e => e.includes("too common")));

    const common2 = validatePassword("qwerty12345");
    assert.equal(common2.valid, false);
    assert.ok(common2.errors.some(e => e.includes("too common")));

    // Valid strong password
    const strong = validatePassword("SecureP@ssw0rd!2026");
    assert.equal(strong.valid, true);
    assert.equal(strong.score, 4);
    assert.equal(strong.label, "Strong");
    assert.equal(strong.errors.length, 0);
  });

  test("2.2 Disposable email filter detects throwaway domains", () => {
    assert.equal(checkDisposableEmail("user@mailinator.com").isDisposable, true);
    assert.equal(checkDisposableEmail("test@tempmail.com").isDisposable, true);
    assert.equal(checkDisposableEmail("bot@10minutemail.com").isDisposable, true);
    assert.equal(checkDisposableEmail("bot@trashmail.com").isDisposable, true);

    // Legitimate domains allowed
    assert.equal(checkDisposableEmail("jyotirmoy@gmail.com").isDisposable, false);
    assert.equal(checkDisposableEmail("contact@jyotirmoyb.com").isDisposable, false);
    assert.equal(checkDisposableEmail("user@outlook.com").isDisposable, false);
  });

  test("2.3 Contact phone normalizer validates +91 and +977 and marks phoneStatus as unverified", () => {
    // Valid Indian phone without prefix
    const inRes = validateAndNormalizeContactPhone("9876543210");
    assert.equal(inRes.valid, true);
    assert.equal(inRes.e164, "+919876543210");
    assert.equal(inRes.country, "IN");
    assert.equal(inRes.phoneStatus, "unverified");
    assert.equal(inRes.label, "Number not verified");

    // Valid Nepali phone with country prefix or default country
    const npRes = validateAndNormalizeContactPhone("+9779841234567");
    assert.equal(npRes.valid, true);
    assert.equal(npRes.e164, "+9779841234567");
    assert.equal(npRes.country, "NP");
    assert.equal(npRes.phoneStatus, "unverified");

    const npRes2 = validateAndNormalizeContactPhone("9841234567", "NP");
    assert.equal(npRes2.valid, true);
    assert.equal(npRes2.e164, "+9779841234567");
    assert.equal(npRes2.country, "NP");

    // Invalid formats
    assert.equal(validateAndNormalizeContactPhone("12345").valid, false);
    assert.equal(validateAndNormalizeContactPhone("+14155552671").valid, false);
  });

  test("2.4 FirebaseAuthClient handles sign-up, verification email trigger, and friendly error translation", async () => {
    let emailVerificationCalled = false;
    const mockAuth = {
      createUserWithEmailAndPassword: async (email, password) => ({
        user: {
          uid: "user_spark_123",
          email,
          emailVerified: false,
          sendEmailVerification: async () => {
            emailVerificationCalled = true;
          },
          updateProfile: async () => {}
        }
      }),
      signInWithEmailAndPassword: async (email, password) => ({
        user: {
          uid: "user_spark_123",
          email,
          emailVerified: true
        }
      }),
      sendPasswordResetEmail: async (email) => {},
      signOut: async () => {}
    };

    const client = new FirebaseAuthClient({ auth: mockAuth });

    // Test sign-up
    const res = await client.signUpWithEmailPassword({
      email: "test@example.com",
      password: "StrongPassword123!",
      displayName: "Rahul Sharma"
    });

    assert.equal(res.user.uid, "user_spark_123");
    assert.equal(res.emailVerified, false);
    assert.equal(emailVerificationCalled, true);

    // Test error translation
    const errTooMany = client.formatAuthError({ code: "auth/too-many-requests" });
    assert.match(errTooMany, /Too many failed attempts/);

    const errDiffCred = client.formatAuthError({ code: "auth/account-exists-with-different-credential" });
    assert.match(errDiffCred, /already exists with this email address using another sign-in method/);

    // Test Device Session Info with honest token revocation explanation
    const sessionInfo = client.getDeviceSessionInfo();
    assert.ok(sessionInfo);
    assert.match(sessionInfo.revocationNotice, /Changing your password immediately invalidates refresh tokens/);
    assert.match(sessionInfo.revocationNotice, /valid for up to approximately 1 hour/);
  });

  test("2.5 AuthClient blocks /api/v1/** calls in Spark Mode with TierViolation", async () => {
    const client = new AuthClient("http://localhost:8080");

    // Force Spark mode
    client.isSparkMode = () => true;

    await assert.rejects(
      async () => client.startSignup({ phoneNumber: "+919876543210" }),
      /ERR_SPARK_TIER_RESTRICTION/
    );

    await assert.rejects(
      async () => client.startSignin({ phoneNumber: "+919876543210" }),
      /ERR_SPARK_TIER_RESTRICTION/
    );

    await assert.rejects(
      async () => client.verifyFlowOtp({ flowId: "f1", code: "123456" }),
      /ERR_SPARK_TIER_RESTRICTION/
    );
  });
});
