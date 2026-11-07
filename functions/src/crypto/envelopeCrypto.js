/**
 * Cloud KMS Envelope Encryption Engine (functions/src/crypto/envelopeCrypto.js)
 * 
 * Implements field-level envelope encryption with per-user 256-bit Data Encryption Keys (DEKs)
 * wrapped by Google Cloud KMS KEK in region asia-south1.
 * 
 * Invariants:
 * - Hardware KMS wrapping (projects/<id>/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek)
 * - Persistent wrapped DEK storage in Firestore (users/{uid}/crypto/dek)
 * - Lazy re-wrapping on KMS key rotation
 * - AES-256-GCM authenticated encryption with AAD bound to ${recordId}:${fieldName}
 * - Short in-memory DEK caching (5 minutes)
 * - Zero fallback secrets
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");
const { getKmsProvider, getKmsKeyName } = require("./kmsProvider");
const { getCryptoRepository } = require("../repositories");

// Short-lived in-memory DEK cache (5 min TTL) to avoid calling KMS on every field
const inMemoryDekCache = Object.create(null);

/**
 * Backward-compatible proxy object for legacy tests inspecting inMemoryKeyStore
 */
const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const repo = getCryptoRepository();
    const map = (repo && repo.deks) || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryKeyStore = proxyMap(() => getCryptoRepository().deks);

/**
 * Get or create a per-user Data Encryption Key (DEK).
 * Generates a random 256-bit DEK, wraps it with Cloud KMS in asia-south1,
 * stores it in Firestore, and caches it in memory for 5 minutes.
 * Performs lazy re-wrapping if the KMS key version has rotated.
 * 
 * @param {string} uid - Unique user identifier
 * @returns {Promise<Buffer>} Unwrapped 32-byte DEK buffer
 */
async function getOrCreateUserDek(uid) {
  const now = Date.now();

  // 1. Check in-memory TTL cache
  const cached = inMemoryDekCache[uid];
  if (cached && cached.expiresAt > now) {
    return cached.dek;
  }

  const cryptoRepo = getCryptoRepository();
  const kmsProvider = getKmsProvider();
  const kmsKeyName = getKmsKeyName();

  // 2. Fetch wrapped DEK record from persistent repository
  let keyRecord = await cryptoRepo.getUserDekDoc(uid);

  if (!keyRecord) {
    // Generate fresh random 256-bit DEK
    const rawDek = crypto.randomBytes(32);

    // Wrap raw DEK with Cloud KMS KEK
    const { ciphertext, keyVersionName } = await kmsProvider.wrapKey(rawDek, kmsKeyName);

    keyRecord = {
      wrappedDek: ciphertext.toString("base64"),
      kmsKeyName,
      kmsKeyVersion: keyVersionName,
      algorithm: "AES-256-GCM",
      createdAtMs: now,
      updatedAtMs: now
    };

    await cryptoRepo.saveUserDekDoc(uid, keyRecord);

    inMemoryDekCache[uid] = {
      dek: rawDek,
      expiresAt: now + USER_QUOTAS.DEK_CACHE_TTL_MS
    };

    return rawDek;
  }

  // 3. Unwrap existing wrapped DEK using Cloud KMS
  const wrappedBuf = Buffer.from(keyRecord.wrappedDek, "base64");
  const { plaintext: rawDek } = await kmsProvider.unwrapKey(wrappedBuf, keyRecord.kmsKeyName || kmsKeyName);

  // 4. Lazy Re-wrap on Key Rotation: Check if KMS key version has advanced
  if (kmsProvider.primaryVersion !== undefined) {
    const currentPrimaryVersion = `${kmsKeyName}/cryptoKeyVersions/${kmsProvider.primaryVersion}`;
    if (keyRecord.kmsKeyVersion && keyRecord.kmsKeyVersion !== currentPrimaryVersion) {
      // Re-wrap DEK under the newest primary KMS key version
      const rewrapped = await kmsProvider.wrapKey(rawDek, kmsKeyName);
      keyRecord.wrappedDek = rewrapped.ciphertext.toString("base64");
      keyRecord.kmsKeyVersion = rewrapped.keyVersionName;
      keyRecord.updatedAtMs = now;
      await cryptoRepo.saveUserDekDoc(uid, keyRecord);
    }
  }

  // 5. Cache unwrapped DEK
  inMemoryDekCache[uid] = {
    dek: rawDek,
    expiresAt: now + USER_QUOTAS.DEK_CACHE_TTL_MS
  };

  return rawDek;
}

