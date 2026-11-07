/**
 * Udhaar Auth API Client (webapp/src/api/authClient.js)
 * 
 * Supports dual-tier architecture:
 * - Spark Tier (Default, 100% Free): Uses FirebaseAuthClient directly against Firebase Auth.
 *   Throws a fatal error if /api/v1/** REST endpoints are attempted.
 * - Blaze Tier: Interacts with /api/v1/auth Cloud Functions endpoints.
 */

const { FirebaseAuthClient } = require("./firebaseAuthClient");

class AuthError extends Error {
  /**
   * @param {string} message
   * @param {string} code
   * @param {number} status
   * @param {any} [details]
   */
  constructor(message, code, status, details = null) {
    super(message);
    this.name = "AuthError";
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

class AuthClient {
  constructor(baseUrl = "") {
    this.baseUrl = baseUrl;
    this.csrfToken = this._getCookie("csrf_token") || this._generateCsrfToken();
    this.firebaseAuth = new FirebaseAuthClient();
  }

  isSparkMode() {
    if (typeof window !== "undefined" && window.SITE_CONFIG && typeof window.SITE_CONFIG.isSparkTier === "function") {
      return window.SITE_CONFIG.isSparkTier();
    }
    const envTier = typeof process !== "undefined" ? process.env.UDHAAR_TIER : "spark";
    return (envTier || "spark").toLowerCase() === "spark";
  }

  _assertBlazeAllowed(endpointName) {
    if (this.isSparkMode()) {
      throw new AuthError(
        `[TierViolation: ERR_SPARK_TIER_RESTRICTION] Calling '${endpointName}' on Cloud Functions API is disabled in Spark Mode. ` +
        `Spark Mode operates 100% serverless through Firebase Auth and client Firestore rules.`,
        "ERR_SPARK_TIER_RESTRICTION",
        400
      );
    }
  }

  _getCookie(name) {
    if (typeof document === "undefined") return null;
    const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return match ? decodeURIComponent(match[2]) : null;
  }

  _generateCsrfToken() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return Math.random().toString(36).substring(2) + Date.now().toString(36);
  }

  _getHeaders() {
    const headers = {
      "Content-Type": "application/json",
      "X-CSRF-Token": this.csrfToken,
      "X-Idempotency-Key": this._generateCsrfToken()
    };
    return headers;
  }

  async _handleResponse(res) {
    let data = null;
    try {
      data = await res.json();
    } catch (_) {
      data = {};
    }

    if (!res.ok) {
      throw new AuthError(
        data.detail || data.title || "Authentication request failed",
        data.code || "ERR_AUTH_FAILED",
        res.status,
        data.invalidParams || null
      );
    }

    return data;
  }

  async startSignup({ phoneNumber, locale = "en", isAge18Plus = true, allowWhatsAppReminders = true }) {
    this._assertBlazeAllowed("startSignup");
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/start`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        phoneNumber,
        locale,
        isAge18Plus,
        allowWhatsAppReminders
      })
    });
    return this._handleResponse(res);
  }

  async verifyFlowOtp({ flowId, code, purpose = "SIGNUP" }) {
    this._assertBlazeAllowed("verifyFlowOtp");
    const res = await fetch(`${this.baseUrl}/api/v1/auth/otp/verify`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        code,
        purpose
      })
    });
    return this._handleResponse(res);
  }

  async submitSignupEmail({ flowId, flowToken, email, locale = "en" }) {
    this._assertBlazeAllowed("submitSignupEmail");
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/email`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        email,
        locale
      })
    });
    return this._handleResponse(res);
  }

  async completeSignup({ flowId, flowToken, name, contactPhone = null, password = null }) {
    if (this.isSparkMode()) {
      return this.firebaseAuth.signUpWithEmailPassword({
        email: flowToken,
        password,
        contactPhone,
        displayName: name
      });
    }

    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/complete`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        name
      })
    });
    return this._handleResponse(res);
  }

  async startSignin({ phoneNumber = null, email = null, locale = "en" }) {
    this._assertBlazeAllowed("startSignin");
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/start`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        phoneNumber,
        email,
        locale,
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async verifySignin({ flowId, flowToken, code }) {
    this._assertBlazeAllowed("verifySignin");
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/verify`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        code,
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async getMe() {
    if (this.isSparkMode()) {
      const user = this.firebaseAuth.getCurrentUser();
      if (!user) {
        throw new AuthError("Not authenticated", "ERR_UNAUTHORIZED", 401);
      }
      return {
        uid: user.uid,
        email: user.email,
        displayName: user.displayName || "",
        emailVerified: Boolean(user.emailVerified)
      };
    }

    const res = await fetch(`${this.baseUrl}/api/v1/me`, {
      method: "GET",
      headers: {
        "Accept": "application/json"
      }
    });
    return this._handleResponse(res);
  }

  async signOut() {
    if (this.isSparkMode()) {
      await this.firebaseAuth.signOut();
    } else {
      try {
        await fetch(`${this.baseUrl}/api/v1/auth/session/signout`, {
          method: "POST",
          headers: this._getHeaders()
        });
      } catch (_) {}
    }

    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.clear();
    }
    try {
      const { ledgerDb } = require("../db/ledgerDb");
      if (ledgerDb && ledgerDb.clearAll) {
        await ledgerDb.clearAll();
      }
    } catch (_) {}

    return { success: true };
  }

  async upgradeGuestRecord({ recordId, rawToken }) {
    this._assertBlazeAllowed("upgradeGuestRecord");
    const res = await fetch(`${this.baseUrl}/api/v1/links/${encodeURIComponent(recordId)}/upgrade`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        rawToken
      })
    });
    return this._handleResponse(res);
  }
}

const authClient = new AuthClient();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    AuthClient,
    authClient,
    AuthError
  };
}
