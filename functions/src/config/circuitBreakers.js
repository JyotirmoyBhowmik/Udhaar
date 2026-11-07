/**
 * Cost Controls & External Provider Circuit Breakers
 * (functions/src/config/circuitBreakers.js)
 * Implements Stage S8 Spend & Rate Circuit Breakers:
 * - Persisted in Firestore (system_config/circuit_breakers) via ConfigRepository.
 * - Shared across instances with a 30-second TTL cache.
 * - Automatically trips circuit to OPEN when hourly or daily spend caps are reached.
 * - Zero module-level mutable maps or Proxy facades.
 */

const { getConfigRepository, assertRepoMethod } = require("../repositories");

const CACHE_TTL_MS = 30 * 1000; // 30-second cache TTL

const DEFAULT_LIMITS = Object.freeze({
  WHATSAPP: Object.freeze({ hourlyLimit: 150, dailyLimit: 1000 }),
  EMAIL: Object.freeze({ hourlyLimit: 300, dailyLimit: 2500 }),
  FCM_PUSH: Object.freeze({ hourlyLimit: 2000, dailyLimit: 20000 })
});

function getRequiredConfigRepo() {
  const repo = getConfigRepository();
  assertRepoMethod(repo, "getCircuitBreakers", "ConfigRepository");
  assertRepoMethod(repo, "setCircuitBreakers", "ConfigRepository");
  return repo;
}

function getCacheHolder(repo) {
  if (!repo._circuitBreakerCacheState) {
    repo._circuitBreakerCacheState = {
      data: null,
      expiresAtMs: 0
    };
  }
  return repo._circuitBreakerCacheState;
}

function invalidateCircuitCache() {
  const repo = getConfigRepository();
  if (repo && repo._circuitBreakerCacheState) {
    repo._circuitBreakerCacheState.data = null;
    repo._circuitBreakerCacheState.expiresAtMs = 0;
  }
}

function createFreshCircuit(providerName, now = Date.now()) {
  const limits = DEFAULT_LIMITS[providerName] || { hourlyLimit: 100, dailyLimit: 1000 };
  return {
    providerName,
    state: "CLOSED",
    hourlySpent: 0,
    dailySpent: 0,
    hourlyLimit: limits.hourlyLimit,
    dailyLimit: limits.dailyLimit,
    hourWindowStartMs: now,
    dayWindowStartMs: now,
    trippedAtMs: null,
    trippedReason: null
  };
}

function rotateWindowsIfNeeded(circuit, now = Date.now()) {
  const updated = { ...circuit };
  let changed = false;

  if (now - updated.hourWindowStartMs >= 3600 * 1000) {
    updated.hourlySpent = 0;
    updated.hourWindowStartMs = now;
    changed = true;
  }
  if (now - updated.dayWindowStartMs >= 24 * 3600 * 1000) {
    updated.dailySpent = 0;
    updated.dayWindowStartMs = now;
    changed = true;
  }
  if (changed && updated.state === "OPEN" && !updated.consecutiveFailures && updated.hourlySpent < updated.hourlyLimit && updated.dailySpent < updated.dailyLimit) {
    updated.state = "CLOSED";
    updated.trippedAtMs = null;
    updated.trippedReason = null;
  }
  return { circuit: updated, changed };
}

async function loadAllCircuits(forceRefresh = false) {
  const repo = getRequiredConfigRepo();
  const cache = getCacheHolder(repo);
  const now = Date.now();

  let raw = null;
  if (!forceRefresh && cache.data && now < cache.expiresAtMs) {
    raw = cache.data;
  } else {
    raw = (await repo.getCircuitBreakers()) || {};
  }

  const all = {};
  let anyChanged = false;
  for (const p of Object.keys(DEFAULT_LIMITS)) {
    const base = raw[p] ? { ...createFreshCircuit(p, now), ...raw[p] } : createFreshCircuit(p, now);
    const { circuit, changed } = rotateWindowsIfNeeded(base, now);
    all[p] = circuit;
    if (changed || !raw[p]) anyChanged = true;
  }

  for (const [k, v] of Object.entries(raw)) {
    if (!all[k] && v && typeof v === "object") {
      const { circuit, changed } = rotateWindowsIfNeeded({ ...createFreshCircuit(k, now), ...v }, now);
      all[k] = circuit;
      if (changed) anyChanged = true;
    }
  }

  if (anyChanged && forceRefresh) {
    await repo.setCircuitBreakers(all);
  }

  cache.data = all;
  cache.expiresAtMs = now + CACHE_TTL_MS;
  return all;
}

/**
 * Check if a provider circuit allows dispatch
 */
async function checkProviderCircuit(providerName, now = Date.now()) {
  const key = String(providerName || "").toUpperCase();
  const all = await loadAllCircuits(false);
  const base = all[key] || createFreshCircuit(key, now);
  const { circuit } = rotateWindowsIfNeeded(base, now);

  if (circuit.state === "OPEN") {
    return {
      allowed: false,
      state: "OPEN",
      reason: circuit.trippedReason || `Circuit breaker OPEN for ${key}`,
      hourlySpent: circuit.hourlySpent,
      dailySpent: circuit.dailySpent
    };
  }

  return {
    allowed: true,
    state: "CLOSED",
    hourlySpent: circuit.hourlySpent,
    dailySpent: circuit.dailySpent
  };
}

