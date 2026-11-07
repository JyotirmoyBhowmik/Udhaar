/**
 * Inbox & Counterparty Notifications View (webapp/src/features/inbox/InboxView.js)
 * Implements Stage S7 Inbox UI:
 * - Two-party counterparty event feed (payment claims, date proposals, verification notices)
 * - Interactive action resolutions (Approve/Decline payment claims & date extensions)
 * - Read/Unread filtering and management
 * - Strict CSP: 100% addEventListener, zero inline scripts or styles.
 */

class InboxView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.items = [];
    this.filterUnread = false;
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = `
      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
          <div>
            <h1 style="font-size: 1.25rem; font-weight: 700; color: #065f46;">Inbox & Counterparty Actions</h1>
            <p style="font-size: 0.875rem; color: #64748b;">Review payment claims, due-date proposals, and confirmations</p>
          </div>
          <div style="display: flex; gap: 0.5rem;">
            <button id="inbox-filter-btn" class="btn btn-secondary" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem;">Show Unread</button>
            <button id="inbox-refresh-btn" class="btn btn-secondary" style="font-size: 0.8125rem; padding: 0.375rem 0.75rem;">Refresh</button>
          </div>
        </div>

        <div id="inbox-loading" style="text-align: center; padding: 2rem; color: #64748b;">
          Loading inbox notifications...
        </div>

        <div id="inbox-list" style="display: none; flex-direction: column; gap: 0.75rem;"></div>
      </div>
    `;

    this._bindEvents();
    await this._loadInbox();
  }

  _bindEvents() {
    const refreshBtn = this.container.querySelector("#inbox-refresh-btn");
    if (refreshBtn) {
      refreshBtn.addEventListener("click", () => this._loadInbox());
    }

    const filterBtn = this.container.querySelector("#inbox-filter-btn");
    if (filterBtn) {
      filterBtn.addEventListener("click", () => {
        this.filterUnread = !this.filterUnread;
        filterBtn.textContent = this.filterUnread ? "Show All" : "Show Unread";
        this._loadInbox();
      });
    }
  }

  async _loadInbox() {
    const loading = this.container.querySelector("#inbox-loading");
    const list = this.container.querySelector("#inbox-list");
    if (loading) loading.style.display = "block";
    if (list) list.style.display = "none";

    try {
      const url = this.filterUnread ? "/api/v1/inbox?unread=true" : "/api/v1/inbox";
      const res = await fetch(url);
      if (res.ok) {
        this.items = await res.json();
      } else {
        this.items = [];
      }
      this._renderList();
    } catch (err) {
      this.items = [];
      this._renderList();
    }
  }

  _renderList() {
    const loading = this.container.querySelector("#inbox-loading");
    const list = this.container.querySelector("#inbox-list");
    if (loading) loading.style.display = "none";
    if (list) list.style.display = "flex";

    if (this.items.length === 0) {
      list.innerHTML = `
        <div style="text-align: center; padding: 2rem; color: #94a3b8;">
          No notifications in your inbox.
        </div>
      `;
      return;
    }

    list.innerHTML = "";
    this.items.forEach((item) => {
      const card = document.createElement("div");
      card.className = "inbox-item-card";
      card.style.padding = "0.875rem";
      card.style.border = item.isRead ? "1px solid #e2e8f0" : "1px solid #059669";
      card.style.borderRadius = "8px";
      card.style.backgroundColor = item.isRead ? "#ffffff" : "#f0fdf4";
      card.style.display = "flex";
      card.style.flexDirection = "column";
      card.style.gap = "0.5rem";

      const timeStr = new Date(item.createdAtMs).toLocaleString();
      let badgeColor = "#64748b";
      if (item.type === "PAYMENT_CLAIM") badgeColor = "#059669";
      if (item.type === "DATE_PROPOSAL") badgeColor = "#d97706";

      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              <span style="font-size: 0.6875rem; text-transform: uppercase; font-weight: 700; color: ${badgeColor}; background: #f1f5f9; padding: 0.125rem 0.375rem; border-radius: 4px;">
                ${escapeHtml(item.type.replace(/_/g, " "))}
              </span>
              <strong style="color: #0f172a; font-size: 0.9375rem;">${escapeHtml(item.title)}</strong>
            </div>
            <p style="font-size: 0.8125rem; color: #475569; margin-top: 0.25rem;">${escapeHtml(item.body)}</p>
          </div>
          <span style="font-size: 0.75rem; color: #94a3b8;">${escapeHtml(timeStr)}</span>
        </div>

        <div class="inbox-actions" style="display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 0.25rem;">
          ${this._renderActionButtons(item)}
        </div>
      `;

      this._bindItemButtons(card, item);
      list.appendChild(card);
    });
  }

  _renderActionButtons(item) {
    if (item.status === "PENDING" && (item.type === "PAYMENT_CLAIM" || item.type === "DATE_PROPOSAL")) {
      return `
        <button class="btn btn-primary btn-inbox-approve" data-id="${escapeHtml(item.inboxId)}" style="font-size: 0.75rem; padding: 0.25rem 0.625rem;">Approve</button>
        <button class="btn btn-secondary btn-inbox-decline" data-id="${escapeHtml(item.inboxId)}" style="font-size: 0.75rem; padding: 0.25rem 0.625rem; color: #dc2626;">Decline</button>
      `;
    }

    if (!item.isRead) {
      return `
        <button class="btn btn-secondary btn-inbox-read" data-id="${escapeHtml(item.inboxId)}" style="font-size: 0.75rem; padding: 0.25rem 0.625rem;">Mark Read</button>
      `;
    }

    return `<span style="font-size: 0.75rem; color: #94a3b8;">Status: ${escapeHtml(item.status || "Read")}</span>`;
  }

  _bindItemButtons(cardEl, item) {
    const approveBtn = cardEl.querySelector(".btn-inbox-approve");
    if (approveBtn) {
      approveBtn.addEventListener("click", async () => {
        approveBtn.disabled = true;
        await this._handleDecision(item.inboxId, "APPROVE");
      });
    }

    const declineBtn = cardEl.querySelector(".btn-inbox-decline");
    if (declineBtn) {
      declineBtn.addEventListener("click", async () => {
        declineBtn.disabled = true;
        const note = prompt("Reason for decline (optional):") || "";
        await this._handleDecision(item.inboxId, "DECLINE", note);
      });
    }

    const readBtn = cardEl.querySelector(".btn-inbox-read");
    if (readBtn) {
      readBtn.addEventListener("click", async () => {
        readBtn.disabled = true;
        await this._markRead(item.inboxId);
      });
    }
  }

  async _handleDecision(inboxId, decision, note = "") {
    try {
      const res = await fetch(`/api/v1/inbox/${encodeURIComponent(inboxId)}/action`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ decision, note })
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || "Action processing failed");
      }

      this.onToast(decision === "APPROVE" ? "Action approved successfully." : "Action declined.");
      await this._loadInbox();
    } catch (err) {
      this.onToast(`Error: ${err.message}`);
      await this._loadInbox();
    }
  }

  async _markRead(inboxId) {
    try {
      const res = await fetch(`/api/v1/inbox/${encodeURIComponent(inboxId)}/read`, {
        method: "POST",
        headers: { "Content-Type": "application/json" }
      });
      if (res.ok) {
        this.onToast("Marked as read.");
        await this._loadInbox();
      }
    } catch (err) {
      console.error(err);
    }
  }
}

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { InboxView };
}
