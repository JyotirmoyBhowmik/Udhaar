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

  isSparkMode() {
    if (this.authClient && typeof this.authClient.isSparkMode === "function") {
      return this.authClient.isSparkMode();
    }
    if (typeof window !== "undefined" && window.SITE_CONFIG && typeof window.SITE_CONFIG.isSparkTier === "function") {
      return window.SITE_CONFIG.isSparkTier();
    }
    const envTier = typeof process !== "undefined" && process.env ? process.env.UDHAAR_TIER : "spark";
    return (envTier || "spark").toLowerCase() === "spark";
  }

  async render(container) {
    this.container = container;
    const t = this.i18n.t;
    const currentLoc = this.i18n.getLocale();

    let user = null;
    let devices = [];
    let sessionInfo = null;

    if (this.isSparkMode() && this.authClient.firebaseAuth) {
      sessionInfo = this.authClient.firebaseAuth.getDeviceSessionInfo();
      user = sessionInfo ? {
        displayName: sessionInfo.displayName || "User",
        email: sessionInfo.email,
        emailVerified: sessionInfo.emailVerified,
        maskedEmail: sessionInfo.email,
        maskedPhone: "+91 ••••• ••••"
      } : null;
    } else {
      try {
        const me = await this.authClient.getMe();
        user = me.user || me;
        devices = me.devices || [];
      } catch (_) {
        // Offline or mock
      }
    }

    const emailVerifiedBadge = user?.emailVerified
      ? `<span class="badge-verified" style="margin-left: 0.5rem;">Verified</span>`
      : `<span class="badge-unverified" style="margin-left: 0.5rem;">Unverified — Sync disabled</span>`;

    this.container.innerHTML = `
      <div style="max-width: 600px; margin: 0 auto; display: flex; flex-direction: column; gap: 1.5rem;">
        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("profile_heading")}
          </h2>
          <div style="display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.95rem;">
            <div><strong>${t("display_name_label")}:</strong> ${user?.displayName || "User"}</div>
            <div><strong>${t("email_label")}:</strong> ${user?.email || user?.maskedEmail || "•••••@example.com"} ${emailVerifiedBadge}</div>
            <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
              <strong>${t("phone_label")}:</strong> ${user?.maskedPhone || "+91 ••••• ••••"}
              <span class="badge-unverified">Number not verified</span>
            </div>
            ${!user?.emailVerified ? `
              <div style="margin-top: 0.5rem;">
                <button type="button" id="settings-resend-verify-btn" class="btn btn-secondary btn-sm" style="font-size: 0.8125rem;">
                  Resend verification email
                </button>
              </div>
            ` : ""}
          </div>
        </div>

        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            Security &amp; Active Devices
          </h2>
          <div style="display: flex; flex-direction: column; gap: 0.75rem; font-size: 0.875rem;">
            <div><strong>Active Session:</strong> Current Browser (${navigator.userAgent ? navigator.userAgent.slice(0, 40) + "..." : "Web Client"})</div>
            <div class="auth-notice-box" style="margin: 0.25rem 0;">
              <strong>Device Session Truth:</strong> Changing your password revokes refresh tokens on other devices.
              Please note that active access tokens already issued may remain valid for up to approximately 1 hour before expiring.
            </div>
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

    const resendVerifyBtn = this.container.querySelector("#settings-resend-verify-btn");
    if (resendVerifyBtn) {
      resendVerifyBtn.addEventListener("click", async () => {
        try {
          resendVerifyBtn.disabled = true;
          await this.authClient.firebaseAuth.sendVerificationEmail();
          this.onToast({ message: "Verification email sent! Check your inbox.", type: "success" });
        } catch (err) {
          this.onToast({ message: err.message, type: "error" });
          resendVerifyBtn.disabled = false;
        }
      });
    }

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
