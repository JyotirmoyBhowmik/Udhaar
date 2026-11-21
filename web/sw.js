// =========================================================================
// Udhaar Web Service Worker — Static Shell, Web Push & Offline Sync (v30.0)
// Security Rule: NEVER cache record data, secret tokens, or /api/* endpoints
// Never serve or cache redirected responses for navigation requests
// =========================================================================

const CACHE_NAME = "udhaar-shell-v30";
const SHELL_ASSETS = [
  "/u/guest.css",
  "/u/firebase-init.js",
  "/u/guest.js",
  "/ledger/ledger.css",
  "/ledger/ledger.js",
  "/design-tokens.css",
  "/landing.css",
  "/fonts/inter.woff2",
  "/fonts/space-grotesk.woff2",
  "/fonts/ibm-plex-mono.woff2",
  "/fonts/noto-sans-devanagari.woff2",
  "/fonts/noto-sans-bengali.woff2",
  "/assets/qrcode.min.js",
  "/assets/idb-outbox.js",
  "/assets/merkle-chain.js",
  "/assets/e2ee-crypto.js",
  "/assets/image-processor.js",
  "/assets/telemetry.js",
  "/assets/webauthn-lock.js",
  "/assets/icon.svg",
  "/assets/icon.webp",
  "/manifest.json"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(async (cache) => {
      await Promise.all(
        SHELL_ASSETS.map(async (assetUrl) => {
          try {
            const resp = await fetch(assetUrl, { cache: "no-cache" });
            if (resp && resp.ok && !resp.redirected && resp.type === "basic") {
              await cache.put(assetUrl, resp);
            }
          } catch (_) {
            // Best-effort precache so one missing asset never breaks SW install
          }
        })
      );
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

  // 1. Strict Security Boundary: Only GET requests on same-origin and never API calls
  if (req.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) {
    return;
  }

  // 2. Fragment tokens and sensitive query params must never be stored in cache
  if (url.searchParams.has("secret") || url.searchParams.has("token")) {
    return;
  }

  // 3. Always let HTML document navigations go straight to network so Firebase cleanUrls/trailingSlash
  // redirects (e.g. /ledger/ -> /ledger) never trigger Chrome's SW ERR_FAILED error.
  if (req.mode === "navigate") {
    return;
  }

  // 4. Only intercept known static shell assets (CSS, JS, fonts, icons)
  const isShell = SHELL_ASSETS.includes(url.pathname);
  if (!isShell) {
    return;
  }

  event.respondWith(
    caches.match(req).then((cachedResponse) => {
      if (cachedResponse && !cachedResponse.redirected) {
        return cachedResponse;
      }
      return fetch(req).then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200 && !networkResponse.redirected && networkResponse.type === "basic") {
          const respClone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, respClone));
        }
        return networkResponse;
      });
    })
  );
});

// =========================================================================
// Web Push Notifications Handler (VAPID / RFC 8292)
// =========================================================================
self.addEventListener("push", (event) => {
  let data = {
    title: "Udhaar",
    body: "New update on your Udhaar record.",
    url: "/ledger/"
  };

  try {
    if (event.data) {
      data = event.data.json();
    }
  } catch (_) {
    if (event.data) {
      data.body = event.data.text();
    }
  }

  const title = data.title || "Udhaar";
  const options = {
    body: data.body,
    icon: "/assets/icon.webp",
    badge: "/assets/icon.webp",
    vibrate: [100, 50, 100],
    data: {
      url: data.url || "/ledger/"
    },
    actions: [
      { action: "open", title: "View Record" },
      { action: "close", title: "Dismiss" }
    ]
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  if (event.action === "close") return;

  const targetUrl = event.notification.data?.url || "/ledger/";

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url.includes("/ledger/") && "focus" in client) {
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
    })
  );
});

// =========================================================================
// Periodic & Background Sync Handler
// =========================================================================
self.addEventListener("periodicsync", (event) => {
  if (event.tag === "sync-ledger") {
    event.waitUntil(
      self.clients.matchAll({ type: "window" }).then((clients) => {
        clients.forEach((client) => {
          client.postMessage({ type: "TRIGGER_OUTBOX_SYNC" });
        });
      })
    );
  }
});
