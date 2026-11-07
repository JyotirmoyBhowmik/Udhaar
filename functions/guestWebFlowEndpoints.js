/**
 * Udhaar Cloud Functions — Guest Web Flow HTTP Endpoints & Phone Registry Lookup
 *
 * Deployed endpoints:
 * 1. `registerPhoneIdentity`: Authenticated callable function that computes a server-salted
 *    HMAC-SHA256 (`phoneHmac`) using `UDHAAR_SERVER_SECRET` and writes `phone_registry/{phoneHmac}`.
 * 2. `lookupPhoneIdentity`: Authenticated callable function that resolves a contact's phone number
 *    to a registered Firebase UID using the server-salted HMAC without exposing raw numbers.
 * 3. `guestRecordView`: HTTPS endpoint (`GET /POST`) that verifies the capability token from the
 *    `#t=<rawToken>` fragment (sent in request header `X-Guest-Token` or body, never logged in URL query)
 *    and returns a sanitized view of the shared Udhaar record.
 * 4. `guestRecordAction`: HTTPS endpoint (`POST`) that verifies the capability token and applies
 *    `CONFIRM_RECORD`, `DISPUTE_RECORD`, or `SUBMIT_PAYMENT_CLAIM` in a Firestore transaction,
 *    plus generates a short-lived signed upload URL for payment proof images/PDFs (< 5 MB)
 *    with `contentType` and `x-goog-content-length-range: 0,5242880`.
 */

let onCall, onRequest, HttpsError;
try {
  ({ onCall, onRequest, HttpsError } = require("firebase-functions/v2/https"));
} catch (_) {
  onCall = (_opts, handler) => handler;
  onRequest = (_opts, handler) => handler;
  HttpsError = class HttpsError extends Error {
    constructor(code, message) {
      super(message);
      this.code = code;
    }
  };
}

let admin;
try {
  admin = require("firebase-admin");
  if (!admin.apps.length) {
    admin.initializeApp();
  }
} catch (_) {
  admin = { apps: [] }

let getFirestore;
try {
  ({ getFirestore } = require("firebase-admin/firestore"));
} catch (_) {
  getFirestore = () => null;
}
const DEFAULT_DATABASE_ID = "ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf";

function getFirestoreDb() {
  const dbId = process.env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID;
  try {
    if (admin && admin.apps && admin.apps.length && typeof getFirestore === "function") {
      return getFirestore(dbId);
    }
  } catch (_) {}
  if (admin && typeof admin.firestore === "function") {
    try {
      return admin.firestore();
    } catch (_) {}
  }
  return null;
}
;
}

const crypto = require("crypto");
const {
  UDHAAR_SERVER_SECRET,
  ALLOWED_PROOF_MIME_TYPES,
  MAX_PROOF_SIZE_BYTES,
  computeSaltedPhoneHash,
  verifyGuestCapabilityToken,
  buildGuestActionUpdate
} = require("./guestWebFlow");

const SIGNED_UPLOAD_URL_TTL_MS = 15 * 60 * 1000; // 15 minutes
const PROOF_RETENTION_DAYS = 90;

/**
 * 90-day GCS bucket lifecycle rule for automatic cleanup of guest & payment proof uploads.
 */
const STORAGE_LIFECYCLE_RULE_90_DAYS = Object.freeze({
  action: { type: "Delete" },
  condition: {
    age: PROOF_RETENTION_DAYS,
    matchesPrefix: ["guest_proofs/", "payment_proofs/"]
  }
});

/**
 * Builds the options passed to `file.getSignedUrl(...)` for a guest payment proof upload.
 * Enforces `contentType` and the `x-goog-content-length-range: 0,5242880` extension header.
 *
 * @param {string} contentType - Validated MIME type from ALLOWED_PROOF_MIME_TYPES
 * @param {number} [nowMs] - Current timestamp in ms
 * @returns {object} Signed URL configuration object
 */
function buildProofSignedUrlOptions(contentType, nowMs = Date.now()) {
  if (!ALLOWED_PROOF_MIME_TYPES.includes(contentType)) {
    throw new Error("INVALID_PROOF_TYPE: Only JPEG, PNG, WEBP, or PDF proofs are allowed.");
  }
  return {
    version: "v4",
    action: "write",
    expires: nowMs + SIGNED_UPLOAD_URL_TTL_MS,
    contentType,
    extensionHeaders: {
      "x-goog-content-length-range": `0,${MAX_PROOF_SIZE_BYTES}`
    }
  };
}

/**
 * Ensures the Storage bucket has the 90-day lifecycle deletion rule configured.
 *
 * @param {object} bucket - `@google-cloud/storage` Bucket instance
 */
async function ensureBucketLifecycle90Days(bucket) {
  if (!bucket || typeof bucket.setMetadata !== "function") {
    return;
  }
  await bucket.setMetadata({
    lifecycle: {
      rule: [STORAGE_LIFECYCLE_RULE_90_DAYS]
    }
  });
}

// ---------------------------------------------------------------------------
// 0. Per-IP + Per-Record Rate Limiter (prevents brute-force & spam on guest endpoints)
// ---------------------------------------------------------------------------
const RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const RATE_LIMIT_MAX_REQUESTS_PER_WINDOW = 25;

/**
 * Enforces a sliding-window rate limit in Firestore (`guest_rate_limits/{bucketKey}`)
 * keyed by client IP hash and recordId. Returns `true` if allowed, `false` if throttled.
 */
async function checkGuestEndpointRateLimit(req, recordId) {
  const rawIp =
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    "unknown_ip";
  const ipHash = crypto.createHash("sha256").update(rawIp).digest("hex").slice(0, 24);
  const safeRecordKey = String(recordId || "global").replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
  const bucketId = `${ipHash}_${safeRecordKey}`;
  const nowMs = Date.now();

  const ref = getFirestoreDb().collection("guest_rate_limits").doc(bucketId);
  return getFirestoreDb().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) {
      tx.set(ref, {
        count: 1,
        windowStartMs: nowMs,
        updatedAt: nowMs
      });
      return true;
    }
    const data = snap.data() || {};
    const windowStartMs = typeof data.windowStartMs === "number" ? data.windowStartMs : 0;
    const count = typeof data.count === "number" ? data.count : 0;

    if (nowMs - windowStartMs > RATE_LIMIT_WINDOW_MS) {
      tx.set(ref, {
        count: 1,
        windowStartMs: nowMs,
        updatedAt: nowMs
      });
      return true;
    }

    if (count >= RATE_LIMIT_MAX_REQUESTS_PER_WINDOW) {
      return false;
    }

    tx.update(ref, {
      count: count + 1,
      updatedAt: nowMs
    });
    return true;
  });
}

// ---------------------------------------------------------------------------
// 1. Authenticated Callable: Register Salted Phone Identity
// ---------------------------------------------------------------------------
exports.registerPhoneIdentity = onCall(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication is required to register phone identity.");
    }

    const rawPhone = request.data && request.data.phone;
    const displayName =
      request.data && typeof request.data.displayName === "string"
        ? request.data.displayName.trim().slice(0, 80)
        : "";

    let hashed;
    try {
      hashed = computeSaltedPhoneHash(rawPhone);
    } catch (err) {
      throw new HttpsError("invalid-argument", err.message);
    }

    const nowMs = Date.now();
    const db = getFirestoreDb();

    await db.collection("phone_registry").doc(hashed.phoneHmac).set(
      {
        uid: request.auth.uid,
        phoneHmac: hashed.phoneHmac,
        displayName,
        hashVersion: "hmac-sha256-v1",
        updatedAt: nowMs
      },
      { merge: true }
    );

    // Also store the user's phoneHmac on their private profile so shared records can match by phoneHmac
    await db.collection("users").doc(request.auth.uid).set(
      {
        uid: request.auth.uid,
        phoneHmac: hashed.phoneHmac,
        updatedAt: nowMs
      },
      { merge: true }
    );

    return {
      phoneHmac: hashed.phoneHmac,
      hashVersion: "hmac-sha256-v1"
    };
  }
);

