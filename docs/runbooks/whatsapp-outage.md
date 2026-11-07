# Runbook: WhatsApp Outage & Failover Protocol

- **ID**: RB-COMM-001
- **Severity**: HIGH (P2)
- **Trigger**: WhatsApp message delivery rate drops below 80% over a 5-minute rolling window, or Meta Graph API returns HTTP 5xx.

---

## 1. Detection & Automated Alerts

- Cloud Monitoring alert fires: `udhaar_whatsapp_delivery_rate < 0.80`.
- Automated PagerDuty notification to On-Call Engineer and SRE lead.
- Web client telemetry logs `WHATSAPP_DISPATCH_TIMEOUT` errors.

---

## 2. Immediate Diagnostic Steps

1. **Check Public Status**: Inspect the [Meta for Business Status Page](https://metastatus.com/) for WhatsApp Cloud API incidents.
2. **Inspect Cloud Functions Error Logs**:
   - Query logs: `resource.type="cloud_function" textPayload=~"WhatsApp dispatch error"`.
   - Check error codes:
     - `131026`: Recipient phone number is not registered on WhatsApp.
     - `130429`: Cloud API rate limit exceeded on tier.
     - `131056`: Payment issue or credit line exhaustion in Meta Business Manager.
3. **Verify Webhook Receipt**: Ensure inbound webhook messages on `/api/v1/auth/webhook/whatsapp` receive HTTP 200 responses.

---

## 3. Mitigation & Failover Actions

1. **Activate Fallback Provider Mode**:
   - In Firebase Remote Config or Admin Console, set `otp_provider_fallback_enabled = true`.
   - The UI immediately presents fallback options to users:
     - *"Send code by SMS instead"*
     - *"Send code to verified email"* (for existing registered accounts).
2. **Enable Indian DLT SMS Channel**:
   - Set `sms_channel_active = true`.
   - Outbound OTPs will route through the secondary DLT-registered SMS provider.
3. **Address Meta Credit Line Exhaustion**:
   - If error is `131056`, billing administrator navigates to [Meta Business Manager](https://business.facebook.com/) &rarr; **Payment Methods** to refresh card limit or clear pending invoice.
4. **Display Public Status Banner**:
   - Set `service_status_banner = "WhatsApp delivery is experiencing carrier delays. Please use SMS or Email OTP."`

---

## 4. Recovery & Post-Incident Review

1. Confirm WhatsApp message delivery rate returns to $> 95\%$.
2. Reset `otp_provider_fallback_enabled = false` and clear status banner.
3. Log duration and impacted session count in `auditLogs/`.
