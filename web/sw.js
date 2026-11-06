// =========================================================================
// Udhaar Web Service Worker — Static Shell Only (v25.0)
// Security Rule: NEVER cache record data, secret tokens, or /api/* endpoints
// =========================================================================

const CACHE_NAME = "udhaar-shell-v25";
const SHELL_ASSETS = [
  "/u/",
  "/u/index.html",
  "/u/guest.css",
  "/u/guest.js",
  "/design-tokens.css",
  "/fonts/inter.woff2",
  "/fonts/space-grotesk.woff2",
  "/fonts/ibm-plex-mono.woff2",
  "/fonts/noto-sans-devanagari.woff2",
  "/fonts/noto-sans-bengali.woff2",
  "/assets/qrcode.min.js",
  "/assets/icon.svg",
  "/assets/icon.webp",
  "/manifest.json"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(SHELL_ASSETS);
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    })
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // 1. Strict Security Boundary: Only GET requests and never API calls or dynamic records
  if (req.method !== "GET" || url.pathname.startsWith("/api/")) {
    return; // Pass through to network directly without caching
  }

  // 2. Fragment tokens and sensitive query params must never be stored in cache
  if (url.searchParams.has("secret") || url.searchParams.has("token")) {
    return;
  }

  // 3. For static shell assets, match from cache, fallback to network
  event.respondWith(
    caches.match(req).then((cachedResponse) => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(req).then((networkResponse) => {
        // Cache only exact static shell assets
        if (networkResponse && networkResponse.status === 200 && networkResponse.type === "basic") {
          const isShell = SHELL_ASSETS.some(
            (p) => url.pathname === p || (p === "/u/" && url.pathname === "/u/index.html")
          );
          if (isShell) {
            const respClone = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(req, respClone));
          }
        }
        return networkResponse;
      });
    })
  );
});
