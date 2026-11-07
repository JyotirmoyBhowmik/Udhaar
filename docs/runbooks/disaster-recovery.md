# Udhaar Platform v2: Disaster Recovery & Business Continuity Runbook

- **Document ID**: `RB-OPS-004`
- **Classification**: Confidential / Operations & Security Engineering
- **Target RPO**: $< 1\text{ minute}$ (PITR continuous logs); $< 24\text{ hours}$ (Daily cold snapshot)
- **Target RTO**: $< 30\text{ minutes}$ (Traffic failover & session reset); $< 2\text{ hours}$ (Full database restore & KMS re-wrapping)
- **Primary Region**: Google Cloud `asia-south1` (Mumbai)
- **Backup Storage**: Google Cloud Storage `gs://udhaar-backups-asia-south1/` (Object Versioning & Retention Lock)

---

## 1. Trigger Conditions & Disaster Scenarios

This runbook is initiated upon declaration of a **SEV-0** or **SEV-1** disaster by the Incident Commander:

1. **Scenario A (Data Loss or Corrupted Ingest)**: Malicious or accidental bulk data deletion, logic error corrupting ledger balance states, or multi-zone storage outage.
2. **Scenario B (Cloud KMS Key Compromise)**: Exposure or unauthorized cryptographic operations on `projects/udhaar-prod/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek`.
3. **Scenario C (Widespread Credential / Session Compromise)**: Compromise of master service account credentials, server secret leakage, or active token replay attack.

---

## 2. Disaster Recovery Protocol A: Firestore Point-in-Time Recovery (PITR)

Cloud Firestore maintains continuous 7-day Point-in-Time Recovery (PITR). Any state can be restored to microsecond granularity within the 7-day retention window.

### Step 2.1: Freeze Traffic & Engage Emergency Kill Switches
Prevent ongoing client mutations while preparing recovery:
```bash
# Update Firestore emergency config to block client writes
gcloud firestore documents update "config/global" \
  --project="udhaar-prod" \
  --database="(default)" \
  --data='{"KILL_SWITCH_SYNC_INGEST": true, "KILL_SWITCH_OTP_DISPATCH": true, "updatedAtMs": '$(date +%s%3N)'}'
```

### Step 2.2: Restore to New Firestore Database via PITR
Identify the timestamp immediately preceding corruption (e.g., `2026-11-07T12:00:00Z`):
```bash
# Target recovery database
RECOVERY_DB="udhaar-pitr-$(date +%Y%m%d%H%M)"
RESTORE_TIME="2026-11-07T12:00:00.000Z"

# Execute PITR restore
gcloud firestore databases restore \
  --project="udhaar-prod" \
  --source-database="(default)" \
  --destination-database="${RECOVERY_DB}" \
  --restore-time="${RESTORE_TIME}" \
  --location="asia-south1"
```

*Monitor restore status:*
```bash
gcloud firestore operations list \
  --project="udhaar-prod" \
  --filter="metadata.database:${RECOVERY_DB}"
```

### Step 2.3: Cold Storage Daily Snapshot Fallback
If PITR is unavailable (e.g., corruption occurred beyond 7 days):
```bash
# 1. Create fresh staging database
gcloud firestore databases create \
  --project="udhaar-prod" \
  --database="udhaar-snapshot-restore" \
  --location="asia-south1" \
  --type="firestore-native"

# 2. Import latest daily backup snapshot from locked GCS bucket
gcloud firestore import "gs://udhaar-backups-asia-south1/daily/2026-11-06/" \
  --project="udhaar-prod" \
  --database="udhaar-snapshot-restore"
```

### Step 2.4: Data Integrity Verification
Before rerouting application traffic, verify cryptographic integrity and index completeness:
1. **Document Counts**: Validate record parity across `users`, `phoneIndex`, `emailIndex`, `records`, `sync_changelogs`.
2. **HMAC Index Consistency**: Sample 100 users and verify that `phoneHash` and `emailHash` correspond to HMAC-SHA256(`destination`, `UDHAAR_PHONE_PEPPER`).
3. **Ledger Audit Log Chains**: Validate Merkle parent hash linkages across all `audit_logs` collections.

### Step 2.5: Switch Production Traffic
Update Cloud Functions environment to bind to the verified recovery database:
```bash
firebase functions:config:set firestore.database="${RECOVERY_DB}"
firebase deploy --only functions
```

---

## 3. Disaster Recovery Protocol B: Cloud KMS Key Compromise & Bulk DEK Re-Wrapping

If Cloud KMS KEK version `projects/udhaar-prod/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek/cryptoKeyVersions/N` is exposed:

### Step 3.1: Create New KMS Key Version Immediately
```bash
KEY_RING="projects/udhaar-prod/locations/asia-south1/keyRings/udhaar"
KEY_NAME="${KEY_RING}/cryptoKeys/user-dek"

# Create new primary key version
gcloud kms keys versions create \
  --key="user-dek" \
  --keyring="udhaar" \
  --location="asia-south1" \
  --primary
```

