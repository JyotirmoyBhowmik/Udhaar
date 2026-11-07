# Runbook: Email Provider Outage & Failover Protocol

- **ID**: RB-COMM-002
- **Severity**: MEDIUM (P3)
- **Trigger**: Transactional email delivery rate drops below 90% or provider API (Amazon SES / SendGrid / Resend) returns HTTP 5xx.

---

## 1. Detection & Automated Alerts

- Cloud Monitoring alert fires: `udhaar_email_delivery_failure_rate > 0.10`.
- Client sign-up completion stalls at step 4 (`/auth/signup/email`).

---

## 2. Immediate Diagnostic Steps

1. **Check Provider Health**:
   - For AWS SES: [AWS Service Health Dashboard](https://health.aws.amazon.com/health/status).
   - For SendGrid: [SendGrid Status](https://status.sendgrid.com/).
   - For Resend: [Resend Status](https://resend.com/status).
2. **Review Bounce & Complaint Logs**:
   - Check Cloud Functions logs for SES bounce notifications or reputation metrics.
   - If bounce rate $> 5\%$ or complaint rate $> 0.1\%$, provider may have temporarily throttled sending.
3. **Verify DNS & Domain Authentication**:
   - Ensure SPF, DKIM, and DMARC DNS records for `no-reply@jyotirmoyb.com` remain valid and unexpired.

---

## 3. Mitigation & Failover Actions

1. **Failover to Secondary Email Provider**:
   - In GCP Secret Manager, update secret `EMAIL_PROVIDER_CONFIG`:
     ```json
     {
       "activeProvider": "sendgrid",
       "fallbackProvider": "ses"
     }
     ```
   - Cloud Functions automatically re-initializes the active `EmailProvider` adapter without cold restart.
2. **Extend Email OTP Verification Window**:
   - If carrier network queuing delays emails, temporarily set `EMAIL_OTP_TTL_MINUTES = 20` (from standard 10) in Remote Config.
3. **Suppress High-Risk Disposable Domains**:
   - In `/admin/config`, verify the disposable email blocklist is actively enforcing rejections to preserve provider domain reputation.

---

## 4. Recovery & Post-Incident Review

1. Confirm email dispatch latency $< 15\text{ seconds}$ and delivery success $> 98\%$.
2. Drain any delayed outbox verification notices.
3. Review provider reputation score and unblock any false-positive suppressed addresses.
