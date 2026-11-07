# Operational Runbook: Emergency Kill Switches & Remote Feature Flags

- **Document ID**: RB-OPS-004
- **Classification**: High-Privilege Operational Specification
- **Authorization Required**: `ADMIN` or `OPERATOR` role with mandatory justification reason.
- **Propagation Speed**: Instantaneous (in-memory atomic flags / sub-second).

---

## 1. Remote Kill Switches Directory

| Flag Name | Impact When Enabled (`true`) | Typical Trigger Scenario |
| :--- | :--- | :--- |
| `KILL_SWITCH_REGISTRATION` | Rejects `POST /auth/signup/start` with HTTP 503 `ERR_REGISTRATION_SUSPENDED`. Existing users unaffected. | Credential stuffing, WhatsApp OTP bombing, bot registration flood. |
| `KILL_SWITCH_WHATSAPP` | Halts outbound WhatsApp dispatch. UI prompts for SMS or email fallback. | Meta Cloud API outage, Meta billing dispute, rate-limit exhaustion. |
| `KILL_SWITCH_REMINDERS` | Automated reminder dispatcher halts background evaluation. | High complaint rate, unintended spam loop, bug in escalation rules. |
| `KILL_SWITCH_SYNC` | Freezes all `/sync/push` and `/sync/pull` requests with HTTP 503. Local replicas continue working offline. | Schema migration, severe sync conflict bug, data corruption bug. |
| `KILL_SWITCH_GUEST_LINKS` | Revokes public guest verification portal (`/u/**`). | Token leak on public forums, credential guessing on guest endpoints. |
| `MAINTENANCE_MODE` | Halts all mutating requests platform-wide. System becomes read-only. | Catastrophic database migration failure, active security breach. |

---

## 2. Activation Procedure via Admin Console API

Every kill switch mutation **requires** an administrative justification reason ($\ge 5$ characters) passed via `X-Admin-Reason` header or `body.reason`.

### Example 1: Emergency Freeze on Registrations
```bash
curl -X POST https://app.udhaar.jyotirmoyb.com/api/v1/admin/kill-switches \
  -H "Authorization: Bearer $ADMIN_SESSION" \
  -H "X-Admin-Reason: Bot registration flood detected from subnet 103.45.0.0/16" \
  -H "Content-Type: application/json" \
  -d '{
    "flagName": "KILL_SWITCH_REGISTRATION",
    "enabled": true
  }'
```

### Example 2: Suspend Automated Reminders
```bash
curl -X POST https://app.udhaar.jyotirmoyb.com/api/v1/admin/kill-switches \
  -H "Authorization: Bearer $ADMIN_SESSION" \
  -H "X-Admin-Reason: Bug report in gentle reminder copy; pausing evaluator" \
  -H "Content-Type: application/json" \
  -d '{
    "flagName": "KILL_SWITCH_REMINDERS",
    "enabled": true
  }'
```

---

## 3. Querying Active Kill Switch Status

```bash
curl -X GET https://app.udhaar.jyotirmoyb.com/api/v1/admin/kill-switches \
  -H "Authorization: Bearer $ADMIN_SESSION"
```

**Expected Response**:
```json
{
  "KILL_SWITCH_REGISTRATION": false,
  "KILL_SWITCH_WHATSAPP": false,
  "KILL_SWITCH_REMINDERS": false,
  "KILL_SWITCH_SYNC": false,
  "KILL_SWITCH_GUEST_LINKS": false,
  "MAINTENANCE_MODE": false
}
```

---

## 4. Deactivation & Verification Protocol

1. Once the underlying issue is resolved (e.g., bot IPs blocked in Cloud Armor or hotfix deployed), set the flag back to `false`:
   ```bash
   curl -X POST https://app.udhaar.jyotirmoyb.com/api/v1/admin/kill-switches \
     -H "Authorization: Bearer $ADMIN_SESSION" \
     -H "X-Admin-Reason: Threat neutralized; re-enabling registrations" \
     -H "Content-Type: application/json" \
     -d '{"flagName": "KILL_SWITCH_REGISTRATION", "enabled": false}'
   ```
2. Verify audit trail:
   ```bash
   curl -X GET https://app.udhaar.jyotirmoyb.com/api/v1/admin/audit-logs?action=UPDATE_KILL_SWITCH \
     -H "Authorization: Bearer $ADMIN_SESSION"
   ```
3. Execute end-to-end synthetic user test to verify normal operational flow.
