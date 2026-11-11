/**
 * Udhaar Web App — Local Firebase JS SDK Initialization Module (webapp/src/api/firebaseSdk.js)
 *
 * Initialises Firebase App, Auth, Cloud Firestore, and App Check using the locally bundled
 * `firebase` npm package (zero CDN dependencies, zero `window.__udhaarFirebaseAuth` dependency).
 */

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyBnlNuS6il5y53n4fUfICJSd3mtmAS-WiM",
  authDomain: "gen-lang-client-0216629575.firebaseapp.com",
  projectId: "gen-lang-client-0216629575",
  storageBucket: "gen-lang-client-0216629575.firebasestorage.app",
  messagingSenderId: "528046585099",
  appId: "1:528046585099:android:468fd8b6debc32fcbade0e"
};

const DEFAULT_RECAPTCHA_V3_SITE_KEY = "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI";

let _appInstance = null;
let _authInstance = null;
let _firestoreInstance = null;
let _appCheckInstance = null;
let _emulatorsConnected = false;

function _resolveBundledSdk() {
  if (typeof globalThis !== "undefined" && globalThis.__UDHAAR_BUNDLED_FIREBASE__) {
    return globalThis.__UDHAAR_BUNDLED_FIREBASE__;
  }
  try {
    const appMod = require("firebase/app");
    const authMod = require("firebase/auth");
    const firestoreMod = require("firebase/firestore");
    let appCheckMod = {};
    try {
      appCheckMod = require("firebase/app-check");
    } catch (_) {}
    return {
      ...appMod,
      ...authMod,
      ...firestoreMod,
      ...appCheckMod
    };
  } catch (_) {
    return null;
  }
}

/**
 * Initialises and returns `{ app, auth, firestore, appCheck, sdk }`.
 */
function initFirebaseSdk(options = {}) {
  const sdk = options.sdk || _resolveBundledSdk();
  const config = options.config || FIREBASE_CONFIG;

  if (!sdk || typeof sdk.initializeApp !== "function") {
    return {
      app: _appInstance,
      auth: _authInstance,
      firestore: _firestoreInstance,
      appCheck: _appCheckInstance,
      sdk: sdk || {}
    };
  }

  if (!_appInstance) {
    const existingApps = typeof sdk.getApps === "function" ? sdk.getApps() : [];
    _appInstance = existingApps.length > 0 ? existingApps[0] : sdk.initializeApp(config);
  }

  if (!_authInstance && typeof sdk.getAuth === "function") {
    try {
      _authInstance = sdk.getAuth(_appInstance);
    } catch (_) {}
  }

  if (!_firestoreInstance && typeof sdk.getFirestore === "function") {
    try {
      _firestoreInstance = sdk.getFirestore(_appInstance);
    } catch (_) {}
  }

  // Initialise App Check in browser environments (skip on emulator unless explicitly requested)
  const isEmulatorEnv = _isEmulatorEnvironment(options);
  if (
    !_appCheckInstance &&
    !isEmulatorEnv &&
    typeof window !== "undefined" &&
    typeof sdk.initializeAppCheck === "function" &&
    typeof sdk.ReCaptchaV3Provider === "function"
  ) {
    try {
      const siteKey =
        options.recaptchaSiteKey ||
        (typeof window !== "undefined" && window.__UDHAAR_APPCHECK_KEY__) ||
        DEFAULT_RECAPTCHA_V3_SITE_KEY;
      _appCheckInstance = sdk.initializeAppCheck(_appInstance, {
        provider: new sdk.ReCaptchaV3Provider(siteKey),
        isTokenAutoRefreshEnabled: true
      });
    } catch (_) {}
  }

  if (isEmulatorEnv && !_emulatorsConnected) {
    _connectEmulators(sdk, options);
  }

  return {
    app: _appInstance,
    auth: _authInstance,
    firestore: _firestoreInstance,
    appCheck: _appCheckInstance,
    sdk
  };
}

function _isEmulatorEnvironment(options = {}) {
  if (options.useEmulators === true) return true;
  if (typeof globalThis !== "undefined" && globalThis.__UDHAAR_EMULATORS__) return true;
  if (typeof process !== "undefined" && process.env) {
    if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_AUTH_EMULATOR_HOST) {
      return true;
    }
  }
  if (typeof window !== "undefined" && window.location) {
    const params = new URLSearchParams(window.location.search || "");
    if (params.get("emulator") === "1") return true;
  }
  return false;
}

function _connectEmulators(sdk, options = {}) {
  const emulatorConfig =
    (typeof globalThis !== "undefined" && globalThis.__UDHAAR_EMULATORS__) || {};

  const authHost =
    options.authEmulatorHost ||
    emulatorConfig.authHost ||
    (typeof process !== "undefined" && process.env && process.env.FIREBASE_AUTH_EMULATOR_HOST) ||
    "127.0.0.1:9099";

  const firestoreHostPort =
    options.firestoreEmulatorHost ||
    emulatorConfig.firestoreHost ||
    (typeof process !== "undefined" && process.env && process.env.FIRESTORE_EMULATOR_HOST) ||
    "127.0.0.1:8080";

  if (_authInstance && typeof sdk.connectAuthEmulator === "function") {
    try {
      const authUrl = authHost.startsWith("http") ? authHost : `http://${authHost}`;
      sdk.connectAuthEmulator(_authInstance, authUrl, { disableWarnings: true });
    } catch (_) {}
  }

  if (_firestoreInstance && typeof sdk.connectFirestoreEmulator === "function") {
    try {
      const [host, portStr] = firestoreHostPort.replace(/^https?:\/\//, "").split(":");
      const port = parseInt(portStr || "8080", 10);
      sdk.connectFirestoreEmulator(_firestoreInstance, host || "127.0.0.1", port);
    } catch (_) {}
  }

  _emulatorsConnected = true;
}

function getFirebaseServices() {
  return initFirebaseSdk();
}

module.exports = {
  FIREBASE_CONFIG,
  initFirebaseSdk,
  getFirebaseServices
};
