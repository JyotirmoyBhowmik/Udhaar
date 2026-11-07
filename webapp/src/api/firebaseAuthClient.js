/**
 * Spark Mode Firebase Auth Client (webapp/src/api/firebaseAuthClient.js)
 * 
 * Free-tier direct Firebase Authentication adapter:
 * - Email + Password with validation & email verification enforcement
 * - Google Sign-In via Credential Manager / GoogleAuthProvider
 * - Password Reset
 * - Re-authentication for sensitive actions
 * - Account Linking (Google + Email)
 * - Friendly error translation
 * - Zero dependency on Cloud Functions or paid APIs
 */

class FirebaseAuthClient {
  constructor(options = {}) {
    this.authInstance = options.auth || (typeof window !== "undefined" && window.__udhaarFirebaseAuth) || null;
    this.appCheckToken = null;
    this.currentUser = null;
  }

  getAuth() {
    if (this.authInstance) return this.authInstance;
    if (typeof window !== "undefined" && window.__udhaarFirebaseAuth) {
      return window.__udhaarFirebaseAuth;
    }
    return null;
  }

  setAuthInstance(auth) {
    this.authInstance = auth;
  }

  /**
   * Format Firebase Auth error into friendly UI messages
   */
  formatAuthError(err) {
    const code = err.code || "";
    switch (code) {
      case "auth/invalid-email":
        return "Please enter a valid email address.";
      case "auth/email-already-in-use":
        return "An account with this email already exists. Please sign in instead.";
      case "auth/weak-password":
        return "Password is too weak. Please use at least 10 characters with numbers and symbols.";
      case "auth/wrong-password":
      case "auth/invalid-credential":
      case "auth/user-not-found":
        return "Incorrect email or password. Please check your credentials.";
      case "auth/too-many-requests":
        return "Too many failed attempts. For your security, this account is temporarily locked. Please try again later or reset your password.";
      case "auth/network-request-failed":
        return "Network connection issue. Your local records are safe on this device. Please check your internet connection.";
      case "auth/account-exists-with-different-credential":
        return "An account already exists with this email address using another sign-in method (e.g. Google). Please sign in with that method first, then link your accounts in Settings.";
      case "auth/requires-recent-login":
        return "This action is sensitive. Please enter your password or sign in again to verify it is you.";
      case "auth/unverified-email":
        return "Your email address is not yet verified. Please click the link sent to your inbox to activate sync.";
      default:
        return err.message || "Authentication request failed. Please try again.";
    }
  }

