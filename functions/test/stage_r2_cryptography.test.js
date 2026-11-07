/**
 * Stage R2 Test Suite: Real Cryptography, Cloud KMS Envelope Encryption, and Secret Manager
 * 
 * Verifies:
 * 1. Cloud KMS Envelope Encryption with per-user 256-bit DEK in asia-south1
 * 2. Persistent storage of wrapped DEK in Firestore with 5-minute memory cache
 * 3. Lazy re-wrapping upon KMS primary key version rotation
 * 4. AES-256-GCM field encryption with AAD bound to ${recordId}:${fieldName}
 * 5. Tamper-evident authentication tag and cross-field/cross-record swap rejection
 * 6. Server-side Secret Manager integration for HMAC peppers (phone, email, OTP, IP)
 * 7. Key sensitivity: same inputs under different keys produce distinct hashes
 * 8. Strict production fail-closed invariants for missing/fallback secrets and test doubles
 * 9. DPDP Act 2023 instant cryptographic shredding
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");

const {
  getOrCreateUserDek,
  encryptField,
  decryptField,
  encryptRecordFields,
  decryptRecordFields,
  encryptPersonFields,
  decryptPersonFields,
  shredUserDek,
  clearDekCache
} = require("../src/crypto/envelopeCrypto");

const {
  TestKmsProvider,
  setKmsProvider,
  resetKmsProvider,
  getKmsKeyName
} = require("../src/crypto/kmsProvider");

const {
  getSecret,
  setSecretsProvider,
  resetSecretsProvider
} = require("../src/config/secrets");

const {
  computePhoneHash,
  computeEmailHash,
  computeOtpHash,
  hashIp,
  constantTimeEquals
} = require("../src/crypto/serverCrypto");

const { getCryptoRepository, resetTestRepositories } = require("../src/repositories");
const { enforceProductionEnvironmentGuards } = require("../src/config/environmentGuard");

test.beforeEach(() => {
  resetTestRepositories();
  resetKmsProvider();
  resetSecretsProvider();
  clearDekCache();
  delete process.env.NODE_ENV;
  delete process.env.UDHAAR_ENV;
  delete process.env.UDHAAR_SERVER_SECRET;
});

test.describe("Stage R2 — Real Cryptography and Secrets", () => {

  test("1. Cloud KMS Envelope Encryption: 256-bit DEK per user, wrapped in asia-south1 and stored in Firestore", async () => {
    const testKms = new TestKmsProvider();
    setKmsProvider(testKms);

    const uid = "usr_kms_envelope_test_1001";
    const rawDek1 = await getOrCreateUserDek(uid);

    assert.ok(Buffer.isBuffer(rawDek1), "DEK must be a binary Buffer");
    assert.equal(rawDek1.length, 32, "DEK must be a 256-bit (32 bytes) key");

    // Verify wrapped DEK record was persisted in crypto repository
    const cryptoRepo = getCryptoRepository();
    const storedDoc = await cryptoRepo.getUserDekDoc(uid);

    assert.ok(storedDoc, "Wrapped DEK document must exist in repository");
    assert.ok(storedDoc.wrappedDek, "Document must have wrappedDek ciphertext");
    assert.ok(storedDoc.kmsKeyVersion.includes("/cryptoKeyVersions/1"), "Must record KMS key version");
    assert.equal(storedDoc.algorithm, "AES-256-GCM");
    assert.ok(storedDoc.kmsKeyName.includes("locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek"));

    // Verify plaintext DEK is NOT stored in database
    assert.equal(storedDoc.wrappedDek.includes(rawDek1.toString("utf8")), false);

    // Verify in-memory cache returns identical DEK without re-wrapping
    const callsBefore = testKms.unwrapCalls;
    const rawDek2 = await getOrCreateUserDek(uid);
    assert.equal(rawDek1.equals(rawDek2), true, "Cached DEK must match original");
    assert.equal(testKms.unwrapCalls, callsBefore, "Memory cache hit must NOT invoke KMS unwrap");
  });

  test("2. KMS Key Rotation: Lazy re-wrapping on version bump preserves data decryptability", async () => {
    const testKms = new TestKmsProvider();
    setKmsProvider(testKms);

    const uid = "usr_kms_rotation_test_2002";
    const dek = await getOrCreateUserDek(uid);

    // Encrypt confidential PII using current DEK
    const plaintext = "Secret Personal Loan Note";
    const ciphertext = encryptField(plaintext, dek, "rec_rotate:note");
    assert.ok(ciphertext.startsWith("v1:"));

    // Verify initial storage has KMS Version 1
    const cryptoRepo = getCryptoRepository();
    const docV1 = await cryptoRepo.getUserDekDoc(uid);
    assert.ok(docV1.kmsKeyVersion.endsWith("/cryptoKeyVersions/1"));

    // Rotate KMS KEK to Version 2
    testKms.setPrimaryVersion(2);

    // Expire/clear in-memory DEK cache to trigger lazy re-wrap on next access
    clearDekCache();

    // Access DEK: should unwrap using previous key version, recognize rotation, and lazily re-wrap under Version 2
    const rewrappedDek = await getOrCreateUserDek(uid);
    assert.equal(dek.equals(rewrappedDek), true, "User DEK itself remains unchanged");

    // Verify document in repository was lazily updated to KMS Version 2
    const docV2 = await cryptoRepo.getUserDekDoc(uid);
    assert.ok(docV2.kmsKeyVersion.endsWith("/cryptoKeyVersions/2"), "Document must now be wrapped with Version 2");
    assert.notEqual(docV1.wrappedDek, docV2.wrappedDek, "Wrapped ciphertext must differ under new KMS key version");

    // Verify previously encrypted data still decrypts perfectly
    const decrypted = decryptField(ciphertext, rewrappedDek, "rec_rotate:note");
    assert.equal(decrypted, plaintext, "Ciphertext must remain completely decryptable after KMS rotation");
  });

  test("3. AES-256-GCM Field Encryption with AAD bound to ${recordId}:${fieldName}", () => {
    const dek = crypto.randomBytes(32);
    const recordId = "rec_sec_aad_test_3003";

    const originalRecord = {
      recordId,
      principalAmountPaise: 1500000,
      remainingAmountPaise: 1500000,
      counterpartyRole: "BORROWER",
      partyName: "Sunita Deshmukh",
      partyPhone: "+91 94321 09876",
      note: "Quarterly stock inventory purchase",
      upiId: "sunita@icici"
    };

    const encrypted = encryptRecordFields(originalRecord, dek);

    // Confidential fields encrypted
    assert.ok(encrypted.partyName.startsWith("v1:"));
    assert.ok(encrypted.partyPhone.startsWith("v1:"));
    assert.ok(encrypted.note.startsWith("v1:"));
    assert.ok(encrypted.upiId.startsWith("v1:"));

    // Operational numbers remain plain integers
    assert.equal(encrypted.principalAmountPaise, 1500000);
    assert.equal(encrypted.remainingAmountPaise, 1500000);
    assert.equal(encrypted.counterpartyRole, "BORROWER");

    // Legitimate decryption succeeds
    const decrypted = decryptRecordFields(encrypted, dek);
    assert.equal(decrypted.partyName, "Sunita Deshmukh");
    assert.equal(decrypted.partyPhone, "+91 94321 09876");
    assert.equal(decrypted.note, "Quarterly stock inventory purchase");
    assert.equal(decrypted.upiId, "sunita@icici");

    // Attack 1: Cross-field swap attack (e.g. copying partyName ciphertext into note field)
    assert.throws(() => {
      decryptField(encrypted.partyName, dek, `${recordId}:note`);
    }, /Unsupported state or unable to authenticate data/, "Cross-field swap must fail authentication");

    // Attack 2: Cross-record swap attack (e.g. copying partyName into a different record)
    assert.throws(() => {
      decryptField(encrypted.partyName, dek, "rec_victim_4004:partyName");
    }, /Unsupported state or unable to authenticate data/, "Cross-record swap must fail authentication");

    // Attack 3: Tampering with 1 byte of ciphertext or tag
    const tamperedParts = encrypted.partyName.split(":");
    const lastChar = tamperedParts[3].slice(-1);
    tamperedParts[3] = tamperedParts[3].slice(0, -1) + (lastChar === "a" ? "b" : "a");
    const tamperedCiphertext = tamperedParts.join(":");

    assert.throws(() => {
      decryptField(tamperedCiphertext, dek, `${recordId}:partyName`);
    }, /Unsupported state or unable to authenticate data/, "Tampered ciphertext must fail authentication");
  });

  test("4. Server-Side HMAC Peppers & Key Sensitivity: Different keys yield distinct digests", () => {
    const keyA = "secret_pepper_alpha_1111111111111111";
    const keyB = "secret_pepper_beta_2222222222222222";

    const phone = "+91 98765 43210";
    const email = "founder@udhaar.app";
    const code = "782914";
    const flowId = "flow_test_8888";
    const ip = "49.207.180.12";

    // 1. Phone HMAC Blind Index
    const phoneHashA = computePhoneHash(phone, keyA);
    const phoneHashB = computePhoneHash(phone, keyB);
    assert.ok(phoneHashA && phoneHashB);
    assert.notEqual(phoneHashA, phoneHashB, "Different peppers must produce different phone digests");

    // 2. Email HMAC Blind Index
    const emailHashA = computeEmailHash(email, keyA);
    const emailHashB = computeEmailHash(email, keyB);
    assert.ok(emailHashA && emailHashB);
    assert.notEqual(emailHashA, emailHashB, "Different peppers must produce different email digests");

    // 3. OTP Code Hash (HMAC binding code + flowId)
    const otpHashA = computeOtpHash(code, flowId, keyA);
    const otpHashB = computeOtpHash(code, flowId, keyB);
    assert.ok(otpHashA && otpHashB);
    assert.notEqual(otpHashA, otpHashB, "Different server secrets must produce different OTP digests");

    // 4. IP Rate-Limiting Hash
    const ipHashA = hashIp(ip, keyA);
    const ipHashB = hashIp(ip, keyB);
    assert.ok(ipHashA && ipHashB);
    assert.notEqual(ipHashA, ipHashB, "Different server secrets must produce different IP digests");
  });

  test("5. Secret Manager Invariants: Fails closed on missing or fallback secrets in production", () => {
    process.env.NODE_ENV = "production";

    // Missing secret in production throws ERR_MISSING_SECRET
    delete process.env.UDHAAR_SERVER_SECRET;
    assert.throws(() => {
      getSecret("UDHAAR_SERVER_SECRET");
    }, (err) => {
      assert.equal(err.code, "ERR_MISSING_SECRET");
      assert.ok(err.message.includes("Secret 'UDHAAR_SERVER_SECRET' is not configured"));
      return true;
    });

    // Insecure fallback string in production throws ERR_INSECURE_SECRET
    process.env.UDHAAR_SERVER_SECRET = "udhaar_fallback_kek_secret_32_bytes_long!";
    assert.throws(() => {
      getSecret("UDHAAR_SERVER_SECRET");
    }, (err) => {
      assert.equal(err.code, "ERR_INSECURE_SECRET");
      return true;
    });

    // Test secret injection in production throws ERR_ILLEGAL_TEST_PROVIDER
    assert.throws(() => {
      setSecretsProvider({ getSecret: () => "mock_secret" });
    }, (err) => {
      assert.equal(err.code, "ERR_ILLEGAL_TEST_PROVIDER");
      return true;
    });

    // Test KMS provider injection in production throws ERR_ILLEGAL_TEST_PROVIDER
    assert.throws(() => {
      setKmsProvider(new TestKmsProvider());
    }, (err) => {
      assert.equal(err.code, "ERR_ILLEGAL_TEST_PROVIDER");
      return true;
    });
  });

  test("6. DPDP Act 2023: Instant Cryptographic Shredding deletes wrapped DEK permanently", async () => {
    const testKms = new TestKmsProvider();
    setKmsProvider(testKms);

    const uid = "usr_dpdp_shred_test_6006";
    const dek = await getOrCreateUserDek(uid);

    // User data is encrypted
    const secretNotes = "Confidential borrower family history";
    const ciphertext = encryptField(secretNotes, dek, "rec_shred:notes");

    const cryptoRepo = getCryptoRepository();
    const docBefore = await cryptoRepo.getUserDekDoc(uid);
    assert.ok(docBefore, "Wrapped DEK must exist prior to erasure request");

    // Execute DPDP statutory erasure: Cryptographic Shredding
    const shredResult = shredUserDek(uid);
    assert.equal(shredResult.success, true);
    assert.ok(shredResult.shreddedAtMs > 0);

    // Verify wrapped DEK document is destroyed in repository
    const docAfter = await cryptoRepo.getUserDekDoc(uid);
    assert.equal(docAfter, null, "DEK document must be completely eradicated from database");

    // Clear cache to simulate server restart / subsequent request
    clearDekCache();

    // Subsequent retrieval will generate a brand new DEK, making past ciphertext mathematically undecryptable
    const newDek = await getOrCreateUserDek(uid);
    assert.notEqual(dek.toString("hex"), newDek.toString("hex"), "Newly generated DEK must differ");

    assert.throws(() => {
      decryptField(ciphertext, newDek, "rec_shred:notes");
    }, /Unsupported state or unable to authenticate data/, "Past ciphertext must be permanently undecryptable");
  });

});
