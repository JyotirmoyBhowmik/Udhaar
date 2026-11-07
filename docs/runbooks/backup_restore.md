# Runbook: Firestore Point-in-Time Recovery & Backup Restore Drill

- **ID**: RB-OPS-003
- **Severity**: HIGH
- **RPO Target**: $< 1\text{ hour}$
- **RTO Target**: $< 4\text{ hours}$

---

## 1. Daily Backup Architecture

1. **Continuous PITR**: Cloud Firestore Point-in-Time Recovery is enabled with 7-day retention.
2. **Daily Snapshot**: Cloud Scheduler triggers automated daily export to locked Cloud Storage bucket `gs://udhaar-backups-asia-south1/` at `02:00 IST` (20:30 UTC).
3. **Retention**: 90 days retention via Storage lifecycle policy.

## 2. Restore Procedure (Quarterly Drill)

1. **Provision Staging Recovery Database**:
   ```bash
   gcloud firestore databases create --database=restore-drill-$(date +%Y%m%d) --location=asia-south1
   ```
2. **Import Daily Snapshot**:
   ```bash
   gcloud firestore import gs://udhaar-backups-asia-south1/2026-11-07/ --database=restore-drill-$(date +%Y%m%d)
   ```
3. **Verify Data Integrity**:
   - Check document counts in `users/`, `records/`, `events/`.
   - Run cryptographic validation check on sample Merkle audit chains.
4. **Teardown**:
   - Delete temporary restore drill database once verification succeeds.