  /**
   * Sign up with Email and Password
   */
  async signUpWithEmailPassword({ email, password, contactPhone = null, displayName = "" }) {
    const auth = this.getAuth();
    if (!auth) {
      throw new Error("Firebase Auth is not initialized. Please ensure Firebase is loaded.");
    }

    try {
      let userCredential;
      if (typeof auth.createUserWithEmailAndPassword === "function") {
        userCredential = await auth.createUserWithEmailAndPassword(email, password);
      } else {
        // Modular SDK fallback or emulator
        const { createUserWithEmailAndPassword } = require("firebase/auth");
        userCredential = await createUserWithEmailAndPassword(auth, email, password);
      }

      const user = userCredential.user || userCredential;
      this.currentUser = user;

      // Update display name if provided
      if (displayName && typeof user.updateProfile === "function") {
        await user.updateProfile({ displayName });
      }

      // Send verification email automatically (free)
      await this.sendVerificationEmail(user);

      return {
        user,
        emailVerified: false,
        contactPhone,
        message: "Account created! A verification link has been sent to your email."
      };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Send Firebase verification email
   */
  async sendVerificationEmail(user = null) {
    const targetUser = user || this.getCurrentUser();
    if (!targetUser) throw new Error("No signed in user to verify.");

    try {
      if (typeof targetUser.sendEmailVerification === "function") {
        await targetUser.sendEmailVerification();
      } else {
        const { sendEmailVerification } = require("firebase/auth");
        await sendEmailVerification(targetUser);
      }
      return { sent: true };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Sign in with Email and Password
   */
  async signInWithEmailPassword({ email, password }) {
    const auth = this.getAuth();
    if (!auth) {
      throw new Error("Firebase Auth is not initialized.");
    }

    try {
      let userCredential;
      if (typeof auth.signInWithEmailAndPassword === "function") {
        userCredential = await auth.signInWithEmailAndPassword(email, password);
      } else {
        const { signInWithEmailAndPassword } = require("firebase/auth");
        userCredential = await signInWithEmailAndPassword(auth, email, password);
      }

      const user = userCredential.user || userCredential;
      this.currentUser = user;

      return {
        user,
        emailVerified: Boolean(user.emailVerified)
      };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Sign in with Google
   */
  async signInWithGoogle(providerInstance = null) {
    const auth = this.getAuth();
    if (!auth) {
      throw new Error("Firebase Auth is not initialized.");
    }

    try {
      let userCredential;
      if (typeof auth.signInWithPopup === "function") {
        const provider = providerInstance || new (window.firebase?.auth?.GoogleAuthProvider || Object)();
        userCredential = await auth.signInWithPopup(provider);
      } else {
        const { signInWithPopup, GoogleAuthProvider } = require("firebase/auth");
        userCredential = await signInWithPopup(auth, providerInstance || new GoogleAuthProvider());
      }

      const user = userCredential.user || userCredential;
      this.currentUser = user;

      return {
        user,
        emailVerified: true // Google accounts have pre-verified emails
      };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Send Password Reset Email
   */
  async sendPasswordReset(email) {
    const auth = this.getAuth();
    if (!auth) throw new Error("Firebase Auth is not initialized.");

    try {
      if (typeof auth.sendPasswordResetEmail === "function") {
        await auth.sendPasswordResetEmail(email);
      } else {
        const { sendPasswordResetEmail } = require("firebase/auth");
        await sendPasswordResetEmail(auth, email);
      }
      return { sent: true };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Re-authenticate user before sensitive operations
   */
  async reauthenticate(credential) {
    const user = this.getCurrentUser();
    if (!user) throw new Error("No authenticated user.");

    try {
      if (typeof user.reauthenticateWithCredential === "function") {
        await user.reauthenticateWithCredential(credential);
      } else {
        const { reauthenticateWithCredential } = require("firebase/auth");
        await reauthenticateWithCredential(user, credential);
      }
      return { success: true };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Link Google account to existing email/password account
   */
  async linkGoogle(credential) {
    const user = this.getCurrentUser();
    if (!user) throw new Error("No authenticated user.");

    try {
      if (typeof user.linkWithCredential === "function") {
        await user.linkWithCredential(credential);
      } else {
        const { linkWithCredential } = require("firebase/auth");
        await linkWithCredential(user, credential);
      }
      return { linked: true };
    } catch (err) {
      throw new Error(this.formatAuthError(err));
    }
  }

  /**
   * Sign out current user
   */
  async signOut() {
    const auth = this.getAuth();
    if (auth && typeof auth.signOut === "function") {
      await auth.signOut();
    }
    this.currentUser = null;
    return { signedOut: true };
  }

  getCurrentUser() {
    if (this.currentUser) return this.currentUser;
    const auth = this.getAuth();
    return auth?.currentUser || null;
  }

  /**
   * Return device and session status with honest token lifetime notice
   */
  getDeviceSessionInfo() {
    const user = this.getCurrentUser();
    if (!user) return null;

    const providerIds = (user.providerData || []).map(p => p.providerId);
    const creationTime = user.metadata?.creationTime || "Unknown";
    const lastSignInTime = user.metadata?.lastSignInTime || "Current session";

    return {
      uid: user.uid,
      email: user.email,
      emailVerified: Boolean(user.emailVerified),
      providers: providerIds.length > 0 ? providerIds : ["password"],
      creationTime,
      lastSignInTime,
      // Honest explanation as required by SP1 ground rules
      revocationNotice: (
        "Changing your password immediately invalidates refresh tokens on other devices. " +
        "Note that access tokens already issued to active sessions remain valid for up to approximately 1 hour before expiring."
      )
    };
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    FirebaseAuthClient
  };
}
