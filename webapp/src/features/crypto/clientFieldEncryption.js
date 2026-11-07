/**
 * Udhaar Client-Side Field Encryption (webapp/src/features/crypto/clientFieldEncryption.js)
 * 
 * Provides optional Zero-Knowledge client-side AES-256-GCM encryption
 * with PBKDF2 key derivation for sensitive fields:
 * - partyName
 * - partyPhone
 * - note
 * - upiId
 * 
 * Works in both modern browser Web Crypto API and Node.js test environments.
 */

const SENSITIVE_FIELDS = ["partyName", "partyPhone", "note", "upiId"];
const PBKDF2_ITERATIONS = 100000;
const SALT_BYTES = 16;
const IV_BYTES = 12;

/**
 * Derive AES-GCM-256 CryptoKey from user passphrase and salt.
 */
async function deriveKeyFromPassphrase(passphrase, saltBytes) {
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      enc.encode(passphrase),
      { name: "PBKDF2" },
      false,
      ["deriveKey"]
    );
    return crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        salt: saltBytes,
        iterations: PBKDF2_ITERATIONS,
        hash: "SHA-256"
      },
      keyMaterial,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
  } else {
    // Node.js crypto fallback
    const nodeCrypto = require("node:crypto");
    const derived = nodeCrypto.pbkdf2Sync(passphrase, saltBytes, PBKDF2_ITERATIONS, 32, "sha256");
    return derived;
  }
}

/**
 * Encrypt a plaintext string using AES-256-GCM.
 * Output format: base64(salt[16] || iv[12] || ciphertext || tag[16])
 */
async function encryptField(plainText, passphrase) {
  if (!plainText || typeof plainText !== "string") return plainText;
  if (!passphrase || typeof passphrase !== "string") return plainText;

  if (typeof crypto !== "undefined" && crypto.subtle) {
    const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
    const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
    const key = await deriveKeyFromPassphrase(passphrase, salt);
    const enc = new TextEncoder();
    const cipherBuffer = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv },
      key,
      enc.encode(plainText)
    );

    const combined = new Uint8Array(SALT_BYTES + IV_BYTES + cipherBuffer.byteLength);
    combined.set(salt, 0);
    combined.set(iv, SALT_BYTES);
    combined.set(new Uint8Array(cipherBuffer), SALT_BYTES + IV_BYTES);

    return "enc:v1:" + btoa(String.fromCharCode.apply(null, combined));
  } else {
    // Node.js crypto fallback
    const nodeCrypto = require("node:crypto");
    const salt = nodeCrypto.randomBytes(SALT_BYTES);
    const iv = nodeCrypto.randomBytes(IV_BYTES);
    const key = nodeCrypto.pbkdf2Sync(passphrase, salt, PBKDF2_ITERATIONS, 32, "sha256");
    const cipher = nodeCrypto.createCipheriv("aes-256-gcm", key, iv);
    const encrypted = Buffer.concat([cipher.update(plainText, "utf8"), cipher.final()]);
    const tag = cipher.getAuthTag();
    const combined = Buffer.concat([salt, iv, encrypted, tag]);
    return "enc:v1:" + combined.toString("base64");
  }
}

/**
 * Decrypt an encrypted field string.
 */
async function decryptField(encText, passphrase) {
  if (!encText || typeof encText !== "string" || !encText.startsWith("enc:v1:")) {
    return encText;
  }
  if (!passphrase || typeof passphrase !== "string") {
    return "[Encrypted Field]";
  }

  const base64Data = encText.slice("enc:v1:".length);

  try {
    if (typeof crypto !== "undefined" && crypto.subtle) {
      const rawStr = atob(base64Data);
      const combined = new Uint8Array(rawStr.length);
      for (let i = 0; i < rawStr.length; i++) {
        combined[i] = rawStr.charCodeAt(i);
      }

      const salt = combined.slice(0, SALT_BYTES);
      const iv = combined.slice(SALT_BYTES, SALT_BYTES + IV_BYTES);
      const cipherBuffer = combined.slice(SALT_BYTES + IV_BYTES);

      const key = await deriveKeyFromPassphrase(passphrase, salt);
      const dec = new TextDecoder();
      const plainBuffer = await crypto.subtle.decrypt(
        { name: "AES-GCM", iv },
        key,
        cipherBuffer
      );
      return dec.decode(plainBuffer);
    } else {
      // Node.js fallback
      const nodeCrypto = require("node:crypto");
      const combined = Buffer.from(base64Data, "base64");
      const salt = combined.subarray(0, SALT_BYTES);
      const iv = combined.subarray(SALT_BYTES, SALT_BYTES + IV_BYTES);
      const tag = combined.subarray(combined.length - 16);
      const ciphertext = combined.subarray(SALT_BYTES + IV_BYTES, combined.length - 16);

      const key = nodeCrypto.pbkdf2Sync(passphrase, salt, PBKDF2_ITERATIONS, 32, "sha256");
      const decipher = nodeCrypto.createDecipheriv("aes-256-gcm", key, iv);
      decipher.setAuthTag(tag);
      const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
      return decrypted.toString("utf8");
    }
  } catch (err) {
    return "[Decryption Failed - Invalid Passphrase]";
  }
}

/**
 * Encrypt sensitive fields of a record object before sending to Firestore.
 */
async function encryptRecordFields(record, passphrase) {
  if (!passphrase) return { ...record };
  const cloned = { ...record };
  for (const field of SENSITIVE_FIELDS) {
    if (cloned[field] && typeof cloned[field] === "string") {
      cloned[field] = await encryptField(cloned[field], passphrase);
    }
  }
  cloned.isClientEncrypted = true;
  return cloned;
}

/**
 * Decrypt sensitive fields of a record object after fetching from Firestore.
 */
async function decryptRecordFields(record, passphrase) {
  if (!record || !record.isClientEncrypted) return record;
  const cloned = { ...record };
  for (const field of SENSITIVE_FIELDS) {
    if (cloned[field] && typeof cloned[field] === "string") {
      cloned[field] = await decryptField(cloned[field], passphrase);
    }
  }
  return cloned;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    SENSITIVE_FIELDS,
    encryptField,
    decryptField,
    encryptRecordFields,
    decryptRecordFields
  };
}
