/**
 * Central Repository Provider Registry (functions/src/repositories/index.js)
 *
 * Binds all domain services to Cloud Firestore repositories in staging and production.
 * Allows deterministic injection or test doubles ONLY when not in a strict cloud environment
 * and USE_FIRESTORE_IN_TEST !== 'true'.
 */

const { isStrictCloudEnvironment } = require("../config/environmentGuard");
const { FirestoreAuthRepository } = require("./firestore/authRepository");
const { FirestoreOtpRepository } = require("./firestore/otpRepository");
const { FirestoreSessionRepository } = require("./firestore/sessionRepository");
const { FirestoreLedgerRepository } = require("./firestore/ledgerRepository");
const { FirestoreSyncRepository } = require("./firestore/syncRepository");
const { FirestoreLinkRepository } = require("./firestore/linkRepository");
const { FirestoreChatRepository } = require("./firestore/chatRepository");
const { FirestoreInboxRepository } = require("./firestore/inboxRepository");
const { FirestoreReminderRepository } = require("./firestore/reminderRepository");
const { FirestoreConfigRepository } = require("./firestore/configRepository");
const { FirestoreAdminRepository } = require("./firestore/adminRepository");
const { FirestorePrivacyRepository } = require("./firestore/privacyRepository");
const { FirestoreCryptoRepository } = require("./firestore/cryptoRepository");

let activeRepos = null;

function shouldUseInMemoryDefault() {
  if (isStrictCloudEnvironment()) {
    return false;
  }
  if (process.env.USE_FIRESTORE_IN_TEST === "true") {
    return false;
  }
  return true;
}

function createFirestoreRepositories(db = null) {
  return {
    auth: new FirestoreAuthRepository(db),
    otp: new FirestoreOtpRepository(db),
    session: new FirestoreSessionRepository(db),
    ledger: new FirestoreLedgerRepository(db),
    sync: new FirestoreSyncRepository(db),
    link: new FirestoreLinkRepository(db),
    chat: new FirestoreChatRepository(db),
    inbox: new FirestoreInboxRepository(db),
    reminder: new FirestoreReminderRepository(db),
    config: new FirestoreConfigRepository(db),
    admin: new FirestoreAdminRepository(db),
    privacy: new FirestorePrivacyRepository(db),
    crypto: new FirestoreCryptoRepository(db)
  };
}

function initRepositories() {
  if (activeRepos) return activeRepos;

  if (shouldUseInMemoryDefault()) {
    const {
      InMemoryAuthRepository,
      InMemoryOtpRepository,
      InMemorySessionRepository,
      InMemoryLedgerRepository,
      InMemorySyncRepository,
      InMemoryLinkRepository,
      InMemoryChatRepository,
      InMemoryInboxRepository,
      InMemoryReminderRepository,
      InMemoryConfigRepository,
      InMemoryAdminRepository,
      InMemoryPrivacyRepository,
      InMemoryCryptoRepository
    } = require("../../test/repositories/inMemoryRepositories");

    activeRepos = {
      auth: new InMemoryAuthRepository(),
      otp: new InMemoryOtpRepository(),
      session: new InMemorySessionRepository(),
      ledger: new InMemoryLedgerRepository(),
      sync: new InMemorySyncRepository(),
      link: new InMemoryLinkRepository(),
      chat: new InMemoryChatRepository(),
      inbox: new InMemoryInboxRepository(),
      reminder: new InMemoryReminderRepository(),
      config: new InMemoryConfigRepository(),
      admin: new InMemoryAdminRepository(),
      privacy: new InMemoryPrivacyRepository(),
      crypto: new InMemoryCryptoRepository()
    };
  } else {
    activeRepos = createFirestoreRepositories();
  }

  return activeRepos;
}

function setRepositories(customRepos) {
  if (isStrictCloudEnvironment()) {
    throw new Error("FATAL: Cannot override repositories in staging or production.");
  }
  activeRepos = {
    ...initRepositories(),
    ...customRepos
  };
}

async function resetTestRepositories() {
  if (isStrictCloudEnvironment()) {
    throw new Error("FATAL: Cannot reset repositories in staging or production.");
  }
  if (activeRepos) {
    const clearPromises = [];
    for (const repo of Object.values(activeRepos)) {
      if (repo && typeof repo.clear === "function") {
        clearPromises.push(repo.clear());
      }
    }
    await Promise.all(clearPromises);
  }
  activeRepos = null;
  return initRepositories();
}

function assertRepoMethod(repo, methodName, repoName = "Repository") {
  if (!repo || typeof repo[methodName] !== "function") {
    const err = new Error(`${repoName} is missing required method '${methodName}'`);
    err.code = "ERR_REPOSITORY_METHOD_MISSING";
    err.status = 500;
    throw err;
  }
}

module.exports = {
  initRepositories,
  getRepositories: () => initRepositories(),
  createFirestoreRepositories,
  setRepositories,
  resetTestRepositories,
  assertRepoMethod,
  getAuthRepository: () => initRepositories().auth,
  getOtpRepository: () => initRepositories().otp,
  getSessionRepository: () => initRepositories().session,
  getLedgerRepository: () => initRepositories().ledger,
  getSyncRepository: () => initRepositories().sync,
  getLinkRepository: () => initRepositories().link,
  getChatRepository: () => initRepositories().chat,
  getInboxRepository: () => initRepositories().inbox,
  getReminderRepository: () => initRepositories().reminder,
  getConfigRepository: () => initRepositories().config,
  getAdminRepository: () => initRepositories().admin,
  getPrivacyRepository: () => initRepositories().privacy,
  getCryptoRepository: () => initRepositories().crypto
};
