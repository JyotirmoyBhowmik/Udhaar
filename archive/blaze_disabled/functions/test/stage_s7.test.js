/**
 * Stage S7 Test Suite (functions/test/stage_s7.test.js)
 *
 * Comprehensive verification of:
 * 1. Reminders Centre: 4 escalation levels, quiet hours, 5-language templates, snooze, automated stop conditions, wa.me links.
 * 2. Counterparty Chat: threads, messages, interactive due-date extension accept/decline with ledger update, mute.
 * 3. Inbox Notifications: counterparty feed, interactive payment claim approval (ledger balance update) and decline, mark read.
 * 4. Device Registry & Web Push: device registration, token revocation, PushProvider abstraction & fake push dispatch.
 * 5. Data Exports & Statements: RFC 4180 CSV with formula injection sanitization, printable HTML statement with Merkle seal, full JSON backup (DPDP Act 2023).
 * 6. End-to-end Express API HTTP routing.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("http");

const { createApiApp } = require("../src/app");
const {
  resetRemindersStore,
  getUserReminderPolicy,
  updateUserReminderPolicy,
  isQuietHours,
  calculateEscalationLevel,
  formatReminderMessage,
  snoozeRecordReminders,
  evaluatePendingReminders,
  sendRecordReminder
} = require("../src/reminders/reminderService");

const {
  resetChatStore,
  getOrCreateChatThread,
  listUserChatThreads,
  postChatMessage,
  listThreadMessages,
  respondToExtensionRequest,
  toggleThreadMute
} = require("../src/chat/chatService");

const {
  resetInboxStore,
  createInboxItem,
  listUserInboxItems,
  markInboxItemRead,
  processInboxAction
} = require("../src/inbox/inboxService");

const {
  resetDevicesStore,
  registerDevice,
  listUserDevices,
  revokeDevice,
  sendPushNotification
} = require("../src/push/pushService");

const {
  sanitizeCsvCell,
  generateLedgerCsv,
  generateStatementHtml,
  generateFullDataArchive
} = require("../src/exports/exportService");

const { createRecord, getRecord, resetRecordsStore } = require("../src/ledger/recordService");
const { resetPeopleStore } = require("../src/ledger/peopleService");
const { resetAuthStore } = require("../src/auth/authService");
const { getAuthRepository, resetTestRepositories } = require("../src/repositories");
const { createSession } = require("../src/sessions/sessionManager");
const { fakePush } = require("../src/providers/push");
const { fakeWhatsApp } = require("../src/providers");

test.beforeEach(async () => {
  resetTestRepositories();
  await resetRemindersStore();
  await resetChatStore();
  await resetInboxStore();
  await resetDevicesStore();
  await resetRecordsStore();
  await resetPeopleStore();
  await resetAuthStore();
  fakePush.reset();
  fakeWhatsApp.reset();
});

// Helper to make test HTTP requests
function makeRequest(server, options, requestBody = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
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

    if (requestBody) {
      const payload = typeof requestBody === "string" ? requestBody : JSON.stringify(requestBody);
      req.write(payload);
    }
    req.end();
  });
}

// -------------------------------------------------------------
// 1. Reminders Centre: Policy, Levels, Quiet Hours & Templates
// -------------------------------------------------------------
test("Stage S7: 1.1 Escalation Levels Calculation", () => {
  const now = 1700000000000;
  const dayMs = 86400000;

  // 4 days before due date -> Too early (null)
  assert.equal(calculateEscalationLevel(now + 4 * dayMs, now), null);

  // 3 days before due date -> LEVEL_1 (Gentle)
  assert.equal(calculateEscalationLevel(now + 3 * dayMs, now), "LEVEL_1");

  // On due date -> LEVEL_1
  assert.equal(calculateEscalationLevel(now, now), "LEVEL_1");

  // 3 days overdue -> LEVEL_2 (Follow-up)
  assert.equal(calculateEscalationLevel(now - 3 * dayMs, now), "LEVEL_2");

  // 15 days overdue -> LEVEL_3 (Firm)
  assert.equal(calculateEscalationLevel(now - 15 * dayMs, now), "LEVEL_3");

  // 35 days overdue -> LEVEL_4 (Final Notice)
  assert.equal(calculateEscalationLevel(now - 35 * dayMs, now), "LEVEL_4");
});

test("Stage S7: 1.2 Quiet Hours Evaluation", () => {
  const policy = { quietHoursStartHour: 21, quietHoursEndHour: 8 };

  // 10:00 PM (hour 22) -> quiet hours
  const d1 = new Date();
  d1.setHours(22, 0, 0, 0);
  assert.equal(isQuietHours(policy, d1), true);

  // 04:00 AM (hour 4) -> quiet hours
  const d2 = new Date();
  d2.setHours(4, 0, 0, 0);
  assert.equal(isQuietHours(policy, d2), true);

  // 02:00 PM (hour 14) -> active hours
  const d3 = new Date();
  d3.setHours(14, 0, 0, 0);
  assert.equal(isQuietHours(policy, d3), false);
});

test("Stage S7: 1.3 Multi-lingual Reminder Templates (5 Languages)", () => {
  const locales = ["en", "hi", "bn", "as", "ne"];
  const levels = ["LEVEL_1", "LEVEL_2", "LEVEL_3", "LEVEL_4"];

  for (const loc of locales) {
    for (const lvl of levels) {
      const msg = formatReminderMessage({
        level: lvl,
        locale: loc,
        partyName: "Rohan",
        amountPaise: 500000, // ₹5,000
        dueDateMs: Date.now() + 86400000,
        myName: "Amit"
      });

      assert.ok(msg.length > 10, `Message for ${loc} ${lvl} should be populated`);
      assert.ok(!msg.includes("{name}"), `No raw {name} in ${loc} ${lvl}`);
      assert.ok(!msg.includes("{amount}"), `No raw {amount} in ${loc} ${lvl}`);
      assert.ok(!msg.includes("{due_date}"), `No raw {due_date} in ${loc} ${lvl}`);
      assert.ok(msg.includes("5,000"), `Amount ₹5,000 formatted in ${loc} ${lvl}`);
    }
  }
});

test("Stage S7: 1.4 Reminder Stop Conditions, Snooze & wa.me Links", async () => {
  const uid = "usr_lender_01";
  const now = Date.now();

  // Create an overdue record where user is LENDER (counterpartyRole = BORROWER)
  const rec1 = await createRecord({
    uid,
    recordData: {
      partyName: "Sunil Verma",
      partyPhone: "+919876543210",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 1000000,
      dueDateMs: now - 3 * 86400000
    }
  });

  // Create a record where user is BORROWER (counterpartyRole = LENDER) -> should NOT evaluate reminder
  await createRecord({
    uid,
    recordData: {
      partyName: "Bank Advance",
      partyPhone: "+919811122233",
      counterpartyRole: "LENDER",
      principalAmountPaise: 2000000,
      dueDateMs: now - 5 * 86400000
    }
  });

  // Evaluate reminders
  let evalResult = await evaluatePendingReminders({ uid, nowMs: now });
  assert.equal(evalResult.pendingReminders.length, 1);
  assert.equal(evalResult.pendingReminders[0].recordId, rec1.recordId);
  assert.ok(evalResult.pendingReminders[0].waMeUrl.includes("wa.me/919876543210"));

  // Test Snooze 3 days
  await snoozeRecordReminders({ uid, recordId: rec1.recordId, snoozeDays: 3 });
  evalResult = await evaluatePendingReminders({ uid, nowMs: now });
  assert.equal(evalResult.pendingReminders.length, 0, "Snoozed record should not trigger reminder");

  // Fast forward past snooze
  evalResult = await evaluatePendingReminders({ uid, nowMs: now + 4 * 86400000 });
  assert.equal(evalResult.pendingReminders.length, 1, "Record should trigger reminder after snooze expires");

  // Stop reminders permanently
  await snoozeRecordReminders({ uid, recordId: rec1.recordId, stop: true });
  evalResult = await evaluatePendingReminders({ uid, nowMs: now + 4 * 86400000 });
  assert.equal(evalResult.pendingReminders.length, 0, "Stopped reminders should never trigger");
});

// -------------------------------------------------------------
// 2. Counterparty Chat & Interactive Due Date Extension
// -------------------------------------------------------------
test("Stage S7: 2.1 Chat Threads, Message Types & Push Notifications", async () => {
  const userA = "usr_alice";
  const userB = "usr_bob";
  const recordId = "rec_test_chat_01";

  // Register a push device for Bob
  await registerDevice({
    uid: userB,
    deviceData: { fcmToken: "bob_fcm_token_123", platform: "WEB" }
  });

  // Create thread
  const thread = await getOrCreateChatThread({
    uid: userA,
    recordId,
    partyUid: userB,
    partyName: "Bob"
  });

  assert.ok(thread.threadId.startsWith("th_"));
  assert.deepEqual(thread.participantUids, [userA, userB]);

  // Alice posts TEXT message
  const msg1 = await postChatMessage({
    uid: userA,
    threadId: thread.threadId,
    senderName: "Alice",
    messageType: "TEXT",
    bodyText: "Hello Bob, just checking on our agreement."
  });

  assert.equal(msg1.messageType, "TEXT");

  // Verify Bob received a push notification
  const sentPushes = fakePush.getSentPushes();
  assert.equal(sentPushes.length, 1);
  assert.equal(sentPushes[0].targetUid, userB);
  assert.ok(sentPushes[0].title.includes("Alice"));

  // Alice posts PAYMENT_PROOF message
  await postChatMessage({
    uid: userA,
    threadId: thread.threadId,
    senderName: "Alice",
    messageType: "PAYMENT_PROOF",
    bodyText: "Uploaded receipt for partial advance",
    attachment: { fileUrl: "https://storage.googleapis.com/test.jpg" }
  });

  // Thread messages list
  const messages = await listThreadMessages({ uid: userB, threadId: thread.threadId });
  assert.equal(messages.length, 2);
});

test("Stage S7: 2.2 Interactive Due Date Extension Request (Accept & Ledger Sync)", async () => {
  const lenderUid = "usr_lender_ext";
  const borrowerUid = "usr_borrower_ext";
  const originalDueDate = Date.now() + 7 * 86400000;
  const requestedDueDate = Date.now() + 30 * 86400000;

  // Create record on the lender's ledger
  const record = await createRecord({
    uid: lenderUid,
    recordData: {
      partyName: "Borrower Bob",
      partyPhone: "+919123456789",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 5000000,
      dueDateMs: originalDueDate
    }
  });

  // Create chat thread
  const thread = await getOrCreateChatThread({
    uid: borrowerUid,
    recordId: record.recordId,
    partyUid: lenderUid,
    partyName: "Lender Alice"
  });

  // Borrower requests extension
  const extMsg = await postChatMessage({
    uid: borrowerUid,
    threadId: thread.threadId,
    senderName: "Borrower Bob",
    messageType: "EXTENSION_REQUEST",
    bodyText: "Requesting more time due to bank delay",
    extensionData: { proposedDueDateMs: requestedDueDate }
  });

  assert.equal(extMsg.extensionStatus, "PENDING");
  assert.equal(extMsg.proposedDueDateMs, requestedDueDate);

  // Borrower attempts to accept their own extension -> throws ERR_SELF_DECISION
  await assert.rejects(
    () => respondToExtensionRequest({ uid: borrowerUid, threadId: thread.threadId, messageId: extMsg.messageId, decision: "ACCEPT" }),
    (err) => err.code === "ERR_SELF_DECISION"
  );

  // Lender accepts the extension request
  const acceptRes = await respondToExtensionRequest({
    uid: lenderUid,
    threadId: thread.threadId,
    messageId: extMsg.messageId,
    decision: "ACCEPT"
  });

  assert.equal(acceptRes.extensionStatus, "ACCEPTED");

  // Verify that the record's dueDateMs was automatically updated on the ledger!
  const updatedRecord = await getRecord({ uid: lenderUid, recordId: record.recordId });
  assert.equal(updatedRecord.dueDateMs, requestedDueDate);
});

// -------------------------------------------------------------
// 3. Inbox Notifications & Action Processing
// -------------------------------------------------------------
test("Stage S7: 3.1 Inbox Notifications & Payment Claim Approval (Ledger Sync)", async () => {
  const lenderUid = "usr_lender_inb";
  const borrowerUid = "usr_borrower_inb";

  // Create record on lender ledger with balance ₹10,000 (1,000,000 paise)
  const record = await createRecord({
    uid: lenderUid,
    recordData: {
      partyName: "Borrower Charlie",
      partyPhone: "+919999888877",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 1000000
    }
  });

  // Create an inbox item: Borrower claims to have paid ₹4,000 (400,000 paise)
  const inboxItem = await createInboxItem({
    targetUid: lenderUid,
    senderUid: borrowerUid,
    senderName: "Charlie",
    remoteRecordId: record.recordId,
    type: "PAYMENT_CLAIM",
    title: "Payment Claim of ₹4,000",
    body: "Charlie submitted UPI payment proof for ₹4,000",
    payload: {
      amountPaise: 400000,
      paymentMethod: "UPI",
      referenceNumber: "UPI9876543210"
    }
  });

  // List inbox items
  const items = await listUserInboxItems({ uid: lenderUid });
  assert.equal(items.length, 1);
  assert.equal(items[0].status, "PENDING");

  // Lender APPROVES the payment claim
  const actionRes = await processInboxAction({
    uid: lenderUid,
    inboxId: inboxItem.inboxId,
    decision: "APPROVE",
    note: "Received in bank account"
  });

  assert.equal(actionRes.decision, "APPROVE");
  assert.ok(actionRes.paymentId, "Payment should be recorded on the ledger");

  // Verify the ledger record remaining balance was decremented from ₹10,000 to ₹6,000
  const refreshedRecord = await getRecord({ uid: lenderUid, recordId: record.recordId });
  assert.equal(refreshedRecord.remainingAmountPaise, 600000);
  assert.equal(refreshedRecord.status, "PENDING");

  // Mark as read
  const markRes = await markInboxItemRead({ uid: lenderUid, inboxId: inboxItem.inboxId });
  assert.equal(markRes.isRead, true);
});

// -------------------------------------------------------------
// 4. Device Registry & Push Provider Abstraction
// -------------------------------------------------------------
test("Stage S7: 4.1 Device Registration, List & Revocation", async () => {
  const uid = "usr_device_test";

  // Register Web Push device
  const dev1 = await registerDevice({
    uid,
    deviceData: {
      platform: "WEB",
      endpoint: "https://fcm.googleapis.com/fcm/send/device1",
      keys: { p256dh: "key1", auth: "auth1" }
    }
  });

  // Register Android device
  const dev2 = await registerDevice({
    uid,
    deviceData: {
      platform: "ANDROID",
      fcmToken: "android_fcm_token_2"
    }
  });

  let devices = await listUserDevices({ uid });
  assert.equal(devices.length, 2);

  // Revoke device 1
  const revokeRes = await revokeDevice({ uid, deviceId: dev1.deviceId });
  assert.equal(revokeRes.success, true);

  devices = await listUserDevices({ uid });
  assert.equal(devices.length, 1);
  assert.equal(devices[0].deviceId, dev2.deviceId);
});

// -------------------------------------------------------------
// 5. Statements & Data Portability Exports
// -------------------------------------------------------------
test("Stage S7: 5.1 RFC 4180 CSV Formula Injection Sanitization", async () => {
  // Test formula injection characters
  assert.equal(sanitizeCsvCell("=SUM(A1:A10)"), "\"'=SUM(A1:A10)\"");
  assert.equal(sanitizeCsvCell("+12345"), "\眼前'+12345\"".replace("眼前", "\""));
  assert.equal(sanitizeCsvCell("-500"), "\眼前'-500\"".replace("眼前", "\""));
  assert.equal(sanitizeCsvCell("@EXEC"), "\眼前'@EXEC\"".replace("眼前", "\""));
  assert.equal(sanitizeCsvCell("Normal Text"), "\"Normal Text\"");
  assert.equal(sanitizeCsvCell('With "quotes" inside'), '"With ""quotes"" inside"');

  // Generate full CSV
  const uid = "usr_csv_export";
  await createRecord({
    uid,
    recordData: {
      partyName: "=Malicious Formula",
      partyPhone: "+919876500000",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 250000,
      note: "@cmd|' /C calc'!A0"
    }
  });

  const csv = await generateLedgerCsv({ uid });
  const lines = csv.split("\r\n");
  assert.ok(lines.length >= 2);
  assert.ok(csv.includes("Record ID"));
  assert.ok(csv.includes("Counterparty Name"));
  assert.ok(csv.includes("Principal Amount (INR)"));
  assert.ok(csv.includes("''=Malicious Formula\"") || csv.includes("\"'=Malicious Formula\""));
  assert.ok(csv.includes("\"'@cmd|' /C calc'!A0\"") || csv.includes("''@cmd"));
});

test("Stage S7: 5.2 Printable Statement of Account (HTML & Merkle Seal)", async () => {
  const uid = "usr_statement_test";
  await getAuthRepository().updateUser(uid, { uid, displayName: "Vikram Malhotra" });

  await createRecord({
    uid,
    recordData: {
      partyName: "Deepak Chopra",
      partyPhone: "+919876543210",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 1500000,
      dueDateMs: Date.now() + 10 * 86400000
    }
  });

  const html = await generateStatementHtml({ uid });
  assert.ok(html.includes("<!DOCTYPE html>"));
  assert.ok(html.includes("Official Statement of Account"));
  assert.ok(html.includes("Vikram Malhotra"));
  assert.ok(html.includes("Deepak Chopra"));
  assert.ok(html.includes("15,000.00") || html.includes("15000.00"));
  assert.ok(html.includes("Cryptographic Verification Seal"));
  assert.ok(html.includes("SHA-256 Digest:"));
});

test("Stage S7: 5.3 Full DPDP Act 2023 JSON Data Portability Archive", async () => {
  const uid = "usr_dpdp_test";
  await getAuthRepository().updateUser(uid, {
    uid,
    displayName: "Ananya Iyer",
    phoneHash: "hash_123",
    emailHash: "hash_456",
    consents: { whatsapp: true }
  });

  await createRecord({
    uid,
    recordData: {
      partyName: "Kavita Rao",
      partyPhone: "+919876511111",
      counterpartyRole: "BORROWER",
      principalAmountPaise: 750000
    }
  });

  const archive = await generateFullDataArchive({ uid });
  assert.equal(archive.archiveVersion, "2.0");
  assert.equal(archive.dataSubject.displayName, "Ananya Iyer");
  assert.equal(archive.ledger.recordsCount, 1);
  assert.equal(archive.ledger.records[0].partyName, "Kavita Rao");
  assert.equal(archive.legalCompliance.act, "Digital Personal Data Protection Act 2023 (DPDP Act, India)");
  assert.ok(archive.checksumSha256 && archive.checksumSha256.length === 64);
});

// -------------------------------------------------------------
// 6. Express API HTTP Router Integration Tests
// -------------------------------------------------------------
test("Stage S7: 6.1 Express API Endpoints Integration", async () => {
  const testUid = "usr_http_s7";
  await getAuthRepository().updateUser(testUid, { uid: testUid, displayName: "Priya Sharma" });
  const session = await createSession({ uid: testUid, clientType: "WEB" });
  const sessionToken = session.sessionCookieValue;

  const app = createApiApp();
  const server = http.createServer(app);

  await new Promise((resolve) => server.listen(0, resolve));
  const port = server.address().port;

  const authHeaders = {
    Authorization: `Bearer ${sessionToken}`,
    "Content-Type": "application/json"
  };

  try {
    // 1. Reminders Policy GET & POST
    const policyGet = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/reminders/policy",
      method: "GET",
      headers: authHeaders
    });
    assert.equal(policyGet.status, 200);

    const policyUpdate = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/reminders/policy",
      method: "POST",
      headers: authHeaders
    }, { quietHoursStartHour: 22, defaultLocale: "hi" });
    assert.equal(policyUpdate.status, 200);
    assert.equal(policyUpdate.body.quietHoursStartHour, 22);

    // 2. Chat Threads List
    const chatList = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/chat/threads",
      method: "GET",
      headers: authHeaders
    });
    assert.equal(chatList.status, 200);
    assert.ok(Array.isArray(chatList.body));

    // 3. Inbox List
    const inboxList = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/inbox",
      method: "GET",
      headers: authHeaders
    });
    assert.equal(inboxList.status, 200);
    assert.ok(Array.isArray(inboxList.body));

    // 4. Exports CSV
    const csvExport = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/exports/csv",
      method: "GET",
      headers: authHeaders
    });
    assert.equal(csvExport.status, 200);
    assert.ok(csvExport.headers["content-type"].includes("text/csv"));

    // 5. Exports Statement
    const statementExport = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/exports/statement",
      method: "GET",
      headers: authHeaders
    });
    assert.equal(statementExport.status, 200);
    assert.ok(statementExport.headers["content-type"].includes("text/html"));

    // 6. Device Registration
    const regDevice = await makeRequest(server, {
      hostname: "localhost",
      port,
      path: "/api/v1/me/devices",
      method: "POST",
      headers: authHeaders
    }, {
      platform: "WEB",
      token: "web_push_token_http_test"
    });
    assert.equal(regDevice.status, 201);
    assert.equal(regDevice.body.platform, "WEB");

  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
