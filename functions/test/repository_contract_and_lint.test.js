/**
 * Repository Contract Equality and Anti-Proxy Lint Verification
 * (functions/test/repository_contract_and_lint.test.js)
 * Implements Platform v2 Requirement 4:
 * 1. Contract Test: Compares all 13 Firestore vs InMemory repositories to ensure identical public API method sets.
 * 2. Missing method enforcement: Asserts assertRepoMethod throws ERR_REPOSITORY_METHOD_MISSING.
 * 3. Static Lint Test: Scans functions/src/ to strictly ban Proxy facades and no-op fallback stubs.
 */

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

const {
  FirestoreAuthRepository,
  FirestoreOtpRepository,
  FirestoreSessionRepository,
  FirestoreLedgerRepository,
  FirestoreSyncRepository,
  FirestoreLinkRepository,
  FirestoreChatRepository,
  FirestoreInboxRepository,
  FirestoreReminderRepository,
  FirestoreConfigRepository,
  FirestoreAdminRepository,
  FirestorePrivacyRepository,
  FirestoreCryptoRepository
} = {
  FirestoreAuthRepository: require("../src/repositories/firestore/authRepository").FirestoreAuthRepository,
  FirestoreOtpRepository: require("../src/repositories/firestore/otpRepository").FirestoreOtpRepository,
  FirestoreSessionRepository: require("../src/repositories/firestore/sessionRepository").FirestoreSessionRepository,
  FirestoreLedgerRepository: require("../src/repositories/firestore/ledgerRepository").FirestoreLedgerRepository,
  FirestoreSyncRepository: require("../src/repositories/firestore/syncRepository").FirestoreSyncRepository,
  FirestoreLinkRepository: require("../src/repositories/firestore/linkRepository").FirestoreLinkRepository,
  FirestoreChatRepository: require("../src/repositories/firestore/chatRepository").FirestoreChatRepository,
  FirestoreInboxRepository: require("../src/repositories/firestore/inboxRepository").FirestoreInboxRepository,
  FirestoreReminderRepository: require("../src/repositories/firestore/reminderRepository").FirestoreReminderRepository,
  FirestoreConfigRepository: require("../src/repositories/firestore/configRepository").FirestoreConfigRepository,
  FirestoreAdminRepository: require("../src/repositories/firestore/adminRepository").FirestoreAdminRepository,
  FirestorePrivacyRepository: require("../src/repositories/firestore/privacyRepository").FirestorePrivacyRepository,
  FirestoreCryptoRepository: require("../src/repositories/firestore/cryptoRepository").FirestoreCryptoRepository
};

const inMemoryRepos = require("./repositories/inMemoryRepositories");
const { assertRepoMethod } = require("../src/repositories");

function getPublicMethods(ClassObj) {
  const proto = ClassObj.prototype;
  return Object.getOwnPropertyNames(proto).filter(
    (prop) => typeof proto[prop] === "function" && prop !== "constructor" && !prop.startsWith("_")
  );
}

describe("Requirement 4: Repository Contract Equality & Anti-Proxy Lint", () => {
  describe("1. Contract Equality between Firestore and In-Memory Implementations", () => {
    const repoPairs = [
      ["AuthRepository", FirestoreAuthRepository, inMemoryRepos.InMemoryAuthRepository],
      ["SessionRepository", FirestoreSessionRepository, inMemoryRepos.InMemorySessionRepository],
      ["CryptoRepository", FirestoreCryptoRepository, inMemoryRepos.InMemoryCryptoRepository],
      ["OtpRepository", FirestoreOtpRepository, inMemoryRepos.InMemoryOtpRepository],
      ["LedgerRepository", FirestoreLedgerRepository, inMemoryRepos.InMemoryLedgerRepository],
      ["SyncRepository", FirestoreSyncRepository, inMemoryRepos.InMemorySyncRepository],
      ["LinkRepository", FirestoreLinkRepository, inMemoryRepos.InMemoryLinkRepository],
      ["ReminderRepository", FirestoreReminderRepository, inMemoryRepos.InMemoryReminderRepository],
      ["ChatRepository", FirestoreChatRepository, inMemoryRepos.InMemoryChatRepository],
      ["InboxRepository", FirestoreInboxRepository, inMemoryRepos.InMemoryInboxRepository],
      ["ConfigRepository", FirestoreConfigRepository, inMemoryRepos.InMemoryConfigRepository],
      ["AdminRepository", FirestoreAdminRepository, inMemoryRepos.InMemoryAdminRepository],
      ["PrivacyRepository", FirestorePrivacyRepository, inMemoryRepos.InMemoryPrivacyRepository]
    ];

    for (const [name, FirestoreClass, InMemoryClass] of repoPairs) {
      it(`enforces exact method set match for ${name}`, () => {
        const firestoreMethods = new Set(getPublicMethods(FirestoreClass));
        const inMemoryMethods = new Set(getPublicMethods(InMemoryClass));

        const missingInMem = [...firestoreMethods].filter(m => !inMemoryMethods.has(m));
        const missingInFirestore = [...inMemoryMethods].filter(m => !firestoreMethods.has(m));

        assert.deepEqual(
          missingInMem,
          [],
          `${name}: Methods in Firestore repo but missing in InMemory repo: ${missingInMem.join(", ")}`
        );
        assert.deepEqual(
          missingInFirestore,
          [],
          `${name}: Methods in InMemory repo but missing in Firestore repo: ${missingInFirestore.join(", ")}`
        );
      });
    }

    it("throws ERR_REPOSITORY_METHOD_MISSING when a repository lacks a required method", () => {
      const dummyRepo = {};
      assert.throws(
        () => assertRepoMethod(dummyRepo, "nonExistentMethod", "DummyRepository"),
        (err) => {
          assert.equal(err.code, "ERR_REPOSITORY_METHOD_MISSING");
          return true;
        }
      );
    });
  });

  describe("2. Static Codebase Lint: Ban Proxy Facades and No-Op Stubs in src/", () => {
    function getAllSourceFiles(dir, fileList = []) {
      const files = fs.readdirSync(dir);
      for (const file of files) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
          getAllSourceFiles(fullPath, fileList);
        } else if (file.endsWith(".js")) {
          fileList.push(fullPath);
        }
      }
      return fileList;
    }

    it("ensures zero Proxy usages or fallback no-op stubs in functions/src/", () => {
      const srcDir = path.resolve(__dirname, "../src");
      const files = getAllSourceFiles(srcDir);
      assert.ok(files.length > 10, "Should scan multiple source files");

      const violations = [];

      for (const filePath of files) {
        const content = fs.readFileSync(filePath, "utf8");
        const relPath = path.relative(srcDir, filePath);

        // 1. Ban new Proxy
        if (/\bnew\s+Proxy\b/.test(content)) {
          violations.push(`${relPath}: contains 'new Proxy' instantiation`);
        }

        // 2. Ban proxyMap or Proxy facades
        if (/\bproxyMap\b/.test(content)) {
          violations.push(`${relPath}: contains 'proxyMap' reference`);
        }

        // 3. Ban no-op fallback stubs like "|| { get: () => null, set: () => {} }"
        if (/\|\|\s*\{\s*get:\s*\(\)\s*=>/i.test(content) || /\|\|\s*\{\s*get\(\)/i.test(content)) {
          violations.push(`${relPath}: contains no-op getter/setter fallback object stub`);
        }
      }

      assert.deepEqual(violations, [], `Proxy or stub violations found in functions/src/:\n${violations.join("\n")}`);
    });
  });
});
