/**
 * Cost Controls & Provider Circuit Breakers (functions/src/config/circuitBreakers.js)
 * Implements Stage S8 automated spend caps on expensive third-party APIs (WhatsApp, SMS, Email):
 * - State: CLOSED (Normal), OPEN (Budget Tripped / Calls Blocked), HALF_OPEN (Trialing).
 * - Prevents cloud bill runaways by failing fast before calling provider HTTP endpoints.
 */

const circuitState = Object.create(null);

const DEFAULT_BUDGETS = Object.freeze({
  WHATSAPP: {
    dailyLimitUnits: 500, // Max 500 messages / ~₹2,500 daily cap
    hourlyLimitUnits: 150
  },
  EMAIL: {
    dailyLimitUnits: 2000,
    hourlyLimitUnits: 500
  },
  SMS: {
    dailyLimitUnits: 200,
    hourlyLimitUnits: 50
  }
});

function initCircuits() {
  for (const k of Object.keys(circuitState)) {
    delete circuitState[k];
  }
  for (const [provider, budget] of Object.entries(DEFAULT_BUDGETS)) {
    circuitState[provider] = {
      provider,
      state: "CLOSED", // CLOSED | OPEN | HALF_OPEN
      dailySpent: 0,
      hourlySpent: 0,
      dailyLimit: budget.dailyLimitUnits,
      hourlyLimit: budget.hourlyLimitUnits,
      lastResetDay: new Date().getUTCDate(),
      lastResetHour: new Date().getUTCHours(),
      trippedAtMs: null,
      tripReason: null
    };
  }
}

initCircuits();

const _inMemoryCircuitState = new Proxy(circuitState, {
  get(target, prop) {
    if (prop === "get") return (k) => target[k];
    if (prop === "set") return (k, v) => { target[k] = v; return target; };
    if (prop === "has") return (k) => Object.prototype.hasOwnProperty.call(target, k);
    if (prop === "clear") return () => initCircuits();
    if (prop === "entries") return function* () { for (const k of Object.keys(target)) yield [k, target[k]]; };
    if (prop === "values") return function* () { for (const k of Object.keys(target)) yield target[k]; };
    if (prop === "keys") return function* () { for (const k of Object.keys(target)) yield k; };
    if (prop === "size") return Object.keys(target).length;
    return target[prop];
  }
});

function checkRollingWindow(circuit) {
  const now = new Date();
  const currentDay = now.getUTCDate();
  const currentHour = now.getUTCHours();

  if (circuit.lastResetDay !== currentDay) {
    circuit.dailySpent = 0;
    circuit.hourlySpent = 0;
    circuit.lastResetDay = currentDay;
    circuit.lastResetHour = currentHour;
    if (circuit.state === "OPEN") {
      circuit.state = "CLOSED";
      circuit.trippedAtMs = null;
      circuit.tripReason = null;
    }
  } else if (circuit.lastResetHour !== currentHour) {
    circuit.hourlySpent = 0;
    circuit.lastResetHour = currentHour;
  }
}

/**
 * Check if the circuit breaker allows an operation
 */
function checkProviderCircuit(providerName) {
  const key = String(providerName || "").toUpperCase();
  const circuit = _inMemoryCircuitState.get(key);
  if (!circuit) {
    return { allowed: true, state: "CLOSED" };
  }

  checkRollingWindow(circuit);

  if (circuit.state === "OPEN") {
    return {
      allowed: false,
      state: "OPEN",
      provider: key,
      reason: circuit.tripReason || "Provider budget limit reached.",
      dailySpent: circuit.dailySpent,
      dailyLimit: circuit.dailyLimit
    };
  }

  return {
    allowed: true,
    state: circuit.state,
    provider: key,
    dailySpent: circuit.dailySpent,
    dailyLimit: circuit.dailyLimit
  };
}

/**
 * Record usage/cost units against a provider
 */
function recordProviderSpend(providerName, units = 1) {
  const key = String(providerName || "").toUpperCase();
  const circuit = _inMemoryCircuitState.get(key);
  if (!circuit) return;

  checkRollingWindow(circuit);

  circuit.dailySpent += Number(units) || 1;
  circuit.hourlySpent += Number(units) || 1;

  if (circuit.dailySpent >= circuit.dailyLimit) {
    circuit.state = "OPEN";
    circuit.trippedAtMs = Date.now();
    circuit.tripReason = `Daily budget of ${circuit.dailyLimit} units exceeded (spent: ${circuit.dailySpent}).`;
  } else if (circuit.hourlySpent >= circuit.hourlyLimit) {
    circuit.state = "OPEN";
    circuit.trippedAtMs = Date.now();
    circuit.tripReason = `Hourly rate of ${circuit.hourlyLimit} units exceeded (spent: ${circuit.hourlySpent}).`;
  }
}

/**
 * Manually reset a provider's circuit breaker (Admin only)
 */
function resetProviderCircuit({ providerName, newDailyLimit = null, reason = "Admin override", adminUid = "admin" }) {
  const key = String(providerName || "").toUpperCase();
  const circuit = _inMemoryCircuitState.get(key);
  if (!circuit) {
    const err = new Error(`Unknown provider: '${providerName}'.`);
    err.code = "ERR_INVALID_PROVIDER";
    err.status = 400;
    throw err;
  }

  circuit.state = "CLOSED";
  circuit.dailySpent = 0;
  circuit.hourlySpent = 0;
  circuit.trippedAtMs = null;
  circuit.tripReason = null;
  if (newDailyLimit && Number(newDailyLimit) > 0) {
    circuit.dailyLimit = Number(newDailyLimit);
  }

  return {
    success: true,
    provider: key,
    state: "CLOSED",
    reason,
    adminUid,
    dailyLimit: circuit.dailyLimit,
    resetAtMs: Date.now()
  };
}

/**
 * Get snapshot of all circuit breaker statuses
 */
function getCircuitStatus() {
  const result = {};
  for (const [key, c] of _inMemoryCircuitState.entries()) {
    checkRollingWindow(c);
    result[key] = {
      provider: c.provider,
      state: c.state,
      dailySpent: c.dailySpent,
      dailyLimit: c.dailyLimit,
      hourlySpent: c.hourlySpent,
      hourlyLimit: c.hourlyLimit,
      trippedAtMs: c.trippedAtMs,
      tripReason: c.tripReason
    };
  }
  return result;
}

function resetAllCircuits() {
  initCircuits();
}

module.exports = {
  checkProviderCircuit,
  recordProviderSpend,
  resetProviderCircuit,
  getCircuitStatus,
  resetAllCircuits,
  DEFAULT_BUDGETS,
  _inMemoryCircuitState
};
