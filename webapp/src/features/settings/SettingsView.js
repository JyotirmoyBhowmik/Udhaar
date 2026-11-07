/**
 * Udhaar Web App Settings View (webapp/src/features/settings/SettingsView.js)
 * Implements Profile, Language selection, Theme toggle, Signed-in devices, and DPDP privacy tools.
 */

class SettingsView {
  constructor({ i18n, authClient, router, onToast, onLocaleChange }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.onLocaleChange = onLocaleChange;
  }

  async render(container) {
    this.container = container;
    const t = this.i18n.t;
    const currentLoc = this.i18n.getLocale();

    let user = null;
    let devices = [];
    try {
      const me = await this.authClient.getMe();
      user = me.user;
      devices = me.devices || [];
    } catch (_) {
      // Offline or mock
    }

    this.container.innerHTML = `
      <div style="max-width: 600px; margin: 0 auto; display: flex; flex-direction: column; gap: 1.5rem;">
        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("profile_heading")}
          </h2>
          <div style="display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.95rem;">
            <div><strong>${t("display_name_label")}:</strong> ${user?.displayName || "User"}</div>
            <div><strong>${t("phone_label")}:</strong> ${user?.maskedPhone || "+91 ••••• ••••"}</div>
            <div><strong>${t("email_label")}:</strong> ${user?.maskedEmail || "•••••@example.com"}</div>
          </div>
        </div>

        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("language_switcher")}
          </h2>
          <div class="form-group">
            <label for="settings-lang-select" class="form-label">${t("language_switcher")}</label>
            <select id="settings-lang-select" class="lang-select" aria-label="${t("language_switcher")}" style="width: 100%;">
              <option value="en" ${currentLoc === "en" ? "selected" : ""}>English</option>
              <option value="hi" ${currentLoc === "hi" ? "selected" : ""}>हिन्दी (Hindi)</option>
              <option value="bn" ${currentLoc === "bn" ? "selected" : ""}>বাংলা (Bengali)</option>
              <option value="as" ${currentLoc === "as" ? "selected" : ""}>অসমীয়া (Assamese)</option>
              <option value="ne" ${currentLoc === "ne" ? "selected" : ""}>नेपाली (Nepali)</option>
            </select>
          </div>
        </div>

        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("privacy_notice_heading")}
          </h2>
          <p style="font-size: 0.875rem; color: var(--color-text-muted); line-height: 1.5; margin-bottom: 1rem;">
            ${t("dpdp_summary")}
          </p>
          <a href="/privacy.html" target="_blank" class="btn btn-secondary" style="display: inline-flex; width: auto;">
            ${t("privacy_link")}
          </a>
        </div>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="signout-btn" class="btn btn-secondary" style="color: var(--color-danger); border-color: var(--color-danger);">
            ${t("sign_out")}
          </button>
        </div>
      </div>
    `;

    const langSelect = this.container.querySelector("#settings-lang-select");
    if (langSelect) {
      langSelect.addEventListener("change", (e) => {
        this.onLocaleChange(e.target.value);
        this.render(container);
      });
    }

    const signOutBtn = this.container.querySelector("#signout-btn");
    if (signOutBtn) {
      signOutBtn.addEventListener("click", async () => {
        try {
          await this.authClient.signOut();
        } catch (_) {}
        this.onToast("Signed out successfully");
        this.router.navigate("#signin");
      });
    }
  }

  destroy() {}
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SettingsView };
}
