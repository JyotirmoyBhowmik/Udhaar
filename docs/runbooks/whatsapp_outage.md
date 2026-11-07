# Runbook: WhatsApp Outage & Failover Protocol

- **ID**: RB-COMM-001
- **Severity**: HIGH
- **Trigger**: WhatsApp message delivery rate drops below 80% over 5 minutes or Meta Graph API returns HTTP 5xx.

---

## 1. Detection & Automated Alerts

- Cloud Monitoring alert fires: `udhaar_whatsapp_delivery_rate < 0.80`
- PagerDuty notification to On-Call Engineer.

## 2. Immediate Diagnostic Steps

1. Check Meta Business API Status Page: [https://metastatus.com/](https://metastatus.com/)
2. Verify Cloud Functions error logs for Graph API error codes:
   - `Code 131026`: Message undeliverable (recipient not on WhatsApp).
   - `Code 130429`: Rate limit exceeded on WhatsApp Cloud API tier.
   - `Code 131056`: Payment issue or credit line exhaustion on Meta Business Manager.

## 3. Mitigation & Failover Actions

1. **Activate Fallback Mode via Remote Config**:
   - Change `otp_provider_fallback_enabled` to `true`.
   - The UI automatically presents the secondary choice: *"Send code by SMS instead"* or *"Send code to verified email"*.
2. **Enable Indian DLT SMS Gateway**:
   - Set `sms_channel_active: true` in Firebase Remote Config.
   - Traffic routes through pre-approved DLT templates.
3. **If Meta Credit Line Issue**:
   - Primary billing admin navigates to Meta Business Manager &rarr; Payment Methods to verify card balance.

## 4. Post-Incident Review

- Verify delivery rate recovery above 95%.
- Update `auditLogs/` with outage window and affected user count.