/**
 * Encrypt a single field using AES-256-GCM with Additional Authenticated Data (AAD)
 * 
 * @param {*} plaintext
 * @param {Buffer} dek - 32-byte Data Encryption Key
 * @param {string} [aad=""] - Additional Authenticated Data (${entityId}:${fieldName})
 * @returns {string|null} Ciphertext in format v1:iv:tag:ciphertext
 */
function encryptField(plaintext, dek, aad = "") {
  if (plaintext === null || plaintext === undefined) {
    return null;
  }

  const textToEncrypt = typeof plaintext === "object" ? JSON.stringify(plaintext) : String(plaintext);
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", dek, iv);

  if (aad) {
    cipher.setAAD(Buffer.from(String(aad), "utf8"));
  }

  const encrypted = Buffer.concat([cipher.update(Buffer.from(textToEncrypt, "utf8")), cipher.final()]);
  const tag = cipher.getAuthTag();

  return `v1:${iv.toString("hex")}:${tag.toString("hex")}:${encrypted.toString("hex")}`;
}

/**
 * Decrypt a single field using AES-256-GCM with Additional Authenticated Data (AAD)
 * 
 * @param {string} ciphertextStr - Ciphertext in format v1:iv:tag:ciphertext
 * @param {Buffer} dek - 32-byte Data Encryption Key
 * @param {string} [aad=""] - Additional Authenticated Data (${entityId}:${fieldName})
 * @returns {*} Plaintext or parsed JSON
 */
function decryptField(ciphertextStr, dek, aad = "") {
  if (!ciphertextStr || typeof ciphertextStr !== "string") {
    return ciphertextStr;
  }

  if (!ciphertextStr.startsWith("v1:")) {
    return ciphertextStr; // Unencrypted legacy or plaintext
  }

  const parts = ciphertextStr.split(":");
  if (parts.length !== 4) {
    return ciphertextStr;
  }

  const iv = Buffer.from(parts[1], "hex");
  const tag = Buffer.from(parts[2], "hex");
  const ciphertext = Buffer.from(parts[3], "hex");

  try {
    const decipher = crypto.createDecipheriv("aes-256-gcm", dek, iv);
    if (aad) {
      decipher.setAAD(Buffer.from(String(aad), "utf8"));
    }
    decipher.setAuthTag(tag);
    const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");

    if (decrypted.startsWith("{") || decrypted.startsWith("[")) {
      try {
        return JSON.parse(decrypted);
      } catch (_) {}
    }
    return decrypted;
  } catch (err) {
    // If aad contains a field name (e.g. "rec_123:partyName"), attempt fallback with entityId ("rec_123")
    // for backward-compatibility with records encrypted prior to field-level AAD binding.
    if (aad && aad.includes(":")) {
      const entityId = aad.split(":")[0];
      try {
        const decipher2 = crypto.createDecipheriv("aes-256-gcm", dek, iv);
        decipher2.setAAD(Buffer.from(String(entityId), "utf8"));
        decipher2.setAuthTag(tag);
        const decrypted2 = Buffer.concat([decipher2.update(ciphertext), decipher2.final()]).toString("utf8");
        if (decrypted2.startsWith("{") || decrypted2.startsWith("[")) {
          try {
            return JSON.parse(decrypted2);
          } catch (_) {}
        }
        return decrypted2;
      } catch (_) {}
    }
    throw err;
  }
}

/**
 * Encrypt sensitive record fields with AAD bound to ${recordId}:${fieldName}
 */
