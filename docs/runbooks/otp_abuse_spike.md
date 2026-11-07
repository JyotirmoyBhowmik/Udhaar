# Runbook: OTP Abuse Spike & Bot Mitigation

- **ID**: RB-SEC-002
- **Severity**: CRITICAL
- **Trigger**: Hourly OTP generation exceeds 5x baseline or global daily send budget reaches 80%.

---

## 1. Automated Guardrails

1. At 50% daily budget: Warning alert to Engineering channel.
2. At 80% daily budget: Escalation alert and automatic tightening of IP rate limits from 20/hr to 5/hr.
3. At 100% daily budget: **Automatic Emergency Kill Switch** trips. No new OTP dispatches are permitted without manual admin override.

## 2. Immediate Diagnostic Steps

1. Inspect Cloud Monitoring: Group `otpChallenges/` creation by `ipHash` and country code.
2. Check reCAPTCHA Enterprise score distribution:
   - If scores $< 0.3$: automated botnet script attack.
3. Check target number patterns:
   - Sequential numbers or non-India/Nepal country codes.

## 3. Mitigation Actions

1. **Activate Cloud Armor / WAF IP Blocklist**:
   - Add offending subnet / CIDR blocks to Google Cloud Armor deny rules.
2. **Elevate reCAPTCHA Assessment Threshold**:
   - In Cloud Functions config, set `RECAPTCHA_MIN_SCORE=0.7` (demands higher human confidence).
3. **Emergency Country Quarantine**:
   - If abuse originates from specific international dial codes, restrict `ALLOWED_COUNTRY_CODES` exclusively to `["+91"]` via Remote Config.
