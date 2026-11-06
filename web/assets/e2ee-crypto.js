/**
 * Udhaar Zero-Knowledge End-to-End Encryption Engine (web/assets/e2ee-crypto.js)
 * Implements client-side AES-GCM 256-bit encryption with HKDF key derivation.
 * URL fragment (#token) serves as the capability encryption secret.
 * Zero external dependencies. Uses W3C Web Cryptography API (crypto.subtle).
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarE2EE = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const HKDF_INFO = "udhaar-record-e2ee-v1";
  const DEFAULT_SALT = "udhaar-salt-2026";

  function getSubtleCrypto() {
    const subtle = (typeof crypto !== "undefined" && crypto.subtle) || globalThis.crypto?.subtle;
    if (!subtle) {
      throw new Error("Web Cryptography API (crypto.subtle) is not available.");
    }
    return subtle;
  }

  function uint8ToBase64(u8) {
    if (typeof Buffer !== "undefined") {
      return Buffer.from(u8).toString("base64");
    }
    let binary = "";
    const len = u8.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(u8[i]);
    }
    return btoa(binary);
  }

  function base64ToUint8(b64) {
    if (typeof Buffer !== "undefined") {
      return new Uint8Array(Buffer.from(b64, "base64"));
    }
    const binary = atob(b64);
    const len = binary.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  /**
   * Derive an AES-GCM 256-bit CryptoKey from a secret capability token via HKDF
   */
  async function deriveKeyFromToken(tokenString, salt = DEFAULT_SALT) {
    if (!tokenString || typeof tokenString !== "string") {
      throw new Error("tokenString must be a non-empty string");
    }
    const subtle = getSubtleCrypto();
    const encoder = new TextEncoder();
    const tokenBytes = encoder.encode(tokenString);

    // Import token as raw key material for HKDF
    const ikm = await subtle.importKey(
      "raw",
      tokenBytes,
      { name: "HKDF" },
      false,
      ["deriveKey"]
    );

    // Derive 256-bit AES-GCM key
    return await subtle.deriveKey(
      {
        name: "HKDF",
        hash: "SHA-256",
        salt: encoder.encode(salt),
        info: encoder.encode(HKDF_INFO)
      },
      ikm,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
  }

  /**
   * Encrypt arbitrary JSON object with AES-GCM 256-bit
   */
  async function encryptJson(dataObj, cryptoKey) {
    if (!dataObj || typeof dataObj !== "object") {
      throw new Error("dataObj must be an object to encrypt");
    }
    const subtle = getSubtleCrypto();
    const iv = (typeof crypto !== "undefined" && crypto.getRandomValues)
      ? crypto.getRandomValues(new Uint8Array(12))
      : globalThis.crypto.getRandomValues(new Uint8Array(12));

    const plaintext = new TextEncoder().encode(JSON.stringify(dataObj));
    const ciphertextBuffer = await subtle.encrypt(
      {
        name: "AES-GCM",
        iv,
        tagLength: 128
      },
      cryptoKey,
      plaintext
    );

    return {
      v: 1,
      iv: uint8ToBase64(iv),
      ciphertext: uint8ToBase64(new Uint8Array(ciphertextBuffer)),
      tagLength: 128
    };
  }

  /**
   * Decrypt AES-GCM 256-bit envelope back to JSON object
   */
  async function decryptJson(envelope, cryptoKey) {
    if (!envelope || !envelope.iv || !envelope.ciphertext) {
      throw new Error("Invalid envelope structure: missing iv or ciphertext");
    }
    const subtle = getSubtleCrypto();
    const iv = base64ToUint8(envelope.iv);
    const ciphertext = base64ToUint8(envelope.ciphertext);

    const decryptedBuffer = await subtle.decrypt(
      {
        name: "AES-GCM",
        iv,
        tagLength: envelope.tagLength || 128
      },
      cryptoKey,
      ciphertext
    );

    const decodedStr = new TextDecoder().decode(decryptedBuffer);
    return JSON.parse(decodedStr);
  }

  /**
   * Encrypt sensitive record fields for zero-knowledge cloud storage
   */
  async function encryptRecordSensitives(record, tokenString) {
    if (!tokenString) return record; // cannot encrypt without secret token
    const cryptoKey = await deriveKeyFromToken(tokenString, record.id || DEFAULT_SALT);

    const sensitives = {
      partyName: record.partyName || "",
      note: record.note || "",
      counterpartyPhone: record.counterpartyPhone || "",
      upiId: record.upiId || "",
      items: record.items || []
    };

    const envelope = await encryptJson(sensitives, cryptoKey);

    return {
      ...record,
      e2eeEnvelope: envelope,
      // Placeholders for zero-knowledge storage
      partyName: "[E2EE Encrypted]",
      note: "[E2EE Encrypted]",
      counterpartyPhone: record.counterpartyPhone ? record.counterpartyPhone.slice(0, 3) + "******" + record.counterpartyPhone.slice(-2) : "",
      upiId: record.upiId ? "e2ee@masked" : "",
      items: []
    };
  }

  /**
   * Decrypt sensitive record fields client-side using token
   */
  async function decryptRecordSensitives(record, tokenString) {
    if (!record || !record.e2eeEnvelope || !tokenString) {
      return record; // nothing to decrypt or missing token
    }

    try {
      const cryptoKey = await deriveKeyFromToken(tokenString, record.id || DEFAULT_SALT);
      const decrypted = await decryptJson(record.e2eeEnvelope, cryptoKey);
      return {
        ...record,
        partyName: decrypted.partyName || record.partyName,
        note: decrypted.note || record.note,
        counterpartyPhone: decrypted.counterpartyPhone || record.counterpartyPhone,
        upiId: decrypted.upiId || record.upiId,
        items: decrypted.items || record.items || [],
        isDecrypted: true
      };
    } catch (err) {
      console.warn("E2EE decryption failed (likely invalid token):", err.message);
      return {
        ...record,
        isDecrypted: false,
        decryptionError: "Invalid capability key"
      };
    }
  }

  return {
    deriveKeyFromToken,
    encryptJson,
    decryptJson,
    encryptRecordSensitives,
    decryptRecordSensitives
  };
});
