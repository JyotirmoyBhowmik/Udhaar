/**
 * Enterprise SLI / SLO & Core Web Vitals Metrics Aggregator
 * (functions/src/observability/metrics.js)
 * Implements Stage S9 Observability:
 * - Real-time SLI metrics tracking: Latency p50, p95, p99; Availability; 5xx Error Rate.
 * - SLO Targets:
 *     - Availability: >= 99.9%
 *     - p95 Latency: < 200ms
 *     - p99 Latency: < 500ms
 *     - Error Rate: < 0.1%
 * - RUM Ingestion: Aggregates client-side Core Web Vitals (LCP, INP, CLS) and client exceptions.
 */

// Latency sample buffer (reservoir of up to 10,000 samples)
const latencyReservoir = [];
const MAX_RESERVOIR_SIZE = 10000;

class KeyStore {
  constructor() {
    this._data = Object.create(null);
  }
  get(k) { return this._data[k]; }
  set(k, v) { this._data[k] = v; return this; }
  clear() { this._data = Object.create(null); }
  *entries() { for (const k of Object.keys(this._data)) yield [k, this._data[k]]; }
}

// Metric counters
const counters = {
  totalRequests: 0,
  status2xx: 0,
  status3xx: 0,
  status4xx: 0,
  status5xx: 0,
  endpoints: new KeyStore() // endpoint -> { count, totalDurationMs, status2xx, status4xx, status5xx }
};

// Client RUM metrics buffer
const rumMetrics = {
  lcpSamples: [], // in ms
  inpSamples: [], // in ms
  clsSamples: [], // unitless score
  clientErrors: []
};

function resetMetrics() {
  latencyReservoir.length = 0;
  counters.totalRequests = 0;
  counters.status2xx = 0;
  counters.status3xx = 0;
  counters.status4xx = 0;
  counters.status5xx = 0;
  counters.endpoints.clear();
  rumMetrics.lcpSamples.length = 0;
  rumMetrics.inpSamples.length = 0;
  rumMetrics.clsSamples.length = 0;
  rumMetrics.clientErrors.length = 0;
}

/**
 * Record completed HTTP request
 */
function recordHttpRequest({ method = "GET", path = "/", route = null, statusCode = 200, status = null, durationMs = 0 }) {
  const dur = Math.max(0, Number(durationMs) || 0);
  counters.totalRequests++;

  const resolvedStatus = Number(status !== null ? status : statusCode) || 200;
  if (resolvedStatus >= 200 && resolvedStatus < 300) counters.status2xx++;
  else if (resolvedStatus >= 300 && resolvedStatus < 400) counters.status3xx++;
  else if (resolvedStatus >= 400 && resolvedStatus < 500) counters.status4xx++;
  else if (resolvedStatus >= 500) counters.status5xx++;

  // Add to reservoir
  if (latencyReservoir.length < MAX_RESERVOIR_SIZE) {
    latencyReservoir.push(dur);
  } else {
    // Reservoir sampling replacement
    const idx = Math.floor(Math.random() * counters.totalRequests);
    if (idx < MAX_RESERVOIR_SIZE) {
      latencyReservoir[idx] = dur;
    }
  }

  // Endpoint aggregation
  const rawPath = route || path;
  const normalizedPath = rawPath.split("?")[0].replace(/\/([0-9a-fA-F-]{20,}|[0-9]{10,}|usr_[^/]+|rec_[^/]+|dev_[^/]+|msg_[^/]+)/g, "/:id");
  const endpointKey = `${method.toUpperCase()} ${normalizedPath}`;

  const ep = counters.endpoints.get(endpointKey) || { count: 0, totalDurationMs: 0, status2xx: 0, status4xx: 0, status5xx: 0 };
  ep.count++;
  ep.totalDurationMs += dur;
  if (resolvedStatus >= 200 && resolvedStatus < 300) ep.status2xx++;
  else if (resolvedStatus >= 400 && resolvedStatus < 500) ep.status4xx++;
  else if (resolvedStatus >= 500) ep.status5xx++;
  counters.endpoints.set(endpointKey, ep);
}

/**
 * Calculate percentiles from reservoir
 */
function calculatePercentiles(samples) {
  if (!samples || samples.length === 0) {
    return { p50: 0, p90: 0, p95: 0, p99: 0, min: 0, max: 0, avg: 0 };
  }

  const sorted = [...samples].sort((a, b) => a - b);
  const len = sorted.length;

  const getP = (p) => {
    const idx = Math.min(len - 1, Math.max(0, Math.floor((p / 100) * len)));
    return Number(sorted[idx].toFixed(2));
  };

  const sum = sorted.reduce((acc, val) => acc + val, 0);

  return {
    p50: getP(50),
    p90: getP(90),
    p95: getP(95),
    p99: getP(99),
    min: Number(sorted[0].toFixed(2)),
    max: Number(sorted[len - 1].toFixed(2)),
    avg: Number((sum / len).toFixed(2))
  };
}

