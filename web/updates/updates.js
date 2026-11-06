/**
 * Updates / Changelog Page Script
 * Reads web/updates.json and renders in 5 languages.
 */
(function () {
  "use strict";

  let updatesData = [];

  function getLang() {
    return (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_lang")) || "en";
  }

  function setLang(lang) {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem("udhaar_lang", lang);
    }
    renderChangelog(lang);
  }

  function renderChangelog(lang) {
    const container = document.getElementById("changelog-container");
    if (!container) return;

    if (!updatesData || updatesData.length === 0) {
      container.innerHTML = "<p>Loading updates...</p>";
      return;
    }

    container.innerHTML = "";
    updatesData.forEach((item) => {
      const card = document.createElement("article");
      card.className = "changelog-card";

      const title = (item.title && item.title[lang]) || (item.title && item.title.en) || `v${item.version}`;
      const highlights = (item.highlights && item.highlights[lang]) || (item.highlights && item.highlights.en) || [];

      const h2 = document.createElement("h2");
      h2.textContent = title;
      card.appendChild(h2);

      const dateP = document.createElement("p");
      dateP.className = "changelog-date";
      dateP.textContent = item.date;
      card.appendChild(dateP);

      const ul = document.createElement("ul");
      highlights.forEach((point) => {
        const li = document.createElement("li");
        li.textContent = point;
        ul.appendChild(li);
      });
      card.appendChild(ul);

      container.appendChild(card);
    });

    // Update select element value
    const select = document.getElementById("updates-lang-select");
    if (select) select.value = lang;
  }

  async function loadUpdates() {
    try {
      const res = await fetch("/updates.json");
      if (res.ok) {
        updatesData = await res.json();
        renderChangelog(getLang());
      }
    } catch (e) {
      console.warn("Could not fetch updates.json", e);
    }
  }

  function initUpdates() {
    // Theme toggle
    const themeBtn = document.getElementById("theme-btn");
    const savedTheme = (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_theme")) || "system";
    if (savedTheme !== "system") {
      document.documentElement.setAttribute("data-theme", savedTheme);
    }
    if (themeBtn) {
      themeBtn.addEventListener("click", () => {
        const cur = document.documentElement.getAttribute("data-theme") || "light";
        const next = cur === "dark" ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", next);
        if (typeof localStorage !== "undefined") {
          localStorage.setItem("udhaar_theme", next);
        }
        themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
      });
    }

    // Language selector
    const langSelect = document.getElementById("updates-lang-select");
    if (langSelect) {
      langSelect.value = getLang();
      langSelect.addEventListener("change", (e) => setLang(e.target.value));
    }

    // Play store early access link sync
    if (window.SITE_CONFIG && !window.SITE_CONFIG.PLAY_STORE_LIVE) {
      const playBtn = document.getElementById("header-play-btn");
      if (playBtn) {
        playBtn.href = window.SITE_CONFIG.EARLY_ACCESS_URL;
        playBtn.textContent = "Join early access";
      }
    }

    loadUpdates();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initUpdates);
  } else {
    initUpdates();
  }
})();
