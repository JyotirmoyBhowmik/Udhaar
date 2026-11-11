const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const http = require("node:http");

const rulesPath = path.resolve(__dirname, "../../firestore.rules");
const storageRulesPath = path.resolve(__dirname, "../../storage.rules");
const storageLifecyclePath = path.resolve(__dirname, "../../storage.lifecycle.json");

const rulesText = fs.readFileSync(rulesPath, "utf8");
const storageRulesText = fs.readFileSync(storageRulesPath, "utf8");
const storageLifecycleJson = JSON.parse(fs.readFileSync(storageLifecyclePath, "utf8"));

/**
 * Deterministic Spark Firestore rules evaluator + live Firestore Emulator REST evaluator.
 * Evaluates every Spark collection rule against:
 * - stranger (unauthenticated)
 * - other user (authenticated, verified, different uid)
 * - unverified email (authenticated, matching uid, email_verified: false)
 * - verified owner (authenticated, matching uid, email_verified: true)
 * - expired or revoked guestLinks
 * - fixed actionId caps ('confirm', 'dispute', 'claim_1'..'claim_5', 'date_1'..'date_3')
 * - no updates / no deletes on append-only collections (events, actions)
 */
function createRulesEvaluator() {
  const store = new Map();

  function getDoc(docPath) {
    return store.get(docPath) || null;
  }

  function setSeedDoc(docPath, data) {
    store.set(docPath, structuredClone(data));
  }

  function isSignedIn(auth) {
    return Boolean(auth && auth.uid);
  }

  function isOwner(auth, userId) {
    return isSignedIn(auth) && auth.uid === userId;
  }

  function isVerifiedOwner(auth, userId) {
    return isOwner(auth, userId) && Boolean(auth.token && auth.token.email_verified === true);
  }

  function evaluate({ op, path: docPath, auth, data, nowMs = Date.now() }) {
    const cleanPath = docPath.replace(/^\/+/, "");
    const segs = cleanPath.split("/");

    // 1. /users/{userId}
    if (segs.length === 2 && segs[0] === "users") {
      const userId = segs[1];
      if (op === "get" || op === "list") return isOwner(auth, userId);
      if (op === "create" || op === "update") return isVerifiedOwner(auth, userId);
      if (op === "delete") return false;
    }

    // 2. /users/{userId}/{subcoll}/{docId}
    if (segs.length === 4 && segs[0] === "users") {
      const [, userId, subcoll] = segs;
      const existing = getDoc(cleanPath);

      if (subcoll === "records") {
        if (op === "get" || op === "list") return isVerifiedOwner(auth, userId);
        if (op === "create") {
          return (
            isVerifiedOwner(auth, userId) &&
            data &&
            data.ownerUid === userId &&
            Number.isInteger(data.rev) &&
            data.rev >= 1
          );
        }
        if (op === "update") {
          return (
            isVerifiedOwner(auth, userId) &&
            existing !== null &&
            data &&
            data.ownerUid === userId &&
            Number.isInteger(data.rev) &&
            data.rev === existing.rev + 1
          );
        }
        if (op === "delete") return isVerifiedOwner(auth, userId);
      }

      if (subcoll === "people" || subcoll === "plans" || subcoll === "attachments") {
        if (op === "get" || op === "list" || op === "delete") return isVerifiedOwner(auth, userId);
        if (op === "create" || op === "update") {
          return isVerifiedOwner(auth, userId) && Boolean(data && data.ownerUid === userId);
        }
      }

      if (subcoll === "events") {
        if (op === "get" || op === "list") return isVerifiedOwner(auth, userId);
        if (op === "create") {
          return isVerifiedOwner(auth, userId) && Boolean(data && data.ownerUid === userId);
        }
        if (op === "update" || op === "delete") return false;
      }

      if (subcoll === "changeLog") {
        return false;
      }
    }

    // 3. /users/{userId}/meta/syncSignal/{docId}
    if (segs.length === 5 && segs[0] === "users" && segs[2] === "meta" && segs[3] === "syncSignal") {
      const userId = segs[1];
      if (op === "get" || op === "list" || op === "create" || op === "update" || op === "delete") {
        return isVerifiedOwner(auth, userId);
      }
    }

    // 4. /guestLinks/{linkId}
    if (segs.length === 2 && segs[0] === "guestLinks") {
      const existing = getDoc(cleanPath);
      if (op === "get") {
        if (!isSignedIn(auth) || !existing) return false;
        const ownerMatch =
          auth.uid === existing.ownerUid && Boolean(auth.token && auth.token.email_verified === true);
        const activeLink =
          existing.status === "active" &&
          existing.revoked !== true &&
          typeof existing.expiresAtMs === "number" &&
          existing.expiresAtMs > nowMs;
        return ownerMatch || activeLink;
      }
      if (op === "list") return false;
      if (op === "create") {
        return (
          isSignedIn(auth) &&
          Boolean(auth.token && auth.token.email_verified === true) &&
          Boolean(
            data &&
              data.ownerUid === auth.uid &&
              typeof data.recordId === "string" &&
              typeof data.expiresAtMs === "number" &&
              (data.status === "active" || data.status === "revoked")
          )
        );
      }
      if (op === "update") {
        return (
          isSignedIn(auth) &&
          Boolean(auth.token && auth.token.email_verified === true) &&
          existing !== null &&
          existing.ownerUid === auth.uid &&
          Boolean(data && data.ownerUid === auth.uid)
        );
      }
      if (op === "delete") {
        return (
          isSignedIn(auth) &&
          Boolean(auth.token && auth.token.email_verified === true) &&
          existing !== null &&
          existing.ownerUid === auth.uid
        );
      }
    }

    // 5. /guestLinks/{linkId}/actions/{actionId}
    if (segs.length === 4 && segs[0] === "guestLinks" && segs[2] === "actions") {
      const linkId = segs[1];
      const actionId = segs[3];
      const parentLink = getDoc(`guestLinks/${linkId}`);
      const existingAction = getDoc(cleanPath);
      const allowedActionIds = [
        "confirm",
        "dispute",
        "claim_1",
        "claim_2",
        "claim_3",
        "claim_4",
        "claim_5",
        "date_1",
        "date_2",
        "date_3",
      ];

      if (op === "get" || op === "list") {
        if (!isSignedIn(auth)) return false;
        if (
          existingAction &&
          auth.uid === existingAction.ownerUid &&
          Boolean(auth.token && auth.token.email_verified === true)
        ) {
          return true;
        }
        return Boolean(parentLink && auth.uid === parentLink.ownerUid);
      }
      if (op === "create") {
        if (!isSignedIn(auth) || !parentLink) return false;
        if (existingAction !== null) return false;
        const linkValid =
          parentLink.status === "active" &&
          parentLink.revoked !== true &&
          typeof parentLink.expiresAtMs === "number" &&
          parentLink.expiresAtMs > nowMs;
        if (!linkValid) return false;
        if (!allowedActionIds.includes(actionId)) return false;
        if (!data || data.linkId !== linkId || data.ownerUid !== parentLink.ownerUid) return false;
        if (!["CONFIRM", "DISPUTE", "CLAIM_PAYMENT", "PROPOSE_DATE"].includes(data.type)) return false;
        return true;
      }
      if (op === "update" || op === "delete") {
        return false;
      }
    }

    // 6. Collection group query on actions
    if (segs.length >= 2 && segs[0] === "collectionGroup:actions") {
      const existingAction = getDoc(segs.slice(1).join("/"));
      return (
        isSignedIn(auth) &&
        Boolean(auth.token && auth.token.email_verified === true) &&
        existingAction !== null &&
        auth.uid === existingAction.ownerUid
      );
    }

    return false;
  }

  return { setSeedDoc, evaluate };
}

