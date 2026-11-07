/**
 * Stage S8 Test Suite (functions/test/stage_s8_hardening.test.js)
 *
 * Comprehensive verification of:
 * 1. Admin Governance & RBAC: ADMIN > OPERATOR > AUDITOR hierarchy, 403 for unauthorized roles.
 * 2. Mandatory Audit Justification Reason: X-Admin-Reason enforcement on all mutations.
 * 3. Masked User Lookup & Zero Ledger Read: Pseudonymized search, masked PII, no ledger data.
 * 4. Abuse Mitigation: Account lock/unlock, capability guest link revocation.
 * 5. Emergency Kill Switches: Dynamic toggling, 503 on signup, reminders suspension, guest link cutoff.
 * 6. Provider Cost Controls & Circuit Breakers: Daily spend tracking, auto-trip to OPEN, 503 fail-fast, admin reset.
 * 7. DPDP Act 2023 Cryptographic Erasure: DEK shredding (shredUserDek), 30-day cooling period, device wipe, counterparty anonymization ("Deleted User").
 * 8. Consent Management: Inspection & withdrawal.
 * 9. Cross-Tenant IDOR & Security Isolation.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");
const crypto = require("crypto");

const { createApiApp } = require("../src/app");
const {
  resetAdminStore,
  logAdminAction,
  listAdminAuditLogs,
  lookupUserMasked,
  setAccountLockState,
  revokeGuestLinkAdmin,
  getSystemOverviewMetrics,
  _inMemoryAdminAuditLogs
} = require("../src/admin/adminService");

const {
  setAdminRole,
  removeAdminRole,
  clearAdminRoles,
  _inMemoryAdminRoles
} = require("../src/middleware/adminMiddleware");

const {
  isFeatureEnabled,
  getFeatureFlags,
  updateFeatureFlag,
  resetFeatureFlags
} = require("../src/config/killSwitches");

const {
  checkProviderCircuit,
  recordProviderSpend,
  resetProviderCircuit,
  getCircuitStatus,
  resetAllCircuits
} = require("../src/config/circuitBreakers");

const {
  requestAccountDeletion,
  cancelAccountDeletion,
  getUserConsents,
  updateUserConsents,
  COOLING_PERIOD_MS
} = require("../src/privacy/privacyService");

const {
  getOrCreateUserDek,
  shredUserDek,
  encryptRecordFields,
  decryptRecordFields,
  clearDekCache,
  _inMemoryKeyStore
} = require("../src/crypto/envelopeCrypto");

const { createRecord, getRecord, resetRecordsStore, _inMemoryRecords } = require("../src/ledger/recordService");
const { resetAuthStore, _inMemoryUsers } = require("../src/auth/authService");
const { createSession, resetSessionsStore } = require("../src/sessions/sessionManager");
const { resetDevicesStore, registerDevice, _inMemoryUserDevices } = require("../src/push/pushService");
const { createCapabilityLink, _inMemoryLinks, resolveAndVerifyLink } = require("../src/links/linkService");
const { evaluatePendingReminders, resetRemindersStore } = require("../src/reminders/reminderService");
const { fakePush } = require("../src/providers/push");
const { fakeWhatsApp, fakeEmail } = require("../src/providers");

test.beforeEach(() => {
  resetAdminStore();
  clearAdminRoles();
  resetFeatureFlags();
  resetAllCircuits();
  resetAuthStore();
  resetRecordsStore();
  resetDevicesStore();
  resetRemindersStore();
  resetSessionsStore();
  clearDekCache();
  fakePush.reset();
  fakeWhatsApp.reset();
  fakeEmail.reset();
});

// Helper for test HTTP requests
function makeRequest(server, options, requestBody = null) {
  return new Promise((resolve, reject) => {
    const payload = requestBody ? (typeof requestBody === "string" ? requestBody : JSON.stringify(requestBody)) : null;
    const reqOptions = { ...options };
    if (payload) {
      reqOptions.headers = {
        ...reqOptions.headers,
        "Content-Length": Buffer.byteLength(payload)
      };
    }
    const req = http.request(reqOptions, (res) => {
      let data = "";
      res.on("data", (chunk) => {
        data += chunk;
      });
      res.on("end", () => {
        let json = null;
        try {
          json = JSON.parse(data);
        } catch (_) {}
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: json !== null ? json : data
        });
      });
    });

    req.on("error", reject);

    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

// -------------------------------------------------------------
// 1. Admin Governance & RBAC Hierarchy (ADMIN > OPERATOR > AUDITOR)
// -------------------------------------------------------------
test("Stage S8: 1.1 Admin RBAC Role Permissions & Restrictions", async () => {
  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    // Seed regular user, auditor, operator, admin
    const regularUid = "usr_regular_001";
    const auditorUid = "usr_auditor_001";
    const operatorUid = "usr_operator_001";
    const adminUid = "usr_admin_001";

    const sessionRegular = await createSession({ uid: regularUid, authTime: Date.now() });
    const sessionAuditor = await createSession({ uid: auditorUid, authTime: Date.now() });
    const sessionOperator = await createSession({ uid: operatorUid, authTime: Date.now() });
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });

    setAdminRole(auditorUid, "AUDITOR");
    setAdminRole(operatorUid, "OPERATOR");
    setAdminRole(adminUid, "ADMIN");

    // Case 1: Unauthenticated request to /admin/overview -> 401
    const unauthRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/overview",
      method: "GET"
    });
    assert.equal(unauthRes.status, 401);

    // Case 2: Regular user (no admin role) -> 403 Forbidden
    const regularRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/overview",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionRegular.sessionCookieValue}` }
    });
    assert.equal(regularRes.status, 403);
    assert.equal(regularRes.body.code, "ERR_FORBIDDEN");

    // Case 3: AUDITOR can read overview and audit logs
    const auditorOverview = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/overview",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAuditor.sessionCookieValue}` }
    });
    assert.equal(auditorOverview.status, 200);
    assert.ok(auditorOverview.body.users);
    assert.ok(auditorOverview.body.costControls);

    // Case 4: AUDITOR is forbidden from mutating kill switches (requires OPERATOR or ADMIN)
    const auditorMutate = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/kill-switches",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAuditor.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Attempting auditor override"
      },
      body: { flagName: "KILL_SWITCH_REGISTRATION", enabled: true }
    }, { flagName: "KILL_SWITCH_REGISTRATION", enabled: true });
    assert.equal(auditorMutate.status, 403);

    // Case 5: OPERATOR can mutate kill switches
    const operatorMutate = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/kill-switches",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionOperator.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Emergency pause of new registrations"
      }
    }, { flagName: "KILL_SWITCH_REGISTRATION", enabled: true });
    assert.equal(operatorMutate.status, 200);
    assert.equal(operatorMutate.body.isKilled, true);

    // Case 6: OPERATOR is forbidden from resetting cost circuits (requires ADMIN)
    const operatorResetCircuit = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/circuits/reset",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionOperator.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Operator trying to reset spend budget"
      }
    }, { providerName: "WHATSAPP" });
    assert.equal(operatorResetCircuit.status, 403);

    // Case 7: ADMIN can reset cost circuits
    const adminResetCircuit = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/circuits/reset",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Approved budget expansion for production launch"
      }
    }, { providerName: "WHATSAPP", newDailyLimit: 1000 });
    assert.equal(adminResetCircuit.status, 200);
    assert.equal(adminResetCircuit.body.dailyLimit, 1000);
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 2. Mandatory Audit Justification Reason (X-Admin-Reason)
// -------------------------------------------------------------
test("Stage S8: 2.1 Enforce Mandatory Justification Reason on Mutations", async () => {
  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_admin_reason_test";
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });
    setAdminRole(adminUid, "ADMIN");

    // Mutation with missing reason header and missing body reason -> 400 Bad Request
    const missingReasonRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/kill-switches",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json"
      }
    }, { flagName: "KILL_SWITCH_WHATSAPP", enabled: true });
    assert.equal(missingReasonRes.status, 400);
    assert.equal(missingReasonRes.body.code, "ERR_ADMIN_REASON_REQUIRED");

    // Mutation with short reason (< 5 characters) -> 400 Bad Request
    const shortReasonRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/kill-switches",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "fix"
      }
    }, { flagName: "KILL_SWITCH_WHATSAPP", enabled: true });
    assert.equal(shortReasonRes.status, 400);
    assert.equal(shortReasonRes.body.code, "ERR_ADMIN_REASON_REQUIRED");

    // Mutation with valid header reason -> 200 Success
    const validReasonRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/kill-switches",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Meta Cloud API degradation reported on incident #INC-402"
      }
    }, { flagName: "KILL_SWITCH_WHATSAPP", enabled: true });
    assert.equal(validReasonRes.status, 200);

    // Verify reason logged in immutable audit logs
    const logs = await listAdminAuditLogs({ action: "UPDATE_KILL_SWITCH" });
    assert.ok(logs.length >= 1);
    assert.equal(logs[0].reason, "Meta Cloud API degradation reported on incident #INC-402");
    assert.equal(logs[0].adminUid, adminUid);
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 3. Masked User Lookup & Zero Ledger Read Policy
// -------------------------------------------------------------
test("Stage S8: 3.1 Masked User Lookup & Inaccessibility of Ledger", async () => {
  const targetUid = "usr_target_lookup_999";
  const rawPhone = "+919876543210";
  const phoneHash = crypto.createHash("sha256").update(rawPhone).digest("hex");
  const emailHash = crypto.createHash("sha256").update("alice@example.com").digest("hex");

  _inMemoryUsers.set(targetUid, {
    uid: targetUid,
    displayName: "Alice Sharma",
    phoneHash,
    maskedPhone: "+91 98••• ••210",
    emailHash,
    maskedEmail: "al•••@example.com",
    status: "ACTIVE",
    isLocked: false,
    createdAtMs: Date.now() - 100000
  });

  // Create a record for Alice
  await createRecord({
    userId: targetUid,
    recordData: {
      counterpartyRole: "LENDER",
      partyName: "Bob Builder",
      principalAmountPaise: 500000,
      note: "Confidential debt notes"
    }
  });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_admin_lookup_tester";
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });
    setAdminRole(adminUid, "ADMIN");

    // Perform lookup by phoneHash
    const lookupRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/users/lookup",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Customer support identity verification ticket #5512"
      }
    }, { queryHash: phoneHash });

    assert.equal(lookupRes.status, 200);
    assert.equal(lookupRes.body.uid, targetUid);
    assert.equal(lookupRes.body.maskedPhone, "+91 98••• ••210");
    assert.equal(lookupRes.body.maskedEmail, "al•••@example.com");
    assert.equal(lookupRes.body.isLocked, false);

    // CRITICAL: Ensure zero decrypted financial records are returned
    assert.equal(lookupRes.body.records, undefined);
    assert.equal(lookupRes.body.balancePaise, undefined);
    assert.ok(lookupRes.body.notesCountNotice.includes("envelope encrypted"));

    // Lookup non-existent hash returns 404
    const notFoundRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/users/lookup",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Customer support search query"
      }
    }, { queryHash: "0000000000000000000000000000000000000000000000000000000000000000" });
    assert.equal(notFoundRes.status, 404);
    assert.equal(notFoundRes.body.code, "ERR_USER_NOT_FOUND");
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 4. Abuse Mitigation: Account Lock/Unlock & Guest Link Revocation
// -------------------------------------------------------------
test("Stage S8: 4.1 Account Lock/Unlock & Capability Link Revocation", async () => {
  const targetUid = "usr_abuse_target_123";
  _inMemoryUsers.set(targetUid, {
    uid: targetUid,
    status: "ACTIVE",
    isLocked: false
  });

  const rec = await createRecord({
    uid: targetUid,
    recordData: {
      counterpartyRole: "LENDER",
      partyName: "Guest Partner",
      partyPhone: "+919999999999",
      principalAmountPaise: 250000
    }
  });

  // Seed a capability guest link
  const link = await createCapabilityLink({
    uid: targetUid,
    recordId: rec.recordId
  });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_operator_mod_1";
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });
    setAdminRole(adminUid, "OPERATOR");

    // 1. Lock Account
    const lockRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/admin/users/${targetUid}/lock`,
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Fraudulent chargeback activity reported"
      }
    }, { locked: true });

    assert.equal(lockRes.status, 200);
    assert.equal(lockRes.body.isLocked, true);
    assert.equal(_inMemoryUsers.get(targetUid).isLocked, true);

    // 2. Unlock Account
    const unlockRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/admin/users/${targetUid}/lock`,
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "User verified with KYC documents"
      }
    }, { locked: false });

    assert.equal(unlockRes.status, 200);
    assert.equal(unlockRes.body.isLocked, false);
    assert.equal(_inMemoryUsers.get(targetUid).isLocked, false);

    // 3. Revoke Capability Guest Link
    const revokeLinkRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/admin/links/${link.recordId}/revoke`,
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Guest reported link leak on public forum"
      }
    });

    assert.equal(revokeLinkRes.status, 200);
    assert.equal(revokeLinkRes.body.isRevoked, true);

    // Verifying link resolution now rejects as revoked
    assert.throws(
      () => resolveAndVerifyLink(link.recordId, link.rawToken),
      (err) => err.code === "ERR_LINK_REVOKED"
    );
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 5. Emergency Kill Switches Enforcement
// -------------------------------------------------------------
test("Stage S8: 5.1 Kill Switches Dynamic Execution & Service Disablement", async () => {
  // Test KILL_SWITCH_REGISTRATION blocks sign up start
  updateFeatureFlag({ flagName: "KILL_SWITCH_REGISTRATION", enabled: true });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const signupRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/auth/signup/start",
      method: "POST",
      headers: { "Content-Type": "application/json" }
    }, { phone: "+919876543210", locale: "en" });

    assert.equal(signupRes.status, 503);
    assert.equal(signupRes.body.code, "ERR_REGISTRATION_SUSPENDED");
    assert.ok(signupRes.body.detail.includes("temporarily suspended"));

    // Reset registration kill switch
    updateFeatureFlag({ flagName: "KILL_SWITCH_REGISTRATION", enabled: false });

    // Test KILL_SWITCH_REMINDERS suspends evaluation
    updateFeatureFlag({ flagName: "KILL_SWITCH_REMINDERS", enabled: true });
    const reminderEvalResult = await evaluatePendingReminders({ uid: "usr_any", nowMs: Date.now() });
    assert.equal(reminderEvalResult.remindersSuspended, true);
    assert.equal(reminderEvalResult.pendingReminders.length, 0);

    // Reset reminders kill switch
    updateFeatureFlag({ flagName: "KILL_SWITCH_REMINDERS", enabled: false });

    // Test KILL_SWITCH_GUEST_LINKS suspends guest resolution
    const creatorUid = "usr_kill_switch_creator";
    _inMemoryUsers.set(creatorUid, { uid: creatorUid, status: "ACTIVE" });
    const tempRec = await createRecord({
      uid: creatorUid,
      recordData: {
        counterpartyRole: "LENDER",
        partyName: "Friend",
        partyPhone: "+919876543210",
        principalAmountPaise: 100000
      }
    });
    const tempLink = await createCapabilityLink({
      uid: creatorUid,
      recordId: tempRec.recordId
    });

    updateFeatureFlag({ flagName: "KILL_SWITCH_GUEST_LINKS", enabled: true });
    assert.throws(
      () => resolveAndVerifyLink(tempLink.recordId, tempLink.rawToken),
      (err) => err.code === "ERR_GUEST_PORTAL_SUSPENDED"
    );
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 6. Cost Controls & Provider Circuit Breakers
// -------------------------------------------------------------
test("Stage S8: 6.1 Provider Circuit Breaker Spend Cap Tripping & Admin Reset", async () => {
  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const adminUid = "usr_admin_circuits";
    const sessionAdmin = await createSession({ uid: adminUid, authTime: Date.now() });
    setAdminRole(adminUid, "ADMIN");

    // Check initial state -> CLOSED
    const initialCheck = checkProviderCircuit("WHATSAPP");
    assert.equal(initialCheck.allowed, true);
    assert.equal(initialCheck.state, "CLOSED");

    // Spend 140 units (below hourly limit of 150)
    recordProviderSpend("WHATSAPP", 140);
    assert.equal(checkProviderCircuit("WHATSAPP").allowed, true);

    // 15 more units trips hourly limit (155 >= 150) to OPEN
    recordProviderSpend("WHATSAPP", 15);
    const trippedCheck = checkProviderCircuit("WHATSAPP");
    assert.equal(trippedCheck.allowed, false);
    assert.equal(trippedCheck.state, "OPEN");

    // Any operation checking WHATSAPP circuit now fails fast
    const circuitsRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/circuits",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAdmin.sessionCookieValue}` }
    });
    assert.equal(circuitsRes.status, 200);
    assert.equal(circuitsRes.body.WHATSAPP.state, "OPEN");
    assert.equal(circuitsRes.body.WHATSAPP.hourlySpent, 155);

    // Reset circuit via Admin API
    const resetRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/admin/circuits/reset",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionAdmin.sessionCookieValue}`,
        "Content-Type": "application/json",
        "X-Admin-Reason": "Approved budget expansion following traffic surge"
      }
    }, { providerName: "WHATSAPP", newDailyLimit: 2000 });

    assert.equal(resetRes.status, 200);
    assert.equal(resetRes.body.state, "CLOSED");
    assert.equal(resetRes.body.dailyLimit, 2000);

    // Verify circuit is now back to CLOSED
    const postResetCheck = checkProviderCircuit("WHATSAPP");
    assert.equal(postResetCheck.allowed, true);
    assert.equal(postResetCheck.state, "CLOSED");
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 7. DPDP Act 2023 Section 11 Right to Erasure & Crypto-Shredding
// -------------------------------------------------------------
test("Stage S8: 7.1 Immediate Cryptographic DEK Shredding & Account Deletion Engine", async () => {
  const userA = "usr_subject_alice_dpdp";
  const userB = "usr_counterparty_bob_dpdp";

  _inMemoryUsers.set(userA, {
    uid: userA,
    displayName: "Alice In Wonderland",
    status: "ACTIVE",
    isLocked: false,
    createdAtMs: Date.now() - 500000,
    consents: { termsAccepted: true, whatsappUpdates: true }
  });

  _inMemoryUsers.set(userB, {
    uid: userB,
    displayName: "Bob The Builder",
    status: "ACTIVE",
    isLocked: false
  });

  // Seed encrypted record in userA's ledger and reciprocal record in userB's ledger
  const dekA = await getOrCreateUserDek(userA);
  assert.ok(dekA);

  const encRecordA = await createRecord({
    uid: userA,
    recordData: {
      counterpartyRole: "LENDER",
      partyName: "Bob The Builder",
      principalAmountPaise: 500000,
      note: "Loan for construction equipment"
    }
  });

  // User B holds reciprocal record with linkedCreatorUid = userA
  const recordB = await createRecord({
    uid: userB,
    recordData: {
      counterpartyRole: "BORROWER",
      partyName: "Alice In Wonderland",
      partyPhone: "+919876543210",
      principalAmountPaise: 500000,
      note: "Borrowed from Alice",
      linkedCreatorUid: userA
    }
  });

  // Register push devices for userA
  await registerDevice({
    uid: userA,
    deviceData: {
      deviceId: "device_alice_phone",
      platform: "WEB",
      fcmToken: "token_alice_123"
    }
  });
  assert.equal(_inMemoryUserDevices.get(userA)?.size, 1);

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const sessionA = await createSession({ uid: userA, authTime: Date.now() });

    // 1. Execute DELETE /api/v1/me/account
    const deleteRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/me/account",
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${sessionA.sessionCookieValue}`,
        "Content-Type": "application/json"
      }
    }, { reason: "Closing business and invoking DPDP Section 11 Right to Erasure" });

    assert.equal(deleteRes.status, 200);
    assert.equal(deleteRes.body.cryptoShredded, true);
    assert.equal(deleteRes.body.status, "PENDING_DELETION");
    assert.equal(deleteRes.body.coolingPeriodDays, 30);

    // 2. Verify Cryptographic DEK is mathematically destroyed
    assert.equal(_inMemoryKeyStore.has(userA), false);

    // 3. Verify Registered push devices are cleared
    assert.equal(_inMemoryUserDevices.get(userA)?.size, 0);

    // 4. Verify Counterparty B's record has been masked to preserve accounting integrity without exposing erased PII
    const bRecords = _inMemoryRecords.get(userB);
    const updatedRecB = bRecords.get(recordB.recordId);
    assert.equal(updatedRecB.partyName, "Deleted User");
    assert.equal(updatedRecB.partyPhone, "+910000000000");
    assert.equal(updatedRecB.principalAmountPaise, 500000); // Balance preserved

    // 5. Test Cooling Period Recovery: User A cancels deletion
    const cancelRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/me/account/cancel-deletion",
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionA.sessionCookieValue}`,
        "Content-Type": "application/json"
      }
    });

    assert.equal(cancelRes.status, 200);
    assert.equal(cancelRes.body.status, "ACTIVE");
    assert.equal(_inMemoryUsers.get(userA).status, "ACTIVE");
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 8. Consent Management (DPDP Act 2023)
// -------------------------------------------------------------
test("Stage S8: 8.1 Consent Inspection & Withdrawal", async () => {
  const uid = "usr_consent_alice";
  _inMemoryUsers.set(uid, {
    uid,
    status: "ACTIVE",
    consents: {
      termsAccepted: true,
      whatsappUpdates: true,
      optionalAnalytics: true
    }
  });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const session = await createSession({ uid, authTime: Date.now() });

    // 1. Read consents
    const getConsentRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/me/consent",
      method: "GET",
      headers: { Authorization: `Bearer ${session.sessionCookieValue}` }
    });

    assert.equal(getConsentRes.status, 200);
    assert.equal(getConsentRes.body.consents.termsAccepted, true);
    assert.equal(getConsentRes.body.consents.optionalAnalytics, true);

    // 2. Withdraw optional analytics consent
    const updateConsentRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/me/consent",
      method: "POST",
      headers: {
        Authorization: `Bearer ${session.sessionCookieValue}`,
        "Content-Type": "application/json"
      }
    }, { optionalAnalytics: false });

    assert.equal(updateConsentRes.status, 200);
    assert.equal(updateConsentRes.body.consents.optionalAnalytics, false);
    assert.equal(_inMemoryUsers.get(uid).consents.optionalAnalytics, false);
  } finally {
    server.close();
  }
});

// -------------------------------------------------------------
// 9. Cross-Tenant IDOR & Security Isolation
// -------------------------------------------------------------
test("Stage S8: 9.1 Cross-Tenant Isolation Across Domain Endpoints", async () => {
  const victimUid = "usr_victim_vault";
  const attackerUid = "usr_attacker_spy";

  _inMemoryUsers.set(victimUid, { uid: victimUid, status: "ACTIVE" });
  _inMemoryUsers.set(attackerUid, { uid: attackerUid, status: "ACTIVE" });

  const victimRecord = await createRecord({
    uid: victimUid,
    recordData: {
      counterpartyRole: "LENDER",
      partyName: "Supplier X",
      principalAmountPaise: 7500000,
      note: "Secret supplier loan terms"
    }
  });

  const app = createApiApp();
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  try {
    const sessionAttacker = await createSession({ uid: attackerUid, authTime: Date.now() });

    // Attacker tries to read Victim's record -> 404/403 (Not Found within attacker's tenant)
    const idorReadRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: `/api/v1/ledger/records/${victimRecord.recordId}`,
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAttacker.sessionCookieValue}` }
    });

    // In a multi-tenant design, requesting a record not owned by the tenant returns 404 (preventing record ID enumeration)
    assert.ok(idorReadRes.status === 404 || idorReadRes.status === 403);

    // Attacker tries to fetch Victim's export archive
    const exportRes = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/exports/full-backup",
      method: "GET",
      headers: { Authorization: `Bearer ${sessionAttacker.sessionCookieValue}` }
    });

    assert.equal(exportRes.status, 200);
    // Attacker's export must have 0 records, none of victim's records
    assert.equal(exportRes.body.ledger.records.length, 0);
    assert.equal(exportRes.body.dataSubject.uid, attackerUid);
  } finally {
    server.close();
  }
});
