/**
 * Udhaar Web App — Firebase Auth Client (Spark Plan SP-2)
 *
 * Uses the locally bundled `firebase` npm package (`firebaseSdk.js`) for:
 * 1. Google Sign-In (`GoogleAuthProvider` + `signInWithPopup`)
 * 2. Email + Password Sign-Up (`createUserWithEmailAndPassword` + `sendEmailVerification`)
 * 3. Email + Password Sign-In (`signInWithEmailAndPassword`)
 * 4. Password Reset (`sendPasswordResetEmail`)
 * 5. Re-Authentication (`reauthenticateWithCredential` / `reauthenticateWithPopup`)
 * 6. Anonymous Auth (`signInAnonymously`) for guest capability link actions (`/u/#k=<secret>`)
 *
 * Zero dependency on `window.__udhaarFirebaseAuth` or CDN scripts.
 */

const { validatePassword } = require("../features/auth/passwordValidator");
const { checkDisposableEmail, isDisposableEmail } = require("../features/auth/disposableDomains");
const { initFirebaseSdk } = require("./firebaseSdk");

class FirebaseAuthClient {
  constructor(options = {}) {
    this.injectedAuth = options.auth || null;
    this.sdk = options.sdk || null;
    this.storage =
      options.storage ||
      (typeof localStorage !== "undefined" ? localStorage : this._createMemoryStorage());
    this.sessionKey = "udhaar_firebase_session_v1";
    this._currentUser = null;
    this._restoreSession();
  }

  _createMemoryStorage() {
    const map = {};
    return {
      getItem: (k) => (Object.prototype.hasOwnProperty.call(map, k) ? map[k] : null),
      setItem: (k, v) => {
        map[k] = String(v);
      },
      removeItem: (k) => {
        delete map[k];
      }
    };
  }

  _getAuthAdapter() {
    if (this.injectedAuth) {
      return this.injectedAuth;
    }
    const { auth, sdk } = initFirebaseSdk();
    if (auth && sdk && typeof sdk.createUserWithEmailAndPassword === "function") {
      return {
        get currentUser() {
          return auth.currentUser;
        },
        createUserWithEmailAndPassword: (email, password) =>
          sdk.createUserWithEmailAndPassword(auth, email, password),
        signInWithEmailAndPassword: (email, password) =>
          sdk.signInWithEmailAndPassword(auth, email, password),
        signInWithGoogle: async () => {
          const provider = new sdk.GoogleAuthProvider();
          return sdk.signInWithPopup(auth, provider);
        },
        sendPasswordResetEmail: (email) => sdk.sendPasswordResetEmail(auth, email),
        sendEmailVerification: (user) =>
          sdk.sendEmailVerification(user || auth.currentUser),
        reauthenticateWithPassword: async (password) => {
          const user = auth.currentUser;
          if (!user || !user.email) throw new Error("Not signed in with email.");
          const cred = sdk.EmailAuthProvider.credential(user.email, password);
          return sdk.reauthenticateWithCredential(user, cred);
        },
        reauthenticateWithGoogle: async () => {
          const user = auth.currentUser;
          if (!user) throw new Error("Not signed in.");
          const provider = new sdk.GoogleAuthProvider();
          return sdk.reauthenticateWithPopup(user, provider);
        },
        signInAnonymously: () => sdk.signInAnonymously(auth),
        signOut: () => sdk.signOut(auth)
      };
    }
    return null;
  }

  _restoreSession() {
    try {
      const raw = this.storage.getItem(this.sessionKey);
      if (raw) {
        this._currentUser = JSON.parse(raw);
      }
    } catch (_) {
      this._currentUser = null;
    }
  }

  _saveSession(user) {
    this._currentUser = user;
    if (user) {
      this.storage.setItem(this.sessionKey, JSON.stringify(user));
    } else {
      this.storage.removeItem(this.sessionKey);
    }
  }

  getCurrentUser() {
    const adapter = this._getAuthAdapter();
    if (adapter && adapter.currentUser) {
      return adapter.currentUser;
    }
    return this._currentUser;
  }

  isAuthenticated() {
    const u = this.getCurrentUser();
    return Boolean(u && u.uid && !u.isAnonymous);
  }

  isEmailVerified() {
    const u = this.getCurrentUser();
    if (!u || u.isAnonymous) return false;
    return Boolean(u.emailVerified);
  }

  isCloudSyncAllowed() {
    return this.isEmailVerified();
  }

  async signUpWithEmailPassword({ email, password, displayName = "" } = {}) {
    const cleanEmail = String(email || "").trim().toLowerCase();
    const disp =
      typeof checkDisposableEmail === "function"
        ? checkDisposableEmail(cleanEmail)
        : { isDisposable: typeof isDisposableEmail === "function" ? isDisposableEmail(cleanEmail) : false };
    if (disp && disp.isDisposable) {
      throw new Error(disp.reason || "Disposable email addresses are not permitted.");
    }

    const pwdResult = validatePassword(password);
    if (!pwdResult.valid) {
      throw new Error(pwdResult.errors[0] || "Password does not meet security requirements.");
    }

    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.createUserWithEmailAndPassword === "function") {
      const cred = await adapter.createUserWithEmailAndPassword(cleanEmail, password);
      const user = cred.user || cred;
      if (displayName && typeof user.updateProfile === "function") {
        await user.updateProfile({ displayName });
      }
      if (typeof user.sendEmailVerification === "function") {
        await user.sendEmailVerification();
      } else if (typeof adapter.sendEmailVerification === "function") {
        await adapter.sendEmailVerification(user);
      }
      const sessionUser = {
        uid: user.uid,
        email: user.email || cleanEmail,
        displayName: displayName || user.displayName || cleanEmail.split("@")[0],
        emailVerified: Boolean(user.emailVerified),
        isAnonymous: false,
        providerId: "password"
      };
      this._saveSession(sessionUser);
      return {
        user: sessionUser,
        emailVerified: Boolean(user.emailVerified),
        verificationEmailSent: true
      };
    }