/**
 * Ingest client RUM metric
 */
function ingestRumMetric({ metricName, value, pageUrl = "", userAgent = "", errorDetails = null }) {
  const metric = String(metricName || "").toUpperCase();
  const val = Number(value);

  if (metric === "LCP" && !isNaN(val)) {
    rumMetrics.lcpSamples.push(val);
  } else if (metric === "INP" && !isNaN(val)) {
    rumMetrics.inpSamples.push(val);
  } else if (metric === "CLS" && !isNaN(val)) {
    rumMetrics.clsSamples.push(val);
  } else if (metric === "CLIENT_ERROR" || errorDetails) {
    rumMetrics.clientErrors.push({
      timestampMs: Date.now(),
      pageUrl: String(pageUrl).slice(0, 200),
      message: String(errorDetails?.message || value || "Unknown client error").slice(0, 300),
      stack: String(errorDetails?.stack || "").slice(0, 500)
    });
    if (rumMetrics.clientErrors.length > 200) rumMetrics.clientErrors.shift();
  }

  return { success: true, metricName: metric };
}

/**
 * Get full SLI/SLO snapshot
 */
function getSloSnapshot() {
  const total = counters.totalRequests;
  const availability = total > 0 ? Number((((total - counters.status5xx) / total) * 100).toFixed(3)) : 100.0;
  const errorRate = total > 0 ? Number(((counters.status5xx / total) * 100).toFixed(3)) : 0.0;
  const latencies = calculatePercentiles(latencyReservoir);

  // SLO Compliance Evaluation
  const availabilityOk = availability >= 99.9;
  const p95Ok = latencies.p95 <= 200;
  const p99Ok = latencies.p99 <= 500;
  const errorRateOk = errorRate <= 0.1;

  const isHealthy = availabilityOk && p95Ok && p99Ok && errorRateOk;

  // RUM Web Vitals summaries
  const lcpStats = calculatePercentiles(rumMetrics.lcpSamples);
  const inpStats = calculatePercentiles(rumMetrics.inpSamples);
  const clsStats = calculatePercentiles(rumMetrics.clsSamples);

  const endpointSummaries = {};
  for (const [key, ep] of counters.endpoints.entries()) {
    endpointSummaries[key] = {
      calls: ep.count,
      avgDurationMs: ep.count > 0 ? Number((ep.totalDurationMs / ep.count).toFixed(2)) : 0,
      status2xx: ep.status2xx,
      status4xx: ep.status4xx,
      status5xx: ep.status5xx
    };
  }

  return {
    timestampMs: Date.now(),
    isoDate: new Date().toISOString(),
    status: isHealthy ? "HEALTHY" : "AT_RISK",
    sli: {
      totalRequests: total,
      availabilityPercentage: availability,
      errorRatePercentage: errorRate,
      latencyMs: latencies
    },
    sloTargets: {
      availabilityPercentage: 99.9,
      p95LatencyMs: 200,
      p99LatencyMs: 500,
      maxErrorRatePercentage: 0.1
    },
    sloCompliance: {
      availabilityOk,
      p95Ok,
      p99Ok,
      errorRateOk
    },
    rumWebVitals: {
      lcp: { count: rumMetrics.lcpSamples.length, p75: lcpStats.p90, targetMs: 1200 },
      inp: { count: rumMetrics.inpSamples.length, p75: inpStats.p90, targetMs: 100 },
      cls: { count: rumMetrics.clsSamples.length, p75: clsStats.p90, target: 0.05 },
      clientErrorCount: rumMetrics.clientErrors.length
    },
    endpoints: endpointSummaries
  };
}

function getMetricsSnapshot() {
  const latencies = calculatePercentiles(latencyReservoir);
  return {
    http: {
      total: counters.totalRequests,
      status2xx: counters.status2xx,
      status3xx: counters.status3xx,
      status4xx: counters.status4xx,
      status5xx: counters.status5xx,
      Status5xx: counters.status5xx,
      latency: {
        p50Ms: latencies.p50,
        p95Ms: latencies.p95,
        p99Ms: latencies.p99
      }
    },
    ...getSloSnapshot()
  };
}

function evaluateAlerts() {
  const alerts = [];
  const latencies = calculatePercentiles(latencyReservoir);
  if (counters.status5xx > 0) {
    alerts.push({ code: "ALERT_HIGH_5XX_RATE", severity: "CRITICAL", message: "High 5xx error rate detected" });
  }
  if (latencies.p95 >= 200) {
    alerts.push({ code: "ALERT_HIGH_P95_LATENCY", severity: "WARNING", message: "p95 latency threshold breached" });
  }
  return alerts;
}

module.exports = {
  recordHttpRequest,
  calculatePercentiles,
  ingestRumMetric,
  getSloSnapshot,
  getMetricsSnapshot,
  evaluateAlerts,
  resetMetrics,
  _counters: counters,
  _latencyReservoir: latencyReservoir,
  _rumMetrics: rumMetrics
};
