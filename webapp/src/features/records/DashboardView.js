/**
 * Udhaar Web App Dashboard View (webapp/src/features/records/DashboardView.js)
 * Implements Overview KPI cards, record lists, create/edit modals, conflict resolution,
 * and offline sync triggers. Strict CSP compliant with zero inline event handlers.
 */

const { ledgerDb } = require("../../db/ledgerDb");
const { syncEngine } = require("../../sync/syncEngine");
const { buildGoogleCalendarUrl, downloadIcsFile } = require("../reminders/calendarReminderService");

class DashboardView {
  /**
   * @param {object} options
   * @param {any} options.i18n
   * @param {any} options.authClient
   * @param {any} options.router
   * @param {(msg: string) => void} options.onToast
   */
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.container = null;
    this.records = [];
  }

  /**
   * @param {HTMLElement} container
   */
  async render(container) {
    this.container = container;
    const t = this.i18n.t;

    await syncEngine.init();
    this.records = (await ledgerDb.getAllRecords()).filter(r => !r.deletedAtMs);

    // Compute KPIs
    let youWillGetPaise = 0;
    let youWillGivePaise = 0;

    for (const r of this.records) {
      const remaining = Number(r.remainingAmountPaise !== undefined ? r.remainingAmountPaise : r.principalAmountPaise) || 0;
      if (r.counterpartyRole === "BORROWER") {
        youWillGetPaise += remaining;
      } else {
        youWillGivePaise += remaining;
      }
    }

    const netPaise = youWillGetPaise - youWillGivePaise;
    const netFormatted = `₹${(Math.abs(netPaise) / 100).toLocaleString("en-IN")}`;
    const getFormatted = `₹${(youWillGetPaise / 100).toLocaleString("en-IN")}`;
    const giveFormatted = `₹${(youWillGivePaise / 100).toLocaleString("en-IN")}`;

    // Conflict items
    const conflicts = this.records.filter(r => r.syncStatus === "CONFLICT");

    this.container.innerHTML = `
      <section aria-label="Account Overview" class="dashboard-grid">
        <div class="kpi-card">
          <div class="kpi-title">${t("net_balance")}</div>
          <div class="kpi-amount" style="color: ${netPaise >= 0 ? "var(--color-primary)" : "var(--color-danger)"};">
            ${netPaise < 0 ? "-" : ""}${netFormatted}
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_get")}</div>
          <div class="kpi-amount lent">${getFormatted}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_give")}</div>
          <div class="kpi-amount borrowed">${giveFormatted}</div>
        </div>
      </section>

      ${conflicts.length > 0 ? this._renderConflictsSection(conflicts) : ""}

      <div style="display: flex; justify-content: space-between; align-items: center; margin: 1.5rem 0 0.75rem 0;">
        <h2 style="font-family: var(--font-display); font-size: 1.15rem; font-weight: 700;">
          ${t("nav_records")} (${this.records.length})
        </h2>
        <button type="button" id="add-record-btn" class="btn btn-primary" style="padding: 0.5rem 1rem; width: auto; font-size: 0.875rem;">
          + ${t("add_first_record")}
        </button>
      </div>

      ${this.records.length === 0 ? this._renderEmptyState() : this._renderRecordsList()}

      <!-- Create Record Modal -->
      <div id="add-record-modal" class="modal-overlay" style="display: none;" role="dialog" aria-modal="true" aria-labelledby="add-modal-title">
        <div class="modal-card" style="max-width: 480px; padding: 1.5rem;">
          <h2 id="add-modal-title" style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("add_first_record")}
          </h2>
          <form id="create-record-form">
            <div class="form-group">
              <label class="form-label" for="record-party-name">${t("phone_label") || "Party Name"}</label>
              <input type="text" id="record-party-name" class="input-field" placeholder="e.g. Ramesh Sharma" required>
            </div>
            <div class="form-group">
              <label class="form-label" for="record-amount">Amount (₹)</label>
              <input type="number" id="record-amount" class="input-field" placeholder="1000" min="1" step="any" required>
            </div>
            <div class="form-group">
              <label class="form-label" for="record-role">Type</label>
              <select id="record-role" class="input-field">
                <option value="BORROWER">${t("you_will_get")} (I gave)</option>
                <option value="LENDER">${t("you_will_give")} (I took)</option>
              </select>
            </div>
            <div style="display: flex; gap: 0.75rem; margin-top: 1.5rem;">
              <button type="button" id="cancel-create-btn" class="btn btn-secondary" style="flex: 1;">Cancel</button>
              <button type="submit" id="submit-create-btn" class="btn btn-primary" style="flex: 1;">Save Record</button>
            </div>
          </form>
        </div>
      </div>
    `;

    this._bindEvents();
  }

  _renderEmptyState() {
    const t = this.i18n.t;
    return `
      <section class="card" style="text-align: center; padding: 3rem 1.5rem; margin-top: 0.5rem;">
        <div style="font-size: 3rem; margin-bottom: 0.75rem;">📖</div>
        <h3 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 0.5rem;">
          ${t("no_records_title")}
        </h3>
        <p style="font-size: 0.95rem; color: var(--color-text-muted); max-width: 420px; margin: 0 auto 1.5rem auto;">
          ${t("no_records_desc")}
        </p>
      </section>
    `;
  }

  _renderConflictsSection(conflicts) {
    const t = this.i18n.t;
    return `
      <section aria-label="Conflicts" style="margin-top: 1rem; border: 1.5px solid var(--color-warning, #d97706); border-radius: var(--radius-md); padding: 1rem; background-color: rgba(217, 119, 6, 0.08);">
        <h3 style="font-size: 1rem; font-weight: 700; color: #b45309; margin-bottom: 0.5rem; display: flex; align-items: center; gap: 0.5rem;">
          <span>⚠️</span> <span>${t("conflict_title")} (${conflicts.length})</span>
        </h3>
        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          ${conflicts.map(c => `
            <div class="card" style="margin: 0; padding: 0.75rem 1rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
              <div>
                <strong>${c.partyName || "Record"}</strong>: ₹${((c.remainingAmountPaise || 0) / 100).toFixed(0)}
                <div style="font-size: 0.8rem; color: var(--color-text-muted);">
                  ${c.conflictDetails?.reason || "Concurrent modifications detected"}
                </div>
              </div>
              <div style="display: flex; gap: 0.5rem;">
                <button type="button" class="btn btn-secondary resolve-keep-btn" data-sync-id="${c.syncId}" style="width: auto; padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                  ${t("conflict_keep_mine")}
                </button>
                <button type="button" class="btn btn-primary resolve-accept-btn" data-sync-id="${c.syncId}" style="width: auto; padding: 0.35rem 0.75rem; font-size: 0.8rem;">
                  ${t("conflict_accept_server")}
                </button>
              </div>
            </div>
          `).join("")}
        </div>
      </section>
    `;
  }

  _renderRecordsList() {
    return `
      <div style="display: flex; flex-direction: column; gap: 0.75rem;">
        ${this.records.map(r => {
          const remainingPaise = r.remainingAmountPaise !== undefined ? r.remainingAmountPaise : r.principalAmountPaise;
          const isLent = r.counterpartyRole === "BORROWER";
          const amountFormatted = `₹${((remainingPaise || 0) / 100).toLocaleString("en-IN")}`;
          const badgeColor = r.syncStatus === "SYNCED" ? "#059669" : r.syncStatus === "CONFLICT" ? "#d97706" : "#64748b";

          return `
            <div class="card" style="margin: 0; padding: 1rem; display: flex; justify-content: space-between; align-items: center;" data-sync-id="${r.syncId}">
              <div>
                <div style="font-weight: 700; font-size: 1rem;">${r.partyName || "Unknown"}</div>
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-top: 0.25rem; font-size: 0.8rem;">
                  <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background-color: ${badgeColor};"></span>
                  <span style="color: var(--color-text-muted);">${r.syncStatus || "LOCAL_ONLY"}</span>
                  ${r.status === "SETTLED" ? `<span style="color: #059669; font-weight: 600;">• SETTLED</span>` : ""}
                </div>
              </div>
              <div style="text-align: right;">
                <div style="font-weight: 700; font-size: 1.15rem; color: ${isLent ? "var(--color-primary)" : "var(--color-danger)"};">
                  ${amountFormatted}
                </div>
                <div style="display: flex; gap: 0.5rem; margin-top: 0.5rem; justify-content: flex-end; align-items: center;">
                  <button type="button" class="btn-link edit-record-btn" data-sync-id="${r.syncId}" style="font-size: 0.8rem;">Edit</button>
                  <button type="button" class="btn-link calendar-record-btn" data-sync-id="${r.syncId}" style="font-size: 0.8rem; color: #3b82f6;" title="Add to Google Calendar">📅 Calendar</button>
                  <button type="button" class="btn-link settle-record-btn" data-sync-id="${r.syncId}" style="font-size: 0.8rem; color: var(--color-primary);">Settle</button>
                </div>
              </div>
            </div>
          `;
        }).join("")}
      </div>
    `;
  }

  _bindEvents() {
    const addBtn = this.container.querySelector("#add-record-btn");
    const modal = /** @type {HTMLElement|null} */ (this.container.querySelector("#add-record-modal"));
    const cancelBtn = this.container.querySelector("#cancel-create-btn");
    const createForm = this.container.querySelector("#create-record-form");

    if (addBtn && modal) {
      addBtn.addEventListener("click", () => {
        modal.style.display = "flex";
        const partyInput = /** @type {HTMLInputElement|null} */ (this.container.querySelector("#record-party-name"));
        if (partyInput) partyInput.focus();
      });
    }

    if (cancelBtn && modal) {
      cancelBtn.addEventListener("click", () => {
        modal.style.display = "none";
      });
    }

    if (createForm) {
      createForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const partyInput = /** @type {HTMLInputElement|null} */ (this.container.querySelector("#record-party-name"));
        const amountInput = /** @type {HTMLInputElement|null} */ (this.container.querySelector("#record-amount"));
        const roleInput = /** @type {HTMLSelectElement|null} */ (this.container.querySelector("#record-role"));

        const partyName = partyInput ? partyInput.value.trim() : "";
        const amountRs = parseFloat(amountInput ? amountInput.value : "0");
        const role = roleInput ? roleInput.value : "BORROWER";

        if (!partyName || isNaN(amountRs) || amountRs <= 0) {
          this.onToast("Please enter a valid party name and amount.");
          return;
        }

        const amountPaise = Math.round(amountRs * 100);
        await syncEngine.createRecord({
          partyName,
          principalAmountPaise: amountPaise,
          remainingAmountPaise: amountPaise,
          counterpartyRole: role,
          status: "PENDING"
        });

        if (modal) modal.style.display = "none";
        this.onToast(`Saved record for ${partyName} (saved offline)`);
        await this.render(this.container);
      });
    }

    // Conflict Keep Mine
    this.container.querySelectorAll(".resolve-keep-btn").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        const syncId = e.currentTarget.getAttribute("data-sync-id");
        const rec = await ledgerDb.getRecord(syncId);
        if (rec) {
          rec.syncStatus = "LOCAL_ONLY";
          rec.clientRev = (rec.clientRev || 1) + 1;
          delete rec.conflictDetails;
          await ledgerDb.putRecord(rec);
          await ledgerDb.enqueueMutation({
            entity: "record",
            syncId: rec.syncId,
            op: "UPDATE",
            fields: rec,
            clientRev: rec.clientRev,
            hlc: rec.hlc,
            idempotencyKey: `idem_resolve_${Date.now()}`
          });
          this.onToast("Kept your local version");
          await this.render(this.container);
          syncEngine.sync().catch(() => {});
        }
      });
    });

    // Conflict Accept Server
    this.container.querySelectorAll(".resolve-accept-btn").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        const syncId = e.currentTarget.getAttribute("data-sync-id");
        const rec = await ledgerDb.getRecord(syncId);
        if (rec && rec.conflictDetails?.serverRecord) {
          const serverFields = rec.conflictDetails.serverRecord;
          const merged = { ...rec, ...serverFields, syncStatus: "SYNCED" };
          delete merged.conflictDetails;
          await ledgerDb.putRecord(merged);
          this.onToast("Accepted server version");
          await this.render(this.container);
        }
      });
    });

    // Calendar Reminder Button
    this.container.querySelectorAll(".calendar-record-btn").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        const syncId = e.currentTarget.getAttribute("data-sync-id");
        const rec = await ledgerDb.getRecord(syncId);
        if (rec) {
          const remainingPaise = rec.remainingAmountPaise !== undefined ? rec.remainingAmountPaise : rec.principalAmountPaise;
          const isLent = rec.counterpartyRole === "BORROWER";
          const calUrl = buildGoogleCalendarUrl({
            title: `Udhaar: ${isLent ? "Collect from" : "Repay"} ${rec.partyName}`,
            description: `Udhaar repayment of ₹${((remainingPaise || 0) / 100).toFixed(0)}. Manage at https://udhaar.jyotirmoyb.com`,
            dueDateMs: rec.dueDateMs || Date.now() + 7 * 24 * 60 * 60 * 1000
          });
          if (calUrl && typeof window !== "undefined") {
            window.open(calUrl, "_blank", "noopener,noreferrer");
            this.onToast("Opened Google Calendar reminder template");
          }
        }
      });
    });

    // Settle Record
    this.container.querySelectorAll(".settle-record-btn").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        const syncId = e.currentTarget.getAttribute("data-sync-id");
        const rec = await ledgerDb.getRecord(syncId);
        if (rec) {
          const remaining = rec.remainingAmountPaise || rec.principalAmountPaise;
          if (remaining > 0) {
            await syncEngine.createPayment({
              recordSyncId: syncId,
              amountPaise: remaining,
              paymentMode: "CASH",
              note: "Full settlement"
            });
            this.onToast(`Record for ${rec.partyName} marked as SETTLED`);
            await this.render(this.container);
          }
        }
      });
    });

    // Edit Record
    this.container.querySelectorAll(".edit-record-btn").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        const syncId = e.currentTarget.getAttribute("data-sync-id");
        const rec = await ledgerDb.getRecord(syncId);
        if (rec) {
          const newName = prompt("Edit Party Name:", rec.partyName);
          if (newName && newName.trim() && newName.trim() !== rec.partyName) {
            await syncEngine.updateRecord(syncId, { partyName: newName.trim() });
            this.onToast(`Updated record to ${newName.trim()}`);
            await this.render(this.container);
          }
        }
      });
    });
  }

  destroy() {}
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { DashboardView };
}
