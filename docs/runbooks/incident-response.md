# Operational Runbook: Incident Response & Crisis Management

- **Document ID**: RB-INC-001
- **Classification**: Production Operational Specification
- **Scope**: Udhaar Platform v2 (Cloud Functions v2, Firestore, PWA, Android App, Third-Party Providers).
- **Target SLOs**: Availability $\ge 99.9\%$, p95 Latency $< 200\text{ms}$, Error Rate $< 0.1\%$.

---

## 1. Incident Severity Classification Matrix

| Severity | Definition | Target Response (MTTA) | Target Resolution (MTTR) | Typical Scenarios |
| :---: | :--- | :---: | :---: | :--- |
| **P1 - Critical** | Catastrophic failure affecting all users, active data loss, database compromise, or complete WhatsApp/Sync halt. | **$< 15\text{ minutes}$** | **$< 1\text{ hour}$** | Cloud Functions 5xx rate $> 5\%$, Firestore outage, KMS key unavailable, active data breach. |
| **P2 - Major** | Core feature degraded for significant cohort; third-party provider down with fallback active. | **$< 30\text{ minutes}$** | **$< 4\text{ hours}$** | Meta WhatsApp API degradation, sync conflict backlog spike, circuit breaker tripped for email. |
| **P3 - Minor** | Non-critical functionality degraded; workaround available. | **$< 2\text{ hours}$** | **$< 24\text{ hours}$** | Web Push notification delivery delay, export report generation slowdown. |
| **P4 - Low** | Cosmetic issue, minor telemetry anomaly, or non-impacting error log spike. | **$< 8\text{ hours}$** | **Next release cycle** | Transient client-side RUM metric parse error, non-blocking UI styling glitch. |

---

## 2. Incident Command Team & Roles

1. **Incident Commander (IC)**:
   - Owns incident resolution lifecycle, coordinates team, and approves all production interventions (kill-switch activation, rollback, or key re-wrap).
2. **Technical Lead (TL)**:
   - Investigates root cause, inspects Cloud Monitoring / Cloud Trace, and authors the remediation patch or mitigation command.
3. **Communications Lead (CL)**:
   - Updates status page (`https://updates.udhaar.jyotirmoyb.com`), crafts user notifications, and interfaces with management.

---

## 3. Standard Operating Procedure (SOP)

### Step 1: Triage & War Room Activation
- An alert fires from Cloud Monitoring or Sentry.
- On-call engineer acknowledges alert within 15 minutes.
- For P1/P2 incidents: spin up Google Meet incident war room and assign IC role.

### Step 2: Immediate Containment (Mitigation Over Diagnosis)
If the platform is experiencing an active attack or critical downstream degradation, execute containment immediately before deep debugging:
- If bot attack on sign-up: toggle `KILL_SWITCH_REGISTRATION` via Admin API:
  ```bash
  curl -X POST https://app.udhaar.jyotirmoyb.com/api/v1/admin/kill-switches \
    -H "Authorization: Bearer $ADMIN_SESSION_COOKIE" \
    -H "X-Admin-Reason: Incident INC-501 containment" \
    -H "Content-Type: application/json" \
    -d '{"flagName": "KILL_SWITCH_REGISTRATION", "enabled": true}'
  ```
- If WhatsApp Graph API down: activate SMS fallback or trip WhatsApp circuit breaker.
- If data corruption in progress: toggle `MAINTENANCE_MODE` to freeze all database writes.

### Step 3: Diagnostic Verification
- Inspect structured JSON logs in Google Cloud Logging:
  ```sql
  resource.type="cloud_function"
  jsonPayload.level="ERROR"
  timestamp >= "2026-11-07T00:00:00Z"
  ```
- Group by `correlation_id` to trace end-to-end failure path across edge and backend.
- Inspect Google Cloud Trace for p95/p99 latency bottlenecks.

### Step 4: Resolution & Verification
- Deploy verified hotfix or rollback to prior stable deployment via Firebase CLI:
  ```bash
  firebase hosting:rollback
  firebase deploy --only functions
  ```
- Verify health by executing synthetic canary request:
  ```bash
  curl -I https://app.udhaar.jyotirmoyb.com/api/v1/telemetry/slo
  ```

### Step 5: Post-Incident Review (PIR / Postmortem)
- Within 48 hours of a P1/P2 resolution:
  - Document timeline, root cause, impact duration, and total affected users.
  - Track corrective action items (preventative code changes, new monitoring alerts).
  - Archive in `docs/postmortems/`.