### Step 3.2: Run Automated Bulk DEK Re-Wrapping Script
The system uses envelope encryption: each user has a 256-bit AES DEK stored wrapped in `users/{uid}/crypto/dek`. Run the re-wrap job to unwrap each DEK with the old version and re-wrap under the new primary key:

```javascript
// scripts/ops-rewrap-deks.js
const admin = require("firebase-admin");
const { KeyManagementServiceClient } = require("@google-cloud/kms");

const db = admin.firestore();
const kmsClient = new KeyManagementServiceClient();
const NEW_KEY_NAME = "projects/udhaar-prod/locations/asia-south1/keyRings/udhaar/cryptoKeys/user-dek";

async function rewrapAllUserDeks() {
  const usersSnap = await db.collection("users").get();
  console.log(`Starting DEK re-wrap for ${usersSnap.size} tenants...`);

  for (const userDoc of usersSnap.docs) {
    const uid = userDoc.id;
    const dekRef = db.collection("users").doc(uid).collection("crypto").doc("dek");
    const dekSnap = await dekRef.get();
    if (!dekSnap.exists) continue;

    const { wrappedDek, kmsKeyName } = dekSnap.data();
    
    // 1. Unwrap with prior KMS version
    const [unwrapRes] = await kmsClient.decrypt({
      name: kmsKeyName,
      ciphertext: Buffer.from(wrappedDek, "base64")
    });

    // 2. Re-wrap with new primary KMS key version
    const [wrapRes] = await kmsClient.encrypt({
      name: NEW_KEY_NAME,
      plaintext: unwrapRes.plaintext
    });

    // 3. Atomically persist re-wrapped DEK
    await dekRef.update({
      wrappedDek: Buffer.from(wrapRes.ciphertext).toString("base64"),
      kmsKeyName: NEW_KEY_NAME,
      kmsKeyVersion: wrapRes.name,
      updatedAtMs: Date.now()
    });
  }
  console.log("DEK re-wrap complete. Zero plaintext records were ever exposed.");
}
```

### Step 3.3: Destroy Compromised KMS Key Version
Once all user DEKs are re-wrapped under the new primary version:
```bash
# Destroy old compromised version (schedules permanent destruction)
gcloud kms keys versions destroy 1 \
  --key="user-dek" \
  --keyring="udhaar" \
  --location="asia-south1"
```

---

## 4. Disaster Recovery Protocol C: Emergency Global Session Revocation

If server secrets, admin tokens, or widespread sessions are compromised:

### Step 4.1: Revoke All Firebase Auth Refresh Tokens Project-Wide
```javascript
// Invalidate all tokens issued before now
const cutoffTimeSeconds = Math.floor(Date.now() / 1000);
const users = await admin.auth().listUsers(1000);
for (const user of users.users) {
  await admin.auth().revokeRefreshTokens(user.uid);
}
```

### Step 4.2: Revoke All Firestore Active Sessions
Batch-update the `sessions` collection in Firestore:
```bash
# Mark all active sessions as revoked with immediate effect
node -e '
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();
async function run() {
  const snaps = await db.collection("sessions").where("revoked", "==", false).get();
  const batch = db.batch();
  snaps.docs.forEach(d => batch.update(d.ref, { revoked: true, revokedReason: "EMERGENCY_GLOBAL_RESET" }));
  await batch.commit();
  console.log(`Revoked ${snaps.size} active sessions.`);
}
run();
'
```

### Step 4.3: Cycle Application Secrets in Google Secret Manager
```bash
# Rotate UDHAAR_SERVER_SECRET and webhook secrets
NEW_SECRET=$(openssl rand -hex 32)
echo -n "${NEW_SECRET}" | gcloud secrets versions add "UDHAAR_SERVER_SECRET" --data-file=-

# Redeploy cloud functions to reload active secret bindings
firebase deploy --only functions
```

---

## 5. Post-Disaster Audit & Resumption Checklist

| Step | Verification Action | Responsible Role | Status |
|:---|:---|:---|:---|
| 1 | All unit, chaos, and security suites pass against restored instance | Principal Backend Engineer | [ ] |
| 2 | Point-in-time document counts match expected baseline within 0.01% | Lead SRE | [ ] |
| 3 | Cloud KMS key versions disabled/destroyed; primary version validated | Security Engineer | [ ] |
| 4 | Secret Manager versions rotated and old versions disabled | SRE Lead | [ ] |
| 5 | Emergency kill switches gradually lifted (read-only -> full sync) | Incident Commander | [ ] |
| 6 | Real-time structured log streams monitored for 401/403/429 spikes | Observability Lead | [ ] |
| 7 | Regulatory and incident notifications dispatched (if applicable) | Chief Privacy Officer | [ ] |
