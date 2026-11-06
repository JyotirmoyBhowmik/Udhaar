/* =========================================================
   UDHAAR COMPLETE APP GUIDE SCRIPT (/web/guide/guide.js)
   Handles instant search, category filtering, active TOC
   highlighting, and dark/light theme toggle.
   ========================================================= */
(function () {
  'use strict';

  // Theme Toggle
  const themeBtn = document.getElementById('theme-btn');
  const rootEl = document.documentElement;
  const savedTheme = localStorage.getItem('udhaar_theme') || 'system';
  rootEl.setAttribute('data-theme', savedTheme);

  function updateThemeIcon() {
    if (!themeBtn) return;
    const current = rootEl.getAttribute('data-theme');
    themeBtn.textContent = current === 'dark' ? '☀️' : '🌙';
  }
  updateThemeIcon();

  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      const current = rootEl.getAttribute('data-theme');
      const next = current === 'dark' ? 'light' : 'dark';
      rootEl.setAttribute('data-theme', next);
      localStorage.setItem('udhaar_theme', next);
      updateThemeIcon();
    });
  }

  // Mobile Menu Drawer
  const mobileMenuBtn = document.getElementById('mobile-menu-btn');
  const mobileDrawer = document.getElementById('mobile-drawer');
  const mobileBackdrop = document.getElementById('mobile-drawer-backdrop');
  const drawerCloseBtn = document.getElementById('drawer-close-btn');

  function openDrawer() {
    if (!mobileDrawer || !mobileBackdrop) return;
    mobileDrawer.classList.add('open');
    mobileDrawer.removeAttribute('inert');
    mobileBackdrop.classList.add('active');
    if (mobileMenuBtn) mobileMenuBtn.setAttribute('aria-expanded', 'true');
  }

  function closeDrawer() {
    if (!mobileDrawer || !mobileBackdrop) return;
    mobileDrawer.classList.remove('open');
    mobileDrawer.setAttribute('inert', '');
    mobileBackdrop.classList.remove('active');
    if (mobileMenuBtn) mobileMenuBtn.setAttribute('aria-expanded', 'false');
  }

  if (mobileMenuBtn) mobileMenuBtn.addEventListener('click', openDrawer);
  if (drawerCloseBtn) drawerCloseBtn.addEventListener('click', closeDrawer);
  if (mobileBackdrop) mobileBackdrop.addEventListener('click', closeDrawer);

  // Instant Chapter Search & Category Filter
  const searchInput = document.getElementById('guide-search-input');
  const filterBtns = Array.from(document.querySelectorAll('.guide-filter-btn'));
  const chapters = Array.from(document.querySelectorAll('.guide-chapter-card'));
  const emptyState = document.getElementById('guide-empty-state');
  let activeCategory = 'all';

  function applyFilters() {
    const query = (searchInput ? searchInput.value : '').trim().toLowerCase();
    let visibleCount = 0;

    chapters.forEach(function (card) {
      const cat = (card.getAttribute('data-guide-cat') || '').toLowerCase();
      const text = (card.textContent || '').toLowerCase();
      const matchesCat = activeCategory === 'all' || cat.includes(activeCategory);
      const matchesQuery = !query || text.includes(query);

      if (matchesCat && matchesQuery) {
        card.style.display = '';
        visibleCount++;
      } else {
        card.style.display = 'none';
      }
    });

    if (emptyState) {
      emptyState.style.display = visibleCount === 0 ? 'block' : 'none';
    }
  }

  if (searchInput) {
    searchInput.addEventListener('input', applyFilters);
  }

  filterBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      filterBtns.forEach(function (b) {
        b.classList.remove('active');
      });
      btn.classList.add('active');
      activeCategory = btn.getAttribute('data-filter') || 'all';
      applyFilters();
    });
  });

  // Scroll Spy for Sticky Sidebar TOC
  const tocLinks = Array.from(document.querySelectorAll('.guide-toc-list a'));
  if ('IntersectionObserver' in window && tocLinks.length > 0 && chapters.length > 0) {
    const observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting && entry.target.id) {
            const activeHref = '#' + entry.target.id;
            tocLinks.forEach(function (link) {
              if (link.getAttribute('href') === activeHref) {
                link.classList.add('active');
              } else {
                link.classList.remove('active');
              }
            });
          }
        });
      },
      { rootMargin: '-100px 0px -65% 0px', threshold: 0.05 }
    );

    chapters.forEach(function (ch) {
      observer.observe(ch);
    });
  }
})();