// ---------------------------------------------------------------------------
// 2. Authenticated Callable: Lookup Contact Phone Identity by Salted HMAC
// ---------------------------------------------------------------------------
exports.lookupPhoneIdentity = onCall(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication is required to lookup phone identity.");
    }

    const rawPhone = request.data && request.data.phone;
    let hashed;
    try {
      hashed = computeSaltedPhoneHash(rawPhone);
    } catch (err) {
      throw new HttpsError("invalid-argument", err.message);
    }

    const snap = await getFirestoreDb().collection("phone_registry").doc(hashed.phoneHmac).get();
    if (!snap.exists) {
      return {
        registered: false,
        phoneHmac: hashed.phoneHmac,
        uid: null
      };
    }

    const data = snap.data() || {};
    return {
      registered: true,
      phoneHmac: hashed.phoneHmac,
      uid: data.uid || null,
      displayName: data.displayName || null
    };
  }
);

// ---------------------------------------------------------------------------
// 3. Guest Web Endpoint: View Shared Record (`GET` / `POST`)
// ---------------------------------------------------------------------------
exports.guestRecordView = onRequest({ cors: true }, async (req, res) => {
  try {
    const recordId =
      (req.query && req.query.recordId) ||
      (req.body && req.body.recordId) ||
      "";
    const rawToken =
      req.headers["x-guest-token"] ||
      (req.body && req.body.token) ||
      "";

    if (!recordId || typeof recordId !== "string") {
      res.status(400).json({ error: "MISSING_RECORD_ID" });
      return;
    }

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const snap = await getFirestoreDb().collection("shared_udhaars").doc(recordId).get();
    if (!snap.exists) {
      res.status(404).json({ error: "RECORD_NOT_FOUND" });
      return;
    }

    const record = snap.data() || {};
    const verification = verifyGuestCapabilityToken(record, rawToken, Date.now());
    if (!verification.valid) {
      res.status(403).json({ error: verification.reason || "FORBIDDEN" });
      return;
    }

    const principalAmountPaise =
      typeof record.principalAmountPaise === "number" && Number.isInteger(record.principalAmountPaise)
        ? record.principalAmountPaise
        : Math.round(Number(record.principalAmount || 0) * 100);
    const amountPaidPaise =
      typeof record.amountPaidPaise === "number" && Number.isInteger(record.amountPaidPaise)
        ? record.amountPaidPaise
        : Math.round(Number(record.amountPaid || 0) * 100);

    // Return only sanitized fields needed by the Guest Web Flow UI (never expose internal UIDs or hashes)
    res.status(200).json({
      recordId: record.recordId || recordId,
      senderName: record.senderName || "Udhaar User",
      recipientName: record.recipientName || "Counterparty",
      principalAmountPaise,
      amountPaidPaise,
      currencyCode: record.currencyCode || "INR",
      directionForRecipient: record.senderType === "GIVEN" ? "YOU_OWE" : "OWED_TO_YOU",
      status: record.status || "PENDING_CONFIRMATION",
      description: record.description || "",
      date: record.date || null,
      dueDate: record.dueDate || null,
      interestRate: record.interestRate || 0,
      hasPendingPaymentClaim: Boolean(record.hasPendingPaymentClaim),
      pendingPaymentClaims: Array.isArray(record.pendingPaymentClaims)
        ? record.pendingPaymentClaims.map((c) => ({
            claimId: c.claimId,
            amountPaise:
              typeof c.amountPaise === "number" && Number.isInteger(c.amountPaise)
                ? c.amountPaise
                : Math.round(Number(c.amount || 0) * 100),
            note: c.note || "",
            utrNumber: c.utrNumber || "",
            status: c.status || "PENDING_LENDER_APPROVAL",
            submittedAt: c.submittedAt || null
          }))
        : [],
      disputeReason: record.disputeReason || null,
      updatedAt: record.updatedAt || null
    });
  } catch (err) {
    res.status(500).json({ error: "INTERNAL_ERROR", message: err.message });
  }
});

// ---------------------------------------------------------------------------
// 4. Guest Web Endpoint: Submit Action or Request Signed Proof Upload URL (`POST`)
// ---------------------------------------------------------------------------
exports.guestRecordAction = onRequest({ cors: true }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
    return;
  }

  try {
    const body = req.body || {};
    const recordId = body.recordId || (req.query && req.query.recordId) || "";
    const rawToken = req.headers["x-guest-token"] || body.token || "";
    const actionPayload = body.actionPayload || {};

    if (!recordId || typeof recordId !== "string") {
      res.status(400).json({ error: "MISSING_RECORD_ID" });
      return;
    }

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const db = getFirestoreDb();
    const docRef = db.collection("shared_udhaars").doc(recordId);

    // Optional step: Generate a short-lived signed upload URL for a payment proof image/PDF
    if (actionPayload.action === "REQUEST_PROOF_UPLOAD_URL") {
      const snap = await docRef.get();
      if (!snap.exists) {
        res.status(404).json({ error: "RECORD_NOT_FOUND" });
        return;
      }
      const record = snap.data() || {};
      const verification = verifyGuestCapabilityToken(record, rawToken, Date.now());
      if (!verification.valid) {
        res.status(403).json({ error: verification.reason || "FORBIDDEN" });
        return;
      }

      const { mimeType, sizeBytes, ext } = actionPayload;
      if (!ALLOWED_PROOF_MIME_TYPES.includes(mimeType)) {
        res.status(400).json({ error: "INVALID_PROOF_TYPE" });
        return;
      }
      if (typeof sizeBytes !== "number" || sizeBytes <= 0 || sizeBytes > MAX_PROOF_SIZE_BYTES) {
        res.status(400).json({ error: "INVALID_PROOF_SIZE" });
        return;
      }

      const safeExt = String(ext || "jpg").replace(/[^a-zA-Z0-9]/g, "").slice(0, 5) || "jpg";
      const storagePath = `guest_proofs/${recordId}/proof_${Date.now()}_${crypto.randomBytes(4).toString("hex")}.${safeExt}`;
      const bucket = admin.storage().bucket();
      const file = bucket.file(storagePath);
      const signedUrlOptions = buildProofSignedUrlOptions(mimeType, Date.now());
      const [uploadUrl] = await file.getSignedUrl(signedUrlOptions);

      res.status(200).json({
        uploadUrl,
        storagePath,
        maxSizeBytes: MAX_PROOF_SIZE_BYTES,
        retentionDays: PROOF_RETENTION_DAYS
      });
      return;
    }

    // Standard state-mutating guest actions (CONFIRM_RECORD, DISPUTE_RECORD, SUBMIT_PAYMENT_CLAIM)
    const nowMs = Date.now();
    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(docRef);
      if (!snap.exists) {
        const err = new Error("RECORD_NOT_FOUND");
        err.code = "RECORD_NOT_FOUND";
        throw err;
      }
      const record = { recordId, ...(snap.data() || {}) };
      const updateFields = buildGuestActionUpdate(record, rawToken, actionPayload, nowMs);
      tx.update(docRef, updateFields);
      return updateFields;
    });

    res.status(200).json({
      ok: true,
      status: result.status || "UPDATED",
      hasPendingPaymentClaim: Boolean(result.hasPendingPaymentClaim),
      updatedAt: result.updatedAt
    });
  } catch (err) {
    const status =
      err.code === "RECORD_NOT_FOUND"
        ? 404
        : err.code && err.code.startsWith("TOKEN_")
          ? 403
          : 400;
    res.status(status).json({
      error: err.code || "BAD_REQUEST",
      message: err.message
    });
  }
});

