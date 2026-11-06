/**
 * Udhaar Distributed Tracing & Real User Monitoring (web/assets/telemetry.js)
 * Implements W3C Trace Context (traceparent), Correlation IDs, and Core Web Vitals RUM.
 * Uses navigator.sendBeacon for zero-overhead telemetry on page unload.
 * Zero external dependencies. Strict CSP compliant.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarTelemetry = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  function randomHex(byteCount) {
    if (typeof crypto !== "undefined" && crypto.getRandomValues) {
      const bytes = new Uint8Array(byteCount);
      crypto.getRandomValues(bytes);
      return Array.from(bytes).map(b => b.toString(16).padStart(2, "0")).join("");
    }
    let res = "";
    for (let i = 0; i < byteCount * 2; i++) {
      res += Math.floor(Math.random() * 16).toString(16);
    }
    return res;
  }

  /**
   * Generate standard W3C Trace Context header (traceparent)
   * Format: 00-{traceId (32 hex)}-{spanId (16 hex)}-01
   */
  function generateTraceparent() {
    const version = "00";
    const traceId = randomHex(16); // 16 bytes = 32 hex chars
    const parentId = randomHex(8);  // 8 bytes = 16 hex chars
    const traceFlags = "01";       // 01 = sampled / recorded
    return `${version}-${traceId}-${parentId}-${traceFlags}`;
  }

  /**
   * Generate correlation ID
   */
  function createCorrelationId() {
    return `corr-${Date.now()}-${randomHex(4)}`;
  }

  /**
   * Enhanced fetch wrapper injecting W3C Traceparent and Correlation headers
   */
  async function fetchWithTrace(url, options = {}) {
    const traceparent = options.traceparent || generateTraceparent();
    const correlationId = options.correlationId || createCorrelationId();

    const headers = new Headers(options.headers || {});
    headers.set("traceparent", traceparent);
    headers.set("X-Correlation-Id", correlationId);

    const mergedOptions = {
      ...options,
      headers
    };

    const response = await fetch(url, mergedOptions);
    return {
      response,
      traceparent,
      correlationId
    };
  }

  // Local metric buffer for Core Web Vitals
  const webVitals = {
    lcp: null, // Largest Contentful Paint (ms)
    inp: null, // Interaction to Next Paint (ms)
    cls: 0     // Cumulative Layout Shift (score)
  };

  /**
   * Initialize PerformanceObserver to capture Core Web Vitals
   */
  function initWebVitalsObserver(onMetricCallback = null) {
    if (typeof PerformanceObserver === "undefined") return false;

    try {
      // 1. Largest Contentful Paint (LCP)
      const lcpObserver = new PerformanceObserver((entryList) => {
        const entries = entryList.getEntries();
        const lastEntry = entries[entries.length - 1];
        if (lastEntry) {
          webVitals.lcp = Math.round(lastEntry.startTime);
          if (onMetricCallback) onMetricCallback("LCP", webVitals.lcp);
        }
      });
      lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });

      // 2. Cumulative Layout Shift (CLS)
      const clsObserver = new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries()) {
          if (!entry.hadRecentInput) {
            webVitals.cls = Number((webVitals.cls + entry.value).toFixed(4));
            if (onMetricCallback) onMetricCallback("CLS", webVitals.cls);
          }
        }
      });
      clsObserver.observe({ type: "layout-shift", buffered: true });

      // 3. First Input Delay / INP
      const inpObserver = new PerformanceObserver((entryList) => {
        for (const entry of entryList.getEntries()) {
          const duration = Math.round(entry.duration || (entry.processingStart - entry.startTime));
          webVitals.inp = Math.max(webVitals.inp || 0, duration);
          if (onMetricCallback) onMetricCallback("INP", webVitals.inp);
        }
      });
      inpObserver.observe({ type: "first-input", buffered: true });

      return true;
    } catch (err) {
      console.warn("PerformanceObserver error:", err.message);
      return false;
    }
  }

  /**
   * Send performance and error beacons without blocking page unload
   */
  function sendBeaconTelemetry(endpointUrl, payload = {}) {
    if (!endpointUrl) return false;
    const body = JSON.stringify({
      ...payload,
      timestamp: Date.now(),
      vitals: { ...webVitals },
      correlationId: createCorrelationId()
    });

    if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
      const blob = new Blob([body], { type: "application/json" });
      return navigator.sendBeacon(endpointUrl, blob);
    }
    return false;
  }

  return {
    generateTraceparent,
    createCorrelationId,
    fetchWithTrace,
    initWebVitalsObserver,
    sendBeaconTelemetry,
    getWebVitals: () => ({ ...webVitals })
  };
});
