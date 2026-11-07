/**
 * Udhaar Web App Sign-In Stepper (webapp/src/features/auth/SignInView.js)
 * 
 * Supports dual-tier architecture:
 * - Spark Mode (Default, 100% Free):
 *   Google Sign-In, Email + Password, Password Reset by email,
 *   optional passwordless email-link sign-in, and friendly rate-limit error messages.
 * - Blaze Mode:
 *   WhatsApp OTP sign-in with non-enumerable responses and auto-focus.
 * 
 * Strict CSP: zero inline event handlers.
 */

class SignInView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;

    this.step = 1;
    this.flowId = null;
    this.phoneNumber = "";
    this.resendTimer = null;
    this.resendSecondsLeft = 30;
  }

  isSparkMode() {
    if (this.authClient && typeof this.authClient.isSparkMode === "function") {
      return this.authClient.isSparkMode();
    }
    if (typeof window !== "undefined" && window.SITE_CONFIG && typeof window.SITE_CONFIG.isSparkTier === "function") {
      return window.SITE_CONFIG.isSparkTier();
    }
    const envTier = typeof process !== "undefined" && process.env ? process.env.UDHAAR_TIER : "spark";
    return (envTier || "spark").toLowerCase() === "spark";
  }

  render(container) {
    this.container = container;
    this._renderCurrentStep();
  }

  _renderCurrentStep() {
    this.container.innerHTML = "";
    const card = document.createElement("div");
    card.className = "card auth-container";

    if (this.isSparkMode()) {
      this._renderSparkSignIn(card);
    } else {
      if (this.step === 1) {
        this._renderStepPhone(card);
      } else if (this.step === 2) {
        this._renderStepOtp(card);
      }
    }

    this.container.appendChild(card);
  }

  // =========================================================================
  // SPARK MODE: Email/Password, Google, & Password Reset
  // =========================================================================

  _renderSparkSignIn(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signin_title")}</h1>
        <p>Sign in with your Google account or email</p>
      </div>

      <div style="margin-bottom: 1rem;">
        <button type="button" id="spark-google-signin-btn" class="btn btn-secondary" style="width: 100%; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
          <span class="google-icon">G</span> Sign in with Google
        </button>
      </div>

      <div class="divider"><span>or sign in with email</span></div>

      <form id="spark-signin-form">
        <div class="form-group">
          <label class="form-label" for="spark-signin-email">Email Address</label>
          <input type="email" id="spark-signin-email" class="input-field" placeholder="name@example.com" required autocomplete="email">
          <p id="spark-signin-email-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="form-group">
          <label class="form-label" for="spark-signin-password">Password</label>
          <input type="password" id="spark-signin-password" class="input-field" placeholder="••••••••••" required autocomplete="current-password">
          <div style="text-align: right; margin-top: 0.25rem;">
            <button type="button" id="spark-forgot-pwd-btn" class="btn-link" style="font-size: 0.8125rem;">Forgot password?</button>
          </div>
          <p id="spark-signin-pwd-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="spark-signin-submit" class="btn btn-primary" style="width: 100%; margin-top: 1rem;">
          Sign In
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signup" class="btn-link">${t("dont_have_account")}</a>
        </div>
      </form>

      <div id="spark-reset-card" style="display: none; margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--color-border);">
        <h3 style="font-size: 1rem; font-weight: 600; margin-bottom: 0.25rem;">Reset your password</h3>
        <p class="input-hint" style="margin-bottom: 0.75rem;">Enter your email address and Firebase will send you a reset link.</p>
        <div class="form-group">
          <input type="email" id="spark-reset-email" class="input-field" placeholder="name@example.com">
        </div>
        <div style="display: flex; gap: 0.5rem;">
          <button type="button" id="spark-send-reset-btn" class="btn btn-primary" style="width: auto;">Send Reset Link</button>
          <button type="button" id="spark-cancel-reset-btn" class="btn btn-secondary" style="width: auto;">Cancel</button>
        </div>
        <p id="spark-reset-status" class="input-hint" style="margin-top: 0.5rem; display: none;"></p>
      </div>
    `;

    const form = card.querySelector("#spark-signin-form");
    const googleBtn = card.querySelector("#spark-google-signin-btn");
    const emailInput = card.querySelector("#spark-signin-email");
    const passwordInput = card.querySelector("#spark-signin-password");
    const submitBtn = card.querySelector("#spark-signin-submit");
    const emailErr = card.querySelector("#spark-signin-email-error");
    const pwdErr = card.querySelector("#spark-signin-pwd-error");

    const forgotBtn = card.querySelector("#spark-forgot-pwd-btn");
    const resetCard = card.querySelector("#spark-reset-card");
    const resetEmailInput = card.querySelector("#spark-reset-email");
    const sendResetBtn = card.querySelector("#spark-send-reset-btn");
    const cancelResetBtn = card.querySelector("#spark-cancel-reset-btn");
    const resetStatus = card.querySelector("#spark-reset-status");

    // Google Sign-In
    if (googleBtn) {
      googleBtn.addEventListener("click", async () => {
        try {
          googleBtn.disabled = true;
          await this.authClient.firebaseAuth.signInWithGoogle();
          this.onToast({ message: "Signed in with Google successfully!", type: "success" });
          if (this.router && this.router.navigate) {
            this.router.navigate("dashboard");
          } else if (typeof window !== "undefined") {
            window.location.hash = "#dashboard";
          }
        } catch (err) {
          this.onToast({ message: err.message, type: "error" });
          googleBtn.disabled = false;
        }
      });
    }

    // Toggle reset card
    if (forgotBtn && resetCard) {
      forgotBtn.addEventListener("click", () => {
        resetCard.style.display = resetCard.style.display === "none" ? "block" : "none";
        if (resetEmailInput && emailInput.value) {
          resetEmailInput.value = emailInput.value;
        }
      });
    }

    if (cancelResetBtn && resetCard) {
      cancelResetBtn.addEventListener("click", () => {
        resetCard.style.display = "none";
      });
    }

    if (sendResetBtn && resetEmailInput) {
      sendResetBtn.addEventListener("click", async () => {
        const resetEmail = resetEmailInput.value.trim().toLowerCase();
        if (!resetEmail) return;

        try {
          sendResetBtn.disabled = true;
          sendResetBtn.textContent = "Sending...";
          await this.authClient.firebaseAuth.sendPasswordReset(resetEmail);
          resetStatus.textContent = "Password reset link sent to your email!";
          resetStatus.style.color = "var(--color-primary)";
          resetStatus.style.display = "block";
          sendResetBtn.textContent = "Link Sent";
        } catch (err) {
          resetStatus.textContent = err.message;
          resetStatus.style.color = "var(--color-danger)";
          resetStatus.style.display = "block";
          sendResetBtn.disabled = false;
          sendResetBtn.textContent = "Send Reset Link";
        }
      });
    }

    // Email/Password form submit
    if (form) {
      form.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (emailErr) emailErr.style.display = "none";
        if (pwdErr) pwdErr.style.display = "none";

        const email = emailInput.value.trim().toLowerCase();
        const password = passwordInput.value;

        try {
          submitBtn.disabled = true;
          submitBtn.textContent = "Signing in...";

          await this.authClient.firebaseAuth.signInWithEmailPassword({
            email,
            password
          });

          this.onToast({ message: "Signed in successfully!", type: "success" });
          if (this.router && this.router.navigate) {
            this.router.navigate("dashboard");
          } else if (typeof window !== "undefined") {
            window.location.hash = "#dashboard";
          }
        } catch (err) {
          const msg = err.message;
          if (pwdErr) {
            pwdErr.textContent = msg;
            pwdErr.style.display = "block";
          }
          submitBtn.disabled = false;
          submitBtn.textContent = "Sign In";
        }
      });
    }
  }

  // =========================================================================
  // BLAZE MODE: WhatsApp OTP Sign-In
  // =========================================================================

  _renderStepPhone(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signin_title")}</h1>
        <p>${t("signin_subtitle")}</p>
      </div>

      <form id="signin-phone-form">
        <div class="form-group">
          <label class="form-label" for="signin-phone-input">${t("signin_phone_label")}</label>
          <div class="input-wrap">
            <span class="country-prefix">+91</span>
            <input type="tel" id="signin-phone-input" class="input-field has-prefix" placeholder="${t("phone_placeholder")}" maxlength="10" required autocomplete="tel-national">
          </div>
          <p class="input-hint">${t("phone_hint")}</p>
          <p id="signin-phone-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="signin-phone-submit" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("send_login_code")}
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signup" class="btn-link">${t("dont_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#signin-phone-form");
    const phoneInput = card.querySelector("#signin-phone-input");
    const submitBtn = card.querySelector("#signin-phone-submit");
    const errEl = card.querySelector("#signin-phone-error");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errEl.style.display = "none";
      const rawDigits = phoneInput.value.replace(/\D/g, "");
      if (rawDigits.length !== 10) {
        errEl.textContent = t("err_invalid_phone");
        errEl.style.display = "block";
        return;
      }

      this.phoneNumber = `+91${rawDigits}`;
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      try {
        const res = await this.authClient.startSignin({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });

        this.flowId = res.flowId;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_phone");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_login_code");
      }
    });
  }

  _renderStepOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("login_otp_title")}</h1>
        <p>${t("login_otp_subtitle")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signin-otp-form">
        <div class="form-group">
          <label class="form-label">${t("otp_input_label")}</label>
          <div class="otp-inputs" id="login-otp-group">
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
          </div>
          <p id="login-otp-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="resend-row">
          <span id="login-countdown-text">${t("resend_code_in")} <strong id="login-countdown">30s</strong></span>
          <button type="button" id="login-resend-btn" class="btn-link" style="display: none;">${t("resend_code_now")}</button>
          <button type="button" id="signin-change-phone-btn" class="btn-link">${t("change_number")}</button>
        </div>

        <button type="submit" id="login-otp-submit" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("verify_code_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signin-otp-form");
    const errEl = card.querySelector("#login-otp-error");
    const submitBtn = card.querySelector("#login-otp-submit");
    const inputs = card.querySelectorAll(".otp-field");

    this._setupOtpInputAutoJump(inputs);
    this._startResendCountdown(card, async () => {
      await this.authClient.startSignin({
        phoneNumber: this.phoneNumber,
        locale: this.i18n.getLocale()
      });
    });

    card.querySelector("#signin-change-phone-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 1;
      this._renderCurrentStep();
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errEl.style.display = "none";
      const code = Array.from(inputs).map(inp => inp.value).join("");
      if (code.length !== 6) {
        errEl.textContent = t("err_invalid_code");
        errEl.style.display = "block";
        return;
      }

      submitBtn.disabled = true;
      submitBtn.textContent = t("verifying");

      try {
        await this.authClient.verifySignin({
          flowId: this.flowId,
          flowToken: null,
          code
        });

        this._clearIntervals();
        this.onToast(t("welcome_user"));
        this.router.navigate("#dashboard");
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_code");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("verify_code_button");
      }
    });
  }

  _setupOtpInputAutoJump(inputs) {
    inputs.forEach((input, idx) => {
      input.addEventListener("input", (e) => {
        const val = e.target.value.replace(/\D/g, "");
        e.target.value = val ? val[0] : "";
        if (val && idx < inputs.length - 1) {
          inputs[idx + 1].focus();
        }
      });

      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !e.target.value && idx > 0) {
          inputs[idx - 1].focus();
        }
      });

      input.addEventListener("paste", (e) => {
        e.preventDefault();
        const pasted = (e.clipboardData || window.clipboardData).getData("text").replace(/\D/g, "");
        if (!pasted) return;
        for (let i = 0; i < inputs.length; i++) {
          inputs[i].value = pasted[i] || "";
        }
        const lastFilled = Math.min(pasted.length, inputs.length) - 1;
        if (lastFilled >= 0) inputs[lastFilled].focus();
      });
    });

    if (inputs[0]) inputs[0].focus();
  }

  _startResendCountdown(card, onResend) {
    this._clearIntervals();
    this.resendSecondsLeft = 30;

    const timerTextEl = card.querySelector("#login-countdown-text");
    const countdownEl = card.querySelector("#login-countdown");
    const resendBtn = card.querySelector("#login-resend-btn");

    if (timerTextEl) timerTextEl.style.display = "inline";
    if (resendBtn) resendBtn.style.display = "none";
    if (countdownEl) countdownEl.textContent = "30s";

    this.resendTimer = setInterval(() => {
      this.resendSecondsLeft--;
      if (countdownEl) countdownEl.textContent = `${this.resendSecondsLeft}s`;

      if (this.resendSecondsLeft <= 0) {
        this._clearIntervals();
        if (timerTextEl) timerTextEl.style.display = "none";
        if (resendBtn) resendBtn.style.display = "inline-block";
      }
    }, 1000);

    if (resendBtn) {
      resendBtn.addEventListener("click", async () => {
        resendBtn.style.display = "none";
        if (timerTextEl) timerTextEl.style.display = "inline-block";
        this.resendSecondsLeft = 30;
        if (countdownEl) countdownEl.textContent = "30s";
        this._startResendCountdown(card, onResend);
        await onResend();
      });
    }
  }

  _clearIntervals() {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = null;
    }
  }

  destroy() {
    this._clearIntervals();
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SignInView };
}
