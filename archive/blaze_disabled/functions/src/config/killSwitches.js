/**
 * Emergency Kill Switches & Remote Feature Flags Service
 * (functions/src/config/killSwitches.js)
 * Implements Stage S8 Operational Kill Switches:
 * - Persisted in Firestore (system_config/kill_switches) via ConfigRepository.
 * - Shared across instances with a 30-second TTL cache.
 * - Zero module-level mutable maps or Proxy facades.
 */

const { getConfigRepository, assertRepoMethod } = require("../repositories");

const CACHE_TTL_MS = 30 * 1000; // 30-second cache TTL

const DEFAULT_FLAGS = Object.freeze({
  KILL_SWITCH_REGISTRATION: false,
  KILL_SWITCH_WHATSAPP_OTP: false,
  KILL_SWITCH_WHATSAPP: false,
  KILL_SWITCH_OTP_DISPATCH: false,
  KILL_SWITCH_REMINDERS: false,
  KILL_SWITCH_GUEST_LINKS: false,
  KILL_SWITCH_EXPORTS: false
});

function normalizeFlagName(flagName) {
  if (flagName === "KILL_SWITCH_WHATSAPP") {
    return "KILL_SWITCH_WHATSAPP_OTP";
  }
  return flagName;
}

function getRequiredConfigRepo() {
  const repo = getConfigRepository();
  assertRepoMethod(repo, "getKillSwitches", "ConfigRepository");
  assertRepoMethod(repo, "setKillSwitches", "ConfigRepository");
  return repo;
}

function getCacheHolder(repo) {
  if (!repo._killSwitchCacheState) {
    repo._killSwitchCacheState = {
      data: null,
      expiresAtMs: 0
    };
  }
  return repo._killSwitchCacheState;
}

function invalidateKillSwitchCache() {
  const repo = getConfigRepository();
  if (repo && repo._killSwitchCacheState) {
    repo._killSwitchCacheState.data = null;
    repo._killSwitchCacheState.expiresAtMs = 0;
  }
}

async function loadFlagsWithCache(forceRefresh = false) {
  const repo = getRequiredConfigRepo();
  const cache = getCacheHolder(repo);
  const now = Date.now();

  if (!forceRefresh && cache.data && now < cache.expiresAtMs) {
    return { ...DEFAULT_FLAGS, ...cache.data };
  }

  const stored = (await repo.getKillSwitches()) || {};
  const merged = { ...DEFAULT_FLAGS, ...stored };
  if (merged.KILL_SWITCH_WHATSAPP_OTP) {
    merged.KILL_SWITCH_WHATSAPP = true;
  }
  cache.data = merged;
  cache.expiresAtMs = now + CACHE_TTL_MS;
  return { ...merged };
}

/**
 * Get all current kill switch states
 */
async function getFeatureFlags({ forceRefresh = false } = {}) {
  return loadFlagsWithCache(forceRefresh);
}

/**
 * Check if a specific subsystem is currently killed/suspended
 */
async function isFeatureSuspended(flagName) {
  const canonical = normalizeFlagName(flagName);
  if (!Object.prototype.hasOwnProperty.call(DEFAULT_FLAGS, canonical)) {
    throw new Error(`Unknown kill-switch flag: ${flagName}`);
  }
  const flags = await loadFlagsWithCache(false);
  return Boolean(flags[canonical]);
}

/**
 * Check if a feature is enabled (i.e., NOT suspended by its kill switch)
 */
async function isFeatureEnabled(flagName) {
  const suspended = await isFeatureSuspended(flagName);
  return !suspended;
}

/**
 * Toggle a kill switch (Admin/Operator only)
 */
async function updateFeatureFlag({ flagName, enabled, reason, adminUid }) {
  const canonical = normalizeFlagName(flagName);
  if (!Object.prototype.hasOwnProperty.call(DEFAULT_FLAGS, canonical)) {
    const err = new Error(`Unknown feature flag '${flagName}'. Valid flags: ${Object.keys(DEFAULT_FLAGS).join(", ")}`);
    err.code = "ERR_INVALID_FEATURE_FLAG";
    err.status = 400;
    throw err;
  }

  const repo = getRequiredConfigRepo();
  const current = await loadFlagsWithCache(true);
  const previousValue = Boolean(current[canonical]);
  const newValue = Boolean(enabled);
  const updatedFlags = {
    ...current,
    [canonical]: newValue
  };
  if (canonical === "KILL_SWITCH_WHATSAPP_OTP") {
    updatedFlags.KILL_SWITCH_WHATSAPP = newValue;
  }

  await repo.setKillSwitches(updatedFlags);
  const cache = getCacheHolder(repo);
  cache.data = updatedFlags;
  cache.expiresAtMs = Date.now() + CACHE_TTL_MS;

  return {
    flagName,
    canonicalFlagName: canonical,
    previousValue,
    enabled: newValue,
    isKilled: newValue,
    updatedBy: adminUid || "system",
    reason: reason || "Emergency override",
    updatedAtMs: Date.now()
  };
}

/**
 * Reset all kill switches to default (all false / operational)
 */
async function resetFeatureFlags() {
  const repo = getRequiredConfigRepo();
  const defaults = { ...DEFAULT_FLAGS };
  await repo.setKillSwitches(defaults);
  invalidateKillSwitchCache();
  return defaults;
}

async function setKillSwitch(flagName, value, options = {}) {
  const norm = flagName.startsWith("KILL_SWITCH_") ? flagName : (flagName.startsWith("KILL_") ? "KILL_SWITCH_" + flagName.slice(5) : "KILL_SWITCH_" + flagName);
  return await updateFeatureFlag({ flagName: norm, enabled: value, reason: options.reason, adminUid: options.updatedBy });
}

async function isKillSwitchActive(flagName) {
  const norm = flagName.startsWith("KILL_SWITCH_") ? flagName : (flagName.startsWith("KILL_") ? "KILL_SWITCH_" + flagName.slice(5) : "KILL_SWITCH_" + flagName);
  return await isFeatureSuspended(norm);
}

const resetAllKillSwitches = resetFeatureFlags;

module.exports = {
  CACHE_TTL_MS,
  DEFAULT_FLAGS,
  getFeatureFlags,
  isFeatureSuspended,
  isFeatureEnabled,
  updateFeatureFlag,
  resetFeatureFlags,
  invalidateKillSwitchCache,
  setKillSwitch,
  isKillSwitchActive,
  resetAllKillSwitches
};
