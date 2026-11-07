/**
 * Udhaar Web App Shell Controller (webapp/src/app/AppShell.js)
 * Coordinates layout, internationalization, routing, theme, PWA service worker,
 * and toast announcements.
 */

const { initLocale, setLocale, getLocale, t } = require("../i18n");
const { authClient } = require("../api/authClient");
const { Router } = require("../routes/router");
const { SignUpView } = require("../features/auth/SignUpView");
const { SignInView } = require("../features/auth/SignInView");
const { DashboardView } = require("../features/records/DashboardView");
const { SettingsView } = require("../features/settings/SettingsView");
const { RemindersView } = require("../features/reminders/RemindersView");
const { ChatView } = require("../features/chat/ChatView");
const { InboxView } = require("../features/inbox/InboxView");
const { ExportsView } = require("../features/exports/ExportsView");

class AppShell {
  constructor(rootEl) {
    this.root = rootEl;
    this.router = new Router();
    this.i18n = { initLocale, setLocale, getLocale, t };
    this.authClient = authClient;
  }

  init() {
    this.i18n.initLocale();
    this._initTheme();
    this._renderShell();
    this._setupRoutes();
    this._setupNetworkListeners();
    this._registerServiceWorker();
    this.router.start();
  }

  _initTheme() {
    if (typeof window !== "undefined") {
      const savedTheme = localStorage.getItem("udhaar_theme") || "light";
      document.documentElement.setAttribute("data-theme", savedTheme);
    }
  }

  _toggleTheme() {
    const current = document.documentElement.getAttribute("data-theme") || "light";
    const next = current === "light" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", next);
    if (typeof window !== "undefined") {
      localStorage.setItem("udhaar_theme", next);
    }
    const themeBtn = this.root.querySelector("#theme-btn");
    if (themeBtn) {
      themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
    }
  }

  _renderShell() {
    const currentLoc = this.i18n.getLocale();
    const currentTheme = (typeof document !== "undefined" && document.documentElement.getAttribute("data-theme")) || "light";

    this.root.innerHTML = `
      <header class="app-header">
        <a href="#dashboard" class="brand-wrap" aria-label="Udhaar Home">
          <svg class="brand-logo" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <rect width="48" height="48" rx="12" fill="#059669"/>
            <path d="M14 24H34M24 14V34" stroke="white" stroke-width="4.5" stroke-linecap="round"/>
          </svg>
          <span class="brand-name">${this.i18n.t("app_name")}</span>
        </a>

        <div class="header-actions">
          <select id="shell-lang-select" class="lang-select" aria-label="${this.i18n.t("language_switcher")}">
            <option value="en" ${currentLoc === "en" ? "selected" : ""}>EN</option>
            <option value="hi" ${currentLoc === "hi" ? "selected" : ""}>हिन्दी</option>
            <option value="bn" ${currentLoc === "bn" ? "selected" : ""}>বাংলা</option>
            <option value="as" ${currentLoc === "as" ? "selected" : ""}>অসমীয়া</option>
            <option value="ne" ${currentLoc === "ne" ? "selected" : ""}>नेपाली</option>
          </select>

          <button type="button" id="theme-btn" class="theme-btn" aria-label="Toggle Theme">
            ${currentTheme === "dark" ? "☀️" : "🌙"}
          </button>
        </div>
      </header>

      <div id="offline-banner" class="offline-banner" role="status" aria-live="polite">
        <span aria-hidden="true">⚠️</span>
        <span>${this.i18n.t("offline_notice")}</span>
      </div>

      <main class="app-main">
        <div id="view-container"></div>
      </main>

      <nav id="bottom-nav" class="bottom-nav" aria-label="Main Navigation" style="display: none;">
        <a href="#dashboard" class="nav-item" data-route="#dashboard">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
          <span>${this.i18n.t("nav_records")}</span>
        </a>
        <a href="#inbox" class="nav-item" data-route="#inbox">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
          <span>Inbox</span>
        </a>
        <a href="#reminders" class="nav-item" data-route="#reminders">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span>${this.i18n.t("nav_reminders")}</span>
        </a>
        <a href="#settings" class="nav-item" data-route="#settings">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          <span>${this.i18n.t("nav_settings")}</span>
        </a>
      </nav>

      <div id="toast-container" class="toast-container" role="status" aria-live="polite"></div>
    `;

    // Listeners
    const langSelect = this.root.querySelector("#shell-lang-select");
    langSelect.addEventListener("change", (e) => {
      this.i18n.setLocale(e.target.value);
      this._renderShell();
      this.router.start();
    });

    const themeBtn = this.root.querySelector("#theme-btn");
    themeBtn.addEventListener("click", () => {
      this._toggleTheme();
    });
  }

  _setupRoutes() {
    const viewContainer = this.root.querySelector("#view-container");
    const bottomNav = this.root.querySelector("#bottom-nav");

    const onToast = (msg) => this.showToast(msg);
    const onLocaleChange = (loc) => {
      this.i18n.setLocale(loc);
      this._renderShell();
      this.router.start();
    };

    this.router.addRoute("#signup", () => {
      if (bottomNav) bottomNav.style.display = "none";
      const view = new SignUpView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#signin", () => {
      if (bottomNav) bottomNav.style.display = "none";
      const view = new SignInView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#dashboard", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#dashboard");
      const view = new DashboardView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#settings", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#settings");
      const view = new SettingsView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast,
        onLocaleChange
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#reminders", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#reminders");
      const view = new RemindersView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#inbox", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#inbox");
      const view = new InboxView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#chat", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#chat");
      const view = new ChatView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#exports", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#exports");
      const view = new ExportsView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#people", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#people");
      const view = new DashboardView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.onRouteChanged = (route) => {
      this._updateNavHighlight(route);
    };
  }

  _updateNavHighlight(route) {
    const items = this.root.querySelectorAll(".nav-item");
    items.forEach((item) => {
      if (item.getAttribute("data-route") === route) {
        item.classList.add("active");
        item.setAttribute("aria-current", "page");
      } else {
        item.classList.remove("active");
        item.removeAttribute("aria-current");
      }
    });
  }

  _setupNetworkListeners() {
    const banner = this.root.querySelector("#offline-banner");
    if (!banner) return;

    const updateStatus = () => {
      const isOnline = typeof navigator !== "undefined" ? navigator.onLine : true;
      if (!isOnline) {
        banner.classList.add("active");
      } else {
        banner.classList.remove("active");
      }
    };

    if (typeof window !== "undefined") {
      window.addEventListener("online", updateStatus);
      window.addEventListener("offline", updateStatus);
      updateStatus();
    }
  }

  _registerServiceWorker() {
    if (typeof window !== "undefined" && "serviceWorker" in navigator && location.protocol === "https:") {
      navigator.serviceWorker.register("/sw.js").catch((err) => {
        console.warn("[PWA] Service Worker registration failed:", err);
      });
    }
  }

  showToast(message, durationMs = 4000) {
    const container = this.root.querySelector("#toast-container");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = "toast";
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, durationMs);
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { AppShell };
}