/**
 * Record spend units against a provider and trip circuit if threshold reached
 */
async function recordProviderSpend(providerName, units = 1, now = Date.now()) {
  const key = String(providerName || "").toUpperCase();
  const repo = getRequiredConfigRepo();
  const all = await loadAllCircuits(true);
  const base = all[key] || createFreshCircuit(key, now);
  const { circuit } = rotateWindowsIfNeeded(base, now);

  const amount = Math.max(1, Number(units) || 1);
  circuit.hourlySpent += amount;
  circuit.dailySpent += amount;

  if (circuit.hourlySpent >= circuit.hourlyLimit) {
    circuit.state = "OPEN";
    circuit.trippedAtMs = now;
    circuit.trippedReason = `Hourly quota exceeded (${circuit.hourlySpent}/${circuit.hourlyLimit})`;
  } else if (circuit.dailySpent >= circuit.dailyLimit) {
    circuit.state = "OPEN";
    circuit.trippedAtMs = now;
    circuit.trippedReason = `Daily quota exceeded (${circuit.dailySpent}/${circuit.dailyLimit})`;
  }

  all[key] = circuit;
  await repo.setCircuitBreakers(all);
  const cache = getCacheHolder(repo);
  cache.data = all;
  cache.expiresAtMs = now + CACHE_TTL_MS;

  return { ...circuit };
}

/**
 * Admin manual reset or limit adjustment for a provider circuit breaker
 */
async function resetProviderCircuit({ providerName, newHourlyLimit, newDailyLimit, adminUid, reason }) {
  const key = String(providerName || "").toUpperCase();
  if (!DEFAULT_LIMITS[key]) {
    const err = new Error(`Unknown provider '${providerName}'. Valid: ${Object.keys(DEFAULT_LIMITS).join(", ")}`);
    err.code = "ERR_INVALID_PROVIDER";
    err.status = 400;
    throw err;
  }

  const repo = getRequiredConfigRepo();
  const now = Date.now();
  const all = await loadAllCircuits(true);
  const circuit = all[key] || createFreshCircuit(key, now);

  circuit.state = "CLOSED";
  circuit.hourlySpent = 0;
  circuit.dailySpent = 0;
  circuit.trippedAtMs = null;
  circuit.trippedReason = null;

  if (Number.isFinite(Number(newHourlyLimit)) && Number(newHourlyLimit) > 0) {
    circuit.hourlyLimit = Number(newHourlyLimit);
  }
  if (Number.isFinite(Number(newDailyLimit)) && Number(newDailyLimit) > 0) {
    circuit.dailyLimit = Number(newDailyLimit);
  }

  all[key] = circuit;
  await repo.setCircuitBreakers(all);
  const cache = getCacheHolder(repo);
  cache.data = all;
  cache.expiresAtMs = now + CACHE_TTL_MS;

  return {
    ...circuit,
    resetBy: adminUid || "admin",
    resetReason: reason || "Manual circuit reset",
    resetAtMs: now
  };
}

/**
 * Get status of all provider circuit breakers
 */
async function getCircuitStatus({ forceRefresh = false } = {}) {
  const now = Date.now();
  const all = await loadAllCircuits(forceRefresh);
  const result = {};
  for (const p of Object.keys(DEFAULT_LIMITS)) {
    const { circuit } = rotateWindowsIfNeeded(all[p] || createFreshCircuit(p, now), now);
    result[p] = { ...circuit };
  }
  return result;
}

async function resetAllCircuits() {
  const repo = getRequiredConfigRepo();
  const now = Date.now();
  const defaults = {};
  for (const p of Object.keys(DEFAULT_LIMITS)) {
    defaults[p] = createFreshCircuit(p, now);
  }
  await repo.setCircuitBreakers(defaults);
  invalidateCircuitCache();
  return defaults;
}

async function recordFailure(providerName, errorReason = "Failure") {
  const key = String(providerName || "").toUpperCase();
  const repo = getRequiredConfigRepo();
  const now = Date.now();
  const all = await loadAllCircuits(true);
  const base = all[key] || createFreshCircuit(key, now);
  const { circuit } = rotateWindowsIfNeeded(base, now);

  circuit.consecutiveFailures = (circuit.consecutiveFailures || 0) + 1;
  if (circuit.consecutiveFailures >= 5) {
    circuit.state = "OPEN";
    circuit.trippedAtMs = now;
    circuit.trippedReason = errorReason;
  }

  all[key] = circuit;
  await repo.setCircuitBreakers(all);
  const cache = getCacheHolder(repo);
  cache.data = all;
  cache.expiresAtMs = now + CACHE_TTL_MS;
  return circuit;
}

async function canExecute(providerName) {
  return await checkProviderCircuit(providerName);
}

async function getCircuitState(providerName) {
  const key = String(providerName || "").toUpperCase();
  const all = await loadAllCircuits(false);
  const circuit = all[key] || createFreshCircuit(key);
  return circuit;
}

module.exports = {
  CACHE_TTL_MS,
  DEFAULT_LIMITS,
  checkProviderCircuit,
  recordProviderSpend,
  resetProviderCircuit,
  getCircuitStatus,
  resetAllCircuits,
  invalidateCircuitCache,
  recordFailure,
  canExecute,
  getCircuitState
};
