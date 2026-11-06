/**
 * Udhaar Cryptographic Merkle Hash Chain Engine (web/assets/merkle-chain.js)
 * Implements a verifiable, tamper-evident cryptographic hash chain for transaction state changes.
 * Uses W3C Web Cryptography API (crypto.subtle).
 * Zero external dependencies. Strict CSP compliant.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarMerkle = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const GENESIS_PREV_HASH = "0".repeat(64);

  /**
   * Deterministic JSON serializer (sorts keys recursively)
   */
  function canonicalJson(obj) {
    if (obj === null || typeof obj !== "object") {
      return JSON.stringify(obj);
    }
    if (Array.isArray(obj)) {
      return "[" + obj.map(canonicalJson).join(",") + "]";
    }
    const sortedKeys = Object.keys(obj).sort();
    const parts = [];
    for (const key of sortedKeys) {
      if (obj[key] !== undefined) {
        parts.push(JSON.stringify(key) + ":" + canonicalJson(obj[key]));
      }
    }
    return "{" + parts.join(",") + "}";
  }

  /**
   * Compute SHA-256 hex digest using Web Crypto API
   */
  async function sha256Hex(input) {
    const encoder = new TextEncoder();
    const data = typeof input === "string" ? encoder.encode(input) : input;
    const cryptoSubtle = (typeof crypto !== "undefined" && crypto.subtle) || globalThis.crypto?.subtle;
    if (!cryptoSubtle) {
      throw new Error("Web Cryptography API (crypto.subtle) is not available in this environment.");
    }
    const hashBuffer = await cryptoSubtle.digest("SHA-256", data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, "0")).join("");
  }

  /**
   * Create the genesis block (Block 0) of a record's audit chain
   */
  async function createGenesisBlock(recordId, createdAtMs, initialData = {}) {
    if (!recordId) throw new Error("recordId is required for genesis block");
    const timestamp = Number(createdAtMs) || Date.now();
    const payloadHash = await sha256Hex(canonicalJson(initialData));
    const actionType = "GENESIS";
    const rawContent = `${GENESIS_PREV_HASH}:${actionType}:${recordId}:${timestamp}:${payloadHash}`;
    const hash = await sha256Hex(rawContent);

    return {
      index: 0,
      recordId: String(recordId),
      timestampMs: timestamp,
      actionType,
      payloadSummary: initialData.note || "Record initialized",
      payloadHash,
      prevHash: GENESIS_PREV_HASH,
      hash
    };
  }

  /**
   * Append a new tamper-evident block to an existing chain
   */
  async function appendMerkleBlock(prevBlock, actionType, timestampMs, payload = {}) {
    if (!prevBlock || !prevBlock.hash) {
      throw new Error("Valid prevBlock is required to append to chain");
    }
    if (!actionType) {
      throw new Error("actionType is required");
    }

    const index = (prevBlock.index || 0) + 1;
    const recordId = prevBlock.recordId;
    const timestamp = Number(timestampMs) || Date.now();
    const payloadHash = await sha256Hex(canonicalJson(payload));
    const prevHash = prevBlock.hash;
    const rawContent = `${prevHash}:${actionType}:${recordId}:${timestamp}:${payloadHash}`;
    const hash = await sha256Hex(rawContent);

    let summary = "";
    if (actionType === "CONFIRM") {
      summary = "Confirmed by counterparty via web link";
    } else if (actionType === "PAYMENT") {
      summary = `Partial payment recorded: ₹${payload.amount || (payload.paise ? (payload.paise / 100).toFixed(2) : 0)} via ${payload.mode || "UPI"}`;
    } else if (actionType === "DISPUTE") {
      summary = `Disputed: ${payload.reason || "Terms mismatch"}`;
    } else if (actionType === "EDIT") {
      summary = "Record terms updated";
    } else {
      summary = payload.note || `${actionType} action executed`;
    }

    return {
      index,
      recordId,
      timestampMs: timestamp,
      actionType,
      payloadSummary: summary,
      payloadHash,
      prevHash,
      hash
    };
  }

  /**
   * Verify an entire Merkle hash chain from Block 0 to Block N
   */
  async function verifyMerkleChain(chain) {
    if (!Array.isArray(chain) || chain.length === 0) {
      return {
        isValid: false,
        length: 0,
        unbrokenLength: 0,
        brokenIndex: null,
        error: "Chain is empty or not an array"
      };
    }

    for (let i = 0; i < chain.length; i++) {
      const block = chain[i];
      if (!block || typeof block !== "object") {
        return {
          isValid: false,
          length: chain.length,
          unbrokenLength: i,
          brokenIndex: i,
          error: `Block ${i} is null or malformed`
        };
      }

      if (block.index !== i) {
        return {
          isValid: false,
          length: chain.length,
          unbrokenLength: i,
          brokenIndex: i,
          error: `Block ${i} index mismatch: expected ${i}, found ${block.index}`
        };
      }

      // Genesis check
      if (i === 0) {
        if (block.prevHash !== GENESIS_PREV_HASH) {
          return {
            isValid: false,
            length: chain.length,
            unbrokenLength: 0,
            brokenIndex: 0,
            error: `Genesis block prevHash must be 64 zeros`
          };
        }
        const expectedGenesisRaw = `${GENESIS_PREV_HASH}:${block.actionType}:${block.recordId}:${block.timestampMs}:${block.payloadHash}`;
        const expectedGenesisHash = await sha256Hex(expectedGenesisRaw);
        if (expectedGenesisHash !== block.hash) {
          return {
            isValid: false,
            length: chain.length,
            unbrokenLength: 0,
            brokenIndex: 0,
            error: `Genesis block hash mismatch (data modified)`
          };
        }
      } else {
        // Subsequent blocks
        const prevBlock = chain[i - 1];
        if (block.prevHash !== prevBlock.hash) {
          return {
            isValid: false,
            length: chain.length,
            unbrokenLength: i,
            brokenIndex: i,
            error: `Block ${i} prevHash does not match Block ${i - 1} hash (broken linkage)`
          };
        }

        const expectedRaw = `${prevBlock.hash}:${block.actionType}:${block.recordId}:${block.timestampMs}:${block.payloadHash}`;
        const expectedHash = await sha256Hex(expectedRaw);
        if (expectedHash !== block.hash) {
          return {
            isValid: false,
            length: chain.length,
            unbrokenLength: i,
            brokenIndex: i,
            error: `Block ${i} content hash mismatch (payload or metadata tampered)`
          };
        }
      }
    }

    return {
      isValid: true,
      length: chain.length,
      unbrokenLength: chain.length,
      brokenIndex: null,
      error: null
    };
  }

  /**
   * Format human-readable short seal for UI & Digital Parchi
   */
  function formatMerkleSeal(block) {
    if (!block || !block.hash) return "UNSEALED";
    const shortHash = block.hash.slice(0, 8).toUpperCase() + "..." + block.hash.slice(-4).toUpperCase();
    return `SEAL#${block.index}:${shortHash}`;
  }

  return {
    GENESIS_PREV_HASH,
    canonicalJson,
    sha256Hex,
    createGenesisBlock,
    appendMerkleBlock,
    verifyMerkleChain,
    formatMerkleSeal
  };
});
