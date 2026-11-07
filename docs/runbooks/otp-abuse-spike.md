# Runbook: OTP Abuse Spike & Bot Mitigation

- **ID**: RB-SEC-002
- **Severity**: CRITICAL (P1)
- **Trigger**: Hourly OTP generation exceeds 5x baseline or global daily send budget reaches 80%.

---

## 1. Automated Guardrails & Budget Tripping

1. **50% Global Daily Budget Reached**: Warning alert dispatched to Engineering on-call.
2. **80% Global Daily Budget Reached**: Escalation alert fires; IP rate limits automatically tighten from 20 req/hr to 5 req/hr.
3. **100% Global Daily Budget Reached**: **Automatic Emergency Circuit Breaker Trips (`costControl.state = OPEN`)**. All outbound paid OTP dispatches are frozen until manual administrative reset.

---

## 2. Immediate Diagnostic Steps

1. **Inspect Cloud Monitoring Metrics**:
   - Query `otpChallenges/` grouped by `ipHash`, user agent, and destination dial code prefix.
2. **Evaluate reCAPTCHA Enterprise Assessment Distribution**:
   - Check risk scores: if scores are clustered below $0.3$, an automated botnet or headless browser pool is driving attacks.
3. **Analyze Phone Number Range Clustering**:
   - Check whether destination numbers are sequential (toll-fraud / SMS pumping attacks) or targeting disallowed countries.

---

## 3. Mitigation Actions

1. **Activate Cloud Armor / WAF IP Deny List**:
   - Add the offending ASN, subnet, or CIDR blocks to Google Cloud Armor deny rules.
2. **Elevate reCAPTCHA Human Confidence Threshold**:
   - In Cloud Functions config, elevate `RECAPTCHA_MIN_SCORE` from $0.5$ to $0.7$ or $0.85$.
3. **Emergency Country Quarantine**:
   - In Remote Config / Admin Console, restrict `ALLOWED_COUNTRY_CODES` exclusively to `["+91"]` (India) to block international pumping fraud.
4. **Trigger Admin Kill Switch**:
   - If attacks persist, toggle `otpDispatchPaused = true` in `/admin/killswitches`. Existing authenticated sessions remain unaffected.

---

## 4. Recovery & Post-Incident Review

1. Verify abuse traffic drops to 0.
2. Reset `costControl.state = CLOSED` and restore normal rate limits after threat IP addresses are banned.
3. Audit budget consumption and file dispute/refund with telephony provider if fraudulent toll pumping occurred.
