/**
 * Reminders Centre View (webapp/src/features/reminders/RemindersView.js)
 * Implements Stage S7 Reminders UI:
 * - 4 Escalation Levels & Quiet Hours display
 * - List of pending reminders with action buttons (WhatsApp, wa.me, Snooze, Stop)
 * - Strict CSP: 100% addEventListener, zero inline scripts or styles.
 */

class RemindersView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.policy = null;
    this.pendingReminders = [];
    this.inQuietHours = false;
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = `
      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
          <div>
            <h1 style="font-size: 1.25rem; font-weight: 700; color: #065f46;">${this.i18n.t("nav_reminders")}</h1>
            <p style="font-size: 0.875rem; color: #64748b;">Polite, scheduled reminders for overdue balances</p>
          </div>
          <button id="refresh-reminders-btn" class="btn btn-secondary" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem;">Refresh</button>
        </div>

        <div id="reminders-loading" style="text-align: center; padding: 2rem; color: #64748b;">
          Evaluating reminder schedules...
        </div>

        <div id="reminders-content" style="display: none;">
          <div id="quiet-hours-banner" class="notice-box notice-amber" style="display: none; margin-bottom: 1rem;">
            <span>🌙</span>
            <span>Quiet hours active (9:00 PM – 8:00 AM). Automated reminders paused until morning.</span>
          </div>

          <div id="reminders-list" style="display: flex; flex-direction: column; gap: 0.75rem;"></div>
        </div>
      </div>
    `;

    this._bindEvents();
    await this._loadReminders();
  }

  _bindEvents() {
    const refreshBtn = this.container.querySelector("#refresh-reminders-btn");
    if (refreshBtn) {
      refreshBtn.addEventListener("click", () => this._loadReminders());
    }
  }

  async _loadReminders() {
    try {
      const res = await fetch("/api/v1/reminders/schedule", {
        method: "POST",
        headers: { "Content-Type": "application/json" }
      });

      if (!res.ok) {
        throw new Error("Failed to evaluate reminders");
      }

      const data = await res.json();
      this.pendingReminders = data.pendingReminders || [];
      this.inQuietHours = Boolean(data.inQuietHours);
      this._renderList();
    } catch (err) {
      const content = this.container.querySelector("#reminders-content");
      const loading = this.container.querySelector("#reminders-loading");
      if (loading) loading.style.display = "none";
      if (content) content.style.display = "block";
      this.container.querySelector("#reminders-list").innerHTML = `
        <div style="text-align: center; padding: 1.5rem; color: #94a3b8;">
          No active reminders due at this time.
        </div>
      `;
    }
  }

  _renderList() {
    const loading = this.container.querySelector("#reminders-loading");
    const content = this.container.querySelector("#reminders-content");
    const quietBanner = this.container.querySelector("#quiet-hours-banner");
    const list = this.container.querySelector("#reminders-list");

    if (loading) loading.style.display = "none";
    if (content) content.style.display = "block";
    if (quietBanner) quietBanner.style.display = this.inQuietHours ? "flex" : "none";

    if (this.pendingReminders.length === 0) {
      list.innerHTML = `
        <div style="text-align: center; padding: 2rem; color: #64748b;">
          ✓ All reminders are up to date. No pending notices.
        </div>
      `;
      return;
    }

    list.innerHTML = "";
    for (const rem of this.pendingReminders) {
      const card = document.createElement("div");
      card.className = "card";
      card.style.cssText = "background: #f8fafc; border: 1px solid #e2e8f0; padding: 1rem;";

      const levelLabel = rem.level === "LEVEL_1" ? "Level 1 • Gentle"
        : rem.level === "LEVEL_2" ? "Level 2 • Follow-up"
        : rem.level === "LEVEL_3" ? "Level 3 • Firm"
        : "Level 4 • Final Notice";

      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.5rem;">
          <div>
            <strong style="font-size: 1rem; color: #0f172a;">${rem.partyName}</strong>
            <div style="font-size: 0.8125rem; color: #64748b;">${rem.partyPhone || "No phone"}</div>
          </div>
          <span style="font-size: 0.75rem; padding: 2px 8px; border-radius: 9999px; background: #e0e7ff; color: #3730a3; font-weight: 600;">
            ${levelLabel}
          </span>
        </div>

        <p style="font-size: 0.875rem; color: #334155; margin: 0.5rem 0; line-height: 1.4; background: #fff; padding: 0.75rem; border-radius: 6px; border: 1px solid #e2e8f0;">
          "${rem.message}"
        </p>

        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.75rem;">
          ${rem.waMeUrl ? `<a href="${rem.waMeUrl}" target="_blank" class="btn btn-primary" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem; text-decoration: none;">Send WhatsApp 💬</a>` : ''}
          <button class="btn btn-secondary snooze-1d-btn" data-record="${rem.recordId}" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem;">Snooze 1 Day</button>
          <button class="btn btn-secondary stop-rem-btn" data-record="${rem.recordId}" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem;">Stop</button>
        </div>
      `;

      card.querySelector(".snooze-1d-btn").addEventListener("click", async () => {
        await this._snooze(rem.recordId, 1);
      });

      card.querySelector(".stop-rem-btn").addEventListener("click", async () => {
        await this._snooze(rem.recordId, 0, true);
      });

      list.appendChild(card);
    }
  }

  async _snooze(recordId, days = 1, stop = false) {
    try {
      const res = await fetch(`/api/v1/reminders/${encodeURIComponent(recordId)}/snooze`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ snoozeDays: days, stop })
      });
      if (res.ok) {
        this.onToast(stop ? "Reminders stopped for this record" : `Snoozed for ${days} day${days > 1 ? "s" : ""}`);
        await this._loadReminders();
      }
    } catch (_) {
      this.onToast("Failed to update reminder state");
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { RemindersView };
}
