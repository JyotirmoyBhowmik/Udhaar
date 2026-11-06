/**
 * Udhaar WebAuthn & Biometric Screen Lock Guard (web/assets/webauthn-lock.js)
 * Implements FIDO2 WebAuthn platform passkey authentication (fingerprint, FaceID, Windows Hello).
 * Zero external dependencies. Strict CSP compliant.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarWebAuthn = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const STORAGE_KEYS = {
    ENABLED: "udhaar_biometric_enabled",
    CREDENTIAL_ID: "udhaar_biometric_credential_id",
    LAST_ACTIVITY: "udhaar_last_active_timestamp",
    LOCK_TIMEOUT_MINS: "udhaar_lock_timeout_mins"
  };

  const DEFAULT_TIMEOUT_MINS = 5;

  function bufferToBase64(buffer) {
    if (typeof Buffer !== "undefined") {
      return Buffer.from(buffer).toString("base64");
    }
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  function base64ToBuffer(b64) {
    if (typeof Buffer !== "undefined") {
      return Buffer.from(b64, "base64");
    }
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  function randomChallenge(bytes = 32) {
    if (typeof crypto !== "undefined" && crypto.getRandomValues) {
      const u8 = new Uint8Array(bytes);
      crypto.getRandomValues(u8);
      return u8.buffer;
    }
    const u8 = new Uint8Array(bytes);
    for (let i = 0; i < bytes; i++) u8[i] = Math.floor(Math.random() * 256);
    return u8.buffer;
  }

  /**
   * Check if platform biometric authenticator is supported
   */
  async function isWebAuthnSupported() {
    if (typeof window === "undefined" || !window.PublicKeyCredential) {
      return false;
    }
    if (typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === "function") {
      try {
        return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
      } catch (_) {
        return false;
      }
    }
    return true;
  }

  /**
   * Register a platform biometric passkey (Fingerprint / FaceID / PIN)
   */
  async function registerBiometricPasskey(userId = "guest_user", userName = "Udhaar User") {
    if (typeof navigator === "undefined" || !navigator.credentials) {
      throw new Error("WebAuthn credentials API not available");
    }

    const challenge = randomChallenge(32);
    const enc = new TextEncoder();
    const userIdBuffer = enc.encode(userId);

    const publicKeyOptions = {
      challenge,
      rp: {
        name: "Udhaar Ledger",
        id: (typeof window !== "undefined" && window.location.hostname) || "localhost"
      },
      user: {
        id: userIdBuffer,
        name: userName,
        displayName: userName
      },
      pubKeyCredParams: [
        { type: "public-key", alg: -7 },   // ES256 (standard)
        { type: "public-key", alg: -257 }  // RS256
      ],
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        userVerification: "required",
        requireResidentKey: false
      },
      timeout: 60000,
      attestation: "none"
    };

    const credential = await navigator.credentials.create({
      publicKey: publicKeyOptions
    });

    if (!credential) {
      throw new Error("Biometric credential registration returned null");
    }

    const credIdBase64 = bufferToBase64(credential.rawId);
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(STORAGE_KEYS.ENABLED, "true");
      localStorage.setItem(STORAGE_KEYS.CREDENTIAL_ID, credIdBase64);
    }

    return {
      success: true,
      credentialId: credIdBase64,
      type: credential.type
    };
  }

  /**
   * Authenticate with registered biometric passkey
   */
  async function authenticateBiometricPasskey(storedCredIdBase64 = null) {
    if (typeof navigator === "undefined" || !navigator.credentials) {
      throw new Error("WebAuthn credentials API not available");
    }

    const credIdStr = storedCredIdBase64 || (typeof localStorage !== "undefined" && localStorage.getItem(STORAGE_KEYS.CREDENTIAL_ID));
    if (!credIdStr) {
      throw new Error("No registered biometric passkey found on this device");
    }

    const challenge = randomChallenge(32);
    const rawIdBuffer = base64ToBuffer(credIdStr);

    const assertionOptions = {
      challenge,
      allowCredentials: [
        {
          id: rawIdBuffer,
          type: "public-key",
          transports: ["internal"]
        }
      ],
      userVerification: "required",
      timeout: 60000
    };

    const assertion = await navigator.credentials.get({
      publicKey: assertionOptions
    });

    if (!assertion) {
      throw new Error("Biometric verification returned null");
    }

    recordActivity();
    return {
      success: true,
      credentialId: bufferToBase64(assertion.rawId)
    };
  }

  function isBiometricEnabled() {
    if (typeof localStorage === "undefined") return false;
    return localStorage.getItem(STORAGE_KEYS.ENABLED) === "true";
  }

  function disableBiometric() {
    if (typeof localStorage === "undefined") return;
    localStorage.removeItem(STORAGE_KEYS.ENABLED);
    localStorage.removeItem(STORAGE_KEYS.CREDENTIAL_ID);
  }

  function recordActivity() {
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.setItem(STORAGE_KEYS.LAST_ACTIVITY, String(Date.now()));
    }
  }

  function shouldAutoLock() {
    if (!isBiometricEnabled()) return false;
    if (typeof sessionStorage === "undefined") return false;

    const lastActive = Number(sessionStorage.getItem(STORAGE_KEYS.LAST_ACTIVITY) || 0);
    if (!lastActive) return false;

    const timeoutMinutes = Number(localStorage.getItem(STORAGE_KEYS.LOCK_TIMEOUT_MINS) || DEFAULT_TIMEOUT_MINS);
    const elapsedMinutes = (Date.now() - lastActive) / (1000 * 60);

    return elapsedMinutes >= timeoutMinutes;
  }

  return {
    STORAGE_KEYS,
    bufferToBase64,
    base64ToBuffer,
    isWebAuthnSupported,
    registerBiometricPasskey,
    authenticateBiometricPasskey,
    isBiometricEnabled,
    disableBiometric,
    recordActivity,
    shouldAutoLock
  };
});
