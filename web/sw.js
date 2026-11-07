// =========================================================================
// Udhaar Web Service Worker — Static Shell, Web Push & Offline Sync (v28.0)
// Security Rule: NEVER cache record data, secret tokens, or /api/* endpoints
// =========================================================================

const CACHE_NAME = "udhaar-shell-v28";
const SHELL_ASSETS = [
  "/u/",
  "/u/index.html",
  "/u/guest.css",
  "/u/guest.js",
  "/ledger/",
  "/ledger/index.html",
  "/ledger/ledger.css",
  "/ledger/ledger.js",
  "/design-tokens.css",
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
