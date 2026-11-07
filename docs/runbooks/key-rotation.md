# Operational Runbook: Cloud KMS Key Rotation & Envelope Re-Wrap

- **Document ID**: RB-SEC-005
- **Classification**: Cryptographic Security Specification
- **Rotation Interval**: Every 90 Days (Automated Primary KEK)
- **KMS Key Resource**: `projects/udhaar-prod/locations/asia-south1/keyRings/udhaar-kek-ring/cryptoKeys/user-data-kek`

---

## 1. Cryptographic Envelope Architecture

- **Primary KEK (Key Encryption Key)**: Resides inside Google Cloud KMS (Hardware Security Module HSM / FIPS 140-2 Level 3). It never leaves the KMS boundary.
- **Per-User DEK (Data Encryption Key)**: AES-256 key generated per user in volatile memory. It encrypts sensitive ledger fields (amounts, names, notes, phone, email).
- **Wrapped DEK Storage**: The user's DEK is encrypted by the KMS KEK and stored as `wrappedDek` in `users/{uid}/crypto/keyMetadata`.
- **KMS Rotation**: Cloud KMS automatically rotates the KEK version every 90 days. Existing wrapped keys can always be unwrapped using past key versions.

---

## 2. Routine 90-Day Key Rotation Verification

1. Inspect active KMS key versions:
   ```bash
   gcloud kms keys versions list \
     --key=user-data-kek \
     --keyring=udhaar-kek-ring \
     --location=asia-south1
   ```
2. Confirm the newest key version is marked `PRIMARY`.
3. Historical key versions remain in state `ENABLED` to allow decryption of legacy wrapped DEKs.

---

## 3. Emergency Key Compromise Protocol (KEK Re-Wrap)

If a service account credential with `cloudkms.cryptoKeyEncrypterDecrypter` permissions is leaked or compromised:

### Step 1: Immediately Destroy Compromised Key Version
```bash
gcloud kms keys versions destroy 2 \
  --key=user-data-kek \
  --keyring=udhaar-kek-ring \
  --location=asia-south1
```

### Step 2: Create and Set New Primary Key Version
```bash
gcloud kms keys versions create \
  --key=user-data-kek \
  --keyring=udhaar-kek-ring \
  --location=asia-south1 \
  --primary
```

### Step 3: Trigger Batch DEK Re-Wrap Script
Execute the administrative background task that reads each user's `wrappedDek`, unwraps it with the valid key version, and re-wraps it with the new primary KEK:
```bash
node scripts/rewrap-deks.js --keyring=udhaar-kek-ring --location=asia-south1
```

### Step 4: Flush Server-Side DEK Caches
Call `clearDekCache()` across all running Cloud Functions instances to purge in-memory cached DEK instances.