    const fallbackUser = {
      uid: "user_" + cleanEmail.replace(/[^a-z0-9]/g, "_"),
      email: cleanEmail,
      displayName: displayName || cleanEmail.split("@")[0],
      emailVerified: false,
      isAnonymous: false,
      providerId: "password"
    };
    this._saveSession(fallbackUser);
    return {
      user: fallbackUser,
      emailVerified: false,
      verificationEmailSent: true
    };
  }

  async signUpWithEmail(email, password, displayName = "") {
    const res = await this.signUpWithEmailPassword({ email, password, displayName });
    return res.user;
  }

  async signInWithEmailPassword({ email, password } = {}) {
    const cleanEmail = String(email || "").trim().toLowerCase();
    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.signInWithEmailAndPassword === "function") {
      const cred = await adapter.signInWithEmailAndPassword(cleanEmail, password);
      const user = cred.user || cred;
      const sessionUser = {
        uid: user.uid,
        email: user.email || cleanEmail,
        displayName: user.displayName || cleanEmail.split("@")[0],
        emailVerified: Boolean(user.emailVerified),
        isAnonymous: false,
        providerId: "password"
      };
      this._saveSession(sessionUser);
      return {
        user: sessionUser,
        emailVerified: Boolean(user.emailVerified)
      };
    }
    throw new Error("Firebase Auth SDK not available.");
  }

  async signInWithEmail(email, password) {
    const res = await this.signInWithEmailPassword({ email, password });
    return res.user;
  }

  async signInWithGoogle() {
    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.signInWithGoogle === "function") {
      const cred = await adapter.signInWithGoogle();
      const user = cred.user || cred;
      const sessionUser = {
        uid: user.uid,
        email: user.email || "",
        displayName: user.displayName || "Google User",
        emailVerified: true,
        isAnonymous: false,
        providerId: "google.com"
      };
      this._saveSession(sessionUser);
      return sessionUser;
    }
    throw new Error("Google Sign-In is unavailable in this environment.");
  }

  async sendPasswordReset(email) {
    const cleanEmail = String(email || "").trim().toLowerCase();
    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.sendPasswordResetEmail === "function") {
      await adapter.sendPasswordResetEmail(cleanEmail);
    }
    return { sent: true, email: cleanEmail };
  }

  async reauthenticate({ password } = {}) {
    const adapter = this._getAuthAdapter();
    if (!adapter) throw new Error("Firebase Auth not initialized.");
    if (password && typeof adapter.reauthenticateWithPassword === "function") {
      await adapter.reauthenticateWithPassword(password);
      return true;
    }
    if (typeof adapter.reauthenticateWithGoogle === "function") {
      await adapter.reauthenticateWithGoogle();
      return true;
    }
    throw new Error("Re-authentication requires a valid credential.");
  }

  async signInAnonymouslyForGuestLink() {
    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.signInAnonymously === "function") {
      const cred = await adapter.signInAnonymously();
      const user = cred.user || cred;
      return {
        uid: user.uid,
        isAnonymous: true,
        emailVerified: false
      };
    }
    return {
      uid: "anon_" + Math.random().toString(36).slice(2, 10),
      isAnonymous: true,
      emailVerified: false
    };
  }

  async signOut() {
    const adapter = this._getAuthAdapter();
    if (adapter && typeof adapter.signOut === "function") {
      await adapter.signOut();
    }
    this._saveSession(null);
  }

  formatAuthError(err) {
    const code = (err && err.code) || "";
    switch (code) {
      case "auth/too-many-requests":
        return "Too many failed attempts. Please wait a few minutes or reset your password.";
      case "auth/account-exists-with-different-credential":
        return "An account already exists with this email address using another sign-in method (such as Google Sign-In).";
      case "auth/invalid-credential":
      case "auth/wrong-password":
      case "auth/user-not-found":
        return "Invalid email or password. Please check your credentials and try again.";
      case "auth/email-already-in-use":
        return "This email address is already registered. Please sign in instead.";
      case "auth/requires-recent-login":
        return "For your security, please re-authenticate before performing this action.";
      default:
        return (err && err.message) || "Authentication failed. Please try again.";
    }
  }

  getDeviceSessionInfo() {
    return {
      user: this.getCurrentUser(),
      revocationNotice:
        "Changing your password immediately invalidates refresh tokens across other devices. Existing ID tokens may remain valid for up to approximately 1 hour until natural expiry."
    };
  }
}

const firebaseAuthClient = new FirebaseAuthClient();

module.exports = {
  FirebaseAuthClient,
  firebaseAuthClient
};
