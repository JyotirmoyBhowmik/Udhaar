/**
 * Udhaar Web App Sign-Up Stepper (webapp/src/features/auth/SignUpView.js)
 * Implements 5-step registration: Phone -> WhatsApp OTP -> Email -> Email OTP -> Profile Name.
 * Strict CSP: 100% addEventListener, zero inline scripts/styles.
 */

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

    // Check URL search params or hash for guest bridge parameters
    this.upgradeRecordId = null;
    this.upgradeToken = null;
    this._parseGuestBridgeParams();
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

    this.container.appendChild(card);
  }

  // --- Step 1: Phone & DPDP Consents ---
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
      const digits = this.phoneNumber.replace(/\D/g, "");
      phoneInput.value = digits.length > 10 ? digits.slice(-10) : digits;
    }

    phoneInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const rawDigits = phoneInput.value.replace(/[^0-9]/g, "");
      if (rawDigits.length !== 10) {
        errEl.textContent = t("err_invalid_phone");
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      this.phoneNumber = `+91 ${rawDigits.slice(0, 5)} ${rawDigits.slice(5)}`;

      try {
        const res = await this.authClient.startSignup({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale(),
          isAge18Plus: card.querySelector("#age-check").checked,
          allowWhatsAppReminders: card.querySelector("#wa-consent-check").checked
        });

        this.flowId = res.flowId;
        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("continue_button");
        errEl.textContent = err.message || t("err_rate_limit");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 2: WhatsApp OTP ---
  _renderStepWhatsAppOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_whatsapp")}</h1>
        <p>${t("otp_subtitle_whatsapp")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signup-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="otp-submit-btn" class="btn btn-primary">
          ${t("verify_code_button")}
        </button>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="change-phone-btn" class="btn-link">
            ${t("change_number")}
          </button>
        </div>
      </form>
    `;

    this._setupOtpDigitAutoJump(card, async (code) => {
      await this._verifyPhoneOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        const res = await this.authClient.startSignup({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });
        this.flowId = res.flowId;
        this.onToast("Verification code resent on WhatsApp");
      } catch (err) {
        card.querySelector("#otp-error").textContent = err.message;
        card.querySelector("#otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-phone-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 1;
      this._renderCurrentStep();
    });

    card.querySelector("#signup-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyPhoneOtp(card, digits);
      }
    });
  }

  async _verifyPhoneOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#otp-submit-btn");
    const errEl = card.querySelector("#otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

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
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

  // --- Step 3: Email Input ---
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

    emailInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const rawEmail = emailInput.value.trim().toLowerCase();
      if (!rawEmail.includes("@") || !rawEmail.includes(".")) {
        errEl.textContent = t("err_invalid_email");
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      this.email = rawEmail;

      try {
        const res = await this.authClient.submitSignupEmail({
          flowId: this.flowId,
          flowToken: this.flowToken,
          email: this.email,
          locale: this.i18n.getLocale()
        });

        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 4;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_email_code");
        errEl.textContent = err.message || t("email_disposable_error");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 4: Email OTP ---
  _renderStepEmailOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_email")}</h1>
        <p>${t("otp_subtitle_email")}: <strong>${this.email}</strong></p>
      </div>

      <form id="email-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="email-otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="email-otp-submit-btn" class="btn btn-primary">
          ${t("verify_code_button")}
        </button>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="change-email-btn" class="btn-link">
            ${t("change_email")}
          </button>
        </div>
      </form>
    `;

    this._setupOtpDigitAutoJump(card, async (code) => {
      await this._verifyEmailOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        await this.authClient.submitSignupEmail({
          flowId: this.flowId,
          flowToken: this.flowToken,
          email: this.email,
          locale: this.i18n.getLocale()
        });
        this.onToast("Verification code resent to your email");
      } catch (err) {
        card.querySelector("#email-otp-error").textContent = err.message;
        card.querySelector("#email-otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-email-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 3;
      this._renderCurrentStep();
    });

    card.querySelector("#email-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyEmailOtp(card, digits);
      }
    });
  }

  async _verifyEmailOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#email-otp-submit-btn");
    const errEl = card.querySelector("#email-otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

    try {
      const res = await this.authClient.verifyFlowOtp({
        flowId: this.flowId,
        code,
        purpose: "SIGNUP_EMAIL"
      });

      this.flowToken = res.flowToken;
      this._clearIntervals();
      this.step = 5;
      this._renderCurrentStep();
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

  // --- Step 5: Profile Onboarding & Final Complete ---
  _renderStepProfile(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("profile_title")}</h1>
        <p>${t("profile_subtitle")}</p>
      </div>

      <form id="signup-profile-form">
        <div class="form-group">
          <label class="form-label" for="display-name-input">${t("display_name_label")}</label>
          <input type="text" id="display-name-input" class="input-field" placeholder="${t("display_name_placeholder")}" required maxlength="100" autocomplete="name">
          <p id="profile-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="profile-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("complete_signup_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-profile-form");
    const nameInput = card.querySelector("#display-name-input");
    const submitBtn = card.querySelector("#profile-submit-btn");
    const errEl = card.querySelector("#profile-error");

    nameInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const displayName = nameInput.value.trim();
      if (displayName.length < 2) {
        errEl.textContent = "Please enter your name.";
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = "Setting up your ledger...";

      try {
        const res = await this.authClient.completeSignup({
          flowId: this.flowId,
          flowToken: this.flowToken,
          phoneNumber: this.phoneNumber,
          email: this.email,
          displayName,
          isAge18Plus: true,
          allowWhatsAppReminders: true
        });

        if (this.upgradeRecordId && this.upgradeToken) {
          try {
            await this.authClient.upgradeGuestRecord({
              recordId: this.upgradeRecordId,
              rawToken: this.upgradeToken
            });
            this.onToast("Account created and shared record linked to your ledger!");
          } catch (upgradeErr) {
            console.warn("Auto-upgrade record failed:", upgradeErr);
            this.onToast(`${t("welcome_user")}, ${res.user.displayName}!`);
          }
        } else {
          this.onToast(`${t("welcome_user")}, ${res.user.displayName}!`);
        }
        this.router.navigate("#dashboard");
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("complete_signup_button");
        errEl.textContent = err.message || "Failed to create account. Please try again.";
        errEl.style.display = "block";
      }
    });
  }

  // --- Helper: Auto-Jump OTP input boxes ---
  _setupOtpDigitAutoJump(card, onComplete) {
    const inputs = card.querySelectorAll(".otp-digit");
    if (inputs.length === 0) return;

    inputs[0].focus();

    inputs.forEach((input, index) => {
      input.addEventListener("input", (e) => {
        const val = e.target.value.replace(/[^0-9]/g, "");
        e.target.value = val ? val[val.length - 1] : "";

        if (e.target.value && index < inputs.length - 1) {
          inputs[index + 1].focus();
        }

        const fullCode = Array.from(inputs).map(i => i.value).join("");
        if (fullCode.length === 6) {
          onComplete(fullCode);
        }
      });

      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !e.target.value && index > 0) {
          inputs[index - 1].focus();
        }
      });

      input.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
        if (text) {
          const chars = text.slice(0, 6).split("");
          chars.forEach((char, i) => {
            if (inputs[i]) inputs[i].value = char;
          });
          const lastIdx = Math.min(chars.length, inputs.length) - 1;
          if (inputs[lastIdx]) inputs[lastIdx].focus();
          if (chars.length === 6) {
            onComplete(chars.join(""));
          }
        }
      });
    });
  }

  _startResendCountdown(card, onResend) {
    const countdownEl = card.querySelector("#countdown-val");
    const timerTextEl = card.querySelector("#resend-timer-text");
    const resendBtn = card.querySelector("#resend-otp-btn");

    this._clearIntervals();

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
