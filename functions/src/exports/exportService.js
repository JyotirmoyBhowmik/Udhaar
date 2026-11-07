/**
 * Compliance & Data Portability Export Engine (functions/src/exports/exportService.js)
 * Implements Stage S7:
 * - RFC 4180 CSV Ledger Export with strict Formula Injection Protection (`=`, `+`, `-`, `@`, `\t`, `\r`).
 * - Printable Statement of Account HTML/PDF Template with Cryptographic Merkle Seal.
 * - DPDP Act 2023 Full Personal Data Portability Archive (JSON).
 * - Strictly backed by async AuthRepository, LedgerRepository, ChatRepository, InboxRepository, and ReminderRepository.
 */

const crypto = require("crypto");
const { listRecords } = require("../ledger/recordService");
const { listPeople } = require("../ledger/peopleService");
const { listEvents, verifyAuditChain } = require("../ledger/eventService");
const { listUserChatThreads } = require("../chat/chatService");
const { listUserInboxItems } = require("../inbox/inboxService");
const { getUserReminderPolicy } = require("../reminders/reminderService");
const { getAuthRepository, assertRepoMethod } = require("../repositories");

const FORMULA_DANGEROUS_PREFIXES = ["=", "+", "-", "@", "\t", "\r"];

function getRequiredAuthRepo() {
  const repo = getAuthRepository();
  assertRepoMethod(repo, "getUser", "AuthRepository");
  return repo;
}

/**
 * Sanitize cell against Spreadsheet Formula Injection (OWASP CSV Injection defense)
 * and format according to RFC 4180.
 */
function sanitizeCsvCell(value) {
  if (value === null || value === undefined) return '""';
  let str = String(value);

  if (str.length > 0 && FORMULA_DANGEROUS_PREFIXES.includes(str[0])) {
    str = `'${str}`;
  }

  return `"${str.replace(/"/g, '""')}"`;
}

/**
 * Format integer paise to decimal Rupee string (e.g. 500050 -> "5000.50")
 */
function formatPaiseToRupees(paise) {
  const num = Number(paise) || 0;
  return (num / 100).toFixed(2);
}

/**
 * Generate RFC 4180 compliant CSV export of user's ledger records
 */
async function generateLedgerCsv({ uid }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const { items: records } = await listRecords({ uid, limit: 100 });

  const headers = [
    "Record ID",
    "Counterparty Name",
    "Counterparty Phone",
    "Role",
    "Principal Amount (INR)",
    "Remaining Balance (INR)",
    "Status",
    "Verification Status",
    "Start Date (ISO)",
    "Due Date (ISO)",
    "Notes"
  ];

  const rows = [headers.map(sanitizeCsvCell).join(",")];

  for (const r of records) {
    const row = [
      r.recordId,
      r.partyName || "",
      r.partyPhone || "",
      r.counterpartyRole || "BORROWER",
      formatPaiseToRupees(r.principalAmountPaise),
      formatPaiseToRupees(r.remainingAmountPaise),
      r.status || "PENDING",
      r.verificationStatus || "UNVERIFIED",
      r.startDateMs ? new Date(r.startDateMs).toISOString().split("T")[0] : "",
      r.dueDateMs ? new Date(r.dueDateMs).toISOString().split("T")[0] : "",
      r.note || ""
    ];
    rows.push(row.map(sanitizeCsvCell).join(","));
  }

  return rows.join("\r\n");
}

/**
 * Generate a printable HTML Statement of Account (for PDF printing) with Merkle Seal
 */
