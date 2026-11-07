/**
 * Client-Side Authenticated Cache Encryption (webapp/src/db/encryptedCache.js)
 * Implements AES-GCM-256 authenticated encryption for sensitive ledger records
 * stored in IndexedDB. Works transparently in modern browsers (WebCrypto) and Node.js.
 */

// Field classification: sensitive fields encrypted at rest in client IndexedDB
const SENSITIVE_FIELDS = ["partyName", "notes", "contactPhone", "upiId", "reminderNote"];

// @ts-ignore
const nodeCrypto = typeof require === "function" ? require("crypto") : null;
// @ts-ignore
const NodeBuffer = typeof Buffer !== "undefined" ? Buffer : null;

class EncryptedCacheManager {
  constructor() {
    /** @type {any} */
    this.sessionKey = null;
    this.isBrowser = typeof window !== "undefined" && typeof window.crypto !== "undefined" && !!window.crypto.subtle;
  }

  /**
   * Initializes or derives a 256-bit AES-GCM session key
   * @param {string} [seed] Optional user session identifier or passphrase
   * @returns {Promise<any>}
   */
  async initKey(seed = "udhaar_session_local_key") {
    if (this.sessionKey) return this.sessionKey;

    if (this.isBrowser) {
      const enc = new TextEncoder();
      const keyMaterial = await window.crypto.subtle.importKey(
        "raw",
        enc.encode(seed.padEnd(32, "0").slice(0, 32)),
        { name: "PBKDF2" },
        false,
        ["deriveKey"]
      );

      this.sessionKey = await window.crypto.subtle.deriveKey(
        {
          name: "PBKDF2",
          salt: enc.encode("udhaar_idb_salt_v2"),
          iterations: 10000,
          hash: "SHA-256"
        },
        keyMaterial,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt", "decrypt"]
      );
    } else {
      // Node.js fallback for unit and contract test environments
      if (nodeCrypto) {
        this.sessionKey = nodeCrypto.scryptSync(seed, "udhaar_idb_salt_v2", 32);
      }
    }

    return this.sessionKey;
  }

  /**
   * Encrypts sensitive fields in a ledger record before persisting to IndexedDB
   * @param {Record<string, any>} record
   * @returns {Promise<Record<string, any>>}
   */
  async encryptRecord(record) {
    if (!record || typeof record !== "object") return record;
    if (!this.sessionKey) {
      await this.initKey();
    }

    const payload = { ...record };
    const fieldsToEncrypt = {};
    let hasSensitive = false;

    for (const field of SENSITIVE_FIELDS) {
      if (payload[field] !== undefined && payload[field] !== null) {
        fieldsToEncrypt[field] = payload[field];
        delete payload[field];
        hasSensitive = true;
      }
    }

    if (!hasSensitive) {
      return payload;
    }

    const plaintext = JSON.stringify(fieldsToEncrypt);
    const syncId = String(record.syncId || record.recordId || "default");

    if (this.isBrowser) {
      const enc = new TextEncoder();
      const iv = window.crypto.getRandomValues(new Uint8Array(12));
      const aad = enc.encode(`udhaar:idb:${syncId}`);

      const ciphertextBuffer = await window.crypto.subtle.encrypt(
        {
          name: "AES-GCM",
          iv,
          additionalData: aad
        },
        /** @type {CryptoKey} */ (this.sessionKey),
        enc.encode(plaintext)
      );

      const combined = new Uint8Array(iv.length + ciphertextBuffer.byteLength);
      combined.set(iv, 0);
      combined.set(new Uint8Array(ciphertextBuffer), iv.length);

      // Base64 encode for IDB safety
      let binary = "";
      for (let i = 0; i < combined.byteLength; i++) {
        binary += String.fromCharCode(combined[i]);
      }
      payload._encPayload = btoa(binary);
      payload._encAad = syncId;
    } else if (nodeCrypto && NodeBuffer) {
      const iv = nodeCrypto.randomBytes(12);
      const cipher = nodeCrypto.createCipheriv("aes-256-gcm", this.sessionKey, iv);
      cipher.setAAD(NodeBuffer.from(`udhaar:idb:${syncId}`, "utf8"));
      const ciphertext = NodeBuffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
      const tag = cipher.getAuthTag();

      payload._encPayload = NodeBuffer.concat([iv, tag, ciphertext]).toString("base64");
      payload._encAad = syncId;
    }

    return payload;
  }

  /**
   * Decrypts encrypted payload fields when reading from IndexedDB
   * @param {Record<string, any>} record
   * @returns {Promise<Record<string, any>>}
   */
  async decryptRecord(record) {
    if (!record || typeof record !== "object" || !record._encPayload) {
      return record;
    }
    if (!this.sessionKey) {
      await this.initKey();
    }

    const syncId = String(record._encAad || record.syncId || record.recordId || "default");

    try {
      let plaintext = "{}";
      if (this.isBrowser) {
        const raw = atob(record._encPayload);
        const bytes = new Uint8Array(raw.length);
        for (let i = 0; i < raw.length; i++) {
          bytes[i] = raw.charCodeAt(i);
        }

        const iv = bytes.slice(0, 12);
        const ciphertextWithTag = bytes.slice(12);
        const enc = new TextEncoder();
        const aad = enc.encode(`udhaar:idb:${syncId}`);

        const decryptedBuffer = await window.crypto.subtle.decrypt(
          {
            name: "AES-GCM",
            iv,
            additionalData: aad
          },
          /** @type {CryptoKey} */ (this.sessionKey),
          ciphertextWithTag
        );

        const dec = new TextDecoder();
        plaintext = dec.decode(decryptedBuffer);
      } else if (nodeCrypto && NodeBuffer) {
        const buf = NodeBuffer.from(record._encPayload, "base64");
        const iv = buf.slice(0, 12);
        const tag = buf.slice(12, 28);
        const ciphertext = buf.slice(28);

        const decipher = nodeCrypto.createDecipheriv("aes-256-gcm", this.sessionKey, iv);
        decipher.setAAD(NodeBuffer.from(`udhaar:idb:${syncId}`, "utf8"));
        decipher.setAuthTag(tag);
        plaintext = NodeBuffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");
      }

      const decryptedFields = JSON.parse(plaintext);
      const restored = { ...record, ...decryptedFields };
      delete restored._encPayload;
      delete restored._encAad;
      return restored;
    } catch (err) {
      console.warn(`[EncryptedCache] Decryption failed for record ${syncId}:`, err);
      return record;
    }
  }

  /**
   * Securely purges cached session encryption keys from memory
   */
  clearKey() {
    this.sessionKey = null;
  }
}

const encryptedCache = new EncryptedCacheManager();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    EncryptedCacheManager,
    encryptedCache,
    SENSITIVE_FIELDS
  };
}
