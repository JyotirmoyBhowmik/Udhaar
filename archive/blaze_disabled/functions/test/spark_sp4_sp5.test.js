/**
 * Stage SP4, SP5 & SP6 — App Check, Runbooks, Rate Limiting, Remote Config, Calendar Reminders & Data Safety
 * (functions/test/spark_sp4_sp5.test.js)
 * 
 * Verifies:
 * 1. SP4: App Check setup documentation (docs/app-check-setup.md).
 * 2. SP4: Spark Quota Exhausted runbook (docs/spark-quota-exhausted-runbook.md).
 * 3. SP4: Client write limiter (500 writes/day, 80% warning, midnight reset).
 * 4. SP4: Remote Config kill switches (signup_enabled, guest_links_enabled) & 5-language messages.
 * 5. SP5: Android WorkManager and SyncCloudTransport architecture documentation.
 * 6. SP5: Web calendar reminders (Google Calendar URL & .ics format).
 * 7. SP5: SyncEngine transport selection (FirestoreWebTransport in Spark mode).
 * 8. SP6: Play Data Safety declaration (docs/play-data-safety.md).
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const { ClientWriteLimiter } = require("../../webapp/src/api/clientWriteLimiter");
const { RemoteConfigService, remoteConfigService } = require("../../webapp/src/features/config/remoteConfigService");
const {
  formatIcsDateTime,
  buildGoogleCalendarUrl,
  generateIcsContent
} = require("../../webapp/src/features/reminders/calendarReminderService");
const { FirestoreWebTransport } = require("../../webapp/src/sync/firestoreWebTransport");
const { SyncEngine } = require("../../webapp/src/sync/syncEngine");
const { CapabilityLinkService } = require("../../webapp/src/features/links/capabilityLinkService");

test.describe("Stage SP4: App Check, Runbooks & Quota Guards", () => {
  test("4.1 docs/app-check-setup.md documents Play Integrity and reCAPTCHA v3 setup at zero cost", () => {
    const docPath = path.resolve(__dirname, "../../docs/app-check-setup.md");
    assert.ok(fs.existsSync(docPath), "docs/app-check-setup.md must exist");
    const content = fs.readFileSync(docPath, "utf8");
    assert.match(content, /Play Integrity/i);
    assert.match(content, /reCAPTCHA v3/i);
    assert.match(content, /zero cost/i);
    assert.match(content, /10,000 verifications/i);
    assert.match(content, /Debug Provider/i);
  });

  test("4.2 docs/spark-quota-exhausted-runbook.md documents midnight PST reset and 5-language notices", () => {
    const docPath = path.resolve(__dirname, "../../docs/spark-quota-exhausted-runbook.md");
    assert.ok(fs.existsSync(docPath), "docs/spark-quota-exhausted-runbook.md must exist");
    const content = fs.readFileSync(docPath, "utf8");
    assert.match(content, /RESOURCE_EXHAUSTED/i);
    assert.match(content, /Pacific Time/i);
    assert.match(content, /English/i);
    assert.match(content, /Hindi/i);
    assert.match(content, /Bengali/i);
    assert.match(content, /Assamese/i);
    assert.match(content, /Nepali/i);
  });

  test("4.3 ClientWriteLimiter enforces 500 writes/day cap, 80% warning, and daily reset", () => {
    let mockStorage = {};
    const storageMock = {
      getItem: (k) => mockStorage[k] || null,
      setItem: (k, v) => { mockStorage[k] = String(v); },
      removeItem: (k) => { delete mockStorage[k]; }
    };

    const limiter = new ClientWriteLimiter({ storage: storageMock, maxDailyWrites: 500 });
    assert.equal(limiter.getUsage().count, 0);
    assert.equal(limiter.getUsage().remaining, 500);

    // Initial check passes
    let check = limiter.canWrite(1);
    assert.equal(check.allowed, true);
    assert.equal(check.warningMessage, null);

    // Record 410 writes (past 80% threshold = 400 writes)
    limiter.recordWrite(410);
    assert.equal(limiter.getUsage().count, 410);
    assert.equal(limiter.getUsage().remaining, 90);

    check = limiter.canWrite(1);
    assert.equal(check.allowed, true);
    assert.ok(check.warningMessage.includes("approaching the free daily sync limit"));

    // Record remaining writes up to 500
    limiter.recordWrite(90);
    assert.equal(limiter.getUsage().count, 500);
    assert.equal(limiter.getUsage().remaining, 0);

    // Exceeded
    check = limiter.canWrite(1);
    assert.equal(check.allowed, false);
    assert.ok(check.warningMessage.includes("Daily sync quota reached"));

    // Midnight reset simulation: alter date key in storage
    mockStorage = {};
    mockStorage["udhaar_client_writes_1970-01-01"] = "500";

    const newDayLimiter = new ClientWriteLimiter({ storage: storageMock, maxDailyWrites: 500 });
    assert.equal(newDayLimiter.getUsage().count, 0);
    assert.equal(newDayLimiter.canWrite(1).allowed, true);
  });

  test("4.4 RemoteConfigService handles kill switches and returns 5-language messages", () => {
    const service = new RemoteConfigService();
    assert.equal(service.isSignupEnabled(), true);
    assert.equal(service.isGuestLinksEnabled(), true);

    const languages = ["en", "hi", "bn", "as", "ne"];
    for (const lang of languages) {
      const signupMsg = service.getSignupDisabledMessage(lang);
      const guestMsg = service.getGuestLinksDisabledMessage(lang);
      const quotaMsg = service.getQuotaRestingMessage(lang);

      assert.ok(signupMsg && signupMsg.length > 10, `Signup message missing for ${lang}`);
      assert.ok(guestMsg && guestMsg.length > 10, `Guest links message missing for ${lang}`);
      assert.ok(quotaMsg && quotaMsg.length > 10, `Quota resting message missing for ${lang}`);
    }

    // Toggle override
    service.setOverride("signup_enabled", false);
    assert.equal(service.isSignupEnabled(), false);

    service.setOverride("guest_links_enabled", false);
    assert.equal(service.isGuestLinksEnabled(), false);
  });

  test("4.5 CapabilityLinkService honors remote config kill switch for guest links", async () => {
    remoteConfigService.setOverride("guest_links_enabled", false);

    const linkService = new CapabilityLinkService(null, {
      currentUser: { uid: "user_test", emailVerified: true }
    });

    await assert.rejects(
      async () => {
        await linkService.createCapabilityLink({ id: "rec_1" });
      },
      (err) => {
        assert.equal(err.code, "ERR_GUEST_LINKS_DISABLED");
        assert.ok(err.message.includes("paused"));
        return true;
      }
    );

    // Reset override
    remoteConfigService.setOverride("guest_links_enabled", true);
  });
});

test.describe("Stage SP5: Android Transport, Calendar Reminders & Sync Engine", () => {
  test("5.1 CalendarReminderService formats UTC datetime and builds Google Calendar URL", () => {
    const fixedDateMs = 1794218400000; // 2026-11-10T10:00:00Z
    const icsDate = formatIcsDateTime(fixedDateMs);
    assert.match(icsDate, /^\d{8}T\d{6}Z$/);

    const url = buildGoogleCalendarUrl({
      title: "Udhaar: Collect from Rahul",
      description: "Repayment of ₹5,000",
      dueDateMs: fixedDateMs
    });

    assert.ok(url.startsWith("https://calendar.google.com/calendar/render?"));
    assert.ok(url.includes("action=TEMPLATE"));
    assert.ok(url.includes("Udhaar%3A+Collect+from+Rahul") || url.includes("Udhaar"));
  });

  test("5.2 CalendarReminderService generates standard RFC 5545 iCalendar content", () => {
    const ics = generateIcsContent({
      title: "Udhaar: Return to Amit",
      description: "Repayment of ₹1,200",
      dueDateMs: 1794218400000,
      uid: "test-rec-99"
    });

    assert.ok(ics.includes("BEGIN:VCALENDAR"));
    assert.ok(ics.includes("VERSION:2.0"));
    assert.ok(ics.includes("BEGIN:VEVENT"));
    assert.ok(ics.includes("SUMMARY:Udhaar: Return to Amit"));
    assert.ok(ics.includes("DESCRIPTION:Repayment of ₹1,200"));
    assert.ok(ics.includes("BEGIN:VALARM"));
    assert.ok(ics.includes("TRIGGER:-PT15M"));
    assert.ok(ics.includes("END:VALARM"));
    assert.ok(ics.includes("END:VEVENT"));
    assert.ok(ics.includes("END:VCALENDAR"));
  });

  test("5.3 SyncEngine selects FirestoreWebTransport in Spark mode", () => {
    process.env.UDHAAR_TIER = "spark";

    const memoryDb = {
      open: async () => {},
      getSyncState: async () => null,
      setSyncState: async () => {},
      getPendingMutations: async () => [],
      getAllRecords: async () => []
    };

    const engine = new SyncEngine({ db: memoryDb });
    assert.equal(engine.isSparkMode(), true);

    const transport = engine._getEffectiveTransport();
    assert.ok(transport instanceof FirestoreWebTransport);
  });

  test("5.4 FirestoreWebTransport respects ClientWriteLimiter when quota exhausted", async () => {
    const mockLimiter = {
      canWrite: () => ({ allowed: false, warningMessage: "Daily quota exhausted" }),
      recordWrite: () => {}
    };

    const transport = new FirestoreWebTransport({
      firestore: null,
      auth: null,
      writeLimiter: mockLimiter
    });

    const res = await transport.push({
      deviceId: "test-client",
      changes: [{ syncId: "rec_1", entity: "record", fields: { id: "rec_1" } }]
    });

    assert.equal(res.results.length, 1);
    assert.equal(res.results[0].status, "REJECTED");
    assert.equal(res.results[0].isQuotaResting, true);
  });

  test("5.5 Android FirestoreSyncTransport exists and implements SyncCloudTransport", () => {
    const transportPath = path.resolve(
      __dirname,
      "../../app/src/main/java/com/jyotirmoyb/udhaar/sync/FirestoreSyncTransport.kt"
    );
    assert.ok(fs.existsSync(transportPath), "FirestoreSyncTransport.kt must exist on Android");

    const content = fs.readFileSync(transportPath, "utf8");
    assert.match(content, /class FirestoreSyncTransport/);
    assert.match(content, /: SyncCloudTransport/);
    assert.match(content, /FirebaseFirestore/);
    assert.match(content, /FirebaseAuth/);
    assert.match(content, /monotonic/i);
    assert.match(content, /RESOURCE_EXHAUSTED/);
  });
});

test.describe("Stage SP6: Play Data Safety Declaration", () => {
  test("6.1 docs/play-data-safety.md comprehensively declares collected and uncollected data", () => {
    const docPath = path.resolve(__dirname, "../../docs/play-data-safety.md");
    assert.ok(fs.existsSync(docPath), "docs/play-data-safety.md must exist");

    const content = fs.readFileSync(docPath, "utf8");
    assert.match(content, /Email address/);
    assert.match(content, /phoneStatus: "unverified"/);
    assert.match(content, /Number not verified/);
    assert.match(content, /Bulk contacts/);
    assert.match(content, /Never uploaded/i);
    assert.match(content, /Zero Advertising/i);
    assert.match(content, /Account Deletion/i);
  });
});
