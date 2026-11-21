/**
 * Udhaar Shared Mobile Navigation Drawer (web/nav-drawer.js)
 * Lightweight, CSP-safe drawer controller for subpages (Trust, Updates, Privacy, Terms, Delete Account).
 */
(function () {
  "use strict";

  function initDrawer() {
    const mobileMenuBtn = document.getElementById("mobile-menu-btn");
    const mobileDrawer = document.getElementById("mobile-drawer");
    const mobileBackdrop = document.getElementById("mobile-drawer-backdrop");
    const drawerCloseBtn = document.getElementById("drawer-close-btn");

    if (!mobileMenuBtn || !mobileDrawer || !mobileBackdrop) return;

    function openDrawer() {
      mobileDrawer.classList.add("open");
      mobileDrawer.removeAttribute("inert");
      mobileBackdrop.classList.add("active");
      mobileMenuBtn.setAttribute("aria-expanded", "true");
      if (drawerCloseBtn) drawerCloseBtn.focus();
    }

    function closeDrawer() {
      mobileDrawer.classList.remove("open");
      mobileDrawer.setAttribute("inert", "");
      mobileBackdrop.classList.remove("active");
      mobileMenuBtn.setAttribute("aria-expanded", "false");
    }

    mobileMenuBtn.addEventListener("click", openDrawer);
    if (drawerCloseBtn) drawerCloseBtn.addEventListener("click", closeDrawer);
    mobileBackdrop.addEventListener("click", closeDrawer);

    mobileDrawer.querySelectorAll("a.drawer-link").forEach(function (link) {
      link.addEventListener("click", closeDrawer);
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && mobileDrawer.classList.contains("open")) {
        closeDrawer();
        mobileMenuBtn.focus();
      }
    });

    // Keep drawer CTA synced with SITE_CONFIG (early access vs Play Store live)
    const drawerCta = mobileDrawer.querySelector("[data-store-button='drawer']");
    if (drawerCta && typeof window.SITE_CONFIG === "object" && window.SITE_CONFIG) {
      const cfg = window.SITE_CONFIG;
      drawerCta.href = cfg.PLAY_STORE_LIVE ? cfg.PLAY_STORE_URL : cfg.CLOSED_TESTING_URL;
      drawerCta.textContent = cfg.PLAY_STORE_LIVE ? "Get on Google Play" : "Join early access";
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initDrawer);
  } else {
    initDrawer();
  }
})();
