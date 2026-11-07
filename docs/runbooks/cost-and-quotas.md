# Operational Runbook: Provider Cost Controls, Quotas & Circuit Breakers

- **Document ID**: RB-FIN-006
- **Classification**: Financial & Infrastructure Cost Governance
- **Default Spend Limits**:
  - **Meta WhatsApp Cloud API**: 150 units/hr, 500 units/day (~₹2,500 daily budget).
  - **Transactional Email (SendGrid / Postmark)**: 500 units/hr, 2,000 units/day.
  - **Indian DLT SMS (Fast2SMS / MSG91)**: 50 units/hr, 200 units/day.

---

## 1. Circuit Breaker States & Behavior

```
               [Spend < Limit]
             ┌─────────────────┐
             │  State: CLOSED  │  <-- Operations Proceed Normally
             └────────┬────────┘
                      │ Spend >= Daily / Hourly Cap
                      ▼
             ┌─────────────────┐
             │   State: OPEN   │  <-- Calls Fail Fast with HTTP 503 (ERR_CIRCUIT_OPEN)
             └────────┬────────┘
                      │ Admin Reset / UTC Midnight Rolling Window
                      ▼
             ┌─────────────────┐
             │ State: HALF_OPEN│  <-- Trialing 10 Requests
             └─────────────────┘
```

---

## 2. Inspecting Real-Time Circuit Status

```bash
curl -X GET https://app.udhaar.jyotirmoyb.com/api/v1/admin/circuits \
  -H "Authorization: Bearer $ADMIN_SESSION"
```

**Response Format**:
```json
{
  "WHATSAPP": {
    "provider": "WHATSAPP",
    "state": "CLOSED",
    "dailySpent": 142,
    "dailyLimit": 500,
    "hourlySpent": 28,
    "hourlyLimit": 150,
    "trippedAtMs": null,
    "tripReason": null
  },
  "EMAIL": {
    "provider": "EMAIL",
    "state": "CLOSED",
    "dailySpent": 86,
    "dailyLimit": 2000
  }
}
```

---

## 3. Resolving a Tripped Circuit Breaker

When an alert fires notifying that `WHATSAPP` circuit has tripped:

### Step 1: Analyze Spend Spike Cause
1. Inspect `auditLogs/` and `functions` access logs for unusual patterns:
   - Is traffic legitimate user onboarding surge?
   - Or a botnet attempting OTP bombing?
2. If legitimate launch surge, approve budget expansion.

### Step 2: Execute Admin Reset with Expanded Limit
Reset circuit to `CLOSED` and optionally expand daily limit:
```bash
curl -X POST https://app.udhaar.jyotirmoyb.com/api/v1/admin/circuits/reset \
  -H "Authorization: Bearer $ADMIN_SESSION" \
  -H "X-Admin-Reason: Launch day traffic surge; approved budget expansion to 1,500 units" \
  -H "Content-Type: application/json" \
  -d '{
    "providerName": "WHATSAPP",
    "newDailyLimit": 1500
  }'
```

---

## 4. Cloud Storage Lifecycle Controls

To prevent unbounded storage costs from payment receipt uploads:
- Cloud Storage bucket `gs://udhaar-receipts-asia-south1/` applies a 90-day automated Delete lifecycle rule (`storage.lifecycle.json`).
- Pre-signed URLs mandate a maximum size limit of `5,242,880 bytes` (5MB).
- Uploads exceeding 5MB are rejected at the edge with HTTP 400 `ERR_PAYLOAD_TOO_LARGE`.
