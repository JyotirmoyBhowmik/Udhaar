/**
 * Google Cloud KMS Provider & Test Double Interface (functions/src/crypto/kmsProvider.js)
 * 
 * Provides hardware-backed Master Key Encryption Key (KEK) wrapping for per-user DEKs
 * located in GCP region asia-south1.
 * 
 * Production: Uses @google-cloud/kms KeyManagementServiceClient.
 * Testing: Uses an injectable TestKmsProvider double.
 */

const crypto = require("crypto");
const { KeyManagementServiceClient } = require("@google-cloud/kms");
const { isProductionOrStaging } = require("../config/environmentGuard");

class CloudKmsProvider {
  constructor(options = {}) {
    this.client = new KeyManagementServiceClient(options);
  }

  /**
   * Wrap raw 32-byte DEK with Cloud KMS KEK
   * @param {Buffer} rawDekBuffer
   * @param {string} keyName
   * @returns {Promise<{ ciphertext: Buffer, keyVersionName: string }>}
   */
  async wrapKey(rawDekBuffer, keyName) {
    const [result] = await this.client.encrypt({
      name: keyName,
      plaintext: rawDekBuffer
    });
    return {
      ciphertext: Buffer.from(result.ciphertext),
      keyVersionName: result.name
    };
  }

  /**
   * Unwrap ciphertext with Cloud KMS KEK
   * @param {Buffer} ciphertextBuffer
   * @param {string} keyName
   * @returns {Promise<{ plaintext: Buffer }>}
   */
  async unwrapKey(ciphertextBuffer, keyName) {
    const [result] = await this.client.decrypt({
      name: keyName,
      ciphertext: ciphertextBuffer
    });
    return {
      plaintext: Buffer.from(result.plaintext)
    };
  }
}

/**
 * In-memory test double for unit tests without active GCP credentials
 */
class TestKmsProvider {
  constructor(testKek) {
    this.testKek = testKek || crypto.createHash("sha256").update("udhaar_test_kek_for_unit_tests_32_bytes!!").digest();
    this.primaryVersion = 1;
    this.wrapCalls = 0;
    this.unwrapCalls = 0;
  }

  setPrimaryVersion(v) {
    this.primaryVersion = v;
  }

  async wrapKey(rawDekBuffer, keyName) {
    this.wrapCalls++;
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv("aes-256-gcm", this.testKek, iv);
    const encrypted = Buffer.concat([cipher.update(rawDekBuffer), cipher.final()]);
    const tag = cipher.getAuthTag();
    const packed = Buffer.concat([iv, tag, encrypted]);
    return {
      ciphertext: packed,
      keyVersionName: `${keyName}/cryptoKeyVersions/${this.primaryVersion}`
    };
  }

  async unwrapKey(ciphertextBuffer, keyName) {
    this.unwrapCalls++;
    const iv = ciphertextBuffer.subarray(0, 12);
    const tag = ciphertextBuffer.subarray(12, 28);
    const encrypted = ciphertextBuffer.subarray(28);
    const decipher = crypto.createDecipheriv("aes-256-gcm", this.testKek, iv);
    decipher.setAuthTag(tag);
    const plaintext = Buffer.concat([decipher.update(encrypted), decipher.final()]);
    return { plaintext };
  }
}

let activeKmsProvider = null;

function setKmsProvider(provider) {
  if (isProductionOrStaging()) {
    const err = new Error("[KMS] FATAL: Cannot inject test KMS provider in production or staging.");
    err.code = "ERR_ILLEGAL_TEST_PROVIDER";
    throw err;
  }
  activeKmsProvider = provider;
}

function resetKmsProvider() {
  activeKmsProvider = null;
}

function getKmsProvider() {
  if (activeKmsProvider) {
    if (isProductionOrStaging() && activeKmsProvider instanceof TestKmsProvider) {
      const err = new Error("[KMS] FATAL: Test KMS provider active in production or staging.");
      err.code = "ERR_ILLEGAL_TEST_PROVIDER";
      throw err;
    }
    return activeKmsProvider;
  }

  if (isProductionOrStaging()) {
    return new CloudKmsProvider();
  }

  // Default test provider for unit tests
  activeKmsProvider = new TestKmsProvider();
  return activeKmsProvider;
}

function getKmsKeyName() {
  if (process.env.UDHAAR_KMS_KEY_NAME) {
    return process.env.UDHAAR_KMS_KEY_NAME;
  }
  const projectId = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT || "udhaar-prod";
  return `projects/${projectId}/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek`;
}

module.exports = {
  CloudKmsProvider,
  TestKmsProvider,
  getKmsProvider,
  setKmsProvider,
  resetKmsProvider,
  getKmsKeyName
};
