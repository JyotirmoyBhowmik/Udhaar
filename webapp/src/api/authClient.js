/**
 * Udhaar Auth API Client (webapp/src/api/authClient.js)
 * Interacts with /api/v1/auth endpoints, manages CSRF protection,
 * and formats RFC 9457 Problem Details for friendly UI presentation.
 */

class AuthClient {
  constructor(baseUrl = "") {
    this.baseUrl = baseUrl;
    this.csrfToken = this._getCookie("csrf_token") || this._generateCsrfToken();
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
      const err = new Error(data.detail || data.title || "Authentication request failed");
      err.code = data.code || "ERR_AUTH_FAILED";
      err.status = res.status;
      err.details = data.invalidParams || null;
      throw err;
    }

    return data;
  }

  async startSignup({ phoneNumber, locale = "en", isAge18Plus = true, allowWhatsAppReminders = true }) {
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

  async completeSignup({ flowId, flowToken, phoneNumber, email, displayName, isAge18Plus = true, allowWhatsAppReminders = true }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/complete`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        phoneNumber,
        email,
        displayName,
        isAge18Plus,
        allowWhatsAppReminders,
        acceptedTermsVersion: "v2.0",
        acceptedPrivacyVersion: "v2.0",
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async startSignin({ phoneNumber, locale = "en" }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/start`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        phoneNumber,
        locale
      })
    });
    return this._handleResponse(res);
  }

  async verifySignin({ flowId, code }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/verify`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        code,
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async getMe() {
    const res = await fetch(`${this.baseUrl}/api/v1/me`, {
      method: "GET",
      headers: {
        "Accept": "application/json"
      }
    });
    return this._handleResponse(res);
  }

  async signOut() {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/session/signout`, {
      method: "POST",
      headers: this._getHeaders()
    });
    return this._handleResponse(res);
  }
}

const authClient = new AuthClient();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    AuthClient,
    authClient
  };
}