/**
 * Helper to make a Firestore Emulator REST call with a custom Bearer JWT payload
 * when FIRESTORE_EMULATOR_HOST is running.
 */
function makeEmulatorJwt({ uid, emailVerified = false, isAnonymous = false }) {
  const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      iss: "https://securetoken.google.com/demo-udhaar",
      aud: "demo-udhaar",
      sub: uid,
      user_id: uid,
      email: isAnonymous ? undefined : `${uid}@example.com`,
      email_verified: emailVerified,
      firebase: {
        sign_in_provider: isAnonymous ? "anonymous" : "password",
      },
      iat: Math.floor(Date.now() / 1000) - 60,
      exp: Math.floor(Date.now() / 1000) + 3600,
    })
  ).toString("base64url");
  return `${header}.${payload}.`;
}

function toFirestoreFields(obj) {
  const fields = {};
  for (const [k, v] of Object.entries(obj)) {
    if (typeof v === "string") fields[k] = { stringValue: v };
    else if (typeof v === "boolean") fields[k] = { booleanValue: v };
    else if (typeof v === "number") fields[k] = { integerValue: String(Math.trunc(v)) };
  }
  return { fields };
}

function emulatorRequest({ method, docPath, token, body }) {
  const hostPort = process.env.FIRESTORE_EMULATOR_HOST;
  if (!hostPort) return Promise.resolve(null);
  const [hostname, portStr] = hostPort.split(":");
  const port = Number(portStr || 8080);
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname,
        port,
        method,
        path: `/v1/projects/demo-udhaar/databases/(default)/documents/${docPath}`,
        headers,
      },
      (res) => {
        let raw = "";
        res.on("data", (chunk) => (raw += chunk));
        res.on("end", () => resolve({ status: res.statusCode, body: raw }));
      }
    );
    req.on("error", reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

test("firestore.rules enforces deny-by-default fallback for unmapped collections", () => {
  assert.match(rulesText, /match \/\{document=\*\*\} \{\s*allow read, write: if false;\s*\}/);
});

test("firestore.rules enforces Spark Mode SP2 owner-only and verified email on ledger collections", () => {
  assert.match(rulesText, /function isVerifiedOwner\(uid\)/);
  assert.match(rulesText, /request\.auth\.token\.email_verified == true/);
  assert.match(rulesText, /match \/records\/\{recordId\}/);
  assert.match(rulesText, /isVerifiedOwner\(uid\)/);
  assert.match(rulesText, /validRecord\(\)/);
  assert.match(rulesText, /match \/people\/\{personId\}/);
  assert.match(rulesText, /match \/events\/\{eventId\}/);
  assert.match(rulesText, /allow update, delete:\s*if false;/);
  assert.match(rulesText, /match \/meta\/syncSignal/);
});

test("firestore.rules enforces Spark Mode SP3 guest capability links and proposal pattern", () => {
  assert.match(rulesText, /match \/guestLinks\/\{linkId\}/);
  assert.match(rulesText, /allow get:\s*if isSignedIn\(\)/);
  assert.match(rulesText, /validGuestLinkDoc\(resource\.data\)/);
  assert.match(rulesText, /allow list:\s*if false;/);

  // Fixed action IDs
  assert.match(rulesText, /match \/actions\/\{actionId\}/);
  assert.match(
    rulesText,
    /actionId in \['confirm', 'dispute', 'claim_1', 'claim_2', 'claim_3', 'claim_4', 'claim_5', 'date_1', 'date_2', 'date_3'\]/
  );
  assert.match(rulesText, /allow update, delete:\s*if false;/);

  // Collection group query for owner
  assert.match(rulesText, /match \/\{path=\*\*\}\/actions\/\{actionId\}/);
  assert.match(rulesText, /resource\.data\.ownerUid == request\.auth\.uid/);
  assert.match(rulesText, /request\.auth\.token\.email_verified == true/);
});

test("Spark Collections Matrix: stranger, other user, unverified email, expired/revoked link, action ID caps, no updates", async (t) => {
  const nowMs = 1_760_000_000_000;
  const stranger = null;
  const unverifiedOwner = { uid: "u_owner", token: { email_verified: false } };
  const verifiedOwner = { uid: "u_owner", token: { email_verified: true } };
  const otherVerifiedUser = { uid: "u_other", token: { email_verified: true } };
  const anonGuest = { uid: "u_anon_guest", token: { email_verified: false } };

  await t.test("1. User profile & Ledger collections (records, people, events, plans, attachments, syncSignal)", () => {
    const ev = createRulesEvaluator();
    ev.setSeedDoc("users/u_owner/records/rec_1", { ownerUid: "u_owner", rev: 1 });
    ev.setSeedDoc("users/u_owner/events/ev_1", { ownerUid: "u_owner", type: "CREATE" });

    // Profile: owner can read even before verification, but only verified owner can write; never delete
    assert.equal(ev.evaluate({ op: "get", path: "users/u_owner", auth: stranger }), false);
    assert.equal(ev.evaluate({ op: "get", path: "users/u_owner", auth: otherVerifiedUser }), false);
    assert.equal(ev.evaluate({ op: "get", path: "users/u_owner", auth: unverifiedOwner }), true);
    assert.equal(ev.evaluate({ op: "create", path: "users/u_owner", auth: unverifiedOwner, data: {} }), false);
    assert.equal(ev.evaluate({ op: "create", path: "users/u_owner", auth: verifiedOwner, data: {} }), true);
    assert.equal(ev.evaluate({ op: "delete", path: "users/u_owner", auth: verifiedOwner }), false);

    // Subcollections: stranger, other user, and unverified owner are denied across all Spark collections
    const subcollections = ["records", "people", "events", "plans", "attachments"];
    for (const sub of subcollections) {
      const docPath = `users/u_owner/${sub}/item_1`;
      const payload = { ownerUid: "u_owner", rev: 1 };
      assert.equal(ev.evaluate({ op: "get", path: docPath, auth: stranger }), false, `stranger get ${sub}`);
      assert.equal(ev.evaluate({ op: "get", path: docPath, auth: otherVerifiedUser }), false, `other user get ${sub}`);
      assert.equal(ev.evaluate({ op: "get", path: docPath, auth: unverifiedOwner }), false, `unverified get ${sub}`);
      assert.equal(ev.evaluate({ op: "create", path: docPath, auth: unverifiedOwner, data: payload }), false, `unverified create ${sub}`);
      assert.equal(ev.evaluate({ op: "create", path: docPath, auth: otherVerifiedUser, data: payload }), false, `other user create ${sub}`);
      assert.equal(ev.evaluate({ op: "create", path: docPath, auth: verifiedOwner, data: payload }), true, `verified owner create ${sub}`);
    }

    // Records monotonic revision check (rev == existing.rev + 1)
    assert.equal(
      ev.evaluate({
        op: "update",
        path: "users/u_owner/records/rec_1",
        auth: verifiedOwner,
        data: { ownerUid: "u_owner", rev: 1 },
      }),
      false,
      "Stale rev update must be rejected"
    );
    assert.equal(
      ev.evaluate({
        op: "update",
        path: "users/u_owner/records/rec_1",
        auth: verifiedOwner,
        data: { ownerUid: "u_owner", rev: 2 },
      }),
      true,
      "Incremented rev update must be allowed"
    );

    // Events are strictly append-only (no updates or deletes)
    assert.equal(
      ev.evaluate({
        op: "update",
        path: "users/u_owner/events/ev_1",
        auth: verifiedOwner,
        data: { ownerUid: "u_owner", type: "EDIT" },
      }),
      false,
      "Events must reject updates"
    );
    assert.equal(
      ev.evaluate({
        op: "delete",
        path: "users/u_owner/events/ev_1",
        auth: verifiedOwner,
      }),
      false,
      "Events must reject deletes"
    );

    // SyncSignal requires verified owner
    assert.equal(ev.evaluate({ op: "get", path: "users/u_owner/meta/syncSignal/latest", auth: unverifiedOwner }), false);
    assert.equal(ev.evaluate({ op: "create", path: "users/u_owner/meta/syncSignal/latest", auth: verifiedOwner, data: {} }), true);
  });

  await t.test("2. guestLinks/{linkId}: active vs expired vs revoked, no listing, owner-only creation", () => {
    const ev = createRulesEvaluator();
    ev.setSeedDoc("guestLinks/link_active", {
      ownerUid: "u_owner",
      recordId: "rec_1",
      status: "active",
      revoked: false,
      expiresAtMs: nowMs + 86_400_000,
    });
    ev.setSeedDoc("guestLinks/link_expired", {
      ownerUid: "u_owner",
      recordId: "rec_2",
      status: "active",
      revoked: false,
      expiresAtMs: nowMs - 1_000,
    });
    ev.setSeedDoc("guestLinks/link_revoked", {
      ownerUid: "u_owner",
      recordId: "rec_3",
      status: "revoked",
      revoked: true,
      expiresAtMs: nowMs + 86_400_000,
    });

    // Stranger (not signed in) cannot get even an active link
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_active", auth: stranger, nowMs }), false);
    // Anonymous guest can get active link, but CANNOT list guestLinks
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_active", auth: anonGuest, nowMs }), true);
    assert.equal(ev.evaluate({ op: "list", path: "guestLinks/link_active", auth: anonGuest, nowMs }), false);
    // Expired or revoked links are denied to guests
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_expired", auth: anonGuest, nowMs }), false);
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_revoked", auth: anonGuest, nowMs }), false);
    // Verified owner can still read their own expired/revoked link to manage it
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_expired", auth: verifiedOwner, nowMs }), true);
    assert.equal(ev.evaluate({ op: "get", path: "guestLinks/link_revoked", auth: verifiedOwner, nowMs }), true);
    // Unverified owner or other user cannot create a link for u_owner
    assert.equal(
      ev.evaluate({
        op: "create",
        path: "guestLinks/link_new",
        auth: unverifiedOwner,
        data: { ownerUid: "u_owner", recordId: "rec_9", status: "active", expiresAtMs: nowMs + 60_000 },
        nowMs,
      }),
      false
    );
    assert.equal(
      ev.evaluate({
        op: "create",
        path: "guestLinks/link_new",
        auth: verifiedOwner,
        data: { ownerUid: "u_owner", recordId: "rec_9", status: "active", expiresAtMs: nowMs + 60_000 },
        nowMs,
      }),
      true
    );
  });

  await t.test("3. guestLinks/{linkId}/actions/{actionId}: fixed ID caps, expired/revoked rejection, no updates/deletes, collectionGroup owner query", () => {
    const ev = createRulesEvaluator();
    ev.setSeedDoc("guestLinks/link_active", {
      ownerUid: "u_owner",
      recordId: "rec_1",
      status: "active",
      revoked: false,
      expiresAtMs: nowMs + 86_400_000,
    });
    ev.setSeedDoc("guestLinks/link_expired", {
      ownerUid: "u_owner",
      recordId: "rec_2",
      status: "active",
      revoked: false,
      expiresAtMs: nowMs - 500,
    });
    ev.setSeedDoc("guestLinks/link_revoked", {
      ownerUid: "u_owner",
      recordId: "rec_3",
      status: "revoked",
      revoked: true,
      expiresAtMs: nowMs + 86_400_000,
    });

    const validIds = [
      "confirm",
      "dispute",
      "claim_1",
      "claim_2",
      "claim_3",
      "claim_4",
      "claim_5",
      "date_1",
      "date_2",
      "date_3",
    ];
    for (const id of validIds) {
      assert.equal(
        ev.evaluate({
          op: "create",
          path: `guestLinks/link_active/actions/${id}`,
          auth: anonGuest,
          data: { linkId: "link_active", ownerUid: "u_owner", type: "CONFIRM", createdAtMs: nowMs },
          nowMs,
        }),
        true,
        `Valid fixed actionId '${id}' must be permitted`
      );
    }

    // Out-of-bounds action IDs (claim_6, date_4, random) must be rejected
    for (const badId of ["claim_6", "claim_0", "date_4", "date_0", "arbitrary_id"]) {
      assert.equal(
        ev.evaluate({
          op: "create",
          path: `guestLinks/link_active/actions/${badId}`,
          auth: anonGuest,
          data: { linkId: "link_active", ownerUid: "u_owner", type: "CLAIM_PAYMENT", createdAtMs: nowMs },
          nowMs,
        }),
        false,
        `Capped/invalid actionId '${badId}' must be rejected`
      );
    }

    // Creating an action on an expired or revoked link must be rejected
    assert.equal(
      ev.evaluate({
        op: "create",
        path: "guestLinks/link_expired/actions/confirm",
        auth: anonGuest,
        data: { linkId: "link_expired", ownerUid: "u_owner", type: "CONFIRM", createdAtMs: nowMs },
        nowMs,
      }),
      false,
      "Action on expired link must be rejected"
    );
    assert.equal(
      ev.evaluate({
        op: "create",
        path: "guestLinks/link_revoked/actions/confirm",
        auth: anonGuest,
        data: { linkId: "link_revoked", ownerUid: "u_owner", type: "CONFIRM", createdAtMs: nowMs },
        nowMs,
      }),
      false,
      "Action on revoked link must be rejected"
    );

    // Seed an existing action and verify no overwrites/updates/deletes
    ev.setSeedDoc("guestLinks/link_active/actions/confirm", {
      linkId: "link_active",
      ownerUid: "u_owner",
      type: "CONFIRM",
      createdAtMs: nowMs,
    });
    assert.equal(
      ev.evaluate({
        op: "create",
        path: "guestLinks/link_active/actions/confirm",
        auth: anonGuest,
        data: { linkId: "link_active", ownerUid: "u_owner", type: "CONFIRM", createdAtMs: nowMs },
        nowMs,
      }),
      false,
      "Recreating existing fixed action doc must be rejected"
    );
    assert.equal(
      ev.evaluate({
        op: "update",
        path: "guestLinks/link_active/actions/confirm",
        auth: anonGuest,
        data: { linkId: "link_active", ownerUid: "u_owner", type: "DISPUTE", createdAtMs: nowMs },
        nowMs,
      }),
      false,
      "Updating an existing action must be rejected"
    );
    assert.equal(
      ev.evaluate({
        op: "delete",
        path: "guestLinks/link_active/actions/confirm",
        auth: verifiedOwner,
        nowMs,
      }),
      false,
      "Deleting an action must be rejected"
    );

    // Collection group query on actions: only verified ownerUid can read
    assert.equal(
      ev.evaluate({
        op: "get",
        path: "collectionGroup:actions/guestLinks/link_active/actions/confirm",
        auth: verifiedOwner,
        nowMs,
      }),
      true
    );
    assert.equal(
      ev.evaluate({
        op: "get",
        path: "collectionGroup:actions/guestLinks/link_active/actions/confirm",
        auth: unverifiedOwner,
        nowMs,
      }),
      false
    );
    assert.equal(
      ev.evaluate({
        op: "get",
        path: "collectionGroup:actions/guestLinks/link_active/actions/confirm",
        auth: otherVerifiedUser,
        nowMs,
      }),
      false
    );
  });

  if (process.env.FIRESTORE_EMULATOR_HOST) {
    await t.test("4. Live Firestore Emulator REST verification of Spark rules", async () => {
      const verifiedOwnerJwt = makeEmulatorJwt({ uid: "emu_owner", emailVerified: true });
      const unverifiedOwnerJwt = makeEmulatorJwt({ uid: "emu_owner", emailVerified: false });
      const otherUserJwt = makeEmulatorJwt({ uid: "emu_other", emailVerified: true });
      const anonGuestJwt = makeEmulatorJwt({ uid: "emu_guest", emailVerified: false, isAnonymous: true });

      // Stranger denied on users/emu_owner/records/r1
      const strangerRes = await emulatorRequest({
        method: "GET",
        docPath: "users/emu_owner/records/r1",
        token: null,
      });
      assert.equal(strangerRes.status, 403, "Stranger must receive 403 on records");

      // Unverified email denied creating record
      const unverifiedRes = await emulatorRequest({
        method: "PATCH",
        docPath: "users/emu_owner/records/r1",
        token: unverifiedOwnerJwt,
        body: toFirestoreFields({ ownerUid: "emu_owner", rev: 1, updatedAtMs: Date.now() }),
      });
      assert.equal(unverifiedRes.status, 403, "Unverified owner must receive 403 creating record");

      // Verified owner creates record
      const verifiedRes = await emulatorRequest({
        method: "PATCH",
        docPath: "users/emu_owner/records/r1",
        token: verifiedOwnerJwt,
        body: toFirestoreFields({ ownerUid: "emu_owner", rev: 1, updatedAtMs: Date.now() }),
      });
      assert.equal(verifiedRes.status, 200, "Verified owner must succeed creating record");

      // Other verified user denied reading emu_owner's record
      const otherReadRes = await emulatorRequest({
        method: "GET",
        docPath: "users/emu_owner/records/r1",
        token: otherUserJwt,
      });
      assert.equal(otherReadRes.status, 403, "Other user must receive 403 reading owner's record");

      // Verified owner creates active guestLink and expired guestLink
      const futureMs = Date.now() + 3_600_000;
      const pastMs = Date.now() - 3_600_000;
      const createActiveLink = await emulatorRequest({
        method: "PATCH",
        docPath: "guestLinks/emu_link_active",
        token: verifiedOwnerJwt,
        body: toFirestoreFields({
          ownerUid: "emu_owner",
          recordId: "r1",
          status: "active",
          revoked: false,
          expiresAtMs: futureMs,
        }),
      });
      assert.equal(createActiveLink.status, 200, "Verified owner must create active guestLink");

      const createExpiredLink = await emulatorRequest({
        method: "PATCH",
        docPath: "guestLinks/emu_link_expired",
        token: verifiedOwnerJwt,
        body: toFirestoreFields({
          ownerUid: "emu_owner",
          recordId: "r2",
          status: "active",
          revoked: false,
          expiresAtMs: pastMs,
        }),
      });
      assert.equal(createExpiredLink.status, 200, "Verified owner must create expired guestLink");

      // Anonymous guest can GET active link, but fails on expired link
      const guestGetActive = await emulatorRequest({
        method: "GET",
        docPath: "guestLinks/emu_link_active",
        token: anonGuestJwt,
      });
      assert.equal(guestGetActive.status, 200, "Anon guest must GET active guestLink");

      const guestGetExpired = await emulatorRequest({
        method: "GET",
        docPath: "guestLinks/emu_link_expired",
        token: anonGuestJwt,
      });
      assert.equal(guestGetExpired.status, 403, "Anon guest must be denied GET on expired guestLink");

      // Anonymous guest creates fixed action 'confirm' on active link -> 200
      const guestConfirm = await emulatorRequest({
        method: "POST",
        docPath: "guestLinks/emu_link_active/actions?documentId=confirm",
        token: anonGuestJwt,
        body: toFirestoreFields({
          linkId: "emu_link_active",
          ownerUid: "emu_owner",
          type: "CONFIRM",
          createdAtMs: Date.now(),
        }),
      });
      assert.equal(guestConfirm.status, 200, "Anon guest must create 'confirm' action");

      // Anonymous guest cannot overwrite/update 'confirm' -> 403
      const guestUpdateConfirm = await emulatorRequest({
        method: "PATCH",
        docPath: "guestLinks/emu_link_active/actions/confirm",
        token: anonGuestJwt,
        body: toFirestoreFields({
          linkId: "emu_link_active",
          ownerUid: "emu_owner",
          type: "DISPUTE",
          createdAtMs: Date.now(),
        }),
      });
      assert.equal(guestUpdateConfirm.status, 403, "Anon guest must not update existing action");

      // Anonymous guest cannot create out-of-cap 'claim_6' -> 403
      const guestClaim6 = await emulatorRequest({
        method: "POST",
        docPath: "guestLinks/emu_link_active/actions?documentId=claim_6",
        token: anonGuestJwt,
        body: toFirestoreFields({
          linkId: "emu_link_active",
          ownerUid: "emu_owner",
          type: "CLAIM_PAYMENT",
          createdAtMs: Date.now(),
        }),
      });
      assert.equal(guestClaim6.status, 403, "Anon guest must be denied out-of-cap 'claim_6'");
    });
  }
});

test("storage.rules sets allow create: if false for clients so uploads happen only via signed URLs or Admin SDK", () => {
  assert.match(storageRulesText, /match \/payment_proofs\/\{recordId\}\/\{fileName\}/);
  assert.match(storageRulesText, /match \/guest_proofs\/\{recordId\}\/\{fileName\}/);
  const createMatches = [...storageRulesText.matchAll(/allow\s+create\s*:\s*if\s+([^;]+);/g)];
  assert.ok(createMatches.length >= 2, "Expected allow create rules for payment_proofs and guest_proofs");
  for (const match of createMatches) {
    assert.equal(match[1].trim(), "false", `Expected allow create to be false, got: ${match[0]}`);
  }
  assert.match(storageRulesText, /allow update, delete:\s*if false/);
});

test("storage.lifecycle.json configures a 90-day Delete lifecycle rule for proof uploads", () => {
  assert.ok(storageLifecycleJson.lifecycle && Array.isArray(storageLifecycleJson.lifecycle.rule));
  const deleteRule = storageLifecycleJson.lifecycle.rule.find(
    (r) => r.action && r.action.type === "Delete"
  );
  assert.ok(deleteRule, "Expected a Delete lifecycle rule");
  assert.equal(deleteRule.condition.age, 90);
  assert.deepEqual(deleteRule.condition.matchesPrefix, ["guest_proofs/", "payment_proofs/"]);
});