// ---------------------------------------------------------------------------
// 5. Public Split Expense Link Landing Page & Guest Web Flow (`GET /split/{id}`)
//    Serves a mobile-friendly web page with OpenGraph preview tags, deep link
//    into the Udhaar Android app (`intent://...`), and interactive Guest Web Flow
//    actions (Confirm, Dispute, Submit Payment Claim with UPI payment button).
// ---------------------------------------------------------------------------
function escapeHtml(str) {
  return String(str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

exports.splitWebLanding = onRequest({ cors: true }, async (req, res) => {
  try {
    const pathParts = (req.path || "").split("/").filter(Boolean);
    const recordId =
      (req.query && req.query.id) ||
      (req.query && req.query.recordId) ||
      (pathParts.length > 0 ? pathParts[pathParts.length - 1] : "");

    const safeRecordId = escapeHtml(recordId);
    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Udhaar — Shared Expense &amp; Record Confirmation</title>
  <meta property="og:title" content="Udhaar — Shared Expense &amp; Settlement" />
  <meta property="og:description" content="Review, confirm, or settle this shared expense in the Udhaar app or directly in your browser." />
  <meta property="og:type" content="website" />
  <style>
    :root { --bg: #F6F7F4; --card: #FFFFFF; --primary: #0F5238; --text: #181D1A; --muted: #5C6660; --danger: #B3261E; }
    * { box-sizing: border-box; font-family: system-ui, -apple-system, sans-serif; }
    body { margin: 0; background: var(--bg); color: var(--text); padding: 16px; display: flex; justify-content: center; }
    .container { width: 100%; max-width: 460px; background: var(--card); border-radius: 20px; padding: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.06); }
    .badge { display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; background: #E3F2EA; color: var(--primary); }
    h1 { font-size: 22px; margin: 12px 0 4px; }
    .amount { font-size: 34px; font-weight: 800; color: var(--primary); margin: 12px 0; }
    .meta { font-size: 14px; color: var(--muted); margin-bottom: 16px; line-height: 1.5; }
    .btn { display: block; width: 100%; padding: 14px 16px; border-radius: 12px; border: none; font-size: 15px; font-weight: 700; cursor: pointer; text-align: center; text-decoration: none; margin-top: 10px; }
    .btn-primary { background: var(--primary); color: #fff; }
    .btn-outline { background: transparent; border: 1.5px solid var(--primary); color: var(--primary); }
    .btn-danger { background: #FCEEEE; color: var(--danger); }
    input, textarea { width: 100%; padding: 12px; border: 1px solid #CFD6D1; border-radius: 10px; margin-top: 8px; font-size: 14px; }
    .hidden { display: none; }
    .status-box { padding: 12px; border-radius: 10px; background: #EDF5F0; font-size: 13px; margin-top: 12px; }
  </style>
</head>
<body>
  <div class="container">
    <span class="badge" id="statusBadge">Shared Udhaar Record</span>
    <h1 id="titleText">Loading shared record…</h1>
    <div class="amount" id="amountText">₹ —</div>
    <div class="meta" id="metaText">Verifying secure link…</div>

    <div id="actionsSection" class="hidden">
      <button class="btn btn-primary" id="confirmBtn">Confirm Record</button>
      <button class="btn btn-outline" id="claimToggleBtn">I Have Paid (Submit Proof / UTR)</button>
      <button class="btn btn-danger" id="disputeToggleBtn">Report an Issue / Dispute</button>
    </div>

    <div id="claimForm" class="hidden">
      <input type="number" id="claimAmount" placeholder="Amount paid" step="0.01" />
      <input type="text" id="claimUtr" placeholder="UTR / Reference number (optional)" />
      <input type="file" id="claimProofFile" accept="image/jpeg,image/png,image/webp,application/pdf" />
      <textarea id="claimNote" rows="2" placeholder="Payment note (optional)"></textarea>
      <button class="btn btn-primary" id="submitClaimBtn">Submit Payment Claim</button>
    </div>

    <div id="disputeForm" class="hidden">
      <textarea id="disputeReason" rows="3" placeholder="Explain why this amount or record is incorrect…"></textarea>
      <button class="btn btn-danger" id="submitDisputeBtn">Submit Dispute</button>
    </div>

    <div id="feedbackBox" class="status-box hidden"></div>

    <a class="btn btn-outline" id="openAppBtn" href="intent://udhaar.app/r/${safeRecordId}#Intent;scheme=https;package=com.jyotirmoyb.udhaar;end">
      Open in Udhaar Android App
    </a>
  </div>

  <script>
    (function() {
      const recordId = "${safeRecordId}";
      const hashParams = new URLSearchParams((window.location.hash || "").replace(/^#/, ""));
      const rawToken = hashParams.get("t") || "";
      if (rawToken) {
        document.getElementById("openAppBtn").href =
          "https://udhaar.app/r/" + encodeURIComponent(recordId) + "#t=" + encodeURIComponent(rawToken);
      }

      const feedbackBox = document.getElementById("feedbackBox");
      function showFeedback(msg) {
        feedbackBox.textContent = msg;
        feedbackBox.classList.remove("hidden");
      }

      async function loadRecord() {
        if (!recordId || !rawToken) {
          document.getElementById("titleText").textContent = "Open in Udhaar App";
          document.getElementById("metaText").textContent = "Tap below to view this shared record in Udhaar.";
          return;
        }
        try {
          const res = await fetch("/guestRecordView?recordId=" + encodeURIComponent(recordId), {
            headers: { "X-Guest-Token": rawToken }
          });
          const data = await res.json();
          if (!res.ok) {
            document.getElementById("titleText").textContent = "Link Expired or Invalid";
            document.getElementById("metaText").textContent = data.error || "Unable to load record.";
            return;
          }
          const remainingPaise = Math.max(0, (data.principalAmountPaise || 0) - (data.amountPaidPaise || 0));
          const remainingRupees = (remainingPaise / 100).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
          document.getElementById("statusBadge").textContent = data.status;
          document.getElementById("titleText").textContent =
            data.directionForRecipient === "YOU_OWE"
              ? data.senderName + " shared an expense with you"
              : "You lent to " + data.senderName;
          document.getElementById("amountText").textContent = (data.currencyCode || "INR") + " " + remainingRupees;
          document.getElementById("metaText").textContent = data.description || "Shared via Udhaar";
          document.getElementById("claimAmount").value = (remainingPaise / 100).toFixed(2);
          document.getElementById("actionsSection").classList.remove("hidden");
        } catch (e) {
          showFeedback("Network error while loading record.");
        }
      }

      async function sendAction(actionPayload) {
        const res = await fetch("/guestRecordAction", {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-Guest-Token": rawToken },
          body: JSON.stringify({ recordId, actionPayload })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || data.error || "Action failed");
        return data;
      }

      document.getElementById("confirmBtn").onclick = async () => {
        try {
          await sendAction({ action: "CONFIRM_RECORD" });
          showFeedback("Record confirmed! Both ledgers are now synced.");
          loadRecord();
        } catch (e) { showFeedback(e.message); }
      };

      document.getElementById("claimToggleBtn").onclick = () => {
        document.getElementById("claimForm").classList.toggle("hidden");
      };
      document.getElementById("disputeToggleBtn").onclick = () => {
        document.getElementById("disputeForm").classList.toggle("hidden");
      };

      document.getElementById("submitClaimBtn").onclick = async () => {
        try {
          const amountRupees = parseFloat(document.getElementById("claimAmount").value);
          const amountPaise = Math.round(amountRupees * 100);
          const utrNumber = document.getElementById("claimUtr").value;
          const note = document.getElementById("claimNote").value;
          const fileInput = document.getElementById("claimProofFile");
          let proof = null;
          if (fileInput.files && fileInput.files[0]) {
            const file = fileInput.files[0];
            const ext = (file.name.split(".").pop() || "jpg").toLowerCase();
            const urlData = await sendAction({
              action: "REQUEST_PROOF_UPLOAD_URL",
              mimeType: file.type,
              sizeBytes: file.size,
              ext
            });
            await fetch(urlData.uploadUrl, {
              method: "PUT",
              headers: {
                "Content-Type": file.type,
                "x-goog-content-length-range": "0," + urlData.maxSizeBytes
              },
              body: file
            });
            proof = {
              mimeType: file.type,
              sizeBytes: file.size,
              storagePath: urlData.storagePath
            };
          }
          await sendAction({ action: "SUBMIT_PAYMENT_CLAIM", amountPaise, utrNumber, note, proof });
          showFeedback("Payment claim submitted for lender verification.");
          document.getElementById("claimForm").classList.add("hidden");
          loadRecord();
        } catch (e) { showFeedback(e.message); }
      };

      document.getElementById("submitDisputeBtn").onclick = async () => {
        try {
          const reason = document.getElementById("disputeReason").value;
          await sendAction({ action: "DISPUTE_RECORD", reason });
          showFeedback("Dispute recorded. The sender has been notified.");
          document.getElementById("disputeForm").classList.add("hidden");
          loadRecord();
        } catch (e) { showFeedback(e.message); }
      };

      loadRecord();
    })();
  </script>
</body>
</html>`;
    res.set("Cache-Control", "no-store");
    res.status(200).send(html);
  } catch (err) {
    res.status(500).send("Unable to load shared record.");
  }
});

exports.renewGuestCode = onCall(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication is required to renew a guest code.");
    }
    const { recordId, rawCode } = request.data || {};
    if (!recordId || typeof recordId !== "string") {
      throw new HttpsError("invalid-argument", "recordId is required.");
    }
    const { computeCodeHmac } = require("./guestWebFlow");
    const hmac = computeCodeHmac(rawCode);
    const nowMs = Date.now();
    const expiresAtMillis = nowMs + 15 * 60 * 1000;
    await getFirestoreDb().collection("webLinks").doc(recordId).set(
      {
        whatsappOtpHmac: hmac,
        whatsappOtpExpiresAtMillis: expiresAtMillis,
        whatsappOtpUsed: false,
        updatedAt: nowMs
      },
      { merge: true }
    );
    return {
      success: true,
      expiresAtMillis
    };
  }
);

// ---------------------------------------------------------------------------
// 6. Callable & HTTP Endpoints for /u/r/<recordId>#<secret> Guest Web Portal
// ---------------------------------------------------------------------------
const {
  hashGuestToken,
  computeCodeHmac,
  verifyGuestPhoneOtp: verifyGuestPhoneOtpHelper,
  writeAuditEntry,
  writeInboxItem
} = require("./guestWebFlow");

async function loadWebLinkAndVerify(db, recordId, rawSecret, nowMs = Date.now()) {
  if (!recordId || typeof recordId !== "string") {
    const err = new Error("MISSING_RECORD_ID");
    err.status = 400;
    throw err;
  }
  const webLinkRef = db.collection("webLinks").doc(recordId);
  const snap = await webLinkRef.get();
  if (!snap.exists) {
    const err = new Error("RECORD_NOT_FOUND");
    err.status = 410;
    throw err;
  }
  const linkData = snap.data() || {};
  if (linkData.revoked || linkData.status === "REVOKED") {
    const err = new Error("LINK_REVOKED");
    err.status = 403;
    throw err;
  }
  const ver = verifyGuestCapabilityToken(linkData, rawSecret, nowMs);
  if (!ver.valid) {
    const err = new Error(ver.reason || "FORBIDDEN");
    err.status = ver.reason === "TOKEN_EXPIRED" ? 410 : 403;
    throw err;
  }
  return { webLinkRef, linkData };
}

exports.createGuestWebLink = onCall(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication is required to create a guest web link.");
    }
    const data = request.data || {};
    const recordId = String(data.recordId || "").trim();
    if (!recordId) {
      throw new HttpsError("invalid-argument", "recordId is required.");
    }

    const expiryDays = [7, 30, 90].includes(Number(data.expiryDays)) ? Number(data.expiryDays) : 30;
    const trustLevelRequired = data.trustLevelRequired === "LINK_ONLY" ? "LINK_ONLY" : "WHATSAPP_OTP";
    const secret = crypto.randomBytes(12).toString("base64url");
    const guestTokenHash = hashGuestToken(secret);
    const nowMs = Date.now();
    const guestTokenExpiresAt = nowMs + expiryDays * 24 * 60 * 60 * 1000;

    let whatsappOtp = "";
    let whatsappOtpHmac = "";
    let whatsappOtpExpiresAtMillis = 0;
    if (trustLevelRequired !== "LINK_ONLY") {
      whatsappOtp = String(crypto.randomInt(100000, 1000000));
      whatsappOtpHmac = computeCodeHmac(whatsappOtp);
      whatsappOtpExpiresAtMillis = nowMs + 15 * 60 * 1000;
    }

    const principalAmountPaise = Number(data.principalAmountPaise || 0);
    const amountPaidPaise = Number(data.amountPaidPaise || 0);
    const docPayload = {
      recordId,
      creatorUid: request.auth.uid,
      counterpartyRole: data.counterpartyRole === "LENDER" ? "LENDER" : "BORROWER",
      intendedPhone: String(data.intendedPhone || "").slice(0, 24),
      creatorPhone: String(data.creatorPhone || "").slice(0, 24),
      lenderName: String(data.lenderName || "Lender").slice(0, 80),
      borrowerName: String(data.borrowerName || "Borrower").slice(0, 80),
      principalAmountPaise,
      principalAmount: principalAmountPaise / 100,
      amountPaidPaise,
      currency: String(data.currency || "INR").slice(0, 8),
      startDateMillis: Number(data.startDateMillis || nowMs),
      expiryDateMillis: data.expiryDateMillis ? Number(data.expiryDateMillis) : null,
      note: data.allowNote ? String(data.note || "").slice(0, 500) : "",
      contentHash: String(data.contentHash || ""),
      lenderUpiId: data.showUpiQr ? String(data.lenderUpiId || "").slice(0, 80) : "",
      trustLevelRequired,
      allowedActions: Array.isArray(data.allowedActions) ? data.allowedActions : ["view", "confirm", "dispute"],
      guestTokenHash,
      guestTokenExpiresAt,
      whatsappOtpHmac,
      whatsappOtpExpiresAtMillis,
      whatsappOtpUsed: false,
      whatsappVerified: false,
      revoked: false,
      status: "PENDING_CONFIRMATION",
      createdAt: nowMs,
      updatedAt: nowMs
    };

    await getFirestoreDb().collection("webLinks").doc(recordId).set(docPayload, { merge: true });

    return {
      success: true,
      recordId,
      url: `https://udhaar.jyotirmoyb.com/u/r/${recordId}#${secret}`,
      secret,
      whatsappOtp,
      expiresAt: `${expiryDays} days`,
      trustLevel: trustLevelRequired
    };
  }
);

exports.revokeGuestWebLink = onCall({ cors: true }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const recordId = String((request.data && request.data.recordId) || "").trim();
  if (!recordId) {
    throw new HttpsError("invalid-argument", "recordId is required.");
  }
  await getFirestoreDb().collection("webLinks").doc(recordId).set(
    {
      revoked: true,
      status: "REVOKED",
      guestTokenHash: "",
      updatedAt: Date.now()
    },
    { merge: true }
  );
  return { success: true };
});

exports.claimGuestActions = onCall({ cors: true }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const recordId = String((request.data && request.data.recordId) || "").trim();
  if (!recordId) {
    throw new HttpsError("invalid-argument", "recordId is required.");
  }
  await getFirestoreDb().collection("webLinks").doc(recordId).set(
    {
      claimedByCreatorAt: Date.now()
    },
    { merge: true }
  );
  return { success: true };
});

exports.deleteGuestData = onCall({ cors: true }, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const recordId = String((request.data && request.data.recordId) || "").trim();
  if (!recordId) {
    throw new HttpsError("invalid-argument", "recordId is required.");
  }
  await getFirestoreDb().collection("webLinks").doc(recordId).delete();
  return { success: true };
});

exports.getGuestRecord = onRequest({ region: "asia-south1", cors: true }, async (req, res) => {
  try {
    const body = req.body || {};
    const recordId = String(body.recordId || (req.query && req.query.recordId) || "").trim();
    const secret = String(body.secret || req.headers["x-guest-token"] || "").trim();

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const db = getFirestoreDb();
    const { linkData } = await loadWebLinkAndVerify(db, recordId, secret, Date.now());
    const principalAmountPaise = Number(linkData.principalAmountPaise || Math.round(Number(linkData.principalAmount || 0) * 100));

    res.status(200).json({
      success: true,
      record: {
        recordId,
        counterpartyRole: linkData.counterpartyRole || "BORROWER",
        lenderName: linkData.lenderName || "Lender",
        borrowerName: linkData.borrowerName || "Borrower",
        principalAmount: principalAmountPaise / 100,
        amountPaise: principalAmountPaise,
        currency: linkData.currency || "INR",
        startDateMillis: linkData.startDateMillis || linkData.createdAt || Date.now(),
        expiryDateMillis: linkData.expiryDateMillis || null,
        note: linkData.note || "",
        contentHash: linkData.contentHash || "",
        lenderUpiId: linkData.lenderUpiId || "",
        creatorPhone: linkData.creatorPhone || "",
        trustLevelRequired: linkData.trustLevelRequired || "WHATSAPP_OTP",
        whatsappVerified: Boolean(linkData.whatsappVerified),
        status: linkData.status || "PENDING_CONFIRMATION",
        disputeReason: linkData.disputeReason || "",
        confirmedAtMillis: linkData.confirmedAtMillis || null
      }
    });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
  }
});

exports.verifyGuestPhoneOtp = onRequest(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      const body = req.body || {};
      const recordId = String(body.recordId || "").trim();
      const secret = String(body.secret || "").trim();
      const rawCode = String(body.whatsappOtp || body.guestPhoneOtp || body.code || "").trim();

      const allowed = await checkGuestEndpointRateLimit(req, recordId);
      if (!allowed) {
        res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
        return;
      }

      const db = getFirestoreDb();
      const { webLinkRef, linkData } = await loadWebLinkAndVerify(db, recordId, secret, Date.now());
      const otpRes = await verifyGuestPhoneOtpHelper(db, webLinkRef, linkData, rawCode, Date.now());
      if (!otpRes.verified) {
        res.status(otpRes.status || 403).json({ error: otpRes.error || "INVALID_OTP" });
        return;
      }
      res.status(200).json({
        success: true,
        whatsappVerified: true,
        trustLevel: otpRes.trustLevel || "Code checked"
      });
    } catch (err) {
      res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
    }
  }
);

exports.guestConfirm = onRequest(
  { cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      const body = req.body || {};
      const recordId = String(body.recordId || "").trim();
      const secret = String(body.secret || "").trim();
      const rawCode = String(body.whatsappOtp || body.guestPhoneOtp || "").trim();

      const allowed = await checkGuestEndpointRateLimit(req, recordId);
      if (!allowed) {
        res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
        return;
      }

      const nowMs = Date.now();
      const db = getFirestoreDb();
      const { webLinkRef, linkData } = await loadWebLinkAndVerify(db, recordId, secret, nowMs);

      let whatsappVerified = Boolean(linkData.whatsappVerified);
      if (linkData.trustLevelRequired !== "LINK_ONLY" && !whatsappVerified) {
        const otpRes = await verifyGuestPhoneOtpHelper(db, webLinkRef, linkData, rawCode, nowMs);
        if (!otpRes.verified) {
          res.status(otpRes.status || 403).json({ error: otpRes.error || "INVALID_OTP" });
          return;
        }
        whatsappVerified = true;
      }

      await webLinkRef.set(
        {
          status: "CONFIRMED",
          whatsappVerified,
          confirmedAtMillis: nowMs,
          updatedAt: nowMs
        },
        { merge: true }
      );

      await db.collection("shared_records").doc(recordId).set(
        {
          verificationStatus: "CONFIRMED",
          verifiedAtMillis: nowMs,
          updatedAtMillis: nowMs
        },
        { merge: true }
      );

      await writeAuditEntry(db, recordId, {
        eventType: "CONFIRMED",
        summaryText: "Confirmed via Udhaar Web verification link"
      });
      await writeInboxItem(db, recordId, {
        actionType: "CONFIRM",
        summaryText: "Counterparty confirmed the record via Web link",
        trustLevel: whatsappVerified ? "WHATSAPP_OTP" : "LINK_ONLY"
      });

      res.status(200).json({
        success: true,
        trustLevel: whatsappVerified ? "Code checked" : "Private link"
      });
    } catch (err) {
      res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
    }
  }
);

exports.guestDispute = onRequest({ region: "asia-south1", cors: true }, async (req, res) => {
  try {
    const body = req.body || {};
    const recordId = String(body.recordId || "").trim();
    const secret = String(body.secret || "").trim();
    const reasonCode = String(body.reasonCode || "OTHER").slice(0, 40);
    const reasonText = String(body.reasonText || "").trim().slice(0, 300);
    const combinedReason = reasonText ? `${reasonCode}: ${reasonText}` : reasonCode;

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const nowMs = Date.now();
    const db = getFirestoreDb();
    const { webLinkRef } = await loadWebLinkAndVerify(db, recordId, secret, nowMs);

    await webLinkRef.set(
      {
        status: "DISPUTED",
        disputeReason: combinedReason,
        disputedAtMillis: nowMs,
        updatedAt: nowMs
      },
      { merge: true }
    );

    await db.collection("shared_records").doc(recordId).set(
      {
        verificationStatus: "DISPUTED",
        disputeReason: combinedReason,
        updatedAtMillis: nowMs
      },
      { merge: true }
    );

    await writeAuditEntry(db, recordId, {
      eventType: "DISPUTED",
      summaryText: `Disputed via Web link (${combinedReason})`
    });
    await writeInboxItem(db, recordId, {
      actionType: "DISPUTE",
      summaryText: "Counterparty reported an issue via Web link",
      detailText: combinedReason
    });

    res.status(200).json({ success: true });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
  }
});

exports.guestPaymentClaim = onRequest({ region: "asia-south1", cors: true }, async (req, res) => {
  try {
    const body = req.body || {};
    const recordId = String(body.recordId || "").trim();
    const secret = String(body.secret || "").trim();
    const amountPaise = Math.round(Number(body.amount || body.amountPaise || 0));
    const paymentMode = String(body.paymentMode || "UPI").slice(0, 20);
    const referenceNumber = String(body.referenceNumber || "").trim().slice(0, 100);

    if (!Number.isFinite(amountPaise) || amountPaise <= 0) {
      res.status(400).json({ error: "INVALID_AMOUNT" });
      return;
    }

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const nowMs = Date.now();
    const db = getFirestoreDb();
    const { webLinkRef } = await loadWebLinkAndVerify(db, recordId, secret, nowMs);

    await webLinkRef.set(
      {
        hasPendingPaymentClaim: true,
        lastPaymentClaimPaise: amountPaise,
        lastPaymentClaimMode: paymentMode,
        lastPaymentClaimRef: referenceNumber,
        updatedAt: nowMs
      },
      { merge: true }
    );

    await writeInboxItem(db, recordId, {
      actionType: "PAYMENT_CLAIM",
      amountPaise,
      paymentMode,
      reference: referenceNumber,
      summaryText: `Payment claim of ₹${(amountPaise / 100).toFixed(2)} via ${paymentMode}`
    });

    res.status(200).json({ success: true });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
  }
});

exports.guestProposeDate = onRequest({ region: "asia-south1", cors: true }, async (req, res) => {
  try {
    const body = req.body || {};
    const recordId = String(body.recordId || "").trim();
    const secret = String(body.secret || "").trim();
    const proposedDateMillis = Number(body.proposedDateMillis || 0);
    const note = String(body.note || "").trim().slice(0, 200);

    if (!Number.isFinite(proposedDateMillis) || proposedDateMillis <= 0) {
      res.status(400).json({ error: "INVALID_DATE" });
      return;
    }

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const nowMs = Date.now();
    const db = getFirestoreDb();
    const { webLinkRef } = await loadWebLinkAndVerify(db, recordId, secret, nowMs);

    await webLinkRef.set(
      {
        proposedDateMillis,
        proposedDateNote: note,
        updatedAt: nowMs
      },
      { merge: true }
    );

    await writeInboxItem(db, recordId, {
      actionType: "PROPOSE_DATE",
      proposedDateMillis,
      message: note,
      summaryText: "Counterparty proposed a return date"
    });

    res.status(200).json({ success: true });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
  }
});

exports.guestSetUpi = onRequest({ region: "asia-south1", cors: true }, async (req, res) => {
  try {
    const body = req.body || {};
    const recordId = String(body.recordId || "").trim();
    const secret = String(body.secret || "").trim();
    const upiId = String(body.upiId || "").trim().slice(0, 80);

    if (!upiId || !upiId.includes("@")) {
      res.status(400).json({ error: "INVALID_UPI_ID" });
      return;
    }

    const allowed = await checkGuestEndpointRateLimit(req, recordId);
    if (!allowed) {
      res.status(429).json({ error: "RATE_LIMIT_EXCEEDED" });
      return;
    }

    const nowMs = Date.now();
    const db = getFirestoreDb();
    const { webLinkRef } = await loadWebLinkAndVerify(db, recordId, secret, nowMs);

    await webLinkRef.set(
      {
        guestUpiId: upiId,
        updatedAt: nowMs
      },
      { merge: true }
    );

    await writeInboxItem(db, recordId, {
      actionType: "SET_UPI",
      guestUpiId: upiId,
      summaryText: "Counterparty shared their UPI ID"
    });

    res.status(200).json({ success: true });
  } catch (err) {
    res.status(err.status || 500).json({ error: err.message || "INTERNAL_ERROR" });
  }
});

exports.STORAGE_LIFECYCLE_RULE_90_DAYS = STORAGE_LIFECYCLE_RULE_90_DAYS;
exports.buildProofSignedUrlOptions = buildProofSignedUrlOptions;
exports.ensureBucketLifecycle90Days = ensureBucketLifecycle90Days;


// ===========================================================================
// Web Onboarding, Email Verification & Web Ledger Endpoints
// ===========================================================================

async function validateWebSession(db, sessionToken) {
  if (!sessionToken || typeof sessionToken !== "string" || !sessionToken.startsWith("web_sess_")) {
    return null;
  }
  const sessionHash = crypto.createHash("sha256").update(sessionToken.trim()).digest("hex");
  const snap = await db.collection("web_sessions").doc(sessionHash).get();
  if (!snap.exists) return null;
  const data = snap.data();
  if (Date.now() > (data.expiresAtMillis || 0)) {
    await db.collection("web_sessions").doc(sessionHash).delete();
    return null;
  }
  return data;
}

exports.sendEmailOtp = onRequest(
  { region: "asia-south1", cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
        return;
      }
      const body = req.body || {};
      const email = String(body.email || "").trim().toLowerCase();
      const mobileNumber = String(body.mobileNumber || body.phone || "").trim();
      const locale = String(body.locale || "en").trim().toLowerCase();

      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!email || !emailRegex.test(email) || email.length > 100) {
        res.status(400).json({ error: "INVALID_EMAIL", message: "Please provide a valid email address." });
        return;
      }

      const digits = mobileNumber.replace(/\D/g, "");
      if (digits.length < 10 || digits.length > 15) {
        res.status(400).json({ error: "INVALID_PHONE", message: "Please provide a valid mobile number with at least 10 digits." });
        return;
      }

      const allowed = await checkGuestEndpointRateLimit(req, `email_otp_${email}`);
      if (!allowed) {
        res.status(429).json({ error: "RATE_LIMIT_EXCEEDED", message: "Too many attempts. Please wait 15 minutes." });
        return;
      }

      const otp = String(crypto.randomInt(100000, 1000000));
      const { computeCodeHmac } = require("./guestWebFlow");
      const otpHmac = computeCodeHmac(otp);
      const emailHash = crypto.createHash("sha256").update(email).digest("hex");
      const nowMs = Date.now();
      const expiresAtMillis = nowMs + 10 * 60 * 1000; // 10 minutes

      const db = getFirestoreDb();
      if (db) {
        await db.collection("email_verifications").doc(emailHash).set({
          emailHash,
          emailMasked: email.replace(/^(.)(.*)(@.*)$/, (_m, a, b, c) => a + "*".repeat(Math.min(b.length, 6)) + c),
          mobileNumber,
          otpHmac,
          attemptsRemaining: 5,
          expiresAtMillis,
          createdAt: nowMs
        });
      }

      const channelsDispatched = [];

      // 1. Dispatch Email OTP via Brevo (TransactionalEmailProvider) if BREVO_API_KEY is configured
      const brevoKey = (process.env.BREVO_API_KEY || process.env.EMAIL_PROVIDER_API_KEY || "").trim();
      if (brevoKey) {
        try {
          const { TransactionalEmailProvider } = require("./src/providers/email");
          const emailProvider = new TransactionalEmailProvider({
            apiKey: brevoKey,
            fromAddress: process.env.EMAIL_FROM_ADDRESS
          });
          await emailProvider.sendEmail({ toEmail: email, code: otp, locale });
          channelsDispatched.push("email");
        } catch (emailErr) {
          console.error("[Udhaar Verification] Brevo email delivery failed:", emailErr.message);
        }
      }

      // 2. Optionally dispatch WhatsApp OTP via Meta WhatsApp Cloud API if configured
      const waToken = (process.env.META_WHATSAPP_TOKEN || process.env.WHATSAPP_API_TOKEN || "").trim();
      const waPhoneId = (process.env.META_PHONE_NUMBER_ID || process.env.WHATSAPP_PHONE_NUMBER_ID || "").trim();
      if (waToken && waPhoneId && digits.length >= 10) {
        try {
          const { WhatsAppCloudApiProvider } = require("./src/providers/whatsapp");
          const waProvider = new WhatsAppCloudApiProvider({
            phoneNumberId: waPhoneId,
            accessToken: waToken
          });
          const destPhone = digits.length === 10 ? `+91${digits}` : `+${digits}`;
          await waProvider.sendCode({ destination: destPhone, code: otp, locale });
          channelsDispatched.push("whatsapp");
        } catch (waErr) {
          console.error("[Udhaar Verification] WhatsApp OTP delivery failed:", waErr.message);
        }
      }

      console.log(`[Udhaar Verification] OTP generated for emailHash ${emailHash.substring(0, 8)}... channels=${channelsDispatched.join(",") || "log-only"}`);

      res.status(200).json({
        success: true,
        message: "A 6-digit verification code has been sent to your email address.",
        email: email,
        channels: channelsDispatched,
        expiresAtMillis,
        debugOtp: process.env.NODE_ENV !== "production" ? otp : undefined
      });
    } catch (err) {
      console.error("sendEmailOtp error:", err);
      res.status(500).json({ error: "INTERNAL_ERROR", message: err.message || "Failed to send verification code." });
    }
  }
);

exports.verifyEmailOtp = onRequest(
  { region: "asia-south1", cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
        return;
      }
      const body = req.body || {};
      const email = String(body.email || "").trim().toLowerCase();
      const mobileNumber = String(body.mobileNumber || body.phone || "").trim();
      const otp = String(body.otp || body.code || "").trim();

      if (!email || !otp || !/^\d{6}$/.test(otp)) {
        res.status(400).json({ error: "INVALID_REQUEST", message: "Valid email and 6-digit OTP code are required." });
        return;
      }

      const emailHash = crypto.createHash("sha256").update(email).digest("hex");
      const db = getFirestoreDb();
      if (!db) {
        res.status(500).json({ error: "DB_UNAVAILABLE" });
        return;
      }
      const verDocRef = db.collection("email_verifications").doc(emailHash);
      const verSnap = await verDocRef.get();

      if (!verSnap.exists) {
        res.status(400).json({ error: "OTP_NOT_FOUND", message: "No verification code requested for this email. Please request a new code." });
        return;
      }

      const verData = verSnap.data();
      const nowMs = Date.now();

      if (nowMs > (verData.expiresAtMillis || 0)) {
        res.status(400).json({ error: "OTP_EXPIRED", message: "The verification code has expired. Please request a new one." });
        return;
      }

      if ((verData.attemptsRemaining || 0) <= 0) {
        res.status(429).json({ error: "TOO_MANY_ATTEMPTS", message: "Too many incorrect attempts. Please request a new code." });
        return;
      }

      const { computeCodeHmac } = require("./guestWebFlow");
      const providedHmac = computeCodeHmac(otp);
      const isMatch = crypto.timingSafeEqual(
        Buffer.from(providedHmac, "hex"),
        Buffer.from(verData.otpHmac || "", "hex")
      );

      if (!isMatch) {
        const remaining = (verData.attemptsRemaining || 5) - 1;
        await verDocRef.update({ attemptsRemaining: remaining });
        res.status(403).json({
          error: "INVALID_OTP",
          message: `Incorrect verification code. ${remaining} attempt(s) remaining.`,
          attemptsRemaining: remaining
        });
        return;
      }

      await verDocRef.delete();

      const uid = "web_" + emailHash.slice(0, 20);
      const phoneDigits = mobileNumber ? mobileNumber.replace(/\D/g, "") : (verData.mobileNumber || "").replace(/\D/g, "");
      const { computeSaltedPhoneHash } = require("./guestWebFlow");
      let phoneHmac = "";
      try {
        if (phoneDigits) {
          phoneHmac = computeSaltedPhoneHash(phoneDigits).phoneHmac;
        }
      } catch (_) {}

      const userProfile = {
        userId: uid,
        email,
        phoneNumber: mobileNumber || verData.mobileNumber || "",
        phoneHmac,
        emailVerified: true,
        accountStatus: "VERIFIED",
        updatedAt: nowMs
      };

      await db.collection("user_profiles").doc(uid).set(userProfile, { merge: true });

      const rawSessionToken = "web_sess_" + crypto.randomBytes(24).toString("hex");
      const sessionHash = crypto.createHash("sha256").update(rawSessionToken).digest("hex");
      const sessionExpiresAt = nowMs + 30 * 24 * 60 * 60 * 1000; // 30 days

      await db.collection("web_sessions").doc(sessionHash).set({
        sessionHash,
        userId: uid,
        email,
        mobileNumber: userProfile.phoneNumber,
        createdAt: nowMs,
        expiresAtMillis: sessionExpiresAt
      });

      res.status(200).json({
        success: true,
        sessionToken: rawSessionToken,
        user: {
          uid,
          email,
          mobileNumber: userProfile.phoneNumber,
          isVerified: true
        }
      });
    } catch (err) {
      console.error("verifyEmailOtp error:", err);
      res.status(500).json({ error: "INTERNAL_ERROR", message: err.message || "Failed to verify code." });
    }
  }
);

