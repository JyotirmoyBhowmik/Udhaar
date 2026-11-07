/**
 * Repository Registry & Factory (functions/src/repositories/index.js)
 * Provides access to persistent Firestore repositories in production/staging
 * and injects isolated in-memory repositories during unit testing.
 */

const { isProductionOrStaging, enforceProductionEnvironmentGuards } = require("../config/environmentGuard");

// Firestore implementations
const { FirestoreAuthRepository } = require("./firestore/authRepository");
const { FirestoreOtpRepository } = require("./firestore/otpRepository");
const { FirestoreSessionRepository } = require("./firestore/sessionRepository");
const { FirestoreLedgerRepository } = require("./firestore/ledgerRepository");
const { FirestoreSyncRepository } = require("./firestore/syncRepository");
const { FirestoreLinkRepository } = require("./firestore/linkRepository");
const { FirestoreChatRepository } = require("./firestore/chatRepository");
const { FirestoreInboxRepository } = require("./firestore/inboxRepository");
const { FirestoreReminderRepository } = require("./firestore/reminderRepository");
const { FirestorePrivacyRepository } = require("./firestore/privacyRepository");
const { FirestoreAdminRepository } = require("./firestore/adminRepository");
const { FirestoreConfigRepository } = require("./firestore/configRepository");

// Registry holding active instances
let activeRepositories = null;
let overrides = {};

function initRepositories() {
  const isProd = isProductionOrStaging();

  // Enforce production security guard
  enforceProductionEnvironmentGuards({
    useInMemoryStore: false
  });

  if (isProd || process.env.USE_FIRESTORE_IN_TEST === "true" || process.env.FIRESTORE_EMULATOR_HOST) {
    return {
      auth: new FirestoreAuthRepository(),
      otp: new FirestoreOtpRepository(),
      session: new FirestoreSessionRepository(),
      ledger: new FirestoreLedgerRepository(),
      sync: new FirestoreSyncRepository(),
      link: new FirestoreLinkRepository(),
      chat: new FirestoreChatRepository(),
      inbox: new FirestoreInboxRepository(),
      reminder: new FirestoreReminderRepository(),
      privacy: new FirestorePrivacyRepository(),
      admin: new FirestoreAdminRepository(),
      config: new FirestoreConfigRepository()
    };
  }

  // In non-production testing, dynamically load test doubles from test/
  try {
    const { createInMemoryRepositories } = require("../../test/repositories/inMemoryRepositories");
    return createInMemoryRepositories();
  } catch (err) {
    return {
      auth: new FirestoreAuthRepository(),
      otp: new FirestoreOtpRepository(),
      session: new FirestoreSessionRepository(),
      ledger: new FirestoreLedgerRepository(),
      sync: new FirestoreSyncRepository(),
      link: new FirestoreLinkRepository(),
      chat: new FirestoreChatRepository(),
      inbox: new FirestoreInboxRepository(),
      reminder: new FirestoreReminderRepository(),
      privacy: new FirestorePrivacyRepository(),
      admin: new FirestoreAdminRepository(),
      config: new FirestoreConfigRepository()
    };
  }
}

function getRepositories() {
  if (!activeRepositories) {
    activeRepositories = initRepositories();
  }
  return {
    ...activeRepositories,
    ...overrides
  };
}

function getAuthRepository() { return getRepositories().auth; }
function getOtpRepository() { return getRepositories().otp; }
function getSessionRepository() { return getRepositories().session; }
function getLedgerRepository() { return getRepositories().ledger; }
function getSyncRepository() { return getRepositories().sync; }
function getLinkRepository() { return getRepositories().link; }
function getChatRepository() { return getRepositories().chat; }
function getInboxRepository() { return getRepositories().inbox; }
function getReminderRepository() { return getRepositories().reminder; }
function getPrivacyRepository() { return getRepositories().privacy; }
function getAdminRepository() { return getRepositories().admin; }
function getConfigRepository() { return getRepositories().config; }

function setRepositoryOverride(name, repoInstance) {
  if (isProductionOrStaging() && repoInstance.constructor.name.startsWith("InMemory")) {
    enforceProductionEnvironmentGuards({ useInMemoryStore: true });
  }
  overrides[name] = repoInstance;
}

function resetTestRepositories() {
  overrides = {};
  if (activeRepositories) {
    Object.values(activeRepositories).forEach(repo => {
      if (typeof repo.clear === "function") {
        repo.clear();
      }
    });
  }
}

module.exports = {
  getRepositories,
  getAuthRepository,
  getOtpRepository,
  getSessionRepository,
  getLedgerRepository,
  getSyncRepository,
  getLinkRepository,
  getChatRepository,
  getInboxRepository,
  getReminderRepository,
  getPrivacyRepository,
  getAdminRepository,
  getConfigRepository,
  setRepositoryOverride,
  resetTestRepositories
};
