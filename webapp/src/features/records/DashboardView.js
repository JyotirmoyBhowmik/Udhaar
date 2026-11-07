/**
 * Udhaar Web App Dashboard View (webapp/src/features/records/DashboardView.js)
 * Implements Overview KPI cards, empty state, and ledger layout shell.
 */

class DashboardView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.user = null;
  }

  async render(container) {
    this.container = container;
    const t = this.i18n.t;

    this.container.innerHTML = `
      <section aria-label="Account Overview" class="dashboard-grid">
        <div class="kpi-card">
          <div class="kpi-title">${t("net_balance")}</div>
          <div class="kpi-amount" style="color: var(--color-primary);">₹0</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_get")}</div>
          <div class="kpi-amount lent">₹0</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_give")}</div>
          <div class="kpi-amount borrowed">₹0</div>
        </div>
      </section>

      <section class="card" style="text-align: center; padding: 3rem 1.5rem; margin-top: 1rem;">
        <div style="font-size: 3rem; margin-bottom: 0.75rem;">📖</div>
        <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 0.5rem;">
          ${t("no_records_title")}
        </h2>
        <p style="font-size: 0.95rem; color: var(--color-text-muted); max-width: 420px; margin: 0 auto 1.5rem auto;">
          ${t("no_records_desc")}
        </p>
        <button type="button" id="add-record-btn" class="btn btn-primary" style="max-width: 240px; margin: 0 auto;">
          ${t("add_first_record")}
        </button>
      </section>
    `;

    const addBtn = this.container.querySelector("#add-record-btn");
    if (addBtn) {
      addBtn.addEventListener("click", () => {
        this.onToast("Create record feature is ready in Stage S3!");
      });
    }
  }

  destroy() {}
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { DashboardView };
}
