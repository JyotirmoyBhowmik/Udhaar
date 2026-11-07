/**
 * Udhaar Web App Sign-Up Stepper (webapp/src/features/auth/SignUpView.js)
 * 
 * Supports dual-tier architecture:
 * - Spark Mode (Default, 100% Free):
 *   Google Sign-In, Email + Password with 10+ character validation,
 *   strength meter, disposable email domain blocking, optional contact phone
 *   with "Number not verified" label, DPDP consent, and Firebase email verification.
 * - Blaze Mode:
 *   5-step registration: Phone -> WhatsApp OTP -> Email -> Email OTP -> Profile Name.
 * 
 * Strict CSP: 100% addEventListener, zero inline scripts/styles.
 */

const { validatePassword } = require("./passwordValidator");
const { checkDisposableEmail } = require("./disposableDomains");
const { validateAndNormalizeContactPhone } = require("./phoneValidation");
const { remoteConfigService } = require("../config/remoteConfigService");

class SignUpView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;

    this.step = 1;
    this.flowId = null;
    this.flowToken = null;
    this.phoneNumber = "";
    this.email = "";
    this.resendTimer = null;
    this.resendSecondsLeft = 30;
    this.sparkVerifySent = false;

    // Check URL search params or hash for guest bridge parameters
    this.upgradeRecordId = null;
    this.upgradeToken = null;
    this._parseGuestBridgeParams();
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

  _parseGuestBridgeParams() {
    if (typeof window === "undefined") return;
    try {
      const url = new URL(window.location.href);
      let params = url.searchParams;
      const hash = window.location.hash || "";
      if (hash.includes("?")) {
        const hashQuery = hash.substring(hash.indexOf("?") + 1);
        params = new URLSearchParams(hashQuery);
      }
      const recordId = params.get("recordId");
      const token = params.get("token") || params.get("secret");
      const phone = params.get("phone");
      if (recordId && token) {
        this.upgradeRecordId = recordId;
        this.upgradeToken = token;
      }
      if (phone) {
        const digits = phone.replace(/\D/g, "");
        this.phoneNumber = digits.length > 10 ? digits.slice(-10) : digits;
      }
    } catch (_) {}
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
      if (this.sparkVerifySent) {
        this._renderSparkVerifySent(card);
      } else {
        this._renderSparkSignUp(card);
      }
    } else {
      if (this.step === 1) {
        this._renderStepPhone(card);
      } else if (this.step === 2) {
        this._renderStepWhatsAppOtp(card);
      } else if (this.step === 3) {
        this._renderStepEmail(card);
      } else if (this.step === 4) {
        this._renderStepEmailOtp(card);
      } else if (this.step === 5) {
        this._renderStepProfile(card);
      }
    }

    this.container.appendChild(card);
  }

  // =========================================================================
  // SPARK MODE: Email/Password, Google, Strength Meter, & Contact Phone
  // =========================================================================

  _renderSparkSignUp(card) {
    const t = this.i18n.t;
    const currentLang = (this.i18n && (this.i18n.currentLang || this.i18n.lang)) || "en";
    const isSignupAllowed = remoteConfigService.isSignupEnabled();
    const signupPauseNotice = !isSignupAllowed ? remoteConfigService.getSignupDisabledMessage(currentLang) : "";

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signup_title")}</h1>
        <p>${t("signup_subtitle")}</p>
      </div>

      ${!isSignupAllowed ? `
        <div class="alert alert-warning" id="spark-signup-paused-banner" style="margin-bottom: 1.25rem; padding: 0.875rem; border-radius: var(--radius-md); background: #fef3c7; color: #92400e; border: 1px solid #f59e0b; font-size: 0.875rem; line-height: 1.4;">
          <strong>⏸️ Notice:</strong> ${signupPauseNotice}
        </div>
      ` : ""}

      <div style="margin-bottom: 1rem;">
        <button type="button" id="spark-google-btn" class="btn btn-secondary" style="width: 100%; display: flex; align-items: center; justify-content: center; gap: 0.5rem;" ${!isSignupAllowed ? 'disabled' : ''}>
          <span class="google-icon">G</span> Sign up with Google
        </button>
      </div>

      <div class="divider"><span>or register with email</span></div>

      <form id="spark-signup-form">
        <div class="form-group">
          <label class="form-label" for="spark-name-input">Full Name</label>
          <input type="text" id="spark-name-input" class="input-field" placeholder="e.g. Rahul Sharma" autocomplete="name" ${!isSignupAllowed ? 'disabled' : ''}>
        </div>

        <div class="form-group">
          <label class="form-label" for="spark-email-input">Email Address</label>
          <input type="email" id="spark-email-input" class="input-field" placeholder="name@example.com" required autocomplete="email" ${!isSignupAllowed ? 'disabled' : ''}>
          <p id="spark-email-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="form-group">
          <label class="form-label" for="spark-password-input">Password (Minimum 10 characters)</label>
          <input type="password" id="spark-password-input" class="input-field" placeholder="••••••••••" required autocomplete="new-password" ${!isSignupAllowed ? 'disabled' : ''}>
          <div class="password-meter-wrap">
            <div id="spark-password-meter" class="password-meter-bar"></div>
          </div>
          <div style="display: flex; justify-content: space-between; margin-top: 0.25rem;">
            <p id="spark-password-hint" class="input-hint">Strength: None</p>
            <p class="input-hint">Min. 10 characters</p>
          </div>
          <p id="spark-password-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="form-group">
          <label class="form-label" for="spark-phone-input">Mobile Number (Optional contact detail)</label>
          <div class="input-wrap">
            <span class="country-prefix">+91</span>
            <input type="tel" id="spark-phone-input" class="input-field has-prefix" placeholder="98765 43210" maxlength="10" autocomplete="tel-national" ${!isSignupAllowed ? 'disabled' : ''}>
          </div>
          <div style="margin-top: 0.375rem; display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
            <span class="badge-unverified">Number not verified</span>
            <span class="input-hint" style="font-size: 0.75rem;">Used only for matching contacts and messages. Never used for login or recovery.</span>
          </div>
          <p id="spark-phone-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="spark-age-check" class="checkbox-input" required checked ${!isSignupAllowed ? 'disabled' : ''}>
          <label for="spark-age-check" class="checkbox-label">${t("age_confirm_label")}</label>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="spark-terms-check" class="checkbox-input" required checked ${!isSignupAllowed ? 'disabled' : ''}>
          <label for="spark-terms-check" class="checkbox-label">
            ${t("terms_agree_prefix")} <a href="/terms.html" target="_blank">${t("terms_link")}</a> ${t("and_text")} <a href="/privacy.html" target="_blank">${t("privacy_link")}</a>
          </label>
        </div>

        <button type="submit" id="spark-submit-btn" class="btn btn-primary" style="width: 100%; margin-top: 1rem;" ${!isSignupAllowed ? 'disabled' : ''}>
          Create Account
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signin" class="btn-link">${t("already_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#spark-signup-form");
    const googleBtn = card.querySelector("#spark-google-btn");
    const emailInput = card.querySelector("#spark-email-input");
    const passwordInput = card.querySelector("#spark-password-input");
    const phoneInput = card.querySelector("#spark-phone-input");
    const nameInput = card.querySelector("#spark-name-input");
    const meterBar = card.querySelector("#spark-password-meter");
    const hintText = card.querySelector("#spark-password-hint");
    const emailErr = card.querySelector("#spark-email-error");
    const pwdErr = card.querySelector("#spark-password-error");
    const phoneErr = card.querySelector("#spark-phone-error");
    const submitBtn = card.querySelector("#spark-submit-btn");

    // Real-time password strength meter
    if (passwordInput) {
      passwordInput.addEventListener("input", () => {
        const result = validatePassword(passwordInput.value);
        if (meterBar) {
          meterBar.className = `password-meter-bar score-${result.score}`;
        }
        if (hintText) {
          hintText.textContent = `Strength: ${result.label}`;
        }
        if (pwdErr && result.valid) {
          pwdErr.style.display = "none";
        }
      });
    }

    // Google Sign-Up handler
    if (googleBtn) {
      googleBtn.addEventListener("click", async () => {
        if (!remoteConfigService.isSignupEnabled()) {
          this.onToast({ message: signupPauseNotice, type: "error" });
          return;
        }
        try {
          googleBtn.disabled = true;
          const res = await this.authClient.firebaseAuth.signInWithGoogle();
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

    // Email/Password form submit
    if (form) {
      form.addEventListener("submit", async (e) => {
        e.preventDefault();

        if (!remoteConfigService.isSignupEnabled()) {
          this.onToast({ message: signupPauseNotice, type: "error" });
          return;
        }

        // Validate email
        const email = emailInput.value.trim().toLowerCase();
        const disposableCheck = checkDisposableEmail(email);
        if (disposableCheck.isDisposable) {
          if (emailErr) {
            emailErr.textContent = t("email_disposable_error");
            emailErr.style.display = "block";
          }
          return;
        } else if (emailErr) {
          emailErr.style.display = "none";
        }

        // Validate password
        const pwdResult = validatePassword(passwordInput.value);
        if (!pwdResult.valid) {
          if (pwdErr) {
            pwdErr.textContent = pwdResult.errors[0] || "Invalid password";
            pwdErr.style.display = "block";
          }
          return;
        } else if (pwdErr) {
          pwdErr.style.display = "none";
        }

        // Validate optional phone
        let contactPhone = null;
        if (phoneInput && phoneInput.value.trim()) {
          const phoneResult = validateAndNormalizeContactPhone(phoneInput.value.trim());
          if (!phoneResult.valid) {
            if (phoneErr) {
              phoneErr.textContent = phoneResult.error || "Invalid mobile number";
              phoneErr.style.display = "block";
            }
            return;
          }
          contactPhone = phoneResult.e164;
          if (phoneErr) phoneErr.style.display = "none";
        }

        try {
          if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "Creating account...";
          }

          await this.authClient.firebaseAuth.signUpWithEmailPassword({
            email,
            password: passwordInput.value,
            contactPhone,
            displayName: nameInput.value.trim()
          });

          this.email = email;
          this.sparkVerifySent = true;
          this._renderCurrentStep();
        } catch (err) {
          if (emailErr) {
            emailErr.textContent = err.message;
            emailErr.style.display = "block";
          }
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = "Create Account";
          }
        }
      });
    }
  }

  _renderSparkVerifySent(card) {
    card.innerHTML = `
      <div class="auth-header">
        <h1>Check your email</h1>
        <p>We sent a verification link to <strong>${this.email}</strong>.</p>
      </div>

      <div class="auth-notice-box">
        <strong>Important:</strong> An account whose email is not verified cannot turn on cloud sync or create guest confirmation links.
      </div>

      <div style="display: flex; flex-direction: column; gap: 0.75rem; margin-top: 1.5rem;">
        <button type="button" id="spark-resend-btn" class="btn btn-secondary" style="width: 100%;">
          Resend verification email
        </button>
        <button type="button" id="spark-goto-signin-btn" class="btn btn-primary" style="width: 100%;">
          Continue to Sign In
        </button>
      </div>
    `;

    const resendBtn = card.querySelector("#spark-resend-btn");
    const gotoSignInBtn = card.querySelector("#spark-goto-signin-btn");

    if (resendBtn) {
      resendBtn.addEventListener("click", async () => {
        try {
          resendBtn.disabled = true;
          resendBtn.textContent = "Sending...";
          await this.authClient.firebaseAuth.sendVerificationEmail();
          this.onToast({ message: "Verification email resent! Please check your inbox.", type: "success" });
          resendBtn.textContent = "Email sent!";
        } catch (err) {
          this.onToast({ message: err.message, type: "error" });
          resendBtn.disabled = false;
          resendBtn.textContent = "Resend verification email";
        }
      });
    }

    if (gotoSignInBtn) {
      gotoSignInBtn.addEventListener("click", () => {
        if (this.router && this.router.navigate) {
          this.router.navigate("signin");
        } else if (typeof window !== "undefined") {
          window.location.hash = "#signin";
        }
      });
    }
  }

  // =========================================================================
  // BLAZE MODE: 5-step registration preserved behind toggle
  // =========================================================================

  _renderStepPhone(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signup_title")}</h1>
        <p>${t("signup_subtitle")}</p>
      </div>

      <form id="signup-phone-form">
        <div class="form-group">
          <label class="form-label" for="phone-input">${t("phone_label")}</label>
          <div class="input-wrap">
            <span class="country-prefix">+91</span>
            <input type="tel" id="phone-input" class="input-field has-prefix" placeholder="${t("phone_placeholder")}" maxlength="10" required autocomplete="tel-national">
          </div>
          <p class="input-hint">${t("phone_hint")}</p>
          <p id="phone-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="age-check" class="checkbox-input" required checked>
          <label for="age-check" class="checkbox-label">${t("age_confirm_label")}</label>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="terms-check" class="checkbox-input" required checked>
          <label for="terms-check" class="checkbox-label">
            ${t("terms_agree_prefix")} <a href="/terms.html" target="_blank">${t("terms_link")}</a> ${t("and_text")} <a href="/privacy.html" target="_blank">${t("privacy_link")}</a>
          </label>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="wa-consent-check" class="checkbox-input" checked>
          <label for="wa-consent-check" class="checkbox-label">${t("whatsapp_consent_label")}</label>
        </div>

        <button type="submit" id="phone-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("continue_button")}
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signin" class="btn-link">${t("already_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#signup-phone-form");
    const phoneInput = card.querySelector("#phone-input");
    const submitBtn = card.querySelector("#phone-submit-btn");
    const errEl = card.querySelector("#phone-error");

    if (this.phoneNumber) {
      phoneInput.value = this.phoneNumber;
    }

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
        const res = await this.authClient.startSignup({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale(),
          isAge18Plus: card.querySelector("#age-check").checked,
          allowWhatsAppReminders: card.querySelector("#wa-consent-check").checked
        });

        this.flowId = res.flowId;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_phone");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("continue_button");
      }
    });
  }

  _renderStepWhatsAppOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_whatsapp")}</h1>
        <p>${t("otp_subtitle_whatsapp")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signup-wa-otp-form">
        <div class="form-group">
          <label class="form-label">${t("otp_input_label")}</label>
          <div class="otp-inputs" id="wa-otp-group">
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
          </div>
          <p id="wa-otp-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="resend-row">
          <span id="wa-countdown-text">${t("resend_code_in")} <strong id="wa-countdown">30s</strong></span>
          <button type="button" id="wa-resend-btn" class="btn-link" style="display: none;">${t("resend_code_now")}</button>
          <button type="button" id="change-phone-btn" class="btn-link">${t("change_number")}</button>
        </div>

        <button type="submit" id="wa-otp-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("verify_code_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-wa-otp-form");
    const errEl = card.querySelector("#wa-otp-error");
    const submitBtn = card.querySelector("#wa-otp-submit-btn");
    const inputs = card.querySelectorAll(".otp-field");

    this._setupOtpInputAutoJump(inputs);
    this._startResendCountdown(card, async () => {
      await this.authClient.startSignup({
        phoneNumber: this.phoneNumber,
        locale: this.i18n.getLocale()
      });
    });

    card.querySelector("#change-phone-btn").addEventListener("click", () => {
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
        const res = await this.authClient.verifyFlowOtp({
          flowId: this.flowId,
          code,
          purpose: "SIGNUP"
        });

        this.flowToken = res.flowToken;
        this._clearIntervals();
        this.step = 3;
        this._renderCurrentStep();
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_code");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("verify_code_button");
      }
    });
  }

  _renderStepEmail(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("email_title")}</h1>
        <p>${t("email_subtitle")}</p>
      </div>

      <form id="signup-email-form">
        <div class="form-group">
          <label class="form-label" for="email-input">${t("email_label")}</label>
          <input type="email" id="email-input" class="input-field" placeholder="${t("email_placeholder")}" required autocomplete="email">
          <p class="input-hint">${t("email_hint")}</p>
          <p id="email-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="email-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("send_email_code")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-email-form");
    const emailInput = card.querySelector("#email-input");
    const submitBtn = card.querySelector("#email-submit-btn");
    const errEl = card.querySelector("#email-error");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errEl.style.display = "none";
      const rawEmail = emailInput.value.trim().toLowerCase();

      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      try {
        await this.authClient.submitSignupEmail({
          flowId: this.flowId,
          flowToken: this.flowToken,
          email: rawEmail,
          locale: this.i18n.getLocale()
        });

        this.email = rawEmail;
        this.step = 4;
        this._renderCurrentStep();
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_email");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_email_code");
      }
    });
  }

  _renderStepEmailOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_email")}</h1>
        <p>${t("otp_subtitle_email")}: <strong>${this.email}</strong></p>
      </div>

      <form id="signup-email-otp-form">
        <div class="form-group">
          <label class="form-label">${t("otp_input_label")}</label>
          <div class="otp-inputs" id="email-otp-group">
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
            <input type="text" class="otp-field" maxlength="1" inputmode="numeric" pattern="[0-9]*" required>
          </div>
          <p id="email-otp-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="resend-row">
          <span id="email-countdown-text">${t("resend_code_in")} <strong id="email-countdown">30s</strong></span>
          <button type="button" id="email-resend-btn" class="btn-link" style="display: none;">${t("resend_code_now")}</button>
          <button type="button" id="change-email-btn" class="btn-link">${t("change_email")}</button>
        </div>

        <button type="submit" id="email-otp-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("verify_code_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-email-otp-form");
    const errEl = card.querySelector("#email-otp-error");
    const submitBtn = card.querySelector("#email-otp-submit-btn");
    const inputs = card.querySelectorAll(".otp-field");

    this._setupOtpInputAutoJump(inputs);
    this._startResendCountdown(card, async () => {
      await this.authClient.submitSignupEmail({
        flowId: this.flowId,
        flowToken: this.flowToken,
        email: this.email,
        locale: this.i18n.getLocale()
      });
    });

    card.querySelector("#change-email-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 3;
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
        const res = await this.authClient.verifyFlowOtp({
          flowId: this.flowId,
          code,
          purpose: "SIGNUP"
        });

        this.flowToken = res.flowToken;
        this._clearIntervals();
        this.step = 5;
        this._renderCurrentStep();
      } catch (err) {
        errEl.textContent = err.message || t("err_invalid_code");
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("verify_code_button");
      }
    });
  }

  _renderStepProfile(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("profile_title")}</h1>
        <p>${t("profile_subtitle")}</p>
      </div>

      <form id="signup-profile-form">
        <div class="form-group">
          <label class="form-label" for="name-input">${t("display_name_label")}</label>
          <input type="text" id="name-input" class="input-field" placeholder="${t("display_name_placeholder")}" required autocomplete="name">
          <p id="profile-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="profile-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("complete_signup_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-profile-form");
    const nameInput = card.querySelector("#name-input");
    const submitBtn = card.querySelector("#profile-submit-btn");
    const errEl = card.querySelector("#profile-error");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const name = nameInput.value.trim();
      if (!name) return;

      submitBtn.disabled = true;
      submitBtn.textContent = "Setting up...";

      try {
        await this.authClient.completeSignup({
          flowId: this.flowId,
          flowToken: this.flowToken,
          name
        });

        // Upgrade guest record if user arrived via capability bridge
        if (this.upgradeRecordId && this.upgradeToken) {
          try {
            await this.authClient.upgradeGuestRecord({
              recordId: this.upgradeRecordId,
              rawToken: this.upgradeToken
            });
          } catch (_) {}
        }

        this.onToast(t("welcome_user"));
        this.router.navigate("#dashboard");
      } catch (err) {
        errEl.textContent = err.message || "Failed to complete setup";
        errEl.style.display = "block";
        submitBtn.disabled = false;
        submitBtn.textContent = t("complete_signup_button");
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

    const timerTextEl = card.querySelector("#wa-countdown-text, #email-countdown-text");
    const countdownEl = card.querySelector("#wa-countdown, #email-countdown");
    const resendBtn = card.querySelector("#wa-resend-btn, #email-resend-btn");

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
  module.exports = { SignUpView };
}
