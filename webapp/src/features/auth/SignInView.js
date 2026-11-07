/**
 * Udhaar Web App Sign-In Stepper (webapp/src/features/auth/SignInView.js)
 * Implements WhatsApp OTP sign-in with non-enumerable responses and auto-focus.
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
      this._renderStepOtp(card);
    }

    this.container.appendChild(card);
  }

  // --- Step 1: Phone Number Input ---
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

        <button type="submit" id="signin-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("send_login_code")}
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signup" class="btn-link">${t("dont_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#signin-phone-form");
    const phoneInput = card.querySelector("#signin-phone-input");
    const submitBtn = card.querySelector("#signin-submit-btn");
    const errEl = card.querySelector("#signin-phone-error");

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
        const res = await this.authClient.startSignin({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });

        this.flowId = res.flowId;
        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_login_code");
        errEl.textContent = err.message || t("err_rate_limit");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 2: WhatsApp OTP Input ---
  _renderStepOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("login_otp_title")}</h1>
        <p>${t("login_otp_subtitle")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signin-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="signin-otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="signin-otp-submit-btn" class="btn btn-primary">
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
      await this._verifyOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        const res = await this.authClient.startSignin({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });
        this.flowId = res.flowId;
        this.onToast("Login code resent on WhatsApp");
      } catch (err) {
        card.querySelector("#signin-otp-error").textContent = err.message;
        card.querySelector("#signin-otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-phone-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 1;
      this._renderCurrentStep();
    });

    card.querySelector("#signin-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyOtp(card, digits);
      }
    });
  }

  async _verifyOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#signin-otp-submit-btn");
    const errEl = card.querySelector("#signin-otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

    try {
      const res = await this.authClient.verifySignin({
        flowId: this.flowId,
        code
      });

      this._clearIntervals();
      this.onToast(`Welcome back, ${res.user.displayName}!`);
      this.router.navigate("#dashboard");
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

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
  module.exports = { SignInView };
}
