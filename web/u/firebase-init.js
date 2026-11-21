/**
 * Udhaar Web Guest Portal — Firebase Initialization Provider (web/u/firebase-init.js)
 *
 * Single provider of the Cloud Firestore instance (`db`) on the (default) database.
 * No other web code may initialize or retrieve a Firestore instance directly.
 */

const FIREBASE_GUEST_CONFIG = {
  apiKey: "AIzaSyBnlNuS6il5y53n4fUfICJSd3mtmAS-WiM",
  authDomain: "gen-lang-client-0216629575.firebaseapp.com",
  projectId: "gen-lang-client-0216629575",
  storageBucket: "gen-lang-client-0216629575.firebasestorage.app",
  messagingSenderId: "528046585099",
  appId: "1:528046585099:android:468fd8b6debc32fcbade0e"
};

function resolveFirebaseSdk() {
  if (typeof globalThis !== "undefined" && globalThis.__UDHAAR_BUNDLED_FIREBASE__) {
    return globalThis.__UDHAAR_BUNDLED_FIREBASE__;
  }
  if (typeof require === "function") {
    try {
      const appMod = require("firebase/app");
      const authMod = require("firebase/auth");
      const firestoreMod = require("firebase/firestore");
      return { ...appMod, ...authMod, ...firestoreMod };
    } catch (_) {}
  }
  return null;
}

let _initialized = null;

function initFirebase() {
  if (_initialized) return _initialized;
  const sdk = resolveFirebaseSdk();
  if (!sdk || typeof sdk.initializeApp !== "function") {
    return { app: null, auth: null, db: null, sdk: null };
  }
  const apps = typeof sdk.getApps === "function" ? sdk.getApps() : [];
  const app = apps.length > 0 ? apps[0] : sdk.initializeApp(FIREBASE_GUEST_CONFIG);
  const auth = typeof sdk.getAuth === "function" ? sdk.getAuth(app) : null;

  // Single provider: Spark free tier covers only the "(default)" Firestore database
  const db = typeof sdk.getFirestore === "function" ? sdk.getFirestore(app) : null;

  // App Check (Free tier: reCAPTCHA v3 provider)
  if (app && typeof sdk.initializeAppCheck === "function" && typeof sdk.ReCaptchaV3Provider === "function") {
    try {
      const siteKey = (typeof window !== "undefined" && window.RECAPTCHA_SITE_KEY) ||
                      (typeof RECAPTCHA_SITE_KEY !== "undefined" ? RECAPTCHA_SITE_KEY : "");
      if (siteKey && !siteKey.includes("placeholder")) {
        sdk.initializeAppCheck(app, {
          provider: new sdk.ReCaptchaV3Provider(siteKey),
          isTokenAutoRefreshEnabled: true
        });
      }
    } catch (_) {}
  }

  // [BLAZE-DISABLED START] reason: Cloud Storage unavailable on Spark free plan (calls return 402/403) | restore: Blaze: signed URLs, server-validated ownership, storage.rules in blaze_disabled/storage.rules
  // const storage = (globalThis.UDHAAR_CONFIG?.BLAZE_FEATURES_ENABLED && typeof sdk.getStorage === "function") ? sdk.getStorage(app) : null;
  // [BLAZE-DISABLED END]

  const search = (typeof window !== "undefined" && window.location && window.location.search) || "";
  const urlParams = new URLSearchParams(search);
  const emulatorCfg = (typeof globalThis !== "undefined" && globalThis.__UDHAAR_EMULATORS__) || null;
  const useEmulator = Boolean(emulatorCfg || urlParams.get("emulator") === "1");

  if (useEmulator && sdk) {
    const authHost = (emulatorCfg && emulatorCfg.authHost) || urlParams.get("authHost") || "127.0.0.1:9099";
    const fsHostPort = (emulatorCfg && emulatorCfg.firestoreHost) || urlParams.get("firestoreHost") || "127.0.0.1:8080";
    if (auth && typeof sdk.connectAuthEmulator === "function") {
      try {
        const authUrl = authHost.startsWith("http") ? authHost : `http://${authHost}`;
        sdk.connectAuthEmulator(auth, authUrl, { disableWarnings: true });
      } catch (_) {}
    }
    if (db && typeof sdk.connectFirestoreEmulator === "function") {
      try {
        const [host, portStr] = fsHostPort.replace(/^https?:\/\//, "").split(":");
        sdk.connectFirestoreEmulator(db, host || "127.0.0.1", parseInt(portStr || "8080", 10));
      } catch (_) {}
    }
  }

  _initialized = { app, auth, db, sdk };
  return _initialized;
}

const { app, auth, db, sdk } = initFirebase();

if (typeof globalThis !== "undefined") {
  globalThis.__UDHAAR_FIREBASE_INIT__ = { app, auth, db, sdk, initFirebase };
}
if (typeof module !== "undefined" && module.exports) {
  module.exports = { app, auth, db, sdk, initFirebase, FIREBASE_GUEST_CONFIG };
}
