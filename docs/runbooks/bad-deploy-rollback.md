# Runbook: Bad Deployment Rapid Rollback

- **ID**: RB-OPS-003
- **Severity**: P1 (HIGH) / P2 (MEDIUM)
- **Target SLA**: Rollback executed within 60 seconds; Zero data loss.
- **Trigger**: Error rate exceeds 1% following release, Cloud Function latency $p95 > 500\text{ms}$, or fatal crash reported in Sentry / Cloud Logging.

---

## 1. Automated Detection & Criteria for Rollback

A release MUST be immediately rolled back if any of the following occur within 60 minutes of deployment:
1. HTTP 5xx error rate on `/api/v1/**` exceeds 1.0% over a 5-minute rolling window.
2. Latency $p95$ on critical endpoints (`/auth/**`, `/records`, `/sync/**`) exceeds 500ms.
3. Android app sync crashes or reports SQLite / Room migration failure.
4. Core Web Vitals RUM beacon failure rate exceeds 5%.

---

## 2. Immediate One-Command Rollback Execution

### Option A: Local / CLI Execution
Run the automated emergency rollback script:

```bash
# On Linux / macOS / CI:
./scripts/rollback.sh

# On Windows PowerShell:
.\scripts\rollback.ps1
```

### Option B: GitHub Actions Dispatch
1. Navigate to **GitHub Actions > Udhaar Production Staged Rollout & Release**.
2. Select **Run workflow** &rarr; Select Branch: `main`.
3. Set `canary_percentage: 0` or trigger the **Emergency Rollback** job.

### Option C: Manual Firebase Console Revert
If CI/CD or CLI access is degraded:
1. **Firebase Hosting**:
   - Go to **Firebase Console > Hosting > Release History**.
   - Locate the previous stable version (e.g. `v20.9.4`).
   - Click the three dots `...` &rarr; Click **Roll back**.
2. **Cloud Functions**:
   - Go to **GCP Console > Cloud Run / Cloud Functions**.
   - Select function `api`.
   - Go to **Revisions** tab &rarr; Route 100% of traffic to the previous healthy revision.
   - Click **Save**.

---

## 3. Database & Schema Backward Compatibility

All database changes in Udhaar Platform v2 are **additive and backward-compatible**:
- New Firestore fields have fallback defaults in application logic.
- Room database migrations use explicit fallback and preserve all user ledger data.
- **NEVER** drop collections, tables, or columns during a rollback.
- In-flight outbox mutations queued in client IndexedDB or Android Room will seamlessly resume syncing when the previous stable revision is restored.

---

## 4. Post-Rollback Verification

1. Verify public endpoint health:
   ```bash
   node scripts/rollback.js --verify-health
   ```
2. Check Cloud Logging error rates: ensure HTTP 5xx errors cease within 2 minutes.
3. Notify stakeholders and war room:
   ```text
   [INCIDENT RESOLUTION] Production deployment rolled back to previous stable release.
   Error rate normalized. Active investigation underway. Postmortem to follow within 24h.
   ```