exports.getUserLedger = onRequest(
  { region: "asia-south1", cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      const authHeader = req.headers["authorization"] || "";
      const bodyToken = (req.body && req.body.sessionToken) || "";
      const token = bodyToken || (authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim());

      const db = getFirestoreDb();
      if (!db) {
        res.status(500).json({ error: "DB_UNAVAILABLE" });
        return;
      }
      const session = await validateWebSession(db, token);
      if (!session) {
        res.status(401).json({ error: "UNAUTHORIZED", message: "Invalid or expired session. Please sign in." });
        return;
      }

      const uid = session.userId;
      const ledgerSnap = await db.collection("users").doc(uid).collection("ledger")
        .orderBy("createdAt", "desc")
        .limit(100)
        .get();

      const records = [];
      ledgerSnap.forEach((doc) => {
        records.push({ id: doc.id, ...doc.data() });
      });

      res.status(200).json({
        success: true,
        user: {
          uid,
          email: session.email,
          mobileNumber: session.mobileNumber,
          isVerified: true
        },
        records
      });
    } catch (err) {
      console.error("getUserLedger error:", err);
      res.status(500).json({ error: "INTERNAL_ERROR", message: err.message || "Failed to load ledger." });
    }
  }
);

exports.syncWebRecord = onRequest(
  { region: "asia-south1", cors: true, secrets: [UDHAAR_SERVER_SECRET] },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
        return;
      }
      const authHeader = req.headers["authorization"] || "";
      const body = req.body || {};
      const token = body.sessionToken || (authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim());

      const db = getFirestoreDb();
      if (!db) {
        res.status(500).json({ error: "DB_UNAVAILABLE" });
        return;
      }
      const session = await validateWebSession(db, token);
      if (!session) {
        res.status(401).json({ error: "UNAUTHORIZED", message: "Please sign in to record transactions." });
        return;
      }

      const uid = session.userId;
      const rec = body.record || {};
      const partyName = String(rec.partyName || "").trim().slice(0, 80);
      const partyPhone = String(rec.partyPhone || "").trim().slice(0, 24);
      const counterpartyRole = rec.counterpartyRole === "BORROWER" ? "BORROWER" : "LENDER";
      const amountPaise = Math.round(Number(rec.amountPaise || (Number(rec.amount || 0) * 100)));
      const currency = String(rec.currency || "INR").slice(0, 8);
      const dueDateMillis = rec.dueDateMillis ? Number(rec.dueDateMillis) : null;
      const note = String(rec.note || "").trim().slice(0, 500);
      const upiId = String(rec.upiId || "").trim().slice(0, 80);

      if (!partyName) {
        res.status(400).json({ error: "MISSING_NAME", message: "Person name is required." });
        return;
      }
      if (!Number.isFinite(amountPaise) || amountPaise <= 0) {
        res.status(400).json({ error: "INVALID_AMOUNT", message: "A positive transaction amount is required." });
        return;
      }

      const nowMs = Date.now();
      const recordId = "tx_" + crypto.randomUUID();
      const secret = crypto.randomBytes(12).toString("base64url");
      const { hashGuestToken, computeCodeHmac } = require("./guestWebFlow");
      const guestTokenHash = hashGuestToken(secret);
      const whatsappOtp = String(crypto.randomInt(100000, 1000000));
      const whatsappOtpHmac = computeCodeHmac(whatsappOtp);
      const expiryDays = 30;
      const guestTokenExpiresAt = nowMs + expiryDays * 24 * 60 * 60 * 1000;
      const whatsappOtpExpiresAtMillis = nowMs + 15 * 60 * 1000;

      const isLent = counterpartyRole === "BORROWER";
      const lenderName = isLent ? "You" : partyName;
      const borrowerName = isLent ? partyName : "You";

      // Cryptographic SHA-256 fingerprint & Immutable Activity Audit Trail
      const contentHash = crypto
        .createHash("sha256")
        .update([recordId, uid, partyPhone, amountPaise, currency, nowMs].join(":"))
        .digest("hex");

      const initialAuditTrail = [
        {
          eventId: "ev_" + nowMs + "_1",
          timestamp: nowMs,
          actionType: "RECORD_CREATED",
          actor: uid,
          summaryText: `${isLent ? "Lent" : "Borrowed"} ₹${(amountPaise / 100).toFixed(2).replace(/\.00$/, "")} recorded with ${partyName}`,
          contentHash
        },
        {
          eventId: "ev_" + nowMs + "_2",
          timestamp: nowMs,
          actionType: "WHATSAPP_CODE_GENERATED",
          actor: "SYSTEM",
          summaryText: `Single-use 6-digit WhatsApp verification code generated for ${partyPhone}`,
          contentHash
        }
      ];

      const personalRecord = {
        recordId,
        creatorUid: uid,
        counterpartyRole,
        creatorRole: isLent ? "GIVEN" : "TAKEN",
        partyName,
        partyPhone,
        lenderName,
        borrowerName,
        principalAmountPaise: amountPaise,
        principalAmount: amountPaise / 100,
        currency,
        startDateMillis: nowMs,
        dueDateMillis,
        note,
        upiId,
        status: "PENDING_CONFIRMATION",
        whatsappOtp,
        secret,
        contentHash,
        auditTrail: initialAuditTrail,
        createdAt: nowMs,
        updatedAt: nowMs
      };
      await db.collection("users").doc(uid).collection("ledger").doc(recordId).set(personalRecord);

      const webLinkPayload = {
        recordId,
        creatorUid: uid,
        counterpartyRole,
        intendedPhone: partyPhone,
        creatorPhone: session.mobileNumber || "",
        lenderName,
        borrowerName,
        principalAmountPaise: amountPaise,
        principalAmount: amountPaise / 100,
        currency,
        startDateMillis: nowMs,
        expiryDateMillis: dueDateMillis,
        note,
        lenderUpiId: upiId,
        trustLevelRequired: "WHATSAPP_OTP",
        allowedActions: ["view", "confirm", "dispute"],
        guestTokenHash,
        guestTokenExpiresAt,
        whatsappOtpHmac,
        whatsappOtpExpiresAtMillis,
        whatsappOtpUsed: false,
        whatsappVerified: false,
        revoked: false,
        status: "PENDING_CONFIRMATION",
        contentHash,
        auditTrail: initialAuditTrail,
        createdAt: nowMs,
        updatedAt: nowMs
      };
      await db.collection("webLinks").doc(recordId).set(webLinkPayload, { merge: true });

      await db.collection("shared_records").doc(recordId).set({
        remoteId: recordId,
        creatorUid: uid,
        counterpartyUid: "",
        participantUids: [uid],
        creatorRole: isLent ? "GIVEN" : "TAKEN",
        lenderName,
        borrowerName,
        principalAmount: amountPaise / 100,
        principalAmountPaise: amountPaise,
        currency,
        startDateMillis: nowMs,
        dueDateMillis,
        note,
        verificationStatus: "PENDING_CONFIRMATION",
        createdAtMillis: nowMs,
        updatedAtMillis: nowMs
      }, { merge: true });

      const amountRupees = (amountPaise / 100).toFixed(2).replace(/\.00$/, "");
      const verificationUrl = `https://gen-lang-client-0216629575.web.app/u/r/${recordId}?amt=${amountRupees}&from=${encodeURIComponent(lenderName)}&to=${encodeURIComponent(borrowerName)}&cur=${currency}#${secret}`;

      const actionVerb = isLent ? "lent you" : "borrowed from you";
      const waText = `Hi ${partyName}, I've recorded ₹${amountRupees} (${actionVerb}) on Udhaar. Please review and confirm our record here: ${verificationUrl}\n\nYour 6-digit WhatsApp verification code is: ${whatsappOtp}`;
      let waLink = "https://wa.me/?text=" + encodeURIComponent(waText);
      const cleanPhone = partyPhone.replace(/\D/g, "");
      if (cleanPhone.length >= 10) {
        waLink = `https://wa.me/${cleanPhone.length === 10 ? "91" + cleanPhone : cleanPhone}?text=${encodeURIComponent(waText)}`;
      }

      res.status(200).json({
        success: true,
        recordId,
        record: personalRecord,
        verificationUrl,
        whatsappOtp,
        whatsappShareUrl: waLink,
        whatsappText: waText
      });
    } catch (err) {
      console.error("syncWebRecord error:", err);
      res.status(500).json({ error: "INTERNAL_ERROR", message: err.message || "Failed to sync record." });
    }
  }
);
