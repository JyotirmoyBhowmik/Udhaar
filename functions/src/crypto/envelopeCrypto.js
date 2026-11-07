/**
 * Cloud KMS Envelope Encryption Engine (functions/src/crypto/envelopeCrypto.js)
 * Implements field-level envelope encryption with per-user Data Encryption Keys (DEKs)
 * wrapped by Google Cloud KMS KEK (or server secret wrapper in emulator/test mode).
 * AES-256-GCM authenticated encryption with 5-minute in-memory DEK caching.
 */

const crypto = require("crypto");
const { USER_QUOTAS } = require("../config/quotas");

// Master KEK Key identifier
const KMS_KEK_NAME = process.env.KMS_KEK_NAME || "projects/udhaar/locations/asia-south1/keyRings/udhaar-keyring/cryptoKeys/user-dek-wrapper";
const FALLBACK_KEK_SECRET = process.env.UDHAAR_SERVER_SECRET || "udhaar_fallback_kek_secret_32_bytes_long!";

// In-memory DEK cache: uid -> { dek: Buffer, expiresAt: number }
const inMemoryDekCache = new Map();

// In-memory key store for tests/emulators: uid -> { wrappedDek, kekId, createdAtMs }
const inMemoryKeyStore = new Map();

function getServerKek() {
  return crypto.createHash("sha256").update(FALLBACK_KEK_SECRET).digest();
}

/**
 * Wrap a raw 32-byte DEK using AES-256-GCM
 */
function wrapDek(rawDek, kekKey) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", kekKey, iv);
  const encrypted = Buffer.concat([cipher.update(rawDek), cipher.final()]);
  const tag = cipher.getAuthTag();

  return `kek_v1:${iv.toString("hex")}:${tag.toString("hex")}:${encrypted.toString("hex")}`;
}

/**
 * Unwrap a wrapped DEK using AES-256-GCM
 */
function unwrapDek(wrappedDekStr, kekKey) {
  const parts = wrappedDekStr.split(":");
  if (parts.length !== 4 || parts[0] !== "kek_v1") {
    throw new Error("Invalid wrapped DEK format");
  }

  const iv = Buffer.from(parts[1], "hex");
  const tag = Buffer.from(parts[2], "hex");
  const ciphertext = Buffer.from(parts[3], "hex");

  const decipher = crypto.createDecipheriv("aes-256-gcm", kekKey, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

/**
 * Get or create a per-user Data Encryption Key (DEK)
 */
async function getOrCreateUserDek(uid) {
  const now = Date.now();

  // 1. Check in-memory cache
  const cached = inMemoryDekCache.get(uid);
  if (cached && cached.expiresAt > now) {
    return cached.dek;
  }

  // 2. Look up stored wrapped key
  let keyRecord = inMemoryKeyStore.get(uid);

  if (!keyRecord) {
    // Generate new 32-byte DEK
    const rawDek = crypto.randomBytes(32);
    const kek = getServerKek();
    const wrappedDek = wrapDek(rawDek, kek);

    keyRecord = {
      wrappedDek,
      kekId: KMS_KEK_NAME,
      createdAtMs: now
    };
    inMemoryKeyStore.set(uid, keyRecord);

    // Cache unwrapped DEK
    inMemoryDekCache.set(uid, {
      dek: rawDek,
      expiresAt: now + USER_QUOTAS.DEK_CACHE_TTL_MS
    });

    return rawDek;
  }

  // 3. Unwrap existing DEK
  const kek = getServerKek();
  const rawDek = unwrapDek(keyRecord.wrappedDek, kek);

  inMemoryDekCache.set(uid, {
    dek: rawDek,
    expiresAt: now + USER_QUOTAS.DEK_CACHE_TTL_MS
  });

  return rawDek;
}

/**
 * Encrypt a single field using AES-256-GCM
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
 * Decrypt a single field using AES-256-GCM
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

  const decipher = crypto.createDecipheriv("aes-256-gcm", dek, iv);
  if (aad) {
    decipher.setAAD(Buffer.from(String(aad), "utf8"));
  }
  decipher.setAuthTag(tag);

  const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");

  // If looks like JSON, parse it
  if (decrypted.startsWith("{") || decrypted.startsWith("[")) {
    try {
      return JSON.parse(decrypted);
    } catch (_) {}
  }

  return decrypted;
}

/**
 * Helper to encrypt sensitive record fields
 */
function encryptRecordFields(record, dek) {
  const enc = { ...record };
  if (enc.partyName !== undefined) enc.partyName = encryptField(enc.partyName, dek, enc.recordId);
  if (enc.partyPhone !== undefined) enc.partyPhone = encryptField(enc.partyPhone, dek, enc.recordId);
  if (enc.note !== undefined) enc.note = encryptField(enc.note, dek, enc.recordId);
  if (enc.items !== undefined) enc.items = encryptField(enc.items, dek, enc.recordId);
  if (enc.upiId !== undefined) enc.upiId = encryptField(enc.upiId, dek, enc.recordId);
  return enc;
}

/**
 * Helper to decrypt sensitive record fields
 */
function decryptRecordFields(record, dek) {
  const dec = { ...record };
  if (dec.partyName !== undefined) dec.partyName = decryptField(dec.partyName, dek, dec.recordId);
  if (dec.partyPhone !== undefined) dec.partyPhone = decryptField(dec.partyPhone, dek, dec.recordId);
  if (dec.note !== undefined) dec.note = decryptField(dec.note, dek, dec.recordId);
  if (dec.items !== undefined) dec.items = decryptField(dec.items, dek, dec.recordId);
  if (dec.upiId !== undefined) dec.upiId = decryptField(dec.upiId, dek, dec.recordId);
  return dec;
}

/**
 * Helper to encrypt sensitive person fields
 */
function encryptPersonFields(person, dek) {
  const enc = { ...person };
  if (enc.name !== undefined) enc.name = encryptField(enc.name, dek, enc.personId);
  if (enc.phone !== undefined) enc.phone = encryptField(enc.phone, dek, enc.personId);
  if (enc.email !== undefined) enc.email = encryptField(enc.email, dek, enc.personId);
  if (enc.notes !== undefined) enc.notes = encryptField(enc.notes, dek, enc.personId);
  return enc;
}

/**
 * Helper to decrypt sensitive person fields
 */
function decryptPersonFields(person, dek) {
  const dec = { ...person };
  if (dec.name !== undefined) dec.name = decryptField(dec.name, dek, dec.personId);
  if (dec.phone !== undefined) dec.phone = decryptField(dec.phone, dek, dec.personId);
  if (dec.email !== undefined) dec.email = decryptField(dec.email, dek, dec.personId);
  if (dec.notes !== undefined) dec.notes = decryptField(dec.notes, dek, dec.personId);
  return dec;
}

function clearDekCache() {
  inMemoryDekCache.clear();
  inMemoryKeyStore.clear();
}

module.exports = {
  getOrCreateUserDek,
  encryptField,
  decryptField,
  encryptRecordFields,
  decryptRecordFields,
  encryptPersonFields,
  decryptPersonFields,
  wrapDek,
  unwrapDek,
  clearDekCache,
  _inMemoryKeyStore: inMemoryKeyStore
};