async function generateStatementHtml({ uid }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const authRepo = getRequiredAuthRepo();
  const user = (await authRepo.getUser(uid)) || { uid, displayName: "Udhaar Account Holder" };
  const { items: records } = await listRecords({ uid, limit: 100 });
  const events = await listEvents({ uid });
  const auditVerification = await verifyAuditChain(uid);

  let totalReceivablePaise = 0;
  let totalPayablePaise = 0;

  for (const r of records) {
    if (r.status === "SETTLED") continue;
    if (r.counterpartyRole === "BORROWER") {
      totalReceivablePaise += r.remainingAmountPaise;
    } else {
      totalPayablePaise += r.remainingAmountPaise;
    }
  }

  const latestMerkleHash =
    events.length > 0
      ? events[events.length - 1].merkleHash
      : crypto.createHash("sha256").update(`genesis:${uid}:udhaar_v2`).digest("hex");

  const statementDate = new Date().toISOString().split("T")[0];

  const escapeHtml = (str) =>
    String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");

  const rowsHtml = records
    .map(
      (r) => `
      <tr>
        <td><code>${escapeHtml(r.recordId.slice(0, 14))}</code></td>
        <td><strong>${escapeHtml(r.partyName)}</strong></td>
        <td>${r.counterpartyRole === "BORROWER" ? "Receivable (You Lent)" : "Payable (You Borrowed)"}</td>
        <td style="text-align:right;">₹${formatPaiseToRupees(r.principalAmountPaise)}</td>
        <td style="text-align:right;"><strong>₹${formatPaiseToRupees(r.remainingAmountPaise)}</strong></td>
        <td>${escapeHtml(r.status)}</td>
        <td>${r.dueDateMs ? new Date(r.dueDateMs).toISOString().split("T")[0] : "—"}</td>
      </tr>`
    )
    .join("");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Udhaar Statement of Account — ${escapeHtml(user.displayName || uid)}</title>
  <style>
    body { font-family: system-ui, -apple-system, sans-serif; color: #0f172a; margin: 32px; line-height: 1.5; }
    .header { display: flex; justify-content: space-between; border-bottom: 2px solid #059669; padding-bottom: 16px; margin-bottom: 24px; }
    .summary-cards { display: flex; gap: 24px; margin-bottom: 24px; }
    .card { border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; flex: 1; }
    table { width: 100%; border-collapse: collapse; margin-bottom: 24px; font-size: 14px; }
    th, td { border: 1px solid #e2e8f0; padding: 10px; text-align: left; }
    th { background: #f8fafc; font-weight: 600; }
    .seal { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 16px; font-size: 12px; color: #166534; }
  </style>
</head>
<body>
  <div class="header">
    <div>
      <h1 style="margin:0; color:#059669;">UDHAAR — Official Statement of Account</h1>
      <p style="margin:4px 0 0 0; color:#475569;">Account Holder: <strong>${escapeHtml(user.displayName || "Udhaar User")}</strong> (${escapeHtml(uid)})</p>
    </div>
    <div style="text-align:right;">
      <p style="margin:0;">Date: <strong>${statementDate}</strong></p>
      <p style="margin:4px 0 0 0; color:#475569;">Currency: INR (₹)</p>
    </div>
  </div>

  <div class="summary-cards">
    <div class="card">
      <div style="color:#64748b; font-size:13px;">Total To Receive (Lent)</div>
      <div style="font-size:22px; font-weight:700; color:#059669;">₹${formatPaiseToRupees(totalReceivablePaise)}</div>
    </div>
    <div class="card">
      <div style="color:#64748b; font-size:13px;">Total To Pay (Borrowed)</div>
      <div style="font-size:22px; font-weight:700; color:#dc2626;">₹${formatPaiseToRupees(totalPayablePaise)}</div>
    </div>
    <div class="card">
      <div style="color:#64748b; font-size:13px;">Total Active Records</div>
      <div style="font-size:22px; font-weight:700;">${records.length}</div>
    </div>
  </div>

  <table>
    <thead>
      <tr>
        <th>Record ID</th>
        <th>Counterparty</th>
        <th>Direction</th>
        <th style="text-align:right;">Principal</th>
        <th style="text-align:right;">Remaining</th>
        <th>Status</th>
        <th>Due Date</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml || '<tr><td colspan="7" style="text-align:center;">No records found</td></tr>'}
    </tbody>
  </table>

  <div class="seal">
    <strong>Cryptographic Verification Seal (Merkle Audit Chain)</strong><br/>
    Chain Integrity: <strong>${auditVerification.valid ? "VERIFIED (" + auditVerification.blockCount + " blocks)" : "UNVERIFIED"}</strong><br/>
    Latest Block SHA-256 Digest: <code>${latestMerkleHash}</code>
  </div>
</body>
</html>`;
}

/**
 * Generate full DPDP Act 2023 compliant Data Portability JSON Archive
 */
async function generateFullDataArchive({ uid }) {
  if (!uid) {
    const err = new Error("User ID is required");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const authRepo = getRequiredAuthRepo();
  const user = (await authRepo.getUser(uid)) || { uid };
  const { items: records } = await listRecords({ uid, limit: 100 });
  const { items: people } = await listPeople({ uid, limit: 100 });
  const events = await listEvents({ uid });
  const auditVerification = await verifyAuditChain(uid);
  const chatThreads = await listUserChatThreads({ uid });
  const inbox = await listUserInboxItems({ uid });
  const reminderPolicy = await getUserReminderPolicy({ uid });

  const exportedAtIso = new Date().toISOString();

  const archivePayload = {
    archiveVersion: "2.0",
    legalCompliance: {
      act: "Digital Personal Data Protection Act 2023 (DPDP Act, India)",
      right: "Right to Data Portability & Access (Section 11)",
      exportedAtIso
    },
    dataSubject: {
      uid: user.uid,
      displayName: user.displayName || "Udhaar User",
      phoneHash: user.phoneHash || null,
      emailHash: user.emailHash || null,
      createdAtMs: user.createdAtMs || null,
      consents: user.consents || {}
    },
    ledger: {
      recordsCount: records.length,
      records,
      peopleCount: people.length,
      people,
      eventsCount: events.length,
      events,
      merkleAuditVerification: auditVerification
    },
    communications: {
      chatThreadsCount: chatThreads.length,
      chatThreads,
      inboxItemsCount: inbox.items.length,
      inboxItems: inbox.items,
      reminderPolicy
    }
  };

  const archiveChecksum = crypto
    .createHash("sha256")
    .update(JSON.stringify(archivePayload))
    .digest("hex");

  return {
    ...archivePayload,
    checksumSha256: archiveChecksum
  };
}

module.exports = {
  sanitizeCsvCell,
  formatPaiseToRupees,
  generateLedgerCsv,
  generateStatementHtml,
  generateFullDataArchive
};
