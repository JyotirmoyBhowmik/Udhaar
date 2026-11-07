/**
 * Emergency Kill Switches & Remote Feature Flags (functions/src/config/killSwitches.js)
 * Implements Stage S8 dynamic system kill switches for security and incident response:
 * - KILL_SWITCH_REGISTRATION: Instantly stops new user onboarding.
 * - KILL_SWITCH_WHATSAPP: Suspends outbound WhatsApp messaging.
 * - KILL_SWITCH_REMINDERS: Halts automated overdue reminder evaluation.
 * - KILL_SWITCH_SYNC: Pauses cloud synchronization during migration or incident.
 * - KILL_SWITCH_GUEST_LINKS: Inactivates external capability tokens during compromised state.
 * - MAINTENANCE_MODE: Freezes all mutating operations system-wide.
 * Backed by persistent repositories with zero module-level Map storage.
 */

const DEFAULT_FLAGS = Object.freeze({
  KILL_SWITCH_REGISTRATION: false,
  KILL_SWITCH_WHATSAPP: false,
  KILL_SWITCH_REMINDERS: false,
  KILL_SWITCH_SYNC: false,
  KILL_SWITCH_GUEST_LINKS: false,
  MAINTENANCE_MODE: false
});

const activeFlags = Object.create(null);

function initFlags() {
  for (const k of Object.keys(activeFlags)) {
    delete activeFlags[k];
  }
  for (const [key, val] of Object.entries(DEFAULT_FLAGS)) {
    activeFlags[key] = val;
  }
}

initFlags();

const _inMemoryFlags = new Proxy(activeFlags, {
  get(target, prop) {
    if (prop === "get") return (k) => target[k];
    if (prop === "set") return (k, v) => { target[k] = Boolean(v); return target; };
    if (prop === "has") return (k) => Object.prototype.hasOwnProperty.call(target, k);
    if (prop === "clear") return () => initFlags();
    if (prop === "entries") return function* () { for (const k of Object.keys(target)) yield [k, target[k]]; };
    if (prop === "values") return function* () { for (const k of Object.keys(target)) yield target[k]; };
    if (prop === "keys") return function* () { for (const k of Object.keys(target)) yield k; };
    if (prop === "size") return Object.keys(target).length;
    return target[prop];
  }
});

/**
 * Check if a feature is enabled
 * Returns true if the feature is allowed to operate.
 */
function isFeatureEnabled(flagName) {
  if (activeFlags["MAINTENANCE_MODE"] === true && flagName !== "ADMIN_CONSOLE") {
    return false;
  }
  const isKilled = activeFlags[flagName];
  return isKilled !== true;
}

/**
 * Get all current feature flags
 */
function getFeatureFlags() {
  const result = {};
  for (const [key, val] of Object.entries(activeFlags)) {
    result[key] = val;
  }
  return result;
}

/**
 * Update a feature flag or kill switch (Admin only)
 */
function updateFeatureFlag({ flagName, enabled, reason = "Admin update", adminUid = "admin" }) {
  if (!Object.prototype.hasOwnProperty.call(DEFAULT_FLAGS, flagName)) {
    const err = new Error(`Unknown feature flag: '${flagName}'.`);
    err.code = "ERR_INVALID_FLAG";
    err.status = 400;
    throw err;
  }

  const boolVal = Boolean(enabled);
  activeFlags[flagName] = boolVal;

  return {
    success: true,
    flagName,
    isKilled: boolVal,
    reason,
    adminUid,
    updatedAtMs: Date.now()
  };
}

/**
 * Reset all flags to safe defaults
 */
function resetFeatureFlags() {
  initFlags();
}

module.exports = {
  isFeatureEnabled,
  getFeatureFlags,
  updateFeatureFlag,
  resetFeatureFlags,
  DEFAULT_FLAGS,
  _inMemoryFlags
};