function encryptRecordFields(record, dek) {
  const enc = { ...record };
  const id = enc.recordId || enc.id || "";
  if (enc.partyName !== undefined) enc.partyName = encryptField(enc.partyName, dek, `${id}:partyName`);
  if (enc.partyPhone !== undefined) enc.partyPhone = encryptField(enc.partyPhone, dek, `${id}:partyPhone`);
  if (enc.note !== undefined) enc.note = encryptField(enc.note, dek, `${id}:note`);
  if (enc.items !== undefined) enc.items = encryptField(enc.items, dek, `${id}:items`);
  if (enc.upiId !== undefined) enc.upiId = encryptField(enc.upiId, dek, `${id}:upiId`);
  return enc;
}

/**
 * Decrypt sensitive record fields with AAD bound to ${recordId}:${fieldName}
 */
function decryptRecordFields(record, dek) {
  const dec = { ...record };
  const id = dec.recordId || dec.id || "";
  if (dec.partyName !== undefined) dec.partyName = decryptField(dec.partyName, dek, `${id}:partyName`);
  if (dec.partyPhone !== undefined) dec.partyPhone = decryptField(dec.partyPhone, dek, `${id}:partyPhone`);
  if (dec.note !== undefined) dec.note = decryptField(dec.note, dek, `${id}:note`);
  if (dec.items !== undefined) dec.items = decryptField(dec.items, dek, `${id}:items`);
  if (dec.upiId !== undefined) dec.upiId = decryptField(dec.upiId, dek, `${id}:upiId`);
  return dec;
}

/**
 * Encrypt sensitive person fields with AAD bound to ${personId}:${fieldName}
 */
function encryptPersonFields(person, dek) {
  const enc = { ...person };
  const id = enc.personId || enc.id || "";
  if (enc.name !== undefined) enc.name = encryptField(enc.name, dek, `${id}:name`);
  if (enc.phone !== undefined) enc.phone = encryptField(enc.phone, dek, `${id}:phone`);
  if (enc.email !== undefined) enc.email = encryptField(enc.email, dek, `${id}:email`);
  if (enc.notes !== undefined) enc.notes = encryptField(enc.notes, dek, `${id}:notes`);
  return enc;
}

/**
 * Decrypt sensitive person fields with AAD bound to ${personId}:${fieldName}
 */
function decryptPersonFields(person, dek) {
  const dec = { ...person };
  const id = dec.personId || dec.id || "";
  if (dec.name !== undefined) dec.name = decryptField(dec.name, dek, `${id}:name`);
  if (dec.phone !== undefined) dec.phone = decryptField(dec.phone, dek, `${id}:phone`);
  if (dec.email !== undefined) dec.email = decryptField(dec.email, dek, `${id}:email`);
  if (dec.notes !== undefined) dec.notes = decryptField(dec.notes, dek, `${id}:notes`);
  return dec;
}

/**
 * Cryptographically shred a user's DEK (Digital Personal Data Protection Act 2023)
 * Deletes the wrapped DEK from Firestore and purges memory cache.
 * All historical ciphertexts encrypted with this DEK become permanently unrecoverable.
 */
function shredUserDek(uid) {
  delete inMemoryDekCache[uid];
  const repo = getCryptoRepository();
  if (typeof repo.deleteUserDekDoc === "function") {
    repo.deleteUserDekDoc(uid);
  }
  return { success: true, shreddedAtMs: Date.now() };
}

/**
 * Clears in-memory DEK cache (e.g. simulating cache expiration or memory pressure)
 */
function clearDekCache() {
  for (const k of Object.keys(inMemoryDekCache)) {
    delete inMemoryDekCache[k];
  }
}

module.exports = {
  getOrCreateUserDek,
  encryptField,
  decryptField,
  encryptRecordFields,
  decryptRecordFields,
  encryptPersonFields,
  decryptPersonFields,
  shredUserDek,
  clearDekCache,
  _inMemoryKeyStore
};
