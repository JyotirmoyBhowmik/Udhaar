/**
 * Stage R3 Test Suite: Real Sessions, Cryptographic Tokens, Bot Protection & CSRF
 * 
 * Verifies:
 * 1. Firebase Admin Custom Token minting (Android) and Session Cookie (__session) for Web
 * 2. Revocation enforcement (checkRevoked & revokeRefreshTokens)
 * 3. App Check verification (Play Integrity / reCAPTCHA Enterprise) and staging debug switch
 * 4. Server-side reCAPTCHA Enterprise assessment on Web OTP code dispatch
 * 5. Double-submit CSRF token tied to the session and Origin check
 * 6. Step-Up Authentication (10-minute fresh credential window) on exports and deletion
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");

const { createApiApp } = require("../src/app");
const { createSession, validateSession, revokeDeviceSession } = require("../src/sessions/sessionManager");
const { getAuthAdapter, setAuthAdapter, resetAuthAdapter, TestAuthAdapter } = require("../src/sessions/authAdapter");
const { setAppCheckProvider, resetAppCheckProvider } = require("../src/security/appCheckMiddleware");
const { setRecaptchaProvider, resetRecaptchaProvider } = require("../src/security/recaptchaEnterprise");
const { resetTestRepositories, getAuthRepository } = require("../src/repositories");
const { generateStepUpToken } = require("../src/middleware/stepUpMiddleware");

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
  resetAuthAdapter();
  resetAppCheckProvider();
  resetRecaptchaProvider();
  delete process.env.NODE_ENV;
  delete process.env.UDHAAR_ENV;
  delete process.env.ENFORCE_APP_CHECK;
  delete process.env.ALLOW_APP_CHECK_DEBUG;
});

test.describe("Stage R3 — Real Sessions, Tokens, App Check, and CSRF Protection", () => {

  test("1. Real Firebase Admin Tokens: Custom Token JWT for Android & Session Cookie for Web", async () => {
    const uid = "usr_stage_r3_tokens_001";
    const session = await createSession({
      uid,
      clientType: "ANDROID",
      clientIp: "203.0.113.4"
    });

    // 1. Android Custom Token must be a real cryptographic JWT (3 parts separated by dots), NOT synthetic string
    assert.ok(session.customToken, "Custom token must be minted");
    assert.equal(session.customToken.startsWith("custom_token_usr_"), false, "Must NOT be legacy synthetic string");
    const jwtParts = session.customToken.split(".");
    assert.equal(jwtParts.length, 3, "Custom token must be a valid 3-part cryptographic JWT");

    const header = JSON.parse(Buffer.from(jwtParts[0], "base64url").toString("utf8"));
    const payload = JSON.parse(Buffer.from(jwtParts[1], "base64url").toString("utf8"));
    assert.equal(header.alg, "HS256");
    assert.equal(payload.uid, uid);
    assert.equal(payload.claims.clientType, "ANDROID");

    // 2. Web Session Cookie must be a signed cookie token
    const webSession = await createSession({
      uid,
      clientType: "WEB",
      clientIp: "203.0.113.4"
    });
    assert.ok(webSession.sessionCookieValue, "Session cookie must be minted");
    assert.ok(webSession.csrfToken, "Session must have a cryptographically bound CSRF token");

    // 3. Validation enforces signature and checkRevoked
    const validated = await validateSession(webSession.sessionCookieValue);
    assert.ok(validated);
    assert.equal(validated.uid, uid);
    assert.equal(validated.csrfToken, webSession.csrfToken);

    // 4. Revocation: revoking refresh tokens invalidates session immediately
    await revokeDeviceSession(uid, webSession.deviceId);
    const postRevoke = await validateSession(webSession.sessionCookieValue);
    assert.equal(postRevoke, null, "Revoked session must be rejected upon checkRevoked");
  });

  test("2. App Check Verification: Missing, invalid, and staging debug tokens", async () => {
    process.env.ENFORCE_APP_CHECK = "true";
    const app = createApiApp();
    const server = http.createServer(app);
    try {
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      // 1. Missing App Check token -> 401 ERR_APP_CHECK_MISSING on protected API, but healthz skips App Check
      const resMissingHealth = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/healthz",
        method: "GET"
      });
      assert.equal(resMissingHealth.status, 200);
      assert.ok(["ok", "healthy"].includes(resMissingHealth.body.status));

      const resMissingApi = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "GET"
      });
      assert.equal(resMissingApi.status, 401);
      assert.equal(resMissingApi.body.code, "ERR_APP_CHECK_MISSING");

      // 2. Invalid App Check token -> 401 ERR_APP_CHECK_INVALID
      const resInvalid = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "GET",
        headers: {
          "X-Firebase-AppCheck": "invalid_forged_app_check_token"
        }
      });
      assert.equal(resInvalid.status, 401);
      assert.equal(resInvalid.body.code, "ERR_APP_CHECK_INVALID");

      // 3. Debug token when debug disabled -> 403 ERR_APP_CHECK_DEBUG_FORBIDDEN
      delete process.env.ALLOW_APP_CHECK_DEBUG;
      delete process.env.UDHAAR_ENV;
      const resDebugProd = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "GET",
        headers: {
          "X-Firebase-AppCheck": "valid_staging_debug_app_check_token"
        }
      });
      assert.equal(resDebugProd.status, 403);
      assert.equal(resDebugProd.body.code, "ERR_APP_CHECK_DEBUG_FORBIDDEN");

      // 4. Debug token when debug allowed -> Allowed (moves forward to auth check 401)
      process.env.ALLOW_APP_CHECK_DEBUG = "true";
      const resDebugStaging = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "GET",
        headers: {
          "X-Firebase-AppCheck": "valid_staging_debug_app_check_token"
        }
      });
      // Passed App Check! Now fails with ERR_UNAUTHENTICATED because no auth token provided
      assert.equal(resDebugStaging.status, 401);
      assert.equal(resDebugStaging.body.code, "ERR_UNAUTHENTICATED");

      // 5. Valid App Check token -> Passes App Check
      const resValid = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "GET",
        headers: {
          "X-Firebase-AppCheck": "valid_web_app_check_token"
        }
      });
      assert.equal(resValid.status, 401);
      assert.equal(resValid.body.code, "ERR_UNAUTHENTICATED");
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });

  test("3. Server-Side reCAPTCHA Enterprise Assessment on Web Code Dispatch", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    try {
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      // 1. Web request with low bot score (score = 0.1) -> 403 ERR_BOT_CHECK_FAILED
      const resBot = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/auth/signup/start",
        method: "POST",
        headers: { "Content-Type": "application/json" }
      }, {
        phoneNumber: "+91 98765 43210",
        botCheckToken: "low_score_bot_token"
      });
      assert.equal(resBot.status, 403);
      assert.equal(resBot.body.code, "ERR_BOT_CHECK_FAILED");

      // 2. Web request with tampered/invalid token -> 403 ERR_BOT_CHECK_FAILED
      const resInvalidToken = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/auth/signup/start",
        method: "POST",
        headers: { "Content-Type": "application/json" }
      }, {
        phoneNumber: "+91 98765 43210",
        botCheckToken: "invalid_tampered_token"
      });
      assert.equal(resInvalidToken.status, 403);
      assert.equal(resInvalidToken.body.code, "ERR_BOT_CHECK_FAILED");

      // 3. Web request with valid high human score (score = 0.9) -> 200 OK
      const resHuman = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/auth/signup/start",
        method: "POST",
        headers: { "Content-Type": "application/json" }
      }, {
        phoneNumber: "+91 98765 43210",
        botCheckToken: "valid_high_score_human_token"
      });
      assert.equal(resHuman.status, 200);
      assert.ok(resHuman.body.flowId);
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });

  test("4. CSRF Defense: Cross-Origin rejection and double-submit token enforcement", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    try {
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      const uid = "usr_csrf_test_4004";
      const session = await createSession({ uid, clientType: "WEB" });

      // 1. Attack Scenario A: Malicious Cross-Origin request with stolen __session cookie
      const resAttack = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Cookie": `__session=${session.sessionCookieValue}`,
          "Origin": "https://evil-phishing-attacker.com",
          "X-CSRF-Token": session.csrfToken
        }
      }, {
        principalAmountPaise: 50000,
        counterpartyRole: "BORROWER",
        partyName: "Attacker Target"
      });
      assert.equal(resAttack.status, 403);
      assert.equal(resAttack.body.code, "ERR_CROSS_ORIGIN_FORBIDDEN");

      // 2. Attack Scenario B: Trusted Origin but missing X-CSRF-Token header
      const resMissingCsrf = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Cookie": `__session=${session.sessionCookieValue}`,
          "Origin": "https://app.udhaar.jyotirmoyb.com"
        }
      }, {
        principalAmountPaise: 50000,
        counterpartyRole: "BORROWER",
        partyName: "Attacker Target"
      });
      assert.equal(resMissingCsrf.status, 403);
      assert.equal(resMissingCsrf.body.code, "ERR_CSRF_MISSING");

      // 3. Attack Scenario C: Tampered / Wrong CSRF token (not matching session)
      const resWrongCsrf = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Cookie": `__session=${session.sessionCookieValue}`,
          "Origin": "https://app.udhaar.jyotirmoyb.com",
          "X-CSRF-Token": "forged_random_csrf_token_value_12345678"
        }
      }, {
        principalAmountPaise: 50000,
        counterpartyRole: "BORROWER",
        partyName: "Attacker Target"
      });
      assert.equal(resWrongCsrf.status, 403);
      assert.equal(resWrongCsrf.body.code, "ERR_CSRF_INVALID");

      // 4. Legitimate Request: Trusted Origin + Exact Double-Submit CSRF Token
      const resLegit = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/ledger/records",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Cookie": `__session=${session.sessionCookieValue}`,
          "Origin": "https://app.udhaar.jyotirmoyb.com",
          "X-CSRF-Token": session.csrfToken
        }
      }, {
        principalAmountPaise: 50000,
        counterpartyRole: "BORROWER",
        partyName: "Legitimate Counterparty"
      });
      assert.equal(resLegit.status, 201);
      assert.equal(resLegit.body.principalAmountPaise, 50000);
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });

  test("5. Step-Up Authentication: 10-Minute window protects export and deletion", async () => {
    const app = createApiApp();
    const server = http.createServer(app);
    try {
      await new Promise((r) => server.listen(0, r));
      const port = server.address().port;

      const uid = "usr_step_up_test_5005";
      const authRepo = getAuthRepository();
      await authRepo.createUserTransaction(
        {
          uid,
          phoneNumber: "+91 98765 11111",
          email: "security-officer@udhaar.app",
          displayName: "Security Officer",
          status: "ACTIVE"
        },
        { phoneHash: "hash_phone_11111" },
        { emailHash: "hash_email_officer" }
      );

      // 1. Fresh session (< 10 minutes) accesses full-backup export successfully
      const freshSession = await createSession({ uid, clientType: "WEB" });
      const resFreshExport = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/exports/full-backup",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${freshSession.customToken}`
        }
      });
      assert.equal(resFreshExport.status, 200);
      assert.ok(resFreshExport.body.archiveVersion);

      // 2. Aged session (> 10 minutes elapsed) attempts export
      const agedAuthTime = Math.floor((Date.now() - 15 * 60 * 1000) / 1000); // 15 mins ago
      const authAdapter = getAuthAdapter();
      const agedToken = await authAdapter.createCustomToken(uid, {
        auth_time: agedAuthTime
      });

      const resAgedExport = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/exports/full-backup",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${agedToken}`
        }
      });
      assert.equal(resAgedExport.status, 403);
      assert.equal(resAgedExport.body.code, "ERR_STEP_UP_REQUIRED");

      // 3. User performs Step-Up re-verification via fresh Step-Up token
      const stepUpToken = generateStepUpToken(uid);

      const resSteppedUpExport = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/exports/full-backup",
        method: "GET",
        headers: {
          "Authorization": `Bearer ${agedToken}`,
          "X-Step-Up-Token": stepUpToken
        }
      });
      assert.equal(resSteppedUpExport.status, 200);
      assert.ok(resSteppedUpExport.body.archiveVersion);

      // 4. Account Deletion requires Step-Up
      const resDeleteWithoutStepUp = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/me/account",
        method: "DELETE",
        headers: {
          "Authorization": `Bearer ${agedToken}`
        }
      });
      assert.equal(resDeleteWithoutStepUp.status, 403);
      assert.equal(resDeleteWithoutStepUp.body.code, "ERR_STEP_UP_REQUIRED");

      const resDeleteWithStepUp = await makeRequest(server, {
        hostname: "localhost",
        port,
        path: "/api/v1/me/account",
        method: "DELETE",
        headers: {
          "Authorization": `Bearer ${agedToken}`,
          "X-Step-Up-Token": stepUpToken
        }
      });
      assert.equal(resDeleteWithStepUp.status, 200);
      assert.equal(resDeleteWithStepUp.body.cryptoShredded, true);
    } finally {
      server.closeAllConnections?.();
      server.close();
    }
  });

});
