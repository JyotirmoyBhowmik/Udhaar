/// <reference lib="webworker" />

/**
 * Udhaar Web App Service Worker (webapp/src/pwa/sw.js)
 * Implements App Shell caching, offline navigation fallback, and network resilience.
 */

// @ts-nocheck
const CACHE_NAME = "udhaar-app-shell-v2.0";
const PRECACHE_URLS = [
  "/",
  "/index.html",
  "/app.css",
  "/app.js",
  "/design-tokens.css",
  "/manifest.json",
  "/assets/icon.svg",
  "/fonts/inter.woff2",
  "/fonts/space-grotesk.woff2",
  "/fonts/ibm-plex-mono.woff2",
  "/fonts/noto-sans-devanagari.woff2",
  "/fonts/noto-sans-bengali.woff2"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(PRECACHE_URLS).catch((err) => {
        console.warn("[SW] Precache warning (some assets may be dynamically cached):", err);
      });
    }).then(() => self.skipWaiting())
  );
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
    }).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // 1. API requests: network first with offline JSON error fallback
  if (url.pathname.startsWith("/api/")) {
    event.respondWith(
      fetch(req).catch(() => {
        return new Response(
          JSON.stringify({
            type: "https://udhaar.jyotirmoyb.com/errors/offline",
            title: "Network Offline",
            status: 503,
            detail: "You are currently offline. Changes are saved locally and will sync once reconnected.",
            code: "ERR_OFFLINE"
          }),
          {
            status: 503,
            headers: { "Content-Type": "application/problem+json" }
          }
        );
      })
    );
    return;
  }

  // 2. Navigation requests: SPA offline fallback
  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).catch(() => {
        return caches.match("/index.html").then((cached) => {
          return cached || fetch(req);
        });
      })
    );
    return;
  }

  // 3. Static assets: Stale-While-Revalidate
  event.respondWith(
    caches.match(req).then((cached) => {
      const fetchPromise = fetch(req)
        .then((networkRes) => {
          if (networkRes && networkRes.status === 200) {
            const resClone = networkRes.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(req, resClone);
            });
          }
          return networkRes;
        })
        .catch(() => cached);

      return cached || fetchPromise;
    })
  );
});

// Push notification receiver (Stage S7 readiness)
self.addEventListener("push", (event) => {
  if (!event.data) return;
  try {
    const payload = event.data.json();
    const title = payload.title || "Udhaar Notification";
    const options = {
      body: payload.body || "New ledger activity recorded",
      icon: "/assets/icon.svg",
      badge: "/assets/icon.svg",
      data: payload.data || {}
    };
    event.waitUntil(self.registration.showNotification(title, options));
  } catch (err) {
    console.error("[SW] Push handle error:", err);
  }
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url && "focus" in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
